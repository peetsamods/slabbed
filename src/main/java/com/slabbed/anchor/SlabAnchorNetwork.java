package com.slabbed.anchor;

import com.slabbed.Slabbed;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Forge client-mirror sync for Slabbed anchor marker buckets.
 *
 * <p>This channel mirrors complete per-marker chunk buckets. It does not make
 * the client authoritative, does not wire non-Level render views, and does not
 * change Slabbed legal state or dy law.</p>
 */
public final class SlabAnchorNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final int MAX_POSITIONS_PER_BUCKET = 1 << 20;

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Slabbed.MOD_ID, "anchor_sync"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static boolean registered;

    private SlabAnchorNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        CHANNEL.messageBuilder(SlabAnchorBucketSyncPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SlabAnchorBucketSyncPacket::encode)
                .decoder(SlabAnchorBucketSyncPacket::decode)
                .consumerMainThread(SlabAnchorNetwork::handleBucketSync)
                .add();

        MinecraftForge.EVENT_BUS.addListener(SlabAnchorNetwork::onChunkWatch);
        MinecraftForge.EVENT_BUS.addListener(SlabAnchorNetwork::onChunkUnwatch);
    }

    public static void syncBucket(LevelChunk chunk, SlabAnchorMarker marker, LongOpenHashSet positions) {
        if (chunk == null || marker == null || chunk.getLevel().isClientSide()) {
            return;
        }
        CHANNEL.send(
                PacketDistributor.TRACKING_CHUNK.with(() -> chunk),
                SlabAnchorBucketSyncPacket.from(chunk, marker, positions));
    }

    private static void syncBucketToPlayer(ServerPlayer player, LevelChunk chunk, SlabAnchorMarker marker) {
        if (player == null || chunk == null || marker == null) {
            return;
        }
        SlabAnchorStore store = chunk.getCapability(SlabAnchorCapabilities.SLAB_ANCHOR_STORE).resolve().orElse(null);
        LongOpenHashSet positions = store == null ? new LongOpenHashSet() : store.copy(marker);
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                SlabAnchorBucketSyncPacket.from(chunk, marker, positions));
    }

    private static void clearBucketForPlayer(
            ServerPlayer player,
            ResourceLocation dimension,
            int chunkX,
            int chunkZ,
            SlabAnchorMarker marker
    ) {
        if (player == null || dimension == null || marker == null) {
            return;
        }
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SlabAnchorBucketSyncPacket(
                        dimension,
                        chunkX,
                        chunkZ,
                        marker,
                        new long[0]));
    }

    private static void onChunkWatch(ChunkWatchEvent.Watch event) {
        for (SlabAnchorMarker marker : SlabAnchorMarker.values()) {
            syncBucketToPlayer(event.getPlayer(), event.getChunk(), marker);
        }
    }

    private static void onChunkUnwatch(ChunkWatchEvent.UnWatch event) {
        ResourceLocation dimension = event.getLevel().dimension().location();
        int chunkX = event.getPos().x;
        int chunkZ = event.getPos().z;
        for (SlabAnchorMarker marker : SlabAnchorMarker.values()) {
            clearBucketForPlayer(event.getPlayer(), dimension, chunkX, chunkZ, marker);
        }
    }

    private static void handleBucketSync(
            SlabAnchorBucketSyncPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        SlabAnchorClientMirror.applyBucket(
                packet.dimension(),
                packet.chunkX(),
                packet.chunkZ(),
                packet.marker(),
                packet.positions());
    }

    public record SlabAnchorBucketSyncPacket(
            ResourceLocation dimension,
            int chunkX,
            int chunkZ,
            SlabAnchorMarker marker,
            long[] positions
    ) {
        public SlabAnchorBucketSyncPacket {
            positions = positions == null ? new long[0] : positions.clone();
        }

        private static SlabAnchorBucketSyncPacket from(
                LevelChunk chunk,
                SlabAnchorMarker marker,
                LongOpenHashSet positions
        ) {
            return new SlabAnchorBucketSyncPacket(
                    chunk.getLevel().dimension().location(),
                    chunk.getPos().x,
                    chunk.getPos().z,
                    marker,
                    positions == null ? new long[0] : positions.toLongArray());
        }

        private static void encode(SlabAnchorBucketSyncPacket packet, FriendlyByteBuf buf) {
            buf.writeResourceLocation(packet.dimension());
            buf.writeInt(packet.chunkX());
            buf.writeInt(packet.chunkZ());
            buf.writeVarInt(packet.marker().ordinal());
            buf.writeVarInt(packet.positions().length);
            for (long position : packet.positions()) {
                buf.writeLong(position);
            }
        }

        private static SlabAnchorBucketSyncPacket decode(FriendlyByteBuf buf) {
            ResourceLocation dimension = buf.readResourceLocation();
            int chunkX = buf.readInt();
            int chunkZ = buf.readInt();
            SlabAnchorMarker marker = markerByOrdinal(buf.readVarInt());
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_POSITIONS_PER_BUCKET) {
                throw new IllegalArgumentException("Invalid Slabbed anchor bucket size: " + count);
            }
            long[] positions = new long[count];
            for (int i = 0; i < count; i++) {
                positions[i] = buf.readLong();
            }
            return new SlabAnchorBucketSyncPacket(dimension, chunkX, chunkZ, marker, positions);
        }

        private static SlabAnchorMarker markerByOrdinal(int ordinal) {
            SlabAnchorMarker[] values = SlabAnchorMarker.values();
            if (ordinal < 0 || ordinal >= values.length) {
                throw new IllegalArgumentException("Invalid Slabbed anchor marker ordinal: " + ordinal);
            }
            return values[ordinal];
        }
    }
}
