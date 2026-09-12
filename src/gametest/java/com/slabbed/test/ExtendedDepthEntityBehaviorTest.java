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
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.ChestBoatEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.AfterBatch;
import net.minecraft.test.BeforeBatch;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Physical-behavior proof for the attached entity consumers already supported by Slabbed. */
public final class ExtendedDepthEntityBehaviorTest {
    private static final double DEEP_DY = -3.0d;
    private static final double EPSILON = 1.0e-6d;
    private static final String BATCH = "slabbed_extended_depth_entities";
    private static final String BOAT_BATCH = "slabbed_extended_depth_boats";
    private static final String ARMOR_STAND_BATCH = "slabbed_extended_depth_armor_stands";
    private static final String RAIL_BATCH = "slabbed_rail_coordinates";
    private static final ChunkTicketType<Long> ENTITY_TEST_TICKET =
            ChunkTicketType.create("slabbed_gametest_entity_ticking", Long::compareTo);
    private static final AtomicLong ENTITY_TICKET_IDS = new AtomicLong();
    private static boolean frozenBeforeBatch;
    private static boolean frozenBeforeBoatBatch;
    private static boolean frozenBeforeArmorStandBatch;

    @BeforeBatch(batchId = BATCH)
    public static void enableFrozenDyForBatch(ServerWorld world) {
        frozenBeforeBatch = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
    }

    @AfterBatch(batchId = BATCH)
    public static void restoreFrozenDyAfterBatch(ServerWorld world) {
        SlabAnchorAttachment.FROZEN_DY_ENABLED = frozenBeforeBatch;
    }

    @BeforeBatch(batchId = BOAT_BATCH)
    public static void enableFrozenDyForBoatBatch(ServerWorld world) {
        frozenBeforeBoatBatch = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
    }

    @AfterBatch(batchId = BOAT_BATCH)
    public static void restoreFrozenDyAfterBoatBatch(ServerWorld world) {
        SlabAnchorAttachment.FROZEN_DY_ENABLED = frozenBeforeBoatBatch;
    }

    @BeforeBatch(batchId = ARMOR_STAND_BATCH)
    public static void enableFrozenDyForArmorStandBatch(ServerWorld world) {
        frozenBeforeArmorStandBatch = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
    }

    @AfterBatch(batchId = ARMOR_STAND_BATCH)
    public static void restoreFrozenDyAfterArmorStandBatch(ServerWorld world) {
        SlabAnchorAttachment.FROZEN_DY_ENABLED = frozenBeforeArmorStandBatch;
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
        List<EntityTickingTicket> taskForcedChunks = forceEntityTickingChunks(
                world, flatRail, flatRail.east(6), deepRail, deepRail.east(6));
        double[] flatStartX = {Double.NaN};
        double[] deepStartX = {Double.NaN};
        int[] flatStartAge = {-1};
        int[] deepStartAge = {-1};
        boolean[] movementStarted = {false};
        boolean[] movementMeasured = {false};
        long[] movementStartTick = {-1L};
        flat.setVelocity(Vec3d.ZERO);
        deep.setVelocity(Vec3d.ZERO);

        long readinessDeadline = context.getTick() + 40L;
        context.createTimedTaskRunner().createAndAdd(() -> {
            boolean flatShouldTick = world.shouldTickEntity(flat.getBlockPos());
            boolean deepShouldTick = world.shouldTickEntity(deep.getBlockPos());
            if (!flatShouldTick || !deepShouldTick || flat.age <= 0 || deep.age <= 0) {
                context.throwGameTestException("waiting for paired minecart entity ticking");
            }
            movementStarted[0] = true;
            movementStartTick[0] = context.getTick();
            flatStartX[0] = flat.getX();
            deepStartX[0] = deep.getX();
            flatStartAge[0] = flat.age;
            deepStartAge[0] = deep.age;
            flat.setVelocity(0.18d, 0.0d, 0.0d);
            deep.setVelocity(0.18d, 0.0d, 0.0d);
        });

        context.runAtEveryTick(() -> {
            if (!movementStarted[0] || movementMeasured[0]
                    || context.getTick() < movementStartTick[0] + 8L) return;
            movementMeasured[0] = true;
            try {
                    double flatTravel = flat.getX() - flatStartX[0];
                    double deepTravel = deep.getX() - deepStartX[0];
                    if (!flat.isAlive() || !deep.isAlive() || !flat.isOnRail() || !deep.isOnRail()) {
                        failures.add("minecarts must remain alive and on rail after bounded movement"
                                + " flatAge=" + flat.age + " deepAge=" + deep.age
                                + " flatShouldTick=" + world.shouldTickEntity(flat.getBlockPos())
                                + " deepShouldTick=" + world.shouldTickEntity(deep.getBlockPos()));
                    }
                    if (flat.age <= flatStartAge[0] || deep.age <= deepStartAge[0]) {
                        failures.add("minecarts did not tick during the bounded movement window"
                                + " flatStartAge=" + flatStartAge[0] + " flatAge=" + flat.age
                                + " deepStartAge=" + deepStartAge[0] + " deepAge=" + deep.age);
                    }
                    if (flatTravel <= 0.05d || deepTravel <= 0.05d
                            || Math.abs(flatTravel - deepTravel) > 0.15d) {
                        failures.add("movement parity flat=" + flatTravel + " deep=" + deepTravel
                                + " flatAge=" + flat.age + " deepAge=" + deep.age
                                + " flatShouldTick=" + world.shouldTickEntity(flat.getBlockPos())
                                + " deepShouldTick=" + world.shouldTickEntity(deep.getBlockPos()));
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
            } finally {
                releaseEntityTickingChunks(world, taskForcedChunks);
            }
        });
        context.runAtTick(readinessDeadline, () -> {
            if (!movementStarted[0]) {
                String status = entityTickingStatus(world, flat, deep);
                releaseEntityTickingChunks(world, taskForcedChunks);
                context.throwGameTestException("paired minecart entity ticking timed out " + status);
            }
        });
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty", batchId = BOAT_BATCH)
    public void boatsUseVisibleSurfaceAndRemainSupportedAtRenderedDepth(TestContext context) {
        ServerWorld world = context.getWorld();
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        List<String> failures = new ArrayList<>();
        BlockPos origin = context.getAbsolutePos(new BlockPos(4, 16, 4));

        BoatPair regular = placeBoatPair(
                context, world, player, origin, Items.OAK_BOAT, false, "oak boat");
        if (regular == null) return;
        BoatPair chest = placeBoatPair(
                context, world, player, origin.east(8), Items.OAK_CHEST_BOAT, true, "oak chest boat");
        if (chest == null) return;
        List<EntityTickingTicket> taskForcedChunks = forceEntityTickingChunks(
                world, regular.flatSupport(), regular.deepSupport(), chest.flatSupport(), chest.deepSupport());

        inspectSurfaceEntityPair(regular.flatSupport(), regular.deepSupport(),
                regular.flat(), regular.deep(), "oak boat initial", failures);
        inspectSurfaceEntityPair(chest.flatSupport(), chest.deepSupport(),
                chest.flat(), chest.deep(), "oak chest boat initial", failures);
        Box regularFlatInitial = regular.flat().getBoundingBox();
        Box regularDeepInitial = regular.deep().getBoundingBox();
        Box chestFlatInitial = chest.flat().getBoundingBox();
        Box chestDeepInitial = chest.deep().getBoundingBox();
        int regularFlatInitialAge = regular.flat().age;
        int regularDeepInitialAge = regular.deep().age;
        int chestFlatInitialAge = chest.flat().age;
        int chestDeepInitialAge = chest.deep().age;

        runAfterEntityTicking(context, world, taskForcedChunks, "boat", 4, () -> {
            try {
                requireAlive(regular.flat(), "flat oak boat", failures);
                requireAlive(regular.deep(), "deep oak boat", failures);
                requireAlive(chest.flat(), "flat oak chest boat", failures);
                requireAlive(chest.deep(), "deep oak chest boat", failures);
                requireTicked(world, regular.flat(), regularFlatInitialAge, "flat oak boat", failures);
                requireTicked(world, regular.deep(), regularDeepInitialAge, "deep oak boat", failures);
                requireTicked(world, chest.flat(), chestFlatInitialAge, "flat oak chest boat", failures);
                requireTicked(world, chest.deep(), chestDeepInitialAge, "deep oak chest boat", failures);
                inspectSurfaceEntityPair(regular.flatSupport(), regular.deepSupport(),
                        regular.flat(), regular.deep(), "oak boat after ticks", failures);
                inspectSurfaceEntityPair(chest.flatSupport(), chest.deepSupport(),
                        chest.flat(), chest.deep(), "oak chest boat after ticks", failures);
                requireStable("flat oak boat", regularFlatInitial, regular.flat().getBoundingBox(), failures);
                requireStable("deep oak boat", regularDeepInitial, regular.deep().getBoundingBox(), failures);
                requireStable("flat oak chest boat", chestFlatInitial, chest.flat().getBoundingBox(), failures);
                requireStable("deep oak chest boat", chestDeepInitial, chest.deep().getBoundingBox(), failures);
                if (!failures.isEmpty()) {
                    context.throwGameTestException("EXTENDED_DEPTH_BOAT_ENTITY_RED "
                            + String.join("; ", failures));
                }
                context.complete();
            } finally {
                releaseEntityTickingChunks(world, taskForcedChunks);
            }
        }, regular.flat(), regular.deep(), chest.flat(), chest.deep());
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty", batchId = BOAT_BATCH)
    public void boatsPreserveVanillaFluidAndOcclusionRayRules(TestContext context) {
        ServerWorld world = context.getWorld();
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        BlockPos water = context.getAbsolutePos(new BlockPos(4, 16, 4));
        world.setBlockState(water.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(water, Blocks.WATER.getDefaultState(), Block.NOTIFY_ALL);
        aimAt(player, Vec3d.ofCenter(water));
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.OAK_BOAT));
        var fluidResult = player.getStackInHand(Hand.MAIN_HAND).use(world, player, Hand.MAIN_HAND);
        List<BoatEntity> fluidBoats = world.getEntitiesByClass(
                BoatEntity.class, new Box(water).expand(2.0d), Entity::isAlive);
        if (!fluidResult.getResult().isAccepted() || fluidBoats.size() != 1) {
            context.throwGameTestException("vanilla fluid boat placement changed result="
                    + fluidResult.getResult() + " boats=" + fluidBoats.size());
            return;
        }
        fluidBoats.getFirst().discard();

        BlockPos hiddenSupport = water.south(8);
        if (!seedForEntityProof(context, world, hiddenSupport, DEEP_DY, "occluded deep boat")) return;
        BlockPos occluder = hiddenSupport.north().down(2);
        world.setBlockState(occluder, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        Vec3d hiddenTarget = new Vec3d(
                hiddenSupport.getX() + 0.5d,
                hiddenSupport.getY() + 1.0d + DEEP_DY,
                hiddenSupport.getZ() + 0.5d);
        aimAt(player, hiddenTarget);
        Vec3d eye = player.getEyePos();
        BlockHitResult vanillaOccluder = world.raycast(new RaycastContext(
                eye,
                eye.add(player.getRotationVector(player.getPitch(), player.getYaw())
                        .multiply(player.getBlockInteractionRange())),
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.ANY,
                player));
        if (vanillaOccluder.getType() != HitResult.Type.BLOCK
                || !vanillaOccluder.getBlockPos().equals(occluder)) {
            context.throwGameTestException("occlusion control did not establish the nearer vanilla block");
            return;
        }
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.OAK_BOAT));
        var occludedResult = player.getStackInHand(Hand.MAIN_HAND).use(world, player, Hand.MAIN_HAND);
        List<BoatEntity> occludedBoats = world.getEntitiesByClass(
                BoatEntity.class, new Box(hiddenSupport).expand(3.0d, 5.0d, 3.0d), Entity::isAlive);
        if (!occludedResult.getResult().isAccepted() || occludedBoats.size() != 1
                || occludedBoats.getFirst().getPos().squaredDistanceTo(vanillaOccluder.getPos()) > EPSILON) {
            context.throwGameTestException("boat use did not preserve the nearer vanilla hit result="
                    + occludedResult.getResult() + " boats=" + occludedBoats.size());
            return;
        }
        context.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty", batchId = ARMOR_STAND_BATCH)
    public void armorStandsPlaceAndRemainSupportedAtRenderedDepth(TestContext context) {
        ServerWorld world = context.getWorld();
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        List<String> failures = new ArrayList<>();
        BlockPos flatSupport = context.getAbsolutePos(new BlockPos(4, 16, 4));
        BlockPos deepSupport = flatSupport.south(8);
        ArmorStandEntity flat = placeArmorStand(
                context, world, player, flatSupport, 0.0d, "flat armor stand");
        if (flat == null) return;
        ArmorStandEntity deep = placeArmorStand(
                context, world, player, deepSupport, DEEP_DY, "deep armor stand");
        if (deep == null) return;
        BlockPos flatSlabSupport = flatSupport.east(4);
        BlockPos deepSlabSupport = deepSupport.east(4);
        ArmorStandEntity flatSlab = placeArmorStand(
                context, world, player, flatSlabSupport, 0.0d,
                Blocks.STONE_SLAB.getDefaultState(), "flat slab armor stand");
        if (flatSlab == null) return;
        ArmorStandEntity deepSlab = placeArmorStand(
                context, world, player, deepSlabSupport, DEEP_DY,
                Blocks.STONE_SLAB.getDefaultState(), "deep slab armor stand");
        if (deepSlab == null) return;
        List<EntityTickingTicket> taskForcedChunks = forceEntityTickingChunks(
                world, flatSupport, deepSupport, flatSlabSupport, deepSlabSupport);

        inspectSurfaceEntityPair(flatSupport, deepSupport, flat, deep, "armor stand initial", failures);
        inspectSurfaceEntityPair(
                flatSlabSupport, deepSlabSupport, flatSlab, deepSlab, "slab armor stand initial", failures);
        requireSeatedOnVisibleSupport(world, flatSupport, flat, "flat armor stand initial", failures);
        requireSeatedOnVisibleSupport(world, deepSupport, deep, "deep armor stand initial", failures);
        requireSeatedOnVisibleSupport(world, flatSlabSupport, flatSlab, "flat slab armor stand initial", failures);
        requireSeatedOnVisibleSupport(world, deepSlabSupport, deepSlab, "deep slab armor stand initial", failures);
        Box flatInitial = flat.getBoundingBox();
        Box deepInitial = deep.getBoundingBox();
        Box flatSlabInitial = flatSlab.getBoundingBox();
        Box deepSlabInitial = deepSlab.getBoundingBox();
        int flatInitialAge = flat.age;
        int deepInitialAge = deep.age;
        int flatSlabInitialAge = flatSlab.age;
        int deepSlabInitialAge = deepSlab.age;
        world.setBlockState(deepSupport.east(), Blocks.DIRT.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(deepSupport.east(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

        runAfterEntityTicking(context, world, taskForcedChunks, "armor stand", 4, () -> {
            try {
                requireAlive(flat, "flat armor stand", failures);
                requireAlive(deep, "deep armor stand", failures);
                requireAlive(flatSlab, "flat slab armor stand", failures);
                requireAlive(deepSlab, "deep slab armor stand", failures);
                requireTicked(world, flat, flatInitialAge, "flat armor stand", failures);
                requireTicked(world, deep, deepInitialAge, "deep armor stand", failures);
                requireTicked(world, flatSlab, flatSlabInitialAge, "flat slab armor stand", failures);
                requireTicked(world, deepSlab, deepSlabInitialAge, "deep slab armor stand", failures);
                inspectSurfaceEntityPair(flatSupport, deepSupport, flat, deep, "armor stand after ticks", failures);
                inspectSurfaceEntityPair(
                        flatSlabSupport, deepSlabSupport, flatSlab, deepSlab, "slab armor stand after ticks", failures);
                requireSeatedOnVisibleSupport(world, flatSupport, flat, "flat armor stand after ticks", failures);
                requireSeatedOnVisibleSupport(world, deepSupport, deep, "deep armor stand after ticks", failures);
                requireSeatedOnVisibleSupport(world, flatSlabSupport, flatSlab, "flat slab armor stand after ticks", failures);
                requireSeatedOnVisibleSupport(world, deepSlabSupport, deepSlab, "deep slab armor stand after ticks", failures);
                requireStable("flat armor stand", flatInitial, flat.getBoundingBox(), failures);
                requireStable("deep armor stand", deepInitial, deep.getBoundingBox(), failures);
                requireStable("flat slab armor stand", flatSlabInitial, flatSlab.getBoundingBox(), failures);
                requireStable("deep slab armor stand", deepSlabInitial, deepSlab.getBoundingBox(), failures);
                if (!failures.isEmpty()) {
                    context.throwGameTestException("EXTENDED_DEPTH_ARMOR_STAND_ENTITY_RED "
                            + String.join("; ", failures));
                }
                context.complete();
            } finally {
                releaseEntityTickingChunks(world, taskForcedChunks);
            }
        }, flat, deep, flatSlab, deepSlab);
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty", batchId = RAIL_BATCH)
    public void backgroundMinecartConstructionDoesNotWaitForTheServer(TestContext context) throws InterruptedException {
        ServerWorld world = context.getWorld();
        BlockPos rail = context.getAbsolutePos(new BlockPos(4, 16, 4));
        railShape(world, rail, RailShape.EAST_WEST, DEEP_DY);
        var cart = new java.util.concurrent.atomic.AtomicReference<net.minecraft.entity.vehicle.ChestMinecartEntity>();
        var error = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var done = new java.util.concurrent.CountDownLatch(1);
        Thread worker = new Thread(() -> {
            try {
                cart.set(new net.minecraft.entity.vehicle.ChestMinecartEntity(
                        world, rail.getX() + 0.5d, rail.getY() + 0.0625d, rail.getZ() + 0.5d));
            } catch (Throwable failure) {
                error.set(failure);
            } finally {
                done.countDown();
            }
        }, "minecart-construction-test");
        worker.setDaemon(true);
        worker.start();
        // Holding the server thread makes a synchronous world lookup fail deterministically.
        if (!done.await(2, java.util.concurrent.TimeUnit.SECONDS)) {
            context.throwGameTestException("BACKGROUND_MINECART_CONSTRUCTION_RED: constructor waited for the server thread");
        }
        if (error.get() != null || cart.get() == null) {
            context.throwGameTestException("background minecart construction failed: " + error.get());
        }
        double originalY = rail.getY() + 0.0625d;
        if (Math.abs(cart.get().getY() - originalY) > EPSILON) {
            context.throwGameTestException("background constructor must retain its vanilla position until the server tick");
        }
        cart.get().tick();
        NbtCompound saved = new NbtCompound();
        cart.get().writeNbt(saved);
        if (saved.getDouble("slabbed:rail_dy") != DEEP_DY) {
            context.throwGameTestException("first server tick must bind the deferred lowered rail");
        }
        System.out.println("BACKGROUND_MINECART_CONSTRUCTION_GREEN deferred rail binding verified");
        context.complete();
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
        List<EntityTickingTicket> taskForcedChunks = forceEntityTickingChunks(world, deepRail, deepRail.south(4));
        MinecartEntity cart = new MinecartEntity(world, deepRail.getX() + 0.5d,
                deepRail.getY() + DEEP_DY + 0.0625d, deepRail.getZ() + 4.5d);
        cart.setNoGravity(true);
        cart.setVelocity(Vec3d.ZERO);
        double[] offRailStart = {Double.NaN};
        int[] movementStartAge = {-1};
        if (!world.spawnEntity(cart)) {
            releaseEntityTickingChunks(world, taskForcedChunks);
            context.throwGameTestException("off-rail fixture spawn failed");
        }
        int queryChecks = checks;
        boolean[] movementStarted = {false};
        long readinessDeadline = context.getTick() + 40L;
        context.createTimedTaskRunner().createAndAdd(() -> {
            if (!world.shouldTickEntity(cart.getBlockPos()) || cart.age <= 0) {
                context.throwGameTestException("waiting for rail fixture entity ticking");
            }
            movementStarted[0] = true;
            offRailStart[0] = cart.getZ();
            movementStartAge[0] = cart.age;
            cart.setVelocity(0.0d, 0.0d, 0.1d);
            long movementStartTick = context.getTick();
            context.runAtTick(movementStartTick + 2L, () -> {
                boolean shouldTick = world.shouldTickEntity(cart.getBlockPos());
                double travel = cart.getZ() - offRailStart[0];
                if (!shouldTick || cart.age <= movementStartAge[0]
                        || cart.isOnRail() || travel < 0.05d) {
                    releaseEntityTickingChunks(world, taskForcedChunks);
                    context.throwGameTestException("fixture did not reach vanilla off-rail movement"
                            + " startAge=" + movementStartAge[0]
                            + " age=" + cart.age
                            + " deltaZ=" + travel
                            + " isOnRail=" + cart.isOnRail()
                            + " shouldTick=" + shouldTick);
                }
                cart.setVelocity(Vec3d.ZERO);
                cart.setPosition(deepRail.getX() + 0.5d, deepRail.getY() + DEEP_DY + 0.0625d,
                        deepRail.getZ() + 0.5d);
            });
            context.runAtTick(movementStartTick + 5L, () -> {
                try {
                    if (!cart.isAlive() || !cart.isOnRail()
                            || !near(cart.getY(), deepRail.getY() + DEEP_DY + 0.0625d)) {
                        context.throwGameTestException("minecart did not re-enter the physical deep rail");
                    }
                    System.out.println("MINECART_RAIL_COORDINATES pairedSnapChecks=" + queryChecks
                            + " reload=PASS offRailMovement=PASS deepRailReentry=PASS");
                    context.complete();
                } finally {
                    releaseEntityTickingChunks(world, taskForcedChunks);
                }
            });
        });
        context.runAtTick(readinessDeadline, () -> {
            if (!movementStarted[0]) {
                ChunkPos entityChunk = new ChunkPos(cart.getBlockPos());
                boolean forced = world.getForcedChunks().contains(entityChunk.toLong());
                boolean shouldTick = world.shouldTickEntity(cart.getBlockPos());
                releaseEntityTickingChunks(world, taskForcedChunks);
                context.throwGameTestException("rail fixture entity ticking timed out"
                        + " age=" + cart.age
                        + " alive=" + cart.isAlive()
                        + " removed=" + cart.isRemoved()
                        + " chunk=" + entityChunk.x + "," + entityChunk.z
                        + " forced=" + forced
                        + " shouldTick=" + shouldTick);
            }
        });
    }

    private static List<EntityTickingTicket> forceEntityTickingChunks(ServerWorld world, BlockPos... positions) {
        List<EntityTickingTicket> added = new ArrayList<>();
        for (BlockPos position : positions) {
            ChunkPos chunk = new ChunkPos(position);
            if (added.stream().anyMatch(ticket -> ticket.chunk().equals(chunk))) continue;
            long id = ENTITY_TICKET_IDS.incrementAndGet();
            world.getChunkManager().addTicket(ENTITY_TEST_TICKET, chunk, 2, id);
            added.add(new EntityTickingTicket(chunk, id));
        }
        return added;
    }

    private static void releaseEntityTickingChunks(ServerWorld world, List<EntityTickingTicket> chunks) {
        for (EntityTickingTicket ticket : chunks) {
            world.getChunkManager().removeTicket(ENTITY_TEST_TICKET, ticket.chunk(), 2, ticket.id());
        }
    }

    private record EntityTickingTicket(ChunkPos chunk, long id) {
    }

    private static void runAfterEntityTicking(
            TestContext context,
            ServerWorld world,
            List<EntityTickingTicket> taskForcedChunks,
            String label,
            int activeTicks,
            Runnable action,
            Entity... entities) {
        boolean[] started = {false};
        boolean[] ran = {false};
        long[] startTick = {-1L};
        long readinessDeadline = context.getTick() + 40L;
        context.createTimedTaskRunner().createAndAdd(() -> {
            for (Entity entity : entities) {
                if (!world.shouldTickEntity(entity.getBlockPos()) || entity.age <= 0) {
                    context.throwGameTestException("waiting for " + label + " entity ticking");
                }
            }
            started[0] = true;
            startTick[0] = context.getTick();
        });
        context.runAtEveryTick(() -> {
            if (!started[0] || ran[0] || context.getTick() < startTick[0] + activeTicks) return;
            ran[0] = true;
            action.run();
        });
        context.runAtTick(readinessDeadline, () -> {
            if (!started[0]) {
                String status = entityTickingStatus(world, entities);
                releaseEntityTickingChunks(world, taskForcedChunks);
                context.throwGameTestException(label + " entity ticking timed out " + status);
            }
        });
    }

    private static String entityTickingStatus(ServerWorld world, Entity... entities) {
        StringBuilder status = new StringBuilder();
        for (int index = 0; index < entities.length; index++) {
            Entity entity = entities[index];
            ChunkPos chunk = new ChunkPos(entity.getBlockPos());
            if (index > 0) status.append(' ');
            status.append("entity").append(index)
                    .append("Age=").append(entity.age)
                    .append(" alive=").append(entity.isAlive())
                    .append(" removed=").append(entity.isRemoved())
                    .append(" chunk=").append(chunk.x).append(',').append(chunk.z)
                    .append(" forced=").append(world.getForcedChunks().contains(chunk.toLong()))
                    .append(" shouldTick=").append(world.shouldTickEntity(entity.getBlockPos()));
        }
        return status.toString();
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

    private static BoatPair placeBoatPair(
            TestContext context,
            ServerWorld world,
            PlayerEntity player,
            BlockPos flatSupport,
            Item item,
            boolean chest,
            String label) {
        BlockPos deepSupport = flatSupport.south(8);
        BoatEntity flat = placeBoat(context, world, player, flatSupport, 0.0d, item, chest, "flat " + label);
        if (flat == null) return null;
        BoatEntity deep = placeBoat(context, world, player, deepSupport, DEEP_DY, item, chest, "deep " + label);
        if (deep == null) return null;
        return new BoatPair(flatSupport, deepSupport, flat, deep);
    }

    private static BoatEntity placeBoat(
            TestContext context,
            ServerWorld world,
            PlayerEntity player,
            BlockPos support,
            double dy,
            Item item,
            boolean chest,
            String label) {
        if (!seedForEntityProof(context, world, support, dy, label)) return null;
        Vec3d target = new Vec3d(
                support.getX() + 0.5d,
                support.getY() + 1.0d + dy,
                support.getZ() + 0.5d);
        aimAt(player, target);

        ItemStack stack = new ItemStack(item);
        player.setStackInHand(Hand.MAIN_HAND, stack);
        var result = stack.use(world, player, Hand.MAIN_HAND);
        if (!result.getResult().isAccepted()) {
            context.throwGameTestException(label + " actual BoatItem.use failed: " + result.getResult());
            return null;
        }
        List<BoatEntity> boats = world.getEntitiesByClass(
                BoatEntity.class,
                new Box(support).expand(2.0d, 5.0d, 2.0d),
                boat -> boat.isAlive() && (chest == (boat instanceof ChestBoatEntity)));
        if (boats.size() != 1) {
            context.throwGameTestException(label + " expected exactly one placed boat, found=" + boats.size());
            return null;
        }
        return boats.getFirst();
    }

    private static ArmorStandEntity placeArmorStand(
            TestContext context,
            ServerWorld world,
            PlayerEntity player,
            BlockPos support,
            double dy,
            String label) {
        return placeArmorStand(
                context, world, player, support, dy, Blocks.STONE.getDefaultState(), label);
    }

    private static ArmorStandEntity placeArmorStand(
            TestContext context,
            ServerWorld world,
            PlayerEntity player,
            BlockPos support,
            double dy,
            BlockState supportState,
            String label) {
        if (!seedForEntityProof(context, world, support, dy, supportState, label)) return null;
        ItemStack stack = new ItemStack(Items.ARMOR_STAND);
        player.setStackInHand(Hand.MAIN_HAND, stack);
        Vec3d hitPos = new Vec3d(
                support.getX() + 0.5d,
                visibleSupportTop(world, support),
                support.getZ() + 0.5d);
        ActionResult result = stack.useOnBlock(new ItemUsageContext(
                player,
                Hand.MAIN_HAND,
                new BlockHitResult(hitPos, Direction.UP, support, false)));
        if (!result.isAccepted()) {
            context.throwGameTestException(label + " item placement failed: " + result);
            return null;
        }
        List<ArmorStandEntity> stands = world.getEntitiesByClass(
                ArmorStandEntity.class,
                new Box(support).expand(2.0d, 5.0d, 2.0d),
                Entity::isAlive);
        if (stands.size() != 1) {
            context.throwGameTestException(label + " expected exactly one placed stand, found=" + stands.size());
            return null;
        }
        return stands.getFirst();
    }

    private static boolean seedForEntityProof(
            TestContext context, ServerWorld world, BlockPos support, double dy, String label) {
        return seedForEntityProof(
                context, world, support, dy, Blocks.STONE.getDefaultState(), label);
    }

    private static boolean seedForEntityProof(
            TestContext context,
            ServerWorld world,
            BlockPos support,
            double dy,
            BlockState supportState,
            String label) {
        world.setBlockState(support, supportState, Block.NOTIFY_ALL);
        int writes = SlabAnchorAttachment.writePlacementDyBatch(
                world, Map.of(support.toImmutable(), Double.doubleToRawLongBits(dy)));
        if (writes != 1) {
            context.throwGameTestException(label + " support fixture refused stored dy=" + dy);
            return false;
        }
        return true;
    }

    private static void aimAt(PlayerEntity player, Vec3d target) {
        player.setPosition(target.x, target.y + 1.0d, target.z - 3.0d);
        Vec3d delta = target.subtract(player.getEyePos());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        player.setYaw((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        player.setPitch((float) -Math.toDegrees(Math.atan2(delta.y, horizontal)));
    }

    private static void inspectSurfaceEntityPair(
            BlockPos flatSupport,
            BlockPos deepSupport,
            Entity flat,
            Entity deep,
            String label,
            List<String> failures) {
        Box expectedDeep = flat.getBoundingBox().offset(
                deepSupport.getX() - flatSupport.getX(),
                deepSupport.getY() - flatSupport.getY() + DEEP_DY,
                deepSupport.getZ() - flatSupport.getZ());
        Box actualDeep = deep.getBoundingBox();
        if (!near(expectedDeep.minX, actualDeep.minX)
                || !near(expectedDeep.minY, actualDeep.minY)
                || !near(expectedDeep.minZ, actualDeep.minZ)
                || !near(expectedDeep.maxX, actualDeep.maxX)
                || !near(expectedDeep.maxY, actualDeep.maxY)
                || !near(expectedDeep.maxZ, actualDeep.maxZ)) {
            failures.add(label + " physical body did not match the flat baseline shifted by dy=" + DEEP_DY);
        }
        double flatContactPlane = flatSupport.getY() + 1.0d;
        double deepContactPlane = deepSupport.getY() + 1.0d + DEEP_DY;
        double flatContactError = flat.getBoundingBox().minY - flatContactPlane;
        double deepContactError = deep.getBoundingBox().minY - deepContactPlane;
        if (!near(flatContactError, deepContactError)) {
            failures.add(label + " support contact mismatch flatError=" + flatContactError
                    + " deepError=" + deepContactError);
        }
    }

    private static void requireAlive(Entity entity, String label, List<String> failures) {
        if (!entity.isAlive() || entity.isRemoved()) {
            failures.add(label + " did not remain alive through bounded ticks");
        }
    }

    private static void requireSeatedOnVisibleSupport(
            ServerWorld world, BlockPos support, Entity entity, String label, List<String> failures) {
        double contactError = entity.getBoundingBox().minY - visibleSupportTop(world, support);
        if (!near(contactError, 0.0d)) {
            failures.add(label + " support contact error=" + contactError);
        }
    }

    private static double visibleSupportTop(ServerWorld world, BlockPos support) {
        return support.getY() + world.getBlockState(support)
                .getCollisionShape(world, support).getMax(Direction.Axis.Y);
    }

    private static void requireTicked(
            ServerWorld world, Entity entity, int initialAge, String label, List<String> failures) {
        if (entity.age <= initialAge || !world.shouldTickEntity(entity.getBlockPos())) {
            failures.add(label + " was outside authoritative entity ticking"
                    + " initialAge=" + initialAge + " age=" + entity.age
                    + " shouldTick=" + world.shouldTickEntity(entity.getBlockPos()));
        }
    }

    private static void requireStable(String label, Box initial, Box current, List<String> failures) {
        if (Math.abs(initial.minY - current.minY) > 0.1d
                || Math.abs(initial.minX - current.minX) > 0.1d
                || Math.abs(initial.minZ - current.minZ) > 0.1d) {
            failures.add(label + " moved more than 0.1 blocks from its initial supported position");
        }
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

    private record BoatPair(
            BlockPos flatSupport,
            BlockPos deepSupport,
            BoatEntity flat,
            BoatEntity deep) {
    }
}
