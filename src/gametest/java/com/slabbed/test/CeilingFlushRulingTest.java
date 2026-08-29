package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Port of the flush-ceiling ruling coverage (maintainer ruling, 2026-07-03; adopted on this
 * line 2026-08-16): the +0.5 "reach-up" for ceiling-attached objects under a FLUSH top slab
 * is deprecated — everything hangs flush. The production choke point is
 * {@code SlabSupport#isLoweringTopLikeCeiling}, the one shared predicate for every
 * dy-computing ceiling walk, so the ruling can never be applied to one walk and forgotten
 * on the others.
 *
 * <p>What deliberately SURVIVES the ruling, pinned by the -0.5 control rows here: the
 * lowered-top-slab merge COMPENSATION ({@code slabDy + 0.5}, netting &lt;= 0.0 — flush
 * against the lowered underside, not a reach-up).
 */
@GameTestHolder("fabric-gametest-api-v1")
@PrefixGameTestTemplate(false)
public final class CeilingFlushRulingTest {
    private static final String TEMPLATE = "empty";
    private static final double EPS = 1.0e-6;

    /** Walk B (direct): a flush TOP slab appearing above a placed TOP trapdoor must not move it. */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void topTrapdoorStaysFlushWhenTopSlabPlacedAbove(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos wall = ctx.absolutePos(new BlockPos(2, 2, 3));
        BlockPos trapdoor = ctx.absolutePos(new BlockPos(2, 2, 2));
        world.setBlock(wall, Blocks.STONE.defaultBlockState(), 2);
        placeTopTrapdoor(ctx, world, wall, trapdoor);
        assertDy(ctx, world, trapdoor, 0.0, "premise: the placed trapdoor starts flush");
        world.setBlock(trapdoor.above(), topSlab(), Block.UPDATE_ALL);
        assertDy(ctx, world, trapdoor, 0.0,
                "flush ruling (walk B): a flush top slab placed above must NOT reach the trapdoor +0.5 up");
        ctx.succeed();
    }

    /** Walk C (cascading): the lower of two stacked TOP trapdoors under a flush top slab stays flush. */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void cascadedTopTrapdoorStaysFlushUnderTopSlab(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos wallLow = ctx.absolutePos(new BlockPos(2, 2, 3));
        BlockPos wallHigh = ctx.absolutePos(new BlockPos(2, 3, 3));
        BlockPos lower = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos upper = ctx.absolutePos(new BlockPos(2, 3, 2));
        world.setBlock(wallLow, Blocks.STONE.defaultBlockState(), 2);
        world.setBlock(wallHigh, Blocks.STONE.defaultBlockState(), 2);
        placeTopTrapdoor(ctx, world, wallLow, lower);
        placeTopTrapdoor(ctx, world, wallHigh, upper);
        assertDy(ctx, world, lower, 0.0, "premise: the lower trapdoor starts flush");
        world.setBlock(upper.above(), topSlab(), Block.UPDATE_ALL);
        assertDy(ctx, world, lower, 0.0,
                "flush ruling (walk C): the flush top slab above the cascade must not reach the lower trapdoor up");
        assertDy(ctx, world, upper, 0.0,
                "flush ruling (walk B via cascade scene): the upper trapdoor must stay flush too");
        ctx.succeed();
    }

    /** Walk A (always-hung family): hanging roots under a flush top slab hang flush, not +0.5. */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void hangingRootsUnderFlushTopSlabStayFlush(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos slab = ctx.absolutePos(new BlockPos(2, 3, 2));
        BlockPos roots = slab.below();
        world.setBlock(slab, topSlab(), 2);
        place(ctx, new ItemStack(Items.HANGING_ROOTS), slab, Direction.DOWN, 0.0);
        ctx.assertTrue(world.getBlockState(roots).is(Blocks.HANGING_ROOTS),
                "scene premise: hanging roots must place under the top slab, got " + world.getBlockState(roots));
        assertDy(ctx, world, roots, 0.0,
                "flush ruling (walk A): roots under a FLUSH top slab hang flush, not reached +0.5 up");
        ctx.succeed();
    }

    /** Ceiling lever: a top/double slab underside is sturdy here, so ceiling levers genuinely survive. */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void ceilingLeverUnderFlushTopSlabStaysFlush(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos slab = ctx.absolutePos(new BlockPos(2, 3, 2));
        BlockPos lever = slab.below();
        world.setBlock(slab, topSlab(), 2);
        place(ctx, new ItemStack(Items.LEVER), slab, Direction.DOWN, 0.0);
        ctx.assertTrue(world.getBlockState(lever).is(Blocks.LEVER),
                "scene premise: the lever must place under the top slab, got " + world.getBlockState(lever));
        assertDy(ctx, world, lever, 0.0,
                "flush ruling: a ceiling lever under a FLUSH top slab hangs flush, not +0.5");
        ctx.succeed();
    }

    /** Ceiling button — same family, same ruling. */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void ceilingButtonUnderFlushTopSlabStaysFlush(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos slab = ctx.absolutePos(new BlockPos(2, 3, 2));
        BlockPos button = slab.below();
        world.setBlock(slab, topSlab(), 2);
        place(ctx, new ItemStack(Items.STONE_BUTTON), slab, Direction.DOWN, 0.0);
        ctx.assertTrue(world.getBlockState(button).is(Blocks.STONE_BUTTON),
                "scene premise: the button must place under the top slab, got " + world.getBlockState(button));
        assertDy(ctx, world, button, 0.0,
                "flush ruling: a ceiling button under a FLUSH top slab hangs flush, not +0.5");
        ctx.succeed();
    }

    /**
     * The BREAK direction of the old symptom ("jump on break"): dy must stay 0.0 through the whole
     * slab lifecycle — absent, placed above, removed again — pinning the transition itself rather
     * than two static snapshots that could pass through different code paths.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void topTrapdoorHoldsFlushThroughSlabPlaceAndBreak(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos wall = ctx.absolutePos(new BlockPos(2, 2, 3));
        BlockPos trapdoor = ctx.absolutePos(new BlockPos(2, 2, 2));
        world.setBlock(wall, Blocks.STONE.defaultBlockState(), 2);
        placeTopTrapdoor(ctx, world, wall, trapdoor);
        assertDy(ctx, world, trapdoor, 0.0, "premise: flush before any slab exists");
        world.setBlock(trapdoor.above(), topSlab(), Block.UPDATE_ALL);
        assertDy(ctx, world, trapdoor, 0.0, "flush ruling: flush while the top slab is above");
        world.setBlock(trapdoor.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        assertDy(ctx, world, trapdoor, 0.0, "flush ruling (jump-on-break): still flush after the slab is broken");
        ctx.succeed();
    }

    /** A Y-axis chain directly under a TOP slab hangs flush; the bridge MODEL closes the visual seam. */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void chainUnderTopSlabHangsFlush(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos chain = ctx.absolutePos(new BlockPos(2, 3, 2));
        world.setBlock(chain.above(), topSlab(), Block.UPDATE_ALL);
        world.setBlock(chain, yChain(), Block.UPDATE_ALL);
        assertDy(ctx, world, chain, 0.0,
                "flush ruling: a Y-axis chain directly under a TOP slab hangs flush at grid height");
        ctx.succeed();
    }

    /**
     * Targeting guard for the flush chain: the ceiling-bridge selection proxy must still cover the
     * bridge-only upper segment above the native 16px chain, or the visible connecting span becomes
     * unclickable once the chain no longer raises.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void ceilingBridgedChainSelectionExtendsToVisibleBridge(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos chain = ctx.absolutePos(new BlockPos(2, 3, 2));
        world.setBlock(chain.above(), topSlab(), Block.UPDATE_ALL);
        world.setBlock(chain, yChain(), Block.UPDATE_ALL);
        assertDy(ctx, world, chain, 0.0,
                "setup: direct Y-chain under a TOP slab hangs flush (flush ruling)");

        BlockState actual = world.getBlockState(chain);
        VoxelShape outline = actual.getShape(world, chain, CollisionContext.empty());
        Vec3 from = new Vec3(chain.getX() + 0.5d, chain.getY() + 1.25d, chain.getZ() - 0.5d);
        Vec3 to = new Vec3(chain.getX() + 0.5d, chain.getY() + 1.25d, chain.getZ() + 1.5d);
        BlockHitResult outlineHit = outline.clip(from, to, chain);
        ctx.assertTrue(outlineHit != null && outlineHit.getBlockPos().equals(chain),
                "the flush TOP bridge must extend the outline above the native chain through local y=1.25");
        ctx.succeed();
    }

    // ── CONTROLS: the lowered-top-slab merge compensation deliberately SURVIVES the ruling ──

    /**
     * Walk A cascade tail: a cascaded hanger whose column tops at a lowered TOP slab must read the
     * SAME merged dy as its carrier. Before the ruling the reach-up return shadowed the cascade tail
     * for every top-slab column top, so the tail's merge compensation was unreachable; the ruling
     * exposes it, and without the compensation the lower hanger sinks to raw -1.0 while its carrier
     * sits at -0.5.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void cascadedHangingSignUnderMarkedUpperSlabFollowsCarrier(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos slab = buildMarkedUpperTopSlab(ctx, world);
        BlockPos upper = slab.below();
        BlockPos lower = upper.below();
        world.setBlock(upper, Blocks.OAK_HANGING_SIGN.defaultBlockState(), 2);
        world.setBlock(lower, Blocks.OAK_HANGING_SIGN.defaultBlockState(), 2);
        assertDy(ctx, world, upper, -0.5,
                "premise/direct leg: the upper hanging sign merges at -0.5 under the -1.0 marked slab");
        assertDy(ctx, world, lower, -0.5,
                "cascade tail: the lower hanging sign must match its carrier (-0.5), not sink to raw -1.0");
        ctx.succeed();
    }

    /**
     * The geometric compensation leg pinned on an UNANCHORED trapdoor (setBlock — no placement
     * hooks), the lane that serves support-arrives-later, worldgen, and command-placed cells.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void unanchoredTrapdoorUnderMarkedUpperSlabReadsGeometricMerge(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos slab = buildMarkedUpperTopSlab(ctx, world);
        BlockPos trapdoor = slab.below();
        world.setBlock(trapdoor, Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(BlockStateProperties.HALF, Half.TOP), 2);
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(world, trapdoor),
                "scene premise: the setBlock trapdoor must be UNANCHORED");
        assertDy(ctx, world, trapdoor, -0.5,
                "geometric compensation: unanchored trapdoor under the -1.0 marked slab merges at -0.5");
        ctx.succeed();
    }

    /** Live-confirmed merge (2026-07-03 lineage): a placed trapdoor under the -1.0 marked slab reads -0.5. */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void controlTrapdoorUnderMarkedUpperSlabKeepsCompensation(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos slab = buildMarkedUpperTopSlab(ctx, world);
        BlockPos wall = ctx.absolutePos(new BlockPos(2, 4, 3));
        BlockPos trapdoor = slab.below();
        world.setBlock(wall, Blocks.STONE.defaultBlockState(), 2);
        placeTopTrapdoor(ctx, world, wall, trapdoor);
        assertDy(ctx, world, trapdoor, -0.5,
                "control: the lowered-compensation merge (slabDy -1.0 + 0.5 = -0.5) must survive the ruling");
        ctx.succeed();
    }

    /** Walk A compensation control: roots under the -1.0 marked slab read -0.5 — survives the ruling. */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void controlHangingRootsUnderMarkedUpperSlabKeepCompensation(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos slab = buildMarkedUpperTopSlab(ctx, world);
        BlockPos roots = slab.below();
        place(ctx, new ItemStack(Items.HANGING_ROOTS), slab, Direction.DOWN, 0.0);
        ctx.assertTrue(world.getBlockState(roots).is(Blocks.HANGING_ROOTS),
                "scene premise: hanging roots must place under the marked top slab, got " + world.getBlockState(roots));
        assertDy(ctx, world, roots, -0.5,
                "control: walk A's lowered-compensation (supportDy -1.0 + 0.5 = -0.5) must survive the ruling");
        ctx.succeed();
    }

    // ── rig and helpers ──

    /** Marked side-UPPER TOP slab (-1.0, authored marker beside a genuine compound stack). */
    private static BlockPos buildMarkedUpperTopSlab(GameTestHelper ctx, ServerLevel world) {
        BlockPos base = ctx.absolutePos(new BlockPos(3, 1, 2));
        world.setBlock(base, Blocks.STONE.defaultBlockState(), 2);
        world.setBlock(base.above(1),
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        world.setBlock(base.above(2), Blocks.STONE.defaultBlockState(), 2);
        world.setBlock(base.above(3),
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        BlockPos fullBlock = base.above(4);
        world.setBlock(fullBlock, Blocks.STONE.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(world, fullBlock, world.getBlockState(fullBlock));
        SlabAnchorAttachment.addCompoundFullBlockAnchor(world, fullBlock, world.getBlockState(fullBlock));
        BlockPos slab = fullBlock.west();
        world.setBlock(slab, topSlab(), 2);
        SlabAnchorAttachment.addCompoundVisibleSideUpperSlab(world, slab, world.getBlockState(slab),
                fullBlock, world.getBlockState(fullBlock));
        double slabDy = SlabSupport.getYOffset(world, slab, world.getBlockState(slab));
        ctx.assertTrue(Math.abs(slabDy + 1.0) <= EPS,
                "scene premise: the marked side-UPPER top slab must read -1.0, got " + slabDy);
        return slab;
    }

    /** Real useOn click on a face of {@code clicked}; {@code yNudge} selects the upper/lower half. */
    private static void place(GameTestHelper ctx, ItemStack stack, BlockPos clicked, Direction face,
                              double yNudge) {
        Player player = ctx.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(clicked)
                .add(face.getStepX() * 0.5, face.getStepY() * 0.5 + yNudge, face.getStepZ() * 0.5);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, clicked, false)));
    }

    /** TOP-half oak trapdoor placed by a real high side-face click against a wall block. */
    private static void placeTopTrapdoor(GameTestHelper ctx, ServerLevel world, BlockPos wall,
                                         BlockPos expected) {
        place(ctx, new ItemStack(Items.OAK_TRAPDOOR), wall, Direction.NORTH, 0.3);
        BlockState state = world.getBlockState(expected);
        ctx.assertTrue(state.hasProperty(BlockStateProperties.HALF)
                        && state.getValue(BlockStateProperties.HALF) == Half.TOP,
                "scene premise: expected a TOP-half trapdoor at " + expected.toShortString() + ", got " + state);
    }

    private static void assertDy(GameTestHelper ctx, ServerLevel world, BlockPos pos, double expected,
                                 String message) {
        double got = SlabSupport.getYOffset(world, pos, world.getBlockState(pos));
        ctx.assertTrue(Math.abs(got - expected) <= EPS,
                message + ": expected dy " + expected + ", got " + got);
    }

    private static BlockState topSlab() {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
    }

    private static BlockState yChain() {
        return Blocks.CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
    }
}
