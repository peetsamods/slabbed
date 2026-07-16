package com.slabbed.compat;

import com.slabbed.compat.terrainslabs.TerrainSlabsCompat;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Central compat dispatch. All compat hooks must be subtractive-only and
 * unreachable when their target mod is not present.
 */
public final class CompatHooks {
    private static final String PONDER_LEVEL_CLASS_NAME =
            "net.createmod.ponder.api.level.PonderLevel";
    private static final ClassValue<Boolean> PONDER_RENDER_VIEW = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
                if (PONDER_LEVEL_CLASS_NAME.equals(cursor.getName())) {
                    return true;
                }
            }
            return false;
        }
    };

    private CompatHooks() {
    }

    /**
     * Returns true if compat requires skipping slab offset behavior for this state.
     */
    public static boolean shouldSkipOffset(BlockState state) {
        if (TerrainSlabsCompat.isLoaded()) {
            return TerrainSlabsCompat.shouldSkipOffset(state);
        }
        return false;
    }

    /**
     * Returns true for Create's temporary Ponder tutorial world.
     *
     * <p>Ponder owns the authored scene geometry and rebuilds it through the normal model
     * tessellator. Running Slabbed's live-world attachment and neighbour policy there is both
     * semantically wrong and extremely expensive. The name-based hierarchy check keeps Ponder an
     * optional dependency and also covers subclasses without matching unrelated Create worlds.
     */
    public static boolean shouldSkipOffsetView(Object view) {
        return view != null && PONDER_RENDER_VIEW.get(view.getClass());
    }

    /**
     * Returns true when a compat block should keep its own slab/support semantics
     * instead of becoming a Slabbed support source.
     */
    public static boolean shouldSkipSlabSupport(BlockState state) {
        if (TerrainSlabsCompat.isLoaded()) {
            return TerrainSlabsCompat.shouldSkipSlabSupport(state);
        }
        return false;
    }
}
