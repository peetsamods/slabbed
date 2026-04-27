package com.slabbed.anchor;

import java.util.function.Predicate;
import com.mojang.serialization.Codec;
import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.PaleMossCarpetBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Persistent slab-anchor registry.
 *
 * <p>When an ordinary full block is placed directly on a bottom slab, that placement is
 * recorded as an anchor on the chunk so the block keeps its lowered dy even if the
 * supporting bottom slab is later removed. Anchors are cleared when the anchored block
 * itself is broken/replaced.
 *
 * <p>Storage: per-{@link WorldChunk} {@link LongOpenHashSet} of packed {@link BlockPos}
 * longs. Persisted via Fabric data attachment, synced to all watching clients.
 *
 * <p>Scope: direct FB-on-BS only. No retroactive anchoring, no chain anchoring,
 * no side-slab persistence, no torch interaction.
 */
public final class SlabAnchorAttachment {
    private SlabAnchorAttachment() {
    }

    public static final boolean TRACE =
            Boolean.getBoolean("slabbed.anchor.trace");

    /**
     * Client-side fallback for anchor queries issued by chunk render paths that
     * receive a non-{@link World} {@link net.minecraft.world.BlockView}
     * (e.g. {@code ChunkRendererRegion}).  Set by the client entrypoint; always
     * null on a dedicated server.  No {@code MinecraftClient} reference needed
     * in common code.
     */
    public static Predicate<BlockPos> clientAnchorLookup = null;

    private static final Identifier ANCHOR_ID = Identifier.of(Slabbed.MOD_ID, "slab_anchors");

    /**
     * Codec for the anchor set.  Backed by {@code long[]} so the NBT representation is
     * a {@code LongArrayTag}, the most compact form available.
     */
    private static final Codec<LongOpenHashSet> SET_CODEC = Codec.LONG_STREAM.xmap(
            stream -> new LongOpenHashSet(stream.toArray()),
            set -> java.util.stream.LongStream.of(set.toLongArray())
    );

    /**
     * Packet codec for client sync. {@link AttachmentSyncPredicate#all()} is used at
     * registration so anchors travel with the chunk packet automatically.
     */
    private static final PacketCodec<RegistryByteBuf, LongOpenHashSet> PACKET_CODEC = PacketCodec.of(
            (set, buf) -> {
                long[] arr = set.toLongArray();
                buf.writeVarInt(arr.length);
                for (long v : arr) {
                    buf.writeLong(v);
                }
            },
            buf -> {
                int n = buf.readVarInt();
                LongOpenHashSet s = new LongOpenHashSet(n);
                for (int i = 0; i < n; i++) {
                    s.add(buf.readLong());
                }
                return s;
            }
    );

    public static final AttachmentType<LongOpenHashSet> ANCHOR_TYPE =
            AttachmentRegistry.<LongOpenHashSet>create(ANCHOR_ID, builder -> builder
                    .persistent(SET_CODEC)
                    .syncWith(PACKET_CODEC, AttachmentSyncPredicate.all())
            );

    /**
     * Triggers static-init class loading. Call once from the mod entrypoint so the
     * attachment is registered before any chunk loads.
     */
    public static void register() {
        // Touch the class so the static field initializes and registers with Fabric.
        if (ANCHOR_TYPE == null) {
            throw new IllegalStateException("SlabAnchorAttachment failed to register");
        }
    }

    // ── server-side mutation ──────────────────────────────────────────

    /**
     * Records an anchor at {@code pos}. Server-side only; no-op on client world or
     * if {@code pos} does not qualify under {@link #qualifiesForDirectAnchor}.
     */
    public static void addAnchor(World world, BlockPos pos, BlockState state) {
        if (world == null || world.isClient()) {
            return;
        }
        boolean qualifies = qualifiesForDirectAnchor(world, pos, state);
        if (TRACE) {
            Slabbed.LOGGER.info("[ANCHOR] add attempt side=SERVER pos={} state={} qualifies={}",
                    pos.toShortString(), state, qualifies);
        }
        if (!qualifies) {
            return;
        }
        WorldChunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            if (TRACE) {
                Slabbed.LOGGER.info("[ANCHOR] add reject pos={} reason=chunk_null", pos.toShortString());
            }
            return;
        }
        LongOpenHashSet set = chunk.getAttached(ANCHOR_TYPE);
        if (set == null) {
            set = new LongOpenHashSet();
        }
        if (set.add(pos.asLong())) {
            // setAttached triggers persistence + auto-sync for synced attachments.
            chunk.setAttached(ANCHOR_TYPE, set);
            if (TRACE) {
                Slabbed.LOGGER.info("[ANCHOR] add success pos={} chunk={} setSize={}",
                        pos.toShortString(), chunk.getPos(), set.size());
            }
        }
    }

    /**
     * Clears any anchor at {@code pos}. Server-side only.
     */
    public static void removeAnchor(World world, BlockPos pos) {
        if (world == null || world.isClient()) {
            return;
        }
        WorldChunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return;
        }
        LongOpenHashSet set = chunk.getAttached(ANCHOR_TYPE);
        if (set == null || set.isEmpty()) {
            if (TRACE) {
                Slabbed.LOGGER.info("[ANCHOR] remove pos={} existed=false", pos.toShortString());
            }
            return;
        }
        boolean removed = set.remove(pos.asLong());
        if (TRACE) {
            Slabbed.LOGGER.info("[ANCHOR] remove pos={} existed={}", pos.toShortString(), removed);
        }
        if (removed) {
            if (set.isEmpty()) {
                chunk.removeAttached(ANCHOR_TYPE);
            } else {
                chunk.setAttached(ANCHOR_TYPE, set);
            }
        }
    }

    // ── shared query ──────────────────────────────────────────────────

    /**
     * Returns true if {@code pos} carries a persistent slab-anchor.
     *
     * <p>Safe on both server and client (client mirror is populated via attachment sync).
     * Returns false for any {@link BlockView} that is not a full {@link World}, so it is
     * safe to call from shape mixins that may receive partial views during chunk gen.
     */
    public static boolean isAnchored(BlockView world, BlockPos pos) {
        if (pos == null) {
            return false;
        }
        if (!(world instanceof World w)) {
            // Chunk render paths (e.g. ChunkRendererRegion) are not World instances and
            // cannot access chunk attachments directly.  Delegate to the client fallback
            // hook so the model render path sees the same anchor state as outline/raycast.
            return clientAnchorLookup != null && clientAnchorLookup.test(pos);
        }
        WorldChunk chunk = w.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return false;
        }
        LongOpenHashSet set = chunk.getAttached(ANCHOR_TYPE);
        boolean anchored = set != null && set.contains(pos.asLong());
        if (TRACE && anchored) {
            Slabbed.LOGGER.info("[ANCHOR] query true side={} pos={}",
                    w.isClient() ? "CLIENT" : "SERVER", pos.toShortString());
        }
        return anchored;
    }

    // ── qualifier ─────────────────────────────────────────────────────

    /**
     * Tight predicate matching the existing direct FB-on-BS rule:
     * <ul>
     *   <li>not air, not fluid</li>
     *   <li>not a slab, carpet, thin top layer, block-entity, bed, or double-block</li>
     *   <li>solid full block</li>
     *   <li>has a bottom slab directly below ({@link SlabSupport#hasBottomSlabBelow})</li>
     * </ul>
     *
     * <p>Strictly narrower than {@link SlabSupport#shouldOffset}: chain-of-blocks and
     * compound bed/double-half cases are intentionally excluded from anchoring v1.
     */
    public static boolean qualifiesForDirectAnchor(BlockView world, BlockPos pos, BlockState state) {
        if (state == null || state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        var block = state.getBlock();
        if (block instanceof SlabBlock) {
            return false;
        }
        if (block instanceof CarpetBlock || block instanceof PaleMossCarpetBlock) {
            return false;
        }
        if (SlabSupport.isThinTopLayer(state)) {
            return false;
        }
        if (block instanceof BlockEntityProvider) {
            return false;
        }
        if (state.contains(Properties.BED_PART)) {
            return false;
        }
        if (state.contains(Properties.DOUBLE_BLOCK_HALF)) {
            return false;
        }
        if (!state.isSolidBlock(world, pos)) {
            return false;
        }
        return SlabSupport.hasBottomSlabBelow(world, pos);
    }
}
