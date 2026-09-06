package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.client.model.OffsetBlockStateModel;
import com.slabbed.util.SlabSupport;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Existing-world proof for the shipped per-cell mode, driven in the native client.
 *
 * <p>{@code -Dslabbed.keepProbe=author} creates a disposable world, authors cells the way a
 * pre-policy world carries them (anchor-only, compound, a latent height fact without provenance,
 * flat) plus one cell carrying the provenance an item placement writes, records their heights on
 * server, client and mesh, saves, and quits. {@code -Dslabbed.keepProbe=reopen} opens that saved
 * world again, re-checks every cell against the recorded expectations, then places a real item on
 * top of the legacy lowered cell through the client interaction path and checks that the new block
 * receives provenance and lands on the old block's visible top.
 */
public final class KeepOnlyReopenProbe implements ClientModInitializer {
    private static final String MODE_PROPERTY = "slabbed.keepProbe";
    private static final String EVIDENCE_PROPERTY = "slabbed.keepProbe.evidenceDir";
    private static final String WORLD_PROPERTY = "slabbed.keepProbe.world";
    private static final String CONTROLS_PROPERTY = "slabbed.keepProbe.controls";
    private static final int BOOTSTRAP_TIMEOUT = 2400, SETTLE_TICKS = 80, MESH_TIMEOUT = 200, PLACE_TIMEOUT = 200;
    private static final double EPS = 1.0e-6d;

    private record Control(String name, BlockPos pos, double expected) {
    }

    private record ServerObservation(String state, double dy, boolean fact, double factValue, boolean modern) {
    }

    private enum Phase { BOOTSTRAP, AUTHOR, SETTLE, OBSERVE, PLACE, OBSERVE_PLACED, SAVE, FINISH }

    private static boolean initialized, worldRequested, authored, saved;
    private static Phase phase = Phase.BOOTSTRAP;
    private static int ticks, phaseTick, controlIndex, greenRows, redRows;
    private static String mode = "";
    private static Path evidenceDir, observations, controlsFile;
    private static String worldName;
    private static final List<Control> CONTROLS = new ArrayList<>();
    private static BlockPos viewFeet;
    private static float viewYaw, viewPitch;
    private static final Map<String, ServerObservation> SERVER = new ConcurrentHashMap<>();
    private static volatile boolean serverPending;
    private static BlockPos placedSupport, placedCell;
    private static String placementResult = "not_started";

    @Override
    public void onInitializeClient() {
        if (initialized) return;
        initialized = true;
        mode = System.getProperty(MODE_PROPERTY, "").trim();
        ClientTickEvents.END_CLIENT_TICK.register(KeepOnlyReopenProbe::tick);
    }

    private static void tick(MinecraftClient client) {
        if (mode.isEmpty() || phase == Phase.FINISH) return;
        ticks++;
        try {
            if (evidenceDir == null && !initializeEvidence()) {
                finish(client, "RED_MISSING_OR_INVALID_EVIDENCE_DIR");
                return;
            }
            switch (phase) {
                case BOOTSTRAP -> bootstrap(client);
                case AUTHOR -> author(client);
                case SETTLE -> settle(client);
                case OBSERVE -> observe(client);
                case PLACE -> place(client);
                case OBSERVE_PLACED -> observePlaced(client);
                case SAVE -> save(client);
                default -> { }
            }
        } catch (RuntimeException exception) {
            exception.printStackTrace();
            redRows++;
            finish(client, "RED_EXCEPTION_" + exception.getClass().getSimpleName());
        }
    }

    private static boolean initializeEvidence() {
        String configured = System.getProperty(EVIDENCE_PROPERTY, "").trim();
        String controls = System.getProperty(CONTROLS_PROPERTY, "").trim();
        if (configured.isEmpty() || controls.isEmpty()) return false;
        try {
            Path root = Path.of(configured).toAbsolutePath().normalize();
            evidenceDir = root.resolve("keep-" + mode + "-" + Long.toUnsignedString(System.currentTimeMillis(), 36));
            Files.createDirectories(evidenceDir);
            observations = evidenceDir.resolve("observations.tsv");
            Files.writeString(observations,
                    "kind\tmode\tname\tpos\texpected\tserverState\tserverDy\tclientState\tclientDy\tclientFact\tclientModern\tmeshSeen\tmeshDy\tverdict\n",
                    StandardOpenOption.CREATE_NEW);
            controlsFile = Path.of(controls).toAbsolutePath().normalize();
            worldName = System.getProperty(WORLD_PROPERTY, "slabbed-keep-fixture").trim();
            return true;
        } catch (IOException | RuntimeException exception) {
            evidenceDir = null;
            return false;
        }
    }

    private static void bootstrap(MinecraftClient client) {
        if (!worldRequested && client != null && (client.world != null || client.player != null)) {
            redRows++;
            finish(client, "RED_EXISTING_WORLD_OPEN");
            return;
        }
        if (!worldRequested && client != null && client.isFinishedLoading()) {
            worldRequested = true;
            if ("author".equals(mode)) {
                LevelInfo info = new LevelInfo("Slabbed Keep Fixture", GameMode.CREATIVE, false,
                        Difficulty.PEACEFUL, true, new GameRules(), DataConfiguration.SAFE_MODE);
                client.createIntegratedServerLoader().createAndStart(worldName, info,
                        new GeneratorOptions(0L, false, false), KeepOnlyReopenProbe::flat, null);
                append("WORLD\t" + mode + "\t" + worldName + "\t-\t-\t-\t-\t-\t-\t-\t-\t-\t-\tcreate_requested");
            } else {
                client.createIntegratedServerLoader().start(worldName, () -> finish(client, "RED_WORLD_OPEN_CANCELLED"));
                append("WORLD\t" + mode + "\t" + worldName + "\t-\t-\t-\t-\t-\t-\t-\t-\t-\t-\treopen_requested");
            }
            return;
        }
        if (ready(client)) {
            client.options.pauseOnLostFocus = false;
            if (client.currentScreen != null) client.setScreen(null);
            if ("author".equals(mode)) {
                phase = Phase.AUTHOR;
            } else {
                loadControls();
                phase = Phase.SETTLE;
                teleport(client);
            }
            phaseTick = ticks;
        } else if (ticks >= BOOTSTRAP_TIMEOUT) {
            redRows++;
            finish(client, "RED_BOOTSTRAP_TIMEOUT");
        }
    }

    private static DimensionOptionsRegistryHolder flat(DynamicRegistryManager registries) {
        return registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder();
    }

    private static boolean ready(MinecraftClient client) {
        return client != null && client.world != null && client.player != null
                && client.interactionManager != null && client.getServer() != null;
    }

    /** Author mode: cells as a pre-policy world carries them, plus one cell with modern provenance. */
    private static void author(MinecraftClient client) {
        if (authored) {
            phase = Phase.SETTLE;
            phaseTick = ticks;
            teleport(client);
            return;
        }
        BlockPos origin = client.player.getBlockPos().add(8, 2, 8).toImmutable();
        CONTROLS.clear();
        CONTROLS.add(new Control("legacy_half", origin, -0.5d));
        CONTROLS.add(new Control("legacy_compound", origin.add(3, 0, 0), -1.0d));
        CONTROLS.add(new Control("latent_fact", origin.add(6, 0, 0), 0.0d));
        CONTROLS.add(new Control("flat", origin.add(9, 0, 0), 0.0d));
        CONTROLS.add(new Control("modern_half", origin.add(12, 0, 0), -0.5d));
        viewFeet = origin.add(6, 1, -6);
        viewYaw = 0.0f;
        viewPitch = 25.0f;
        MinecraftServer server = client.getServer();
        RegistryKey<World> dimension = client.world.getRegistryKey();
        server.execute(() -> {
            ServerWorld world = server.getWorld(dimension);
            if (world == null) return;
            for (Control control : CONTROLS) {
                for (int y = -1; y <= 2; y++) {
                    BlockPos cell = control.pos().add(0, y, 0);
                    SlabAnchorAttachment.removeAnchor(world, cell);
                    world.setBlockState(cell, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
            BlockPos legacyHalf = CONTROLS.get(0).pos(), legacyCompound = CONTROLS.get(1).pos(),
                    latent = CONTROLS.get(2).pos(), flat = CONTROLS.get(3).pos(), modern = CONTROLS.get(4).pos();
            world.setBlockState(legacyHalf, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(legacyCompound, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(latent.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(latent, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(flat.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(flat, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(modern.down(),
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
            world.setBlockState(modern, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            // Legacy representation exactly as the headless disk round-trip row authors it.
            for (BlockPos pos : List.of(legacyHalf, legacyCompound)) {
                WorldChunk chunk = world.getWorldChunk(pos);
                LongOpenHashSet anchors = chunk.getAttached(SlabAnchorAttachment.ANCHOR_TYPE) == null
                        ? new LongOpenHashSet() : new LongOpenHashSet(chunk.getAttached(SlabAnchorAttachment.ANCHOR_TYPE));
                anchors.add(pos.asLong());
                chunk.setAttached(SlabAnchorAttachment.ANCHOR_TYPE, anchors);
            }
            WorldChunk compoundChunk = world.getWorldChunk(legacyCompound);
            LongOpenHashSet compounds = compoundChunk.getAttached(SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE) == null
                    ? new LongOpenHashSet()
                    : new LongOpenHashSet(compoundChunk.getAttached(SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE));
            compounds.add(legacyCompound.asLong());
            compoundChunk.setAttached(SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE, compounds);
            WorldChunk latentChunk = world.getWorldChunk(latent);
            Long2ByteOpenHashMap facts = latentChunk.getAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE) == null
                    ? new Long2ByteOpenHashMap()
                    : new Long2ByteOpenHashMap(latentChunk.getAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE));
            facts.put(latent.asLong(), (byte) SlabAnchorAttachment.quantiseDy(-2.0d));
            latentChunk.setAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE, facts);
            // The modern cell carries exactly what an accepted item placement writes: fact + provenance.
            SlabAnchorAttachment.writePlacementDyBatch(world, Map.of(modern, Double.doubleToRawLongBits(-0.5d)));
            SlabAnchorAttachment.markPostPolicyPlacements(world, List.of(modern));
            for (Control control : CONTROLS) world.getChunkManager().markForUpdate(control.pos());
            authored = true;
        });
        try {
            StringBuilder lines = new StringBuilder("name\tx\ty\tz\texpected\n");
            for (Control control : CONTROLS) {
                lines.append(control.name()).append('\t').append(control.pos().getX()).append('\t')
                        .append(control.pos().getY()).append('\t').append(control.pos().getZ()).append('\t')
                        .append(control.expected()).append('\n');
            }
            lines.append("VIEW\t").append(viewFeet.getX()).append('\t').append(viewFeet.getY()).append('\t')
                    .append(viewFeet.getZ()).append('\t').append(viewYaw).append('/').append(viewPitch).append('\n');
            Files.writeString(controlsFile, lines.toString());
        } catch (IOException exception) {
            throw new IllegalStateException("controls write failed", exception);
        }
    }

    private static void loadControls() {
        try {
            CONTROLS.clear();
            for (String line : Files.readAllLines(controlsFile)) {
                String[] parts = line.split("\t");
                if (parts.length < 5 || parts[0].equals("name")) continue;
                BlockPos pos = new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                if (parts[0].equals("VIEW")) {
                    viewFeet = pos;
                    String[] angles = parts[4].split("/");
                    viewYaw = Float.parseFloat(angles[0]);
                    viewPitch = Float.parseFloat(angles[1]);
                } else {
                    CONTROLS.add(new Control(parts[0], pos, Double.parseDouble(parts[4])));
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("controls read failed", exception);
        }
        if (CONTROLS.isEmpty() || viewFeet == null) throw new IllegalStateException("controls file incomplete");
    }

    private static void teleport(MinecraftClient client) {
        MinecraftServer server = client.getServer();
        java.util.UUID id = client.player.getUuid();
        double x = viewFeet.getX() + 0.5d, y = viewFeet.getY(), z = viewFeet.getZ() + 0.5d;
        float yaw = viewYaw, pitch = viewPitch;
        server.execute(() -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player != null) player.networkHandler.requestTeleport(x, y, z, yaw, pitch);
        });
        client.player.getAbilities().allowFlying = true;
        client.player.getAbilities().flying = true;
        client.player.sendAbilitiesUpdate();
    }

    private static void settle(MinecraftClient client) {
        if (ticks - phaseTick < SETTLE_TICKS) return;
        controlIndex = 0;
        phase = Phase.OBSERVE;
        phaseTick = ticks;
        beginControl(client);
    }

    private static void beginControl(MinecraftClient client) {
        Control control = CONTROLS.get(controlIndex);
        OffsetBlockStateModel.resetFullMeshBoundsSample(control.pos());
        client.worldRenderer.scheduleBlockRenders(control.pos().getX(), control.pos().getY(), control.pos().getZ(),
                control.pos().getX(), control.pos().getY(), control.pos().getZ());
        SERVER.remove(control.name());
        requestServer(client, control.name(), control.pos());
        phaseTick = ticks;
    }

    private static void requestServer(MinecraftClient client, String name, BlockPos pos) {
        if (serverPending) return;
        serverPending = true;
        MinecraftServer server = client.getServer();
        RegistryKey<World> dimension = client.world.getRegistryKey();
        server.execute(() -> {
            ServerWorld world = server.getWorld(dimension);
            if (world != null) {
                BlockState state = world.getBlockState(pos);
                var fact = SlabAnchorAttachment.rawPlacementDyFact(world, pos);
                SERVER.put(name, new ServerObservation(state.getBlock().toString(),
                        SlabSupport.getYOffset(world, pos, state), fact.present(), fact.valueOrNaN(),
                        SlabAnchorAttachment.isModernPlacement(world, pos)));
            }
            serverPending = false;
        });
    }

    private static void observe(MinecraftClient client) {
        Control control = CONTROLS.get(controlIndex);
        ServerObservation server = SERVER.get(control.name());
        OffsetBlockStateModel.FullMeshBoundsSample mesh = OffsetBlockStateModel.snapshotFullMeshBoundsSample();
        boolean timedOut = ticks - phaseTick >= MESH_TIMEOUT;
        if (server == null || (!mesh.seen() && !timedOut)) {
            if (server == null && ticks - phaseTick >= MESH_TIMEOUT) requestServer(client, control.name(), control.pos());
            return;
        }
        record(client, "CONTROL", control.name(), control.pos(), control.expected(), server, mesh);
        controlIndex++;
        if (controlIndex < CONTROLS.size()) {
            beginControl(client);
            return;
        }
        screenshot(client, mode + "-controls");
        if ("author".equals(mode)) {
            phase = Phase.SAVE;
        } else {
            phase = Phase.PLACE;
        }
        phaseTick = ticks;
    }

    private static void record(MinecraftClient client, String kind, String name, BlockPos pos, double expected,
                               ServerObservation server, OffsetBlockStateModel.FullMeshBoundsSample mesh) {
        BlockState clientState = client.world.getBlockState(pos);
        double clientDy = ClientDy.dyFor(client.world, pos, clientState);
        var clientFact = SlabAnchorAttachment.rawPlacementDyFact(client.world, pos);
        boolean clientModern = SlabAnchorAttachment.isModernPlacement(client.world, pos);
        boolean serverOk = same(server.dy(), expected);
        boolean clientOk = same(clientDy, expected);
        boolean meshOk = !mesh.seen() || same(mesh.dy(), expected);
        boolean green = serverOk && clientOk && meshOk && mesh.seen();
        if (green) greenRows++; else redRows++;
        append(kind + "\t" + mode + "\t" + name + "\t" + pos.toShortString() + "\t" + expected
                + "\t" + server.state() + "\t" + server.dy()
                + "\t" + clientState.getBlock() + "\t" + clientDy
                + "\t" + (clientFact.present() ? clientFact.valueOrNaN() : "none") + "\t" + clientModern
                + "\t" + mesh.seen() + "\t" + (mesh.seen() ? mesh.dy() : "NaN")
                + "\t" + (green ? "GREEN" : "RED"
                        + (serverOk ? "" : "_SERVER") + (clientOk ? "" : "_CLIENT")
                        + (mesh.seen() ? (meshOk ? "" : "_MESH") : "_MESH_UNSEEN")));
    }

    /** Reopen mode: a real item placement onto the legacy lowered cell's visible top. */
    private static void place(MinecraftClient client) {
        Control legacy = CONTROLS.get(0);
        if (placedSupport == null) {
            placedSupport = legacy.pos();
            placedCell = placedSupport.up();
            Vec3d hit = new Vec3d(placedSupport.getX() + 0.5d, placedSupport.getY() + 1.0d + legacy.expected(),
                    placedSupport.getZ() + 0.5d);
            Vec3d eye = hit.add(0.0d, 1.1d, 2.6d);
            Vec3d delta = hit.subtract(eye);
            double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
            float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
            double feetY = eye.y - 1.62d;
            MinecraftServer server = client.getServer();
            java.util.UUID id = client.player.getUuid();
            server.execute(() -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                if (player == null) return;
                player.getInventory().setStack(player.getInventory().selectedSlot, new ItemStack(Items.STONE, 16));
                player.networkHandler.requestTeleport(eye.x, feetY, eye.z, yaw, pitch);
            });
            client.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            client.player.setVelocity(Vec3d.ZERO);
            client.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE, 16));
            phaseTick = ticks;
            return;
        }
        if (ticks - phaseTick < 20) return;
        client.gameRenderer.updateCrosshairTarget(0.0f);
        HitResult hit = client.crosshairTarget;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                && blockHit.getBlockPos().equals(placedSupport) && blockHit.getSide() == Direction.UP) {
            ActionResult result = client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, blockHit);
            placementResult = String.valueOf(result);
            append("PLACE\t" + mode + "\tonto_legacy_half\t" + placedCell.toShortString() + "\t" + legacy.expected()
                    + "\t-\t-\t-\t-\t-\t-\t-\t-\thit=" + blockHit.getPos() + " result=" + result);
            OffsetBlockStateModel.resetFullMeshBoundsSample(placedCell);
            SERVER.remove("placed");
            phase = Phase.OBSERVE_PLACED;
            phaseTick = ticks;
            return;
        }
        if (ticks - phaseTick >= PLACE_TIMEOUT) {
            redRows++;
            append("PLACE\t" + mode + "\tonto_legacy_half\t" + placedCell.toShortString() + "\t" + legacy.expected()
                    + "\t-\t-\t-\t-\t-\t-\t-\t-\tRED_CROSSHAIR_MISS hit=" + hit);
            screenshot(client, mode + "-placement-miss");
            finish(client, "RED_PLACEMENT_CROSSHAIR");
        }
    }

    private static void observePlaced(MinecraftClient client) {
        double expected = CONTROLS.get(0).expected();
        requestServer(client, "placed", placedCell);
        ServerObservation server = SERVER.get("placed");
        OffsetBlockStateModel.FullMeshBoundsSample mesh = OffsetBlockStateModel.snapshotFullMeshBoundsSample();
        boolean timedOut = ticks - phaseTick >= MESH_TIMEOUT;
        if (server == null || !server.state().contains("stone") || !client.world.getBlockState(placedCell).isOf(Blocks.STONE)
                || (!mesh.seen() && !timedOut)) {
            if (timedOut && server != null) {
                record(client, "PLACED", "onto_legacy_half", placedCell, expected, server, mesh);
                screenshot(client, mode + "-after-placement");
                finish(client, "RED_PLACEMENT_INCOMPLETE");
            }
            return;
        }
        boolean provenance = server.modern() && server.fact() && same(server.factValue(), expected);
        record(client, "PLACED", "onto_legacy_half", placedCell, expected, server, mesh);
        if (!provenance) {
            redRows++;
            append("PLACED_PROVENANCE\t" + mode + "\tonto_legacy_half\t" + placedCell.toShortString() + "\t" + expected
                    + "\t" + server.state() + "\t" + server.dy() + "\t-\t-\t" + server.factValue() + "\t" + server.modern()
                    + "\t-\t-\tRED_NO_PROVENANCE");
        }
        // The legacy owner underneath must still read its old height after the placement on top.
        Control legacy = CONTROLS.get(0);
        SERVER.remove("legacy_after");
        requestServer(client, "legacy_after", legacy.pos());
        phase = Phase.SAVE;
        phaseTick = ticks;
        screenshot(client, mode + "-after-placement");
    }

    private static void save(MinecraftClient client) {
        if ("reopen".equals(mode)) {
            ServerObservation legacyAfter = SERVER.get("legacy_after");
            if (legacyAfter == null) {
                if (ticks - phaseTick > MESH_TIMEOUT) finish(client, "RED_LEGACY_RECHECK_TIMEOUT");
                return;
            }
            Control legacy = CONTROLS.get(0);
            boolean ok = same(legacyAfter.dy(), legacy.expected()) && !legacyAfter.modern();
            if (ok) greenRows++; else redRows++;
            append("LEGACY_AFTER_PLACEMENT\t" + mode + "\t" + legacy.name() + "\t" + legacy.pos().toShortString() + "\t"
                    + legacy.expected() + "\t" + legacyAfter.state() + "\t" + legacyAfter.dy() + "\t-\t-\t-\t"
                    + legacyAfter.modern() + "\tfalse\tNaN\t" + (ok ? "GREEN" : "RED"));
            finish(client, redRows > 0 ? "RED" : "GREEN");
            return;
        }
        if (!saved) {
            saved = true;
            MinecraftServer server = client.getServer();
            server.execute(() -> {
                server.saveAll(true, true, true);
                append("SAVE\t" + mode + "\t" + worldName + "\t-\t-\t-\t-\t-\t-\t-\t-\t-\t-\tsaveAll_done");
                SERVER.put("saved", new ServerObservation("saved", 0.0d, false, Double.NaN, false));
            });
            phaseTick = ticks;
            return;
        }
        if (SERVER.containsKey("saved") && ticks - phaseTick >= 20) {
            finish(client, redRows > 0 ? "RED" : "GREEN");
        } else if (ticks - phaseTick > MESH_TIMEOUT) {
            finish(client, "RED_SAVE_TIMEOUT");
        }
    }

    private static boolean same(double actual, double expected) {
        return Double.isFinite(actual) && Double.isFinite(expected) && Math.abs(actual - expected) <= EPS;
    }

    private static String screenshot(MinecraftClient client, String label) {
        if (client == null) return "ERROR_CLIENT_NOT_READY";
        Framebuffer framebuffer = client.getFramebuffer();
        if (framebuffer == null) return "ERROR_FRAMEBUFFER_NOT_READY";
        if (client.currentScreen != null) client.setScreen(null);
        Path path = evidenceDir.resolve(label.replaceAll("[^a-z0-9._-]+", "_") + ".png");
        try (NativeImage image = ScreenshotRecorder.takeScreenshot(framebuffer)) {
            image.writeTo(path);
            append("SCREENSHOT\t" + mode + "\t" + label + "\t-\t-\t-\t-\t-\t-\t-\t-\t-\t-\t" + path);
            return path.toString();
        } catch (IOException | RuntimeException exception) {
            return "ERROR_" + exception.getClass().getSimpleName();
        }
    }

    private static void finish(MinecraftClient client, String verdict) {
        if (phase == Phase.FINISH) return;
        phase = Phase.FINISH;
        String summary = "[KEEP_REOPEN_PROBE_SUMMARY] mode=" + mode + " verdict=" + verdict
                + " rows=" + (greenRows + redRows) + " green=" + greenRows + " red=" + redRows
                + " placement=" + placementResult + " world=" + worldName + " evidenceDir=" + evidenceDir;
        System.out.println(summary);
        append("SUMMARY\t" + mode + "\t" + verdict + "\t-\t-\t-\t-\t-\t-\t-\t-\t-\t-\tgreen=" + greenRows + " red=" + redRows);
        if (client != null) client.scheduleStop();
    }

    private static void append(String line) {
        System.out.println("[KEEP_REOPEN_PROBE] " + line);
        if (observations == null) return;
        try {
            Files.writeString(observations, line + "\n", StandardOpenOption.APPEND);
        } catch (IOException exception) {
            System.err.println("[KEEP_REOPEN_PROBE] evidence write failed: " + exception.getClass().getSimpleName());
        }
    }
}
