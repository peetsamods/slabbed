package com.slabbed.anchor;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Capacity characterization for {@code slabbed:placement_dy} — the direct twin of
 * {@link FrozenFlatAttachmentCapacityTest}, which exists because issue #38 ({@code 5817d264})
 * shipped a per-chunk position set that threw out of {@code setAttached} once a real chunk got
 * dense.
 *
 * <p>The placement-height store is strictly heavier than that set (it carries a value per
 * position), so its ceiling is LOWER and this file is not optional. It pins three things:
 *
 * <ol>
 *   <li>the realistic dense shape — a whole built-in-height chunk of cells placed on ONE lowered
 *       surface — still fits, because a uniform section costs a single palette byte;</li>
 *   <li>where the boundary actually sits for the expensive shape (dense AND mixed-height), stated
 *       as a number rather than a hope;</li>
 *   <li>that crossing that boundary makes {@code record} decline the fact and leave the cell on its
 *       pre-existing live behaviour — it never throws, which is precisely the #38 failure.</li>
 * </ol>
 *
 * <p>The budget arithmetic: Fabric rejects a synchronized attachment whose Netty backing array
 * exceeds 32,502 bytes, and Netty rounds that array up to a power of two, so 16,384 is the largest
 * capacity that clears the ceiling and the real budget is a 16,383-byte write plus Fabric's own
 * one-byte prefix. That is the same arithmetic that made 2,047 raw longs fit and 2,048 fail.
 */
public final class PlacementDyAttachmentCapacityTest {
    private static final int FABRIC_ATTACHMENT_MAX_DATA_BYTES = 32_502;

    /** dy = -0.5 and dy = -1.0 in the stored sixteenths-of-a-block grid. */
    private static final byte HALF_DOWN = -8;
    private static final byte FULL_DOWN = -16;

    /**
     * The realistic dense shape: every cell of a full built-in-height chunk carrying the SAME
     * placement height. This is a whole chunk built on one lowered surface, and it must fit —
     * a uniform section spends one palette byte for all 4,096 of its positions.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void denseUniformBuiltInChunkPacketRoundTripsBelowFabricLimit(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        Long2ByteOpenHashMap facts = new Long2ByteOpenHashMap();
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        int bottomY = world.getBottomY();
        int topYExclusive = bottomY + world.getHeight();
        for (int y = bottomY; y < topYExclusive; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    facts.put(BlockPos.asLong((chunkX << 4) + x, y, (chunkZ << 4) + z), HALF_DOWN);
                }
            }
        }

        RegistryByteBuf buf = new RegistryByteBuf(PacketByteBufs.create(), world.getRegistryManager());
        // AttachmentChange.create prefixes one boolean and then compares the Netty backing-array
        // capacity — not only the writer index — to Fabric's ceiling.
        buf.writeBoolean(true);
        SlabPlacementDyAttachment.packetCodecForTesting().encode(buf, facts);
        int fabricMeasuredBytes = buf.array().length;
        ctx.assertTrue(
                fabricMeasuredBytes <= FABRIC_ATTACHMENT_MAX_DATA_BYTES,
                "a uniform-height dense chunk (" + facts.size() + " facts) syncs in "
                        + fabricMeasuredBytes + " bytes, over Fabric's "
                        + FABRIC_ATTACHMENT_MAX_DATA_BYTES + "-byte limit");

        buf.readBoolean();
        Long2ByteOpenHashMap decoded = SlabPlacementDyAttachment.packetCodecForTesting().decode(buf);
        ctx.assertEquals(facts, decoded,
                "dense uniform chunk packet round-trip must preserve every placement height");
        ctx.complete();
    }

    /**
     * The size prediction the write guard consults must be the size the encoder actually writes.
     * If these two ever drift the guard is measuring a fiction, and #38 comes back.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sizePredictionMatchesTheRealEncoderExactly(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;

        for (int paletteSize : new int[] {1, 2, 3, 5, 17}) {
            Long2ByteOpenHashMap facts = new Long2ByteOpenHashMap();
            for (int index = 0; index < 700; index++) {
                facts.put(positionAt(chunkX, chunkZ, world.getBottomY(), index),
                        (byte) -(index % paletteSize));
            }
            RegistryByteBuf buf =
                    new RegistryByteBuf(PacketByteBufs.create(), world.getRegistryManager());
            SlabPlacementDyAttachment.packetCodecForTesting().encode(buf, facts);
            int written = buf.writerIndex();
            int predicted = SlabPlacementDyAttachment.encodedByteLengthForTesting(facts);
            ctx.assertTrue(written == predicted,
                    "palette " + paletteSize + ": the guard predicted " + predicted
                            + " bytes but the codec wrote " + written);
            Long2ByteOpenHashMap decoded =
                    SlabPlacementDyAttachment.packetCodecForTesting().decode(buf);
            ctx.assertEquals(facts, decoded,
                    "palette " + paletteSize + " must round-trip exactly");
        }
        ctx.complete();
    }

    /**
     * WHERE THE BOUNDARY SITS for the expensive shape: dense AND mixed-height, which spends a
     * packed index per position on top of the occupancy bitmap. Reports the exact fact count at
     * which one chunk stops fitting, and fails only if that count drops below a floor a real build
     * could plausibly reach — so this row is a characterization that also guards a regression.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void mixedHeightDenseChunkBoundaryIsCharacterized(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        int bottomY = world.getBottomY();
        int totalPositions = 16 * 16 * world.getHeight();

        int fits = largestMixedFactCountThatFits(chunkX, chunkZ, bottomY, totalPositions);
        String boundary = "mixed-height boundary: " + fits + " facts fit in one chunk ("
                + SlabPlacementDyAttachment.encodedByteLengthForTesting(
                        mixedFacts(chunkX, chunkZ, bottomY, fits))
                + " bytes), " + (fits + 1) + " does not";

        ctx.assertTrue(fits < totalPositions,
                "a fully dense MIXED chunk was expected to exceed the sync budget; if it now fits, "
                        + "this characterization is stale and the guard below is untested. "
                        + boundary);
        // MEASURED 2026-08-06 on the built-in Overworld (98,304 positions per chunk column):
        // 64,136 two-height facts fit in 16,340 bytes, so two thirds of a fully dense chunk is
        // already covered. The floor below is deliberately half of that: a codec change that gave
        // up the packed palette and spent a byte per position would drop the boundary to roughly
        // 14,500 and this row would catch it, while ordinary drift will not trip it.
        ctx.assertTrue(fits >= 32_768,
                "the placement-height store must hold at least 32,768 mixed-height facts per chunk "
                        + "(measured 64,136 when this row was written). " + boundary);
        ctx.complete();
    }

    /**
     * THE #38 LESSON, APPLIED BEFORE THE FACT. A chunk already at the sync budget must make
     * {@code record} decline quietly — the cell keeps its live behaviour — instead of throwing out
     * of {@code setAttached} the way the dense frozen-flat set did.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void recordDeclinesAtTheBudgetInsteadOfThrowing(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        int bottomY = world.getBottomY();
        int totalPositions = 16 * 16 * world.getHeight();
        WorldChunk chunk = world.getChunk(chunkX, chunkZ);

        int fits = largestMixedFactCountThatFits(chunkX, chunkZ, bottomY, totalPositions);
        Long2ByteOpenHashMap saturated = mixedFacts(chunkX, chunkZ, bottomY, fits);
        BlockPos overflowPos = BlockPos.fromLong(positionAt(chunkX, chunkZ, bottomY, fits));

        try {
            chunk.setAttached(SlabPlacementDyAttachment.PLACEMENT_DY_TYPE, saturated);
            ctx.assertEquals(saturated,
                    chunk.getAttached(SlabPlacementDyAttachment.PLACEMENT_DY_TYPE),
                    "a chunk exactly at the sync budget must survive the attachment path intact");

            boolean recorded = SlabPlacementDyAttachment.record(world, overflowPos, -0.5);
            ctx.assertTrue(!recorded,
                    "record must decline the fact that would cross the sync budget");
            ctx.assertTrue(
                    Double.isNaN(SlabPlacementDyAttachment.storedDy(world, overflowPos)),
                    "the declined cell must have no stored height, so it keeps its live behaviour");
            Long2ByteOpenHashMap after =
                    chunk.getAttached(SlabPlacementDyAttachment.PLACEMENT_DY_TYPE);
            ctx.assertTrue(after != null && after.size() == fits,
                    "declining must leave the existing facts untouched");
        } finally {
            // Fabric stores the value before building its sync change, so always drop the dense
            // proof value — even on an unexpected throw.
            chunk.removeAttached(SlabPlacementDyAttachment.PLACEMENT_DY_TYPE);
        }
        ctx.complete();
    }

    /**
     * Exactness across the shapes the section encoder actually has to survive: negative
     * coordinates, section edges in all three axes, and every height the line can produce.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sparsePacketRoundTripsSectionEdgesAndEveryCanonicalHeight(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        Long2ByteOpenHashMap expected = new Long2ByteOpenHashMap();
        expected.put(BlockPos.asLong(-17, -65, -17), FULL_DOWN);
        expected.put(BlockPos.asLong(-16, -64, -16), HALF_DOWN);
        expected.put(BlockPos.asLong(-1, -1, -1), (byte) 0);
        expected.put(BlockPos.asLong(0, 0, 0), HALF_DOWN);
        expected.put(BlockPos.asLong(15, 15, 15), FULL_DOWN);
        expected.put(BlockPos.asLong(16, 16, 16), (byte) 0);

        RegistryByteBuf buf = new RegistryByteBuf(PacketByteBufs.create(), world.getRegistryManager());
        SlabPlacementDyAttachment.packetCodecForTesting().encode(buf, expected);
        Long2ByteOpenHashMap decoded = SlabPlacementDyAttachment.packetCodecForTesting().decode(buf);

        ctx.assertEquals(expected, decoded,
                "sparse packets must preserve negative coordinates, section edges and every height");
        ctx.complete();
    }

    // ── helpers ───────────────────────────────────────────────────────

    /** Deterministic dense fill order: x fastest, then z, then y, starting at the chunk bottom. */
    private static long positionAt(int chunkX, int chunkZ, int bottomY, int index) {
        return BlockPos.asLong(
                (chunkX << 4) + (index & 15),
                bottomY + (index >>> 8),
                (chunkZ << 4) + ((index >>> 4) & 15));
    }

    /** {@code count} facts in the fill order above, alternating between two heights. */
    private static Long2ByteOpenHashMap mixedFacts(int chunkX, int chunkZ, int bottomY, int count) {
        Long2ByteOpenHashMap facts = new Long2ByteOpenHashMap(Math.max(1, count));
        for (int index = 0; index < count; index++) {
            facts.put(positionAt(chunkX, chunkZ, bottomY, index),
                    (index & 1) == 0 ? HALF_DOWN : FULL_DOWN);
        }
        return facts;
    }

    private static int largestMixedFactCountThatFits(
            int chunkX, int chunkZ, int bottomY, int totalPositions) {
        int low = 0;
        int high = totalPositions;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            int size = SlabPlacementDyAttachment.encodedByteLengthForTesting(
                    mixedFacts(chunkX, chunkZ, bottomY, mid));
            if (size <= SlabPlacementDyAttachment.SYNC_SAFE_ENCODED_BYTES) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }
}
