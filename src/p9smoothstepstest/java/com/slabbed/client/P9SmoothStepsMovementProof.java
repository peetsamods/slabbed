package com.slabbed.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabPlacementHeightAttachment;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.countered.smoothsteps.IStepTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Exact-version integration proof for Smooth Steps and Slabbed collision ownership. */
public final class P9SmoothStepsMovementProof {
    private static final String PROPERTY = "slabbed.p9.smooth_steps.proof";
    private static final String RED_PROPERTY = "slabbed.p9.smooth_steps.expect_collision_red";
    private static final boolean EXPECT_COLLISION_RED = Boolean.getBoolean(RED_PROPERTY);
    private static final String WORLD_NAME = EXPECT_COLLISION_RED
            ? "p9-smooth-steps-red"
            : "p9-smooth-steps-proof";
    private static final Path PROOF_DIRECTORY = Path.of("proof");
    private static final ResourceLocation TERRAIN_GRASS_SLAB =
            ResourceLocation.fromNamespaceAndPath("terrain_slabs", "grass_slab");
    private static final double EXPECTED_DY = -0.5d;
    private static final double EPSILON = 1.0e-5d;
    private static final int MAX_TICKS = 3_600;

    private static boolean registered;
    private static boolean worldQueued;
    private static boolean fixtureQueued;
    private static boolean teleportQueued;
    private static boolean terminal;
    private static int ticks;
    private static int phaseTicks;
    private static int stableTicks;
    private static int movementTicks;
    private static int settleTicks;
    private static Phase phase = Phase.RAYCAST;
    private static volatile Fixture fixture;
    private static volatile String serverFailure;
    private static Geometry geometry;
    private static boolean raycastGreen;
    private static boolean flatGreen;
    private static boolean stepped;
    private static boolean stepGreen;
    private static boolean trackerNonzero;
    private static boolean cameraSmoothed;
    private static boolean cameraFinite = true;
    private static double flatStartY;
    private static double stepStartY;
    private static double flatMaxTracker;
    private static double maxTracker;
    private static double maxCameraDelta;
    private static double lastCameraDelta = Double.POSITIVE_INFINITY;

    private P9SmoothStepsMovementProof() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        if (!Boolean.getBoolean(PROPERTY)) {
            throw new IllegalStateException("The P9 Smooth Steps proof requires its launch property");
        }
        registered = true;
        NeoForge.EVENT_BUS.addListener(P9SmoothStepsMovementProof::onClientTick);
        NeoForge.EVENT_BUS.addListener(P9SmoothStepsMovementProof::onRenderFrame);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (terminal) {
            return;
        }
        ticks++;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.pauseOnLostFocus = false;
        if (serverFailure != null) {
            fail(minecraft, "server_" + serverFailure);
            return;
        }
        if (ticks > MAX_TICKS) {
            fail(minecraft, "timeout_" + phase.name().toLowerCase(Locale.ROOT));
            return;
        }
        if (minecraft.level == null || minecraft.player == null || !minecraft.hasSingleplayerServer()) {
            maybeOpenWorld(minecraft);
            return;
        }
        if (!fixtureQueued) {
            queueFixture(minecraft);
            return;
        }
        Fixture current = fixture;
        if (current == null || !clientFactsReady(minecraft, current)) {
            return;
        }

        try {
            if (geometry == null) {
                geometry = evaluateGeometry(minecraft, current);
                Slabbed.LOGGER.info(
                        "[P9_SMOOTH_STEPS_GEOMETRY] mode={} versions={} tracker={} placement={} permanent={} visual={} collision={} collisionRed={} modelShift={} reason={}",
                        EXPECT_COLLISION_RED ? "red" : "green",
                        geometry.versions(),
                        geometry.tracker(),
                        geometry.placement(),
                        geometry.permanent(),
                        geometry.visual(),
                        geometry.collision(),
                        geometry.collisionMismatch(),
                        format(geometry.modelShift()),
                        geometry.reason());
                if (!geometry.baseGreen(EXPECT_COLLISION_RED)) {
                    fail(minecraft, geometry.reason());
                    return;
                }
            }
            advance(minecraft, current);
        } catch (RuntimeException exception) {
            Slabbed.LOGGER.error("P9 Smooth Steps proof raised an exception", exception);
            fail(minecraft, "exception_" + exception.getClass().getSimpleName());
        }
    }

    private static void onRenderFrame(RenderFrameEvent.Post event) {
        if (terminal || phase != Phase.SETTLE || !stepped) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        double cameraY = minecraft.gameRenderer.getMainCamera().getPosition().y;
        double eyeY = minecraft.player.getEyeY();
        double delta = Math.abs(cameraY - eyeY);
        if (!Double.isFinite(cameraY) || !Double.isFinite(eyeY) || !Double.isFinite(delta)) {
            cameraFinite = false;
            return;
        }
        lastCameraDelta = delta;
        maxCameraDelta = Math.max(maxCameraDelta, delta);
        if (settleTicks >= 2
                && trackerMagnitude(minecraft) > 1.0e-4d
                && delta > 0.005d
                && delta < 0.75d) {
            cameraSmoothed = true;
        }
    }

    private static void maybeOpenWorld(Minecraft minecraft) {
        if (worldQueued || ticks < 40 || !minecraft.isGameLoadFinished()) {
            return;
        }
        worldQueued = true;
        LevelSettings settings = new LevelSettings(
                "P9 Smooth Steps Proof",
                GameType.SURVIVAL,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
                WorldDataConfiguration.DEFAULT);
        Screen previous = minecraft.screen;
        minecraft.createWorldOpenFlows().createFreshLevel(
                WORLD_NAME,
                settings,
                new WorldOptions(0L, false, false),
                P9SmoothStepsMovementProof::flatDimensions,
                previous);
    }

    private static WorldDimensions flatDimensions(RegistryAccess registryAccess) {
        return registryAccess.registryOrThrow(Registries.WORLD_PRESET)
                .getHolderOrThrow(WorldPresets.FLAT)
                .value()
                .createWorldDimensions();
    }

    private static void queueFixture(Minecraft minecraft) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        fixtureQueued = true;
        server.execute(() -> {
            try {
                ServerLevel world = server.overworld();
                List<ServerPlayer> players = server.getPlayerList().getPlayers();
                if (players.isEmpty()) {
                    throw new IllegalStateException("player_missing");
                }
                ServerPlayer player = players.getFirst();
                Block terrainBlock = BuiltInRegistries.BLOCK.getOptional(TERRAIN_GRASS_SLAB)
                        .orElseThrow(() -> new IllegalStateException("terrain_grass_slab_missing"));
                BlockState terrainSupport = terrainBlock.defaultBlockState();
                if (!(terrainBlock instanceof SlabBlock)
                        || !terrainSupport.hasProperty(SlabBlock.TYPE)) {
                    throw new IllegalStateException("terrain_grass_slab_contract_changed");
                }
                terrainSupport = terrainSupport.setValue(SlabBlock.TYPE, SlabType.BOTTOM);

                BlockPos origin = player.blockPosition().offset(8, 4, 0);
                clear(world, origin);
                BlockPos terrainSupportPos = origin.immutable();
                BlockPos flatSupportPos = origin.offset(5, 0, 0);
                world.setBlock(terrainSupportPos, terrainSupport, Block.UPDATE_ALL);
                world.setBlock(flatSupportPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);

                for (int x = -4; x <= -1; x++) {
                    world.setBlock(origin.offset(x, 0, 0), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                }
                for (int x = -4; x <= 4; x++) {
                    world.setBlock(origin.offset(x, 0, 7), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                }
                world.setBlock(origin.offset(0, 0, 3), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);

                Placement terrain = placeGrass(world, player, terrainSupportPos);
                Placement flat = placeGrass(world, player, flatSupportPos);
                BlockPos updateProbe = terrain.subject().north();
                world.setBlock(updateProbe, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                world.setBlock(updateProbe, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                boolean permanent = world.getBlockState(terrain.subject()).is(Blocks.GRASS_BLOCK)
                        && SlabPlacementHeightAttachment.storedHalfSteps(
                                world.getChunkAt(terrain.subject()), terrain.subject()).orElse(99) == -1;

                Vec3 raycastStart = new Vec3(
                        origin.getX() + 0.5d,
                        origin.getY() + 1.0d,
                        origin.getZ() + 3.5d);
                Vec3 flatStart = new Vec3(
                        origin.getX() - 3.5d,
                        origin.getY() + 1.0d,
                        origin.getZ() + 7.5d);
                Vec3 stepStart = new Vec3(
                        origin.getX() - 0.5d,
                        origin.getY() + 1.0d,
                        origin.getZ() + 0.5d);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setDeltaMovement(Vec3.ZERO);
                player.teleportTo(world, raycastStart.x, raycastStart.y, raycastStart.z, 180.0f, 28.5f);
                fixture = new Fixture(
                        terrain,
                        flat,
                        raycastStart,
                        flatStart,
                        stepStart,
                        permanent,
                        "1.1.0".equals(version("smooth_steps")),
                        "3.1.2".equals(version("terrain_slabs")),
                        "13.0.8".equals(version("architectury")));
            } catch (RuntimeException exception) {
                serverFailure = exception.getClass().getSimpleName() + "_" + safeMessage(exception);
            }
        });
    }

    private static void clear(ServerLevel world, BlockPos origin) {
        for (int dx = -5; dx <= 7; dx++) {
            for (int dz = -2; dz <= 9; dz++) {
                for (int dy = -2; dy <= 4; dy++) {
                    world.setBlock(origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    private static Placement placeGrass(ServerLevel world, ServerPlayer player, BlockPos support) {
        BlockPos subject = support.above();
        player.setPos(support.getX() + 0.5d, support.getY() + 2.0d, support.getZ() + 0.5d);
        ItemStack selected = new ItemStack(Blocks.GRASS_BLOCK, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, selected);
        AtomicInteger placeEvents = new AtomicInteger();
        Consumer<BlockEvent.EntityPlaceEvent> listener = event -> {
            if (event.getLevel() == world && event.getPos().equals(subject)) {
                placeEvents.incrementAndGet();
            }
        };
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, listener);
        InteractionResult interaction;
        try {
            BlockHitResult hit = new BlockHitResult(
                    Vec3.atCenterOf(support).add(0.0d, 0.5d, 0.0d),
                    Direction.UP,
                    support,
                    false);
            interaction = selected.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        int stored = SlabPlacementHeightAttachment.storedHalfSteps(
                        world.getChunkAt(subject), subject)
                .orElse(Integer.MIN_VALUE);
        return new Placement(
                support,
                subject,
                interaction.consumesAction(),
                selected.getCount() == 1,
                placeEvents.get() == 1,
                world.getBlockState(subject).is(Blocks.GRASS_BLOCK),
                stored);
    }

    private static String version(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("missing");
    }

    private static boolean clientFactsReady(Minecraft minecraft, Fixture current) {
        return minecraft.level.getBlockState(current.terrain().subject()).is(Blocks.GRASS_BLOCK)
                && minecraft.level.getBlockState(current.flat().subject()).is(Blocks.GRASS_BLOCK)
                && near(SlabPlacementHeightAttachment.storedOffset(
                        minecraft.level, current.terrain().subject()), EXPECTED_DY)
                && near(SlabPlacementHeightAttachment.storedOffset(
                        minecraft.level, current.flat().subject()), 0.0d);
    }

    private static Geometry evaluateGeometry(Minecraft minecraft, Fixture current) {
        BlockState terrainState = minecraft.level.getBlockState(current.terrain().subject());
        BlockState flatState = minecraft.level.getBlockState(current.flat().subject());
        boolean versions = current.smoothStepsVersion()
                && current.terrainVersion()
                && current.architecturyVersion();
        boolean tracker = minecraft.player instanceof IStepTracker;
        boolean placement = placementGreen(current.terrain())
                && current.terrain().storedHalfSteps() == -1
                && placementGreen(current.flat())
                && current.flat().storedHalfSteps() == 0;
        boolean permanent = current.permanent();
        double dy = ClientDy.dyFor(minecraft.level, current.terrain().subject(), terrainState);
        double outlineMin = terrainState.getShape(
                minecraft.level,
                current.terrain().subject(),
                CollisionContext.empty()).bounds().minY;
        double modelShift = fallbackRenderShift(minecraft, terrainState, current.terrain(), current.flat());
        boolean visual = near(dy, EXPECTED_DY)
                && near(outlineMin, EXPECTED_DY)
                && near(modelShift, EXPECTED_DY);

        double ownerY = current.terrain().subject().getY();
        AABB renderedBody = box(current.terrain().subject(), ownerY - 0.25d);
        AABB phantomBody = box(current.terrain().subject(), ownerY + 0.75d);
        boolean renderedOccupied = !minecraft.level.noCollision(renderedBody);
        boolean phantomEmpty = minecraft.level.noCollision(phantomBody);
        boolean collision = renderedOccupied && phantomEmpty;
        boolean collisionMismatch = !renderedOccupied && !phantomEmpty;
        String reason = geometryFailure(
                versions,
                tracker,
                placement,
                permanent,
                visual,
                collision,
                collisionMismatch);
        return new Geometry(
                versions,
                tracker,
                placement,
                permanent,
                visual,
                collision,
                collisionMismatch,
                modelShift,
                reason);
    }

    private static boolean placementGreen(Placement placement) {
        return placement.accepted()
                && placement.consumedOne()
                && placement.singlePlaceEvent()
                && placement.placedGrass();
    }

    private static void advance(Minecraft minecraft, Fixture current) {
        switch (phase) {
            case RAYCAST -> advanceRaycast(minecraft, current);
            case WAIT_FLAT -> advanceWaitFlat(minecraft, current);
            case FLAT_MOVE -> advanceFlatMove(minecraft, current);
            case WAIT_STEP -> advanceWaitStep(minecraft, current);
            case STEP_MOVE -> advanceStepMove(minecraft, current);
            case SETTLE -> advanceSettle(minecraft, current);
        }
    }

    private static void advanceRaycast(Minecraft minecraft, Fixture current) {
        if (!nearPosition(minecraft.player.position(), current.raycastStart(), 0.30d)) {
            return;
        }
        minecraft.setCameraEntity(minecraft.player);
        minecraft.player.setYRot(180.0f);
        minecraft.player.yRotO = 180.0f;
        minecraft.player.setYHeadRot(180.0f);
        minecraft.player.yHeadRotO = 180.0f;
        minecraft.player.setXRot(28.5f);
        minecraft.player.xRotO = 28.5f;
        if (phaseTicks++ < 20) {
            return;
        }
        minecraft.gameRenderer.pick(1.0f);
        HitResult hit = minecraft.hitResult;
        raycastGreen = hit instanceof BlockHitResult blockHit
                && blockHit.getType() == HitResult.Type.BLOCK
                && blockHit.getBlockPos().equals(current.terrain().subject());
        Slabbed.LOGGER.info(
                "[P9_SMOOTH_STEPS_RAYCAST] result={} target={} actual={}",
                raycastGreen ? "GREEN" : "RED",
                current.terrain().subject(),
                hit instanceof BlockHitResult blockHit ? blockHit.getBlockPos() : "non_block");
        if (!raycastGreen) {
            fail(minecraft, "production_raycast");
            return;
        }
        if (EXPECT_COLLISION_RED) {
            write(
                    "p9-smooth-steps-collision-red.ok",
                    "versions=true tracker=true placement=true permanent=true visual=true collision_red=true raycast=true startup=true\n");
            Slabbed.LOGGER.info(
                    "[P9_SMOOTH_STEPS_PROOF] mode=red result=GREEN reason=collision_follow_disabled_mismatch");
            terminal = true;
            minecraft.stop();
            return;
        }
        beginTeleport(minecraft, current.flatStart(), Phase.WAIT_FLAT);
    }

    private static void advanceWaitFlat(Minecraft minecraft, Fixture current) {
        if (!waitForTeleportAndTracker(minecraft, current.flatStart())) {
            return;
        }
        flatStartY = minecraft.player.getY();
        flatMaxTracker = 0.0d;
        movementTicks = 0;
        phase = Phase.FLAT_MOVE;
    }

    private static void advanceFlatMove(Minecraft minecraft, Fixture current) {
        minecraft.player.move(MoverType.SELF, new Vec3(0.10d, 0.0d, 0.0d));
        movementTicks++;
        flatMaxTracker = Math.max(flatMaxTracker, trackerMagnitude(minecraft));
        if (movementTicks < 10) {
            return;
        }
        flatGreen = near(minecraft.player.getY(), flatStartY) && flatMaxTracker <= 1.0e-4d;
        Slabbed.LOGGER.info(
                "[P9_SMOOTH_STEPS_FLAT] result={} startY={} endY={} maxTracker={}",
                flatGreen ? "GREEN" : "RED",
                format(flatStartY),
                format(minecraft.player.getY()),
                format(flatMaxTracker));
        if (!flatGreen) {
            fail(minecraft, "flat_control");
            return;
        }
        beginTeleport(minecraft, current.stepStart(), Phase.WAIT_STEP);
    }

    private static void advanceWaitStep(Minecraft minecraft, Fixture current) {
        if (!waitForTeleportAndTracker(minecraft, current.stepStart())) {
            return;
        }
        stepStartY = minecraft.player.getY();
        movementTicks = 0;
        maxTracker = 0.0d;
        stepped = false;
        phase = Phase.STEP_MOVE;
    }

    private static void advanceStepMove(Minecraft minecraft, Fixture current) {
        minecraft.player.move(MoverType.SELF, new Vec3(0.10d, 0.0d, 0.0d));
        movementTicks++;
        double tracker = trackerMagnitude(minecraft);
        maxTracker = Math.max(maxTracker, tracker);
        trackerNonzero |= tracker > 1.0e-4d;
        stepped |= minecraft.player.getY() >= stepStartY + 0.25d;
        double stopX = current.terrain().support().getX() + 0.35d;
        if (minecraft.player.getX() < stopX && movementTicks < 30) {
            return;
        }
        stepGreen = stepped
                && near(minecraft.player.getY(), stepStartY + 0.5d)
                && minecraft.level.noCollision(minecraft.player);
        Slabbed.LOGGER.info(
                "[P9_SMOOTH_STEPS_STEP] result={} startY={} endY={} x={} trackerNonzero={} maxTracker={}",
                stepGreen ? "GREEN" : "RED",
                format(stepStartY),
                format(minecraft.player.getY()),
                format(minecraft.player.getX()),
                trackerNonzero,
                format(maxTracker));
        if (!stepGreen) {
            fail(minecraft, "half_step_movement");
            return;
        }
        phase = Phase.SETTLE;
        settleTicks = 0;
        stableTicks = 0;
    }

    private static void advanceSettle(Minecraft minecraft, Fixture current) {
        settleTicks++;
        double tracker = trackerMagnitude(minecraft);
        maxTracker = Math.max(maxTracker, tracker);
        trackerNonzero |= tracker > 1.0e-4d;
        boolean converged = tracker <= 0.005d
                && lastCameraDelta <= 0.01d
                && settleTicks >= 10;
        stableTicks = converged ? stableTicks + 1 : 0;
        if (stableTicks >= 5) {
            finishGreen(minecraft);
            return;
        }
        if (settleTicks > 180) {
            fail(minecraft, "camera_convergence");
        }
    }

    private static boolean waitForTeleportAndTracker(Minecraft minecraft, Vec3 target) {
        if (!nearPosition(minecraft.player.position(), target, 0.20d)) {
            stableTicks = 0;
            return false;
        }
        teleportQueued = false;
        double tracker = trackerMagnitude(minecraft);
        stableTicks = tracker <= 0.005d ? stableTicks + 1 : 0;
        if (stableTicks < 10) {
            return false;
        }
        stableTicks = 0;
        return true;
    }

    private static void beginTeleport(Minecraft minecraft, Vec3 target, Phase nextPhase) {
        if (teleportQueued) {
            return;
        }
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            throw new IllegalStateException("server_missing_for_teleport");
        }
        teleportQueued = true;
        phase = nextPhase;
        phaseTicks = 0;
        stableTicks = 0;
        server.execute(() -> {
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            if (players.isEmpty()) {
                serverFailure = "player_missing_for_teleport";
                return;
            }
            ServerPlayer player = players.getFirst();
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0f;
            player.teleportTo(server.overworld(), target.x, target.y, target.z, -90.0f, 0.0f);
        });
    }

    private static void finishGreen(Minecraft minecraft) {
        boolean cameraGreen = trackerNonzero && cameraSmoothed && cameraFinite;
        boolean green = geometry != null
                && geometry.baseGreen(false)
                && raycastGreen
                && flatGreen
                && stepGreen
                && cameraGreen;
        Slabbed.LOGGER.info(
                "[P9_SMOOTH_STEPS_PROOF] mode=green result={} versions={} tracker={} placement={} permanent={} visual={} collision={} raycast={} step={} camera={} flat={} trackerNonzero={} cameraSmoothed={} cameraFinite={} maxTracker={} maxCameraDelta={} lastCameraDelta={} reason={}",
                green ? "GREEN" : "RED",
                geometry != null && geometry.versions(),
                geometry != null && geometry.tracker(),
                geometry != null && geometry.placement(),
                geometry != null && geometry.permanent(),
                geometry != null && geometry.visual(),
                geometry != null && geometry.collision(),
                raycastGreen,
                stepGreen,
                cameraGreen,
                flatGreen,
                trackerNonzero,
                cameraSmoothed,
                cameraFinite,
                format(maxTracker),
                format(maxCameraDelta),
                format(lastCameraDelta),
                green ? "green" : "smooth_steps_contract");
        if (!green) {
            fail(minecraft, "smooth_steps_contract");
            return;
        }
        write(
                "p9-smooth-steps.ok",
                "versions=true tracker=true placement=true permanent=true visual=true collision=true raycast=true step=true camera=true flat=true startup=true\n");
        terminal = true;
        minecraft.stop();
    }

    private static double trackerMagnitude(Minecraft minecraft) {
        if (!(minecraft.player instanceof IStepTracker tracker)) {
            return Double.POSITIVE_INFINITY;
        }
        double current = tracker.smoothSteps$getOffset();
        double previous = tracker.smoothSteps$getOffsetO();
        if (!Double.isFinite(current) || !Double.isFinite(previous)) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(Math.abs(current), Math.abs(previous));
    }

    private static double fallbackRenderShift(
            Minecraft minecraft,
            BlockState state,
            Placement terrain,
            Placement flat
    ) {
        BakedModel renderedModel = minecraft.getBlockRenderer().getBlockModel(state);
        BakedModel passthrough = new PassthroughModel(renderedModel);
        Bounds flatBounds = renderBounds(minecraft, passthrough, state, flat.subject());
        Bounds terrainBounds = renderBounds(minecraft, passthrough, state, terrain.subject());
        return terrainBounds.minY() - flatBounds.minY();
    }

    private static Bounds renderBounds(
            Minecraft minecraft,
            BakedModel model,
            BlockState state,
            BlockPos pos
    ) {
        CapturingVertexConsumer consumer = new CapturingVertexConsumer();
        minecraft.getBlockRenderer().getModelRenderer().tesselateBlock(
                minecraft.level,
                model,
                state,
                pos,
                new PoseStack(),
                consumer,
                true,
                RandomSource.create(53L),
                state.getSeed(pos),
                0);
        if (consumer.vertices == 0) {
            throw new IllegalStateException("render_emitted_no_vertices");
        }
        return new Bounds(consumer.minY, consumer.maxY, consumer.vertices);
    }

    private static AABB box(BlockPos owner, double centerY) {
        return new AABB(
                owner.getX() + 0.35d,
                centerY - 0.10d,
                owner.getZ() + 0.35d,
                owner.getX() + 0.65d,
                centerY + 0.10d,
                owner.getZ() + 0.65d);
    }

    private static String geometryFailure(
            boolean versions,
            boolean tracker,
            boolean placement,
            boolean permanent,
            boolean visual,
            boolean collision,
            boolean collisionMismatch
    ) {
        if (!versions) return "dependency_identity";
        if (!tracker) return "smooth_steps_tracker_missing";
        if (!placement) return "real_placement_fact";
        if (!permanent) return "placement_permanence";
        if (!visual) return "visual_alignment";
        if (EXPECT_COLLISION_RED && !collisionMismatch) return "collision_red_not_observed";
        if (!EXPECT_COLLISION_RED && !collision) return "collision_alignment";
        return "green";
    }

    private static boolean nearPosition(Vec3 actual, Vec3 expected, double tolerance) {
        return actual.distanceToSqr(expected) <= tolerance * tolerance;
    }

    private static boolean near(double actual, double expected) {
        return Double.isFinite(actual) && Math.abs(actual - expected) <= EPSILON;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.5f", value);
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null ? "no_message" : message.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static void fail(Minecraft minecraft, String reason) {
        if (terminal) {
            return;
        }
        terminal = true;
        write("p9-smooth-steps.failed", reason + "\n");
        minecraft.stop();
    }

    private static void write(String name, String content) {
        try {
            Files.createDirectories(PROOF_DIRECTORY);
            Files.writeString(PROOF_DIRECTORY.resolve(name), content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write P9 Smooth Steps proof receipt", exception);
        }
    }

    private enum Phase {
        RAYCAST,
        WAIT_FLAT,
        FLAT_MOVE,
        WAIT_STEP,
        STEP_MOVE,
        SETTLE
    }

    private record Placement(
            BlockPos support,
            BlockPos subject,
            boolean accepted,
            boolean consumedOne,
            boolean singlePlaceEvent,
            boolean placedGrass,
            int storedHalfSteps
    ) {
    }

    private record Fixture(
            Placement terrain,
            Placement flat,
            Vec3 raycastStart,
            Vec3 flatStart,
            Vec3 stepStart,
            boolean permanent,
            boolean smoothStepsVersion,
            boolean terrainVersion,
            boolean architecturyVersion
    ) {
    }

    private record Geometry(
            boolean versions,
            boolean tracker,
            boolean placement,
            boolean permanent,
            boolean visual,
            boolean collision,
            boolean collisionMismatch,
            double modelShift,
            String reason
    ) {
        boolean baseGreen(boolean expectCollisionRed) {
            return versions
                    && tracker
                    && placement
                    && permanent
                    && visual
                    && (expectCollisionRed ? collisionMismatch : collision);
        }
    }

    private record Bounds(double minY, double maxY, int vertices) {
    }

    private static final class PassthroughModel implements BakedModel {
        private final BakedModel delegate;

        private PassthroughModel(BakedModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction direction, RandomSource random) {
            return delegate.getQuads(state, direction, random);
        }

        @Override public boolean useAmbientOcclusion() { return false; }
        @Override public boolean isGui3d() { return delegate.isGui3d(); }
        @Override public boolean usesBlockLight() { return delegate.usesBlockLight(); }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleIcon() { return delegate.getParticleIcon(); }
        @Override public ItemOverrides getOverrides() { return delegate.getOverrides(); }
    }

    private static final class CapturingVertexConsumer implements VertexConsumer {
        private double minY = Double.POSITIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;
        private int vertices;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            vertices++;
            return this;
        }

        @Override public VertexConsumer setColor(int red, int green, int blue, int alpha) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
