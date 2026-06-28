package com.slabbed.client.model;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;

/**
 * Fabric/Yarn alternate baked geometry for a vertical chain hanging directly under a slab
 * ceiling support.
 *
 * <p>The standard 16px chain model rendered at dy=+0.5 opens a half-block gap at the bottom
 * (above a hanging lantern) and disagrees with the 0..1.5 outline/hitbox. This substitutes an
 * elongated chain model that spans y=0..24 (1.5 blocks) at dy=0, so render == outline.
 *
 * <p>Mirrors the live-confirmed NeoForge behaviour (tag
 * {@code save/neoforge-1-21-1-lantern-chain-live-confirmed}) adapted to the Fabric model API.
 * The elongated asset is registered via {@code ModelLoadingPlugin.Context.addModels(Identifier)}
 * and retrieved at render time via the Fabric-injected
 * {@link FabricBakedModelManager#getModel(Identifier)} overload on {@link BakedModelManager}.
 */
public final class ChainCeilingGeometry {
    /** Plain model identifier of the elongated chain asset (registered + retrieved by this id). */
    public static final Identifier MODEL_ID = Identifier.of(Slabbed.MOD_ID, "block/chain_ceiling_support");

    private ChainCeilingGeometry() {
    }

    /**
     * @return whether this position is a vertical chain sitting directly under a ceiling support,
     *         in which case the elongated alternate geometry should be emitted instead of the
     *         standard dy=+0.5 chain. Guarded against render-region out-of-bounds reads.
     */
    public static boolean usesAlternateGeometry(BlockRenderView world, BlockPos pos, BlockState state) {
        try {
            return SlabSupport.isVerticalChainDirectlyUnderCeilingSupport(world, pos, state);
        } catch (IndexOutOfBoundsException outsideRenderRegion) {
            return false;
        }
    }

    /**
     * @return the baked elongated chain model, or {@code null} if it has not been baked yet
     *         (e.g. before the first resource reload completes).
     */
    public static BakedModel bakedOrNull() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return null;
        }
        BakedModelManager manager = client.getBakedModelManager();
        if (manager == null) {
            return null;
        }
        // Fabric injects FabricBakedModelManager#getModel(Identifier) onto BakedModelManager,
        // which resolves plain Identifiers registered via Context#addModels(Identifier...).
        return ((FabricBakedModelManager) manager).getModel(MODEL_ID);
    }
}
