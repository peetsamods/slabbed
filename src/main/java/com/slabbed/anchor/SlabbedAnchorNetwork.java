package com.slabbed.anchor;

import com.slabbed.Slabbed;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
 * The explicit sync a Forge capability needs and a NeoForge attachment does not.
 *
 * <p>Three server-to-client messages: a whole marker bucket, a whole placement-height map, and a
 * single placement delta. The delta exists because a whole-map resend on every placement is
 * O(chunk) on the wire and hits a payload cliff in a heavily built chunk; the full form is used
 * only when a player starts watching a chunk, and on unwatch to clear what they were told.
 *
 * <p>Nothing here makes the client authoritative. It fills {@link SlabbedClientMirror}, which is a
 * render and prediction input; server law always reads the capability.
 */
public final class SlabbedAnchorNetwork {
    /**
     * Bump on any wire change. A mismatched pair must fail the handshake loudly: with a stale
     * version string the two sides connect and silently drop each other's height packets, which
     * recreates exactly the server/client split this channel exists to close.
     */
    private static final String PROTOCOL_VERSION = "1";

    private static final int MAX_POSITIONS_PER_PACKET = 1 << 20;

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Slabbed.MOD_ID, "anchor_sync"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static boolean registered;

    private SlabbedAnchorNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        CHANNEL.messageBuilder(BucketSyncPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BucketSyncPacket::encode)
                .decoder(BucketSyncPacket::decode)
                .consumerMainThread(SlabbedAnchorNetwork::handleBucket)
                .add();
        CHANNEL.messageBuilder(PlacementFullPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PlacementFullPacket::encode)
                .decoder(PlacementFullPacket::decode)
                .consumerMainThread(SlabbedAnchorNetwork::handlePlacementFull)
                .add();
        CHANNEL.messageBuilder(PlacementDeltaPacket.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PlacementDeltaPacket::encode)
                .decoder(PlacementDeltaPacket::decode)
                .consumerMainThread(SlabbedAnchorNetwork::handlePlacementDelta)
                .add();
        CHANNEL.messageBuilder(ConsentSyncPacket.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ConsentSyncPacket::encode)
                .decoder(ConsentSyncPacket::decode)
                .consumerMainThread(SlabbedAnchorNetwork::handleConsent)
                .add();

        MinecraftForge.EVENT_BUS.addListener(SlabbedAnchorNetwork::onChunkWatch);
        MinecraftForge.EVENT_BUS.addListener(SlabbedAnchorNetwork::onChunkUnwatch);
    }

    // ----------------------------------------------------------- server sends

    public static void syncBucket(
            @Nullable LevelChunk chunk, SlabAnchorMarker marker, LongOpenHashSet positions
    ) {
        if (chunk == null || marker == null || chunk.getLevel().isClientSide()) {
            return;
        }
        CHANNEL.send(
                PacketDistributor.TRACKING_CHUNK.with(() -> chunk),
                new BucketSyncPacket(
                        chunk.getLevel().dimension().location(),
                        chunk.getPos().x, chunk.getPos().z,
                        marker,
                        positions == null ? new long[0] : positions.toLongArray()));
    }

    public static void syncPlacementFull(@Nullable LevelChunk chunk) {
        if (chunk == null || chunk.getLevel().isClientSide()) {
            return;
        }
        SlabbedChunkStore store = SlabbedCapabilities.chunkStore(chunk);
        CHANNEL.send(
                PacketDistributor.TRACKING_CHUNK.with(() -> chunk),
                fullPacket(chunk, store));
    }

    public static void syncPlacementDelta(
            @Nullable LevelChunk chunk, long packedPos, boolean present, byte halfSteps
    ) {
        if (chunk == null || chunk.getLevel().isClientSide()) {
            return;
        }
        CHANNEL.send(
                PacketDistributor.TRACKING_CHUNK.with(() -> chunk),
                new PlacementDeltaPacket(
                        chunk.getLevel().dimension().location(), packedPos, present, halfSteps));
    }

    /** Pushes the consent stamp for one level to everyone in it. */
    public static void syncConsent(
            @Nullable ServerLevel level, @Nullable DeepDyConsentAttachment.Stamp stamp
    ) {
        if (level == null) {
            return;
        }
        CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), ConsentSyncPacket.of(stamp));
    }

    public static void syncConsentToPlayer(
            @Nullable ServerPlayer player, @Nullable DeepDyConsentAttachment.Stamp stamp
    ) {
        if (player == null) {
            return;
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), ConsentSyncPacket.of(stamp));
    }

    private static PlacementFullPacket fullPacket(LevelChunk chunk, @Nullable SlabbedChunkStore store) {
        SlabbedChunkStore.PlacementPair placement =
                store == null ? SlabbedChunkStore.PlacementPair.EMPTY : store.placementPair();
        return new PlacementFullPacket(
                chunk.getLevel().dimension().location(),
                chunk.getPos().x, chunk.getPos().z,
                placement.positions(), placement.halfSteps());
    }

    private static void onChunkWatch(ChunkWatchEvent.Watch event) {
        ServerPlayer player = event.getPlayer();
        LevelChunk chunk = event.getChunk();
        SlabbedChunkStore store = SlabbedCapabilities.chunkStore(chunk);
        ResourceLocation dimension = chunk.getLevel().dimension().location();
        for (SlabAnchorMarker marker : SlabAnchorMarker.values()) {
            long[] positions = store == null ? new long[0] : store.markerPositions(marker);
            CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new BucketSyncPacket(
                            dimension, chunk.getPos().x, chunk.getPos().z, marker, positions));
        }
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player), fullPacket(chunk, store));
    }

    private static void onChunkUnwatch(ChunkWatchEvent.UnWatch event) {
        ServerPlayer player = event.getPlayer();
        ResourceLocation dimension = event.getLevel().dimension().location();
        int chunkX = event.getPos().x;
        int chunkZ = event.getPos().z;
        for (SlabAnchorMarker marker : SlabAnchorMarker.values()) {
            CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new BucketSyncPacket(dimension, chunkX, chunkZ, marker, new long[0]));
        }
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new PlacementFullPacket(dimension, chunkX, chunkZ, new long[0], new byte[0]));
    }

    // -------------------------------------------------------- client handlers

    private static void handleBucket(BucketSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        SlabbedClientMirror.applyBucket(
                packet.dimension(), packet.chunkX(), packet.chunkZ(),
                packet.marker(), packet.positions());
    }

    private static void handlePlacementFull(
            PlacementFullPacket packet, Supplier<NetworkEvent.Context> ctx) {
        SlabbedClientMirror.applyPlacementFull(
                packet.dimension(), packet.chunkX(), packet.chunkZ(),
                packet.positions(), packet.halfSteps());
    }

    private static void handlePlacementDelta(
            PlacementDeltaPacket packet, Supplier<NetworkEvent.Context> ctx) {
        SlabbedClientMirror.applyPlacementDelta(
                packet.dimension(), packet.packedPos(), packet.present(), packet.halfSteps());
    }

    private static void handleConsent(ConsentSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        SlabbedClientMirror.applyConsent(packet.stamp());
    }

    // ---------------------------------------------------------------- packets

    public record BucketSyncPacket(
            ResourceLocation dimension, int chunkX, int chunkZ,
            SlabAnchorMarker marker, long[] positions
    ) {
        public BucketSyncPacket {
            positions = positions == null ? new long[0] : positions.clone();
        }

        static void encode(BucketSyncPacket packet, FriendlyByteBuf buf) {
            buf.writeResourceLocation(packet.dimension());
            buf.writeInt(packet.chunkX());
            buf.writeInt(packet.chunkZ());
            buf.writeVarInt(packet.marker().ordinal());
            buf.writeVarInt(packet.positions().length);
            for (long position : packet.positions()) {
                buf.writeLong(position);
            }
        }

        static BucketSyncPacket decode(FriendlyByteBuf buf) {
            ResourceLocation dimension = buf.readResourceLocation();
            int chunkX = buf.readInt();
            int chunkZ = buf.readInt();
            SlabAnchorMarker marker = SlabAnchorMarker.byOrdinal(buf.readVarInt());
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_POSITIONS_PER_PACKET) {
                throw new IllegalArgumentException("Invalid Slabbed anchor bucket size: " + count);
            }
            long[] positions = new long[count];
            for (int i = 0; i < count; i++) {
                positions[i] = buf.readLong();
            }
            return new BucketSyncPacket(dimension, chunkX, chunkZ, marker, positions);
        }
    }

    public record PlacementFullPacket(
            ResourceLocation dimension, int chunkX, int chunkZ,
            long[] positions, byte[] halfSteps
    ) {
        public PlacementFullPacket {
            positions = positions == null ? new long[0] : positions.clone();
            halfSteps = halfSteps == null ? new byte[0] : halfSteps.clone();
        }

        static void encode(PlacementFullPacket packet, FriendlyByteBuf buf) {
            if (packet.positions().length != packet.halfSteps().length) {
                // Never truncate to a prefix: positions matched to the wrong heights would
                // author wrong values on every client that received them.
                throw new IllegalArgumentException("placement pair length mismatch: "
                        + packet.positions().length + " vs " + packet.halfSteps().length);
            }
            buf.writeResourceLocation(packet.dimension());
            buf.writeInt(packet.chunkX());
            buf.writeInt(packet.chunkZ());
            buf.writeVarInt(packet.positions().length);
            for (int i = 0; i < packet.positions().length; i++) {
                buf.writeLong(packet.positions()[i]);
                buf.writeByte(packet.halfSteps()[i]);
            }
        }

        static PlacementFullPacket decode(FriendlyByteBuf buf) {
            ResourceLocation dimension = buf.readResourceLocation();
            int chunkX = buf.readInt();
            int chunkZ = buf.readInt();
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_POSITIONS_PER_PACKET) {
                throw new IllegalArgumentException("Invalid Slabbed placement map size: " + count);
            }
            long[] positions = new long[count];
            byte[] halfSteps = new byte[count];
            for (int i = 0; i < count; i++) {
                positions[i] = buf.readLong();
                halfSteps[i] = buf.readByte();
            }
            return new PlacementFullPacket(dimension, chunkX, chunkZ, positions, halfSteps);
        }
    }

    public record PlacementDeltaPacket(
            ResourceLocation dimension, long packedPos, boolean present, byte halfSteps
    ) {
        static void encode(PlacementDeltaPacket packet, FriendlyByteBuf buf) {
            buf.writeResourceLocation(packet.dimension());
            buf.writeLong(packet.packedPos());
            buf.writeBoolean(packet.present());
            buf.writeByte(packet.halfSteps());
        }

        static PlacementDeltaPacket decode(FriendlyByteBuf buf) {
            return new PlacementDeltaPacket(
                    buf.readResourceLocation(), buf.readLong(), buf.readBoolean(), buf.readByte());
        }
    }

    public record ConsentSyncPacket(@Nullable CompoundTag payload) {
        private static final String STAMP_KEY = "stamp";

        static ConsentSyncPacket of(@Nullable DeepDyConsentAttachment.Stamp stamp) {
            if (stamp == null) {
                return new ConsentSyncPacket(null);
            }
            CompoundTag tag = new CompoundTag();
            tag.put(STAMP_KEY, stamp.serializedTag());
            return new ConsentSyncPacket(tag);
        }

        @Nullable
        DeepDyConsentAttachment.Stamp stamp() {
            CompoundTag tag = payload();
            if (tag == null || !tag.contains(STAMP_KEY)) {
                return null;
            }
            Tag stored = tag.get(STAMP_KEY);
            return stored == null ? null : DeepDyConsentAttachment.Stamp.fromTag(stored);
        }

        static void encode(ConsentSyncPacket packet, FriendlyByteBuf buf) {
            buf.writeNbt(packet.payload());
        }

        static ConsentSyncPacket decode(FriendlyByteBuf buf) {
            return new ConsentSyncPacket(buf.readNbt());
        }
    }
}
