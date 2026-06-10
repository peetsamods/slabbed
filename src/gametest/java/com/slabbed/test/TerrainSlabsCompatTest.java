package com.slabbed.test;

import com.slabbed.compat.CompatHooks;
import com.slabbed.compat.CompatSlabSurfaceKind;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * Headless Terrain Slabs compat tests — exercise the named-surface direct-support path
 * against the {@link TerrainSlabsTestShim} {@code terrain_slabs:test_slab} (the gametest mod
 * provides "terrain_slabs", so the compat fires exactly as with the real mod).
 *
 * <p>The compat goal is PARITY: an object on a Terrain Slabs BOTTOM_LIKE surface must behave
 * exactly like the same object on a vanilla bottom slab. So most tests assert
 * {@code getYOffset(on TS) == getYOffset(on vanilla bottom slab)} rather than a hard-coded
 * value — that is the precise contract and is robust to per-category dy rules.
 */
public final class TerrainSlabsCompatTest {

    private static final double EPS = 1.0e-6;

    private static BlockState tsBottomSlab() {
        return TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static BlockState vanillaBottomSlab() {
        return Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static BlockState tsTopSlab() {
        return TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);
    }

    private static BlockState tsDoubleSlab() {
        return TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE);
    }

    private static double dyOnSupport(ServerWorld w, BlockPos supportPos, BlockState support, BlockState object) {
        w.setBlockState(supportPos, support, Block.NOTIFY_LISTENERS);
        BlockPos objPos = supportPos.up();
        w.setBlockState(objPos, object, Block.NOTIFY_LISTENERS);
        return SlabSupport.getYOffset(w, objPos, w.getBlockState(objPos));
    }

    /** Asserts the object's dy on a TS surface equals its dy on a vanilla bottom slab. */
    private static void assertParity(TestContext ctx, BlockState object, String what) {
        ServerWorld w = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        double tsDy = dyOnSupport(w, origin.add(3, 2, 3), tsBottomSlab(), object);
        double vanillaDy = dyOnSupport(w, origin.add(6, 2, 3), vanillaBottomSlab(), object);
        ctx.assertTrue(Math.abs(tsDy - vanillaDy) <= EPS,
                what + ": dy on Terrain Slabs (" + tsDy + ") must match vanilla bottom slab (" + vanillaDy + ")");
        ctx.complete();
    }

    // 0. Shim sanity — the test slab classifies as BOTTOM_LIKE (proves the harness wiring).
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void shimClassifiesAsBottomLike(TestContext ctx) {
        CompatSlabSurfaceKind kind = CompatHooks.customSlabSurfaceKind(tsBottomSlab());
        ctx.assertTrue(kind == CompatSlabSurfaceKind.BOTTOM_LIKE,
                "shim terrain_slabs:test_slab[type=bottom] must classify BOTTOM_LIKE, got " + kind);
        ctx.complete();
    }

    // 1. PLACEMENT keystone — a TS bottom slab must be a placeable solid top + direct support
    //    surface (this is what lets lanterns/torches/etc. be PLACED on it).
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void tsSlabIsPlaceableSupport(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(slab, tsBottomSlab(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabSupport.isDirectObjectSupportSurface(w, slab, w.getBlockState(slab)),
                "TS bottom slab must be a direct object support surface");
        ctx.assertTrue(SlabSupport.canTreatAsSolidTopFace(w, slab),
                "TS bottom slab top must count as solid support (so objects can be placed on it)");
        ctx.complete();
    }

    // 2-5. dy parity per object category.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void fullBlockParity(TestContext ctx) { assertParity(ctx, Blocks.STONE.getDefaultState(), "full block (stone)"); }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void lanternParity(TestContext ctx) { assertParity(ctx, Blocks.LANTERN.getDefaultState(), "standing lantern"); }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void torchParity(TestContext ctx) { assertParity(ctx, Blocks.TORCH.getDefaultState(), "floor torch"); }

    // Redstone wire lowers -0.5 on a Terrain Slabs slab (parity with 1.21.11, and with a
    // vanilla bottom slab). Now owned by the directCustom lane (redstone is a non-solid
    // slab-sit subject). This pins the parity so a future lane change can't silently regress it.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void redstoneWireLowersOnTs(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(base, tsBottomSlab(), Block.NOTIFY_LISTENERS);
        // Pass the redstone state explicitly (the render path computes dy from the state, not
        // the placed world block — and redstone's strict survival check isn't the subject here).
        BlockState redstone = Blocks.REDSTONE_WIRE.getDefaultState();
        double dy = SlabSupport.getYOffset(w, base.up(), redstone);
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "redstone state on a Terrain Slabs slab should lower -0.5 (got " + dy + ")");
        ctx.complete();
    }

    // 6. Carpet uses the client-side ClientDy.dyFor path (gated on hasBottomSlabBelow), not
    //    getYOffset — so assert the server-side condition that path keys off.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void carpetSupportRecognized(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(slab, tsBottomSlab(), Block.NOTIFY_LISTENERS);
        BlockPos carpetPos = slab.up();
        w.setBlockState(carpetPos, Blocks.WHITE_CARPET.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabSupport.hasBottomSlabBelow(w, carpetPos),
                "carpet over a TS bottom slab must see a bottom-slab-equivalent below (ClientDy lowers it -0.5)");
        ctx.complete();
    }

    // ── Combining / mixed-slab compound (terrain + vanilla) ───────────────────────────
    private static BlockState vanillaSlab(SlabType type) {
        return Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, type);
    }

    /** Places layers bottom-up from base; returns getYOffset of the top (sitter) layer. */
    private static double stackDy(ServerWorld w, BlockPos base, BlockState... layers) {
        BlockPos p = base;
        for (BlockState layer : layers) {
            w.setBlockState(p, layer, Block.NOTIFY_LISTENERS);
            p = p.up();
        }
        BlockPos top = base.up(layers.length - 1);
        return SlabSupport.getYOffset(w, top, w.getBlockState(top));
    }

    // Contract #3: a vanilla BOTTOM slab on a Terrain Slabs slab combines flush (-0.5).
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void vanillaBottomSlabCombinesOnTs(TestContext ctx) {
        double dy = stackDy(ctx.getWorld(), ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3),
                tsBottomSlab(), vanillaSlab(SlabType.BOTTOM));
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "vanilla bottom slab on a Terrain Slabs slab should combine flush at -0.5, got " + dy);
        ctx.complete();
    }

    // Contract #5: a vanilla TOP slab on a Terrain Slabs slab compounds to -1.0.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void vanillaTopSlabCompoundsOnTs(TestContext ctx) {
        double dy = stackDy(ctx.getWorld(), ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3),
                tsBottomSlab(), vanillaSlab(SlabType.TOP));
        ctx.assertTrue(Math.abs(dy + 1.0) <= EPS,
                "vanilla TOP slab on a Terrain Slabs slab should compound to -1.0, got " + dy);
        ctx.complete();
    }

    // DODO fix: a perpendicular slab placed beside the TOP of a 2-block stack on a TS slab must
    // inherit the lowering (not sit +0.5 high leaving a see-through gap).
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void sideSlabBesideTsStackLowers(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(base, tsBottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(base.up(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(base.up(2), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos sidePos = base.up(2).east();
        w.setBlockState(sidePos, vanillaSlab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        double dy = SlabSupport.getYOffset(w, sidePos, w.getBlockState(sidePos));
        ctx.assertTrue(dy < -1.0e-6,
                "side slab beside a 2-block stack on a Terrain Slab must lower (no +0.5 DODO), got dy=" + dy);
        ctx.complete();
    }

    // Fence "split" fix: a fence lowered onto a Terrain Slab and a ground fence at the same Y
    // sit at different visual heights, so they must read as a stepped pair (connection broken).
    // Two ground fences at the same height must NOT (control).
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void fenceConnectionBreaksAcrossTsStep(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos groundBase = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(groundBase, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos groundFence = groundBase.up();
        w.setBlockState(groundFence, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);

        // Adjacent column: a TS slab + a fence lowered onto it (same Y as the ground fence).
        BlockPos tsBase = groundBase.east();
        w.setBlockState(tsBase, tsBottomSlab(), Block.NOTIFY_LISTENERS);
        BlockPos loweredFence = tsBase.up();
        w.setBlockState(loweredFence, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);

        ctx.assertTrue(
                SlabSupport.isSteppedConnectingNeighbor(w, groundFence, w.getBlockState(groundFence),
                        loweredFence, w.getBlockState(loweredFence)),
                "a ground fence and a fence lowered onto a TS slab (same Y) must be a stepped pair");

        // Control: a second ground fence at the same height must NOT be stepped.
        BlockPos groundBase2 = groundBase.west();
        w.setBlockState(groundBase2, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos groundFence2 = groundBase2.up();
        w.setBlockState(groundFence2, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(
                !SlabSupport.isSteppedConnectingNeighbor(w, groundFence, w.getBlockState(groundFence),
                        groundFence2, w.getBlockState(groundFence2)),
                "two ground fences at the same height must NOT be a stepped pair");
        ctx.complete();
    }

    // PROBE: a bottom slab sitting on TOP of a 2-block stack on a TS slab — does it render lowered
    // (flush) or at full height (the +0.5 DODO over the placement)? Logs the actual dy.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void slabOnTopOfTsStackDyProbe(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(base, tsBottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(base.up(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(base.up(2), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos topSlabPos = base.up(3);
        w.setBlockState(topSlabPos, vanillaSlab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        double topStoneDy = SlabSupport.getYOffset(w, base.up(2), w.getBlockState(base.up(2)));
        double slabDy = SlabSupport.getYOffset(w, topSlabPos, w.getBlockState(topSlabPos));
        // Explicit values (NOT just equality — equal-at-zero would pass vacuously).
        ctx.assertTrue(Math.abs(topStoneDy + 0.5) <= EPS,
                "top stone of a 2-stack on a TS slab must be -0.5, got " + topStoneDy);
        ctx.assertTrue(Math.abs(slabDy + 0.5) <= EPS,
                "bottom slab on top of a TS-lowered stack must be -0.5 (flush), got " + slabDy
                        + " (topStoneDy=" + topStoneDy + ")");
        ctx.complete();
    }

    // ── CHARACTERIZATION: TOP_LIKE / DOUBLE_LIKE surfaces + the skip-offset asymmetry ──────
    // These pin CURRENT behavior (green tripwires). Several encode the KNOWN GAP that a
    // Terrain Slabs block is a support surface but is itself never lowered (shouldSkipOffset).
    // When the asymmetry is resolved (see SLABBED_YSYSTEM_DESIGN.md), flip the asserts.

    // Sanity: shim TOP/DOUBLE classify as TOP_LIKE / DOUBLE_LIKE.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void shimTopAndDoubleClassify(TestContext ctx) {
        ctx.assertTrue(CompatHooks.customSlabSurfaceKind(tsTopSlab()) == CompatSlabSurfaceKind.TOP_LIKE,
                "TS top slab must classify TOP_LIKE");
        ctx.assertTrue(CompatHooks.customSlabSurfaceKind(tsDoubleSlab()) == CompatSlabSurfaceKind.DOUBLE_LIKE,
                "TS double slab must classify DOUBLE_LIKE");
        ctx.complete();
    }

    // A full block sitting ON a TS TOP_LIKE surface is NOT lowered (top surface is full height).
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void fullBlockOnTsTopLikeNotLowered(TestContext ctx) {
        double dy = dyOnSupport(ctx.getWorld(), ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3),
                tsTopSlab(), Blocks.STONE.getDefaultState());
        ctx.assertTrue(Math.abs(dy) <= EPS,
                "stone on a TS TOP_LIKE slab should sit at full height (dy 0), got " + dy);
        ctx.complete();
    }

    // A full block sitting ON a TS DOUBLE_LIKE surface is NOT lowered (full cube support).
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void fullBlockOnTsDoubleLikeNotLowered(TestContext ctx) {
        double dy = dyOnSupport(ctx.getWorld(), ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3),
                tsDoubleSlab(), Blocks.STONE.getDefaultState());
        ctx.assertTrue(Math.abs(dy) <= EPS,
                "stone on a TS DOUBLE_LIKE slab should sit at full height (dy 0), got " + dy);
        ctx.complete();
    }

    // KNOWN GAP (skip-offset asymmetry): a TS slab placed ON a lowered support (vanilla bottom
    // slab) does NOT inherit the lowering — it stays at dy 0 and floats (this is the "DODO"
    // Julia saw with a Packed Mud TOP_LIKE slab). Pinned so a fix is a deliberate, visible flip.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void tsTopSlabOnLoweredSupportStaysFlush_KNOWN_GAP(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(base, vanillaBottomSlab(), Block.NOTIFY_LISTENERS);   // lowering support
        BlockPos tsPos = base.up();
        w.setBlockState(tsPos, tsTopSlab(), Block.NOTIFY_LISTENERS);
        double dy = SlabSupport.getYOffset(w, tsPos, w.getBlockState(tsPos));
        ctx.assertTrue(Math.abs(dy) <= EPS,
                "KNOWN GAP: TS slab is skipOffset, so on a lowered support it stays dy 0 (got " + dy
                        + "). Flip when the asymmetry is resolved.");
        ctx.complete();
    }

    // Companion: a VANILLA full block on the same lowered support DOES lower — proving the gap is
    // specifically the TS skip-offset exclusion, not the support failing to lower.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void vanillaBlockOnSameLoweredSupportDoesLower(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(base, vanillaBottomSlab(), Block.NOTIFY_LISTENERS);
        BlockPos objPos = base.up();
        w.setBlockState(objPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        double dy = SlabSupport.getYOffset(w, objPos, w.getBlockState(objPos));
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "control: vanilla stone on a bottom slab lowers -0.5 (got " + dy + ")");
        ctx.complete();
    }

    // Contract #2: an object on a MIXED slab (vanilla bottom slab capping a TS slab) compounds to -1.0.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void objectOnMixedTsSlabCompounds(TestContext ctx) {
        double dy = stackDy(ctx.getWorld(), ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3),
                tsBottomSlab(), vanillaSlab(SlabType.BOTTOM), Blocks.LANTERN.getDefaultState());
        ctx.assertTrue(Math.abs(dy + 1.0) <= EPS,
                "lantern on a mixed (vanilla-on-terrain) slab should compound to -1.0, got " + dy);
        ctx.complete();
    }

    // BUG 1 fix: an object on a vanilla TOP slab capping a Terrain Slab must compound to -1.0 (the
    // TOP slab itself compounds to -1.0; the object on its lowered top surface follows it down).
    // Was -0.5 (floating 0.5 above) because loweredBottomSlabSupportDy ignored TOP-slab supports.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void lanternOnVanillaTopSlabOnTsCompounds(TestContext ctx) {
        double dy = stackDy(ctx.getWorld(), ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3),
                tsBottomSlab(), vanillaSlab(SlabType.TOP), Blocks.LANTERN.getDefaultState());
        ctx.assertTrue(Math.abs(dy + 1.0) <= EPS,
                "lantern on a vanilla TOP slab capping a Terrain Slab should compound to -1.0 (BUG 1), got " + dy);
        ctx.complete();
    }

    // Vegetation double-offset fix: Terrain Slabs already lowers its own vegetation on slabs, so
    // Slabbed must NOT also lower it (otherwise generated grass clips into the slab). dy stays 0.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void vegetationNotLoweredWhenTerrainSlabsHandlesIt(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(base, tsBottomSlab(), Block.NOTIFY_LISTENERS);
        BlockPos grassPos = base.up();
        w.setBlockState(grassPos, Blocks.SHORT_GRASS.getDefaultState(), Block.NOTIFY_LISTENERS);
        double dy = SlabSupport.getYOffset(w, grassPos, w.getBlockState(grassPos));
        ctx.assertTrue(Math.abs(dy) <= EPS,
                "short grass on a TS slab must NOT be lowered by Slabbed (TS handles it), got " + dy);
        ctx.complete();
    }

    // Fence-side fix (Julia's exact geometry): a fence post on a TS slab is lowered; a slab placed
    // beside the UPPER fence in the stack must inherit -0.5 (flush), not float.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void slabBesideStackedFenceOnTsInheritsLowering(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(base, tsBottomSlab(), Block.NOTIFY_LISTENERS);          // Packed-Mud-like TS slab
        BlockPos fenceLow = base.up();
        BlockPos fenceHigh = base.up(2);
        w.setBlockState(fenceLow, Blocks.SPRUCE_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(fenceHigh, Blocks.SPRUCE_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);
        double upperFenceDy = SlabSupport.getYOffset(w, fenceHigh, w.getBlockState(fenceHigh));
        ctx.assertTrue(upperFenceDy < -1.0e-6,
                "fixture: upper fence on a TS-slab column should be lowered, got " + upperFenceDy);
        BlockPos slabPos = fenceHigh.east();
        w.setBlockState(slabPos, vanillaSlab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        double slabDy = SlabSupport.getYOffset(w, slabPos, w.getBlockState(slabPos));
        ctx.assertTrue(Math.abs(slabDy + 0.5) <= EPS,
                "slab beside the upper fence (on a TS-slab column) should inherit -0.5, got " + slabDy);
        ctx.complete();
    }

    // Powder-snow DODO fix: powder snow is a FULL CUBE, so it matched the full-block-on-slab
    // lowering branch and Slabbed dropped it -0.5 while neighbouring powder snow stayed flush —
    // a half-block step across snowy terrain. Powder snow (PowderSnowBlock, NOT a SnowBlock) is
    // natural terrain fill and must NEVER be offset: dy stays 0. Control: a stone block on the
    // same slab IS lowered, proving the support is active (so the powder-snow pass isn't vacuous).
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void powderSnowOnSlabIsNeverLowered(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(base, vanillaSlab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        // control: an ordinary full block on the same bottom slab IS lowered -0.5
        BlockPos stonePos = base.up();
        w.setBlockState(stonePos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        double stoneDy = SlabSupport.getYOffset(w, stonePos, w.getBlockState(stonePos));
        ctx.assertTrue(stoneDy < -1.0e-6,
                "fixture: a full block on a bottom slab should be lowered, got " + stoneDy);
        // powder snow on the same kind of slab must NOT be lowered
        BlockPos snowBase = base.east(2);
        w.setBlockState(snowBase, vanillaSlab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        BlockPos powderPos = snowBase.up();
        w.setBlockState(powderPos, Blocks.POWDER_SNOW.getDefaultState(), Block.NOTIFY_LISTENERS);
        double powderDy = SlabSupport.getYOffset(w, powderPos, w.getBlockState(powderPos));
        ctx.assertTrue(Math.abs(powderDy) <= EPS,
                "powder snow on a slab must NOT be lowered (it is terrain fill), got " + powderDy);
        ctx.complete();
    }

    // Hanger-inherits-from-below fix (Bug 1): hanging roots hang from the block ABOVE. They lack
    // the HANGING property (lanterns have it), so they used to fall through to hasSlabInColumn,
    // which walks DOWN — and a lantern placed beneath them bridges that walk to a slab further
    // down, wrongly lowering the roots -0.5. With a FLUSH support above, the roots must stay flush
    // regardless of what is below.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void hangingRootsNotLoweredByBridgedSlabBelow(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(base.up(2), tsBottomSlab(), Block.NOTIFY_LISTENERS);   // flush support ABOVE
        BlockPos rootsPos = base.up();
        w.setBlockState(rootsPos, Blocks.HANGING_ROOTS.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(base, Blocks.LANTERN.getDefaultState(), Block.NOTIFY_LISTENERS);  // bridges the down-walk
        w.setBlockState(base.down(), tsBottomSlab(), Block.NOTIFY_LISTENERS);  // slab the walk would otherwise find
        double dy = SlabSupport.getYOffset(w, rootsPos, w.getBlockState(rootsPos));
        ctx.assertTrue(Math.abs(dy) <= EPS,
                "hanging roots hang from the flush slab ABOVE; a lantern-bridged slab BELOW must NOT lower them, got " + dy);
        ctx.complete();
    }

    // Sign-smoosh fix (Bug 2) regression guard: a hanging sign under a NORMAL (non-lowered) top
    // slab still gets the +0.5 raised-attach baseline. Adding HangingSignBlock to the lowered-
    // support follow set must not disturb this common case. (The lowered-top-slab case — where the
    // sign should combine supportDy + 0.5 to sit flush instead of +0.5 smooshing up — needs side/
    // anchor lowering that setBlockState can't build, and is verified live.)
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void hangingSignUnderNormalTopSlabKeepsRaisedBaseline(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(base.up(), vanillaSlab(SlabType.TOP), Block.NOTIFY_LISTENERS);
        BlockPos signPos = base;
        w.setBlockState(signPos, Blocks.WARPED_HANGING_SIGN.getDefaultState(), Block.NOTIFY_LISTENERS);
        double dy = SlabSupport.getYOffset(w, signPos, w.getBlockState(signPos));
        ctx.assertTrue(Math.abs(dy - 0.5) <= EPS,
                "hanging sign under a normal top slab keeps the +0.5 raised-attach baseline, got " + dy);
        ctx.complete();
    }

}
