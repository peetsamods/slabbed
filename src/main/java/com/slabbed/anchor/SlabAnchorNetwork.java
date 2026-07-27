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
    // "2" since STAYS Phase 5: message ids 1/2 (placement-dy sync) joined the channel. A
    // pre-Phase-5 build must be rejected LOUDLY at the handshake — with the old version string a
    // mixed pair connects fine and silently drops every dy packet, recreating exactly the
    // server/client height split this sync exists to eliminate (adversarial review).
    private static final String PROTOCOL_VERSION = "2";
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
        CHANNEL.messageBuilder(PlacementDyFullSyncPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PlacementDyFullSyncPacket::encode)
                .decoder(PlacementDyFullSyncPacket::decode)
                .consumerMainThread(SlabAnchorNetwork::handlePlacementDyFull)
                .add();
        CHANNEL.messageBuilder(PlacementDyDeltaPacket.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PlacementDyDeltaPacket::encode)
                .decoder(PlacementDyDeltaPacket::decode)
                .consumerMainThread(SlabAnchorNetwork::handlePlacementDyDelta)
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
        SlabAnchorStore store = chunk.getCapability(SlabAnchorCapabilities.SLAB_ANCHOR_STORE).orElse(null);
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
        syncPlacementDyFullToPlayer(event.getPlayer(), event.getChunk());
    }

    /**
     * One placement's dy change (STAYS Phase 5). DELIBERATELY A DELTA, not the donor's shape:
     * the 26.2 reference re-encodes and re-sends the ENTIRE chunk map to every tracking player
     * on every single placement, and hard-fails past 2,032 stored heights in one chunk. A delta
     * makes each placement O(1) on the wire and removes the per-placement cliff. Residual bound,
     * stated honestly: the chunk-watch FULL path still carries the whole map in one packet, so a
     * chunk beyond roughly 65,000 stored heights would exceed the 1 MiB payload limit on watch —
     * two orders of magnitude past any real build, but a bound, not "no cliff".
     */
    public static void syncPlacementDyDelta(LevelChunk chunk, long packedPos, boolean present, long rawBits) {
        if (chunk == null || chunk.getLevel().isClientSide()) {
            return;
        }
        CHANNEL.send(
                PacketDistributor.TRACKING_CHUNK.with(() -> chunk),
                new PlacementDyDeltaPacket(
                        chunk.getLevel().dimension().location(), packedPos, present, rawBits));
    }

    private static void syncPlacementDyFullToPlayer(ServerPlayer player, LevelChunk chunk) {
        if (player == null || chunk == null) {
            return;
        }
        SlabAnchorStore store = chunk.getCapability(SlabAnchorCapabilities.SLAB_ANCHOR_STORE).orElse(null);
        long[][] entries = store == null ? new long[][] {new long[0], new long[0]}
                : store.placementDyEntries();
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new PlacementDyFullSyncPacket(
                        chunk.getLevel().dimension().location(),
                        chunk.getPos().x, chunk.getPos().z,
                        entries[0], entries[1]));
    }

    private static void onChunkUnwatch(ChunkWatchEvent.UnWatch event) {
        ResourceLocation dimension = event.getLevel().dimension().location();
        int chunkX = event.getPos().x;
        int chunkZ = event.getPos().z;
        for (SlabAnchorMarker marker : SlabAnchorMarker.values()) {
            clearBucketForPlayer(event.getPlayer(), dimension, chunkX, chunkZ, marker);
        }
        CHANNEL.send(
                PacketDistributor.PLAYER.with(event::getPlayer),
                new PlacementDyFullSyncPacket(dimension, chunkX, chunkZ, new long[0], new long[0]));
    }

    private static void handlePlacementDyFull(
            PlacementDyFullSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        SlabAnchorClientMirror.applyPlacementDyFull(
                packet.dimension(), packet.chunkX(), packet.chunkZ(),
                packet.positions(), packet.bits());
    }

    private static void handlePlacementDyDelta(
            PlacementDyDeltaPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        SlabAnchorClientMirror.applyPlacementDyDelta(
                packet.dimension(), packet.packedPos(), packet.present(), packet.rawBits());
    }

    /** Full per-chunk dy map: interleaved-safe (one count, parallel arrays on the wire). */
    public record PlacementDyFullSyncPacket(
            ResourceLocation dimension, int chunkX, int chunkZ, long[] positions, long[] bits) {

        public PlacementDyFullSyncPacket {
            positions = positions == null ? new long[0] : positions.clone();
            bits = bits == null ? new long[0] : bits.clone();
        }

        public static void encode(PlacementDyFullSyncPacket packet, FriendlyByteBuf buf) {
            buf.writeResourceLocation(packet.dimension());
            buf.writeInt(packet.chunkX());
            buf.writeInt(packet.chunkZ());
            if (packet.positions().length != packet.bits().length) {
                // Drop-whole/fail-loudly law: a mismatched pair must never silently truncate to
                // a prefix — mispaired heights would author wrong values on every client.
                throw new IllegalArgumentException("placement-dy pair length mismatch: "
                        + packet.positions().length + " vs " + packet.bits().length);
            }
            int n = packet.positions().length;
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                buf.writeLong(packet.positions()[i]);
                buf.writeLong(packet.bits()[i]);
            }
        }

        public static PlacementDyFullSyncPacket decode(FriendlyByteBuf buf) {
            ResourceLocation dimension = buf.readResourceLocation();
            int chunkX = buf.readInt();
            int chunkZ = buf.readInt();
            int n = buf.readVarInt();
            if (n < 0 || n > MAX_POSITIONS_PER_BUCKET) {
                throw new IllegalArgumentException("Invalid Slabbed placement-dy map size: " + n);
            }
            long[] positions = new long[n];
            long[] bits = new long[n];
            for (int i = 0; i < n; i++) {
                positions[i] = buf.readLong();
                bits[i] = buf.readLong();
            }
            return new PlacementDyFullSyncPacket(dimension, chunkX, chunkZ, positions, bits);
        }
    }

    /** One placement's dy change: constant-size on the wire regardless of chunk contents. */
    public record PlacementDyDeltaPacket(
            ResourceLocation dimension, long packedPos, boolean present, long rawBits) {

        public static void encode(PlacementDyDeltaPacket packet, FriendlyByteBuf buf) {
            buf.writeResourceLocation(packet.dimension());
            buf.writeLong(packet.packedPos());
            buf.writeBoolean(packet.present());
            buf.writeLong(packet.rawBits());
        }

        public static PlacementDyDeltaPacket decode(FriendlyByteBuf buf) {
            return new PlacementDyDeltaPacket(
                    buf.readResourceLocation(), buf.readLong(), buf.readBoolean(), buf.readLong());
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
