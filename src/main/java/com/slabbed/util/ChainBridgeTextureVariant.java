package com.slabbed.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pure, headlessly-testable selection of which chain-ceiling-bridge texture a given chain block uses.
 *
 * <p>The client emits an extended "bridge" model (see {@code ChainCeilingGeometry}) INSTEAD of a
 * Y-axis chain's own model when the chain hangs directly under a slab ceiling. That bridge must carry
 * the SAME texture as the chain itself, otherwise a non-iron chain (e.g. {@code waxed_copper_chain})
 * renders with the plain iron texture while bridged, then snaps back to its real texture when the slab
 * is removed and the bridge lane deactivates (the live-reported "copper chain turns into a plain
 * chain" bug). 26.2 has five chain textures — {@code iron_chain} and the four copper weather states,
 * each shared by its waxed variant — so this maps every chain block to one of five variants.
 *
 * <p>This class holds only the pure block-id → variant decision + the per-variant model-resource path,
 * with no client/render imports, so the mapping is loadable and assertable in a headless server
 * gametest (the render OUTCOME still needs a live client per lesson S7, but the SELECTION does not).
 */
public enum ChainBridgeTextureVariant {
    IRON("chain_ceiling_support", "minecraft:block/iron_chain"),
    COPPER("chain_ceiling_support_copper", "minecraft:block/copper_chain"),
    EXPOSED_COPPER("chain_ceiling_support_exposed_copper", "minecraft:block/exposed_copper_chain"),
    WEATHERED_COPPER("chain_ceiling_support_weathered_copper", "minecraft:block/weathered_copper_chain"),
    OXIDIZED_COPPER("chain_ceiling_support_oxidized_copper", "minecraft:block/oxidized_copper_chain");

    private final String modelPath;
    private final String expectedTexture;

    ChainBridgeTextureVariant(String modelPath, String expectedTexture) {
        this.modelPath = modelPath;
        this.expectedTexture = expectedTexture;
    }

    /** Model resource path under {@code slabbed:block/} (no namespace, no {@code block/} prefix). */
    public String modelPath() {
        return modelPath;
    }

    /** The vanilla texture id this variant's bridge model is expected to reference (test anchor). */
    public String expectedTexture() {
        return expectedTexture;
    }

    /**
     * Selects the bridge texture variant matching this chain block's own texture. Keyed on the
     * registry path; waxed variants map to their unwaxed texture. Anything unrecognised falls back to
     * {@link #IRON} so a novel/modded chain still bridges (with the plain texture) rather than failing.
     */
    public static ChainBridgeTextureVariant forBlock(BlockState state) {
        if (state == null) {
            return IRON;
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) {
            return IRON;
        }
        String path = id.getPath();
        if (path.contains("oxidized_copper_chain")) {
            return OXIDIZED_COPPER;
        }
        if (path.contains("weathered_copper_chain")) {
            return WEATHERED_COPPER;
        }
        if (path.contains("exposed_copper_chain")) {
            return EXPOSED_COPPER;
        }
        if (path.contains("copper_chain")) {
            return COPPER;
        }
        return IRON;
    }
}
