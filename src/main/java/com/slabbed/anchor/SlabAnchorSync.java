package com.slabbed.anchor;

import com.slabbed.Slabbed;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Server-to-client carrier for the chunk attachments on Minecraft 1.20.1.
 *
 * <p>Fabric API's data attachments on this version can save but cannot sync. The initial saved
 * placement data travels with the chunk so it can be installed before the chunk is visible. Separate
 * messages carry the remaining attachments, support older clients and update the watching clients
 * after server-side changes. The client installs each value on the chunk and the existing change
 * poll refreshes its geometry.
 *
 * <p>Wire format per message: chunk position (long), attachment index (varint, the position in
 * {@link #TYPES}), presence byte (0 = removed, 1 = value follows), then the type's own compact
 * encoding ({@link ChunkPositionSetPacketCodec} / {@link ChunkPositionDyMapPacketCodec}).
 */
public final class SlabAnchorSync {

    public static final Identifier CHANNEL = new Identifier(Slabbed.MOD_ID, "chunk_attachments");
    private static final String INITIAL_PLACEMENT = Slabbed.MOD_ID + ":placement_snapshot_v1";
    private static final String HEIGHTS = "heights";
    private static final String MARKERS = "markers";

    /** The synced attachment types, in wire order. Append only: the index travels on the wire. */
    static final List<AttachmentType<?>> TYPES = List.of(
            SlabAnchorAttachment.ANCHOR_TYPE,
            SlabAnchorAttachment.FROZEN_FLAT_TYPE,
            SlabAnchorAttachment.LOWERED_SLAB_CARRIER_TYPE,
            SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE,
            SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE,
            SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE,
            SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE,
            SlabAnchorAttachment.COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE,
            SlabAnchorAttachment.MODERN_PLACEMENT_TYPE,
            SlabAnchorAttachment.PLACEMENT_DY_TYPE);

    private SlabAnchorSync() {
    }

    /**
     * Carries the two placement attachments in the same packet as the block sections. Vanilla
     * reads only known heightmap names, so the namespaced entry leaves its packet format intact.
     */
    public static void writeInitialPlacement(WorldChunk chunk, NbtCompound heightmaps) {
        NbtCompound snapshot = new NbtCompound();
        writeSnapshotAttachment(chunk, snapshot, HEIGHTS, SlabAnchorAttachment.PLACEMENT_DY_TYPE);
        writeSnapshotAttachment(chunk, snapshot, MARKERS, SlabAnchorAttachment.MODERN_PLACEMENT_TYPE);
        heightmaps.put(INITIAL_PLACEMENT, snapshot);
    }

    private static void writeSnapshotAttachment(WorldChunk chunk, NbtCompound snapshot, String key,
            AttachmentType<?> type) {
        if (!chunk.hasAttached(type)) {
            return;
        }
        PacketByteBuf buf = encode(chunk, TYPES.indexOf(type), type);
        try {
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            snapshot.putByteArray(key, data);
        } finally {
            buf.release();
        }
    }

    /** Installs both decoded values before the new client chunk is made available to renderers. */
    public static void readInitialPlacement(WorldChunk chunk, NbtCompound heightmaps) {
        if (!heightmaps.contains(INITIAL_PLACEMENT, NbtElement.COMPOUND_TYPE)) {
            return;
        }
        NbtCompound snapshot = heightmaps.getCompound(INITIAL_PLACEMENT);
        Update heights;
        Update markers;
        try {
            heights = readSnapshotAttachment(chunk, snapshot, HEIGHTS, SlabAnchorAttachment.PLACEMENT_DY_TYPE);
            markers = readSnapshotAttachment(chunk, snapshot, MARKERS, SlabAnchorAttachment.MODERN_PLACEMENT_TYPE);
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            Slabbed.LOGGER.warn("Ignoring invalid initial placement data for chunk {}", chunk.getPos());
            return;
        }
        apply(chunk, heights);
        apply(chunk, markers);
    }

    private static Update readSnapshotAttachment(WorldChunk chunk, NbtCompound snapshot, String key,
            AttachmentType<?> type) {
        long position = chunk.getPos().toLong();
        if (!snapshot.contains(key)) {
            return new Update(position, type, null);
        }
        if (!snapshot.contains(key, NbtElement.BYTE_ARRAY_TYPE)) {
            throw new IllegalArgumentException("Expected encoded attachment");
        }
        PacketByteBuf buf = new PacketByteBuf(Unpooled.wrappedBuffer(snapshot.getByteArray(key)));
        try {
            Update update = decode(buf);
            if (update.chunkPos() != position || update.type() != type || buf.isReadable()) {
                throw new IllegalArgumentException("Invalid initial placement attachment");
            }
            return update;
        } finally {
            buf.release();
        }
    }

    /** Everything a newly watching player needs for this chunk: one message per attachment type. */
    public static void sendChunk(ServerPlayerEntity player, WorldChunk chunk) {
        if (!ServerPlayNetworking.canSend(player, CHANNEL)) {
            return;
        }
        int markerIndex = TYPES.indexOf(SlabAnchorAttachment.MODERN_PLACEMENT_TYPE);
        for (int index = 0; index < TYPES.size(); index++) {
            AttachmentType<?> type = TYPES.get(index);
            if (index != markerIndex && chunk.hasAttached(type)) {
                ServerPlayNetworking.send(player, CHANNEL, encode(chunk, index, type));
            }
        }
        // A synchronized marker selects the frozen reader. Send its value first without changing wire IDs.
        if (chunk.hasAttached(SlabAnchorAttachment.MODERN_PLACEMENT_TYPE)) {
            ServerPlayNetworking.send(player, CHANNEL,
                    encode(chunk, markerIndex, SlabAnchorAttachment.MODERN_PLACEMENT_TYPE));
        }
    }

    /** After a server-side write of {@code type} on {@code chunk}: re-send that type to the chunk's watchers. */
    public static void broadcast(World world, WorldChunk chunk, AttachmentType<?> type) {
        if (!(world instanceof ServerWorld serverWorld) || chunk == null) {
            return;
        }
        int index = TYPES.indexOf(type);
        if (index < 0) {
            return;
        }
        PacketByteBuf buf = null;
        for (ServerPlayerEntity player : PlayerLookup.tracking(serverWorld, chunk.getPos())) {
            if (!ServerPlayNetworking.canSend(player, CHANNEL)) {
                continue;
            }
            if (buf == null) {
                buf = encode(chunk, index, type);
            }
            ServerPlayNetworking.send(player, CHANNEL, PacketByteBufs.copy(buf));
        }
    }

    static PacketByteBuf encode(WorldChunk chunk, int index, AttachmentType<?> type) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeLong(chunk.getPos().toLong());
        buf.writeVarInt(index);
        Object value = chunk.getAttached(type);
        if (value == null) {
            buf.writeByte(0);
            return buf;
        }
        buf.writeByte(1);
        if (value instanceof Long2ByteOpenHashMap map) {
            ChunkPositionDyMapPacketCodec.encode(map, buf);
        } else {
            ChunkPositionSetPacketCodec.encode((LongOpenHashSet) value, buf);
        }
        return buf;
    }

    /** One decoded message. {@code value} is null when the type was removed on the server. */
    public record Update(long chunkPos, AttachmentType<?> type, Object value) {
    }

    public static Update decode(PacketByteBuf buf) {
        long chunkPos = buf.readLong();
        int index = buf.readVarInt();
        if (index < 0 || index >= TYPES.size()) {
            throw new IllegalArgumentException("unknown attachment index " + index);
        }
        AttachmentType<?> type = TYPES.get(index);
        if (buf.readByte() == 0) {
            return new Update(chunkPos, type, null);
        }
        Object value = type == SlabAnchorAttachment.PLACEMENT_DY_TYPE
                ? ChunkPositionDyMapPacketCodec.decode(buf)
                : ChunkPositionSetPacketCodec.decode(buf);
        return new Update(chunkPos, type, value);
    }

    /** Installs a decoded message on a client-side chunk (every client read path reads the chunk). */
    public static void install(WorldChunk chunk, Update update) {
        apply(chunk, update);
    }

    @SuppressWarnings("unchecked")
    private static void apply(WorldChunk chunk, Update update) {
        if (update.value() == null) {
            chunk.removeAttached(update.type());
        } else {
            chunk.setAttached((AttachmentType<Object>) update.type(), update.value());
        }
    }
}
