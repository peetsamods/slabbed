package com.slabbed.test;

import com.slabbed.anchor.ClientRenderDyPrediction;
import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.util.SlabSupport;
import java.util.function.LongToIntFunction;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("fabric-gametest-api-v1")
@PrefixGameTestTemplate(false)
public final class SlabPlacementHeightResolverTest {
    private static final String TEMPLATE = "empty";

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void storedFactsAreFirstResolverAuthority(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos storedZero = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos storedMinusOne = ctx.absolutePos(new BlockPos(5, 2, 2));
        BlockPos dynamicHanger = ctx.absolutePos(new BlockPos(8, 2, 2));
        BlockPos dynamicTrapdoor = ctx.absolutePos(new BlockPos(2, 2, 5));
        BlockPos carpet = ctx.absolutePos(new BlockPos(5, 2, 5));

        LongToIntFunction previousLookup = SlabPlacementHeightAttachment.installClientRenderHalfStepsLookup(
                packed -> packed == storedMinusOne.asLong()
                        ? -2
                        : SlabPlacementHeightAttachment.ABSENT_HALF_STEPS);
        try {
            world.setBlock(storedZero.below(), Blocks.STONE_SLAB.defaultBlockState()
                    .setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_ALL);
            world.setBlock(storedZero, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            assertExact(ctx, SlabSupport.getYOffset(
                            world, storedZero, world.getBlockState(storedZero)), -0.5d,
                    "fixture must reach the lowered legacy lane before a fact exists");
            ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(
                            world.getChunk(storedZero.getX() >> 4, storedZero.getZ() >> 4), storedZero, 0),
                    "fixture must store an explicit zero fact");
            assertExact(ctx, SlabSupport.getYOffset(
                            world, storedZero, world.getBlockState(storedZero)), 0.0d,
                    "stored explicit zero must beat lowered geometry");
            assertExact(ctx, SlabSupport.getUnstoredYOffset(
                            world, storedZero, world.getBlockState(storedZero)), -0.5d,
                    "the store-blind twin must preserve the lowered legacy lane");

            world.setBlock(storedMinusOne.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            world.setBlock(storedMinusOne, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            assertExact(ctx, SlabSupport.getYOffset(
                            world, storedMinusOne, world.getBlockState(storedMinusOne)), 0.0d,
                    "fixture must be flat before a fact exists");
            ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(
                            world.getChunk(storedMinusOne.getX() >> 4, storedMinusOne.getZ() >> 4),
                            storedMinusOne,
                            -2),
                    "fixture must store an exact minus-one fact");
            assertExact(ctx, SlabSupport.getYOffset(
                            world, storedMinusOne, world.getBlockState(storedMinusOne)), -1.0d,
                    "stored minus one must beat flat geometry");

            BlockGetter renderView = new NonLevelView(world);
            assertExact(ctx, SlabSupport.getYOffset(
                            renderView, storedMinusOne, world.getBlockState(storedMinusOne)), -1.0d,
                    "a non-level render view must read synchronized placement truth");
            assertExact(ctx, SlabSupport.getUnstoredYOffset(
                            renderView, storedMinusOne, world.getBlockState(storedMinusOne)), 0.0d,
                    "a non-level render view must preserve the store-blind legacy twin");

            world.setBlock(dynamicHanger.above(), Blocks.STONE_SLAB.defaultBlockState()
                    .setValue(SlabBlock.TYPE, SlabType.TOP), Block.UPDATE_ALL);
            world.setBlock(dynamicHanger, Blocks.LANTERN.defaultBlockState()
                    .setValue(BlockStateProperties.HANGING, true), Block.UPDATE_ALL);
            assertExact(ctx, SlabSupport.getUnstoredYOffset(
                            world, dynamicHanger, world.getBlockState(dynamicHanger)), 0.0d,
                    "flush ruling: a hanger under a flush top slab hangs at grid height");
            ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(
                            world.getChunk(dynamicHanger.getX() >> 4, dynamicHanger.getZ() >> 4),
                            dynamicHanger,
                            -1),
                    "fixture must install a conflicting nonzero legacy fact");
            assertExact(ctx, SlabSupport.getYOffset(
                            world, dynamicHanger, world.getBlockState(dynamicHanger)), 0.0d,
                    "a true ceiling follower must follow its support rather than a stale fact");

            world.setBlock(dynamicHanger.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(
                            world.getChunk(dynamicHanger.getX() >> 4, dynamicHanger.getZ() >> 4),
                            dynamicHanger.above(),
                            -2),
                    "fixture must store the ceiling support's exact lowered height");
            assertExact(ctx, SlabSupport.getYOffset(
                            world, dynamicHanger, world.getBlockState(dynamicHanger)), -1.0d,
                    "a ceiling follower must inherit its support's stored height");

            world.setBlock(dynamicTrapdoor.above(), Blocks.STONE_SLAB.defaultBlockState()
                    .setValue(SlabBlock.TYPE, SlabType.TOP), Block.UPDATE_ALL);
            BlockState topTrapdoor = Blocks.OAK_TRAPDOOR.defaultBlockState()
                    .setValue(BlockStateProperties.HALF, Half.TOP);
            world.setBlock(dynamicTrapdoor, topTrapdoor, Block.UPDATE_ALL);
            BlockState placedTopTrapdoor = requireExactState(
                    ctx, world, dynamicTrapdoor, topTrapdoor, "capped top trapdoor");
            ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(
                            world.getChunk(dynamicTrapdoor.getX() >> 4, dynamicTrapdoor.getZ() >> 4),
                            dynamicTrapdoor,
                            -1),
                    "fixture must install a conflicting nonzero trapdoor fact");
            assertExact(ctx, SlabSupport.getYOffset(world, dynamicTrapdoor, placedTopTrapdoor), 0.0d,
                    "a top-half ceiling trapdoor must stay support-relative rather than freeze");

            world.setBlock(carpet.below(), Blocks.STONE_SLAB.defaultBlockState()
                    .setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_ALL);
            BlockState carpetState = Blocks.WHITE_CARPET.defaultBlockState();
            world.setBlock(carpet, carpetState, Block.UPDATE_ALL);
            ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(
                            world.getChunk(carpet.getX() >> 4, carpet.getZ() >> 4), carpet, 0),
                    "fixture must install a conflicting thin-layer fact");
            assertExact(ctx, ClientDy.dyFor(world, carpet, carpetState), -0.5d,
                    "carpet must remain seated on the slab top instead of trusting its own fact");

            assertDynamicRoles(ctx, world, dynamicTrapdoor, storedZero);
        } finally {
            SlabPlacementHeightAttachment.installClientRenderHalfStepsLookup(previousLookup);
            SlabPlacementHeightAttachment.remove(
                    world.getChunk(storedZero.getX() >> 4, storedZero.getZ() >> 4), storedZero);
            SlabPlacementHeightAttachment.remove(
                    world.getChunk(storedMinusOne.getX() >> 4, storedMinusOne.getZ() >> 4), storedMinusOne);
            SlabPlacementHeightAttachment.remove(
                    world.getChunk(dynamicHanger.getX() >> 4, dynamicHanger.getZ() >> 4), dynamicHanger);
            SlabPlacementHeightAttachment.remove(
                    world.getChunk(dynamicHanger.getX() >> 4, dynamicHanger.getZ() >> 4), dynamicHanger.above());
            SlabPlacementHeightAttachment.remove(
                    world.getChunk(dynamicTrapdoor.getX() >> 4, dynamicTrapdoor.getZ() >> 4), dynamicTrapdoor);
            SlabPlacementHeightAttachment.remove(
                    world.getChunk(carpet.getX() >> 4, carpet.getZ() >> 4), carpet);
        }

        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void positionedAttachmentRolesChooseTheActualOwner(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos floorOwner = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos ceilingFollower = ctx.absolutePos(new BlockPos(5, 2, 2));
        BlockPos ambiguousOwner = ctx.absolutePos(new BlockPos(8, 2, 2));
        StringBuilder failures = new StringBuilder();

        BlockState[] floorStates = {
                Blocks.LEVER.defaultBlockState()
                        .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR),
                Blocks.OAK_BUTTON.defaultBlockState()
                        .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR),
                Blocks.BELL.defaultBlockState()
                        .setValue(BlockStateProperties.BELL_ATTACHMENT, BellAttachType.FLOOR),
                Blocks.POINTED_DRIPSTONE.defaultBlockState()
                        .setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.UP),
                Blocks.LANTERN.defaultBlockState()
                        .setValue(BlockStateProperties.HANGING, false)
        };
        BlockState[] ceilingStates = {
                Blocks.LEVER.defaultBlockState()
                        .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.CEILING),
                Blocks.OAK_BUTTON.defaultBlockState()
                        .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.CEILING),
                Blocks.BELL.defaultBlockState()
                        .setValue(BlockStateProperties.BELL_ATTACHMENT, BellAttachType.CEILING),
                Blocks.POINTED_DRIPSTONE.defaultBlockState()
                        .setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.DOWN),
                Blocks.LANTERN.defaultBlockState()
                        .setValue(BlockStateProperties.HANGING, true),
                Blocks.OAK_HANGING_SIGN.defaultBlockState()
        };

        try {
            for (BlockState floorState : floorStates) {
                clearRoleFixture(world, floorOwner);
                world.setBlock(floorOwner.below(), Blocks.STONE_SLAB.defaultBlockState()
                        .setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_ALL);
                world.setBlock(floorOwner, floorState, Block.UPDATE_CLIENTS);
                BlockState actualFloorState = requireExactState(
                        ctx, world, floorOwner, floorState, "floor role");
                recordExact(failures, SlabSupport.getYOffset(world, floorOwner, actualFloorState), -0.5d,
                        floorState.getBlock() + " floor role must follow its support before authorship");
                putHalfSteps(ctx, world, floorOwner, -2, "floor owner");
                recordExact(failures, SlabSupport.getYOffset(world, floorOwner, actualFloorState), -1.0d,
                        floorState.getBlock() + " floor role must retain its own stored height");
            }

            for (BlockState ceilingState : ceilingStates) {
                clearRoleFixture(world, ceilingFollower);
                world.setBlock(ceilingFollower.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                putHalfSteps(ctx, world, ceilingFollower.above(), -2, "ceiling support");
                world.setBlock(ceilingFollower, ceilingState, Block.UPDATE_CLIENTS);
                BlockState actualCeilingState = requireExactState(
                        ctx, world, ceilingFollower, ceilingState, "ceiling role");
                putHalfSteps(ctx, world, ceilingFollower, 0, "conflicting ceiling follower");
                recordExact(failures, SlabSupport.getYOffset(world, ceilingFollower, actualCeilingState), -1.0d,
                        ceilingState.getBlock() + " ceiling role must follow the exact support above");
            }

            BlockState topTrapdoor = Blocks.OAK_TRAPDOOR.defaultBlockState()
                    .setValue(BlockStateProperties.HALF, Half.TOP);
            clearRoleFixture(world, ambiguousOwner);
            world.setBlock(ambiguousOwner, topTrapdoor, Block.UPDATE_CLIENTS);
            BlockState actualTopTrapdoor = requireExactState(
                    ctx, world, ambiguousOwner, topTrapdoor, "uncapped top trapdoor");
            putHalfSteps(ctx, world, ambiguousOwner, -1, "uncapped top trapdoor");
            recordExact(failures, SlabSupport.getYOffset(world, ambiguousOwner, actualTopTrapdoor), -0.5d,
                    "an uncapped top trapdoor must retain its own stored height");
            AABB uncappedTrapdoorOutline = actualTopTrapdoor
                    .getShape(world, ambiguousOwner, CollisionContext.empty())
                    .bounds();
            recordExact(failures, uncappedTrapdoorOutline.minY, 0.3125d,
                    "an uncapped top trapdoor outline must use the same stored height");
            recordExact(failures, uncappedTrapdoorOutline.maxY, 0.5d,
                    "an uncapped top trapdoor outline must preserve its thickness");
            world.setBlock(ambiguousOwner.above(), Blocks.STONE_SLAB.defaultBlockState()
                    .setValue(SlabBlock.TYPE, SlabType.TOP), Block.UPDATE_ALL);
            recordExact(failures, SlabSupport.getYOffset(world, ambiguousOwner, actualTopTrapdoor), 0.0d,
                    "a top trapdoor under a flush slab cap hangs flush (flush ruling)");
            AABB cappedTrapdoorOutline = actualTopTrapdoor
                    .getShape(world, ambiguousOwner, CollisionContext.empty())
                    .bounds();
            recordExact(failures, cappedTrapdoorOutline.minY, 0.8125d,
                    "a flush-capped top trapdoor outline must stay at its native height");
            recordExact(failures, cappedTrapdoorOutline.maxY, 1.0d,
                    "a flush-capped top trapdoor outline must preserve its thickness");

            BlockState verticalChain = Blocks.CHAIN.defaultBlockState()
                    .setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
            clearRoleFixture(world, ambiguousOwner);
            world.setBlock(ambiguousOwner, verticalChain, Block.UPDATE_CLIENTS);
            BlockState actualVerticalChain = requireExactState(
                    ctx, world, ambiguousOwner, verticalChain, "uncapped vertical chain");
            putHalfSteps(ctx, world, ambiguousOwner, -1, "uncapped vertical chain");
            recordExact(failures, SlabSupport.getYOffset(world, ambiguousOwner, actualVerticalChain), -0.5d,
                    "an uncapped vertical chain must retain its own stored height");
            AABB uncappedChainOutline = actualVerticalChain
                    .getShape(world, ambiguousOwner, CollisionContext.empty())
                    .bounds();
            recordExact(failures, uncappedChainOutline.minY, -0.5d,
                    "an uncapped vertical chain outline must use the same stored height");
            recordExact(failures, uncappedChainOutline.maxY, 0.5d,
                    "an uncapped vertical chain outline must preserve its height");
            world.setBlock(ambiguousOwner.above(), Blocks.STONE_SLAB.defaultBlockState()
                    .setValue(SlabBlock.TYPE, SlabType.TOP), Block.UPDATE_ALL);
            recordExact(failures, SlabSupport.getYOffset(world, ambiguousOwner, actualVerticalChain), 0.0d,
                    "a vertical chain under a flush slab cap hangs flush; the bridge shape covers the seam");
            AABB cappedChainOutline = actualVerticalChain
                    .getShape(world, ambiguousOwner, CollisionContext.empty())
                    .bounds();
            recordExact(failures, cappedChainOutline.minY, 0.0d,
                    "a capped vertical chain outline must include the ceiling bridge");
            recordExact(failures, cappedChainOutline.maxY, 1.5d,
                    "a capped vertical chain outline must reach the ceiling support");

            BlockState wallHangingSign = Blocks.OAK_WALL_HANGING_SIGN.defaultBlockState();
            clearRoleFixture(world, ambiguousOwner);
            world.setBlock(ambiguousOwner.east(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            world.setBlock(ambiguousOwner.west(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            world.setBlock(ambiguousOwner.above(), Blocks.STONE_SLAB.defaultBlockState()
                    .setValue(SlabBlock.TYPE, SlabType.TOP), Block.UPDATE_ALL);
            world.setBlock(ambiguousOwner, wallHangingSign, Block.UPDATE_CLIENTS);
            BlockState actualWallHangingSign = requireExactState(
                    ctx, world, ambiguousOwner, wallHangingSign, "wall hanging sign");
            putHalfSteps(ctx, world, ambiguousOwner, -1, "wall hanging sign");
            recordExact(failures, SlabSupport.getYOffset(
                            world, ambiguousOwner, actualWallHangingSign), -0.5d,
                    "a wall hanging sign must remain side-owned even with an unrelated cap above");
        } finally {
            clearRoleFixture(world, floorOwner);
            clearRoleFixture(world, ceilingFollower);
            clearRoleFixture(world, ambiguousOwner);
        }

        ctx.assertTrue(failures.isEmpty(), "position-aware attachment role mismatches: " + failures);
        ctx.succeed();
    }

    private static void putHalfSteps(
            GameTestHelper ctx,
            ServerLevel world,
            BlockPos pos,
            int halfSteps,
            String fixture
    ) {
        ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(
                        world.getChunk(pos.getX() >> 4, pos.getZ() >> 4), pos, halfSteps),
                fixture + " must store its exact test fact");
    }

    private static BlockState requireExactState(
            GameTestHelper ctx,
            ServerLevel world,
            BlockPos pos,
            BlockState expected,
            String fixture
    ) {
        BlockState actual = world.getBlockState(pos);
        ctx.assertTrue(actual == expected,
                fixture + " must survive as the exact intended state; expected=" + expected + " actual=" + actual);
        return actual;
    }

    private static void clearRoleFixture(ServerLevel world, BlockPos pos) {
        BlockPos[] ownedPositions = {
                pos,
                pos.above(),
                pos.below(),
                pos.east(),
                pos.west()
        };
        for (BlockPos ownedPos : ownedPositions) {
            SlabPlacementHeightAttachment.remove(
                    world.getChunk(ownedPos.getX() >> 4, ownedPos.getZ() >> 4), ownedPos);
            world.setBlock(ownedPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static void recordExact(StringBuilder failures, double actual, double expected, String message) {
        if (Double.doubleToRawLongBits(actual) != Double.doubleToRawLongBits(expected)) {
            if (!failures.isEmpty()) {
                failures.append(" | ");
            }
            failures.append(message)
                    .append("; expected=")
                    .append(expected)
                    .append(" actual=")
                    .append(actual);
        }
    }

    private static void assertDynamicRoles(
            GameTestHelper ctx,
            BlockGetter world,
            BlockPos cappedPos,
            BlockPos uncappedPos
    ) {
        BlockState verticalChain = Blocks.CHAIN.defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
        BlockState topTrapdoor = Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(BlockStateProperties.HALF, Half.TOP);
        ctx.assertTrue(SlabSupport.isDynamicCeilingFollower(world, cappedPos, verticalChain),
                "a capped vertical chain must remain on the ceiling-follower path");
        ctx.assertTrue(!SlabSupport.isDynamicCeilingFollower(world, uncappedPos, verticalChain),
                "an uncapped vertical chain must remain a placement-height owner");
        ctx.assertTrue(SlabSupport.isDynamicCeilingFollower(world, cappedPos, topTrapdoor),
                "a capped top trapdoor must remain on the ceiling-follower path");
        ctx.assertTrue(!SlabSupport.isDynamicCeilingFollower(world, uncappedPos, topTrapdoor),
                "an uncapped top trapdoor must remain a placement-height owner");
        ctx.assertTrue(!SlabSupport.isDynamicCeilingFollower(
                        world, cappedPos, Blocks.OAK_WALL_HANGING_SIGN.defaultBlockState()),
                "a wall hanging sign must remain side-owned even below a ceiling cap");
        ctx.assertTrue(SlabSupport.isDynamicCeilingFollower(
                        world, cappedPos, Blocks.OAK_HANGING_SIGN.defaultBlockState()),
                "a hanging sign must remain on the ceiling-follower path");
        ctx.assertTrue(SlabSupport.isDynamicCeilingFollower(
                        world, cappedPos, Blocks.CAVE_VINES.defaultBlockState()),
                "cave vines must remain on the ceiling-follower path");
        ctx.assertTrue(SlabSupport.isDynamicCeilingFollower(world, cappedPos,
                        Blocks.LEVER.defaultBlockState()
                                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.CEILING)),
                "a ceiling-mounted lever must remain support-relative");
        ctx.assertTrue(!SlabSupport.isDynamicCeilingFollower(world, cappedPos,
                        Blocks.LEVER.defaultBlockState()
                                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)),
                "a floor-mounted lever must remain a placement-height owner");
        ctx.assertTrue(SlabSupport.isDynamicCeilingFollower(world, cappedPos,
                        Blocks.BELL.defaultBlockState()
                                .setValue(BlockStateProperties.BELL_ATTACHMENT, BellAttachType.CEILING)),
                "a ceiling bell must remain support-relative");
        ctx.assertTrue(!SlabSupport.isDynamicCeilingFollower(world, cappedPos,
                        Blocks.BELL.defaultBlockState()
                                .setValue(BlockStateProperties.BELL_ATTACHMENT, BellAttachType.FLOOR)),
                "a floor bell must remain a placement-height owner");
        ctx.assertTrue(SlabSupport.isDynamicCeilingFollower(world, cappedPos,
                        Blocks.POINTED_DRIPSTONE.defaultBlockState()
                                .setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.DOWN)),
                "downward dripstone must remain support-relative");
        ctx.assertTrue(!SlabSupport.isDynamicCeilingFollower(world, cappedPos,
                        Blocks.POINTED_DRIPSTONE.defaultBlockState()
                                .setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.UP)),
                "upward dripstone must remain a placement-height owner");
    }

    private static void assertExact(GameTestHelper ctx, double actual, double expected, String message) {
        ctx.assertTrue(Double.doubleToRawLongBits(actual) == Double.doubleToRawLongBits(expected),
                message + "; expected=" + expected + " actual=" + actual);
    }

    /**
     * A chunk render region is a fixed array over a bounded box: a read that leaves it throws
     * rather than falling back to the level. The resolver walks a support chain, so on a mesh
     * worker it reaches that edge mid-frame.
     *
     * <p>Three claims, one per lane. A region that can see the evidence must answer exactly what
     * the level answers - bounding may not perturb a reachable result. A region that cannot must
     * decline to flush rather than invent a height from a substituted read, because air is
     * positive evidence in this resolver and a block over nothing sinks. And a view that is not
     * a render region must rethrow, so this never masks a real defect.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void resolverEndsItsWalkAtARenderRegionEdge(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos subject = ctx.absolutePos(new BlockPos(2, 3, 9));
        world.setBlock(subject.below(), Blocks.STONE_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_ALL);
        world.setBlock(subject, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        BlockState state = world.getBlockState(subject);
        double onTheLevel = SlabSupport.getYOffset(world, subject, state);

        java.util.function.Predicate<BlockGetter> previous =
                SlabSupport.installRenderRegionDetector(view -> view instanceof BoundedRegionView);
        try {
            double seen = SlabSupport.getYOffset(
                    new BoundedRegionView(world, subject, 8), subject, state);
            ctx.assertTrue(Double.compare(seen, onTheLevel) == 0,
                    "a region that can see the evidence must answer what the level answers;"
                            + " level=" + onTheLevel + " region=" + seen);

            double blind = SlabSupport.getYOffset(
                    new BoundedRegionView(world, subject, 0), subject, state);
            ctx.assertTrue(blind == 0.0d,
                    "a region that cannot reach the evidence must decline to flush rather than"
                            + " invent a height; got " + blind);
        } finally {
            SlabSupport.installRenderRegionDetector(previous);
        }

        // With nothing claiming the view is a region, the escape is a real defect and must
        // surface. Swallowing it here would hide exactly the bugs this guard must not hide.
        boolean rethrown = false;
        try {
            SlabSupport.getYOffset(new BoundedRegionView(world, subject, 0), subject, state);
        } catch (IndexOutOfBoundsException expected) {
            rethrown = true;
        }
        ctx.assertTrue(rethrown,
                "outside a render region the bounds escape must be rethrown, not swallowed");
        ctx.succeed();
    }

    private record NonLevelView(ServerLevel delegate) implements BlockGetter {
        @Override
        public BlockState getBlockState(BlockPos pos) {
            return delegate.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return delegate.getFluidState(pos);
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return delegate.getBlockEntity(pos);
        }

        @Override
        public int getHeight() {
            return delegate.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return delegate.getMinBuildHeight();
        }
    }

    /** A {@link BlockGetter} that throws outside its box, the way a render region's array does. */
    /**
     * A client prediction reaches the chunk mesh and nothing else.
     *
     * <p>The client resolves the placement height itself and must draw it immediately, or the
     * block renders the live fallback for as long as the fact takes to sync and then jumps. What
     * must NOT happen is that same guess reaching collision or targeting, where an unarrived fact
     * would become indistinguishable from a frozen one. The two halves are asserted together
     * because a fix that satisfies only the first is the bug this row exists to prevent.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void aClientPredictionReachesTheMeshAndNotTheWorld(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos subject = ctx.absolutePos(new BlockPos(2, 2, 8));
        world.setBlock(subject.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(subject, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);

        // No fact anywhere: the mesh view and the level view must agree on that.
        // A lookup that never answers, deliberately: the prediction must be consulted by the
        // production path itself, not by anything this fixture supplies. With a helpful lookup
        // here the row would pass with the real wiring removed.
        LongToIntFunction previousLookup =
                SlabPlacementHeightAttachment.installClientRenderHalfStepsLookup(
                        packed -> SlabPlacementHeightAttachment.ABSENT_HALF_STEPS);
        try {
            ClientRenderDyPrediction.clear();
            BlockGetter meshView = new BoundedRegionView(world, subject, 8);
            assertExact(ctx, SlabPlacementHeightAttachment.storedOffset(meshView, subject),
                    Double.NaN, "premise: the mesh view sees no fact before a prediction exists");

            ClientRenderDyPrediction.record(subject.asLong(), -2);

            assertExact(ctx, SlabPlacementHeightAttachment.storedOffset(meshView, subject), -1.0d,
                    "the mesh must draw the height the client already resolved");
            assertExact(ctx, SlabPlacementHeightAttachment.storedOffset(world, subject),
                    Double.NaN,
                    "a prediction must stay out of the world view that collision and targeting"
                            + " read - a guess there is indistinguishable from a frozen height");

            // An authoritative fact supersedes it, and the prediction must not linger.
            ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(
                            world.getChunk(subject.getX() >> 4, subject.getZ() >> 4), subject, -1),
                    "fixture must store a real fact over the prediction");
            ClientRenderDyPrediction.forget(subject.asLong());
            assertExact(ctx, SlabPlacementHeightAttachment.storedOffset(world, subject), -0.5d,
                    "the real fact governs once it exists");
            assertExact(ctx, SlabPlacementHeightAttachment.storedOffset(meshView, subject),
                    Double.NaN,
                    "the prediction must be gone once its fact has arrived");

            // And it expires on its own, because some placements never produce a fact at all.
            ClientRenderDyPrediction.record(subject.asLong(), -2);
            for (int tick = 0; tick < 64; tick++) {
                ClientRenderDyPrediction.advanceTick();
            }
            assertExact(ctx, SlabPlacementHeightAttachment.storedOffset(meshView, subject),
                    Double.NaN,
                    "a prediction no fact ever answers must expire rather than become one");
        } finally {
            ClientRenderDyPrediction.clear();
            SlabPlacementHeightAttachment.installClientRenderHalfStepsLookup(previousLookup);
        }
        ctx.succeed();
    }

    private record BoundedRegionView(ServerLevel delegate, BlockPos centre, int radius)
            implements BlockGetter {
        private boolean inside(BlockPos pos) {
            return Math.abs(pos.getX() - centre.getX()) <= radius
                    && Math.abs(pos.getY() - centre.getY()) <= radius
                    && Math.abs(pos.getZ() - centre.getZ()) <= radius;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (!inside(pos)) {
                throw new IndexOutOfBoundsException("outside the modelled render region: " + pos);
            }
            return delegate.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            if (!inside(pos)) {
                throw new IndexOutOfBoundsException("outside the modelled render region: " + pos);
            }
            return delegate.getFluidState(pos);
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return inside(pos) ? delegate.getBlockEntity(pos) : null;
        }

        @Override
        public int getHeight() {
            return delegate.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return delegate.getMinBuildHeight();
        }
    }
}
