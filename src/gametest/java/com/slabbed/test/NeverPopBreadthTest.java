package com.slabbed.test;

import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.util.SlabSupport;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Never-pop breadth, ported from the donor suites onto this line's architecture: every subject
 * is placed through the REAL held-item transaction, so the stored numeric placement fact — this
 * line's first height authority — is captured exactly as a player would create it, and the rows
 * then prove neighbor and support mutations cannot move the height (LAW 1 in LAW.md; the
 * blocking law gate itself is {@code NeighborUpdateInvarianceTest} and is deliberately not part
 * of this class).
 *
 * <p>Subjects the capture contract EXCLUDES (bed parts, double-block halves, dynamic ceiling
 * followers) are pinned on their documented legacy lanes instead: no fact is captured, and the
 * geometric resolution stays stable across the mutations that vanilla itself survives.
 */
@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class NeverPopBreadthTest {
    private static final String TEMPLATE = "empty";
    private static final double EPS = 1.0e-9;

    private static final Item[] LOWERING_CANDIDATES = {
            Items.STONE,
            Items.OAK_FENCE,
            Items.OAK_FENCE_GATE,
            Items.COBBLESTONE_WALL,
            Items.GLASS_PANE,
            Items.IRON_BARS
    };

    /** Matrix base: each candidate placed lowered on a slab keeps its height when the slab breaks. */
    @GameTest(template = TEMPLATE)
    public void loweringCandidatesPlacedOnSlabSurviveSupportBreak(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        StringBuilder failures = new StringBuilder();
        for (int i = 0; i < LOWERING_CANDIDATES.length; i++) {
            BlockPos support = ctx.absolutePos(new BlockPos(1 + i, 2, 2));
            BlockPos subject = support.above();
            world.setBlock(support, bottomSlab(), Block.UPDATE_ALL);
            placeWithHeldItem(ctx, LOWERING_CANDIDATES[i], support, Direction.UP, 0.0);
            String name = LOWERING_CANDIDATES[i].toString();
            if (world.getBlockState(subject).isAir()) {
                failures.append(name).append(": did not place; ");
                continue;
            }
            double placed = dy(world, subject);
            if (Math.abs(placed + 0.5) > EPS) {
                failures.append(name).append(": placed dy ").append(placed).append(" not -0.5; ");
                continue;
            }
            if (storedFact(world, subject).orElse(Integer.MIN_VALUE) != -1) {
                failures.append(name).append(": no -0.5 placement fact captured; ");
                continue;
            }
            world.setBlock(support, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            double after = dy(world, subject);
            if (world.getBlockState(subject).isAir()) {
                failures.append(name).append(": vanished on support break; ");
            } else if (Math.abs(after - placed) > EPS) {
                failures.append(name).append(": popped ").append(placed).append(" -> ").append(after).append("; ");
            }
        }
        ctx.assertTrue(failures.isEmpty(), "support-break matrix violations: " + failures);
        ctx.succeed();
    }

    /** Matrix base, other direction: a block placed FLAT is never pulled down by a later slab. */
    @GameTest(template = TEMPLATE)
    public void placedFlatBlockIsNeverPulledDownByALaterSlab(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        StringBuilder failures = new StringBuilder();
        for (int i = 0; i < LOWERING_CANDIDATES.length; i++) {
            BlockPos ground = ctx.absolutePos(new BlockPos(1 + i, 2, 4));
            BlockPos subject = ground.above();
            world.setBlock(ground, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            placeWithHeldItem(ctx, LOWERING_CANDIDATES[i], ground, Direction.UP, 0.0);
            String name = LOWERING_CANDIDATES[i].toString();
            if (world.getBlockState(subject).isAir()) {
                failures.append(name).append(": did not place; ");
                continue;
            }
            double placed = dy(world, subject);
            if (Math.abs(placed) > EPS) {
                failures.append(name).append(": flat placement read ").append(placed).append("; ");
                continue;
            }
            world.setBlock(ground, bottomSlab(), Block.UPDATE_ALL);
            double after = dy(world, subject);
            if (Math.abs(after) > EPS) {
                failures.append(name).append(": pulled down to ").append(after).append("; ");
            }
        }
        ctx.assertTrue(failures.isEmpty(), "slab-shoved-under matrix violations: " + failures);
        ctx.succeed();
    }

    /** Block entities placed lowered keep both their height and their block entity. */
    @GameTest(template = TEMPLATE)
    public void blockEntitiesLoweredOnSlabAreHeightLocked(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        Item[] subjects = {Items.HOPPER, Items.CHEST, Items.FURNACE, Items.BARREL};
        StringBuilder failures = new StringBuilder();
        for (int i = 0; i < subjects.length; i++) {
            BlockPos support = ctx.absolutePos(new BlockPos(1 + i, 2, 6));
            BlockPos subject = support.above();
            world.setBlock(support, bottomSlab(), Block.UPDATE_ALL);
            placeWithHeldItem(ctx, subjects[i], support, Direction.UP, 0.0);
            String name = subjects[i].toString();
            double placed = dy(world, subject);
            if (Math.abs(placed + 0.5) > EPS) {
                failures.append(name).append(": placed dy ").append(placed).append(" not -0.5; ");
                continue;
            }
            world.setBlock(support, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            double after = dy(world, subject);
            if (Math.abs(after - placed) > EPS) {
                failures.append(name).append(": popped ").append(placed).append(" -> ").append(after).append("; ");
            }
            if (world.getBlockEntity(subject) == null) {
                failures.append(name).append(": block entity vanished across the support break; ");
            }
        }
        ctx.assertTrue(failures.isEmpty(), "block-entity lock violations: " + failures);
        ctx.succeed();
    }

    /** Flat block entity is not pulled down when a slab is shoved under it. */
    @GameTest(template = TEMPLATE)
    public void flatHopperIsNotPulledDownByASlabShovedUnder(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos ground = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos subject = ground.above();
        world.setBlock(ground, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        placeWithHeldItem(ctx, Items.HOPPER, ground, Direction.UP, 0.0);
        assertDy(ctx, world, subject, 0.0, "premise: the flat hopper reads 0.0");
        world.setBlock(ground, bottomSlab(), Block.UPDATE_ALL);
        assertDy(ctx, world, subject, 0.0, "a slab shoved under a flat hopper must not pull it down");
        ctx.succeed();
    }

    /** A same-kind connection-state property change never moves an already-placed fence. */
    @GameTest(template = TEMPLATE)
    public void fenceConnectionStateUpdateKeepsHeight(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos support = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos subject = support.above();
        world.setBlock(support, bottomSlab(), Block.UPDATE_ALL);
        placeWithHeldItem(ctx, Items.OAK_FENCE, support, Direction.UP, 0.0);
        assertDy(ctx, world, subject, -0.5, "premise: the fence placed lowered");
        BlockState connected = world.getBlockState(subject).setValue(BlockStateProperties.NORTH, true);
        world.setBlock(subject, connected, Block.UPDATE_ALL);
        assertDy(ctx, world, subject, -0.5, "a connection-state update must not move the fence");
        ctx.assertTrue(storedFact(world, subject).orElse(Integer.MIN_VALUE) == -1,
                "the placement fact must survive a same-kind property change");
        ctx.succeed();
    }

    /** A fence gate keeps its height and fact across an open toggle. */
    @GameTest(template = TEMPLATE)
    public void fenceGateToggleKeepsHeight(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos support = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos subject = support.above();
        world.setBlock(support, bottomSlab(), Block.UPDATE_ALL);
        placeWithHeldItem(ctx, Items.OAK_FENCE_GATE, support, Direction.UP, 0.0);
        assertDy(ctx, world, subject, -0.5, "premise: the gate placed lowered");
        BlockState toggled = world.getBlockState(subject).setValue(BlockStateProperties.OPEN, true);
        world.setBlock(subject, toggled, Block.UPDATE_ALL);
        assertDy(ctx, world, subject, -0.5, "opening the gate must not move it");
        ctx.assertTrue(storedFact(world, subject).orElse(Integer.MIN_VALUE) == -1,
                "the placement fact must survive the toggle");
        ctx.succeed();
    }

    /**
     * The torch row: support removal is a genuine vanilla pop, so the mutations here are the
     * lateral and overhead edits a torch survives vanilla-wise — the height must not move.
     */
    @GameTest(template = TEMPLATE)
    public void torchOnSlabSurvivesLateralNeighborEdits(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos support = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos subject = support.above();
        world.setBlock(support, bottomSlab(), Block.UPDATE_ALL);
        placeWithHeldItem(ctx, Items.TORCH, support, Direction.UP, 0.0);
        ctx.assertTrue(world.getBlockState(subject).is(Blocks.TORCH), "premise: the torch placed");
        double placed = dy(world, subject);
        ctx.assertTrue(Math.abs(placed + 0.5) <= EPS, "premise: the torch placed lowered; got " + placed);

        world.setBlock(subject.north(), bottomSlab(), Block.UPDATE_ALL);
        assertStable(ctx, world, subject, placed, "add_slab_north");
        world.setBlock(subject.east(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.removeBlock(subject.east(), false);
        assertStable(ctx, world, subject, placed, "add_and_break_east_neighbor");
        world.setBlock(subject.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        assertStable(ctx, world, subject, placed, "add_full_block_above");
        ctx.assertTrue(world.getBlockState(subject).is(Blocks.TORCH),
                "the torch must survive every non-support mutation");
        ctx.succeed();
    }

    /** The flower-pot family transform is an eligible in-place kind change: fact and height stay. */
    @GameTest(template = TEMPLATE)
    public void potTransformKeepsHeightAndFact(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos support = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos subject = support.above();
        world.setBlock(support, bottomSlab(), Block.UPDATE_ALL);
        placeWithHeldItem(ctx, Items.FLOWER_POT, support, Direction.UP, 0.0);
        assertDy(ctx, world, subject, -0.5, "premise: the pot placed lowered");
        ctx.assertTrue(storedFact(world, subject).orElse(Integer.MIN_VALUE) == -1,
                "premise: the pot captured a -0.5 fact");

        world.setBlock(subject, Blocks.POTTED_CORNFLOWER.defaultBlockState(), Block.UPDATE_ALL);
        assertDy(ctx, world, subject, -0.5, "potting the flower must not move the pot");
        ctx.assertTrue(storedFact(world, subject).orElse(Integer.MIN_VALUE) == -1,
                "the fact must survive the pot-to-potted transform");
        world.setBlock(subject, Blocks.FLOWER_POT.defaultBlockState(), Block.UPDATE_ALL);
        assertDy(ctx, world, subject, -0.5, "emptying the pot must not move it either");
        ctx.succeed();
    }

    /** Ceiling followers are excluded from capture: a hanging sign placement stores no fact. */
    @GameTest(template = TEMPLATE)
    public void hangingSignPlacementDoesNotCaptureAFact(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos ceiling = ctx.absolutePos(new BlockPos(2, 4, 2));
        BlockPos subject = ceiling.below();
        world.setBlock(ceiling, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        placeWithHeldItem(ctx, Items.OAK_HANGING_SIGN, ceiling, Direction.DOWN, 0.0);
        ctx.assertTrue(!world.getBlockState(subject).isAir(), "premise: the hanging sign placed");
        ctx.assertTrue(storedFact(world, subject).isEmpty(),
                "a dynamic ceiling follower must NOT capture a placement fact — it follows its support");
        assertDy(ctx, world, subject, 0.0, "a hanging sign under a flush ceiling hangs flush");
        ctx.succeed();
    }

    /** Doors stay on the multi-cell legacy lane: no fact, and per-half height survives a toggle. */
    @GameTest(template = TEMPLATE)
    public void doorPairToggleAndNeighborInvariance(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos support = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos lower = support.above();
        BlockPos upper = lower.above();
        world.setBlock(support, bottomSlab(), Block.UPDATE_ALL);
        placeWithHeldItem(ctx, Items.OAK_DOOR, support, Direction.UP, 0.0);
        ctx.assertTrue(world.getBlockState(lower).hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                        && world.getBlockState(lower).getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER,
                "premise: the door's lower half placed");
        ctx.assertTrue(storedFact(world, lower).isEmpty() && storedFact(world, upper).isEmpty(),
                "double-block halves are excluded from capture and must stay factless");
        double lowerBefore = dy(world, lower);
        double upperBefore = dy(world, upper);
        ctx.assertTrue(Math.abs(lowerBefore + 0.5) <= EPS && Math.abs(upperBefore + 0.5) <= EPS,
                "premise: both door halves read -0.5 on the slab; got " + lowerBefore + "/" + upperBefore);

        world.setBlock(lower.east(), Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
        world.removeBlock(lower.east(), false);
        BlockState toggledLower = world.getBlockState(lower)
                .setValue(BlockStateProperties.OPEN, true).setValue(BlockStateProperties.POWERED, true);
        BlockState toggledUpper = world.getBlockState(upper)
                .setValue(BlockStateProperties.OPEN, true).setValue(BlockStateProperties.POWERED, true);
        world.setBlock(lower, toggledLower, Block.UPDATE_ALL);
        world.setBlock(upper, toggledUpper, Block.UPDATE_ALL);
        ctx.assertTrue(!world.getBlockState(lower).isAir() && !world.getBlockState(upper).isAir(),
                "both halves must survive the toggle");
        assertStable(ctx, world, lower, lowerBefore, "door_lower_across_toggle");
        assertStable(ctx, world, upper, upperBefore, "door_upper_across_toggle");
        ctx.succeed();
    }

    /** Beds stay on the multi-cell legacy lane: no fact, and per-half height survives neighbor edits. */
    @GameTest(template = TEMPLATE)
    public void bedPairNeighborInvariance(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos support = ctx.absolutePos(new BlockPos(3, 2, 3));
        world.setBlock(support, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            world.setBlock(support.relative(side), bottomSlab(), Block.UPDATE_ALL);
        }
        BlockPos foot = support.above();
        placeWithHeldItem(ctx, Items.RED_BED, support, Direction.UP, 0.0);
        BlockState footState = world.getBlockState(foot);
        ctx.assertTrue(footState.hasProperty(BlockStateProperties.BED_PART)
                        && footState.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT,
                "premise: the bed's foot placed on the center support");
        Direction facing = footState.getValue(BlockStateProperties.HORIZONTAL_FACING);
        BlockPos head = foot.relative(facing);
        ctx.assertTrue(world.getBlockState(head).hasProperty(BlockStateProperties.BED_PART)
                        && world.getBlockState(head).getValue(BlockStateProperties.BED_PART) == BedPart.HEAD,
                "premise: the head half landed at the placed facing");
        ctx.assertTrue(storedFact(world, foot).isEmpty() && storedFact(world, head).isEmpty(),
                "bed halves are excluded from capture and must stay factless");
        double footBefore = dy(world, foot);
        double headBefore = dy(world, head);
        ctx.assertTrue(Math.abs(footBefore + 0.5) <= EPS && Math.abs(headBefore + 0.5) <= EPS,
                "premise: the head's slab support lowers both halves; got " + footBefore + "/" + headBefore);

        world.setBlock(foot.above(), Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
        world.removeBlock(foot.above(), false);
        world.setBlock(head.above(), Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
        world.removeBlock(head.above(), false);
        assertStable(ctx, world, foot, footBefore, "bed_foot_across_neighbor_edits");
        assertStable(ctx, world, head, headBefore, "bed_head_across_neighbor_edits");
        ctx.assertTrue(world.getBlockState(foot).getValue(BlockStateProperties.BED_PART) == BedPart.FOOT
                        && world.getBlockState(head).getValue(BlockStateProperties.BED_PART) == BedPart.HEAD,
                "the pair must still be a valid foot/head pair");
        ctx.succeed();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static BlockState bottomSlab() {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static double dy(ServerLevel world, BlockPos pos) {
        return SlabSupport.getYOffset(world, pos, world.getBlockState(pos));
    }

    private static OptionalInt storedFact(ServerLevel world, BlockPos pos) {
        return SlabPlacementHeightAttachment.storedHalfSteps(world.getChunkAt(pos), pos);
    }

    private static void placeWithHeldItem(GameTestHelper ctx, Item item, BlockPos clicked,
                                          Direction face, double yNudge) {
        Player player = ctx.makeMockPlayer();
        ItemStack stack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(clicked)
                .add(face.getStepX() * 0.5, face.getStepY() * 0.5 + yNudge, face.getStepZ() * 0.5);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, clicked, false)));
    }

    private static void assertDy(GameTestHelper ctx, ServerLevel world, BlockPos pos,
                                 double expected, String message) {
        double got = dy(world, pos);
        ctx.assertTrue(Math.abs(got - expected) <= EPS,
                message + ": expected dy " + expected + ", got " + got);
    }

    private static void assertStable(GameTestHelper ctx, ServerLevel world, BlockPos pos,
                                     double before, String mutation) {
        double after = dy(world, pos);
        ctx.assertTrue(Math.abs(after - before) <= EPS,
                mutation + ": dy moved " + before + " -> " + after);
    }
}
