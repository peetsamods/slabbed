package com.slabbed.anchor;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * Per-level deep-dy consent storage behind one Forge capability.
 *
 * <p>The NeoForge line hangs the consent stamp off the level as a synchronized data attachment.
 * Forge 1.20.1 has neither, so the stamp lives here and reaches clients through
 * {@link SlabbedAnchorNetwork}. Absent storage still means "legacy save": this class never
 * fabricates a stamp on read.
 */
public final class SlabbedConsentStore {
    private static final String STAMP_KEY = "stamp";

    @Nullable
    private DeepDyConsentAttachment.Stamp stamp;

    /** The stored stamp, or null for a legacy save. */
    @Nullable
    public DeepDyConsentAttachment.Stamp stampOrNull() {
        return stamp;
    }

    public void putStamp(@Nullable DeepDyConsentAttachment.Stamp value) {
        this.stamp = value;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        DeepDyConsentAttachment.Stamp current = stamp;
        if (current != null) {
            tag.put(STAMP_KEY, current.serializedTag());
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag.contains(STAMP_KEY)) {
            Tag stored = tag.get(STAMP_KEY);
            stamp = stored == null ? null : DeepDyConsentAttachment.Stamp.fromTag(stored);
        } else {
            stamp = null;
        }
    }
}
