package com.slabbed.client.model;

import com.slabbed.Slabbed;
import com.slabbed.util.ChainBridgeTextureVariant;
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

import java.util.EnumMap;
import java.util.Map;
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
 * emitQuads} (26.1.2 has no {@code BlockModelDyTranslateMixin}). Standalone models registered via the
 * Fabric extra-model API, one per chain texture.
 *
 * <p><b>Per-chain-type texture (2026-07-06).</b> 26.2 has five chain textures — {@code iron_chain}
 * plus the four copper weather states ({@code copper}/{@code exposed}/{@code weathered}/{@code
 * oxidized}, each shared by its waxed variant). The bridge is emitted INSTEAD of the block's own
 * model, so a single hardcoded-{@code iron_chain} bridge made every non-iron chain under a slab
 * ceiling render with the plain iron texture (a copper chain "turned into" a plain chain, reverting
 * to copper when the slab was broken and the bridge lane deactivated — live-reported). Fixed by
 * baking one bridge model per texture ({@link ChainBridgeTextureVariant}, a pure server-loadable
 * selector) and selecting it from the chain block's registry id, so each chain type bridges with its
 * own texture. The dy is unchanged (still grid-height 0.0 for a bridged chain — the deliberate
 * chandelier-style ceiling-mount geometry, unchanged by this fix).
 */
public final class ChainCeilingGeometry {

    /** Per-texture bridge model: keyed off the pure {@link ChainBridgeTextureVariant} model path. */
    private static final Map<ChainBridgeTextureVariant, ExtraModelKey<BlockStateModel>> KEYS =
            new EnumMap<>(ChainBridgeTextureVariant.class);
    private static final Map<ChainBridgeTextureVariant, Identifier> MODEL_IDS =
            new EnumMap<>(ChainBridgeTextureVariant.class);
    private static final Map<ChainBridgeTextureVariant, BlockStateModel> BAKED =
            new EnumMap<>(ChainBridgeTextureVariant.class);

    static {
        for (ChainBridgeTextureVariant variant : ChainBridgeTextureVariant.values()) {
            String path = variant.modelPath();
            MODEL_IDS.put(variant, Identifier.fromNamespaceAndPath(Slabbed.MOD_ID, "block/" + path));
            KEYS.put(variant, ExtraModelKey.create(() -> "slabbed:" + path));
        }
    }

    /**
     * Backward-compatible aliases for the iron (default) bridge model — the names the model-loading
     * plugin used before the per-texture split.
     */
    public static final Identifier MODEL_ID = MODEL_IDS.get(ChainBridgeTextureVariant.IRON);
    public static final ExtraModelKey<BlockStateModel> KEY = KEYS.get(ChainBridgeTextureVariant.IRON);

    private ChainCeilingGeometry() {
    }

    /** The registered extra-model key for a given texture variant (used by the model-loading plugin). */
    public static ExtraModelKey<BlockStateModel> keyFor(ChainBridgeTextureVariant variant) {
        return KEYS.get(variant);
    }

    /** The model resource id for a given texture variant (used by the model-loading plugin). */
    public static Identifier modelIdFor(ChainBridgeTextureVariant variant) {
        return MODEL_IDS.get(variant);
    }

    /** All texture variants that must be registered as extra models. */
    public static ChainBridgeTextureVariant[] variants() {
        return ChainBridgeTextureVariant.values();
    }

    /** True when the frozen chain dy and its ceiling support select the extended bridge route. */
    public static boolean usesAlternateGeometry(BlockGetter world, BlockPos pos, BlockState state, double frozenDy) {
        return SlabSupport.usesCeilingBridgeGeometry(world, pos, state, frozenDy);
    }

    /**
     * Emits the extended chain geometry instead of the wrapped model when this chain hangs under a
     * slab ceiling. Returns true when it handled the emit (caller must then skip the normal path).
     */
    public static boolean emitIfPresent(FabricBlockStateModel fallback, QuadEmitter emitter,
                                        BlockAndTintGetter world, BlockPos pos, BlockState state,
                                        double frozenDy, RandomSource random, Predicate<Direction> cullTest) {
        if (!usesAlternateGeometry(world, pos, state, frozenDy)) {
            return false;
        }
        BlockStateModel alternate = alternateModel(ChainBridgeTextureVariant.forBlock(state));
        if (!(alternate instanceof FabricBlockStateModel fabric)) {
            return false;
        }
        fabric.emitQuads(emitter, world, pos, state, random, cullTest);
        return true;
    }

    private static BlockStateModel alternateModel(ChainBridgeTextureVariant variant) {
        BlockStateModel cached = BAKED.get(variant);
        if (cached != null) {
            return cached;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getModelManager() == null) {
            return null;
        }
        BlockStateModel baked = ((FabricModelManager) client.getModelManager()).getModel(KEYS.get(variant));
        if (baked != null) {
            BAKED.put(variant, baked);
        }
        return baked;
    }
}
