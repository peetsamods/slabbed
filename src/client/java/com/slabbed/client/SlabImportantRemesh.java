package com.slabbed.client;

import com.slabbed.client.SlabGeometricRemeshScheduler.SectionBox;
import com.slabbed.mixin.client.ClientLevelLevelExtractorAccessor;
import com.slabbed.mixin.client.LevelExtractorImportantDirtyAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.extract.LevelExtractor;

/**
 * Single authority for issuing Slabbed's own client section re-mesh as an <b>IMPORTANT</b>
 * (near-immediate) rebuild, so both the geometric-remesh mixin and the compound-visible attachment
 * refresh dirty their {@link SectionBox} with the same priority.
 *
 * <p>See {@link SlabGeometricRemeshScheduler#REQUEST_IMPORTANT_REBUILD} and
 * {@link LevelExtractorImportantDirtyAccessor} for why {@code important == true} matters (it is decisive
 * under Sodium: {@code false} lands in Sodium's deferred, per-frame-budgeted queue — the ~30-second-snap
 * delay — while {@code true} reaches its near-immediate presentation path for a near-camera section).
 * Falls back to nothing extra on vanilla beyond a harmless {@code isDirtyFromPlayer = true}.
 *
 * <p>Iterates the box section-by-section calling the {@code important} 4-arg overload directly (the
 * public {@code setSectionRangeDirty} would re-hardcode {@code false}). The section counts are tiny (a
 * 3×3×3 box for the geometric/compound paths), so the explicit loop is cheap and keeps the priority
 * flag under Slabbed's control.
 */
@Environment(EnvType.CLIENT)
public final class SlabImportantRemesh {

    private SlabImportantRemesh() {
    }

    /**
     * Marks every section in {@code box} render-dirty on {@code level} with
     * {@code important = }{@link SlabGeometricRemeshScheduler#REQUEST_IMPORTANT_REBUILD}. {@code box}
     * fields are SECTION coordinates (already {@code >> 4}); do NOT pass block coordinates.
     */
    public static void dirtyImportant(ClientLevel level, SectionBox box) {
        if (level == null) {
            return;
        }
        LevelExtractor extractor = ((ClientLevelLevelExtractorAccessor) level).slabbed$levelExtractor();
        if (extractor == null) {
            return;
        }
        LevelExtractorImportantDirtyAccessor accessor = (LevelExtractorImportantDirtyAccessor) extractor;
        boolean important = SlabGeometricRemeshScheduler.REQUEST_IMPORTANT_REBUILD;
        for (int sx = box.minX(); sx <= box.maxX(); sx++) {
            for (int sy = box.minY(); sy <= box.maxY(); sy++) {
                for (int sz = box.minZ(); sz <= box.maxZ(); sz++) {
                    accessor.slabbed$setSectionDirtyImportant(sx, sy, sz, important);
                }
            }
        }
    }
}
