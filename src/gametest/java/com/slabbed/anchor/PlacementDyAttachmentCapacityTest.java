package com.slabbed.anchor;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import java.lang.reflect.Field;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Regression proof for the placement-dy half of the GH #36 family: dense {@code slabbed:placement_dy}
 * chunk data must round-trip bit-exactly without exceeding Fabric's per-attachment synchronization
 * ceiling. The sibling of {@link FrozenFlatAttachmentCapacityTest}, which pinned the SET half; the
 * MAP half shipped unfixed at sixteen bytes per entry and died at ~2,030 stored placements — a 16x16
 * footprint with eight placement layers is 2,048, a reachable build.
 *
 * <p>MUTATION that must redden the capacity rows alone: revert {@code DY_MAP_PACKET_CODEC} to the
 * naive count-plus-(long,double) form.
 */
public final class PlacementDyAttachmentCapacityTest {
    /** 1 presence byte + 2 varint bytes + 16 x 2,032 = 32,515 > 32,502; 2,031 fits at 32,499. */
    private static final int FIRST_LEGACY_COUNT_THAT_OVERFLOWS = 2_032;
    private static final int FABRIC_ATTACHMENT_MAX_DATA_BYTES = 32_502;
    private static final int COMPACT_FORMAT_MARKER = -1;
    private static final int MAX_SECTION_GROUPS = 4_096;

    /** The half-step depths a real build actually mints — the realistic palette. */
    private static final double[] REALISTIC_DYS = {-0.5, -1.0, -1.5, -2.0};

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void firstLegacyOverflowCountNowFitsAndRoundTripsExactly(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        Long2DoubleOpenHashMap map = newDyMap();
        int placed = 0;
        // Fill bottom-up through the chunk column, cycling the realistic palette per entry so the
        // encoder cannot take the uniform shortcut anywhere — this measures the PER-CELL mode.
        outer:
        for (int y = helper.getLevel().getMinY(); y < helper.getLevel().getMinY() + helper.getLevel().getHeight(); y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (placed >= FIRST_LEGACY_COUNT_THAT_OVERFLOWS) {
                        break outer;
                    }
                    map.put(
                            BlockPos.asLong(((origin.getX() >> 4) << 4) + x, y, ((origin.getZ() >> 4) << 4) + z),
                            REALISTIC_DYS[placed % REALISTIC_DYS.length]);
                    placed++;
                }
            }
        }

        RegistryFriendlyByteBuf buffer = newBuffer(helper);
        buffer.writeBoolean(true);
        productionPacketCodec().encode(buffer, map);
        int attachmentBytes = buffer.readableBytes();
        if (attachmentBytes > FABRIC_ATTACHMENT_MAX_DATA_BYTES) {
            throw helper.assertionException(
                    "production DY_MAP_PACKET_CODEC encoded " + map.size()
                            + " placements into " + attachmentBytes
                            + " readable attachment bytes, over Fabric's "
                            + FABRIC_ATTACHMENT_MAX_DATA_BYTES + "-byte attachment limit — the "
                            + "first count the LEGACY format could not carry must fit compactly");
        }
        if (!buffer.readBoolean()) {
            throw helper.assertionException("attachment presence prefix was not preserved");
        }
        assertExactMap(helper, map, productionPacketCodec().decode(buffer), "legacy-overflow-count round-trip");
        System.out.println(
                "ISSUE36_DY_CAPACITY selector=legacy_overflow placements=" + map.size()
                        + " readableAttachmentBytes=" + attachmentBytes
                        + " limit=" + FABRIC_ATTACHMENT_MAX_DATA_BYTES);
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void denseUniformFullBuiltInHeightFitsAndRoundTripsExactly(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        int minY = helper.getLevel().getMinY();
        int height = helper.getLevel().getHeight();
        Long2DoubleOpenHashMap map = newDyMap();
        for (int y = minY; y < minY + height; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    map.put(BlockPos.asLong((chunkX << 4) + x, y, (chunkZ << 4) + z), -0.5d);
                }
            }
        }

        RegistryFriendlyByteBuf buffer = newBuffer(helper);
        buffer.writeBoolean(true);
        productionPacketCodec().encode(buffer, map);
        int attachmentBytes = buffer.readableBytes();
        if (attachmentBytes > FABRIC_ATTACHMENT_MAX_DATA_BYTES) {
            throw helper.assertionException(
                    "dense uniform full-height placement-dy map (" + map.size()
                            + " cells) encoded to " + attachmentBytes
                            + " bytes, over the " + FABRIC_ATTACHMENT_MAX_DATA_BYTES
                            + "-byte limit — the UNIFORM section mode must keep a flattened "
                            + "terrace under the ceiling regardless of cell count");
        }
        if (!buffer.readBoolean()) {
            throw helper.assertionException("attachment presence prefix was not preserved");
        }
        assertExactMap(helper, map, productionPacketCodec().decode(buffer), "dense-uniform round-trip");
        System.out.println(
                "ISSUE36_DY_CAPACITY selector=dense_uniform placements=" + map.size()
                        + " readableAttachmentBytes=" + attachmentBytes
                        + " limit=" + FABRIC_ATTACHMENT_MAX_DATA_BYTES
                        + " minY=" + minY + " height=" + height);
        helper.succeed();
    }

    /**
     * LAW 1 says every read returns the stored value verbatim, so the wire must be BIT-transparent:
     * 0.0 and -0.0 stay distinct, and the decoded map keeps the NaN default for absent keys.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void valuesRoundTripBitExactlyIncludingSignedZero(GameTestHelper helper) {
        Long2DoubleOpenHashMap map = newDyMap();
        map.put(BlockPos.asLong(0, 0, 0), 0.0d);
        map.put(BlockPos.asLong(1, 0, 0), -0.0d);
        map.put(BlockPos.asLong(-17, -64, -17), -0.5d);
        map.put(BlockPos.asLong(15, 15, 15), -3.0d);
        map.put(BlockPos.asLong(16, 16, 16), -2.5d);

        RegistryFriendlyByteBuf buffer = newBuffer(helper);
        productionPacketCodec().encode(buffer, map);
        Long2DoubleOpenHashMap decoded = productionPacketCodec().decode(buffer);
        assertExactMap(helper, map, decoded, "bit-exact multi-value round-trip");
        if (!Double.isNaN(decoded.get(BlockPos.asLong(9, 9, 9)))) {
            throw helper.assertionException(
                    "decoded map must answer NaN for an absent key — the absent-vs-zero distinction "
                    + "is load-bearing (air is positive evidence in this resolver)");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void legacyFormatDecodesAndMalformedPayloadsAreRejected(GameTestHelper helper) {
        Long2DoubleOpenHashMap expected = newDyMap();
        for (int i = 0; i < 513; i++) {
            expected.put(BlockPos.asLong(i & 15, i >> 4, 7), REALISTIC_DYS[i % REALISTIC_DYS.length]);
        }
        RegistryFriendlyByteBuf legacy = newBuffer(helper);
        legacy.writeVarInt(expected.size());
        for (var e : expected.long2DoubleEntrySet()) {
            legacy.writeLong(e.getLongKey());
            legacy.writeDouble(e.getDoubleValue());
        }
        assertExactMap(helper, expected, productionPacketCodec().decode(legacy), "legacy count-plus-pairs decode");

        assertDecodeRejected(helper, "unknown compact format marker", buffer -> buffer.writeVarInt(-2));
        assertDecodeRejected(helper, "negative section count", buffer -> {
            buffer.writeVarInt(COMPACT_FORMAT_MARKER);
            buffer.writeVarInt(0);
            buffer.writeVarInt(-1);
        });
        assertDecodeRejected(helper, "impossible section count", buffer -> {
            buffer.writeVarInt(COMPACT_FORMAT_MARKER);
            buffer.writeVarInt(0);
            buffer.writeVarInt(MAX_SECTION_GROUPS + 1);
        });
        assertDecodeRejected(helper, "palette index outside palette", buffer -> {
            buffer.writeVarInt(COMPACT_FORMAT_MARKER);
            buffer.writeVarInt(1);
            buffer.writeLong(Double.doubleToRawLongBits(-0.5d));
            buffer.writeVarInt(1);
            buffer.writeVarInt(0);
            buffer.writeVarInt(0);
            buffer.writeVarInt(0);
            buffer.writeLong(1L);
            buffer.writeLong(1L);
            buffer.writeByte(0);
            buffer.writeVarInt(7);
        });
        assertDecodeRejected(helper, "legacy count larger than payload", buffer -> {
            buffer.writeVarInt(2);
            buffer.writeLong(BlockPos.asLong(0, 0, 0));
            buffer.writeDouble(-0.5d);
        });
        helper.succeed();
    }

    /**
     * The {@code /slabdy chunk} gauge measures a REAL chunk with the REAL codec: the entries count
     * matches what was written, the bytes line reports the production encoding, and the band words
     * cover all four thresholds. The railing for the cliff this class pins.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chunkGaugeReportsMeasuredEntriesAndBands(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(2, 2, 2));
        var chunk = level.getChunkAt(base);
        for (int i = 0; i < 5; i++) {
            SlabAnchorAttachment.writePlacementDy(level, base.offset(i, 0, 0), -0.5d);
        }

        java.util.List<String> lines = ChunkPlacementGauge.report(chunk, level.registryAccess());
        String headline = lines.isEmpty() ? "" : lines.get(0);
        if (!headline.contains("placement heights: 5 entries") || !headline.contains("GREEN")) {
            throw helper.assertionException(
                    "gauge headline must report the 5 written entries and the GREEN band, got: " + headline);
        }
        boolean namedBlock = lines.stream().anyMatch(l -> l.contains("heights by block"));
        if (!namedBlock) {
            throw helper.assertionException(
                    "gauge must break stored heights down by block type, got: " + lines);
        }

        if (!"GREEN".equals(ChunkPlacementGauge.band(0))
                || !"YELLOW".equals(ChunkPlacementGauge.band((int) (ChunkPlacementGauge.FABRIC_ATTACHMENT_MAX_DATA_BYTES * 0.6)))
                || !"ORANGE".equals(ChunkPlacementGauge.band((int) (ChunkPlacementGauge.FABRIC_ATTACHMENT_MAX_DATA_BYTES * 0.8)))
                || !"RED".equals(ChunkPlacementGauge.band((int) (ChunkPlacementGauge.FABRIC_ATTACHMENT_MAX_DATA_BYTES * 0.95)))) {
            throw helper.assertionException("band thresholds must be GREEN<50 YELLOW<75 ORANGE<90 RED>=90");
        }

        // Leave the chunk as found — the store is per-chunk state shared with later tests.
        // removeAnchor is the production removal path and clears the dy entry with it.
        for (int i = 0; i < 5; i++) {
            SlabAnchorAttachment.removeAnchor(level, base.offset(i, 0, 0));
        }
        helper.succeed();
    }

    private static void assertExactMap(
            GameTestHelper helper, Long2DoubleOpenHashMap expected, Long2DoubleOpenHashMap actual, String what) {
        if (actual.size() != expected.size()) {
            throw helper.assertionException(
                    what + ": size " + actual.size() + " != expected " + expected.size());
        }
        for (var e : expected.long2DoubleEntrySet()) {
            double got = actual.get(e.getLongKey());
            if (Double.doubleToRawLongBits(got) != Double.doubleToRawLongBits(e.getDoubleValue())) {
                throw helper.assertionException(
                        what + ": value at " + BlockPos.of(e.getLongKey()) + " decoded to " + got
                                + " (raw " + Double.doubleToRawLongBits(got) + "), expected "
                                + e.getDoubleValue() + " (raw "
                                + Double.doubleToRawLongBits(e.getDoubleValue())
                                + ") — the wire format must be BIT-transparent");
            }
        }
    }

    private static void assertDecodeRejected(
            GameTestHelper helper, String what, Consumer<RegistryFriendlyByteBuf> author) {
        RegistryFriendlyByteBuf buffer = newBuffer(helper);
        author.accept(buffer);
        try {
            productionPacketCodec().decode(buffer);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw helper.assertionException(what + " must be rejected with IllegalArgumentException");
    }

    private static Long2DoubleOpenHashMap newDyMap() {
        Long2DoubleOpenHashMap map = new Long2DoubleOpenHashMap();
        map.defaultReturnValue(Double.NaN);
        return map;
    }

    @SuppressWarnings("unchecked")
    private static StreamCodec<RegistryFriendlyByteBuf, Long2DoubleOpenHashMap> productionPacketCodec() {
        try {
            Field field = SlabAnchorAttachment.class.getDeclaredField("DY_MAP_PACKET_CODEC");
            field.setAccessible(true);
            return (StreamCodec<RegistryFriendlyByteBuf, Long2DoubleOpenHashMap>) field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "GH #36 dy-map harness cannot access production DY_MAP_PACKET_CODEC", exception);
        }
    }

    private static RegistryFriendlyByteBuf newBuffer(GameTestHelper helper) {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
    }
}
