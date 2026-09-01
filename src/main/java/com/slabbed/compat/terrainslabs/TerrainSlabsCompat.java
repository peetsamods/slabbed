package com.slabbed.compat.terrainslabs;

import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import com.slabbed.compat.CompatSlabSurfaceKind;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

/**
 * Countered Terrain Slabs compatibility: subtractive-only. When the mod is present,
 * skip slab offsets for its blocks to avoid terrain/shape artifacts. Absent the mod,
 * this class is unreachable.
 */
public final class TerrainSlabsCompat {
    private TerrainSlabsCompat() {
    }

    public static final String MOD_ID = "terrain_slabs";
    public static final String LEGACY_MOD_ID = "terrainslabs";

    /**
     * Lazily latched. An early class-load can reach this gate before the mod list
     * exists (a mixin consumer touched during vanilla bootstrap); an answer taken
     * then must never be cached, or a present Terrain Slabs would stay permanently
     * invisible and its blocks would receive Slabbed offsets.
     */
    private static volatile Boolean loaded;

    public static boolean isLoaded() {
        Boolean latched = loaded;
        if (latched == null) {
            ModList modList = ModList.get();
            if (modList == null) {
                return false;
            }
            latched = modList.isLoaded(MOD_ID) || modList.isLoaded(LEGACY_MOD_ID);
            loaded = latched;
        }
        return latched;
    }

    /** Returns true if slab offsets should be skipped for this state. */
    public static boolean shouldSkipOffset(BlockState state) {
        if (!isLoaded()) {
            return false;
        }

        Block block = state.getBlock();
        // See SlabSupport.registryIdOf: the wrapped registry allocates an Optional per call.
        ResourceLocation id = block == null
                ? null : block.builtInRegistryHolder().key().location();
        return isTerrainSlabsId(id);
    }

    /**
     * Terrain Slabs supplies terrain-shaped slab models and culling behavior.
     * Keep those blocks out of Slabbed's generic support-source rules.
     */
    public static boolean shouldSkipSlabSupport(BlockState state) {
        return shouldSkipOffset(state);
    }

    /**
     * TS's own on-top data. Read by id rather than hardcoding its members: it is a datapack tag,
     * so a pack that adds to it must move this mirror with it or the two silently desync.
     */
    private static final TagKey<Block> ON_TOP_BLOCKS = TagKey.create(
            Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(MOD_ID, "on_top_blocks"));

    /**
     * True when Terrain Slabs positions this object itself, so Slabbed must not add its own
     * lowering on top of it. Without that deferral the two offsets compound: measured 2026-08-20
     * against the real mod, snow on a VANILLA bottom slab resolved -0.5 from Slabbed and -0.5
     * again from TS, sinking a full block.
     *
     * <p>This is deliberately RELATION-shaped rather than a per-state classifier. Both blocks in
     * that case are vanilla - {@code minecraft:snow} on {@code minecraft:stone_slab} - so no
     * namespace test on either one can ever see it, and widening
     * {@link #shouldSkipOffset(BlockState)} to try would reopen the anchor-widening regression
     * family. It mirrors TS's own gate: an on-top subject over a BOTTOM slab, reading two cells
     * down for the upper half of a double plant, since that is the cell TS itself consults.
     *
     * <p>Subtractive and inert when TS is absent, so uninstalling the mod restores Slabbed's own
     * lowering. Snow carries no placement fact - it resolves live on the geometric lane - so this
     * deferral has no permanence to undo either way.
     */
    public static boolean handlesObjectOffset(BlockGetter world, BlockPos pos, BlockState state) {
        if (!isLoaded() || world == null || pos == null || state == null) {
            return false;
        }
        // Defer only to a Terrain Slabs whose on-top DATA is actually loaded. The mod id alone is
        // not enough: the headless classifier shim claims that id to exercise classification, and
        // deferring to it would drop Slabbed's own lowering with nothing to replace it.
        if (BuiltInRegistries.BLOCK.getTag(ON_TOP_BLOCKS).isEmpty()) {
            return false;
        }
        if (!state.is(ON_TOP_BLOCKS) && !(state.getBlock() instanceof BushBlock)) {
            return false;
        }
        BlockPos supportPos = state.getBlock() instanceof DoublePlantBlock
                && state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
                ? pos.below(2)
                : pos.below();
        BlockState support = world.getBlockState(supportPos);
        return support.is(BlockTags.SLABS)
                && support.hasProperty(SlabBlock.TYPE)
                && support.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    /**
     * Returns the direct-only custom slab surface role for a proven Terrain Slabs state.
     * Classification trusts the id and the state shape - a shim claiming the mod id classifies
     * identically, which is what makes this lane headlessly provable; only DEFERRAL requires
     * the real mod's data (see {@link #handlesObjectOffset}).
     */
    public static CompatSlabSurfaceKind customSlabSurfaceKind(BlockState state) {
        if (!isLoaded() || state == null || !state.hasProperty(SlabBlock.TYPE)) {
            return CompatSlabSurfaceKind.NONE;
        }
        ResourceLocation id = com.slabbed.util.SlabSupport.registryIdOf(state);
        if (!isNamedCustomSlabSurface(id) || !state.getFluidState().isEmpty() || isSnowy(state)) {
            return CompatSlabSurfaceKind.NONE;
        }
        return switch (state.getValue(SlabBlock.TYPE)) {
            case BOTTOM -> CompatSlabSurfaceKind.BOTTOM_LIKE;
            case TOP -> CompatSlabSurfaceKind.TOP_LIKE;
            case DOUBLE -> CompatSlabSurfaceKind.DOUBLE_LIKE;
        };
    }

    private static boolean isNamedCustomSlabSurface(ResourceLocation id) {
        if (!isTerrainSlabsId(id)) {
            return false;
        }
        String path = id.getPath();
        return path.endsWith("_slab") || path.endsWith("_slab_bottom");
    }

    private static boolean isSnowy(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if ("snowy".equals(property.getName())) {
                Comparable<?> value = state.getValue(property);
                return "true".equals(String.valueOf(value));
            }
        }
        return false;
    }

    private static boolean isTerrainSlabsId(ResourceLocation id) {
        return id != null && (MOD_ID.equals(id.getNamespace()) || LEGACY_MOD_ID.equals(id.getNamespace()));
    }

}
