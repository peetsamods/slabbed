package com.slabbed.test;

import com.slabbed.anchor.SlabPlacementHeightAttachment;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Drives the server half of the isolated P2b persistence proof. */
public final class SlabPlacementHeightP2bProof {
    public static final String PHASE_PROPERTY = "slabbed.p2b.phase";
    public static final String WORLD_NAME = "p2b-proof";
    public static final BlockPos RETAINED_POS = new BlockPos(2, 80, 2);
    public static final BlockPos REMOVED_POS = new BlockPos(3, 80, 2);
    public static final BlockPos LEGACY_POS = new BlockPos(4, 80, 2);
    private static boolean registered;
    private static boolean stopPending;

    private SlabPlacementHeightP2bProof() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        String phase = System.getProperty(PHASE_PROPERTY);
        if (!"writer".equals(phase) && !"client".equals(phase)) {
            throw new IllegalStateException("Unknown P2b proof phase");
        }
        registered = true;
        if ("writer".equals(phase)) {
            NeoForge.EVENT_BUS.addListener(SlabPlacementHeightP2bProof::onServerStarted);
            NeoForge.EVENT_BUS.addListener(SlabPlacementHeightP2bProof::onServerTick);
        }
    }

    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        LevelChunk chunk = level.getChunkAt(RETAINED_POS);

        level.setBlockAndUpdate(RETAINED_POS, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(REMOVED_POS, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(LEGACY_POS, Blocks.STONE.defaultBlockState());
        level.setDefaultSpawnPos(new BlockPos(1, 81, 1), 0.0F);

        require(SlabPlacementHeightAttachment.putHalfSteps(chunk, RETAINED_POS, -1),
                "writer_initial_retained");
        require(SlabPlacementHeightAttachment.putHalfSteps(chunk, RETAINED_POS, -2),
                "writer_replaced_retained");
        require(SlabPlacementHeightAttachment.putHalfSteps(chunk, REMOVED_POS, -1),
                "writer_initial_removed");
        require(SlabPlacementHeightAttachment.storedHalfSteps(chunk, RETAINED_POS).orElse(0) == -2,
                "writer_retained_value");
        require(SlabPlacementHeightAttachment.storedHalfSteps(chunk, REMOVED_POS).orElse(0) == -1,
                "writer_removed_value");
        require(SlabPlacementHeightAttachment.storedHalfSteps(chunk, LEGACY_POS).isEmpty(),
                "writer_legacy_absence");
        require(chunk.isUnsaved(), "writer_chunk_dirty");
        require(server.saveAllChunks(false, true, true), "writer_disk_save");
        writeReceipt("writer.ok", "writer=green\n");
        stopPending = true;
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (stopPending) {
            stopPending = false;
            event.getServer().halt(false);
        }
    }

    public static void writeReceipt(String fileName, String content) {
        try {
            Path proofDirectory = Path.of("proof");
            Files.createDirectories(proofDirectory);
            Files.writeString(proofDirectory.resolve(fileName), content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write the P2b proof receipt", exception);
        }
    }

    private static void require(boolean condition, String check) {
        if (!condition) {
            throw new IllegalStateException("P2b proof check failed: " + check);
        }
    }
}
