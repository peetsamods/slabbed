package com.slabbed.dev;

import com.slabbed.anchor.DeepDyConsentAttachment;
import com.slabbed.anchor.SlabAnchorMarker;
import com.slabbed.anchor.SlabbedCapabilities;
import com.slabbed.anchor.SlabbedChunkStore;
import com.slabbed.anchor.SlabbedConsentStore;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Test-only reach into the capability seam.
 *
 * <p>The donor suite talks to per-chunk and per-level state through data-attachment handles
 * ({@code chunk.getExistingDataOrNull(TYPE)} and friends). Forge has no attachment registry, so
 * that state lives in capabilities and there is no handle to name. These helpers give the suite
 * the same three verbs against the capability instead, so each row keeps asserting what it was
 * written to assert.
 *
 * <p>This class lives in the gametest source set only. It must never be referenced from
 * production code and never reaches a shipped archive.
 */
public final class SlabbedTestAccess {

    private SlabbedTestAccess() {
    }

    /** True when the chunk carries the Slabbed store at all. */
    public static boolean hasStore(LevelChunk chunk) {
        return SlabbedCapabilities.chunkStore(chunk) != null;
    }

    /** The live placement map, or null when this chunk holds no authored heights. */
    @Nullable
    public static Long2ByteOpenHashMap placementFacts(LevelChunk chunk) {
        SlabbedChunkStore store = SlabbedCapabilities.chunkStore(chunk);
        return store == null ? null : store.placementDyOrNull();
    }

    public static void putPlacementFacts(LevelChunk chunk, Long2ByteOpenHashMap facts) {
        SlabbedChunkStore store = SlabbedCapabilities.chunkStore(chunk);
        if (store != null) {
            store.putPlacementMap(facts);
        }
    }

    public static void clearPlacementFacts(LevelChunk chunk) {
        SlabbedChunkStore store = SlabbedCapabilities.chunkStore(chunk);
        if (store != null) {
            store.removePlacementMap();
        }
    }

    /** The live marker bucket, or null when that marker has never been written here. */
    @Nullable
    public static LongOpenHashSet marker(LevelChunk chunk, SlabAnchorMarker marker) {
        SlabbedChunkStore store = SlabbedCapabilities.chunkStore(chunk);
        return store == null ? null : store.markerOrNull(marker);
    }

    public static void putMarker(LevelChunk chunk, SlabAnchorMarker marker, LongOpenHashSet set) {
        SlabbedChunkStore store = SlabbedCapabilities.chunkStore(chunk);
        if (store != null) {
            store.putMarker(marker, set);
        }
    }

    /** The store's own NBT, standing in for the donor's chunk-attachment serialization. */
    @Nullable
    public static CompoundTag saveStore(LevelChunk chunk) {
        SlabbedChunkStore store = SlabbedCapabilities.chunkStore(chunk);
        return store == null ? null : store.save();
    }

    public static void loadStore(LevelChunk chunk, CompoundTag tag) {
        SlabbedChunkStore store = SlabbedCapabilities.chunkStore(chunk);
        if (store != null) {
            store.load(tag);
        }
    }

    @Nullable
    public static DeepDyConsentAttachment.Stamp consentStamp(Level level) {
        SlabbedConsentStore store = SlabbedCapabilities.consentStore(level);
        return store == null ? null : store.stampOrNull();
    }

    public static void putConsentStamp(Level level, @Nullable DeepDyConsentAttachment.Stamp stamp) {
        SlabbedConsentStore store = SlabbedCapabilities.consentStore(level);
        if (store != null) {
            store.putStamp(stamp);
        }
    }

    public static void clearConsentStamp(Level level) {
        putConsentStamp(level, null);
    }
}
