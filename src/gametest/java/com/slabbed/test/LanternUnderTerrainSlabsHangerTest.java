package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.api.ModInitializer;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * L11 port (1.21.1). Covers TWO DISTINCT bugs in the underside-support helpers
 * ({@code loweredSlabUndersideSupportDy} / {@code floorTorchBottomSlabSupportDy}); see the commit
 * message and HANDOFF for the full distinction and the empirical reachability findings.
 *
 * <p><b>Bug A — Terrain-Slabs-compat guard (the actual L11 lesson, ported from the 1.21.11 sibling's
 * {@code LanternUnderTerrainSlabsHangerTest}).</b> A hanger under a Terrain-Slabs (TS)-owned slab must
 * NOT inherit a Slabbed -0.5: TS owns its own vertical offset and renders its slabs FLUSH, so following
 * would drop the object a half-block into the slab (the smoosh). This session's L8 anchor-widening made
 * a cantilevered TS slab a persistent lowered carrier for the first time, so
 * {@code loweredSlabUndersideSupportDy}'s {@code isAdjacentSideSlabLowered} branch now reports a lowered
 * TS TOP/DOUBLE support and leaks -0.5. Fix = fold a {@code CompatHooks.shouldSkipOffset} guard into the
 * shared helper as its first substantive check, fixing every consumer at once (the shared-predicate
 * half-fix lesson). The REACHABLE, empirically-confirmed leak is a hanger (spore blossom etc.) under a
 * lowered TS DOUBLE slab — proven RED without the guard (spore read -0.5). See the doc note on why the
 * BOTTOM-support and HANGING-lantern paths do NOT exercise this guard (they short-circuit earlier).
 *
 * <p><b>Bug B — DOUBLE-only slab-below gap (a pre-existing, TS-unrelated rendering bug).</b>
 * {@code floorTorchBottomSlabSupportDy} recognized only a lowered {@code SlabType.DOUBLE} carrier below
 * the bottom slab (via {@code isLoweredDoubleSlabCarrier}) — the exact gap Phase 2 (L8) already fixed in
 * {@code getYOffsetInner}'s own slab-below branch via {@code isLoweredTopLikeSlabCarrier}. So an object
 * on a bottom slab resting on a lowered TOP carrier rendered a half-block too high versus the identical
 * DOUBLE case. Fix = widen that check with {@code || isLoweredTopLikeSlabCarrier}. (The sibling 1.21.11
 * branch has no separate {@code floorTorchBottomSlabSupportDy}, so Bug B is unique to this branch.)
 *
 * <p>TS is stubbed exactly as {@code RedstoneGateOnTerrainSlabsPlacementTest} does it: a real
 * {@code SlabBlock} registered under the {@code terrain_slabs} namespace, so
 * {@code CompatHooks.shouldSkipOffset} (which keys on that namespace) returns true for it, while the
 * gametest mod {@code provides} {@code terrain_slabs} so {@code TerrainSlabsCompat.isLoaded()} is true.
 */
public final class LanternUnderTerrainSlabsHangerTest {

    private static final double EPS = 1.0e-6;

    private static final Identifier TEST_TS_SLAB_ID = Identifier.of("terrain_slabs", "l11_underside_test_slab");
    private static final Block TEST_TS_SLAB = new SlabBlock(AbstractBlock.Settings.copy(Blocks.STONE_SLAB));

    public static final class TerrainSlabsUndersideTestEntrypoint implements ModInitializer {
        @Override
        public void onInitialize() {
            if (!Registries.BLOCK.containsId(TEST_TS_SLAB_ID)) {
                Registry.register(Registries.BLOCK, TEST_TS_SLAB_ID, TEST_TS_SLAB);
            }
        }
    }

    // Places an anchored dirt lowering a same-level cantilevered slab of `carrierType` via horizontal
    // side-adjacency, and returns that carrier's pos. `carrierBlock` chooses vanilla vs the TS stub.
    private static BlockPos loweredCarrier(ServerWorld w, TestContext ctx, int zOff, Block carrierBlock, SlabType carrierType) {
        BlockPos anchorBottomSlab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, zOff);
        BlockPos dirt = anchorBottomSlab.up();
        BlockPos carrier = dirt.east();
        w.setBlockState(anchorBottomSlab,
                Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        w.setBlockState(dirt, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, dirt, w.getBlockState(dirt));
        w.setBlockState(carrier, carrierBlock.getDefaultState().with(SlabBlock.TYPE, carrierType), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, carrier, w.getBlockState(carrier));
        return carrier;
    }

    // ── Bug A: the REACHABLE, RED-provable leak ────────────────────────────────────────────────────

    // THE FIX (Bug A): a spore blossom hanging under a lowered Terrain-Slabs DOUBLE slab must stay FLUSH
    // (0.0). Without the shouldSkipOffset guard in loweredSlabUndersideSupportDy, isAdjacentSideSlabLowered
    // reports the (L8-anchored) TS slab lowered and the spore follows to -0.5 — the smoosh. Proven RED
    // (measured -0.5 with the guard neutralized). Spore blossom is used (not a HANGING lantern) because a
    // HANGING lantern short-circuits earlier via ceilingHungDecorationDy's own pre-existing guard and so
    // does not exercise THIS helper.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void sporeBlossomUnderTerrainSlabsDoubleSlabStaysFlush(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos tsCarrier = loweredCarrier(w, ctx, 12, TEST_TS_SLAB, SlabType.DOUBLE);
        double tsDy = SlabSupport.getYOffset(w, tsCarrier, w.getBlockState(tsCarrier));
        ctx.assertTrue(Math.abs(tsDy) <= EPS,
                "setup: the TS DOUBLE slab must render FLUSH (0.0) — TS owns its offset; got " + tsDy);
        ctx.assertTrue(SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, tsCarrier, w.getBlockState(tsCarrier)),
                "setup: the TS slab must be a persistent carrier (the L8 anchor-widening that makes this "
                        + "leak reachable) — otherwise this test proves nothing");

        BlockPos sporePos = tsCarrier.down();
        w.setBlockState(sporePos, Blocks.SPORE_BLOSSOM.getDefaultState(), Block.NOTIFY_LISTENERS);
        double sporeDy = SlabSupport.getYOffset(w, sporePos, w.getBlockState(sporePos));
        ctx.assertTrue(Math.abs(sporeDy) <= EPS,
                "Bug A: a spore blossom under a flush Terrain-Slabs DOUBLE slab must stay FLUSH (0.0), not "
                        + "follow a Slabbed -0.5 into the slab; got " + sporeDy);
        ctx.complete();
    }

    // REGRESSION GUARD (Bug A anti-jam): the SAME helper+lane must STILL lower a hanger under a lowered
    // VANILLA DOUBLE slab to -0.5, or the lowered slab underside clips into the hanger. The fix must
    // change ONLY the Terrain-Slabs case. This exercises the exact loweredSlabUndersideSupportDy branch
    // the TS guard sits in front of, proving the guard is surgical.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void sporeBlossomUnderLoweredVanillaDoubleSlabStillFollows(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos carrier = loweredCarrier(w, ctx, 24, Blocks.BIRCH_SLAB, SlabType.DOUBLE);
        double carrierDy = SlabSupport.getYOffset(w, carrier, w.getBlockState(carrier));
        ctx.assertTrue(Math.abs(carrierDy + 0.5) <= EPS,
                "setup: the vanilla DOUBLE carrier must itself render -0.5 (Slabbed lowers it, unlike TS); got " + carrierDy);

        BlockPos sporePos = carrier.down();
        w.setBlockState(sporePos, Blocks.SPORE_BLOSSOM.getDefaultState(), Block.NOTIFY_LISTENERS);
        double sporeDy = SlabSupport.getYOffset(w, sporePos, w.getBlockState(sporePos));
        ctx.assertTrue(Math.abs(sporeDy + 0.5) <= EPS,
                "Bug A anti-jam: a spore blossom under a lowered VANILLA DOUBLE slab must STILL follow it to "
                        + "-0.5 — the fix must only change the Terrain-Slabs case; got " + sporeDy);
        ctx.complete();
    }

    // COVERAGE (Bug A, sibling parity): a HANGING lantern under a flush TS slab must stay flush. This
    // routes via ceilingHungDecorationDy's PRE-EXISTING shouldSkipOffset guard (not the helper this phase
    // touched), but pins the end-to-end "hanger under TS is flush" contract the live report was about.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void hangingLanternUnderTerrainSlabsBottomSlabStaysFlush(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos tsCarrier = loweredCarrier(w, ctx, 36, TEST_TS_SLAB, SlabType.BOTTOM);
        ctx.assertTrue(SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, tsCarrier, w.getBlockState(tsCarrier)),
                "setup: TS slab must anchor (L8 widening) or this proves nothing");

        BlockPos lanternPos = tsCarrier.down();
        w.setBlockState(lanternPos, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true), Block.NOTIFY_LISTENERS);
        double lanternDy = SlabSupport.getYOffset(w, lanternPos, w.getBlockState(lanternPos));
        ctx.assertTrue(Math.abs(lanternDy) <= EPS,
                "Bug A coverage: a hanging lantern under a flush Terrain-Slabs slab must stay FLUSH (0.0); got " + lanternDy);
        ctx.complete();
    }

    // COVERAGE (Bug A, top-half-trapdoor lane — unique to THIS branch): a top-half trapdoor under a flush
    // TS slab must stay flush. (For a TS BOTTOM support the helper short-circuits to NaN via
    // isBottomSlab's own TS exclusion, so this pins the flush contract but does not itself go RED —
    // documented honestly; the RED-provable Bug A case is the spore-under-TS-DOUBLE test above.)
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void topTrapdoorUnderTerrainSlabsSlabStaysFlush(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos tsCarrier = loweredCarrier(w, ctx, 48, TEST_TS_SLAB, SlabType.BOTTOM);
        BlockPos trapdoorPos = tsCarrier.down();
        w.setBlockState(trapdoorPos,
                Blocks.OAK_TRAPDOOR.getDefaultState().with(Properties.BLOCK_HALF, BlockHalf.TOP), Block.NOTIFY_LISTENERS);
        double trapdoorDy = SlabSupport.getYOffset(w, trapdoorPos, w.getBlockState(trapdoorPos));
        ctx.assertTrue(Math.abs(trapdoorDy) <= EPS,
                "Bug A coverage: a top-half trapdoor under a flush Terrain-Slabs slab must stay flush (0.0); got " + trapdoorDy);
        ctx.complete();
    }

    // ── Bug B: DOUBLE-only slab-below gap in floorTorchBottomSlabSupportDy ──────────────────────────

    // THE FIX (Bug B): a floor torch on a bottom slab that rests on a lowered TOP carrier must follow to
    // -1.0, exactly like the identical DOUBLE case — previously the TOP carrier was unrecognized
    // (isLoweredDoubleSlabCarrier only) and the torch read a half-block too high (-0.5). Proven RED
    // without the widening (measured -0.5).
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void floorTorchOnBottomSlabOverLoweredTopCarrierFollowsToMinusOne(TestContext ctx) {
        double torchDy = floorTorchDyOverLoweredCarrier(ctx, SlabType.TOP, 60);
        ctx.assertTrue(Math.abs(torchDy + 1.0) <= EPS,
                "Bug B: a floor torch on a bottom slab resting on a lowered TOP carrier must follow to -1.0 "
                        + "(matching the DOUBLE case); got " + torchDy);
        ctx.complete();
    }

    // CONTROL (Bug B): the already-correct DOUBLE case — same geometry, DOUBLE carrier, must be -1.0.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void floorTorchOnBottomSlabOverLoweredDoubleCarrierFollowsToMinusOne(TestContext ctx) {
        double torchDy = floorTorchDyOverLoweredCarrier(ctx, SlabType.DOUBLE, 72);
        ctx.assertTrue(Math.abs(torchDy + 1.0) <= EPS,
                "control: a floor torch on a bottom slab resting on a lowered DOUBLE carrier follows to -1.0; got "
                        + torchDy);
        ctx.complete();
    }

    // REGRESSION GUARD (Bug B): a floor torch on a bottom slab resting on a PLAIN (non-lowered) TOP
    // carrier must read -0.5 (the bottom slab's own lowering of the object) and NOT gain the extra -0.5
    // a *lowered* TOP carrier adds (which would be -1.0). Isolates the exact -0.5 delta the fix contributes.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void floorTorchOnBottomSlabOverPlainTopSlabIsNotDoubleLowered(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos plainTopSlab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 84);
        BlockPos bottomSlabOnTop = plainTopSlab.up();
        BlockPos torch = bottomSlabOnTop.up();

        w.setBlockState(plainTopSlab, Blocks.BIRCH_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP), Block.NOTIFY_LISTENERS);
        w.setBlockState(bottomSlabOnTop, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        w.setBlockState(torch, Blocks.TORCH.getDefaultState(), Block.NOTIFY_LISTENERS);

        double torchDy = SlabSupport.getYOffset(w, torch, w.getBlockState(torch));
        ctx.assertTrue(Math.abs(torchDy + 0.5) <= EPS,
                "Bug B regression: a floor torch on a bottom slab over a PLAIN (non-lowered) top slab must "
                        + "read -0.5 (bottom-slab lowering only), NOT -1.0 — the widening must fire ONLY for a "
                        + "*lowered* TOP carrier; got " + torchDy);
        ctx.complete();
    }

    // Shared Bug B fixture: an anchored dirt lowers a same-level carrier slab (TOP or DOUBLE) via side
    // adjacency; a vanilla bottom slab rests on the lowered carrier; a floor torch stands on it. Returns
    // the torch's dy.
    private static double floorTorchDyOverLoweredCarrier(TestContext ctx, SlabType carrierType, int zOff) {
        ServerWorld w = ctx.getWorld();
        BlockPos carrier = loweredCarrier(w, ctx, zOff, Blocks.BIRCH_SLAB, carrierType);
        ctx.assertTrue(SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, carrier, w.getBlockState(carrier)),
                "setup: the " + carrierType + " carrier must be lowered/persistent");
        double carrierDy = SlabSupport.getYOffset(w, carrier, w.getBlockState(carrier));
        ctx.assertTrue(Math.abs(carrierDy + 0.5) <= EPS,
                "setup: the " + carrierType + " carrier must render -0.5; got " + carrierDy);

        BlockPos bottomSlabOnCarrier = carrier.up();
        w.setBlockState(bottomSlabOnCarrier, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        double bottomSlabDy = SlabSupport.getYOffset(w, bottomSlabOnCarrier, w.getBlockState(bottomSlabOnCarrier));
        ctx.assertTrue(Math.abs(bottomSlabDy + 0.5) <= EPS,
                "setup: the bottom slab resting on the lowered " + carrierType + " carrier must itself render "
                        + "-0.5 (getYOffsetInner already recognizes TOP-like carriers since Phase 2); got " + bottomSlabDy);

        BlockPos torch = bottomSlabOnCarrier.up();
        w.setBlockState(torch, Blocks.TORCH.getDefaultState(), Block.NOTIFY_LISTENERS);
        return SlabSupport.getYOffset(w, torch, w.getBlockState(torch));
    }
}
