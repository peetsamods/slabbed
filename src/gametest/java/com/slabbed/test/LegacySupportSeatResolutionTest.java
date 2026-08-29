package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.util.PlacementDepthPolicy;
import com.slabbed.util.SlabSupport;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.function.LongToIntFunction;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("fabric-gametest-api-v1")
@PrefixGameTestTemplate(false)
public final class LegacySupportSeatResolutionTest {
    private static final String TEMPLATE = "empty";

    /**
     * A bed is one rigid body over two cells: both halves read ONE level value, following the
     * DEEPEST resolved support, and a half over an unclassified support inherits its partner
     * (P26 resting-dy semantics, depth-complete under the 2026-08-17 flush ruling; a split or
     * tilted bed is never a legal read).
     */
    /**
     * Deep rows run consent-armed: derived descent floors at the resolved floor, and the
     * deep alphabet is what carries these depths (maintainer ruling, 2026-08-21, matching
     * the reference line). The law under test is unchanged; only its arming moved.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void bedHalvesReadOneLevelValueOnTheirSharedSupports(GameTestHelper ctx) {
        SlabSupport.armDeepAlphabet(true);
        try {
            slabbedDeepArmedBedHalvesReadOneLevelValueOnTheirSharedSupports(ctx);
        } finally {
            SlabSupport.armDeepAlphabet(false);
        }
    }

    private void slabbedDeepArmedBedHalvesReadOneLevelValueOnTheirSharedSupports(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos footSupport = absolute(ctx, 5, 2, 5);
        BlockPos headSupport = footSupport.north();
        BlockPos foot = footSupport.above();
        BlockPos head = headSupport.above();
        try {
            world.setBlock(footSupport,
                    Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.UPDATE_CLIENTS);
            world.setBlock(headSupport,
                    Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.UPDATE_CLIENTS);
            putFact(ctx, world, footSupport, -2, "bed foot support");
            putFact(ctx, world, headSupport, -2, "bed head support");
            BlockState footState = Blocks.RED_BED.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                    .setValue(BlockStateProperties.BED_PART,
                            net.minecraft.world.level.block.state.properties.BedPart.FOOT);
            BlockState headState = Blocks.RED_BED.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                    .setValue(BlockStateProperties.BED_PART,
                            net.minecraft.world.level.block.state.properties.BedPart.HEAD);
            world.setBlock(foot, footState, Block.UPDATE_CLIENTS);
            world.setBlock(head, headState, Block.UPDATE_CLIENTS);
            assertExact(ctx, ClientDy.dyFor(world, foot, world.getBlockState(foot)),
                    -1.5d, "bed foot must seat flush on its lowered support");
            assertExact(ctx, ClientDy.dyFor(world, head, world.getBlockState(head)),
                    -1.5d, "bed head must share the foot's level value");

            world.setBlock(headSupport, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            removeFact(world, headSupport);
            assertExact(ctx, ClientDy.dyFor(world, foot, world.getBlockState(foot)),
                    -1.5d, "the bed follows its deepest resolved support (P26 resting dy)");
            assertExact(ctx, ClientDy.dyFor(world, head, world.getBlockState(head)),
                    -1.5d, "a half over an unclassified support inherits its partner's value");
        } finally {
            removeFact(world, footSupport);
            removeFact(world, headSupport);
        }
        ctx.succeed();
    }

    /**
     * Deep rows run consent-armed: derived descent floors at the resolved floor, and the
     * deep alphabet is what carries these depths (maintainer ruling, 2026-08-21, matching
     * the reference line). The law under test is unchanged; only its arming moved.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void doorPairOnDeepBottomSlabSeatsFlushOnTheResolvedSurface(GameTestHelper ctx) {
        SlabSupport.armDeepAlphabet(true);
        try {
            slabbedDeepArmedDoorPairOnDeepBottomSlabSeatsFlushOnTheResolvedSurface(ctx);
        } finally {
            SlabSupport.armDeepAlphabet(false);
        }
    }

    private void slabbedDeepArmedDoorPairOnDeepBottomSlabSeatsFlushOnTheResolvedSurface(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos support = absolute(ctx, 1, 2, 1);
        BlockPos lower = support.above();
        BlockPos upper = lower.above();
        try {
            world.setBlock(
                    support,
                    Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.UPDATE_CLIENTS);
            putFact(ctx, world, support, -2, "deep door support");
            world.setBlock(
                    lower,
                    Blocks.OAK_DOOR.defaultBlockState().setValue(
                            BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER),
                    Block.UPDATE_CLIENTS);
            world.setBlock(
                    upper,
                    Blocks.OAK_DOOR.defaultBlockState().setValue(
                            BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER),
                    Block.UPDATE_CLIENTS);

            BlockState lowerState = world.getBlockState(lower);
            BlockState upperState = world.getBlockState(upper);
            // Maintainer ruling, 2026-08-17: a follower seats flush on the support's RESOLVED
            // surface; the root floor governs support resolution, never flush contact. The
            // resolved support here is -1.0, so the flush door contact is -1.5.
            assertExact(ctx, ClientDy.dyFor(world, lower, lowerState),
                    -1.5d, "deep door lower half must seat flush on the resolved surface");
            assertExact(ctx, ClientDy.dyFor(world, upper, upperState),
                    -1.5d, "deep door upper half must share the lower half's flush dy");

            VoxelShape lowerOutline = lowerState.getShape(world, lower, CollisionContext.empty());
            VoxelShape lowerCollision = SlabSupport.collisionShapeForBroadphaseCell(
                    lowerState, world, lower, CollisionContext.empty());
            assertExact(ctx, lowerOutline.bounds().minY,
                    -1.5d, "deep door outline must sit flush on the resolved surface");
            assertExact(ctx, lowerCollision.bounds().minY,
                    -1.5d, "deep door broadphase collision must follow the flush model lane");
        } finally {
            removeFact(world, support);
        }
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void factlessFollowersUseTheSupportsResolvedTopFace(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos fullSupport = absolute(ctx, 1, 2, 1);
        BlockPos bottomSupport = absolute(ctx, 3, 2, 1);
        BlockPos topSupport = absolute(ctx, 5, 2, 1);
        BlockPos doubleSupport = absolute(ctx, 1, 2, 3);
        BlockPos flushFullSupport = absolute(ctx, 3, 2, 3);
        BlockPos flushTopSupport = absolute(ctx, 5, 2, 3);
        BlockPos flushDoubleSupport = absolute(ctx, 1, 2, 5);
        BlockPos topFollowerSupport = absolute(ctx, 3, 2, 5);
        BlockPos missingSupport = absolute(ctx, 5, 2, 5);
        BlockPos frozenSupport = absolute(ctx, 7, 5, 3);
        BlockPos renderSupport = absolute(ctx, 3, 5, 1);
        BlockPos anchoredSupport = absolute(ctx, 5, 5, 1);
        BlockPos explicitZeroSupport = absolute(ctx, 1, 5, 3);

        Predicate<BlockPos> previousAnchorLookup = SlabAnchorAttachment.clientAnchorLookup;
        Predicate<BlockPos> previousFrozenLookup = SlabAnchorAttachment.clientFrozenFlatLookup;
        LongToIntFunction previousHeightLookup = SlabPlacementHeightAttachment.installClientRenderHalfStepsLookup(
                packed -> packed == renderSupport.asLong()
                        ? -2
                        : SlabPlacementHeightAttachment.ABSENT_HALF_STEPS);
        try {
            assertStoredSupportRow(ctx, fullSupport, Blocks.STONE.defaultBlockState(), -1,
                    Blocks.STONE.defaultBlockState(), -0.5d, "full-height support");
            assertStoredSupportRow(ctx, bottomSupport, slab(SlabType.BOTTOM), -1,
                    Blocks.STONE.defaultBlockState(), -1.0d, "bottom-slab support");
            assertStoredSupportRow(ctx, topSupport, slab(SlabType.TOP), -1,
                    Blocks.STONE.defaultBlockState(), -0.5d, "top-slab support");
            assertStoredSupportRow(ctx, doubleSupport, slab(SlabType.DOUBLE), -1,
                    Blocks.STONE.defaultBlockState(), -0.5d, "double-slab support");

            assertFlushSupportRow(ctx, flushFullSupport, Blocks.STONE.defaultBlockState(),
                    Blocks.STONE.defaultBlockState(), 0.0d, "flush full-height seat");
            assertFlushSupportRow(ctx, flushTopSupport, slab(SlabType.TOP),
                    Blocks.STONE.defaultBlockState(), 0.0d, "flush top-slab seat");
            assertFlushSupportRow(ctx, flushDoubleSupport, slab(SlabType.DOUBLE),
                    Blocks.STONE.defaultBlockState(), 0.0d, "flush double-slab seat");
            assertFlushSupportRow(ctx, topFollowerSupport, Blocks.STONE.defaultBlockState(),
                    slab(SlabType.TOP), -0.5d, "top-slab follower on a flush seat");
            BlockPos loweredTopFollower = topFollowerSupport.above();
            putFact(ctx, world, topFollowerSupport, -1, "lowered top-slab follower support");
            assertExact(ctx, SlabSupport.getYOffset(
                            world, loweredTopFollower, world.getBlockState(loweredTopFollower)),
                    -1.0d, "a top-slab follower must place its raw lower face on the lowered support top");
            assertExact(ctx, world.getBlockState(loweredTopFollower)
                            .getShape(world, loweredTopFollower).min(Direction.Axis.Y),
                    -0.5d, "the top-slab outline must contact the lowered support face exactly");

            world.setBlock(missingSupport, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            BlockPos missingSubject = missingSupport.above();
            world.setBlock(missingSubject, slab(SlabType.TOP), Block.UPDATE_ALL);
            assertFactAbsent(ctx, world, missingSubject, "missing-seat control subject");
            assertUnauthored(ctx, world, missingSubject, "missing-seat control subject");
            assertExact(ctx, SlabSupport.getYOffset(world, missingSubject, world.getBlockState(missingSubject)),
                    0.0d, "a missing seat must not invent a top-slab lowering");

            placeSeat(world, frozenSupport, Blocks.STONE.defaultBlockState());
            BlockPos frozenSubject = frozenSupport.above();
            world.setBlock(frozenSubject, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            SlabAnchorAttachment.freezeLoweredOnPlace(world, frozenSubject, world.getBlockState(frozenSubject));
            ctx.assertTrue(SlabAnchorAttachment.isFrozenFlat(world, frozenSubject),
                    "the frozen-flat control must own an explicit flat marker");
            assertFactAbsent(ctx, world, frozenSubject, "frozen-flat control subject");
            putFact(ctx, world, frozenSupport, -2, "frozen-flat support");
            assertExact(ctx, SlabSupport.getYOffset(world, frozenSubject, world.getBlockState(frozenSubject)),
                    0.0d, "a frozen-flat legacy subject must remain flat");

            placeSeat(world, renderSupport, Blocks.STONE.defaultBlockState());
            BlockPos renderSubject = renderSupport.above();
            world.setBlock(renderSubject, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            assertFactAbsent(ctx, world, renderSubject, "render-view subject");
            assertUnauthored(ctx, world, renderSubject, "render-view subject");
            BlockGetter renderView = new NonLevelView(world);
            assertExact(ctx, SlabSupport.getYOffset(renderView, renderSubject, world.getBlockState(renderSubject)),
                    -1.0d, "a render view must use the synchronized support fact");
            SlabAnchorAttachment.clientAnchorLookup = pos -> pos.equals(renderSubject)
                    || previousAnchorLookup != null && previousAnchorLookup.test(pos);
            assertExact(ctx, SlabSupport.getYOffset(renderView, renderSubject, world.getBlockState(renderSubject)),
                    -0.5d, "a render-view legacy anchor must remain ahead of the live seat");
            SlabAnchorAttachment.clientAnchorLookup = previousAnchorLookup;
            SlabAnchorAttachment.clientFrozenFlatLookup = pos -> pos.equals(renderSubject)
                    || previousFrozenLookup != null && previousFrozenLookup.test(pos);
            assertExact(ctx, SlabSupport.getYOffset(renderView, renderSubject, world.getBlockState(renderSubject)),
                    0.0d, "a render-view frozen-flat marker must remain ahead of the live seat");
            SlabAnchorAttachment.clientFrozenFlatLookup = previousFrozenLookup;

            placeSeat(world, anchoredSupport, slab(SlabType.BOTTOM));
            BlockPos anchoredSubject = anchoredSupport.above();
            world.setBlock(anchoredSubject, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            forceLegacyAnchor(world, anchoredSubject);
            ctx.assertTrue(SlabAnchorAttachment.isAnchored(world, anchoredSubject),
                    "the authored control must retain its legacy anchor");
            assertFactAbsent(ctx, world, anchoredSubject, "authored control subject");
            assertExact(ctx, SlabSupport.getYOffset(world, anchoredSubject, world.getBlockState(anchoredSubject)),
                    -0.5d, "the authored control must begin at its anchored height");
            putFact(ctx, world, anchoredSupport, -2, "authored control support");
            assertExact(ctx, SlabSupport.getYOffset(world, anchoredSubject, world.getBlockState(anchoredSubject)),
                    -0.5d, "a later support fact must not move an authored legacy subject");

            world.setBlock(explicitZeroSupport.below(), slab(SlabType.BOTTOM), Block.UPDATE_ALL);
            world.setBlock(explicitZeroSupport, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            BlockPos explicitZeroSubject = explicitZeroSupport.above();
            world.setBlock(explicitZeroSubject, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            assertFactAbsent(ctx, world, explicitZeroSubject, "explicit-zero subject");
            assertUnauthored(ctx, world, explicitZeroSubject, "explicit-zero subject");
            putFact(ctx, world, explicitZeroSupport, 0, "explicit-zero support");
            assertExact(ctx, SlabPlacementHeightAttachment.storedOffset(world, explicitZeroSupport),
                    0.0d, "the explicit-zero support fact must be distinguishable from absence");
            assertExact(ctx, SlabSupport.getYOffset(world, explicitZeroSubject,
                            world.getBlockState(explicitZeroSubject)),
                    0.0d, "an explicit-zero support fact must beat deeper legacy geometry");
            removeFact(world, explicitZeroSupport);
            assertFactAbsent(ctx, world, explicitZeroSupport, "removed explicit-zero support");
            assertExact(ctx, SlabSupport.getYOffset(world, explicitZeroSupport,
                            world.getBlockState(explicitZeroSupport)),
                    -0.5d, "the legacy support must become lowered again after zero is absent");
            assertExact(ctx, SlabSupport.getYOffset(world, explicitZeroSubject,
                            world.getBlockState(explicitZeroSubject)),
                    -0.5d, "absence must remain distinct from an explicit-zero support fact");
        } finally {
            SlabAnchorAttachment.clientAnchorLookup = previousAnchorLookup;
            SlabAnchorAttachment.clientFrozenFlatLookup = previousFrozenLookup;
            SlabPlacementHeightAttachment.installClientRenderHalfStepsLookup(previousHeightLookup);
            removeFact(world, fullSupport);
            removeFact(world, bottomSupport);
            removeFact(world, topSupport);
            removeFact(world, doubleSupport);
            removeFact(world, topFollowerSupport);
            removeFact(world, frozenSupport);
            removeFact(world, anchoredSupport);
            removeFact(world, explicitZeroSupport);
            removeAnchor(world, anchoredSupport.above());
            removeAnchor(world, frozenSupport.above());
        }

        ctx.succeed();
    }

    /**
     * Deep rows run consent-armed: derived descent floors at the resolved floor, and the
     * deep alphabet is what carries these depths (maintainer ruling, 2026-08-21, matching
     * the reference line). The law under test is unchanged; only its arming moved.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void carpetFollowerUsesTheSupportsResolvedTopFace(GameTestHelper ctx) {
        SlabSupport.armDeepAlphabet(true);
        try {
            slabbedDeepArmedCarpetFollowerUsesTheSupportsResolvedTopFace(ctx);
        } finally {
            SlabSupport.armDeepAlphabet(false);
        }
    }

    private void slabbedDeepArmedCarpetFollowerUsesTheSupportsResolvedTopFace(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos fullSupport = absolute(ctx, 1, 2, 1);
        BlockPos topSupport = absolute(ctx, 3, 2, 1);
        BlockPos doubleSupport = absolute(ctx, 5, 2, 1);
        BlockPos bottomSupport = absolute(ctx, 1, 2, 3);
        BlockPos deepBottomSupport = absolute(ctx, 1, 2, 5);
        BlockPos explicitZeroSupport = absolute(ctx, 3, 3, 3);
        BlockPos flushSupport = absolute(ctx, 5, 2, 3);
        BlockPos geometricSupport = absolute(ctx, 7, 2, 3);
        BlockPos thinFollowerSupport = absolute(ctx, 7, 2, 5);

        try {
            assertCarpetFollowerRow(ctx, fullSupport, Blocks.STONE.defaultBlockState(), -1,
                    -0.5d, "stored full-height support");
            assertCarpetFollowerRow(ctx, topSupport, slab(SlabType.TOP), -1,
                    -0.5d, "stored top-slab support");
            assertCarpetFollowerRow(ctx, doubleSupport, slab(SlabType.DOUBLE), -1,
                    -0.5d, "stored double-slab support");
            assertCarpetFollowerRow(ctx, bottomSupport, slab(SlabType.BOTTOM), -1,
                    -1.0d, "stored bottom-slab support");
            assertCarpetFollowerRow(ctx, deepBottomSupport, slab(SlabType.BOTTOM), -2,
                    -1.5d, "deep bottom-slab support (flush on the resolved surface)");
            assertExact(ctx, SlabSupport.getSupportTopFaceYOffset(world, deepBottomSupport),
                    -1.5d, "the resolved bottom-slab top face names the real seat surface");
            assertExact(ctx, SlabSupport.getSupportFollowerYOffset(world, deepBottomSupport),
                    -1.5d, "the follower must seat flush on the resolved top face"
                            + " (maintainer ruling, 2026-08-17: flush wins over the root floor)");

            world.setBlock(explicitZeroSupport.below(), slab(SlabType.BOTTOM), Block.UPDATE_ALL);
            world.setBlock(explicitZeroSupport, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            BlockPos explicitZeroCarpet = explicitZeroSupport.above();
            world.setBlock(explicitZeroCarpet, Blocks.WHITE_CARPET.defaultBlockState(), Block.UPDATE_ALL);
            assertState(ctx, world, explicitZeroCarpet, Blocks.WHITE_CARPET.defaultBlockState(),
                    "explicit-zero carpet");
            assertFactAbsent(ctx, world, explicitZeroCarpet, "explicit-zero carpet");
            putFact(ctx, world, explicitZeroSupport, 0, "explicit-zero carpet support");
            assertExact(ctx, ClientDy.dyFor(world, explicitZeroCarpet, world.getBlockState(explicitZeroCarpet)),
                    0.0d, "an explicit-zero support fact must keep its carpet flat");
            removeFact(world, explicitZeroSupport);
            assertFactAbsent(ctx, world, explicitZeroSupport, "removed explicit-zero carpet support");
            assertExact(ctx, ClientDy.dyFor(world, explicitZeroCarpet, world.getBlockState(explicitZeroCarpet)),
                    -0.5d, "fact absence must expose the lowered legacy support beneath the carpet");

            placeSeat(world, flushSupport, Blocks.STONE.defaultBlockState());
            BlockPos flushCarpet = flushSupport.above();
            world.setBlock(flushCarpet, Blocks.WHITE_CARPET.defaultBlockState(), Block.UPDATE_ALL);
            assertState(ctx, world, flushCarpet, Blocks.WHITE_CARPET.defaultBlockState(), "flush carpet");
            assertFactAbsent(ctx, world, flushSupport, "flush carpet support");
            assertFactAbsent(ctx, world, flushCarpet, "flush carpet");
            assertExact(ctx, ClientDy.dyFor(world, flushCarpet, world.getBlockState(flushCarpet)),
                    0.0d, "an ordinary flush support must keep its carpet flat");

            placeSeat(world, thinFollowerSupport, Blocks.STONE.defaultBlockState());
            placeHeldBlock(ctx, thinFollowerSupport, Direction.UP,
                    Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
            BlockPos thinFollower = thinFollowerSupport.above();
            assertState(ctx, world, thinFollower,
                    Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState(),
                    "unnamed thin floor follower");
            assertFactAbsent(ctx, world, thinFollower,
                    "an unnamed geometry-equivalent thin floor follower");
            putFact(ctx, world, thinFollowerSupport, -1, "unnamed thin follower support");
            assertExact(ctx, ClientDy.dyFor(world, thinFollower, world.getBlockState(thinFollower)),
                    -0.5d, "an unnamed thin floor follower must track the support's changed face");
            assertExact(ctx, world.getBlockState(thinFollower)
                            .getShape(world, thinFollower).min(Direction.Axis.Y),
                    -0.5d, "the unnamed thin follower outline must track the same support face");

            world.setBlock(geometricSupport.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            BlockState halfHeightSnow = Blocks.SNOW.defaultBlockState()
                    .setValue(SnowLayerBlock.LAYERS, 4);
            world.setBlock(geometricSupport, halfHeightSnow, Block.UPDATE_ALL);
            BlockPos geometricCarpet = geometricSupport.above();
            world.setBlock(geometricCarpet, Blocks.WHITE_CARPET.defaultBlockState(), Block.UPDATE_ALL);
            assertFactAbsent(ctx, world, geometricSupport, "geometry-only half-height support");
            assertFactAbsent(ctx, world, geometricCarpet, "geometry-only support carpet");
            assertExact(ctx, SlabSupport.getSupportTopFaceYOffset(world, geometricSupport),
                    -0.5d, "a non-slab half-height support must expose its raw top face");
            assertExact(ctx, ClientDy.dyFor(world, geometricCarpet, world.getBlockState(geometricCarpet)),
                    -0.5d, "a carpet must consume a geometry-equivalent support without a class gate");
        } finally {
            removeFact(world, fullSupport);
            removeFact(world, topSupport);
            removeFact(world, doubleSupport);
            removeFact(world, bottomSupport);
            removeFact(world, deepBottomSupport);
            removeFact(world, explicitZeroSupport);
            removeFact(world, geometricSupport);
            removeFact(world, geometricSupport.above());
            removeFact(world, thinFollowerSupport);
            removeFact(world, thinFollowerSupport.above());
        }

        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void decorationAndBlockEntityFollowResolvedSupportTopFace(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos potSupport = absolute(ctx, 1, 2, 1);
        BlockPos barrelSupport = absolute(ctx, 3, 2, 1);
        BlockPos bottomControlSupport = absolute(ctx, 5, 2, 1);
        BlockPos explicitZeroSupport = absolute(ctx, 1, 3, 3);
        BlockPos heldBarrelSupport = absolute(ctx, 3, 2, 3);
        BlockPos heldBarrel = heldBarrelSupport.above();
        BlockPos barrelSeatSupport = absolute(ctx, 5, 2, 3);
        BlockPos hangingFloorSupport = absolute(ctx, 1, 2, 5);
        BlockPos doorFloorSupport = absolute(ctx, 3, 2, 5);
        BlockPos wallSkullFloorSupport = absolute(ctx, 5, 2, 5);

        try {
            placeSeat(world, hangingFloorSupport, Blocks.STONE.defaultBlockState());
            putFact(ctx, world, hangingFloorSupport, -1, "ceiling-hanger negative-control floor");
            BlockPos hangingLantern = hangingFloorSupport.above();
            world.setBlock(hangingLantern.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            world.setBlock(hangingLantern,
                    Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true),
                    Block.UPDATE_CLIENTS);
            assertFactAbsent(ctx, world, hangingLantern, "ceiling-hanger negative control");
            assertExact(ctx, ClientDy.dyFor(world, hangingLantern, world.getBlockState(hangingLantern)),
                    0.0d, "a true ceiling hanger must not follow the cell below");

            placeSeat(world, doorFloorSupport, Blocks.STONE.defaultBlockState());
            putFact(ctx, world, doorFloorSupport, -1, "multi-cell negative-control floor");
            BlockPos doorLower = doorFloorSupport.above();
            world.setBlock(doorLower,
                    Blocks.OAK_DOOR.defaultBlockState().setValue(
                            BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER),
                    Block.UPDATE_CLIENTS);
            world.setBlock(doorLower.above(),
                    Blocks.OAK_DOOR.defaultBlockState().setValue(
                            BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER),
                    Block.UPDATE_CLIENTS);
            ctx.assertTrue(world.getBlockState(doorLower).is(Blocks.OAK_DOOR)
                            && world.getBlockState(doorLower.above()).is(Blocks.OAK_DOOR),
                    "the multi-cell negative control must retain both door halves");
            assertFactAbsent(ctx, world, doorLower, "multi-cell lower negative control");
            assertFactAbsent(ctx, world, doorLower.above(), "multi-cell upper negative control");
            // This row pinned 0.0 until 2026-08-23. Its subject is lane scope - a multi-cell
            // block must not enter the GENERIC floor-follower path - and that is still true and
            // still what makes it a control. The value moved because the door lane, which is not
            // that path, now reads the support face whether or not the support is a slab
            // (maintainer ruling, 2026-08-23). A door standing on a lowered block stands ON it.
            // The upper half is asserted with it: one object, one height.
            assertExact(ctx, ClientDy.dyFor(world, doorLower, world.getBlockState(doorLower)),
                    -0.5d, "a door seats on its support face, slab or not");
            assertExact(ctx, ClientDy.dyFor(world, doorLower.above(),
                            world.getBlockState(doorLower.above())),
                    -0.5d, "both halves of one door read one height");


            placeSeat(world, wallSkullFloorSupport, Blocks.STONE.defaultBlockState());
            putFact(ctx, world, wallSkullFloorSupport, -1, "wall-owned negative-control floor");
            BlockPos wallSkull = wallSkullFloorSupport.above();
            BlockState wallSkullState = Blocks.SKELETON_WALL_SKULL.defaultBlockState();
            Direction wallFacing = wallSkullState.getValue(WallSkullBlock.FACING);
            world.setBlock(wallSkull.relative(wallFacing.getOpposite()),
                    Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            world.setBlock(wallSkull, wallSkullState, Block.UPDATE_CLIENTS);
            ctx.assertTrue(world.getBlockState(wallSkull).is(Blocks.SKELETON_WALL_SKULL)
                            && world.getBlockEntity(wallSkull) != null,
                    "the wall-owned negative control must retain its block entity");
            assertFactAbsent(ctx, world, wallSkull, "wall-owned block-entity negative control");
            assertExact(ctx, ClientDy.dyFor(world, wallSkull, world.getBlockState(wallSkull)),
                    0.0d, "a wall-owned block entity must not follow the cell below");

            assertFloorFollowerRow(ctx, potSupport, Blocks.STONE.defaultBlockState(), -1,
                    Blocks.FLOWER_POT.defaultBlockState(), -0.5d, false,
                    "flower pot above a stored full-height support");
            assertFloorFollowerRow(ctx, barrelSupport, Blocks.STONE.defaultBlockState(), -1,
                    Blocks.BARREL.defaultBlockState(), -0.5d, true,
                    "barrel above a stored full-height support");
            assertFloorFollowerRow(ctx, bottomControlSupport, slab(SlabType.BOTTOM), -1,
                    Blocks.FLOWER_POT.defaultBlockState(), -1.0d, false,
                    "flower pot above a stored bottom-slab support");

            world.setBlock(explicitZeroSupport.below(), slab(SlabType.BOTTOM), Block.UPDATE_ALL);
            world.setBlock(explicitZeroSupport, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            BlockPos explicitZeroPot = explicitZeroSupport.above();
            world.setBlock(explicitZeroPot, Blocks.FLOWER_POT.defaultBlockState(), Block.UPDATE_ALL);
            putFact(ctx, world, explicitZeroSupport, 0, "explicit-zero decoration support");
            assertFactAbsent(ctx, world, explicitZeroPot, "explicit-zero flower pot");
            assertUnauthored(ctx, world, explicitZeroPot, "explicit-zero flower pot");
            assertExact(ctx, ClientDy.dyFor(world, explicitZeroPot, world.getBlockState(explicitZeroPot)),
                    0.0d, "an explicit-zero support fact must keep its flower pot flat");

            placeSeat(world, heldBarrelSupport, Blocks.STONE.defaultBlockState());
            assertFactAbsent(ctx, world, heldBarrelSupport, "held-placement flat support");
            placeHeldBlock(ctx, heldBarrelSupport, Direction.UP, Blocks.BARREL.defaultBlockState());
            ctx.assertTrue(world.getBlockState(heldBarrel).is(Blocks.BARREL),
                    "the real held-item route must place the barrel in the tested cell");
            ctx.assertTrue(world.getBlockEntity(heldBarrel) != null,
                    "the real held-item route must create the barrel block entity");
            assertFact(ctx, world, heldBarrel, 0,
                    "a barrel genuinely placed flat must own an explicit-zero height fact");
            putFact(ctx, world, heldBarrelSupport, -1, "later-lowered barrel support");
            assertExact(ctx, ClientDy.dyFor(world, heldBarrel, world.getBlockState(heldBarrel)),
                    0.0d, "the barrel's own placement fact must beat later support movement");

            removeFact(world, heldBarrel);
            removeAnchor(world, heldBarrel);
            assertFactAbsent(ctx, world, heldBarrel, "legacy factless barrel control");
            assertUnauthored(ctx, world, heldBarrel, "legacy factless barrel control");
            assertExact(ctx, ClientDy.dyFor(world, heldBarrel, world.getBlockState(heldBarrel)),
                    -0.5d, "only the legacy factless barrel may follow the lowered support");

            placeSeat(world, barrelSeatSupport, Blocks.BARREL.defaultBlockState());
            ctx.assertTrue(world.getBlockEntity(barrelSeatSupport) != null,
                    "the stored structural support must retain its live block entity");
            putFact(ctx, world, barrelSeatSupport, -1, "stored structural block-entity support");
            assertExact(ctx, SlabSupport.getSupportFollowerYOffset(world, barrelSeatSupport),
                    -0.5d, "a full-height block entity must expose its stored support face");
            BlockPos barrelSeatSubject = barrelSeatSupport.above();
            world.setBlock(barrelSeatSubject, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            assertState(ctx, world, barrelSeatSubject, Blocks.STONE.defaultBlockState(),
                    "factless subject above structural block-entity support");
            assertFactAbsent(ctx, world, barrelSeatSubject,
                    "factless subject above structural block-entity support");
            assertUnauthored(ctx, world, barrelSeatSubject,
                    "factless subject above structural block-entity support");
            assertExact(ctx, ClientDy.dyFor(world, barrelSeatSubject, world.getBlockState(barrelSeatSubject)),
                    -0.5d, "a factless structural subject must follow the block entity's stored face");
            assertExact(ctx, world.getBlockState(barrelSeatSubject)
                            .getShape(world, barrelSeatSubject).min(Direction.Axis.Y),
                    -0.5d, "the subject outline must follow the block entity's stored face");
        } finally {
            removeFact(world, potSupport);
            removeFact(world, barrelSupport);
            removeFact(world, bottomControlSupport);
            removeFact(world, explicitZeroSupport);
            removeFact(world, heldBarrelSupport);
            removeFact(world, heldBarrel);
            removeFact(world, barrelSeatSupport);
            removeFact(world, hangingFloorSupport);
            removeFact(world, doorFloorSupport);
            removeFact(world, wallSkullFloorSupport);
            removeAnchor(world, heldBarrel);
        }

        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void geometricFloorContactAdmitsEquivalentStatesAndDefersConnectors(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos saplingSupport = absolute(ctx, 1, 2, 1);
        BlockPos decoratedPotSupport = absolute(ctx, 3, 2, 1);
        BlockPos floorGrindstoneSupport = absolute(ctx, 5, 2, 1);
        BlockPos fenceSupport = absolute(ctx, 1, 2, 3);
        BlockPos wallSupport = absolute(ctx, 3, 2, 3);
        BlockPos barsSupport = absolute(ctx, 5, 2, 3);
        BlockPos wallGrindstoneSupport = absolute(ctx, 1, 2, 5);

        try {
            assertFloorFollowerRow(ctx, saplingSupport, Blocks.DIRT.defaultBlockState(), -1,
                    Blocks.OAK_SAPLING.defaultBlockState(), -0.5d, false,
                    "unnamed floor-contact sapling");
            assertFloorFollowerRow(ctx, decoratedPotSupport, Blocks.STONE.defaultBlockState(), -1,
                    Blocks.DECORATED_POT.defaultBlockState(), -0.5d, true,
                    "unnamed floor-contact block entity");
            assertFloorFollowerRow(ctx, floorGrindstoneSupport, Blocks.STONE.defaultBlockState(), -1,
                    Blocks.GRINDSTONE.defaultBlockState().setValue(
                            BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR),
                    -0.5d, false, "floor-mounted attachment state");

            assertDeferredConnectionRow(ctx, fenceSupport, Blocks.OAK_FENCE.defaultBlockState(),
                    "four-way fence state");
            assertDeferredConnectionRow(ctx, wallSupport, Blocks.COBBLESTONE_WALL.defaultBlockState(),
                    "four-way wall state");
            assertDeferredConnectionRow(ctx, barsSupport, Blocks.IRON_BARS.defaultBlockState(),
                    "four-way pane state");

            placeSeat(world, wallGrindstoneSupport, Blocks.STONE.defaultBlockState());
            putFact(ctx, world, wallGrindstoneSupport, -1, "wall-grindstone floor control");
            BlockPos wallGrindstone = wallGrindstoneSupport.above();
            BlockState wallGrindstoneState = Blocks.GRINDSTONE.defaultBlockState().setValue(
                    BlockStateProperties.ATTACH_FACE, AttachFace.WALL);
            Direction facing = wallGrindstoneState.getValue(BlockStateProperties.HORIZONTAL_FACING);
            world.setBlock(wallGrindstone.relative(facing.getOpposite()),
                    Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            world.setBlock(wallGrindstone, wallGrindstoneState, Block.UPDATE_CLIENTS);
            assertState(ctx, world, wallGrindstone, wallGrindstoneState,
                    "wall-mounted attachment negative control");
            assertFactAbsent(ctx, world, wallGrindstone, "wall-mounted attachment negative control");
            assertUnauthored(ctx, world, wallGrindstone, "wall-mounted attachment negative control");
            assertExact(ctx, SlabSupport.getSupportFollowerYOffset(world, wallGrindstoneSupport),
                    -0.5d, "the wall-mounted control must have a genuinely lowered cell below it");
            assertExact(ctx, ClientDy.dyFor(world, wallGrindstone, world.getBlockState(wallGrindstone)),
                    0.0d, "a wall-mounted grindstone must not follow the cell below");
            ctx.assertTrue(!SlabSupport.isRawShapeProbeActive(),
                    "the raw floor-contact probe must restore its re-entry fence");
        } finally {
            removeFact(world, saplingSupport);
            removeFact(world, decoratedPotSupport);
            removeFact(world, floorGrindstoneSupport);
            removeFact(world, fenceSupport);
            removeFact(world, wallSupport);
            removeFact(world, barsSupport);
            removeFact(world, wallGrindstoneSupport);
        }

        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void nestedFactlessFollowersUseOneBoundedSupportPath(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos halfStepSupport = absolute(ctx, 1, 2, 1);
        BlockPos productFloorSupport = absolute(ctx, 3, 2, 1);
        BlockPos explicitZeroSupport = absolute(ctx, 5, 2, 1);
        BlockPos deepFlatBase = absolute(ctx, 1, 2, 3);
        BlockPos dualMarkerSupport = absolute(ctx, 1, 2, 5);
        BlockPos cantileverSourceSlab = absolute(ctx, 3, 2, 5);
        BlockPos depthBoundaryFact = absolute(ctx, 5, 1, 5);

        try {
            placeSeat(world, halfStepSupport, Blocks.STONE.defaultBlockState());
            assertNestedFollowerStack(ctx, world, halfStepSupport, -1, -0.5d,
                    "stored half-step ancestry");

            placeSeat(world, productFloorSupport, Blocks.STONE.defaultBlockState());
            assertNestedFollowerStack(ctx, world, productFloorSupport, -2, -1.0d,
                    "stored product-floor ancestry");

            world.setBlock(explicitZeroSupport.below(), slab(SlabType.BOTTOM), Block.UPDATE_ALL);
            world.setBlock(explicitZeroSupport, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            assertExact(ctx, SlabSupport.getUnstoredYOffset(
                            world, explicitZeroSupport, world.getBlockState(explicitZeroSupport)),
                    -0.5d, "the explicit-zero support must hide genuinely lowered legacy geometry");
            assertNestedFollowerStack(ctx, world, explicitZeroSupport, 0, 0.0d,
                    "explicit-zero ancestry");

            placeSeat(world, deepFlatBase, Blocks.STONE.defaultBlockState());
            BlockPos deepFlatTop = deepFlatBase;
            for (int course = 0; course < 6; course++) {
                deepFlatTop = deepFlatTop.above();
                world.setBlock(deepFlatTop, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                assertFactAbsent(ctx, world, deepFlatTop,
                        "deep factless flush ancestry course " + course);
                assertUnauthored(ctx, world, deepFlatTop,
                        "deep factless flush ancestry course " + course);
            }
            assertExact(ctx, ClientDy.dyFor(world, deepFlatTop, world.getBlockState(deepFlatTop)),
                    0.0d, "budget exhaustion must not lower a wholly factless flush column");
            assertExact(ctx, world.getBlockState(deepFlatTop)
                            .getShape(world, deepFlatTop).min(Direction.Axis.Y),
                    0.0d, "the deep factless flush outline must remain on the block grid");

            placeSeat(world, dualMarkerSupport, Blocks.STONE.defaultBlockState());
            SlabAnchorAttachment.freezeLoweredOnPlace(
                    world, dualMarkerSupport, world.getBlockState(dualMarkerSupport));
            ctx.assertTrue(SlabAnchorAttachment.isFrozenFlat(world, dualMarkerSupport),
                    "the dual-marker support must begin with a flat placement marker");
            forceLegacyAnchor(world, dualMarkerSupport);
            ctx.assertTrue(SlabAnchorAttachment.isAnchored(world, dualMarkerSupport)
                            && SlabAnchorAttachment.isFrozenFlat(world, dualMarkerSupport),
                    "the dual-marker control must exercise both legacy markers");
            assertExact(ctx, SlabSupport.getYOffset(
                            world, dualMarkerSupport, world.getBlockState(dualMarkerSupport)),
                    -0.5d, "an anchor must remain the support's own authority over a flat marker");
            assertExact(ctx, SlabSupport.getSupportTopFaceYOffset(world, dualMarkerSupport),
                    -0.5d, "the support face must preserve anchor-before-flat-marker precedence");
            BlockPos dualMarkerSubject = dualMarkerSupport.above();
            world.setBlock(dualMarkerSubject, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            assertFactAbsent(ctx, world, dualMarkerSubject, "dual-marker follower");
            assertUnauthored(ctx, world, dualMarkerSubject, "dual-marker follower");
            assertExact(ctx, ClientDy.dyFor(
                            world, dualMarkerSubject, world.getBlockState(dualMarkerSubject)),
                    -0.5d, "a factless follower must consume the same dual-marker support face");

            world.setBlock(cantileverSourceSlab, slab(SlabType.BOTTOM), Block.UPDATE_CLIENTS);
            BlockPos cantileverSource = cantileverSourceSlab.above();
            BlockPos cantileverSupport = cantileverSource.east();
            BlockPos cantileverSubject = cantileverSupport.above();
            world.setBlock(cantileverSource, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            world.setBlock(cantileverSupport.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            world.setBlock(cantileverSupport, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            world.setBlock(cantileverSubject, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            assertFactAbsent(ctx, world, cantileverSupport, "cantilever support");
            assertUnauthored(ctx, world, cantileverSupport, "cantilever support");
            assertFactAbsent(ctx, world, cantileverSubject, "cantilever follower");
            assertUnauthored(ctx, world, cantileverSubject, "cantilever follower");
            assertExact(ctx, SlabSupport.getYOffset(
                            world, cantileverSupport, world.getBlockState(cantileverSupport)),
                    -0.5d, "the unauthored cantilever support must be genuinely lowered");
            assertExact(ctx, ClientDy.dyFor(
                            world, cantileverSubject, world.getBlockState(cantileverSubject)),
                    -0.5d, "a factless follower must consume the cantilever's actual top face");
            assertExact(ctx, world.getBlockState(cantileverSubject)
                            .getShape(world, cantileverSubject).min(Direction.Axis.Y),
                    -0.5d, "the cantilever follower outline must consume the same support face");

            placeSeat(world, depthBoundaryFact, Blocks.STONE.defaultBlockState());
            putFact(ctx, world, depthBoundaryFact, -1,
                    "last included support-depth fact");
            BlockPos depthBoundarySubject = depthBoundaryFact;
            // The walk reaches as far as the floor allows, so this column tracks the floor:
            // deep enough that the last fact is the final included one. A literal here would
            // stop measuring the boundary the moment the floor moved, which is exactly what
            // it exists to measure. One course per half step, two of seat headroom, plus the
            // boundary course itself.
            int boundaryColumnCourses = (int) Math.ceil(
                    Math.abs(PlacementDepthPolicy.MIN_TARGETABLE_DY) / 0.5d) + 3;
            for (int course = 0; course < boundaryColumnCourses; course++) {
                depthBoundarySubject = depthBoundarySubject.above();
                world.setBlock(depthBoundarySubject,
                        Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            assertExact(ctx, ClientDy.dyFor(
                            world, depthBoundarySubject, world.getBlockState(depthBoundarySubject)),
                    -0.5d, "the last fact inside the bounded walk must still be consumed");
            removeFact(world, depthBoundaryFact);
            BlockPos excludedDepthFact = depthBoundaryFact.below();
            putFact(ctx, world, excludedDepthFact, -1,
                    "first excluded support-depth fact");
            assertExact(ctx, ClientDy.dyFor(
                            world, depthBoundarySubject, world.getBlockState(depthBoundarySubject)),
                    0.0d, "the first fact past the bounded walk must still be ignored");
            assertExact(ctx, world.getBlockState(depthBoundarySubject)
                            .getShape(world, depthBoundarySubject).min(Direction.Axis.Y),
                    0.0d, "the first excluded depth must not shift the subject outline");
        } finally {
            removeFact(world, halfStepSupport);
            removeFact(world, productFloorSupport);
            removeFact(world, explicitZeroSupport);
            removeFact(world, depthBoundaryFact);
            removeFact(world, depthBoundaryFact.below());
            removeAnchor(world, dualMarkerSupport);
        }

        ctx.succeed();
    }

    private static void assertNestedFollowerStack(
            GameTestHelper ctx,
            ServerLevel world,
            BlockPos support,
            int supportHalfSteps,
            double expected,
            String label
    ) {
        putFact(ctx, world, support, supportHalfSteps, label + " support");
        assertFact(ctx, world, support, supportHalfSteps,
                label + " support must retain its exact fact");

        BlockPos lower = support.above();
        BlockPos upper = lower.above();
        world.setBlock(lower, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(upper, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        assertFactAbsent(ctx, world, lower, label + " lower follower");
        assertFactAbsent(ctx, world, upper, label + " upper follower");
        assertUnauthored(ctx, world, lower, label + " lower follower");
        assertUnauthored(ctx, world, upper, label + " upper follower");

        assertExact(ctx, world.getBlockState(upper).getShape(world, upper).min(Direction.Axis.Y),
                expected, label + " upper outline queried first must follow the same ancestry");
        assertExact(ctx, ClientDy.dyFor(world, upper, world.getBlockState(upper)),
                expected, label + " upper follower queried first must inherit the stored height");
        assertExact(ctx, ClientDy.dyFor(world, lower, world.getBlockState(lower)),
                expected, label + " lower follower must inherit the stored height");
        assertExact(ctx, ClientDy.dyFor(world, lower, world.getBlockState(lower)),
                expected, label + " reverse-order lower query must remain exact");
        assertExact(ctx, ClientDy.dyFor(world, upper, world.getBlockState(upper)),
                expected, label + " reverse-order upper query must remain exact");
        ctx.assertTrue(ClientDy.dyFor(world, upper, world.getBlockState(upper)) >= -2.0d,
                label + " must stay inside the placement envelope");
    }

    private static void assertStoredSupportRow(
            GameTestHelper ctx,
            BlockPos support,
            BlockState supportState,
            int supportHalfSteps,
            BlockState subjectState,
            double expected,
            String label
    ) {
        ServerLevel world = ctx.getLevel();
        placeSeat(world, support, supportState);
        assertState(ctx, world, support, supportState, label + " support");
        putFact(ctx, world, support, supportHalfSteps, label);
        assertExact(ctx, SlabPlacementHeightAttachment.storedOffset(world, support), supportHalfSteps * 0.5d,
                label + " must expose its stored support height");
        BlockPos subject = support.above();
        world.setBlock(subject, subjectState, Block.UPDATE_ALL);
        assertState(ctx, world, subject, subjectState, label + " subject");
        assertFactAbsent(ctx, world, subject, label + " subject");
        assertUnauthored(ctx, world, subject, label + " subject");
        assertExact(ctx, SlabSupport.getYOffset(world, subject, world.getBlockState(subject)), expected,
                label + " must expose its exact rendered top face");
    }

    private static void assertFlushSupportRow(
            GameTestHelper ctx,
            BlockPos support,
            BlockState supportState,
            BlockState subjectState,
            double expected,
            String label
    ) {
        ServerLevel world = ctx.getLevel();
        placeSeat(world, support, supportState);
        assertState(ctx, world, support, supportState, label + " support");
        assertFactAbsent(ctx, world, support, label + " support");
        BlockPos subject = support.above();
        world.setBlock(subject, subjectState, Block.UPDATE_ALL);
        assertState(ctx, world, subject, subjectState, label + " subject");
        assertFactAbsent(ctx, world, subject, label + " subject");
        assertUnauthored(ctx, world, subject, label + " subject");
        assertExact(ctx, SlabSupport.getYOffset(world, subject, world.getBlockState(subject)), expected,
                label + " must be distinct from an absent seat");
    }

    private static void assertCarpetFollowerRow(
            GameTestHelper ctx,
            BlockPos support,
            BlockState supportState,
            int supportHalfSteps,
            double expected,
            String label
    ) {
        ServerLevel world = ctx.getLevel();
        placeSeat(world, support, supportState);
        assertState(ctx, world, support, supportState, label);
        putFact(ctx, world, support, supportHalfSteps, label);
        BlockPos carpet = support.above();
        world.setBlock(carpet, Blocks.WHITE_CARPET.defaultBlockState(), Block.UPDATE_ALL);
        assertState(ctx, world, carpet, Blocks.WHITE_CARPET.defaultBlockState(), label + " carpet");
        assertFactAbsent(ctx, world, carpet, label + " carpet");
        assertUnauthored(ctx, world, carpet, label + " carpet");
        assertExact(ctx, ClientDy.dyFor(world, carpet, world.getBlockState(carpet)), expected,
                label + " must expose its resolved support height to the carpet follower");
    }

    private static void assertFloorFollowerRow(
            GameTestHelper ctx,
            BlockPos support,
            BlockState supportState,
            int supportHalfSteps,
            BlockState subjectState,
            double expected,
            boolean requireBlockEntity,
            String label
    ) {
        ServerLevel world = ctx.getLevel();
        placeSeat(world, support, supportState);
        assertState(ctx, world, support, supportState, label + " support");
        putFact(ctx, world, support, supportHalfSteps, label);
        assertFact(ctx, world, support, supportHalfSteps,
                label + " support must retain its exact stored height");
        assertExact(ctx, SlabPlacementHeightAttachment.storedOffset(world, support),
                supportHalfSteps * 0.5d, label + " support must expose its stored height");
        assertExact(ctx, SlabSupport.getSupportFollowerYOffset(world, support), expected,
                label + " support must expose the expected resolved face before subject resolution");
        BlockPos subject = support.above();
        world.setBlock(subject, subjectState, Block.UPDATE_ALL);
        assertState(ctx, world, subject, subjectState, label);
        assertFactAbsent(ctx, world, subject, label);
        assertUnauthored(ctx, world, subject, label);
        ctx.assertTrue(world.getBlockState(subject).canSurvive(world, subject),
                label + " must remain a valid floor-supported state");
        if (requireBlockEntity) {
            ctx.assertTrue(world.getBlockEntity(subject) != null,
                    label + " must retain its live block entity");
        }
        assertExact(ctx, ClientDy.dyFor(world, subject, world.getBlockState(subject)), expected,
                label + " must follow the support's resolved top face");
        assertExact(ctx, world.getBlockState(subject).getShape(world, subject).min(Direction.Axis.Y), expected,
                label + " outline must consume the same support-relative height");
        assertExact(ctx, ClientDy.dyFor(world, subject, world.getBlockState(subject)), expected,
                label + " repeated resolution must remain finite and deterministic");
        ctx.assertTrue(!SlabSupport.isRawShapeProbeActive(),
                label + " must not leak the raw floor-contact probe");
    }

    private static void assertDeferredConnectionRow(
            GameTestHelper ctx,
            BlockPos support,
            BlockState connectionState,
            String label
    ) {
        ServerLevel world = ctx.getLevel();
        placeSeat(world, support, Blocks.STONE.defaultBlockState());
        putFact(ctx, world, support, -1, label + " support");
        assertExact(ctx, SlabSupport.getSupportFollowerYOffset(world, support), -0.5d,
                label + " must have a genuinely lowered support face");
        BlockPos subject = support.above();
        world.setBlock(subject, connectionState, Block.UPDATE_ALL);
        ctx.assertTrue(world.getBlockState(subject).is(connectionState.getBlock()),
                label + " must occupy the tested cell");
        assertFactAbsent(ctx, world, subject, label);
        assertUnauthored(ctx, world, subject, label);
        assertExact(ctx, ClientDy.dyFor(world, subject, world.getBlockState(subject)), 0.0d,
                label + " must remain on its separate connection-geometry owner");
        assertExact(ctx, world.getBlockState(subject).getShape(world, subject).min(Direction.Axis.Y),
                0.0d, label + " outline must remain unshifted with its connection model");
    }

    private static void placeHeldBlock(
            GameTestHelper ctx,
            BlockPos clicked,
            Direction face,
            BlockState heldState
    ) {
        Player player = ctx.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(clicked.getX() + 0.5d, clicked.getY() + 2.0d, clicked.getZ() + 0.5d);
        ItemStack stack = new ItemStack(heldState.getBlock());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hitLocation = Vec3.atCenterOf(clicked).add(
                face.getStepX() * 0.5d,
                face.getStepY() * 0.5d,
                face.getStepZ() * 0.5d);
        InteractionResult result = stack.useOn(new UseOnContext(
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(hitLocation, face, clicked, false)));
        ctx.assertTrue(result.consumesAction(), "the real held-item placement must be accepted");
    }

    private static void assertState(
            GameTestHelper ctx,
            ServerLevel world,
            BlockPos pos,
            BlockState expected,
            String label
    ) {
        ctx.assertTrue(world.getBlockState(pos).equals(expected),
                label + " must occupy the tested world cell");
    }

    private static void placeSeat(ServerLevel world, BlockPos support, BlockState supportState) {
        world.setBlock(support.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(support, supportState, Block.UPDATE_ALL);
    }

    private static void putFact(
            GameTestHelper ctx,
            ServerLevel world,
            BlockPos pos,
            int halfSteps,
            String label
    ) {
        ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(chunk(world, pos), pos, halfSteps),
                label + " must receive its exact support fact");
    }

    private static void forceLegacyAnchor(ServerLevel world, BlockPos pos) {
        LevelChunk chunk = chunk(world, pos);
        LongOpenHashSet existing = chunk.getExistingDataOrNull(SlabAnchorAttachment.ANCHOR_TYPE.get());
        LongOpenHashSet replacement = existing == null
                ? new LongOpenHashSet()
                : new LongOpenHashSet(existing);
        replacement.add(pos.asLong());
        chunk.setData(SlabAnchorAttachment.ANCHOR_TYPE.get(), replacement);
    }

    private static void assertFactAbsent(
            GameTestHelper ctx,
            ServerLevel world,
            BlockPos pos,
            String label
    ) {
        ctx.assertTrue(SlabPlacementHeightAttachment.storedHalfSteps(chunk(world, pos), pos).isEmpty(),
                label + " must remain on the legacy factless path");
    }

    private static void assertFact(
            GameTestHelper ctx,
            ServerLevel world,
            BlockPos pos,
            int expectedHalfSteps,
            String label
    ) {
        ctx.assertTrue(SlabPlacementHeightAttachment.storedHalfSteps(chunk(world, pos), pos)
                        .orElse(Integer.MIN_VALUE) == expectedHalfSteps,
                label + "; expected=" + expectedHalfSteps);
    }

    private static void assertUnauthored(
            GameTestHelper ctx,
            ServerLevel world,
            BlockPos pos,
            String label
    ) {
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(world, pos)
                        && !SlabAnchorAttachment.isFrozenFlat(world, pos),
                label + " must have no legacy authored-height marker");
    }

    private static void removeFact(ServerLevel world, BlockPos pos) {
        SlabPlacementHeightAttachment.remove(chunk(world, pos), pos);
    }

    private static void removeAnchor(ServerLevel world, BlockPos pos) {
        SlabAnchorAttachment.removeAnchor(world, pos);
    }

    private static LevelChunk chunk(ServerLevel world, BlockPos pos) {
        return world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static BlockState slab(SlabType type) {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, type);
    }

    private static BlockPos absolute(GameTestHelper ctx, int x, int y, int z) {
        return ctx.absolutePos(new BlockPos(x, y, z));
    }

    private static void assertExact(GameTestHelper ctx, double actual, double expected, String message) {
        ctx.assertTrue(Double.doubleToRawLongBits(actual) == Double.doubleToRawLongBits(expected),
                message + "; expected=" + expected + " actual=" + actual);
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
}
