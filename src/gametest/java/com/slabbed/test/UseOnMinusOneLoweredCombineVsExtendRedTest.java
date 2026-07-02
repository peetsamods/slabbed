package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * RED DIAGNOSTIC — deliberately NOT registered in {@code src/gametest/resources/fabric.mod.json}
 * (fabric-gametest entrypoints), so {@code runGameTest} stays green on this branch. This cell
 * FAILS on HEAD and documents a verified open gap; do not register it until the production
 * decision below is made.
 *
 * <p><b>The gap (code-verified + run-verified):</b> the WYSIWYG combine-vs-extend remap in
 * {@code BlockItemPlacementIntentMixin#slabbed$remapLoweredFullBlockSideHit} only fires when
 * the clicked slab's {@code SlabSupport.getVisualYOffset(...) == -0.5d} exactly
 * ({@code targetIsLoweredSlab}, and the general lane's {@code yOffset != -0.5d} early-return).
 * A vanilla TOP slab resting on a Terrain Slabs BOTTOM_LIKE surface renders at visual dy
 * <b>-1.0</b> (pinned by {@code OffsetRaycastTargetingTest#vanillaTopSlabOnTerrainLowersFull}),
 * so its placement path falls through UNREMAPPED to vanilla {@code SlabBlock.canReplace}, whose
 * RAW fraction discriminator ({@code hit.y - clickedPos.y > 0.5}) reads an honest click on the
 * visible geometry (raw fraction ~-0.1, dy-corrected ~0.9) as "lower half" → COMBINE: the cell
 * silently becomes a DOUBLE slab where the player visibly aimed to EXTEND sideways.
 *
 * <p><b>Sibling evidence this lane bites in practice:</b> forge1201 fixed the whole dy range
 * with a dedicated dy-corrected {@code canBeReplaced} mixin ({@code SlabCanBeReplacedDyMixin},
 * commit {@code e9a6f45d}, RED/GREEN-pinned by
 * {@code cantileveredTopSlabExtendsSidewaysInsteadOfCombining}); cleanpub (1.21.1) hit the -1.0
 * placement lane as a live RED and fixed it in {@code fc608690}. main1211 has no
 * {@code canReplace}/{@code canBeReplaced} mixin at all — the intent remap is the only
 * combine-vs-extend machinery and it is hard-gated to -0.5.
 *
 * <p><b>Production fix = Maintainer's decision (NOT made here):</b> either widen the intent-mixin
 * gate from {@code == -0.5d} to "any lowered dy" (remap Y against the actual visual offset,
 * not the hardcoded half), or port the forge1201 dy-corrected-fraction {@code canReplace}
 * behavior idiomatically. Both must keep the -0.5 lane
 * ({@code UseOnCombineVsExtendPlacementTest}) green.
 *
 * <p><b>To enable:</b> add {@code com.slabbed.test.UseOnMinusOneLoweredCombineVsExtendRedTest}
 * to the {@code fabric-gametest} entrypoint list in
 * {@code src/gametest/resources/fabric.mod.json}. Expected on current HEAD: FAIL with the
 * clicked cell reading {@code OAK_SLAB[DOUBLE]} and the extend cell still air.
 */
public final class UseOnMinusOneLoweredCombineVsExtendRedTest {

    // -1.0 lane: vanilla TOP slab on a Terrain Slabs bottom (visual dy -1.0, visible span
    // [Y-0.5, Y]). Click its west face on the VISIBLE geometry (raw fraction -0.1,
    // dy-corrected 0.9). WYSIWYG intent: EXTEND sideways, stay TYPE=TOP.
    // Current HEAD: no remap (gate == -0.5) → vanilla raw fraction → COMBINE → DOUBLE. RED.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnMinusOneLoweredTopSlabSideClickExtendsInsteadOfCombining(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        Block ts = Registries.BLOCK.get(Identifier.of("terrainslabs", "grass_slab"));
        ctx.assertTrue(ts != Blocks.AIR, "fixture: Terrain Slabs loaded");
        BlockState tsBottom = ts.getDefaultState();
        if (tsBottom.contains(SlabBlock.TYPE)) {
            tsBottom = tsBottom.with(SlabBlock.TYPE, SlabType.BOTTOM);
        }

        BlockPos tsPos = origin.add(3, 2, 3);
        world.setBlockState(tsPos, tsBottom, Block.NOTIFY_LISTENERS);
        BlockPos top = tsPos.up();
        world.setBlockState(top, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_LISTENERS);

        double dy = SlabSupport.getYOffset(world, top, world.getBlockState(top));
        ctx.assertTrue(dy == -1.0,
                "fixture: vanilla TOP slab on a Terrain Slabs bottom must render -1.0 "
                        + "(vanillaTopSlabOnTerrainLowersFull lane), got " + dy);
        double visualDy = SlabSupport.getVisualYOffset(world, top, world.getBlockState(top));
        ctx.assertTrue(visualDy == -1.0,
                "fixture: intent-mixin gate input getVisualYOffset must read -1.0, got " + visualDy);

        BlockPos extendCell = top.west();
        ctx.assertTrue(world.getBlockState(extendCell).isAir(),
                "fixture: the extend target cell west of the -1.0 slab must start as air");

        PlayerEntity player = UseOnCombineVsExtendPlacementTest.mockSlabPlayer(ctx, top.west(3));
        // Visible span is [Y-0.5, Y]; hit its upper region: absolute Y - 0.1 → raw fraction
        // -0.1 (vanilla reads "lower half" → combine), dy-corrected fraction 0.9 (visible
        // upper half → extend intent). BlockHitResult still targets the slab's own cell,
        // exactly as the offset-aware raycast resolves side hits on lowered visuals.
        Vec3d hit = new Vec3d(top.getX(), top.getY() - 0.1, top.getZ() + 0.5);
        ActionResult result = UseOnCombineVsExtendPlacementTest.useHeldOakSlab(
                world, player, top, Direction.WEST, hit);
        UseOnCombineVsExtendPlacementTest.row("minusOne.loweredTopSlab.sideClick", world, top, extendCell, result);

        ctx.assertTrue(result.isAccepted(),
                "-1.0 lane: useOn on the -1.0 slab's visible west face must place, got " + result);
        BlockState clickedAfter = world.getBlockState(top);
        ctx.assertTrue(clickedAfter.isOf(Blocks.OAK_SLAB) && clickedAfter.get(SlabBlock.TYPE) == SlabType.TOP,
                "-1.0 lane: the -1.0-lowered TOP slab must NOT combine into a DOUBLE on a visible "
                        + "side click (WYSIWYG extend intent; intent-mixin gate is == -0.5 so the "
                        + "raw-fraction misdecision persists here), got "
                        + UseOnCombineVsExtendPlacementTest.describe(world, top));
        BlockState extended = world.getBlockState(extendCell);
        ctx.assertTrue(extended.isOf(Blocks.OAK_SLAB),
                "-1.0 lane: the adjacent cell must gain the extended slab, got "
                        + UseOnCombineVsExtendPlacementTest.describe(world, extendCell));
        ctx.complete();
    }
}
