package com.slabbed.anchor;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-level deep-dy consent storage, persisted through {@link SavedData}.
 *
 * <p>This began life as a {@code Level} capability, mirroring the NeoForge line's level
 * attachment - and that was a latent data-loss bug: Forge 1.20.1 serializes CHUNK capabilities
 * through {@code ChunkSerializer}, but no engine path ever calls a {@code Level} capability's
 * serializer, so the stamp would have silently reset on every server restart. {@link SavedData}
 * is the mechanism vanilla itself uses for exactly this shape of per-level fact; the file rides
 * the level's own save cycle. Do not migrate this back onto a capability.
 *
 * <p>Absent storage still means "legacy save": this class never fabricates a stamp on read.
 */
public final class SlabbedConsentStore extends SavedData {
    private static final String STORAGE_NAME = "slabbed_deep_dy_consent";
    private static final String STAMP_KEY = "stamp";

    @Nullable
    private DeepDyConsentAttachment.Stamp stamp;

    private SlabbedConsentStore() {
    }

    /** The level's store, created empty on first use. Server side only, by construction. */
    public static SlabbedConsentStore of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                SlabbedConsentStore::load, SlabbedConsentStore::new, STORAGE_NAME);
    }

    /** The stored stamp, or null for a legacy save. */
    @Nullable
    public DeepDyConsentAttachment.Stamp stampOrNull() {
        return stamp;
    }

    public void putStamp(@Nullable DeepDyConsentAttachment.Stamp value) {
        this.stamp = value;
        // An absent stamp is a meaningful state (legacy save), so clearing must persist too.
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        DeepDyConsentAttachment.Stamp current = stamp;
        if (current != null) {
            tag.put(STAMP_KEY, current.serializedTag());
        }
        return tag;
    }

    private static SlabbedConsentStore load(CompoundTag tag) {
        SlabbedConsentStore store = new SlabbedConsentStore();
        if (tag.contains(STAMP_KEY)) {
            Tag stored = tag.get(STAMP_KEY);
            store.stamp = stored == null ? null : DeepDyConsentAttachment.Stamp.fromTag(stored);
        }
        return store;
    }
}
