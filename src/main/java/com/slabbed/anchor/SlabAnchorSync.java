package com.slabbed.anchor;

import com.slabbed.Slabbed;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Server-to-client carrier for the chunk attachments on Minecraft 1.20.1.
 *
 * <p>Fabric API's data attachments on this version can save but not sync: there is no
 * {@code syncWith}. Every height fact Slabbed stores lives in a chunk attachment and the client reads
 * it from its copy of the chunk, so this class does the one thing the newer lines get from the API:
 * when a player starts watching a chunk the current value of every attachment type is sent (one
 * message per type, so a dense chunk never approaches the payload cap), and after every server-side
 * write the changed type is re-sent to the players tracking that chunk. The client installs each
 * message with {@code setAttached} / {@code removeAttached} on its chunk, so every client read path and
 * the existing change poll stay exactly as they are. A client chunk unload drops the values with the
 * chunk; nothing has to be cleared explicitly.
 *
 * <p>Wire format per message: chunk position (long), attachment index (varint, the position in
 * {@link #TYPES}), presence byte (0 = removed, 1 = value follows), then the type's own compact
 * encoding ({@link ChunkPositionSetPacketCodec} / {@link ChunkPositionDyMapPacketCodec}).
 */
public final class SlabAnchorSync {

    public static final Identifier CHANNEL = new Identifier(Slabbed.MOD_ID, "chunk_attachments");

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

    /** Everything a newly watching player needs for this chunk: one message per attachment type. */
    public static void sendChunk(ServerPlayerEntity player, WorldChunk chunk) {
        if (!ServerPlayNetworking.canSend(player, CHANNEL)) {
            return;
        }
        for (int index = 0; index < TYPES.size(); index++) {
            AttachmentType<?> type = TYPES.get(index);
            if (chunk.hasAttached(type)) {
                ServerPlayNetworking.send(player, CHANNEL, encode(chunk, index, type));
            }
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
    @SuppressWarnings("unchecked")
    public static void install(WorldChunk chunk, Update update) {
        if (update.value() == null) {
            chunk.removeAttached(update.type());
        } else {
            chunk.setAttached((AttachmentType<Object>) update.type(), update.value());
        }
    }
}
