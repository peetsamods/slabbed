package com.slabbed.anchor;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Regression proof for issue #38: dense {@code slabbed:frozen_flat} chunk data must remain
 * exact without exceeding Fabric's per-attachment synchronization ceiling.
 */
public final class FrozenFlatAttachmentCapacityTest {
    private static final int LAST_RAW_LONG_COUNT_THAT_FITS = 2_047;
    private static final int FIRST_RAW_LONG_COUNT_THAT_OVERFLOWS = 2_048;
    private static final int FABRIC_ATTACHMENT_MAX_DATA_BYTES = 32_502;

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void twoThousandFortyEightMarkersSetWithoutOverflow(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        int sectionY = origin.getY() >> 4;
        WorldChunk chunk = world.getChunk(chunkX, chunkZ);

        LongOpenHashSet justBelowBoundary =
                sectionPrefix(chunkX, sectionY, chunkZ, LAST_RAW_LONG_COUNT_THAT_FITS);
        chunk.setAttached(SlabAnchorAttachment.FROZEN_FLAT_TYPE, justBelowBoundary);
        ctx.assertEquals(
                justBelowBoundary,
                chunk.getAttached(SlabAnchorAttachment.FROZEN_FLAT_TYPE),
                "2,047 exact frozen-flat markers should survive the current attachment path");

        LongOpenHashSet firstOverflow =
                sectionPrefix(chunkX, sectionY, chunkZ, FIRST_RAW_LONG_COUNT_THAT_OVERFLOWS);
        IllegalArgumentException overflow = null;
        try {
            chunk.setAttached(SlabAnchorAttachment.FROZEN_FLAT_TYPE, firstOverflow);
            ctx.assertEquals(
                    firstOverflow,
                    chunk.getAttached(SlabAnchorAttachment.FROZEN_FLAT_TYPE),
                    "the attachment path must preserve all 2,048 markers exactly");
        } catch (IllegalArgumentException exception) {
            overflow = exception;
        } finally {
            // Fabric stores the new value before constructing its sync change, so always remove
            // the dense proof value even on the expected pre-fix exception.
            chunk.removeAttached(SlabAnchorAttachment.FROZEN_FLAT_TYPE);
        }

        if (overflow != null) {
            ctx.throwGameTestException(
                    "Issue #38 RED: 2,048 exact markers overflow the current frozen_flat sync: "
                            + overflow.getMessage());
            return;
        }
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void denseBuiltInChunkPacketRoundTripsBelowFabricLimit(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        int bottomY = world.getBottomY();
        int topYExclusive = bottomY + world.getHeight();
        LongOpenHashSet allBuiltInChunkPositions =
                new LongOpenHashSet(16 * 16 * world.getHeight());

        for (int y = bottomY; y < topYExclusive; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    allBuiltInChunkPositions.add(BlockPos.asLong(
                            (chunkX << 4) + x,
                            y,
                            (chunkZ << 4) + z));
                }
            }
        }

        RegistryByteBuf buf =
                new RegistryByteBuf(PacketByteBufs.create(), world.getRegistryManager());
        // AttachmentChange.create prefixes one boolean and then compares the Netty backing-array
        // capacity—not only the writer index—to Fabric's 32,502-byte ceiling.
        buf.writeBoolean(true);
        SlabAnchorAttachment.packetCodecForTesting().encode(buf, allBuiltInChunkPositions);
        int fabricMeasuredBytes = buf.array().length;
        ctx.assertTrue(
                fabricMeasuredBytes <= FABRIC_ATTACHMENT_MAX_DATA_BYTES,
                "dense built-in-height chunk sync uses " + fabricMeasuredBytes
                        + " bytes, over Fabric's " + FABRIC_ATTACHMENT_MAX_DATA_BYTES + "-byte limit");

        buf.readBoolean();
        LongOpenHashSet decoded = SlabAnchorAttachment.packetCodecForTesting().decode(buf);
        ctx.assertEquals(
                allBuiltInChunkPositions,
                decoded,
                "dense chunk packet round-trip must preserve every frozen-flat position");
        ctx.complete();
    }

    private static LongOpenHashSet sectionPrefix(
            int chunkX, int sectionY, int chunkZ, int count) {
        LongOpenHashSet positions = new LongOpenHashSet(count);
        for (int index = 0; index < count; index++) {
            int localX = index & 15;
            int localZ = (index >>> 4) & 15;
            int localY = (index >>> 8) & 15;
            positions.add(BlockPos.asLong(
                    (chunkX << 4) + localX,
                    (sectionY << 4) + localY,
                    (chunkZ << 4) + localZ));
        }
        return positions;
    }
}
