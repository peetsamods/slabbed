package com.slabbed.anchor;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Capacity proof for the PLACEMENT_DY store — the sibling of
 * {@link FrozenFlatAttachmentCapacityTest}, which covers only the boolean marker sets.
 *
 * <p><b>Why this exists.</b> The marker sets got a compact, section-grouped codec and a regression
 * test after issue #38. The placement-height map — the store this line intends to switch ON as its
 * shipped default — never did: {@code DY_MAP_PACKET_CODEC} writes a raw eight-byte position
 * followed by a raw eight-byte double, sixteen bytes per stored height, with no grouping, no
 * quantisation, and no write guard. It is therefore the LESS efficient of the two attachments on
 * this line while being the one that carries the actual product feature.
 *
 * <p><b>The ceiling, restated from issue #38.</b> Fabric rejects a synchronized attachment whose
 * Netty backing array exceeds {@value #FABRIC_ATTACHMENT_MAX_DATA_BYTES} bytes, and Netty rounds
 * that array up to a power of two — so the largest capacity that clears the ceiling is 16,384, and
 * a write one byte past it already lands on 32,768 and fails. Fabric prefixes a boolean of its own.
 * At sixteen bytes per fact that budget is exhausted after roughly a thousand placed heights in a
 * single chunk.
 *
 * <p><b>Why a thousand is a player number, not a synthetic one.</b> A chunk is sixteen by sixteen,
 * so one fully-tiled layer of placed lowered blocks is 256 facts. Four layers — a modest terrace, a
 * staircase landing, a floor with three tiers above it — reach the boundary. This is not a stress
 * scenario; it is an afternoon of building in one chunk.
 */
public final class PlacementDyAttachmentCapacityTest {
    private static final int FABRIC_ATTACHMENT_MAX_DATA_BYTES = 32_502;

    /** One fully-tiled sixteen-by-sixteen layer of placed heights. */
    private static final int FACTS_PER_FULL_LAYER = 16 * 16;

    /** Four tiled layers: the modest, entirely ordinary build described above. */
    private static final int REALISTIC_DENSE_BUILD_FACTS = FACTS_PER_FULL_LAYER * 4;

    /**
     * The store must survive a realistic dense build without throwing out of {@code setAttached}.
     *
     * <p>RED before the compact-encoding port: the raw sixteen-bytes-per-fact wire form overflows
     * Fabric's ceiling and {@code setAttached} throws, which in a real session is a chunk that
     * refuses to synchronize.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void realisticDenseBuildDoesNotOverflowTheStore(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        WorldChunk chunk = world.getChunk(chunkX, chunkZ);

        Long2ByteOpenHashMap dense = denseFacts(chunkX, chunkZ, origin.getY(),
                REALISTIC_DENSE_BUILD_FACTS);

        RuntimeException overflow = null;
        try {
            chunk.setAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE, dense);
        } catch (RuntimeException exception) {
            overflow = exception;
        } finally {
            chunk.removeAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE);
        }

        ctx.assertTrue(overflow == null,
                "a " + REALISTIC_DENSE_BUILD_FACTS + "-fact chunk (four tiled layers) must not "
                        + "overflow the placement-dy sync, but setAttached threw: "
                        + (overflow != null ? overflow.getMessage() : ""));
        ctx.complete();
    }

    /**
     * Measures the wire cost directly, through the exact registered codec, so the boundary is
     * characterised as a number rather than inferred from a thrown exception.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void realisticDenseBuildEncodesBelowFabricLimit(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        Long2ByteOpenHashMap dense = denseFacts(origin.getX() >> 4, origin.getZ() >> 4,
                origin.getY(), REALISTIC_DENSE_BUILD_FACTS);

        RegistryByteBuf buf =
                new RegistryByteBuf(PacketByteBufs.create(), world.getRegistryManager());
        // Mirrors AttachmentChange.create: one boolean of Fabric's own, then the codec, then the
        // Netty backing-array capacity — not the writer index — is what Fabric compares.
        buf.writeBoolean(true);
        SlabAnchorAttachment.dyMapPacketCodecForTesting().encode(buf, dense);
        int fabricMeasuredBytes = buf.array().length;

        ctx.assertTrue(fabricMeasuredBytes <= FABRIC_ATTACHMENT_MAX_DATA_BYTES,
                REALISTIC_DENSE_BUILD_FACTS + " placed heights encode to " + fabricMeasuredBytes
                        + " bytes, over Fabric's " + FABRIC_ATTACHMENT_MAX_DATA_BYTES
                        + "-byte attachment ceiling");
        ctx.complete();
    }

    /** Whatever the wire form becomes, it must return every stored height byte-for-byte. */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void denseFactsRoundTripExactly(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        Long2ByteOpenHashMap dense = denseFacts(origin.getX() >> 4, origin.getZ() >> 4,
                origin.getY(), FACTS_PER_FULL_LAYER);

        RegistryByteBuf buf =
                new RegistryByteBuf(PacketByteBufs.create(), world.getRegistryManager());
        SlabAnchorAttachment.dyMapPacketCodecForTesting().encode(buf, dense);
        Long2ByteOpenHashMap decoded =
                SlabAnchorAttachment.dyMapPacketCodecForTesting().decode(buf);

        ctx.assertTrue(dense.size() == decoded.size(),
                "round-trip changed the fact count: " + dense.size() + " -> " + decoded.size());
        for (var entry : dense.long2ByteEntrySet()) {
            byte back = decoded.get(entry.getLongKey());
            ctx.assertTrue(back == entry.getByteValue(),
                    "round-trip changed a stored height at " + entry.getLongKey()
                            + ": " + entry.getByteValue() + " -> " + back);
        }
        ctx.complete();
    }

    /**
     * Characterises where the boundary now sits, and reports it, rather than only asserting that
     * some chosen density fits. The donor line's equivalent row exists for the same reason: a
     * capacity gate that only says "fits" tells you nothing on the day it stops fitting.
     *
     * <p>Measured at two heights per section, which is the realistic mixed case — a build sitting
     * on one lowered surface with a second height somewhere in the same section. A uniform section
     * is cheaper still; a pathological many-height section degrades to about a byte per entry
     * rather than failing, because the palette widens instead of the format breaking.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void reportsWhereTheSynchronizationBoundaryNowSits(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;

        int lastFitting = 0;
        int lastBytes = 0;
        // Step in whole layers; the exact per-fact boundary is not the interesting number, the
        // order of magnitude a player could reach is.
        for (int layers = 1; layers <= 384 / 16 * 16; layers++) {
            int facts = FACTS_PER_FULL_LAYER * layers;
            Long2ByteOpenHashMap dense = denseFacts(chunkX, chunkZ, origin.getY(), facts);
            RegistryByteBuf buf =
                    new RegistryByteBuf(PacketByteBufs.create(), world.getRegistryManager());
            buf.writeBoolean(true);
            SlabAnchorAttachment.dyMapPacketCodecForTesting().encode(buf, dense);
            int bytes = buf.array().length;
            if (bytes > FABRIC_ATTACHMENT_MAX_DATA_BYTES) {
                break;
            }
            lastFitting = facts;
            lastBytes = bytes;
        }

        ctx.assertTrue(lastFitting >= REALISTIC_DENSE_BUILD_FACTS,
                "the store should hold at least the realistic dense build ("
                        + REALISTIC_DENSE_BUILD_FACTS + " facts) but tops out at " + lastFitting);
        com.slabbed.Slabbed.LOGGER.info(
                "[PLACEMENT_DY] capacity: {} placed heights ({} tiled layers) encode to {} bytes, "
                        + "inside Fabric's {}-byte ceiling; the previous raw-double wire form "
                        + "overflowed at {} facts.",
                lastFitting, lastFitting / FACTS_PER_FULL_LAYER, lastBytes,
                FABRIC_ATTACHMENT_MAX_DATA_BYTES, REALISTIC_DENSE_BUILD_FACTS);
        ctx.complete();
    }

    /**
     * Builds {@code count} placed heights tiled across the chunk, layer by layer, using the two
     * half-step values a real placement actually produces.
     */
    private static Long2ByteOpenHashMap denseFacts(int chunkX, int chunkZ, int baseY, int count) {
        Long2ByteOpenHashMap facts = new Long2ByteOpenHashMap();
        int written = 0;
        for (int layer = 0; written < count; layer++) {
            for (int z = 0; z < 16 && written < count; z++) {
                for (int x = 0; x < 16 && written < count; x++) {
                    long packed = BlockPos.asLong((chunkX << 4) + x, baseY + layer, (chunkZ << 4) + z);
                    // -0.5 and -1.0 as sixteenths: the two values a real placement produces most.
                    facts.put(packed, (byte) ((written % 2 == 0) ? -8 : -16));
                    written++;
                }
            }
        }
        return facts;
    }
}
