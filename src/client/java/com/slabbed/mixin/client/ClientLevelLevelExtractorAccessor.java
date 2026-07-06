package com.slabbed.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@code ClientLevel}'s private {@code levelExtractor} field so Slabbed's client re-mesh paths
 * can reach {@link LevelExtractorImportantDirtyAccessor#slabbed$setSectionDirtyImportant} and request an
 * IMPORTANT (near-immediate) rebuild instead of the deferred default. See
 * {@link LevelExtractorImportantDirtyAccessor} for the full 30-second-snap-delay rationale.
 */
@Mixin(ClientLevel.class)
public interface ClientLevelLevelExtractorAccessor {

    @Accessor("levelExtractor")
    LevelExtractor slabbed$levelExtractor();
}
