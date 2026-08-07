package com.slabbed.anchor;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Capacity suite for the two chunk attachments — the frozen-flat position SET and the
 * placement-dy position→height MAP — which are self-described twins: both persist per-chunk
 * facts and both must respect Fabric's attachment sync ceiling. Merged 2026-08-07 from
 * {@code FrozenFlatAttachmentCapacityTest} + {@code PlacementDyAttachmentCapacityTest}
 * (every test and helper preserved verbatim; the shared 32,502-byte ceiling constant deduped).
 *
 * <p>Original class docs follow, per section.
 */
public final class AttachmentCapacitySuite {

    // ═══ frozen-flat set (FrozenFlatAttachmentCapacityTest) ═══
    // Original doc:
    // /**
    //  * Regression proof for issue #38: dense {@code slabbed:frozen_flat} chunk data must remain
    //  * exact without exceeding Fabric's per-attachment synchronization ceiling.
    //  */
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

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void legacyRawLongPacketStillDecodesExactly(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        LongOpenHashSet expected =
                sectionPrefix(origin.getX() >> 4, origin.getY() >> 4, origin.getZ() >> 4, 513);
        RegistryByteBuf legacy =
                new RegistryByteBuf(PacketByteBufs.create(), world.getRegistryManager());

        legacy.writeVarInt(expected.size());
        for (long packed : expected) {
            legacy.writeLong(packed);
        }

        LongOpenHashSet decoded = SlabAnchorAttachment.packetCodecForTesting().decode(legacy);
        ctx.assertEquals(expected, decoded, "the compact codec must still read legacy raw-long packets");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sparseCompactPacketRoundTripsSectionEdges(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        LongOpenHashSet expected = new LongOpenHashSet();
        expected.add(BlockPos.asLong(-17, -65, -17));
        expected.add(BlockPos.asLong(-16, -64, -16));
        expected.add(BlockPos.asLong(-1, -1, -1));
        expected.add(BlockPos.asLong(0, 0, 0));
        expected.add(BlockPos.asLong(15, 15, 15));
        expected.add(BlockPos.asLong(16, 16, 16));

        RegistryByteBuf compact =
                new RegistryByteBuf(PacketByteBufs.create(), world.getRegistryManager());
        SlabAnchorAttachment.packetCodecForTesting().encode(compact, expected);
        LongOpenHashSet decoded = SlabAnchorAttachment.packetCodecForTesting().decode(compact);

        ctx.assertEquals(
                expected,
                decoded,
                "sparse compact packets must preserve negative coordinates and section edges");
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

    // ═══ placement-dy map (PlacementDyAttachmentCapacityTest) ═══
    // Original doc:
    // /**
    //  * Capacity characterization for {@code slabbed:placement_dy} — the direct twin of
    //  * {@link FrozenFlatAttachmentCapacityTest}, which exists because issue #38 ({@code 5817d264})
    //  * shipped a per-chunk position set that threw out of {@code setAttached} once a real chunk got
    //  * dense.
    //  *
    //  * <p>The placement-height store is strictly heavier than that set (it carries a value per
    //  * position), so its ceiling is LOWER and this file is not optional. It pins three things:
    //  *
    //  * <ol>
    //  *   <li>the realistic dense shape — a whole built-in-height chunk of cells placed on ONE lowered
    //  *       surface — still fits, because a uniform section costs a single palette byte;</li>
    //  *   <li>where the boundary actually sits for the expensive shape (dense AND mixed-height), stated
    //  *       as a number rather than a hope — RE-MEASURED at Stage 4 (2026-08-07), because the stored
    //  *       alphabet grew from two values to three and a mixed section's palette went from 1 bit to
    //  *       2 bits per entry;</li>
    //  *   <li>that crossing that boundary makes {@code record} decline the fact and leave the cell on its
    //  *       pre-existing live behaviour — it never throws, which is precisely the #38 failure.</li>
    //  * </ol>
    //  *
    //  * <p>The budget arithmetic: Fabric rejects a synchronized attachment whose Netty backing array
    //  * exceeds 32,502 bytes, and Netty rounds that array up to a power of two, so 16,384 is the largest
    //  * capacity that clears the ceiling and the real budget is a 16,383-byte write plus Fabric's own
    //  * one-byte prefix. That is the same arithmetic that made 2,047 raw longs fit and 2,048 fail.
    //  */

    /**
     * dy = -0.5, -1.0 and -2.0 in the stored sixteenths-of-a-block grid.
     *
     * <p>{@code DOUBLE_DOWN} is the cap {@code SlabSupport.DEEP_DY_ALPHABET} arms (Stage 4,
     * 2026-08-07). It needs NO format change — {@code -2.0} is 32 sixteenths and the signed-byte
     * range is {@code [-8.0, +7.9375]} — but it does grow the stored ALPHABET from two values to
     * three, which pushes a mixed section's palette from 1 bit per entry to 2. That is a capacity
     * change, and it is the reason the boundary row below had to be re-measured rather than
     * inherited.
     */
    private static final byte HALF_DOWN = -8;
    private static final byte FULL_DOWN = -16;
    private static final byte DOUBLE_DOWN = -32;

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
                + " bytes) over a " + STORED_ALPHABET.length + "-value alphabet, " + (fits + 1)
                + " does not";
        System.out.println("[STAGE4-CAPACITY] " + boundary);

        ctx.assertTrue(fits < totalPositions,
                "a fully dense MIXED chunk was expected to exceed the sync budget; if it now fits, "
                        + "this characterization is stale and the guard below is untested. "
                        + boundary);
        // RE-MEASURED 2026-08-07 (Stage 4) on the built-in Overworld, 98,304 positions per chunk
        // column. The alphabet grew from two stored values to three, so a mixed section spends 2
        // bits per occupied position where it used to spend 1, and the boundary moved:
        //
        //     2-value alphabet (1 bit/entry), measured 2026-08-06:  64,136 facts in 16,340 bytes
        //     3-value alphabet (2 bits/entry), measured 2026-08-07: 42,944-43,008 in ~16,380 bytes
        //
        // A 33% reduction — the staged plan's "dense-chunk headroom drops ~a third" was right — and
        // still 44% of a completely full chunk column, far past anything a real build reaches. Past
        // the boundary the store declines new facts rather than failing (the row below).
        //
        // THE BOUNDARY IS A RANGE, NOT A POINT, AND THAT IS WHY IT IS NOT PINNED EXACTLY. Each
        // section header carries three VarInts of chunk coordinate, and the gametest plot lands at
        // randomized world coordinates, so the same alphabet measures a few dozen facts either side
        // of the figure above from run to run. Observed across consecutive runs: 42,944 and 43,008.
        // An equality pin here would be flaky by construction; a floor is the honest gate.
        //
        // THE FLOOR IS DELIBERATELY KEPT AT 32,768. It was chosen as roughly half the ORIGINAL
        // measured boundary so that a codec change abandoning the packed palette — one byte per
        // position, which would drop the boundary to about 14,500 — trips this row while ordinary
        // drift does not. The re-measured figure still clears it, so the guard keeps exactly the
        // force it was given rather than being re-floated up to the new measurement.
        ctx.assertTrue(fits >= 32_768,
                "the placement-height store must hold at least 32,768 mixed-height facts per chunk "
                        + "(measured 42,944-43,008 over the 3-value alphabet when this row was "
                        + "re-measured; 64,136 over the 2-value one before it). " + boundary);
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
        // THE DEEPER CAP (Stage 4). Placed in the SAME section as the two 15/15/15-adjacent cells
        // above so the section carries a three-value palette and the encoder takes its 2-bit
        // packed-index path, not the 1-bit one every other section here exercises.
        expected.put(BlockPos.asLong(14, 15, 15), DOUBLE_DOWN);
        expected.put(BlockPos.asLong(-2, -1, -1), DOUBLE_DOWN);

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

    /**
     * {@code count} facts in the fill order above, cycling THE WHOLE STORED ALPHABET.
     *
     * <p>Two heights until 2026-08-07; three since Stage 4 armed {@code -2.0}. This is what makes
     * the boundary row expensive-shape-correct: three distinct values force
     * {@code ceil(log2(3)) -> 2} bits per occupied position instead of 1, so a dense mixed chunk
     * now spends twice the packed-index budget it used to and the boundary moves. Re-measured
     * rather than inherited.
     */
    private static final byte[] STORED_ALPHABET = {HALF_DOWN, FULL_DOWN, DOUBLE_DOWN};

    private static Long2ByteOpenHashMap mixedFacts(int chunkX, int chunkZ, int bottomY, int count) {
        Long2ByteOpenHashMap facts = new Long2ByteOpenHashMap(Math.max(1, count));
        for (int index = 0; index < count; index++) {
            facts.put(positionAt(chunkX, chunkZ, bottomY, index),
                    STORED_ALPHABET[index % STORED_ALPHABET.length]);
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
