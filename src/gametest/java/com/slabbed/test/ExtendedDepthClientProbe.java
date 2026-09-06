package com.slabbed.test;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.client.model.OffsetBlockStateModel;
import com.slabbed.upgrade.WorldUpgradeDecision;
import com.slabbed.upgrade.WorldUpgradeRuntimePolicy;
import com.slabbed.util.SlabSupport;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.*;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
public final class ExtendedDepthClientProbe implements ClientModInitializer {
    private static final String ENABLE_PROPERTY = "slabbed.depthClientProbe", EVIDENCE_DIR_PROPERTY = "slabbed.depthEvidenceDir";
    private static final int BOOTSTRAP_TIMEOUT = 2400, SUPPORT_SYNC_TIMEOUT = 200, AIM_TIMEOUT = 120, SAMPLE_TIMEOUT = 200;
    private static final double EPS = 1.0e-6d;
    private static final double[] DEPTHS = {-1.0d, -1.5d, -2.0d, -2.5d, -3.0d};
    private static final double[] EXTRA_DEPTHS = {0.0d, -3.0d};
    private static final List<ProbeCase> CASES = List.of(
            new ProbeCase("stone", Items.STONE), new ProbeCase("slab", Items.STONE_SLAB),
            new ProbeCase("anvil", Items.ANVIL), new ProbeCase("trapdoor", Items.OAK_TRAPDOOR),
            new ProbeCase("candle", Items.CANDLE), new ProbeCase("campfire", Items.CAMPFIRE),
            new ProbeCase("torch", Items.TORCH), new ProbeCase("redstone_torch", Items.REDSTONE_TORCH),
            new ProbeCase("carpet", Items.WHITE_CARPET), new ProbeCase("snow", Items.SNOW),
            new ProbeCase("fence", Items.OAK_FENCE), new ProbeCase("wall", Items.COBBLESTONE_WALL),
            new ProbeCase("pane", Items.GLASS_PANE), new ProbeCase("stairs", Items.STONE_STAIRS),
            new ProbeCase("chest", Items.CHEST), new ProbeCase("hopper", Items.HOPPER),
            new ProbeCase("chain", Items.CHAIN), new ProbeCase("lantern", Items.LANTERN),
            new ProbeCase("pointed_dripstone", Items.POINTED_DRIPSTONE));
    private static final List<ProbeCase> EXTRA_CASES = List.of(
            floor("anvil_visual", Items.ANVIL), floor("chest_visual", Items.CHEST),
            floor("candle_emitter", Items.CANDLE), floor("campfire_emitter", Items.CAMPFIRE),
            floor("torch_emitter", Items.TORCH), floor("redstone_torch_emitter", Items.REDSTONE_TORCH),
            floor("soul_campfire", Items.SOUL_CAMPFIRE), floor("soul_torch", Items.SOUL_TORCH),
            floor("rail", Items.RAIL), floor("redstone_wire", Items.REDSTONE, Blocks.REDSTONE_WIRE),
            floor("repeater", Items.REPEATER), floor("comparator", Items.COMPARATOR),
            floor("standing_sign", Items.OAK_SIGN), floor("standing_banner", Items.WHITE_BANNER),
            floor("floor_skull", Items.SKELETON_SKULL), floor("fence_gate", Items.OAK_FENCE_GATE),
            floor("flower_pot", Items.FLOWER_POT), floor("decorated_pot", Items.DECORATED_POT),
            floor("cauldron", Items.CAULDRON), floor("brewing_stand", Items.BREWING_STAND),
            floor("cake", Items.CAKE), floor("lightning_rod", Items.LIGHTNING_ROD),
            floor("end_rod", Items.END_ROD), floor("enchanting_table", Items.ENCHANTING_TABLE),
            floor("piston", Items.PISTON), floor("sticky_piston", Items.STICKY_PISTON),
            floor("observer", Items.OBSERVER), pair("door", Items.OAK_DOOR, PairKind.VERTICAL),
            pair("bed", Items.RED_BED, PairKind.HORIZONTAL),
            wall("ladder", Items.LADDER, Blocks.LADDER), wall("wall_torch", Items.TORCH, Blocks.WALL_TORCH),
            wall("wall_sign", Items.OAK_SIGN, Blocks.OAK_WALL_SIGN),
            wall("wall_banner", Items.WHITE_BANNER, Blocks.WHITE_WALL_BANNER),
            wall("wall_skull", Items.SKELETON_SKULL, Blocks.SKELETON_WALL_SKULL),
            wall("wall_button", Items.ACACIA_BUTTON, Blocks.ACACIA_BUTTON),
            wall("wall_lever", Items.LEVER, Blocks.LEVER), wall("wall_bell", Items.BELL, Blocks.BELL),
            wall("side_trapdoor", Items.OAK_TRAPDOOR, Blocks.OAK_TRAPDOOR),
            ceiling("top_slab", Items.STONE_SLAB), ceiling("ceiling_chain", Items.CHAIN),
            ceiling("hanging_lantern", Items.LANTERN), ceiling("hanging_sign", Items.OAK_HANGING_SIGN),
            ceiling("down_dripstone", Items.POINTED_DRIPSTONE),
            ceiling("ceiling_button", Items.ACACIA_BUTTON), ceiling("ceiling_lever", Items.LEVER),
            ceiling("ceiling_bell", Items.BELL), ceiling("top_trapdoor", Items.OAK_TRAPDOOR));
    private static final List<ProbeRow> ROWS = buildRows();
    private static final int MAX_ROWS = readMaxRows();
    private enum Phase { BOOTSTRAP, PREPARE, SUPPORT_SYNC, AIM, PLACE, OBSERVE, NEXT, ATTACHED, SURFACE_ENTITIES, PISTON_LIVE, FINISH }
    private static int attachedStage;
    private static volatile boolean attachedReady;
    private static final List<AttachedEntityDepthProbe.Capture> attachedFlat = new ArrayList<>();
    private static final List<PistonMovingRenderAudit.Snapshot> movingFlat = new ArrayList<>();
    private static PistonMovingRenderAudit.ModelSnapshot directModelFlat;
    private static boolean breakStarted;
    private static int breakTick;
    private static Phase phase = Phase.BOOTSTRAP;
    private static boolean initialized, worldRequested;
    private static volatile boolean fixtureReady;
    private static int stableMeshSamples;
    private static volatile boolean snapshotPending, serverSyncPending, serverSyncReady;
    private static volatile String activeCaseId = "none";
    private static volatile ServerSnapshot serverSnapshot = ServerSnapshot.missing("none");
    private static boolean predictionFailed, modelMismatch, intermediateModelSeen, backedMeshSeen, evidenceFailed;
    private static boolean particleReset, particlePending;
    private static ParticleSinkAudit.Snapshot eventParticles;
    private static int eventParticleTick;
    private static int particleSeed;
    private static int ticks, phaseTick, caseIndex = Integer.getInteger("slabbed.depthProbe.startRow", 0),
            greenRows, redRows, unknownRows;
    private static BlockPos baseOrigin, supportPos, targetPos;
    private static Path evidenceDir, observations;
    private static String worldName;
    private static String placementResult = "not_started";
    @Override
    public void onInitializeClient() {
        if (initialized) return;
        initialized = true;
        ClientTickEvents.END_CLIENT_TICK.register(ExtendedDepthClientProbe::tick);
        WorldRenderEvents.END.register(context -> {
            SurfaceEntityClientProbe.onWorldRendered(context.camera());
            PistonLiveCycleProbe.onWorldRendered(context.camera());
        });
    }
    private static void tick(MinecraftClient client) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || phase == Phase.FINISH) return;
        ticks++;
        try {
            if (evidenceDir == null && !initializeEvidence()) {
                finish(client, "RED_MISSING_OR_INVALID_EVIDENCE_DIR");
                return;
            }
            if (phase != Phase.BOOTSTRAP && ticks - phaseTick > SAMPLE_TIMEOUT + 80) {
                if (phase == Phase.ATTACHED || phase == Phase.PISTON_LIVE) {
                    redRows++;
                    finish(client, "RED_ATTACHED_ENTITY_TIMEOUT");
                    return;
                }
                completeCase(client, "RED_PHASE_TIMEOUT_" + phase);
                return;
            }
            switch (phase) {
                case BOOTSTRAP -> bootstrap(client);
                case PREPARE -> prepare(client);
                case SUPPORT_SYNC -> awaitSupport(client);
                case AIM -> aim(client);
                case PLACE -> place(client);
                case OBSERVE -> observe(client);
                case NEXT -> next();
                case ATTACHED -> attached(client);
                case SURFACE_ENTITIES -> surfaceEntities(client);
                case PISTON_LIVE -> {
                    var result = PistonLiveCycleProbe.tick(client);
                    if (result.status() != PistonLiveCycleProbe.Status.PENDING) {
                        append("PISTON_LIVE\t" + result.status() + "\t" + result.detail());
                        if (result.status() == PistonLiveCycleProbe.Status.GREEN) greenRows++;
                        else redRows++;
                        finish(client, redRows > 0 ? "RED" : "TECHNICAL_GREEN_SCREENSHOTS_UNREVIEWED");
                    }
                }
                case FINISH -> { }
            }
        } catch (RuntimeException exception) {
            append("ERROR\t" + caseKey() + "\t" + exception.getClass().getSimpleName()
                    + "\t" + exception.getMessage());
            exception.printStackTrace();
            redRows++;
            finish(client, "RED_EXCEPTION");
        }
    }
    private static boolean initializeEvidence() {
        String configured = System.getProperty(EVIDENCE_DIR_PROPERTY, "").trim();
        if (configured.isEmpty()) {
            System.err.println("[DEPTH_CLIENT_PROBE] required property missing: " + EVIDENCE_DIR_PROPERTY);
            return false;
        }
        long stamp = System.currentTimeMillis();
        try {
            Path root = Path.of(configured).toAbsolutePath().normalize();
            evidenceDir = root.resolve("depth-probe-" + Long.toUnsignedString(stamp, 36));
            Files.createDirectories(evidenceDir);
            observations = evidenceDir.resolve("observations.tsv");
            Files.writeString(observations,
                    "kind\tcase\ttick\tclientState\tserverState\teffectiveDy\tbackingDy\tserverDy\tmeshSeen\tmeshDy\tmeshMinBefore\tmeshMinAfter\tnote\n",
                    StandardOpenOption.CREATE_NEW);
            worldName = "slabbed-depth-probe-" + Long.toUnsignedString(stamp, 36)
                    + "-" + Integer.toUnsignedString(configured.hashCode(), 36);
            return true;
        } catch (IOException | RuntimeException exception) {
            System.err.println("[DEPTH_CLIENT_PROBE] evidence init failed: "
                    + exception.getClass().getSimpleName());
            evidenceDir = null;
            return false;
        }
    }
    private static void bootstrap(MinecraftClient client) {
        if (!worldRequested && client != null && (client.world != null || client.player != null)) {
            redRows++;
            finish(client, "RED_EXISTING_WORLD_OPEN_NEW_WORLD_ONLY");
            return;
        }
        if (!worldRequested && client != null && client.isFinishedLoading()) {
            worldRequested = true;
            LevelInfo info = new LevelInfo("Slabbed Extended Depth Probe", GameMode.CREATIVE, false,
                    Difficulty.PEACEFUL, true, new GameRules(), DataConfiguration.SAFE_MODE);
            client.createIntegratedServerLoader().createAndStart(worldName, info,
                    new GeneratorOptions(0L, false, false),
                    ExtendedDepthClientProbe::createSuperflatDimensionOptions, null);
            append("WORLD\t" + worldName + "\t" + ticks + "\trequested");
            return;
        }
        if (ready(client)) {
            client.options.pauseOnLostFocus = false;
            if (client.currentScreen != null) client.setScreen(null);
            if (pistonsOnly()) {
                SlabAnchorAttachment.FROZEN_DY_ENABLED = false;
                WorldUpgradeRuntimePolicy.activate(client.world, WorldUpgradeDecision.Mode.KEEP_EXISTING);
                append("PISTON_MODE\tpolicy=KEEP_EXISTING\tglobalFrozen=false");
            }
            baseOrigin = client.player.getBlockPos().add(10, 20, 10).toImmutable();
            phase = pistonsOnly() ? Phase.ATTACHED
                    : Boolean.getBoolean("slabbed.depthProbe.surfaceEntitiesOnly")
                    ? Phase.SURFACE_ENTITIES : Phase.PREPARE;
            phaseTick = ticks;
        } else if (ticks >= BOOTSTRAP_TIMEOUT) {
            redRows++;
            finish(client, "RED_BOOTSTRAP_TIMEOUT");
        }
    }
    private static void prepare(MinecraftClient client) {
        if (client == null || client.world == null || client.getServer() == null) return;
        ProbeRow row = ROWS.get(caseIndex);
        if (row.continuation()) {
            supportPos = targetPos;
        } else if (row.sequence() >= 0) {
            supportPos = baseOrigin.add((row.sequence() % 5) * 20, 20,
                    (row.sequence() / 5) * 20).toImmutable();
        } else {
            supportPos = baseOrigin.add((caseIndex % 14) * 5, 0, (caseIndex / 14) * 5).toImmutable();
        }
        if (probeCase().id().startsWith("section_boundary_")) {
            int localY = Integer.parseInt(probeCase().id().substring("section_boundary_".length()));
            int targetY = Math.floorDiv(baseOrigin.getY(), 16) * 16 + localY;
            supportPos = new BlockPos(supportPos.getX(), targetY - 1, supportPos.getZ());
        }
        targetPos = supportPos.offset(clickedFace());
        String id = caseKey();
        activeCaseId = id;
        fixtureReady = false;
        stableMeshSamples = 0;
        snapshotPending = false;
        serverSyncPending = false;
        serverSyncReady = false;
        serverSnapshot = ServerSnapshot.missing(id);
        predictionFailed = false;
        modelMismatch = false;
        intermediateModelSeen = false;
        backedMeshSeen = false;
        particleReset = false;
        particlePending = false;
        particleSeed = 0;
        eventParticles = null;
        breakStarted = false;
        placementResult = "not_started";
        double dy = depth();
        BlockState capState = probeCase().capState();
        PairKind pairKind = probeCase().pairKind();
        MinecraftServer server = client.getServer();
        RegistryKey<World> dimension = client.world.getRegistryKey();
        BlockPos support = supportPos;
        server.execute(() -> {
            if (!id.equals(activeCaseId)) return;
            ServerWorld world = server.getWorld(dimension);
            if (world == null) return;
            if (row.continuation()) {
                fixtureReady = true;
                return;
            }
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                for (int y = -4; y <= 4; y++) {
                    BlockPos pos = support.add(x, y, z);
                    SlabAnchorAttachment.removeAnchor(world, pos);
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                }
            }
            world.setBlockState(support, capState, 3);
            if (capState.isOf(Blocks.FARMLAND)) {
                world.setBlockState(support.add(2, 1, 0), Blocks.GLOWSTONE.getDefaultState(), 3);
            }
            Map<BlockPos, Long> facts = new LinkedHashMap<>();
            facts.put(support, Double.doubleToRawLongBits(dy));
            if (capState.isOf(Blocks.SAND)) {
                world.setBlockState(support.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
                facts.put(support.down(), Double.doubleToRawLongBits(dy));
            }
            if (capState.isOf(Blocks.FARMLAND)) {
                world.setBlockState(support, capState.with(Properties.MOISTURE, 7), Block.NOTIFY_ALL);
                world.setBlockState(support.north(), Blocks.WATER.getDefaultState(), Block.NOTIFY_ALL);
            }
            if (pairKind == PairKind.HORIZONTAL) {
                for (Direction direction : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                    BlockPos neighbor = support.offset(direction);
                    world.setBlockState(neighbor, capState, 3);
                    facts.put(neighbor, Double.doubleToRawLongBits(dy));
                }
            }
            SlabAnchorAttachment.writePlacementDyBatch(world, facts);
            world.getChunkManager().markForUpdate(support);
            fixtureReady = true;
        });
        phase = Phase.SUPPORT_SYNC;
        phaseTick = ticks;
    }
    private static void awaitSupport(MinecraftClient client) {
        if (!fixtureReady || client.world == null) return;
        requestServerSnapshot(client);
        ServerSnapshot server = serverSnapshot;
        double expected = depth();
        BlockState clientState = client.world.getBlockState(supportPos);
        var clientFact = SlabAnchorAttachment.rawPlacementDyFact(client.world, supportPos);
        double serverDy = idMatches(server) ? server.supportDy() : Double.NaN;
        if (clientState.isOf(probeCase().capState().getBlock()) && clientFact.present()
                && same(clientFact.valueOrNaN(), expected) && same(serverDy, expected)
                && server.supportState().isOf(probeCase().capState().getBlock())) {
            phase = Phase.AIM;
            phaseTick = ticks;
            return;
        }
        if (ticks - phaseTick >= SUPPORT_SYNC_TIMEOUT) completeCase(client, "RED_SUPPORT_SYNC_TIMEOUT");
    }
    private static void aim(MinecraftClient client) {
        syncPlayerAndItem(client);
        if (ticks - phaseTick < 3 || client.gameRenderer == null) return;
        client.gameRenderer.updateCrosshairTarget(0.0f);
        HitResult hit = client.crosshairTarget;
        if (serverSyncReady && hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                && blockHit.getBlockPos().equals(supportPos) && blockHit.getSide() == clickedFace()
                && blockHit.getPos().squaredDistanceTo(visibleHit()) <= 0.0025d) {
            OffsetBlockStateModel.resetFullMeshBoundsSample(targetPos);
            OffsetBlockStateModel.resetModelDyOwnerSample(targetPos);
            BlockEntityRenderAudit.reset();
            phase = Phase.PLACE;
            phaseTick = ticks;
            return;
        }
        if (ticks - phaseTick >= AIM_TIMEOUT) completeCase(client, "RED_CROSSHAIR_OWNERSHIP_MISMATCH");
    }
    private static void place(MinecraftClient client) {
        syncPlayerAndItem(client);
        client.gameRenderer.updateCrosshairTarget(0.0f);
        HitResult hit = client.crosshairTarget;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK
                || !blockHit.getBlockPos().equals(supportPos) || blockHit.getSide() != clickedFace()) {
            completeCase(client, "RED_CROSSHAIR_CHANGED_BEFORE_INTERACT");
            return;
        }
        ActionResult result = client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, blockHit);
        if (result == ActionResult.PASS && probeCase().item() == Items.POWDER_SNOW_BUCKET) {
            result = client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
        }
        placementResult = String.valueOf(result);
        Block expected = expectedBlock();
        BlockState immediate = client.world.getBlockState(targetPos);
        double immediateDy = ClientDy.dyFor(client.world, targetPos, immediate);
        predictionFailed = !immediate.isOf(expected) || !same(immediateDy, expectedTargetDy());
        appendFrame(client, "IMMEDIATE_PREDICTION");
        phase = Phase.OBSERVE;
        phaseTick = ticks;
    }
    private static void observe(MinecraftClient client) {
        if (client.currentScreen != null) client.setScreen(null);
        requestServerSnapshot(client);
        if (breakStarted) {
            observeBrokenPane(client);
            return;
        }
        appendFrame(client, "BACKING_WAIT");
        if (client.world == null) return;
        ServerSnapshot server = serverSnapshot;
        Block expected = expectedBlock();
        BlockState clientState = client.world.getBlockState(targetPos);
        BlockState serverState = idMatches(server) ? server.targetState() : Blocks.AIR.getDefaultState();
        double effective = ClientDy.dyFor(client.world, targetPos, clientState);
        var backing = SlabAnchorAttachment.rawPlacementDyFact(client.world, targetPos);
        double serverDy = idMatches(server) ? server.targetDy() : Double.NaN;
        OffsetBlockStateModel.FullMeshBoundsSample mesh = OffsetBlockStateModel.snapshotFullMeshBoundsSample();
        OffsetBlockStateModel.ModelDyOwnerSample model = OffsetBlockStateModel.snapshotModelDyOwnerSample();
        BlockEntityRenderAudit.Snapshot blockEntity = BlockEntityRenderAudit.snapshot(targetPos);
        boolean statesReady = clientState.isOf(expected) && serverState.isOf(expected);
        boolean valuesReady = same(effective, expectedTargetDy()) && same(serverDy, expectedTargetDy())
                && backing.present() && same(backing.valueOrNaN(), expectedTargetDy())
                && pairReady(client, server);
        boolean meshReady = validMesh(mesh, expectedTargetDy());
        if (mesh.seen() && (!same(mesh.dy(), expectedTargetDy()) || finiteTranslationMismatch(mesh, expectedTargetDy()))) {
            modelMismatch = true;
        }
        if (model.seen()) {
            if (!same(model.minDy(), expectedTargetDy()) || !same(model.maxDy(), expectedTargetDy())) modelMismatch = true;
            else if (!backing.present()) intermediateModelSeen = true;
        }
        boolean blockEntityReady = blockEntity.exactMatrixGreen()
                && same(blockEntity.minExpectedDy(), expectedTargetDy())
                && same(blockEntity.maxExpectedDy(), expectedTargetDy());
        if (blockEntity.mismatchCount() > 0) modelMismatch = true;
        boolean renderReady = (meshReady && model.seen()) || blockEntityReady;
        if (backing.present() && renderReady) backedMeshSeen = true;
        stableMeshSamples = statesReady && valuesReady && renderReady
                ? stableMeshSamples + 1 : 0;
        if (statesReady && valuesReady && renderReady && backedMeshSeen && stableMeshSamples >= 3
                && ticks - phaseTick >= 4) {
            if (probeCase().id().startsWith("break_pane_")) {
                breakPane(client);
                return;
            }
            String particle = advanceParticle(client, clientState);
            if (particle.startsWith("WAIT")) return;
            completeCase(client, predictionFailed || modelMismatch ? "RED_SNAP_OR_MODEL_DY"
                    : particle.startsWith("RED") ? particle : "GREEN_TECHNICAL");
        } else if (ticks - phaseTick >= SAMPLE_TIMEOUT) {
            boolean modelMayBeAbsent = expected instanceof BlockEntityProvider;
            String verdict = !statesReady || !valuesReady ? "RED_BACKING_OR_STATE_TIMEOUT"
                    : predictionFailed || modelMismatch ? "RED_SNAP_OR_MODEL_DY"
                    : modelMayBeAbsent && !renderReady ? "MODEL_UNKNOWN_NO_RENDER_SAMPLE"
                    : "RED_REQUIRED_MODEL_SAMPLE_MISSING";
            completeCase(client, verdict);
        }
    }
    private static void breakPane(MinecraftClient client) {
        Box bounds = client.world.getBlockState(targetPos).getOutlineShape(client.world, targetPos)
                .getBoundingBox().offset(targetPos);
        Vec3d center = bounds.getCenter(), eye = center.add(0.0d, 0.2d, 2.6d);
        Vec3d delta = center.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
        client.player.refreshPositionAndAngles(eye.x, eye.y - client.player.getStandingEyeHeight(), eye.z, yaw, pitch);
        client.gameRenderer.updateCrosshairTarget(1.0f);
        if (!(client.crosshairTarget instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(targetPos)) {
            completeCase(client, "RED_PANE_BREAK_VISIBLE_TARGET");
            return;
        }
        breakStarted = client.interactionManager.attackBlock(targetPos, hit.getSide());
        breakTick = ticks;
        if (!breakStarted) completeCase(client, "RED_PANE_BREAK_REJECTED");
    }
    private static void observeBrokenPane(MinecraftClient client) {
        ServerSnapshot server = serverSnapshot;
        boolean removed = client.world.getBlockState(targetPos).isAir() && idMatches(server)
                && server.targetState().isAir()
                && !SlabAnchorAttachment.rawPlacementDyFact(client.world, targetPos).present();
        boolean supportKept = client.world.getBlockState(supportPos).isOf(probeCase().capState().getBlock())
                && idMatches(server) && server.supportState().isOf(probeCase().capState().getBlock())
                && same(server.supportDy(), depth());
        if (removed && supportKept && ticks - breakTick >= 12) {
            completeCase(client, predictionFailed || modelMismatch ? "RED_SNAP_OR_MODEL_DY" : "GREEN_PANE_BREAK_AND_SUBSEQUENT_FRAMES");
        } else if (ticks - breakTick >= SAMPLE_TIMEOUT) completeCase(client, "RED_PANE_BREAK_CLEANUP_TIMEOUT");
    }
    private static String advanceParticle(MinecraftClient client, BlockState clientState) {
        if (probeCase().id().startsWith("event_")) return advanceEventParticle(client, clientState);
        if (!particleRequired()) return "GREEN_NOT_APPLICABLE";
        if (probeCase().item() == Items.CANDLE
                && (!clientState.contains(Properties.LIT) || !clientState.get(Properties.LIT))) {
            lightCandle(client);
            return ticks - phaseTick >= SAMPLE_TIMEOUT ? "RED_CANDLE_LIT_SYNC_TIMEOUT" : "WAIT_CANDLE_LIT_SYNC";
        }
        if (depth() == 0.0d) {
            ParticleSinkAudit.forceTickAt(client.world, targetPos, 0L);
            return "UNSHIFTED_PARTICLE_CONTROL";
        }
        if (!particleReset) {
            ParticleSinkAudit.reset();
            particleReset = true;
        }
        ParticleSinkAudit.Snapshot sample = ParticleSinkAudit.forceTickAt(client.world, targetPos, particleSeed++);
        append("PARTICLE\t" + caseKey() + "\t" + ticks + "\tseed=" + (particleSeed - 1)
                + "\tcount=" + sample.count() + "\tvalidated=" + sample.validatedCount()
                + "\tmismatches=" + sample.mismatchCount() + "\tdy=" + number(sample.dy())
                + "\tminY=" + number(sample.minY()) + "\tmaxY=" + number(sample.maxY()));
        if (sample.mismatchCount() > 0 || (sample.count() > 0 && !sample.exactOnceGreen())) {
            return "RED_PARTICLE_TRANSLATION";
        }
        if (sample.exactOnceGreen() && !same(sample.dy(), expectedTargetDy())) return "RED_PARTICLE_OWNER_DY";
        if (sample.exactOnceGreen()) return "GREEN_PARTICLE_SINK";
        return particleSeed >= 64 ? "RED_PARTICLE_ZERO_EMISSIONS" : "WAIT_PARTICLE_EMISSION";
    }
    private static void lightCandle(MinecraftClient client) {
        setLitState(client, true);
    }
    private static void setLitState(MinecraftClient client, boolean lit) {
        if (particlePending || client == null || client.world == null || client.getServer() == null) return;
        particlePending = true;
        MinecraftServer server = client.getServer();
        RegistryKey<World> dimension = client.world.getRegistryKey();
        String id = activeCaseId;
        BlockPos pos = targetPos;
        server.execute(() -> {
            if (!id.equals(activeCaseId)) return;
            ServerWorld world = server.getWorld(dimension);
            if (world != null) {
                BlockState state = world.getBlockState(pos);
                if (state.contains(Properties.LIT)) world.setBlockState(pos, state.with(Properties.LIT, lit), Block.NOTIFY_ALL);
            }
            particlePending = false;
        });
    }
    private static String advanceEventParticle(MinecraftClient client, BlockState state) {
        if (state.contains(Properties.LIT) && state.get(Properties.LIT)) {
            setLitState(client, false);
            return "WAIT_EVENT_STATE_SYNC";
        }
        boolean campfire = probeCase().item() == Items.CAMPFIRE;
        boolean candle = probeCase().item() == Items.CANDLE;
        boolean lever = probeCase().item() == Items.LEVER;
        boolean egg = probeCase().item() == Items.TURTLE_EGG;
        if (eventParticles == null) {
            ParticleSinkAudit.reset();
            eventParticles = campfire
                    ? ParticleSinkAudit.forceCampfireExtinguish(client.world, targetPos)
                    : candle ? ParticleSinkAudit.forceCandleExtinguish(client.world, targetPos, client.player)
                    : lever ? ParticleSinkAudit.forceLeverUse(client.world, targetPos, client.player,
                            new BlockHitResult(Vec3d.ofCenter(targetPos).add(0.0d, depth(), 0.0d),
                                    Direction.UP, targetPos, false))
                    : egg ? ParticleSinkAudit.forceWorldEvent(client.worldRenderer, client.world, 2012, targetPos, 15)
                    : ParticleSinkAudit.forceWorldEvent(client.worldRenderer, client.world, 1502, targetPos, 0);
            eventParticleTick = ticks;
            append("EVENT_PARTICLE\t" + caseKey() + "\t" + ticks + "\tcount=" + eventParticles.count()
                    + "\tminY=" + eventParticles.minY() + "\tmaxY=" + eventParticles.maxY()
                    + "\townerY=" + targetPos.getY() + "\texpectedDy=" + expectedTargetDy());
        }
        if (ticks - eventParticleTick < 4) return "WAIT_EVENT_RENDER";
        double lower = targetPos.getY() + expectedTargetDy() + (campfire || egg ? 0.0d : lever ? 0.3d : candle ? 0.5d : 0.2d);
        double upper = targetPos.getY() + expectedTargetDy() + (campfire ? 2.0d : egg ? 0.4375d : lever ? 0.3d : candle ? 0.5d : 0.8d);
        int count = campfire ? 40 : egg ? 15 : candle || lever ? 1 : 5;
        if (egg && !ParticleSinkAudit.containsOnlyType(targetPos, net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER))
            return "RED_EVENT_PARTICLE_TYPE";
        return eventParticles.count() == count && eventParticles.minY() >= lower - EPS
                && eventParticles.maxY() <= upper + EPS
                ? "GREEN_EVENT_PARTICLE_BAND" : "RED_EVENT_PARTICLE_ORIGIN";
    }
    private static boolean particleRequired() {
        if (depth() != 0.0d && depth() != -3.0d) return false;
        Item item = probeCase().item();
        return item == Items.CANDLE || item == Items.CAMPFIRE || item == Items.TORCH
                || item == Items.REDSTONE_TORCH || item == Items.END_ROD;
    }
    private static void completeCase(MinecraftClient client, String verdict) {
        if (expectedTargetDy() == -3.0d || depth() == 0.0d || ROWS.get(caseIndex).sequence() >= 0) {
            String screenshot = screenshot(client, String.format(Locale.ROOT, "%02d-%s-%s-final",
                    categoryIndex(), probeCase().id(), depth() == 0.0d ? "flat" : "m3"));
            if (screenshot.startsWith("ERROR")) verdict = "RED_SCREENSHOT_FAILED";
            append("SCREENSHOT\t" + caseKey() + "\t" + ticks + "\t" + screenshot);
        }
        if (verdict.startsWith("GREEN")) greenRows++;
        else if (verdict.startsWith("MODEL_UNKNOWN") || verdict.startsWith("PARTIAL")) unknownRows++;
        else redRows++;
        append("ROW\t" + caseKey() + "\t" + ticks + "\t" + verdict
                + "\tplacement=" + placementResult + "\tpredictionFailed=" + predictionFailed
                + "\tmodelMismatch=" + modelMismatch + "\tintermediateRender="
                + (intermediateModelSeen ? "OBSERVED" : "NOT_OBSERVED")
                + "\tbackedMeshSeen=" + backedMeshSeen + "\texpectedTargetDy=" + number(expectedTargetDy()));
        phase = Phase.NEXT;
        phaseTick = ticks;
    }
    private static void next() {
        if (ticks - phaseTick < 2) return;
        caseIndex++;
        if (caseIndex >= MAX_ROWS) {
            if (Boolean.getBoolean("slabbed.depthProbe.attachedEntities")) {
                phase = Phase.ATTACHED;
                phaseTick = ticks;
            } else {
                finish(MinecraftClient.getInstance(), redRows > 0 ? "RED" : unknownRows > 0 ? "PARTIAL" : "TECHNICAL_GREEN_SCREENSHOTS_UNREVIEWED");
            }
        } else {
            phase = Phase.PREPARE;
            phaseTick = ticks;
        }
    }
    private static void attached(MinecraftClient client) {
        BlockPos frame = baseOrigin.add(100, 6, 0), rail = frame.east(5), slope = frame.east(10);
        int movingCases = pistonsOnly() ? 5 : 4;
        if (attachedStage == 0 || attachedStage == 2) {
            double dy = attachedStage == 0 ? 0.0d : -3.0d;
            attachedReady = false;
            RegistryKey<World> dimension = client.world.getRegistryKey();
            client.getServer().execute(() -> {
                ServerWorld world = client.getServer().getWorld(dimension);
                if (pistonsOnly()) {
                    WorldUpgradeRuntimePolicy.activate(world, WorldUpgradeDecision.Mode.KEEP_EXISTING);
                }
                if (!pistonsOnly()) {
                    world.setBlockState(frame, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
                    world.setBlockState(rail.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
                    world.setBlockState(slope.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
                    world.setBlockState(slope.east(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
                    world.setBlockState(rail, Blocks.RAIL.getDefaultState(), Block.NOTIFY_ALL);
                    world.setBlockState(slope, Blocks.POWERED_RAIL.getDefaultState().with(PoweredRailBlock.SHAPE,
                            net.minecraft.block.enums.RailShape.ASCENDING_EAST), Block.NOTIFY_ALL);
                    SlabAnchorAttachment.writePlacementDyBatch(world, Map.of(frame, Double.doubleToRawLongBits(dy),
                            rail, Double.doubleToRawLongBits(dy), slope, Double.doubleToRawLongBits(dy)));
                    AttachedEntityDepthProbe.prepareFrames(world, frame, dy);
                    AttachedEntityDepthProbe.prepareCarts(world, rail, slope);
                }
                for (int index = 0; index < movingCases; index++) {
                    Direction direction = index < 2 ? Direction.EAST : index < 4 ? Direction.UP : Direction.NORTH;
                    BlockPos source = frame.south(6 + index * 6), destination = source.offset(direction);
                    world.setBlockState(source, Blocks.STICKY_PISTON.getDefaultState()
                            .with(PistonBlock.FACING, direction), Block.NOTIFY_ALL);
                    world.setBlockState(destination, Blocks.MOVING_PISTON.getDefaultState()
                            .with(PistonExtensionBlock.FACING, direction), Block.NOTIFY_ALL);
                    SlabAnchorAttachment.writePlacementDyBatch(world, Map.of(source, Double.doubleToRawLongBits(dy),
                            destination, Double.doubleToRawLongBits(dy)));
                    if (pistonsOnly() && index < 4) {
                        SlabAnchorAttachment.markPostPolicyPlacements(world, List.of(source, destination));
                    }
                }
                attachedReady = true;
            });
            attachedStage++;
            return;
        }
        double expected = attachedStage == 1 ? 0.0d : -3.0d;
        if (!attachedReady) return;
        if (!pistonsOnly()) {
            if (!AttachedEntityDepthProbe.framesReady(client, expected)) return;
            if (!AttachedEntityDepthProbe.cartsReady(client, rail, slope, expected)) return;
            if (!client.world.getBlockState(frame).isOf(Blocks.STONE)
                    || !client.world.getBlockState(rail).isOf(Blocks.RAIL)
                    || !client.world.getBlockState(slope).isOf(Blocks.POWERED_RAIL)) return;
            for (BlockPos pos : List.of(frame, rail, slope)) {
                var fact = SlabAnchorAttachment.rawPlacementDyFact(client.world, pos);
                if (!fact.present() || !same(fact.valueOrNaN(), expected)) return;
            }
        }
        for (int index = 0; index < movingCases; index++) {
            Direction direction = index < 2 ? Direction.EAST : index < 4 ? Direction.UP : Direction.NORTH;
            BlockPos source = frame.south(6 + index * 6), destination = source.offset(direction);
            if (!client.world.getBlockState(destination).isOf(Blocks.MOVING_PISTON)) {
                if (ticks % 20 == 0) append("MOVING_FIXTURE_WAIT\t" + destination
                        + "\t" + client.world.getBlockState(destination));
                return;
            }
            for (BlockPos pos : List.of(source, destination)) {
                var fact = SlabAnchorAttachment.rawPlacementDyFact(client.world, pos);
                if (!fact.present() || !same(fact.valueOrNaN(), expected)) return;
                if (pistonsOnly()
                        && SlabAnchorAttachment.isModernPlacement(client.world, pos) != (index < 4)) return;
            }
        }
        BlockPos directPos = frame.south(6);
        BlockState directState = client.world.getBlockState(directPos);
        var directModel = PistonMovingRenderAudit.renderModelAndSnapshot(client, directPos, directState);
        append("DIRECT_BLOCK_MODEL\tdy=" + expected
                + "\tselectedDyBefore=" + directModel.selectedDyBefore()
                + "\tselectedDyAfter=" + directModel.selectedDyAfter()
                + "\tsuppressedBefore=" + directModel.suppressedBefore()
                + "\tsuppressedAfter=" + directModel.suppressedAfter()
                + "\tfabricModel=" + directModel.fabricModel()
                + "\tvanillaAdapter=" + directModel.vanillaAdapter()
                + "\trenderBalanced=" + directModel.renderBalanced()
                + "\tsmoothBalanced=" + directModel.smoothBalanced()
                + "\tflatBalanced=" + directModel.flatBalanced()
                + "\tearlyHookBalanced=" + directModel.earlyHookBalanced()
                + "\tearlyRenderBalanced=" + directModel.earlyRenderBalanced()
                + "\trenderVertices=" + directModel.vertexCount()
                + "\trenderY=" + directModel.minY() + ".." + directModel.maxY()
                + "\tsmoothVertices=" + directModel.smoothVertexCount()
                + "\tsmoothY=" + directModel.smoothMinY() + ".." + directModel.smoothMaxY()
                + "\tflatVertices=" + directModel.flatVertexCount()
                + "\tflatY=" + directModel.flatMinY() + ".." + directModel.flatMaxY()
                + "\tearlyVertices=" + directModel.earlyVertexCount()
                + "\tearlyY=" + directModel.earlyMinY() + ".." + directModel.earlyMaxY());
        if (attachedStage == 1) {
            directModelFlat = directModel;
        } else {
            try {
                PistonMovingRenderAudit.assertDirectModelDepthDelta(directModelFlat, directModel, -3.0d);
                greenRows++;
            } catch (AssertionError failure) {
                redRows++;
                append("DIRECT_BLOCK_MODEL_RED\t" + failure.getMessage());
            }
        }
        if (!pistonsOnly()) {
            for (Direction facing : Direction.values()) {
                var capture = AttachedEntityDepthProbe.capture(client, frame, facing, rail, slope);
                append("ATTACHED_ENTITY_MATRIX\t" + facing + "\tdy=" + expected + "\t" + capture);
                if (attachedStage == 1) attachedFlat.add(capture);
                else {
                    try {
                        AttachedEntityDepthProbe.assertStoredDepthDelta(attachedFlat.get(facing.ordinal()), capture, -3.0d);
                        greenRows++;
                    } catch (AssertionError failure) {
                        redRows++;
                        append("ATTACHED_ENTITY_RED\t" + facing + "\t" + failure.getMessage());
                    }
                }
            }
        }
        for (int index = 0; index < movingCases; index++) {
            Direction direction = index < 2 ? Direction.EAST : index < 4 ? Direction.UP : Direction.NORTH;
            BlockPos destination = frame.south(6 + index * 6).offset(direction);
            boolean head = index % 2 == 0;
            BlockState pushed = head ? Blocks.PISTON_HEAD.getDefaultState()
                    .with(PistonHeadBlock.FACING, direction) : Blocks.STONE.getDefaultState();
            var moving = new net.minecraft.block.entity.PistonBlockEntity(destination,
                    client.world.getBlockState(destination), pushed, direction, true, head);
            moving.setWorld(client.world);
            var sample = sampleMovingPiston(client, moving);
            append("MOVING_PISTON_MATRIX\t" + index + "\tmodern=" + (index < 4)
                    + "\tdy=" + expected + "\tvertices=" + sample.vertexCount()
                    + "\tyBounds=" + sample.minY() + ".." + sample.maxY());
            if (attachedStage == 1) movingFlat.add(sample);
            else {
                try {
                    PistonMovingRenderAudit.assertStoredDepthDelta(
                            movingFlat.get(index), sample, index < 4 ? -3.0d : 0.0d);
                    greenRows++;
                } catch (AssertionError failure) {
                    redRows++;
                    append("MOVING_PISTON_RED\t" + index + "\t" + failure.getMessage());
                }
            }
        }
        if (attachedStage == 1) { attachedStage = 2; return; }
        if (Boolean.getBoolean("slabbed.depthProbe.pistonLive")) {
            PistonLiveCycleProbe.start(client, baseOrigin.add(20, 15, 100));
            phase = Phase.PISTON_LIVE;
            phaseTick = ticks;
        } else finish(client, redRows > 0 ? "RED" : pistonsOnly()
                ? "TECHNICAL_GREEN_VISUAL_NOT_RUN" : "TECHNICAL_GREEN_SCREENSHOTS_UNREVIEWED");
    }
    private static void surfaceEntities(MinecraftClient client) {
        var result = SurfaceEntityClientProbe.tick(client, baseOrigin.add(40, 8, 40), evidenceDir);
        if (result.status() == SurfaceEntityClientProbe.Status.PENDING) return;
        append("SURFACE_ENTITY_SUMMARY\t" + result.status() + "\t" + result.detail());
        if (result.status() == SurfaceEntityClientProbe.Status.GREEN) greenRows += result.cases();
        else redRows++;
        finish(client, result.status() == SurfaceEntityClientProbe.Status.GREEN
                ? "TECHNICAL_GREEN_SCREENSHOTS_UNREVIEWED" : "RED_SURFACE_ENTITY_CLIENT_PROOF");
    }
    private static PistonMovingRenderAudit.Snapshot sampleMovingPiston(MinecraftClient client,
            net.minecraft.block.entity.PistonBlockEntity moving) {
        Vec3d old = client.player.getPos();
        float yaw = client.player.getYaw(), pitch = client.player.getPitch();
        Vec3d eye = Vec3d.ofCenter(moving.getPos()).add(0.0d, 1.5d, 4.0d);
        var camera = client.gameRenderer.getCamera();
        try {
            client.player.refreshPositionAndAngles(eye.x, eye.y - client.player.getStandingEyeHeight(),
                    eye.z, 180.0f, 0.0f);
            camera.update(client.world, client.player, false, false, 1.0f);
            client.getBlockEntityRenderDispatcher().configure(client.world, camera, client.crosshairTarget);
            return PistonMovingRenderAudit.renderAndSnapshot(client.getBlockEntityRenderDispatcher(),
                    moving, 0.0f, new net.minecraft.client.util.math.MatrixStack(),
                    client.getBufferBuilders().getEntityVertexConsumers());
        } finally {
            client.player.refreshPositionAndAngles(old.x, old.y, old.z, yaw, pitch);
            camera.update(client.world, client.player, false, false, 1.0f);
            client.getBlockEntityRenderDispatcher().configure(client.world, camera, client.crosshairTarget);
        }
    }
    private static void appendFrame(MinecraftClient client, String note) {
        BlockState clientState = client.world == null ? Blocks.AIR.getDefaultState() : client.world.getBlockState(targetPos);
        ServerSnapshot server = serverSnapshot;
        BlockState serverState = idMatches(server) ? server.targetState() : Blocks.AIR.getDefaultState();
        double effective = client.world == null ? Double.NaN : ClientDy.dyFor(client.world, targetPos, clientState);
        var backing = client.world == null ? null : SlabAnchorAttachment.rawPlacementDyFact(client.world, targetPos);
        double serverDy = idMatches(server) ? server.targetDy() : Double.NaN;
        var mesh = OffsetBlockStateModel.snapshotFullMeshBoundsSample();
        var model = OffsetBlockStateModel.snapshotModelDyOwnerSample();
        var blockEntity = BlockEntityRenderAudit.snapshot(targetPos);
        append("FRAME\t" + caseKey() + "\t" + ticks + "\t" + blockId(clientState) + "\t"
                + blockId(serverState) + "\t" + number(effective) + "\t"
                + number(backing != null && backing.present() ? backing.valueOrNaN() : Double.NaN) + "\t"
                + number(serverDy) + "\t" + mesh.seen() + "\t" + number(mesh.dy()) + "\t"
                + number(mesh.minBeforeY()) + "\t" + number(mesh.minAfterY()) + "\t" + note
                + "/modelSeen=" + model.seen() + "/modelMinDy=" + number(model.minDy())
                + "/modelMaxDy=" + number(model.maxDy()) + "/beFrames=" + blockEntity.seenFrames()
                + "/beMismatch=" + blockEntity.mismatchCount() + "/beGreen=" + blockEntity.exactMatrixGreen());
        if (probeCase().id().equals("crop") && idMatches(server)) {
            append("SUPPORT_DIAGNOSTIC\t" + caseKey() + "\t" + ticks
                    + "\tserverSupport=" + server.supportState() + "\tserverSupportDy=" + server.supportDy());
        }
    }
    private static void syncPlayerAndItem(MinecraftClient client) {
        Vec3d hit = visibleHit();
        Direction face = clickedFace();
        Vec3d eye = face.getAxis().isVertical()
                ? hit.add(0.0d, face.getOffsetY() * 1.1d, 2.6d)
                : hit.add(face.getOffsetX() * 2.6d, 0.3d, face.getOffsetZ() * 2.6d);
        Vec3d delta = hit.subtract(eye);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        double feetY = eye.y - 1.62d;
        ItemStack stack = new ItemStack(probeCase().item(), 16);
        client.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
        client.player.setVelocity(Vec3d.ZERO);
        client.player.setSneaking(false);
        client.player.getAbilities().allowFlying = true;
        client.player.getAbilities().flying = true;
        client.player.sendAbilitiesUpdate();
        client.player.setStackInHand(Hand.MAIN_HAND, stack);
        MinecraftServer server = client.getServer();
        if (server == null || serverSyncReady || serverSyncPending) return;
        serverSyncPending = true;
        String id = activeCaseId;
        server.execute(() -> {
            if (!id.equals(activeCaseId)) return;
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                serverSyncPending = false;
                return;
            }
            var player = server.getPlayerManager().getPlayerList().get(0);
            player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            player.setVelocity(Vec3d.ZERO);
            player.setSneaking(false);
            player.changeGameMode(GameMode.CREATIVE);
            player.getAbilities().allowFlying = true;
            player.getAbilities().flying = true;
            player.sendAbilitiesUpdate();
            player.setStackInHand(Hand.MAIN_HAND, stack.copy());
            serverSyncReady = true;
            serverSyncPending = false;
        });
    }
    private static boolean validMesh(OffsetBlockStateModel.FullMeshBoundsSample mesh, double dy) {
        return mesh.seen() && same(mesh.dy(), dy) && Double.isFinite(mesh.minBeforeY())
                && Double.isFinite(mesh.maxBeforeY()) && Double.isFinite(mesh.minAfterY())
                && Double.isFinite(mesh.maxAfterY()) && !finiteTranslationMismatch(mesh, dy);
    }
    private static boolean finiteTranslationMismatch(OffsetBlockStateModel.FullMeshBoundsSample mesh, double dy) {
        if (!Double.isFinite(mesh.minBeforeY()) || !Double.isFinite(mesh.maxBeforeY())
                || !Double.isFinite(mesh.minAfterY()) || !Double.isFinite(mesh.maxAfterY())) return false;
        return !same(mesh.minAfterY() - mesh.minBeforeY(), dy)
                || !same(mesh.maxAfterY() - mesh.maxBeforeY(), dy);
    }
    static String screenshot(MinecraftClient client, String label) {
        if (client == null) return "ERROR_CLIENT_NOT_READY";
        Framebuffer framebuffer = client.getFramebuffer();
        if (framebuffer == null) return "ERROR_FRAMEBUFFER_NOT_READY";
        if (client.currentScreen != null) client.setScreen(null);
        Path path = evidenceDir.resolve(label.replaceAll("[^a-z0-9._-]+", "_") + ".png");
        try (NativeImage image = ScreenshotRecorder.takeScreenshot(framebuffer)) {
            image.writeTo(path);
            return path.toString();
        } catch (IOException | RuntimeException exception) {
            return "ERROR_" + exception.getClass().getSimpleName();
        }
    }
    private static void finish(MinecraftClient client, String verdict) {
        if (phase == Phase.FINISH) return;
        phase = Phase.FINISH;
        if (evidenceFailed) verdict = "RED_EVIDENCE_WRITE_FAILED";
        String summary = "[DEPTH_CLIENT_PROBE_SUMMARY] verdict=" + verdict + " rows="
                + (greenRows + redRows + unknownRows) + " green=" + greenRows + " red=" + redRows
                + " modelUnknown=" + unknownRows + " evidenceWriteFailed=" + evidenceFailed
                + " screenshots=" + (pistonsOnly()
                ? Boolean.getBoolean("slabbed.depthProbe.pistonLive")
                ? PistonLiveCycleProbe.screenshotsCaptured() ? "BOTH_CAPTURED_UNREVIEWED" : "INCOMPLETE"
                : "NOT_RUN" : "CAPTURED_UNREVIEWED")
                + " evidenceDir=" + evidenceDir;
        System.out.println(summary);
        append("SUMMARY\tall\t" + ticks + "\t" + verdict + "\tgreen=" + greenRows
                + "\tred=" + redRows + "\tunknown=" + unknownRows);
        if (client != null) client.scheduleStop();
    }
    private static void append(String line) {
        System.out.println("[DEPTH_CLIENT_PROBE] " + line);
        if (observations == null || evidenceFailed) return;
        try {
            Files.writeString(observations, line + "\n", StandardOpenOption.APPEND);
        } catch (IOException exception) {
            evidenceFailed = true;
            System.err.println("[DEPTH_CLIENT_PROBE] evidence write failed: "
                    + exception.getClass().getSimpleName());
        }
    }
    private static void requestServerSnapshot(MinecraftClient client) {
        if (snapshotPending || client == null || client.world == null || client.getServer() == null) return;
        snapshotPending = true;
        MinecraftServer server = client.getServer();
        RegistryKey<World> dimension = client.world.getRegistryKey();
        String id = activeCaseId;
        BlockPos support = supportPos, target = targetPos;
        PairKind pair = probeCase().pairKind();
        Block expected = expectedBlock();
        server.execute(() -> {
            if (!id.equals(activeCaseId)) return;
            ServerWorld world = server.getWorld(dimension);
            if (world == null) { snapshotPending = false; return; }
            BlockState supportState = world.getBlockState(support);
            BlockState targetState = world.getBlockState(target);
            BlockPos secondary = pair == PairKind.VERTICAL ? target.up() : null;
            if (pair == PairKind.HORIZONTAL) {
                for (Direction direction : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                    BlockPos candidate = target.offset(direction);
                    if (world.getBlockState(candidate).isOf(expected)) { secondary = candidate; break; }
                }
            }
            BlockState secondaryState = secondary == null ? Blocks.AIR.getDefaultState() : world.getBlockState(secondary);
            serverSnapshot = new ServerSnapshot(id, supportState, targetState,
                    SlabSupport.getYOffset(world, support, supportState), SlabSupport.getYOffset(world, target, targetState),
                    secondary, secondaryState, secondary == null ? Double.NaN
                    : SlabSupport.getYOffset(world, secondary, secondaryState));
            snapshotPending = false;
        });
    }
    private static boolean ready(MinecraftClient client) {
        return client != null && client.world != null && client.player != null
                && client.interactionManager != null && client.getServer() != null;
    }
    private static DimensionOptionsRegistryHolder createSuperflatDimensionOptions(DynamicRegistryManager registries) {
        return registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder();
    }
    private static ProbeCase probeCase() {
        return ROWS.get(caseIndex).spec();
    }
    private static int categoryIndex() {
        if (ROWS.get(caseIndex).sequence() >= 0) return caseIndex;
        int baseRows = CASES.size() * DEPTHS.length;
        return caseIndex < baseRows ? caseIndex / DEPTHS.length
                : CASES.size() + (caseIndex - baseRows) / EXTRA_DEPTHS.length;
    }
    private static double depth() {
        return ROWS.get(caseIndex).ownerDepth();
    }
    private static Block expectedBlock() { return probeCase().expectedBlock(); }
    private static Direction clickedFace() { return probeCase().route().face; }
    private static Vec3d visibleHit() {
        Direction face = clickedFace();
        var world = MinecraftClient.getInstance().world;
        var shape = world.getBlockState(supportPos).getOutlineShape(world, supportPos);
        Box bounds = shape.getBoundingBox();
        double band = ROWS.get(caseIndex).band();
        double x = face == Direction.WEST ? bounds.minX : face == Direction.EAST ? bounds.maxX
                : (bounds.minX + bounds.maxX) / 2.0d;
        double y = face == Direction.DOWN ? bounds.minY : face == Direction.UP ? bounds.maxY
                : bounds.minY + (bounds.maxY - bounds.minY) * band;
        double z = face == Direction.NORTH ? bounds.minZ : face == Direction.SOUTH ? bounds.maxZ
                : (bounds.minZ + bounds.maxZ) / 2.0d;
        return new Vec3d(supportPos.getX() + x, supportPos.getY() + y, supportPos.getZ() + z);
    }
    private static double expectedTargetDy() {
        BlockState cap = probeCase().capState();
        if (clickedFace() == Direction.UP && cap.isOf(Blocks.STONE_SLAB)
                && cap.get(SlabBlock.TYPE) == SlabType.BOTTOM) return depth() - 0.5d;
        if (clickedFace() != Direction.DOWN) return depth();
        if (cap.isOf(Blocks.STONE_SLAB) && cap.contains(SlabBlock.TYPE)
                && cap.get(SlabBlock.TYPE) == SlabType.TOP && depth() < 0.0d) return depth() + 0.5d;
        return depth();
    }
    private static String caseKey() { return phase == Phase.ATTACHED || caseIndex >= ROWS.size()
            ? "attached_entities" : probeCase().id() + "@" + number(depth()); }
    private static boolean same(double a, double b) { return Double.isFinite(a) && Math.abs(a - b) <= EPS; }
    private static String number(double value) { return Double.isFinite(value) ? String.format(Locale.ROOT, "%.6f", value) : "NaN"; }
    private static String blockId(BlockState state) { return Registries.BLOCK.getId(state.getBlock()).toString(); }
    private static boolean idMatches(ServerSnapshot snapshot) { return snapshot != null && activeCaseId.equals(snapshot.caseId()); }
    private static boolean pairReady(MinecraftClient client, ServerSnapshot server) {
        if (probeCase().pairKind() == PairKind.NONE) return true;
        if (!idMatches(server) || server.secondaryPos() == null
                || !server.secondaryState().isOf(expectedBlock()) || !same(server.secondaryDy(), expectedTargetDy())) return false;
        BlockState clientState = client.world.getBlockState(server.secondaryPos());
        var fact = SlabAnchorAttachment.rawPlacementDyFact(client.world, server.secondaryPos());
        return clientState.isOf(expectedBlock()) && same(ClientDy.dyFor(client.world, server.secondaryPos(), clientState), expectedTargetDy())
                && fact.present() && same(fact.valueOrNaN(), expectedTargetDy());
    }
    private static int readMaxRows() {
        int total = ROWS.size();
        try {
            return Math.max(1, Math.min(total, Integer.parseInt(System.getProperty(
                    "slabbed.depthProbe.maxRows", Integer.toString(total)))));
        } catch (NumberFormatException ignored) { return total; }
    }
    private static boolean pistonsOnly() {
        return Boolean.getBoolean("slabbed.depthProbe.pistonsOnly");
    }
    private static ProbeCase floor(String id, Item item) { return floor(id, item, ((BlockItem) item).getBlock()); }
    private static ProbeCase floor(String id, Item item, Block expected) {
        return new ProbeCase(id, item, Route.FLOOR, expected, Blocks.STONE.getDefaultState(), PairKind.NONE);
    }
    private static ProbeCase wall(String id, Item item, Block expected) {
        return new ProbeCase(id, item, Route.WALL, expected, Blocks.STONE.getDefaultState(), PairKind.NONE);
    }
    private static ProbeCase ceiling(String id, Item item) {
        return new ProbeCase(id, item, Route.CEILING, ((BlockItem) item).getBlock(), Blocks.STONE.getDefaultState(), PairKind.NONE);
    }
    private static ProbeCase pair(String id, Item item, PairKind pair) {
        return new ProbeCase(id, item, Route.FLOOR, ((BlockItem) item).getBlock(), Blocks.STONE.getDefaultState(), pair);
    }
    private enum Route {
        FLOOR(Direction.UP), WALL(Direction.EAST), CEILING(Direction.DOWN),
        NORTH(Direction.NORTH), SOUTH(Direction.SOUTH), WEST(Direction.WEST);
        private final Direction face;
        Route(Direction face) { this.face = face; }
    }
    private enum PairKind { NONE, VERTICAL, HORIZONTAL }
    private record ProbeRow(ProbeCase spec, double ownerDepth, boolean continuation, int sequence, double band) { }
    private static List<ProbeRow> buildRows() {
        List<ProbeRow> rows = new ArrayList<>();
        for (ProbeCase spec : CASES) for (double dy : DEPTHS) {
            rows.add(new ProbeRow(spec, dy, false, -1, 0.5d));
        }
        for (ProbeCase spec : EXTRA_CASES) for (double dy : EXTRA_DEPTHS) {
            rows.add(new ProbeRow(spec, dy, false, -1, 0.5d));
        }
        for (int localY : new int[]{0, 1, 2, 15}) {
            rows.add(new ProbeRow(floor("section_boundary_" + localY, Items.STONE),
                    -3.0d, false, -1, 0.5d));
        }
        for (ProbeCase spec : List.of(floor("event_campfire_extinguish", Items.CAMPFIRE),
                floor("event_redstone_burnout", Items.REDSTONE_TORCH))) {
            for (double dy : EXTRA_DEPTHS) rows.add(new ProbeRow(spec, dy, false, -1, 0.5d));
        }
        int sequence = 0;
        for (SlabType type : SlabType.values()) {
            BlockState cap = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, type);
            double continuationDy = type == SlabType.TOP ? -2.5d : -3.0d;
            Item[] hanging = {Items.CHAIN, Items.CHAIN, Items.LANTERN};
            BlockState prior = cap;
            for (int step = 0; step < hanging.length; step++) {
                Item item = hanging[step];
                ProbeCase spec = new ProbeCase("slab_" + type.asString() + "_chain_lantern_" + step,
                        item, Route.CEILING, ((BlockItem) item).getBlock(), prior, PairKind.NONE);
                rows.add(new ProbeRow(spec, step == 0 ? -3.0d : continuationDy,
                        step > 0, sequence, 0.5d));
                prior = ((BlockItem) item).getBlock().getDefaultState();
            }
            sequence++;
            prior = cap;
            for (int step = 0; step < 2; step++) {
                ProbeCase spec = new ProbeCase("slab_" + type.asString() + "_dripstone_" + step,
                        Items.POINTED_DRIPSTONE, Route.CEILING, Blocks.POINTED_DRIPSTONE,
                        prior, PairKind.NONE);
                rows.add(new ProbeRow(spec, step == 0 ? -3.0d : continuationDy,
                        step > 0, sequence, 0.5d));
                prior = Blocks.POINTED_DRIPSTONE.getDefaultState();
            }
            sequence++;
        }
        for (Route route : new Route[]{Route.NORTH, Route.WALL, Route.SOUTH, Route.WEST}) {
            for (double band : new double[]{0.25d, 0.75d}) {
                BlockState prior = Blocks.STONE.getDefaultState();
                for (int step = 0; step < 8; step++) {
                    Item item = step % 2 == 0 ? Items.STONE : Items.STONE_SLAB;
                    ProbeCase spec = new ProbeCase("cantilever_" + route.face.asString()
                            + "_" + (band < 0.5d ? "lower" : "upper") + "_" + step,
                            item, route, ((BlockItem) item).getBlock(), prior, PairKind.NONE);
                    rows.add(new ProbeRow(spec, -3.0d, step > 0, sequence, band));
                    prior = ((BlockItem) item).getBlock().getDefaultState();
                }
                sequence++;
            }
        }
        for (ProbeCase spec : List.of(floor("event_candle_extinguish", Items.CANDLE),
                floor("event_lever_use", Items.LEVER))) {
            for (double dy : EXTRA_DEPTHS) rows.add(new ProbeRow(spec, dy, false, -1, 0.5d));
        }
        for (ProbeCase spec : List.of(
                supported("small_flower", Items.DANDELION, Blocks.DANDELION, Blocks.DIRT),
                supported("sapling", Items.OAK_SAPLING, Blocks.OAK_SAPLING, Blocks.DIRT),
                supported("crop", Items.WHEAT_SEEDS, Blocks.WHEAT, Blocks.FARMLAND),
                supported("cactus", Items.CACTUS, Blocks.CACTUS, Blocks.SAND),
                supported("bamboo", Items.BAMBOO, Blocks.BAMBOO_SAPLING, Blocks.DIRT),
                new ProbeCase("tall_flower", Items.SUNFLOWER, Route.FLOOR, Blocks.SUNFLOWER,
                        Blocks.DIRT.getDefaultState(), PairKind.VERTICAL),
                supported("sea_pickle", Items.SEA_PICKLE, Blocks.SEA_PICKLE, Blocks.PRISMARINE),
                supported("turtle_egg", Items.TURTLE_EGG, Blocks.TURTLE_EGG, Blocks.SAND),
                floor("powder_snow_bucket", Items.POWDER_SNOW_BUCKET, Blocks.POWDER_SNOW))) {
            for (double dy : EXTRA_DEPTHS) rows.add(new ProbeRow(spec, dy, false, -1, 0.5d));
        }
        for (boolean slab : new boolean[]{false, true}) {
            BlockState cap = slab ? Blocks.STONE_SLAB.getDefaultState() : Blocks.STONE.getDefaultState();
            ProbeCase spec = new ProbeCase("break_pane_" + (slab ? "slab" : "full"), Items.GLASS_PANE,
                    Route.FLOOR, Blocks.GLASS_PANE, cap, PairKind.NONE);
            for (double dy : new double[]{0.0d, slab ? -2.5d : -3.0d}) {
                rows.add(new ProbeRow(spec, dy, false, -1, 0.5d));
            }
        }
        for (double dy : EXTRA_DEPTHS) rows.add(new ProbeRow(
                supported("event_turtle_egg_placed", Items.TURTLE_EGG, Blocks.TURTLE_EGG, Blocks.SAND),
                dy, false, -1, 0.5d));
        return List.copyOf(rows);
    }
    private static ProbeCase supported(String id, Item item, Block expected, Block support) {
        return new ProbeCase(id, item, Route.FLOOR, expected, support.getDefaultState(), PairKind.NONE);
    }
    private record ProbeCase(String id, Item item, Route route, Block expectedBlock,
                             BlockState capState, PairKind pairKind) {
        private ProbeCase(String id, Item item) {
            this(id, item, Route.FLOOR, ((BlockItem) item).getBlock(), Blocks.STONE.getDefaultState(), PairKind.NONE);
        }
    }
    private record ServerSnapshot(String caseId, BlockState supportState, BlockState targetState,
                                  double supportDy, double targetDy, BlockPos secondaryPos,
                                  BlockState secondaryState, double secondaryDy) {
        private static ServerSnapshot missing(String id) {
            return new ServerSnapshot(id, Blocks.AIR.getDefaultState(), Blocks.AIR.getDefaultState(), Double.NaN,
                    Double.NaN, null, Blocks.AIR.getDefaultState(), Double.NaN);
        }
    }
}
