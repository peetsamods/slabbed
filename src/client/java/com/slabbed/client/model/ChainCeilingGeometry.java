package com.slabbed.client.model;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricModelManager;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/**
 * Alternate baked geometry for a Y-axis chain hanging under a slab CEILING (a TOP or DOUBLE slab
 * directly above it). The vanilla chain model stops at the cell boundary, so a chain raised +0.5 to
 * meet the slab's lowered underside leaves a gap to the chain below; this model extends the chain to
 * y=24 (an extra 0.5 block) so the column connects continuously — the "top chain shortened/extended
 * so chains chain fully" behaviour. Chains are a chainable that connects across the step; lanterns are
 * not emitted here, but their dy follows the same visible chain-column contract in {@code SlabSupport}.
 *
 * <p>Port of the proofed-but-unmerged 1.21.11 {@code fix/chains-*} branches (b95a742d + a56fc90b +
 * 57704624), adapted to the 26.x render path: emitted directly into {@code OffsetBlockStateModel#
 * emitQuads} (26.1.2 has no {@code BlockModelDyTranslateMixin}). Standalone model registered via the
 * Fabric extra-model API ({@link #KEY} / {@link #MODEL_ID}).
 */
public final class ChainCeilingGeometry {
    public static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath(Slabbed.MOD_ID, "block/chain_ceiling_support");
    public static final ExtraModelKey<BlockStateModel> KEY = ExtraModelKey.create(() -> "slabbed:chain_ceiling_support");

    private static volatile BlockStateModel model;

    private ChainCeilingGeometry() {
    }

    /** True for a Y-axis chain whose block directly above is a ceiling support (TOP/DOUBLE slab). */
    public static boolean usesAlternateGeometry(BlockGetter world, BlockPos pos, BlockState state) {
        return SlabSupport.isVerticalChainDirectlyUnderCeilingSupport(world, pos, state);
    }

    /**
     * Emits the extended chain geometry instead of the wrapped model when this chain hangs under a
     * slab ceiling. Returns true when it handled the emit (caller must then skip the normal path).
     */
    public static boolean emitIfPresent(FabricBlockStateModel fallback, QuadEmitter emitter,
                                        BlockAndTintGetter world, BlockPos pos, BlockState state,
                                        RandomSource random, Predicate<Direction> cullTest) {
        if (!usesAlternateGeometry(world, pos, state)) {
            return false;
        }
        BlockStateModel alternate = alternateModel();
        if (!(alternate instanceof FabricBlockStateModel fabric)) {
            return false;
        }
        fabric.emitQuads(emitter, world, pos, state, random, cullTest);
        return true;
    }

    private static BlockStateModel alternateModel() {
        BlockStateModel cached = model;
        if (cached != null) {
            return cached;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getModelManager() == null) {
            return null;
        }
        BlockStateModel baked = ((FabricModelManager) client.getModelManager()).getModel(KEY);
        if (baked != null) {
            model = baked;
        }
        return baked;
    }
}
