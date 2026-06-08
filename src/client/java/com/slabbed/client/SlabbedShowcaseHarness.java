package com.slabbed.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dev-only client harness (gated by {@code -Dslabbed.mc1211.screenshotShowcase=true}) that
 * builds a Terrain Slabs showcase scene in a fresh superflat creative world, frames a 3/4
 * camera, hides the HUD, and captures a clean PNG to {@code run/slabbed-screenshots/}, then
 * stops the client.
 *
 * <p>Runs under {@code runClient} (NOT the gametest harness) on purpose: there the real
 * Terrain Slabs mod loads with no {@code provides} conflict, so {@code terrain_slabs:*_slab}
 * surfaces actually exist. Inert in production (the property is unset).
 */
public final class SlabbedShowcaseHarness {

    private static final String PROPERTY = "slabbed.mc1211.screenshotShowcase";

    private static boolean worldRequested;
    private static boolean sceneBuilt;
    private static boolean cameraSet;
    private static boolean captured;
    private static int ticks;
    private static int sceneBuiltTick;
    private static int cameraSetTick;
    private static BlockPos origin;

    private SlabbedShowcaseHarness() {
    }

    public static void init() {
        if (!Boolean.getBoolean(PROPERTY)) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(SlabbedShowcaseHarness::onTick);
        System.out.println("[SLABBED_SHOWCASE] armed");
    }

    private static void onTick(MinecraftClient client) {
        if (captured || client == null) {
            return;
        }
        ticks++;

        if (!worldRequested) {
            if (!client.isFinishedLoading() || client.world != null || client.player != null) {
                return;
            }
            worldRequested = true;
            LevelInfo levelInfo = new LevelInfo(
                    "Slabbed TS Showcase", GameMode.CREATIVE, false,
                    Difficulty.PEACEFUL, true, new GameRules(), DataConfiguration.SAFE_MODE);
            GeneratorOptions generatorOptions = new GeneratorOptions(0L, false, false);
            System.out.println("[SLABBED_SHOWCASE] world-start superflat creative");
            client.createIntegratedServerLoader().createAndStart(
                    "slabbed-ts-showcase", levelInfo, generatorOptions,
                    SlabbedShowcaseHarness::superflatDimensions, null);
            return;
        }

        if (client.world == null || client.player == null) {
            return;
        }
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null) {
            return;
        }

        if (!sceneBuilt) {
            sceneBuilt = true;
            sceneBuiltTick = ticks;
            origin = client.player.getBlockPos().toImmutable();
            BlockPos o = origin;
            serverWorld.getServer().execute(() -> {
                buildScene(serverWorld, o);
                serverWorld.setTimeOfDay(1000L);
            });
            System.out.println("[SLABBED_SHOWCASE] scene-build origin=" + o.toShortString());
            return;
        }
        if (ticks < sceneBuiltTick + 40) {
            return;
        }

        BlockPos o = origin;
        double camX = o.getX() + 1.0;
        double camEyeY = o.getY() + 3.1;
        double camZ = o.getZ() - 1.8;
        double tgtX = o.getX() + 5.2;
        double tgtY = o.getY() + 0.3;
        double tgtZ = o.getZ() + 1.0;
        double dx = tgtX - camX;
        double dy = tgtY - camEyeY;
        double dz = tgtZ - camZ;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, horiz));
        client.player.getAbilities().flying = true;
        client.player.refreshPositionAndAngles(camX, camEyeY - 1.62, camZ, yaw, pitch);
        client.player.setVelocity(0.0, 0.0, 0.0);
        client.options.hudHidden = true;
        if (!cameraSet) {
            cameraSet = true;
            cameraSetTick = ticks;
            System.out.println("[SLABBED_SHOWCASE] camera yaw=" + yaw + " pitch=" + pitch);
        }

        if (ticks < cameraSetTick + 100) {
            return;
        }

        captured = true;
        capture(client);
    }

    private static DimensionOptionsRegistryHolder superflatDimensions(DynamicRegistryManager registries) {
        return registries.get(RegistryKeys.WORLD_PRESET)
                .getOrThrow(WorldPresets.FLAT)
                .createDimensionsRegistryHolder();
    }

    private static ServerWorld serverWorldFor(MinecraftClient client) {
        MinecraftServer server = client.getServer();
        if (server == null || client.world == null) {
            return null;
        }
        return server.getWorld(client.world.getRegistryKey());
    }

    private static void buildScene(ServerWorld w, BlockPos o) {
        for (int x = -1; x <= 9; x++) {
            for (int z = -2; z <= 3; z++) {
                w.setBlockState(o.add(x, -1, z), Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
                for (int y = 0; y <= 4; y++) {
                    w.setBlockState(o.add(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
        String[][] rowSpec = {
                {"moss_slab", "minecraft", "lantern"},
                {"calcite_slab", "minecraft", "torch"},
                {"podzol_slab", "minecraft", "oak_log"},
                {"sand_slab", "minecraft", "white_carpet"},
                {"deepslate_slab", "minecraft", "soul_lantern"}
        };
        int x = 2;
        for (String[] spec : rowSpec) {
            Block slab = Registries.BLOCK.get(Identifier.of("terrain_slabs", spec[0]));
            Block obj = Registries.BLOCK.get(Identifier.of(spec[1], spec[2]));
            BlockState slabState = slab.getDefaultState();
            if (slabState.contains(SlabBlock.TYPE)) {
                slabState = slabState.with(SlabBlock.TYPE, SlabType.BOTTOM);
            }
            w.setBlockState(o.add(x, 0, 1), slabState, Block.NOTIFY_LISTENERS);
            w.setBlockState(o.add(x, 1, 1), obj.getDefaultState(), Block.NOTIFY_LISTENERS);
            x++;
        }
    }

    private static void capture(MinecraftClient client) {
        try {
            Path dir = FabricLoader.getInstance().getGameDir().resolve("run").resolve("slabbed-screenshots");
            Files.createDirectories(dir);
            Path out = dir.resolve("slabbed-ts-showcase.png");
            try (NativeImage image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
                image.writeTo(out);
            }
            System.out.println("[SLABBED_SHOWCASE] SCREENSHOT_SAVED path=" + out);
        } catch (Exception e) {
            System.out.println("[SLABBED_SHOWCASE] SCREENSHOT_FAILED " + e);
        }
        client.execute(client::scheduleStop);
    }
}
