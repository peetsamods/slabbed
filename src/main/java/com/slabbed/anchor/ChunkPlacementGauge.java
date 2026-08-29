package com.slabbed.anchor;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * The {@code /slabdy chunk} gauge: how much of Fabric's per-attachment sync budget this chunk's
 * stored placement data is using, made visible BEFORE it is fatal.
 *
 * <p>Why this exists: the GH #36 failure mode is a cliff with no railing — a chunk silently
 * accumulates stored heights until one attachment's sync encoding crosses Fabric's 32,502-byte
 * ceiling, and then the chunk refuses to load, taking the area (and in the reported case the
 * server) with it. The compact codecs pushed that cliff far out; this gauge is the railing that
 * makes the remaining distance visible to the player standing in the chunk.
 *
 * <p>Bytes are MEASURED, not estimated: each synced attachment is encoded with its real production
 * codec into a throwaway buffer, plus the one-byte presence prefix Fabric counts. The number the
 * gauge prints is the number Fabric's ceiling is compared against. The band thresholds are
 * green &lt; 50%, yellow &lt; 75%, orange &lt; 90%, red ≥ 90% of the ceiling, applied to the
 * LARGEST single attachment — the limit is per attachment, not per chunk total, so the largest one
 * is the one that kills the chunk first.
 *
 * <p>Runs only inside a command {@code executes(...)} body. Nothing here is a tick hook, cache, or
 * listener; a full pass over a dense chunk's map plus one encode is command-invocation work.
 */
public final class ChunkPlacementGauge {

    /** Fabric's complete readable-attachment-bytes ceiling, including its presence prefix. */
    public static final int FABRIC_ATTACHMENT_MAX_DATA_BYTES = 32_502;

    private static final int BREAKDOWN_TOP_N = 6;

    private ChunkPlacementGauge() {
    }

    /** One synced attachment's measured footprint. */
    public record AttachmentUsage(String name, int entries, int bytes) {
    }

    /**
     * The formatted gauge report for {@code chunk}. Never null; a chunk with no stored data reports
     * an explicitly empty gauge rather than nothing, so "no data" is distinguishable from "command
     * broken".
     */
    public static List<String> report(LevelChunk chunk, RegistryAccess registryAccess) {
        List<AttachmentUsage> usages = new ArrayList<>();

        Long2DoubleOpenHashMap dyMap = chunk.getAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE);
        int dyEntries = dyMap == null ? 0 : dyMap.size();
        usages.add(new AttachmentUsage("placement heights", dyEntries,
                dyMap == null || dyMap.isEmpty()
                        ? 0
                        : measure(registryAccess, buf -> ChunkPositionDyMapPacketCodec.INSTANCE.encode(buf, dyMap))));

        addSetUsage(usages, chunk, registryAccess, "anchors", SlabAnchorAttachment.ANCHOR_TYPE);
        addSetUsage(usages, chunk, registryAccess, "frozen-flat", SlabAnchorAttachment.FROZEN_FLAT_TYPE);
        addSetUsage(usages, chunk, registryAccess, "carriers", SlabAnchorAttachment.LOWERED_SLAB_CARRIER_TYPE);
        addSetUsage(usages, chunk, registryAccess, "compound anchors",
                SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE);
        addSetUsage(usages, chunk, registryAccess, "compound side-lower",
                SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE);
        addSetUsage(usages, chunk, registryAccess, "compound side-upper",
                SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE);
        addSetUsage(usages, chunk, registryAccess, "compound side-double",
                SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE);
        addSetUsage(usages, chunk, registryAccess, "compound owner-top",
                SlabAnchorAttachment.COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE);

        AttachmentUsage largest = usages.stream()
                .max(Comparator.comparingInt(AttachmentUsage::bytes))
                .orElseThrow();

        List<String> lines = new ArrayList<>();
        BlockPos chunkOrigin = chunk.getPos().getWorldPosition();
        int chunkX = chunkOrigin.getX() >> 4;
        int chunkZ = chunkOrigin.getZ() >> 4;
        lines.add("[slabdy] chunk (" + chunkX + ", " + chunkZ + "): "
                + describe(largest) + " — " + band(largest.bytes()));

        StringBuilder others = new StringBuilder();
        for (AttachmentUsage usage : usages) {
            if (usage == largest || usage.entries() == 0) {
                continue;
            }
            if (others.length() > 0) {
                others.append(" · ");
            }
            others.append(usage.name()).append(": ").append(usage.entries());
        }
        if (others.length() > 0) {
            lines.add("[slabdy]   also " + others);
        }

        if (dyMap != null && !dyMap.isEmpty()) {
            lines.add("[slabdy]   " + breakdown(chunk, dyMap));
        }
        if (largest.entries() == 0) {
            lines.set(0, "[slabdy] chunk (" + chunkX + ", " + chunkZ
                    + "): no stored placement data — " + band(0));
        }
        // Keep the origin visible so "which chunk is this" needs no F3 archaeology.
        lines.add("[slabdy]   chunk origin " + chunkOrigin.getX() + ", " + chunkOrigin.getZ()
                + " (borders: F3+G)");
        return lines;
    }

    private static void addSetUsage(
            List<AttachmentUsage> usages,
            LevelChunk chunk,
            RegistryAccess registryAccess,
            String name,
            net.fabricmc.fabric.api.attachment.v1.AttachmentType<LongOpenHashSet> type) {
        LongOpenHashSet set = chunk.getAttached(type);
        usages.add(new AttachmentUsage(name, set == null ? 0 : set.size(),
                set == null || set.isEmpty()
                        ? 0
                        : measure(registryAccess, buf -> ChunkPositionSetPacketCodec.INSTANCE.encode(buf, set))));
    }

    private static int measure(RegistryAccess registryAccess, java.util.function.Consumer<RegistryFriendlyByteBuf> encoder) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);
        try {
            buf.writeBoolean(true); // Fabric's presence prefix — counted against the ceiling.
            encoder.accept(buf);
            return buf.readableBytes();
        } finally {
            buf.release();
        }
    }

    private static String describe(AttachmentUsage usage) {
        int percent = percentOfCeiling(usage.bytes());
        return usage.name() + ": " + usage.entries() + (usage.entries() == 1 ? " entry, " : " entries, ")
                + formatBytes(usage.bytes()) + " of " + formatBytes(FABRIC_ATTACHMENT_MAX_DATA_BYTES)
                + " sync budget (" + percent + "%)";
    }

    /** Band by the worst attachment: the per-attachment ceiling kills the chunk, not the total. */
    public static String band(int bytes) {
        int percent = percentOfCeiling(bytes);
        if (percent < 50) {
            return "GREEN";
        }
        if (percent < 75) {
            return "YELLOW";
        }
        if (percent < 90) {
            return "ORANGE";
        }
        return "RED";
    }

    private static int percentOfCeiling(int bytes) {
        return (int) Math.min(100L, (bytes * 100L) / FABRIC_ATTACHMENT_MAX_DATA_BYTES);
    }

    private static String formatBytes(int bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        return String.format("%.1f KB", bytes / 1024.0d);
    }

    /** Top block types carrying stored heights, so "what would resetting cost" has a concrete answer. */
    private static String breakdown(LevelChunk chunk, Long2DoubleOpenHashMap dyMap) {
        Map<String, Integer> byBlock = new LinkedHashMap<>();
        for (var iterator = dyMap.keySet().iterator(); iterator.hasNext(); ) {
            long packed = iterator.nextLong();
            BlockPos pos = BlockPos.of(packed);
            String name = chunk.getBlockState(pos).getBlock().getName().getString();
            byBlock.merge(name, 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(byBlock.entrySet());
        sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        StringBuilder out = new StringBuilder("heights by block: ");
        int shown = 0;
        for (Map.Entry<String, Integer> entry : sorted) {
            if (shown == BREAKDOWN_TOP_N) {
                out.append(" · +").append(sorted.size() - BREAKDOWN_TOP_N).append(" more types");
                break;
            }
            if (shown > 0) {
                out.append(" · ");
            }
            out.append(entry.getValue()).append(" ").append(entry.getKey());
            shown++;
        }
        return out.toString();
    }
}
