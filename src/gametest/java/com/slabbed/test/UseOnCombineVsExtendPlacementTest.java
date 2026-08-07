package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/**
 * Placement-path (REAL {@code useOn}) coverage for the slab combine-vs-extend decision on
 * lowered targets — the gap every prior test on this branch left open: the matrix/fixture
 * suites build states via {@code setBlockState} and never exercise
 * {@code SlabBlock.canReplace} / {@code BlockItem.useOnBlock}, so the WYSIWYG remap in
 * {@code BlockItemPlacementIntentMixin#slabbed$remapLoweredFullBlockSideHit} had ZERO
 * placement-path pins.
 *
 * <p>Mechanism: a mock survival player holding an oak slab drives
 * {@code ItemStack.useOnBlock(ItemUsageContext)} with a hand-built {@link BlockHitResult}
 * (the same headless-useOn shape as the 26.1.2 {@code Slabbed2612UseOnPlacementTest} and the
 * Forge 1.20.1 {@code cantileveredTopSlabExtendsSidewaysInsteadOfCombining} harness,
 * re-derived for Yarn/Fabric 1.21.11). This runs the full vanilla decision chain — the
 * intent-mixin remap, {@code ItemPlacementContext} construction, {@code SlabBlock.canReplace}
 * (combine vs extend), {@code getPlacementState} (TOP/BOTTOM discriminator) — not a synthetic
 * state write.
 *
 * <p>Vanilla bug class under WYSIWYG lowering: {@code SlabBlock.canReplace} discriminates on
 * the RAW hit fraction {@code hit.y - clickedPos.y > 0.5}. A vanilla TOP slab rendered
 * lowered -0.5 is VISIBLE in the lower half of its cell, so an honest click on its visible
 * side face carries a raw fraction of ~0.4 — vanilla reads "lower half" and COMBINES the cell
 * into a DOUBLE slab, while the player visibly aimed at the (nominal) top half and wanted to
 * EXTEND the walkway sideways. The intent mixin fixes this at dy == -0.5 by remapping the hit
 * Y to the visible half before the {@code ItemPlacementContext} is built.
 *
 * <p>Cells here are the GREEN (passing-on-HEAD) lanes:
 * <ul>
 *   <li>control: an honest vanilla combine gesture (top face of a flat BOTTOM slab) still
 *       combines — proves the harness drives real placement AND that the remap does not
 *       over-extend un-lowered targets;</li>
 *   <li>-0.5 lane: side click on the visible face of a -0.5-lowered TOP slab EXTENDS
 *       sideways (stays TYPE=TOP, adjacent cell gains a slab) instead of combining.</li>
 * </ul>
 *
 * <p>The -1.0 lane (vanilla TOP slab on a Terrain Slabs bottom, visual dy = -1.0, see
 * {@code OffsetRaycastTargetingTest#vanillaTopSlabOnTerrainLowersFull}) is intentionally NOT
 * here: the intent-mixin gate is {@code getVisualYOffset == -0.5d} exactly, so the raw-fraction
 * misdecision persists there and the cell is RED on HEAD. It lives UNREGISTERED in
 * {@link UseOnMinusOneLoweredCombineVsExtendRedTest} until the maintainer rules on the -1.0 gate fix.
 */
public final class UseOnCombineVsExtendPlacementTest {

    // ── headless useOn harness (package-visible: the unregistered RED -1.0 cell
    //    in UseOnMinusOneLoweredCombineVsExtendRedTest reuses it) ───────────
    static PlayerEntity mockSlabPlayer(TestContext ctx, BlockPos standAbs) {
        PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
        player.refreshPositionAndAngles(standAbs.getX() + 0.5, standAbs.getY(), standAbs.getZ() + 0.5, 0.0f, 0.0f);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Blocks.OAK_SLAB.asItem(), 16));
        return player;
    }

    /** Fires the REAL useOn chain (BlockItem.useOnBlock → intent mixin → canReplace → place). */
    static ActionResult useHeldOakSlab(ServerWorld world, PlayerEntity player,
                                       BlockPos clickAbs, Direction face, Vec3d hitAbs) {
        ItemStack held = player.getStackInHand(Hand.MAIN_HAND);
        BlockHitResult hit = new BlockHitResult(hitAbs, face, clickAbs, false);
        return held.useOnBlock(new ItemUsageContext(world, player, Hand.MAIN_HAND, held, hit));
    }

    static String describe(ServerWorld world, BlockPos pos) {
        BlockState s = world.getBlockState(pos);
        String base = s.getBlock().toString();
        if (s.contains(SlabBlock.TYPE)) {
            base += "[" + s.get(SlabBlock.TYPE) + "]";
        }
        return base + " dy=" + SlabSupport.getYOffset(world, pos, s);
    }

    static void row(String cfg, ServerWorld world, BlockPos clicked, BlockPos side, ActionResult result) {
        System.out.println("[USEON] " + cfg
                + " | clicked=" + describe(world, clicked)
                + " | side=" + describe(world, side)
                + " | result=" + result);
    }

    // ── control: honest combine gesture on a FLAT slab still combines ────
    // Bottom slab on solid ground (dy 0.0), slab item, click the slab's top face centrally.
    // Vanilla law: BOTTOM + side==UP → canReplace true → the cell combines to DOUBLE.
    // The intent mixin must NOT divert this (visual dy is 0.0; central up-face hit is outside
    // the 0.20 edge band). Proves the harness reaches real placement and pins no-over-extend.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnFlatBottomSlabTopFaceStillCombinesToDouble(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        BlockPos ground = origin.add(1, 2, 1);
        world.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos slab = ground.up();
        world.setBlockState(slab, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabSupport.getYOffset(world, slab, world.getBlockState(slab)) == 0.0,
                "fixture: bottom slab on solid ground must be flat (dy 0.0)");

        PlayerEntity player = mockSlabPlayer(ctx, slab.north(3));
        Vec3d hit = new Vec3d(slab.getX() + 0.5, slab.getY() + 0.5, slab.getZ() + 0.5);
        ActionResult result = useHeldOakSlab(world, player, slab, Direction.UP, hit);
        row("control.flatBottomSlab.topFaceCombine", world, slab, slab.up(), result);

        ctx.assertTrue(result.isAccepted(),
                "control: useOn on a flat bottom slab's top face must place, got " + result);
        BlockState after = world.getBlockState(slab);
        ctx.assertTrue(after.isOf(Blocks.OAK_SLAB) && after.get(SlabBlock.TYPE) == SlabType.DOUBLE,
                "control: central top-face slab click on a FLAT bottom slab must COMBINE to DOUBLE "
                        + "(vanilla law preserved through the placement path), got " + describe(world, slab));
        ctx.complete();
    }

    // ── -0.5 lane: side click on a lowered TOP slab EXTENDS, not combines ─
    // Fixture (proven green on HEAD, same shape as objectOnLoweredTopOrDoubleSlabFollows):
    // bottom slab → stone (lowered -0.5) → vanilla TOP slab (renders -0.5, visible span
    // [Y, Y+0.5]). Click the TOP slab's west face on its VISIBLE geometry at raw fraction 0.4:
    // raw vanilla would read "lower half" → COMBINE (the bug); the intent-mixin remap
    // (gate: visual dy == -0.5) rewrites the hit to the visible half → canReplace false →
    // EXTEND into the adjacent cell, minted TYPE=TOP by the same remapped discriminator.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnLoweredTopSlabSideClickExtendsInsteadOfCombining(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        BlockPos base = origin.add(3, 2, 1);
        world.setBlockState(base, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos fb = base.up();
        world.setBlockState(fb, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos top = fb.up();
        world.setBlockState(top, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_LISTENERS);

        double dy = SlabSupport.getYOffset(world, top, world.getBlockState(top));
        ctx.assertTrue(dy == -0.5,
                "fixture: TOP slab on a lowered full block must render lowered -0.5, got " + dy);
        double visualDy = SlabSupport.getVisualYOffset(world, top, world.getBlockState(top));
        ctx.assertTrue(visualDy == -0.5,
                "fixture: intent-mixin gate input getVisualYOffset must read -0.5, got " + visualDy);

        BlockPos extendCell = top.west();
        ctx.assertTrue(world.getBlockState(extendCell).isAir(),
                "fixture: the extend target cell west of the lowered TOP slab must start as air");

        PlayerEntity player = mockSlabPlayer(ctx, top.west(3));
        // West face of the slab's cell, on the VISIBLE lowered geometry: raw fraction 0.4
        // (absolute Y + 0.4), dy-corrected fraction 0.9 — the exact raw-vs-visible flip case.
        Vec3d hit = new Vec3d(top.getX(), top.getY() + 0.4, top.getZ() + 0.5);
        ActionResult result = useHeldOakSlab(world, player, top, Direction.WEST, hit);
        row("minusHalf.loweredTopSlab.sideClick", world, top, extendCell, result);

        ctx.assertTrue(result.isAccepted(),
                "-0.5 lane: useOn on the lowered TOP slab's visible west face must place, got " + result);
        BlockState clickedAfter = world.getBlockState(top);
        ctx.assertTrue(clickedAfter.isOf(Blocks.OAK_SLAB) && clickedAfter.get(SlabBlock.TYPE) == SlabType.TOP,
                "-0.5 lane: the lowered TOP slab must NOT combine into a DOUBLE on a visible side "
                        + "click (WYSIWYG extend intent), got " + describe(world, top));
        BlockState extended = world.getBlockState(extendCell);
        ctx.assertTrue(extended.isOf(Blocks.OAK_SLAB),
                "-0.5 lane: the adjacent cell must gain the extended slab, got " + describe(world, extendCell));
        ctx.assertTrue(extended.get(SlabBlock.TYPE) == SlabType.TOP,
                "-0.5 lane: the extended slab must mint TYPE=TOP from the remapped (visible-half) "
                        + "discriminator, got " + describe(world, extendCell));
        ctx.complete();
    }
}
