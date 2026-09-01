package com.slabbed.test;

import com.slabbed.anchor.SlabPlacementHeightAttachment;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import java.util.Arrays;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class SlabPlacementHeightPersistenceSyncTest {
    private static final String TEMPLATE = "empty";
    // The store writes its own keys straight into the capability tag, where the donor
    // wrote one namespaced attachment blob.
    private static final String ATTACHMENT_KEY = "placement_dy_pos";

    @GameTest(template = TEMPLATE)
    public void nativePersistenceAndSyncBindingsAreLive(GameTestHelper ctx) {
        LevelChunk chunk = testChunk(ctx);
        BlockPos pos = firstPosition(chunk, ctx);
        ChunkSnapshot snapshot = snapshot(chunk);
        try {
            SlabbedTestAccess.clearPlacementFacts(chunk);
            chunk.setUnsaved(false);

            // The donor asks a registry whether placement_dy is declared synchronized. Forge has
            // no such registry: synchronization is explicit, performed by SlabbedAnchorNetwork,
            // so there is nothing to interrogate. What that row was really guarding - that the
            // store exists on the chunk and round-trips - is asserted directly below.
            ctx.assertTrue(SlabbedTestAccess.hasStore(chunk),
                    "the placement store must be attached to the chunk");
            ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(chunk, pos, -2),
                    "the persistence fixture fact must be stored");

            CompoundTag encoded = SlabbedTestAccess.saveStore(chunk);
            ctx.assertTrue(encoded != null && encoded.contains(ATTACHMENT_KEY),
                    "the native chunk serializer must include the placement_dy attachment");

            ctx.assertTrue(SlabPlacementHeightAttachment.remove(chunk, pos),
                    "removing the only persistence fixture fact must change the attachment");
            CompoundTag empty = SlabbedTestAccess.saveStore(chunk);
            ctx.assertTrue(empty == null || !empty.contains(ATTACHMENT_KEY),
                    "an empty placement_dy attachment must be omitted from persistence");

            SlabbedTestAccess.loadStore(chunk, encoded);
            ctx.assertTrue(SlabPlacementHeightAttachment.storedHalfSteps(chunk, pos).orElse(0) == -2,
                    "the native attachment serializer must restore the exact half-step fact");
        } finally {
            restore(chunk, snapshot);
        }
        ctx.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void streamCodecIsCanonicalAndRoundTrips(GameTestHelper ctx) {
        Long2ByteOpenHashMap first = new Long2ByteOpenHashMap();
        first.put(BlockPos.asLong(16, -1, -16), (byte) 1);
        first.put(BlockPos.asLong(-1, 0, 15), (byte) -2);
        first.put(BlockPos.asLong(15, 16, 16), (byte) 0);
        first.put(BlockPos.asLong(-16, 1, -17), (byte) -1);

        Long2ByteOpenHashMap second = new Long2ByteOpenHashMap();
        second.put(BlockPos.asLong(-16, 1, -17), (byte) -1);
        second.put(BlockPos.asLong(15, 16, 16), (byte) 0);
        second.put(BlockPos.asLong(-1, 0, 15), (byte) -2);
        second.put(BlockPos.asLong(16, -1, -16), (byte) 1);

        byte[] firstBytes = encode(ctx, first);
        byte[] secondBytes = encode(ctx, second);
        ctx.assertTrue(Arrays.equals(firstBytes, secondBytes),
                "equal fact maps must have one canonical native sync encoding");
        ctx.assertTrue(decode(ctx, firstBytes).equals(first),
                "the native sync codec must round-trip every packed position and half-step");

        // Read the ORDER off the wire, not just the equality of two encodings. Byte equality
        // alone is satisfiable by hash geometry: for any small key set whose iteration order
        // happens to be insertion-independent, both encodings agree whether or not the encoder
        // sorts at all - so deleting the canonical sort left this row green. Asserting the
        // decoded positions ascend tests the property the row is named for, and cannot be
        // satisfied by a coincidence of the fixture's key choice.
        long[] wireOrder = decodePositionsInWireOrder(ctx, firstBytes);
        ctx.assertTrue(wireOrder.length == first.size(),
                "premise: the wire must carry every fact, or the order below is read from a"
                        + " truncated encoding; wire=" + wireOrder.length + " map=" + first.size());
        for (int index = 1; index < wireOrder.length; index++) {
            ctx.assertTrue(wireOrder[index - 1] < wireOrder[index],
                    "the sync encoding must place packed positions in ascending order, or equal"
                            + " maps stop having one encoding as soon as a key set's hash order"
                            + " stops being insertion-independent; index=" + index
                            + " previous=" + wireOrder[index - 1] + " current=" + wireOrder[index]);
        }
        ctx.succeed();
    }

    /** Reads back only the packed positions, in the exact order the encoder wrote them. */
    private static long[] decodePositionsInWireOrder(GameTestHelper ctx, byte[] bytes) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            int count = buffer.readVarInt();
            long[] positions = new long[count];
            for (int index = 0; index < count; index++) {
                positions[index] = buffer.readLong();
                buffer.readByte();
            }
            return positions;
        } finally {
            buffer.release();
        }
    }

    @GameTest(template = TEMPLATE)
    public void streamCodecRejectsUnboundedDuplicateAndTruncatedInput(GameTestHelper ctx) {
        assertDecodeRejected(ctx,
                buffer -> buffer.writeVarInt(SlabPlacementHeightAttachment.MAX_FACTS_PER_CHUNK + 1),
                "a fact count above the physical chunk limit must be rejected before allocation");
        assertDecodeRejected(ctx, buffer -> buffer.writeVarInt(-1),
                "a negative fact count must be rejected before allocation");

        long duplicate = BlockPos.asLong(4, 5, 6);
        assertDecodeRejected(ctx, buffer -> {
            buffer.writeVarInt(2);
            buffer.writeLong(duplicate);
            buffer.writeByte(-1);
            buffer.writeLong(duplicate);
            buffer.writeByte(-2);
        }, "duplicate packed positions must be rejected on the wire");

        assertDecodeRejected(ctx, buffer -> {
            buffer.writeVarInt(1);
            buffer.writeLong(BlockPos.asLong(7, 8, 9));
        }, "a truncated placement-height value must be rejected before decoding entries");
        ctx.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void mutationsAreCopyOnWriteNoOpAwareAndRemoveEmptyStorage(GameTestHelper ctx) {
        LevelChunk chunk = testChunk(ctx);
        BlockPos firstPos = firstPosition(chunk, ctx);
        BlockPos secondPos = firstPos.offset(1, 0, 0);
        BlockPos absentPos = firstPos.offset(2, 0, 0);
        ChunkSnapshot snapshot = snapshot(chunk);
        try {
            SlabbedTestAccess.clearPlacementFacts(chunk);
            chunk.setUnsaved(false);

            ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(chunk, firstPos, -2),
                    "the first canonical fact must create the attachment");
            Long2ByteOpenHashMap firstMap = currentMap(chunk);
            ctx.assertTrue(firstMap != null && chunk.isUnsaved(),
                    "a changed attachment must mark its chunk unsaved");

            chunk.setUnsaved(false);
            ctx.assertTrue(!SlabPlacementHeightAttachment.putHalfSteps(chunk, firstPos, -2),
                    "storing an identical fact must be a no-op");
            ctx.assertTrue(currentMap(chunk) == firstMap && !chunk.isUnsaved(),
                    "an identical write must neither replace the map nor dirty the chunk");

            ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(chunk, secondPos, -1),
                    "a different position must add a second fact");
            Long2ByteOpenHashMap secondMap = currentMap(chunk);
            ctx.assertTrue(secondMap != null && secondMap != firstMap
                            && firstMap.size() == 1 && secondMap.size() == 2,
                    "a changed write must replace rather than mutate the previous map");

            chunk.setUnsaved(false);
            ctx.assertTrue(!SlabPlacementHeightAttachment.remove(chunk, absentPos),
                    "removing an absent fact must be a no-op");
            ctx.assertTrue(currentMap(chunk) == secondMap && !chunk.isUnsaved(),
                    "an absent removal must neither replace the map nor dirty the chunk");

            ctx.assertTrue(SlabPlacementHeightAttachment.remove(chunk, firstPos),
                    "an existing fact must be removable");
            Long2ByteOpenHashMap finalMap = currentMap(chunk);
            ctx.assertTrue(finalMap != null && finalMap != secondMap && finalMap.size() == 1,
                    "a non-empty removal must install a copy with the remaining facts");

            chunk.setUnsaved(false);
            ctx.assertTrue(SlabPlacementHeightAttachment.remove(chunk, secondPos),
                    "the last existing fact must be removable");
            ctx.assertTrue(currentMap(chunk) == null && chunk.isUnsaved(),
                    "removing the last fact must remove the attachment and dirty the chunk");
        } finally {
            restore(chunk, snapshot);
        }
        ctx.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void invalidWritesAndPhysicalCapacityAreBoundedWithoutMaximumAllocation(GameTestHelper ctx) {
        LevelChunk chunk = testChunk(ctx);
        BlockPos valid = firstPosition(chunk, ctx);
        BlockPos foreign = valid.offset(16, 0, 0);
        BlockPos below = new BlockPos(valid.getX(), chunk.getMinBuildHeight() - 1, valid.getZ());
        BlockPos above = new BlockPos(valid.getX(), chunk.getMaxBuildHeight(), valid.getZ());
        ChunkSnapshot snapshot = snapshot(chunk);
        try {
            SlabbedTestAccess.clearPlacementFacts(chunk);
            chunk.setUnsaved(false);

            ctx.assertTrue(!SlabPlacementHeightAttachment.putHalfSteps(chunk, foreign, -1)
                            && !SlabPlacementHeightAttachment.putHalfSteps(chunk, below, -1)
                            && !SlabPlacementHeightAttachment.putHalfSteps(chunk, above, -1)
                            && !SlabPlacementHeightAttachment.putHalfSteps(chunk, valid, Byte.MIN_VALUE - 1)
                            && !SlabPlacementHeightAttachment.putHalfSteps(chunk, valid, Byte.MAX_VALUE + 1)
                            && !SlabPlacementHeightAttachment.remove(chunk, foreign)
                            && !SlabPlacementHeightAttachment.remove(chunk, below)
                            && !SlabPlacementHeightAttachment.remove(chunk, above),
                    "foreign, out-of-height and out-of-byte mutations must all be declined");
            ctx.assertTrue(currentMap(chunk) == null && !chunk.isUnsaved(),
                    "declined writes must not create storage or dirty the chunk");

            int physicalMaximum = 16 * 16 * DimensionType.Y_SIZE;
            ctx.assertTrue(SlabPlacementHeightAttachment.MAX_FACTS_PER_CHUNK == physicalMaximum
                            && physicalMaximum == 1_040_384,
                    "the codec bound must equal every physical cell in the tallest legal chunk");
            long maximumWireBytes = 3L + (long) physicalMaximum * (Long.BYTES + Byte.BYTES);
            ctx.assertTrue(maximumWireBytes == 9_363_459L,
                    "the maximum native full-map body arithmetic must remain explicit");
            ctx.assertTrue(16 * 16 * chunk.getHeight() <= physicalMaximum,
                    "the active dimension's chunk column must fit inside the global physical bound");

            Long2ByteOpenHashMap oversizedWithoutEntries = new Long2ByteOpenHashMap() {
                @Override
                public int size() {
                    return physicalMaximum + 1;
                }
            };
            ctx.assertTrue(SlabPlacementHeightAttachment.codec()
                            .encodeStart(NbtOps.INSTANCE, oversizedWithoutEntries)
                            .result()
                            .isEmpty(),
                    "persistence encoding must reject an over-physical count before walking entries");

            int oversized = SlabPlacementHeightAttachment.MAX_FACTS_PER_CHUNK + 1;
            CompoundTag oversizedPersistence = new CompoundTag();
            oversizedPersistence.putLongArray("positions", new long[oversized]);
            oversizedPersistence.putIntArray("height_half_steps", new int[oversized]);
            ctx.assertTrue(SlabPlacementHeightAttachment.codec()
                            .parse(NbtOps.INSTANCE, oversizedPersistence)
                            .result()
                            .isEmpty(),
                    "persistence decoding must reject arrays beyond one chunk's physical capacity");
        } finally {
            restore(chunk, snapshot);
        }
        ctx.succeed();
    }

    private static LevelChunk testChunk(GameTestHelper ctx) {
        BlockPos anchor = ctx.absolutePos(new BlockPos(3, 3, 3));
        return ctx.getLevel().getChunk(anchor.getX() >> 4, anchor.getZ() >> 4);
    }

    private static BlockPos firstPosition(LevelChunk chunk, GameTestHelper ctx) {
        int y = Math.max(chunk.getMinBuildHeight(),
                Math.min(ctx.absolutePos(new BlockPos(3, 3, 3)).getY(), chunk.getMaxBuildHeight() - 1));
        return new BlockPos(chunk.getPos().getMinBlockX() + 1, y, chunk.getPos().getMinBlockZ() + 1);
    }

    private static Long2ByteOpenHashMap currentMap(LevelChunk chunk) {
        return SlabbedTestAccess.placementFacts(chunk);
    }

    private static ChunkSnapshot snapshot(LevelChunk chunk) {
        Long2ByteOpenHashMap current = currentMap(chunk);
        return new ChunkSnapshot(
                current == null ? null : new Long2ByteOpenHashMap(current),
                chunk.isUnsaved());
    }

    private static void restore(LevelChunk chunk, ChunkSnapshot snapshot) {
        if (snapshot.facts() == null) {
            SlabbedTestAccess.clearPlacementFacts(chunk);
        } else {
            SlabbedTestAccess.putPlacementFacts(chunk, new Long2ByteOpenHashMap(snapshot.facts()));
        }
        chunk.setUnsaved(snapshot.unsaved());
    }

    private static byte[] encode(GameTestHelper ctx, Long2ByteOpenHashMap facts) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SlabPlacementHeightAttachment.encodeStream(buffer, facts);
            byte[] encoded = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), encoded);
            return encoded;
        } finally {
            buffer.release();
        }
    }

    private static Long2ByteOpenHashMap decode(GameTestHelper ctx, byte[] encoded) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(encoded));
        try {
            return SlabPlacementHeightAttachment.decodeStream(buffer);
        } finally {
            buffer.release();
        }
    }

    private static void assertDecodeRejected(
            GameTestHelper ctx,
            Consumer<FriendlyByteBuf> writer,
            String message
    ) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        boolean rejected = false;
        try {
            writer.accept(buffer);
            SlabPlacementHeightAttachment.decodeStream(buffer);
        } catch (RuntimeException expected) {
            rejected = true;
        } finally {
            buffer.release();
        }
        ctx.assertTrue(rejected, message);
    }

    private record ChunkSnapshot(Long2ByteOpenHashMap facts, boolean unsaved) {
    }
}
