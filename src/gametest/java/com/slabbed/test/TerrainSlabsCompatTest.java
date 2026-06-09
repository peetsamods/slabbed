package com.slabbed.test;

import com.slabbed.compat.CompatHooks;
import com.slabbed.compat.CompatSlabSurfaceKind;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * Headless Terrain Slabs compat tests — exercise the named-surface direct-support path
 * against the {@link TerrainSlabsTestShim} {@code terrain_slabs:test_slab} (the gametest mod
 * provides "terrain_slabs", so the compat fires exactly as with the real mod).
 *
 * <p>The compat goal is PARITY: an object on a Terrain Slabs BOTTOM_LIKE surface must behave
 * exactly like the same object on a vanilla bottom slab. So most tests assert
 * {@code getYOffset(on TS) == getYOffset(on vanilla bottom slab)} rather than a hard-coded
 * value — that is the precise contract and is robust to per-category dy rules.
 */
public final class TerrainSlabsCompatTest {

    private static final double EPS = 1.0e-6;

    private static BlockState tsBottomSlab() {
        return TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static BlockState vanillaBottomSlab() {
        return Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static double dyOnSupport(ServerWorld w, BlockPos supportPos, BlockState support, BlockState object) {
        w.setBlockState(supportPos, support, Block.NOTIFY_LISTENERS);
        BlockPos objPos = supportPos.up();
        w.setBlockState(objPos, object, Block.NOTIFY_LISTENERS);
        return SlabSupport.getYOffset(w, objPos, w.getBlockState(objPos));
    }

    /** Asserts the object's dy on a TS surface equals its dy on a vanilla bottom slab. */
    private static void assertParity(TestContext ctx, BlockState object, String what) {
        ServerWorld w = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        double tsDy = dyOnSupport(w, origin.add(3, 2, 3), tsBottomSlab(), object);
        double vanillaDy = dyOnSupport(w, origin.add(6, 2, 3), vanillaBottomSlab(), object);
        ctx.assertTrue(Math.abs(tsDy - vanillaDy) <= EPS,
                what + ": dy on Terrain Slabs (" + tsDy + ") must match vanilla bottom slab (" + vanillaDy + ")");
        ctx.complete();
    }

    // 0. Shim sanity — the test slab classifies as BOTTOM_LIKE (proves the harness wiring).
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void shimClassifiesAsBottomLike(TestContext ctx) {
        CompatSlabSurfaceKind kind = CompatHooks.customSlabSurfaceKind(tsBottomSlab());
        ctx.assertTrue(kind == CompatSlabSurfaceKind.BOTTOM_LIKE,
                "shim terrain_slabs:test_slab[type=bottom] must classify BOTTOM_LIKE, got " + kind);
        ctx.complete();
    }

    // 1. PLACEMENT keystone — a TS bottom slab must be a placeable solid top + direct support
    //    surface (this is what lets lanterns/torches/etc. be PLACED on it).
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void tsSlabIsPlaceableSupport(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(slab, tsBottomSlab(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabSupport.isDirectObjectSupportSurface(w, slab, w.getBlockState(slab)),
                "TS bottom slab must be a direct object support surface");
        ctx.assertTrue(SlabSupport.canTreatAsSolidTopFace(w, slab),
                "TS bottom slab top must count as solid support (so objects can be placed on it)");
        ctx.complete();
    }

    // 2-5. dy parity per object category.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void fullBlockParity(TestContext ctx) { assertParity(ctx, Blocks.STONE.getDefaultState(), "full block (stone)"); }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void lanternParity(TestContext ctx) { assertParity(ctx, Blocks.LANTERN.getDefaultState(), "standing lantern"); }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void torchParity(TestContext ctx) { assertParity(ctx, Blocks.TORCH.getDefaultState(), "floor torch"); }

    // Redstone wire lowers -0.5 on a Terrain Slabs slab (parity with 1.21.11, and with a
    // vanilla bottom slab). Now owned by the directCustom lane (redstone is a non-solid
    // slab-sit subject). This pins the parity so a future lane change can't silently regress it.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void redstoneWireLowersOnTs(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(base, tsBottomSlab(), Block.NOTIFY_LISTENERS);
        // Pass the redstone state explicitly (the render path computes dy from the state, not
        // the placed world block — and redstone's strict survival check isn't the subject here).
        BlockState redstone = Blocks.REDSTONE_WIRE.getDefaultState();
        double dy = SlabSupport.getYOffset(w, base.up(), redstone);
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "redstone state on a Terrain Slabs slab should lower -0.5 (got " + dy + ")");
        ctx.complete();
    }

    // 6. Carpet uses the client-side ClientDy.dyFor path (gated on hasBottomSlabBelow), not
    //    getYOffset — so assert the server-side condition that path keys off.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void carpetSupportRecognized(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(slab, tsBottomSlab(), Block.NOTIFY_LISTENERS);
        BlockPos carpetPos = slab.up();
        w.setBlockState(carpetPos, Blocks.WHITE_CARPET.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabSupport.hasBottomSlabBelow(w, carpetPos),
                "carpet over a TS bottom slab must see a bottom-slab-equivalent below (ClientDy lowers it -0.5)");
        ctx.complete();
    }

    // ── Combining / mixed-slab compound (terrain + vanilla) ───────────────────────────
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

    // Contract #3: a vanilla BOTTOM slab on a Terrain Slabs slab combines flush (-0.5).
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void vanillaBottomSlabCombinesOnTs(TestContext ctx) {
        double dy = stackDy(ctx.getWorld(), ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3),
                tsBottomSlab(), vanillaSlab(SlabType.BOTTOM));
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "vanilla bottom slab on a Terrain Slabs slab should combine flush at -0.5, got " + dy);
        ctx.complete();
    }

    // Contract #5: a vanilla TOP slab on a Terrain Slabs slab compounds to -1.0.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void vanillaTopSlabCompoundsOnTs(TestContext ctx) {
        double dy = stackDy(ctx.getWorld(), ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3),
                tsBottomSlab(), vanillaSlab(SlabType.TOP));
        ctx.assertTrue(Math.abs(dy + 1.0) <= EPS,
                "vanilla TOP slab on a Terrain Slabs slab should compound to -1.0, got " + dy);
        ctx.complete();
    }

    // Contract #2: an object on a MIXED slab (vanilla bottom slab capping a TS slab) compounds to -1.0.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void objectOnMixedTsSlabCompounds(TestContext ctx) {
        double dy = stackDy(ctx.getWorld(), ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3),
                tsBottomSlab(), vanillaSlab(SlabType.BOTTOM), Blocks.LANTERN.getDefaultState());
        ctx.assertTrue(Math.abs(dy + 1.0) <= EPS,
                "lantern on a mixed (vanilla-on-terrain) slab should compound to -1.0, got " + dy);
        ctx.complete();
    }
}
