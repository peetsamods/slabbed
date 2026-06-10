package com.slabbed.test;

import com.slabbed.compat.CompatHooks;
import com.slabbed.compat.CompatSlabSurfaceKind;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Headless tests for the Terrain Slabs <b>COMPOUND -1.0</b> lane:
 *
 * <ul>
 *   <li>a vanilla BOTTOM slab placed on a Terrain Slabs slab combines flush at -0.5;</li>
 *   <li>a vanilla TOP slab placed on a Terrain Slabs slab compounds to -1.0;</li>
 *   <li>an object on a MIXED slab (a vanilla bottom slab capping a Terrain Slabs slab) compounds
 *       to -1.0;</li>
 *   <li>(control) an object on a plain vanilla bottom slab — with no Terrain Slabs surface below —
 *       lowers only -0.5, proving the compound is specifically the mixed TS+vanilla stack.</li>
 * </ul>
 *
 * <p>These run against the REAL Countered's Terrain Slabs mod ({@code terrainslabs}), loaded into
 * the headless {@code runGameTest} server via {@code modLocalRuntime}. The named surface used is
 * {@code terrainslabs:dirt_slab}, classified BOTTOM_LIKE by
 * {@link CompatHooks#customSlabSurfaceKind}.
 */
public final class TerrainSlabsCompoundDyTest {

    private static final double EPS = 1.0e-6;

    /** A real Terrain Slabs slab surface (classifies BOTTOM_LIKE when type = bottom). */
    private static Block tsSlabBlock() {
        return Registries.BLOCK.get(Identifier.of("terrainslabs", "dirt_slab"));
    }

    private static BlockState tsBottomSlab() {
        return tsSlabBlock().getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static BlockState vanillaSlab(SlabType type) {
        return Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, type);
    }

    /** Places layers bottom-up from base; returns getYOffset of the top (sitter) layer. */
    private static double stackDy(ServerWorld w, BlockPos base, BlockState... layers) {
        BlockPos p = base;
        for (BlockState layer : layers) {
            w.setBlockState(p, layer, Block.NOTIFY_LISTENERS);
            p = p.up();
        }
        BlockPos top = base.up(layers.length - 1);
        return SlabSupport.getYOffset(w, top, w.getBlockState(top));
    }

    // Harness sanity — the real terrainslabs:dirt_slab[type=bottom] classifies BOTTOM_LIKE.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void tsSlabClassifiesAsBottomLike(TestContext ctx) {
        Block ts = tsSlabBlock();
        ctx.assertTrue(ts instanceof SlabBlock,
                "terrainslabs:dirt_slab must resolve to a SlabBlock (got " + ts + ")");
        CompatSlabSurfaceKind kind = CompatHooks.customSlabSurfaceKind(tsBottomSlab());
        ctx.assertTrue(kind == CompatSlabSurfaceKind.BOTTOM_LIKE,
                "terrainslabs:dirt_slab[type=bottom] must classify BOTTOM_LIKE, got " + kind);
        ctx.complete();
    }

    // A vanilla BOTTOM slab on a Terrain Slabs slab combines flush at -0.5.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void vanillaBottomSlabCombinesOnTs(TestContext ctx) {
        double dy = stackDy(ctx.getWorld(), ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3),
                tsBottomSlab(), vanillaSlab(SlabType.BOTTOM));
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "vanilla bottom slab on a Terrain Slabs slab should combine flush at -0.5, got " + dy);
        ctx.complete();
    }

    // A vanilla TOP slab placed directly on a Terrain Slabs slab compounds to -1.0.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void vanillaTopSlabCompoundsOnTs(TestContext ctx) {
        double dy = stackDy(ctx.getWorld(), ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3),
                tsBottomSlab(), vanillaSlab(SlabType.TOP));
        ctx.assertTrue(Math.abs(dy + 1.0) <= EPS,
                "vanilla TOP slab on a Terrain Slabs slab should compound to -1.0, got " + dy);
        ctx.complete();
    }

    // Keystone: an object on a MIXED slab (vanilla bottom slab capping a TS slab) compounds to -1.0.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void objectOnMixedTsSlabCompounds(TestContext ctx) {
        double dy = stackDy(ctx.getWorld(), ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3),
                tsBottomSlab(), vanillaSlab(SlabType.BOTTOM), Blocks.LANTERN.getDefaultState());
        ctx.assertTrue(Math.abs(dy + 1.0) <= EPS,
                "lantern on a mixed (vanilla-on-terrain) slab should compound to -1.0, got " + dy);
        ctx.complete();
    }

    // BUG 1 fix: an object on a vanilla TOP slab capping a Terrain Slab compounds to -1.0 (was -0.5,
    // floating, because loweredBottomSlabSupportDy ignored TOP-slab supports). Mirrors the 1.21.1 fix.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void lanternOnVanillaTopSlabOnTsCompounds(TestContext ctx) {
        double dy = stackDy(ctx.getWorld(), ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3),
                tsBottomSlab(), vanillaSlab(SlabType.TOP), Blocks.LANTERN.getDefaultState());
        ctx.assertTrue(Math.abs(dy + 1.0) <= EPS,
                "lantern on a vanilla TOP slab capping a Terrain Slab should compound to -1.0 (BUG 1), got " + dy);
        ctx.complete();
    }

    // Control: a single vanilla bottom slab (no TS below) does NOT compound — only -0.5.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void objectOnPlainVanillaSlabDoesNotCompound(TestContext ctx) {
        double dy = stackDy(ctx.getWorld(), ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3),
                vanillaSlab(SlabType.BOTTOM), Blocks.LANTERN.getDefaultState());
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "lantern on a plain vanilla bottom slab should lower only -0.5, got " + dy);
        ctx.complete();
    }
}
