package com.slabbed.anchor;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.lang.reflect.Field;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Regression proof for issue #38: dense {@code slabbed:frozen_flat} chunk data must remain
 * exact without exceeding Fabric's per-attachment synchronization ceiling.
 */
public final class FrozenFlatAttachmentCapacityTest {
    private static final int FIRST_RAW_LONG_COUNT_THAT_OVERFLOWS = 4_063;
    private static final int FABRIC_ATTACHMENT_MAX_DATA_BYTES = 32_502;
    private static final int COMPACT_FORMAT_MARKER = -1;
    private static final int MAX_SECTION_GROUPS = 4_096;

    /**
     * The method name is frozen with the original RED selector. Its assertion uses Fabric's
     * complete readable attachment bytes, including the one-byte attachment-presence prefix.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void denseSingleChunkPacketExceedsFabricCapacity(GameTestHelper helper) {
        LongOpenHashSet positions = sectionPrefix(
                helper.absolutePos(BlockPos.ZERO).getX() >> 4,
                helper.absolutePos(BlockPos.ZERO).getY() >> 4,
                helper.absolutePos(BlockPos.ZERO).getZ() >> 4,
                FIRST_RAW_LONG_COUNT_THAT_OVERFLOWS);
        RegistryFriendlyByteBuf buffer = newBuffer(helper);
        buffer.writeBoolean(true);

        productionPacketCodec().encode(buffer, positions);

        int attachmentBytes = buffer.readableBytes();
        if (attachmentBytes > FABRIC_ATTACHMENT_MAX_DATA_BYTES) {
            throw helper.assertionException(
                    "production frozen_flat PACKET_CODEC encoded "
                            + positions.size() + " markers into " + attachmentBytes
                            + " readable attachment bytes, over Fabric's "
                            + FABRIC_ATTACHMENT_MAX_DATA_BYTES + "-byte attachment limit");
        }
        if (!buffer.readBoolean()) {
            throw helper.assertionException("attachment presence prefix was not preserved");
        }
        LongOpenHashSet decoded = productionPacketCodec().decode(buffer);
        assertExactSet(helper, positions, decoded, "4,063-marker compact round-trip");
        System.out.println(
                "ISSUE38_CAPACITY selector=frozen_red markers=" + positions.size()
                        + " readableAttachmentBytes=" + attachmentBytes
                        + " limit=" + FABRIC_ATTACHMENT_MAX_DATA_BYTES);
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void denseBuiltInHeightCapacityAndExactCompactRoundTrip(GameTestHelper helper) {
        int chunkX = helper.absolutePos(BlockPos.ZERO).getX() >> 4;
        int chunkZ = helper.absolutePos(BlockPos.ZERO).getZ() >> 4;
        int minY = helper.getLevel().getMinY();
        int height = helper.getLevel().getHeight();
        LongOpenHashSet positions = new LongOpenHashSet(16 * 16 * height);
        for (int y = minY; y < minY + height; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    positions.add(BlockPos.asLong(
                            (chunkX << 4) + x,
                            y,
                            (chunkZ << 4) + z));
                }
            }
        }

        RegistryFriendlyByteBuf buffer = newBuffer(helper);
        buffer.writeBoolean(true);
        productionPacketCodec().encode(buffer, positions);
        int attachmentBytes = buffer.readableBytes();
        int compactPayloadBytes = attachmentBytes - 1;
        if (attachmentBytes > FABRIC_ATTACHMENT_MAX_DATA_BYTES) {
            throw helper.assertionException(
                    "dense built-in-height packet used " + attachmentBytes
                            + " readable attachment bytes, over Fabric's "
                            + FABRIC_ATTACHMENT_MAX_DATA_BYTES + "-byte attachment limit");
        }

        if (!buffer.readBoolean()) {
            throw helper.assertionException("attachment presence prefix was not preserved");
        }
        int marker = buffer.readVarInt();
        if (marker != COMPACT_FORMAT_MARKER) {
            throw helper.assertionException(
                    "production codec emitted format marker " + marker
                            + " instead of compact marker " + COMPACT_FORMAT_MARKER);
        }
        buffer.readerIndex(1);
        LongOpenHashSet decoded = productionPacketCodec().decode(buffer);
        assertExactSet(helper, positions, decoded, "dense built-in-height compact round-trip");
        System.out.println(
                "ISSUE38_CAPACITY selector=dense_built_in markers=" + positions.size()
                        + " readableAttachmentBytes=" + attachmentBytes
                        + " compactPayloadBytes=" + compactPayloadBytes
                        + " limit=" + FABRIC_ATTACHMENT_MAX_DATA_BYTES
                        + " minY=" + minY
                        + " height=" + height);
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void legacyCountAndRawLongPacketDecodesExactly(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        LongOpenHashSet expected =
                sectionPrefix(origin.getX() >> 4, origin.getY() >> 4, origin.getZ() >> 4, 513);
        RegistryFriendlyByteBuf legacy = newBuffer(helper);
        legacy.writeVarInt(expected.size());
        for (long packed : expected) {
            legacy.writeLong(packed);
        }

        LongOpenHashSet decoded = productionPacketCodec().decode(legacy);
        assertExactSet(helper, expected, decoded, "legacy count-plus-raw-long decode");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sparseNegativeCoordinatesAndSectionEdgesRoundTrip(GameTestHelper helper) {
        LongOpenHashSet expected = new LongOpenHashSet();
        expected.add(BlockPos.asLong(-17, -65, -17));
        expected.add(BlockPos.asLong(-16, -64, -16));
        expected.add(BlockPos.asLong(-1, -1, -1));
        expected.add(BlockPos.asLong(0, 0, 0));
        expected.add(BlockPos.asLong(15, 15, 15));
        expected.add(BlockPos.asLong(16, 16, 16));

        RegistryFriendlyByteBuf compact = newBuffer(helper);
        productionPacketCodec().encode(compact, expected);
        LongOpenHashSet decoded = productionPacketCodec().decode(compact);
        assertExactSet(helper, expected, decoded, "negative-coordinate and section-edge round-trip");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void malformedFormatsCountsAndLegacyPayloadsAreRejected(GameTestHelper helper) {
        assertDecodeRejected(helper, "unknown compact format marker", buffer -> buffer.writeVarInt(-2));
        assertDecodeRejected(helper, "negative compact section count", buffer -> {
            buffer.writeVarInt(COMPACT_FORMAT_MARKER);
            buffer.writeVarInt(-1);
        });
        assertDecodeRejected(helper, "impossible compact section count", buffer -> {
            buffer.writeVarInt(COMPACT_FORMAT_MARKER);
            buffer.writeVarInt(MAX_SECTION_GROUPS + 1);
        });
        assertDecodeRejected(helper, "legacy count larger than payload", buffer -> {
            buffer.writeVarInt(2);
            buffer.writeLong(BlockPos.asLong(0, 0, 0));
        });
        helper.succeed();
    }

    @SuppressWarnings("unchecked")
    private static StreamCodec<RegistryFriendlyByteBuf, LongOpenHashSet> productionPacketCodec() {
        try {
            Field field = SlabAnchorAttachment.class.getDeclaredField("PACKET_CODEC");
            field.setAccessible(true);
            return (StreamCodec<RegistryFriendlyByteBuf, LongOpenHashSet>) field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Issue #38 harness cannot access production PACKET_CODEC", exception);
        }
    }

    private static RegistryFriendlyByteBuf newBuffer(GameTestHelper helper) {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
    }

    private static void assertExactSet(
            GameTestHelper helper,
            LongOpenHashSet expected,
            LongOpenHashSet actual,
            String label) {
        if (!expected.equals(actual)) {
            throw helper.assertionException(
                    label + " changed positions: expected " + expected.size()
                            + " markers, decoded " + actual.size());
        }
    }

    private static void assertDecodeRejected(
            GameTestHelper helper,
            String label,
            Consumer<RegistryFriendlyByteBuf> packetWriter) {
        RegistryFriendlyByteBuf buffer = newBuffer(helper);
        packetWriter.accept(buffer);
        try {
            productionPacketCodec().decode(buffer);
        } catch (IllegalArgumentException expected) {
            return;
        } catch (RuntimeException unexpected) {
            throw helper.assertionException(
                    label + " threw " + unexpected.getClass().getSimpleName()
                            + " instead of a safe IllegalArgumentException");
        }
        throw helper.assertionException(label + " was accepted");
    }

    private static LongOpenHashSet sectionPrefix(int chunkX, int sectionY, int chunkZ, int count) {
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
