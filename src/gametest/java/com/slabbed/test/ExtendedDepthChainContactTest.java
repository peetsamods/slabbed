package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedOffsetRaycast;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Real-placement and outline proof for vertical continuations below slab caps through dy -3.
 *
 * <p>This is not a rendered-mesh oracle. It proves the common predicate that selects the known
 * elongated chain mesh, exact outline bounds, and preservation of vanilla-baseline outline gaps.
 * A live client remains required before calling the resulting scene visually gap-free.
 */
public final class ExtendedDepthChainContactTest {
    private static final double EPSILON = 1.0e-6d;
    private static final double[] DEPTHS = {0.0d, -0.5d, -1.0d, -1.5d, -2.0d, -2.5d, -3.0d};

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void slabCapChainLanternAndDripstoneOutlineMatrix(TestContext h) {
        Failures failures = new Failures();
        withFrozenDy(() -> {
            ServerWorld world = h.getWorld();
            BlockPos capPos = h.getAbsolutePos(new BlockPos(3, 9, 3));
            for (SlabType capType : SlabType.values()) {
                for (double capDy : DEPTHS) {
                    runCase(failures, "chain/" + capType + "@" + capDy,
                            () -> chainLanternCase(h, world, capPos, capType, capDy));
                    runCase(failures, "dripstone/" + capType + "@" + capDy,
                            () -> dripstoneCase(h, world, capPos, capType, capDy));
                }
            }
        });
        failures.finish(h, "slab-cap continuation matrix");
    }

    private static void chainLanternCase(
            TestContext h, ServerWorld world, BlockPos capPos, SlabType capType, double capDy) {
        clearArena(world, capPos);
        seedCap(world, capPos, capType, capDy);

        boolean expectedAlternate = capType == SlabType.TOP && sameBits(capDy, 0.0d);
        double chainDy = expectedAlternate
                ? 0.0d
                : capDy + (capType == SlabType.TOP ? 0.5d : 0.0d);
        BlockPos first = placeBelow(h, world, capPos, Items.CHAIN, Blocks.CHAIN, chainDy);
        BlockPos second = placeBelow(h, world, first, Items.CHAIN, Blocks.CHAIN, chainDy);
        BlockPos lantern = placeBelow(h, world, second, Items.LANTERN, Blocks.LANTERN, chainDy);

        boolean actualAlternate = SlabSupport.isVerticalChainDirectlyUnderCeilingSupport(
                world, first, world.getBlockState(first));
        require(actualAlternate == expectedAlternate,
                "alternate-chain predicate=" + actualAlternate + " wanted=" + expectedAlternate
                        + " capType=" + capType + " capDy=" + capDy + " chainDy=" + chainDy);

        Bounds chainBaseline = baselineBounds(verticalChainState());
        Bounds firstActual = worldBounds(world, first);
        if (expectedAlternate) {
            require(near(firstActual.minY(), first.getY())
                            && near(firstActual.maxY(), first.getY() + 1.5d),
                    "flat TOP bridge outline=" + firstActual + " wanted=["
                            + first.getY() + "," + (first.getY() + 1.5d) + "]");
        } else {
            assertTranslatedLikeBaseline("direct chain", first, chainDy, chainBaseline, firstActual);
        }

        Bounds secondBaseline = baselineBounds(world.getBlockState(second));
        Bounds secondActual = worldBounds(world, second);
        assertTranslatedLikeBaseline("continuation chain", second, chainDy, secondBaseline, secondActual);

        Bounds lanternBaseline = baselineBounds(world.getBlockState(lantern));
        Bounds lanternActual = worldBounds(world, lantern);
        assertTranslatedLikeBaseline("hanging lantern", lantern, chainDy, lanternBaseline, lanternActual);

        double capUnderside = capPos.getY() + capDy + (capType == SlabType.TOP ? 0.5d : 0.0d);
        require(near(firstActual.maxY(), capUnderside),
                "cap/direct-chain outline contact differs: capUnderside=" + capUnderside
                        + " chainMax=" + firstActual.maxY());

        double actualChainGap = firstActual.minY() - secondActual.maxY();
        double baselineChainGap = chainBaseline.minY() - secondBaseline.maxY() + 1.0d;
        require(near(actualChainGap, baselineChainGap),
                "chain continuation changed vanilla outline gap: actual=" + actualChainGap
                        + " baseline=" + baselineChainGap);

        double actualLanternGap = secondActual.minY() - lanternActual.maxY();
        double baselineLanternGap = secondBaseline.minY() - lanternBaseline.maxY() + 1.0d;
        require(near(actualLanternGap, baselineLanternGap),
                "chain/lantern changed vanilla outline gap: actual=" + actualLanternGap
                        + " baseline=" + baselineLanternGap);
    }

    private static void dripstoneCase(
            TestContext h, ServerWorld world, BlockPos capPos, SlabType capType, double capDy) {
        clearArena(world, capPos);
        seedCap(world, capPos, capType, capDy);

        double dripstoneDy = capDy + (capType == SlabType.TOP ? 0.5d : 0.0d);
        BlockPos first = placeBelow(
                h, world, capPos, Items.POINTED_DRIPSTONE, Blocks.POINTED_DRIPSTONE, dripstoneDy);
        BlockPos second = placeBelow(
                h, world, first, Items.POINTED_DRIPSTONE, Blocks.POINTED_DRIPSTONE, dripstoneDy);

        BlockState firstState = world.getBlockState(first);
        BlockState secondState = world.getBlockState(second);
        require(firstState.contains(Properties.VERTICAL_DIRECTION)
                        && firstState.get(Properties.VERTICAL_DIRECTION) == Direction.DOWN
                        && secondState.contains(Properties.VERTICAL_DIRECTION)
                        && secondState.get(Properties.VERTICAL_DIRECTION) == Direction.DOWN,
                "dripstone continuation is not a downward pair");

        Bounds firstBaseline = baselineBounds(firstState);
        Bounds secondBaseline = baselineBounds(secondState);
        Bounds firstActual = worldBounds(world, first);
        Bounds secondActual = worldBounds(world, second);
        assertTranslatedLikeBaseline("direct dripstone", first, dripstoneDy, firstBaseline, firstActual);
        assertTranslatedLikeBaseline("continuation dripstone", second, dripstoneDy, secondBaseline, secondActual);

        double capUnderside = capPos.getY() + capDy + (capType == SlabType.TOP ? 0.5d : 0.0d);
        double actualCapGap = capUnderside - firstActual.maxY();
        double baselineCapGap = 1.0d - firstBaseline.maxY();
        require(near(actualCapGap, baselineCapGap),
                "cap/dripstone changed vanilla outline gap: actual=" + actualCapGap
                        + " baseline=" + baselineCapGap);

        double actualPairGap = firstActual.minY() - secondActual.maxY();
        double baselinePairGap = firstBaseline.minY() - secondBaseline.maxY() + 1.0d;
        require(near(actualPairGap, baselinePairGap),
                "dripstone continuation changed vanilla outline gap: actual=" + actualPairGap
                        + " baseline=" + baselinePairGap);
    }

    private static BlockPos placeBelow(
            TestContext h,
            ServerWorld world,
            BlockPos owner,
            Item item,
            Block expectedBlock,
            double expectedDy
    ) {
        BlockHitResult hit = rayAtFace(world, owner, Direction.DOWN);
        require(hit.getType() == HitResult.Type.BLOCK
                        && hit.getBlockPos().equals(owner)
                        && hit.getSide() == Direction.DOWN,
                "placement ray did not own underside: hit=" + hit.getType()
                        + " pos=" + hit.getBlockPos() + " face=" + hit.getSide());
        PlayerEntity player = h.createMockPlayer(GameMode.SURVIVAL);
        positionPlayerAway(player, rayStart(world, owner, Direction.DOWN));
        ItemStack stack = new ItemStack(item);
        player.setStackInHand(Hand.MAIN_HAND, stack);
        ActionResult result = stack.useOnBlock(
                new net.minecraft.item.ItemUsageContext(player, Hand.MAIN_HAND, hit));
        BlockPos placed = owner.down();
        require(result.isAccepted(), "placement was not accepted: " + result);
        require(world.getBlockState(placed).isOf(expectedBlock),
                "wrong placed block: " + world.getBlockState(placed));
        require(sameBits(stored(world, placed), expectedDy),
                "stored dy=" + stored(world, placed) + " wanted=" + expectedDy);
        require(sameBits(SlabSupport.getYOffset(world, placed, world.getBlockState(placed)), expectedDy),
                "live dy=" + SlabSupport.getYOffset(world, placed, world.getBlockState(placed))
                        + " wanted=" + expectedDy);
        require(world.getBlockState(placed).canPlaceAt(world, placed), "placed block is not legally supported");
        return placed;
    }

    private static void seedCap(ServerWorld world, BlockPos capPos, SlabType type, double dy) {
        BlockState cap = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, type);
        world.setBlockState(capPos, cap, Block.NOTIFY_ALL);
        int writes = SlabAnchorAttachment.writePlacementDyBatch(
                world, Map.of(capPos.toImmutable(), Double.doubleToRawLongBits(dy)));
        require(writes == 1, "fixture did not author cap dy=" + dy);
        require(sameBits(stored(world, capPos), dy), "fixture cap dy mismatch");
    }

    private static BlockState verticalChainState() {
        return Blocks.CHAIN.getDefaultState().with(Properties.AXIS, Direction.Axis.Y);
    }

    private static Bounds baselineBounds(BlockState state) {
        VoxelShape shape = state.getOutlineShape(
                EmptyBlockView.INSTANCE, BlockPos.ORIGIN, ShapeContext.absent());
        require(shape != null && !shape.isEmpty(), "vanilla-baseline outline is empty for " + state);
        Box box = shape.getBoundingBox();
        return new Bounds(box.minY, box.maxY);
    }

    private static Bounds worldBounds(ServerWorld world, BlockPos pos) {
        VoxelShape shape = world.getBlockState(pos).getOutlineShape(world, pos, ShapeContext.absent());
        require(shape != null && !shape.isEmpty(), "world outline is empty at " + pos.toShortString());
        Box box = shape.getBoundingBox();
        return new Bounds(pos.getY() + box.minY, pos.getY() + box.maxY);
    }

    private static void assertTranslatedLikeBaseline(
            String label, BlockPos pos, double dy, Bounds baseline, Bounds actualWorld) {
        double expectedMin = pos.getY() + baseline.minY() + dy;
        double expectedMax = pos.getY() + baseline.maxY() + dy;
        require(near(actualWorld.minY(), expectedMin) && near(actualWorld.maxY(), expectedMax),
                label + " outline=" + actualWorld + " wanted=[" + expectedMin + "," + expectedMax + "]");
    }

    private static BlockHitResult rayAtFace(ServerWorld world, BlockPos owner, Direction face) {
        Vec3d start = rayStart(world, owner, face);
        Vec3d center = visibleCenter(world, owner);
        Vec3d end = center.subtract(
                face.getOffsetX() * 2.0d, face.getOffsetY() * 2.0d, face.getOffsetZ() * 2.0d);
        return SlabbedOffsetRaycast.raycast(world, start, end, ShapeContext.absent());
    }

    private static Vec3d rayStart(ServerWorld world, BlockPos owner, Direction face) {
        Vec3d center = visibleCenter(world, owner);
        return center.add(
                face.getOffsetX() * 2.0d, face.getOffsetY() * 2.0d, face.getOffsetZ() * 2.0d);
    }

    private static Vec3d visibleCenter(ServerWorld world, BlockPos pos) {
        VoxelShape outline = world.getBlockState(pos).getOutlineShape(world, pos, ShapeContext.absent());
        require(outline != null && !outline.isEmpty(), "ray owner outline is empty");
        Box bounds = outline.getBoundingBox();
        return new Vec3d(
                pos.getX() + (bounds.minX + bounds.maxX) * 0.5d,
                pos.getY() + (bounds.minY + bounds.maxY) * 0.5d,
                pos.getZ() + (bounds.minZ + bounds.maxZ) * 0.5d);
    }

    private static void positionPlayerAway(PlayerEntity player, Vec3d eye) {
        player.setPosition(eye.x + 8.0d, eye.y - player.getStandingEyeHeight(), eye.z + 8.0d);
    }

    private static double stored(ServerWorld world, BlockPos pos) {
        return SlabAnchorAttachment.rawPlacementDyFact(world, pos).valueOrNaN();
    }

    private static boolean sameBits(double left, double right) {
        return Double.doubleToRawLongBits(left) == Double.doubleToRawLongBits(right);
    }

    private static boolean near(double left, double right) {
        return Math.abs(left - right) <= EPSILON;
    }

    private static void clearArena(ServerWorld world, BlockPos center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -6; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    if (!world.getBlockState(pos).isAir()) {
                        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }
    }

    private static void withFrozenDy(Runnable action) {
        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            action.run();
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previous;
        }
    }

    private static void runCase(Failures failures, String id, Runnable action) {
        try {
            action.run();
        } catch (Throwable throwable) {
            failures.add(id + ": " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Bounds(double minY, double maxY) {
    }

    private static final class Failures {
        private static final int MAX_DETAILS = 40;
        private final List<String> details = new ArrayList<>();
        private int total;

        void add(String detail) {
            total++;
            if (details.size() < MAX_DETAILS) {
                details.add(detail);
            }
        }

        void finish(TestContext h, String group) {
            for (String detail : details) {
                Slabbed.LOGGER.error("[EXTENDED_DEPTH_CHAIN_FAILURE] group={} detail={}", group, detail);
            }
            List<String> shortDetails = details.stream().limit(3)
                    .map(detail -> detail.length() <= 220 ? detail : detail.substring(0, 220))
                    .toList();
            h.assertTrue(total == 0, group + " failures=" + total + "; first=" + shortDetails);
            h.complete();
        }
    }
}
