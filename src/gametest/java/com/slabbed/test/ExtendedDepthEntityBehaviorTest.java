package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RailBlock;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.GlowItemFrameEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.AfterBatch;
import net.minecraft.test.BeforeBatch;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Physical-behavior proof for the attached entity consumers already supported by Slabbed. */
public final class ExtendedDepthEntityBehaviorTest {
    private static final double DEEP_DY = -3.0d;
    private static final double EPSILON = 1.0e-6d;
    private static final String BATCH = "slabbed_extended_depth_entities";
    private static final String RAIL_BATCH = "slabbed_rail_coordinates";
    private static boolean frozenBeforeBatch;

    @BeforeBatch(batchId = BATCH)
    public static void enableFrozenDyForBatch(ServerWorld world) {
        frozenBeforeBatch = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
    }

    @AfterBatch(batchId = BATCH)
    public static void restoreFrozenDyAfterBatch(ServerWorld world) {
        SlabAnchorAttachment.FROZEN_DY_ENABLED = frozenBeforeBatch;
    }

    @BeforeBatch(batchId = RAIL_BATCH)
    public static void enableRailCoordinates(ServerWorld world) {
        enableFrozenDyForBatch(world);
    }

    @AfterBatch(batchId = RAIL_BATCH)
    public static void restoreRailCoordinates(ServerWorld world) {
        restoreFrozenDyAfterBatch(world);
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty", batchId = BATCH)
    public void itemFramesPlaceTargetAndSurviveAtRenderedDepth(TestContext context) {
        ServerWorld world = context.getWorld();
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        List<String> failures = new ArrayList<>();
        BlockPos origin = context.getAbsolutePos(new BlockPos(4, 16, 4));
        FramePair regular = placeFramePair(
                context, world, player, origin, Direction.EAST, Items.ITEM_FRAME, false);
        FramePair glow = placeFramePair(
                context, world, player, origin.add(8, 0, 0), Direction.UP, Items.GLOW_ITEM_FRAME, true);

        inspectFramePair(player, regular, "wall item frame", failures);
        inspectFramePair(player, glow, "floor glow item frame", failures);

        world.setBlockState(regular.deepBacking().north(), Blocks.DIRT.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(regular.deepBacking().north(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(glow.deepBacking().east(), Blocks.DIRT.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(glow.deepBacking().east(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

        context.runAtTick(8, () -> {
            requireAliveAndSupported(regular.deep(), "wall item frame", failures);
            requireAliveAndSupported(glow.deep(), "floor glow item frame", failures);
            inspectFramePair(player, regular, "wall item frame after ticks", failures);
            inspectFramePair(player, glow, "floor glow item frame after ticks", failures);
            requireReloadedFrame(world, regular.deep(), false, failures);
            requireReloadedFrame(world, glow.deep(), true, failures);
            if (!failures.isEmpty()) {
                context.throwGameTestException("EXTENDED_DEPTH_FRAME_ENTITY_RED "
                        + String.join("; ", failures));
            }
            context.complete();
        });
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty", batchId = BATCH)
    public void minecartMovesSeatsTargetsAndInteractsAtRenderedDepth(TestContext context) {
        ServerWorld world = context.getWorld();
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        List<String> failures = new ArrayList<>();
        BlockPos flatRail = context.getAbsolutePos(new BlockPos(4, 16, 4));
        BlockPos deepRail = context.getAbsolutePos(new BlockPos(4, 16, 12));
        buildRailRun(world, flatRail, 0.0d);
        buildRailRun(world, deepRail, DEEP_DY);

        AbstractMinecartEntity flat = placeMinecart(world, player, flatRail, "flat minecart");
        AbstractMinecartEntity deep = placeMinecart(world, player, deepRail, "deep minecart");
        ArmorStandEntity flatPassenger = passenger(world, flat);
        ArmorStandEntity deepPassenger = passenger(world, deep);
        double flatStartX = flat.getX();
        double deepStartX = deep.getX();
        flat.setVelocity(0.18d, 0.0d, 0.0d);
        deep.setVelocity(0.18d, 0.0d, 0.0d);

        context.runAtTick(8, () -> {
                    double flatTravel = flat.getX() - flatStartX;
                    double deepTravel = deep.getX() - deepStartX;
                    if (!flat.isAlive() || !deep.isAlive() || !flat.isOnRail() || !deep.isOnRail()) {
                        failures.add("minecarts must remain alive and on rail after bounded movement");
                    }
                    if (flatTravel <= 0.05d || deepTravel <= 0.05d
                            || Math.abs(flatTravel - deepTravel) > 0.15d) {
                        failures.add("movement parity flat=" + flatTravel + " deep=" + deepTravel);
                    }

                    Box expectedDeepBox = flat.getBoundingBox().offset(
                            deep.getX() - flat.getX(),
                            deepRail.getY() - flatRail.getY() + DEEP_DY,
                            deep.getZ() - flat.getZ());
                    requireBox("minecart", expectedDeepBox, deep.getBoundingBox(), failures);
                    double expectedPassengerY = flatPassenger.getY()
                            + deepRail.getY() - flatRail.getY() + DEEP_DY;
                    if (Math.abs(deepPassenger.getY() - expectedPassengerY) > EPSILON) {
                        failures.add("passenger seat flatY=" + flatPassenger.getY()
                                + " deepY=" + deepPassenger.getY()
                                + " expectedDeepY=" + expectedPassengerY);
                    }

                    EntityHitResult hit = rayAtExpectedBox(player, deep, expectedDeepBox, Direction.SOUTH);
                    if (hit == null || hit.getEntity() != deep) {
                        failures.add("visible minecart ray did not target the deep minecart");
                    }
                    deepPassenger.stopRiding();
                    ActionResult interaction = deep.interact(player, Hand.MAIN_HAND);
                    if (!interaction.isAccepted() || !deep.hasPassenger(player)) {
                        failures.add("minecart interaction did not mount the player: " + interaction);
                    }

                    if (!failures.isEmpty()) {
                        context.throwGameTestException("EXTENDED_DEPTH_MINECART_ENTITY_RED "
                                + String.join("; ", failures));
                    }
                    context.complete();
        });
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty", batchId = RAIL_BATCH)
    public void minecartRailQueriesReloadAndReentryUsePhysicalCoordinates(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos flatRail = context.getAbsolutePos(new BlockPos(4, 16, 4));
        BlockPos deepRail = flatRail.south(10);
        List<String> failures = new ArrayList<>();
        int checks = 0;
        for (RailShape shape : RailShape.values()) {
            railShape(world, flatRail, shape, 0.0d);
            railShape(world, deepRail, shape, DEEP_DY);
            for (int above = 0; above <= 1; above++) {
                double y = flatRail.getY() + 0.0625d + above;
                MinecartEntity flat = new MinecartEntity(world, flatRail.getX() + 0.5d, y, flatRail.getZ() + 0.5d);
                MinecartEntity deep = new MinecartEntity(world, deepRail.getX() + 0.5d, y, deepRail.getZ() + 0.5d);
                Vec3d delta = new Vec3d(0.0d, DEEP_DY, 10.0d);
                requireRailSnap(shape + " direct " + above,
                        flat.snapPositionToRail(flat.getX(), flat.getY(), flat.getZ()),
                        deep.snapPositionToRail(deep.getX(), deep.getY(), deep.getZ()), delta, failures);
                checks++;
                for (double offset : new double[]{-0.3d, 0.3d}) {
                    requireRailSnap(shape + " offset " + offset + " lookup " + above,
                            flat.snapPositionToRailWithOffset(flat.getX(), flat.getY(), flat.getZ(), offset),
                            deep.snapPositionToRailWithOffset(deep.getX(), deep.getY(), deep.getZ(), offset), delta, failures);
                    checks++;
                }
                NbtCompound saved = new NbtCompound();
                deep.writeNbt(saved);
                MinecartEntity restored = new MinecartEntity(EntityType.MINECART, world);
                restored.readNbt(saved);
                if (saved.getDouble("slabbed:rail_dy") != DEEP_DY) failures.add("minecart height missing from NBT");
                requireBox("reloaded minecart", deep.getBoundingBox(), restored.getBoundingBox(), failures);
                requireRailSnap("reloaded " + shape,
                        deep.snapPositionToRail(deep.getX(), deep.getY(), deep.getZ()),
                        restored.snapPositionToRail(restored.getX(), restored.getY(), restored.getZ()),
                        Vec3d.ZERO, failures);
            }
        }
        if (!failures.isEmpty()) {
            System.out.println("MINECART_RAIL_QUERY_FAILURES " + String.join("; ", failures));
            context.throwGameTestException(failures.getFirst());
        }
        railShape(world, deepRail, RailShape.EAST_WEST, DEEP_DY);
        MinecartEntity cart = new MinecartEntity(world, deepRail.getX() + 0.5d,
                deepRail.getY() + DEEP_DY + 0.0625d, deepRail.getZ() + 4.5d);
        cart.setNoGravity(true);
        cart.setVelocity(0.0d, 0.0d, 0.1d);
        double offRailStart = cart.getZ();
        if (!world.spawnEntity(cart)) context.throwGameTestException("off-rail fixture spawn failed");
        context.runAtTick(2, () -> {
            if (cart.isOnRail() || cart.getZ() - offRailStart < 0.05d) {
                context.throwGameTestException("fixture did not reach vanilla off-rail movement");
            }
            cart.setVelocity(Vec3d.ZERO);
            cart.setPosition(deepRail.getX() + 0.5d, deepRail.getY() + DEEP_DY + 0.0625d,
                    deepRail.getZ() + 0.5d);
        });
        int queryChecks = checks;
        context.runAtTick(5, () -> {
            if (!cart.isAlive() || !cart.isOnRail()
                    || !near(cart.getY(), deepRail.getY() + DEEP_DY + 0.0625d)) {
                context.throwGameTestException("minecart did not re-enter the physical deep rail");
            }
            System.out.println("MINECART_RAIL_COORDINATES pairedSnapChecks=" + queryChecks
                    + " reload=PASS offRailMovement=PASS deepRailReentry=PASS");
            context.complete();
        });
    }

    private static void railShape(ServerWorld world, BlockPos rail, RailShape shape, double dy) {
        world.setBlockState(rail.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        for (Direction side : Direction.Type.HORIZONTAL) {
            BlockPos support = rail.offset(side);
            world.setBlockState(support, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
            SlabAnchorAttachment.writePlacementDyBatch(world, Map.of(support, Double.doubleToRawLongBits(dy)));
        }
        world.setBlockState(rail, Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, shape),
                Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        SlabAnchorAttachment.writePlacementDyBatch(world, Map.of(rail, Double.doubleToRawLongBits(dy)));
        if (!world.getBlockState(rail).isOf(Blocks.RAIL)
                || world.getBlockState(rail).get(RailBlock.SHAPE) != shape) {
            throw new IllegalStateException("rail fixture did not retain shape " + shape);
        }
    }

    private static void requireRailSnap(String label, Vec3d flat, Vec3d deep, Vec3d delta, List<String> failures) {
        if (flat == null || deep == null || flat.add(delta).squaredDistanceTo(deep) > EPSILON * EPSILON) {
            failures.add(label + " rail snap mismatch flat=" + flat + " deep=" + deep);
        }
    }

    private static FramePair placeFramePair(
            TestContext context,
            ServerWorld world,
            PlayerEntity player,
            BlockPos flatBacking,
            Direction facing,
            Item item,
            boolean glow) {
        BlockPos deepBacking = flatBacking.add(0, 0, 4);
        seed(world, flatBacking, 0.0d);
        seed(world, deepBacking, DEEP_DY);
        ItemFrameEntity flat = placeFrame(world, player, flatBacking, facing, item, glow, "flat frame");
        ItemFrameEntity deep = placeFrame(world, player, deepBacking, facing, item, glow, "deep frame");
        return new FramePair(
                flatBacking, deepBacking,
                flatBacking.offset(facing), deepBacking.offset(facing),
                flat, deep);
    }

    private static ItemFrameEntity placeFrame(
            ServerWorld world,
            PlayerEntity player,
            BlockPos backing,
            Direction facing,
            Item item,
            boolean glow,
            String label) {
        ItemStack stack = new ItemStack(item);
        player.setStackInHand(Hand.MAIN_HAND, stack);
        Vec3d hitPos = Vec3d.ofCenter(backing).add(
                facing.getOffsetX() * 0.5d,
                facing.getOffsetY() * 0.5d,
                facing.getOffsetZ() * 0.5d);
        ActionResult result = stack.useOnBlock(new ItemUsageContext(
                player, Hand.MAIN_HAND,
                new BlockHitResult(hitPos, facing, backing, false)));
        if (!result.isAccepted()) {
            throw new AssertionError(label + " item placement failed: " + result);
        }
        BlockPos attached = backing.offset(facing);
        List<ItemFrameEntity> frames = world.getEntitiesByClass(
                ItemFrameEntity.class,
                new Box(attached).expand(4.0d),
                frame -> frame.getAttachedBlockPos().equals(attached)
                        && (glow == (frame instanceof GlowItemFrameEntity)));
        if (frames.size() != 1) {
            throw new AssertionError(label + " expected exactly one placed frame, found=" + frames.size());
        }
        return frames.getFirst();
    }

    private static void inspectFramePair(
            PlayerEntity player, FramePair pair, String label, List<String> failures) {
        Box flatLocal = pair.flat().getBoundingBox().offset(
                -pair.flatAttached().getX(), -pair.flatAttached().getY(), -pair.flatAttached().getZ());
        Box expectedDeep = flatLocal.offset(pair.deepAttached()).offset(0.0d, DEEP_DY, 0.0d);
        requireBox(label, expectedDeep, pair.deep().getBoundingBox(), failures);
        EntityHitResult hit = rayAtExpectedBox(
                player, pair.deep(), expectedDeep, pair.deep().getHorizontalFacing());
        if (hit == null || hit.getEntity() != pair.deep()) {
            failures.add(label + " visible ray did not target the deep frame");
        }
    }

    private static void requireAliveAndSupported(
            ItemFrameEntity frame, String label, List<String> failures) {
        if (!frame.isAlive() || frame.isRemoved()) {
            failures.add(label + " did not persist through bounded ticks");
        } else if (!frame.canStayAttached()) {
            failures.add(label + " physical attachment no longer reaches its backing support");
        }
    }

    private static EntityHitResult rayAtExpectedBox(
            PlayerEntity source, Entity expected, Box visibleBox, Direction outward) {
        Vec3d normal = Vec3d.of(outward.getVector());
        Vec3d center = visibleBox.getCenter();
        Vec3d start = center.add(normal.multiply(2.0d));
        Vec3d end = center.subtract(normal.multiply(2.0d));
        return ProjectileUtil.raycast(
                source,
                start,
                end,
                new Box(start, end).expand(0.25d),
                entity -> entity == expected,
                start.squaredDistanceTo(end));
    }

    private static void requireReloadedFrame(
            ServerWorld world, ItemFrameEntity original, boolean glow, List<String> failures) {
        NbtCompound saved = new NbtCompound();
        original.writeNbt(saved);
        if (!saved.contains("slabbed:frame_dy", 99)
                || saved.getDouble("slabbed:frame_dy") != DEEP_DY) {
            failures.add("frame did not save its physical height");
        }
        ItemFrameEntity restored = glow
                ? new GlowItemFrameEntity(EntityType.GLOW_ITEM_FRAME, world)
                : new ItemFrameEntity(EntityType.ITEM_FRAME, world);
        restored.readNbt(saved);
        requireBox("reloaded frame", original.getBoundingBox(), restored.getBoundingBox(), failures);
        if (!original.getAttachedBlockPos().equals(restored.getAttachedBlockPos())
                || !original.getPos().equals(restored.getPos())) {
            failures.add("frame reload changed its logical attachment or physical position");
        }
        var entries = original.getDataTracker().getChangedEntries();
        if (entries == null || entries.stream().noneMatch(entry ->
                entry.value() instanceof Long bits
                        && bits == Double.doubleToRawLongBits(DEEP_DY))) {
            failures.add("frame height is absent from the entity synchronization payload");
        }
    }

    private static void buildRailRun(ServerWorld world, BlockPos start, double dy) {
        for (int x = 0; x < 7; x++) {
            BlockPos rail = start.east(x);
            world.setBlockState(rail.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(rail, Blocks.RAIL.getDefaultState(), Block.NOTIFY_ALL);
            int writes = SlabAnchorAttachment.writePlacementDyBatch(
                    world, Map.of(rail.toImmutable(), Double.doubleToRawLongBits(dy)));
            if (writes != 1) {
                throw new AssertionError("rail fixture refused stored dy=" + dy + " at " + rail.toShortString());
            }
        }
    }

    private static AbstractMinecartEntity placeMinecart(
            ServerWorld world, PlayerEntity player, BlockPos rail, String label) {
        ItemStack stack = new ItemStack(Items.MINECART);
        player.setStackInHand(Hand.MAIN_HAND, stack);
        ActionResult result = stack.useOnBlock(new ItemUsageContext(
                player,
                Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(rail), Direction.UP, rail, false)));
        if (!result.isAccepted()) {
            throw new AssertionError(label + " item placement failed: " + result);
        }
        List<AbstractMinecartEntity> carts = world.getEntitiesByClass(
                AbstractMinecartEntity.class,
                new Box(rail).expand(4.0d),
                Entity::isAlive);
        if (carts.size() != 1) {
            throw new AssertionError(label + " expected exactly one spawned minecart, found=" + carts.size());
        }
        return carts.getFirst();
    }

    private static ArmorStandEntity passenger(ServerWorld world, AbstractMinecartEntity minecart) {
        ArmorStandEntity passenger = new ArmorStandEntity(
                world, minecart.getX(), minecart.getY(), minecart.getZ());
        if (!world.spawnEntity(passenger) || !passenger.startRiding(minecart, true)) {
            throw new AssertionError("armor-stand passenger fixture failed");
        }
        return passenger;
    }

    private static void seed(ServerWorld world, BlockPos backing, double dy) {
        world.setBlockState(backing, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        int writes = SlabAnchorAttachment.writePlacementDyBatch(
                world, Map.of(backing.toImmutable(), Double.doubleToRawLongBits(dy)));
        if (writes != 1) {
            throw new AssertionError("backing fixture refused stored dy=" + dy);
        }
    }

    private static void requireBox(String label, Box expected, Box actual, List<String> failures) {
        if (!near(expected.minX, actual.minX)
                || !near(expected.minY, actual.minY)
                || !near(expected.minZ, actual.minZ)
                || !near(expected.maxX, actual.maxX)
                || !near(expected.maxY, actual.maxY)
                || !near(expected.maxZ, actual.maxZ)) {
            failures.add(label + " physical box expected=" + expected + " actual=" + actual);
        }
    }

    private static boolean near(double first, double second) {
        return Math.abs(first - second) <= EPSILON;
    }

    private record FramePair(
            BlockPos flatBacking,
            BlockPos deepBacking,
            BlockPos flatAttached,
            BlockPos deepAttached,
            ItemFrameEntity flat,
            ItemFrameEntity deep) {
    }
}
