package com.slabbed.mixin.client;

import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@code LevelExtractor}'s private
 * {@code setSectionDirty(int x, int y, int z, boolean important)} overload so Slabbed's client re-mesh
 * paths can request an IMPORTANT (near-immediate) rebuild instead of the deferred default.
 *
 * <h2>Why this exists (the 30-second-snap delay)</h2>
 * In 26.2 all client section-dirty scheduling funnels through {@code LevelExtractor}. The public
 * entrypoints Slabbed was using — {@code ClientLevel.setSectionRangeDirty} → {@code
 * LevelExtractor.setSectionRangeDirty} → {@code setSectionDirty(x,y,z)} — hardcode the {@code important}
 * flag to {@code false} (see {@code LevelExtractor.setSectionDirty(III)}, which calls the 4-arg overload
 * with a constant {@code false}). On VANILLA that flag only distinguishes "dirty from player" ordering
 * and the section still rebuilds within a frame or two regardless, so it was invisible.
 *
 * <p>But under <b>Sodium</b> (present in the live-test profile), {@code LevelExtractor.setSectionDirty(III Z)}
 * is {@code @Overwrite}n to route straight into {@code SodiumWorldRenderer.scheduleRebuildForChunk(x,y,z,important)}
 * → {@code RenderSectionManager.scheduleRebuild(...)}. There the {@code important} flag is decisive:
 * only {@code important == true} AND a near-camera section produces {@code ChunkUpdateTypes.IMPORTANT},
 * which Sodium submits on its near-immediate presentation path; {@code important == false} lands in the
 * ordinary DEFERRED, per-frame-budgeted background rebuild queue. A geometric mesh-staleness re-mesh
 * (the {@code b94b48ae} / {@code 4df516ab} fix family) therefore sat in Sodium's deferred queue and only
 * flushed when a background worker eventually got to it — the ~30-second "snap" the project lead observed
 * live. The delay is entirely inside Sodium's queue, NOT inside Slabbed's own (fully synchronous)
 * scheduling code.
 *
 * <p>Routing Slabbed's own re-mesh through this {@code important == true} accessor is the minimal,
 * targeted "request a higher-priority rebuild" action available to a mod that must not hard-depend on
 * Sodium: it uses only vanilla's own already-present 4-arg overload (which Sodium happens to overwrite),
 * so it degrades to a harmless {@code isDirtyFromPlayer = true} on vanilla and to a near-immediate
 * rebuild on Sodium. It changes the priority of ONLY Slabbed's own dirty calls; no other caller's
 * scheduling is touched.
 *
 * <p>Sodium's overwrite of this overload is {@code private}, so this invoker dispatches (via the
 * mixin-merged method body) to Sodium's version when Sodium is loaded, and to vanilla's otherwise.
 */
@Mixin(LevelExtractor.class)
public interface LevelExtractorImportantDirtyAccessor {

    @Invoker("setSectionDirty")
    void slabbed$setSectionDirtyImportant(int x, int y, int z, boolean important);
}
