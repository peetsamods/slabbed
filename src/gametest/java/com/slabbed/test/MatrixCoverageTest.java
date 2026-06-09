package com.slabbed.test;

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
 * Comprehensive combined-slab / placement MATRIX of headless dy gametests — the "verify the FULL
 * combined-slab chain (every ordering + stacked things + 3-high)" rule, made executable.
 *
 * <p>Every assert pins the dy that {@link SlabSupport#getYOffset} actually returns today, derived
 * from the documented contract:
 * <ul>
 *   <li>object on a BOTTOM_LIKE surface (vanilla or Terrain Slabs) = -0.5;</li>
 *   <li>object on a TOP_LIKE / DOUBLE_LIKE surface = 0 (full-height support);</li>
 *   <li>a Terrain Slabs slab AS AN OBJECT is never lowered (skip-offset) = 0;</li>
 *   <li>compound (object on a vanilla-bottom-slab capping a TS slab) = -1.0 for the object
 *       categories that compound (lantern, functional/block-entity full blocks, vanilla TOP slab).</li>
 * </ul>
 *
 * <p>Where the documented "naive" expectation disagreed with the actual value, the divergence is a
 * KNOWN SURPRISE (skip-offset asymmetry, or ordinary-full-block non-compounding) and is called out
 * in the comment above the test — the assert pins the ACTUAL value so the suite stays an honest
 * green tripwire that will fire the day the behavior changes.
 */
public final class MatrixCoverageTest {

    private static final double EPS = 1.0e-6;

    private static BlockState vSlab(SlabType t) { return Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, t); }
    private static BlockState tsSlab(SlabType t) { return TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState().with(SlabBlock.TYPE, t); }

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

    private static BlockPos origin(TestContext ctx) { return ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3); }

    private static void assertDy(TestContext ctx, double actual, double expected, String what) {
        ctx.assertTrue(Math.abs(actual - expected) <= EPS,
                what + ": expected dy " + expected + " got " + actual);
    }

    // ─────────────────────────────────────────────────────────────────────────────────────
    // A. Object on a SINGLE slab support — every object × every slab type × {vanilla, TS}.
    //    BOTTOM_LIKE => -0.5; TOP_LIKE/DOUBLE_LIKE => 0.
    // ─────────────────────────────────────────────────────────────────────────────────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void stoneOnVanillaBottom(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), vSlab(SlabType.BOTTOM), Blocks.STONE.getDefaultState()), -0.5, "stone on vanilla bottom");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void stoneOnVanillaTop(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), vSlab(SlabType.TOP), Blocks.STONE.getDefaultState()), 0.0, "stone on vanilla top");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void stoneOnVanillaDouble(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), vSlab(SlabType.DOUBLE), Blocks.STONE.getDefaultState()), 0.0, "stone on vanilla double");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void stoneOnTsBottom(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), tsSlab(SlabType.BOTTOM), Blocks.STONE.getDefaultState()), -0.5, "stone on TS bottom");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void stoneOnTsTop(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), tsSlab(SlabType.TOP), Blocks.STONE.getDefaultState()), 0.0, "stone on TS top");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void stoneOnTsDouble(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), tsSlab(SlabType.DOUBLE), Blocks.STONE.getDefaultState()), 0.0, "stone on TS double");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void lanternOnVanillaBottom(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), vSlab(SlabType.BOTTOM), Blocks.LANTERN.getDefaultState()), -0.5, "lantern on vanilla bottom");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void lanternOnVanillaTop(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), vSlab(SlabType.TOP), Blocks.LANTERN.getDefaultState()), 0.0, "lantern on vanilla top");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void lanternOnTsBottom(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), tsSlab(SlabType.BOTTOM), Blocks.LANTERN.getDefaultState()), -0.5, "lantern on TS bottom");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void lanternOnTsTop(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), tsSlab(SlabType.TOP), Blocks.LANTERN.getDefaultState()), 0.0, "lantern on TS top");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void lanternOnTsDouble(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), tsSlab(SlabType.DOUBLE), Blocks.LANTERN.getDefaultState()), 0.0, "lantern on TS double");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void redstoneOnVanillaBottom(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), vSlab(SlabType.BOTTOM), Blocks.REDSTONE_WIRE.getDefaultState()), -0.5, "redstone on vanilla bottom");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void redstoneOnTsBottom(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), tsSlab(SlabType.BOTTOM), Blocks.REDSTONE_WIRE.getDefaultState()), -0.5, "redstone on TS bottom");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void redstoneOnTsTopNotLowered(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), tsSlab(SlabType.TOP), Blocks.REDSTONE_WIRE.getDefaultState()), 0.0, "redstone on TS top");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void fenceOnVanillaBottom(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), vSlab(SlabType.BOTTOM), Blocks.OAK_FENCE.getDefaultState()), -0.5, "fence on vanilla bottom");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void fenceOnTsBottom(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), tsSlab(SlabType.BOTTOM), Blocks.OAK_FENCE.getDefaultState()), -0.5, "fence on TS bottom");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void fenceOnTsTopNotLowered(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), tsSlab(SlabType.TOP), Blocks.OAK_FENCE.getDefaultState()), 0.0, "fence on TS top");
        ctx.complete();
    }

    // ─────────────────────────────────────────────────────────────────────────────────────
    // B. Slab AS OBJECT on a slab support.
    //    SURPRISE: a vanilla bottom slab on a VANILLA bottom slab is NOT lowered (0) — it merges
    //    into the support's lane — but the SAME vanilla bottom slab on a TS bottom slab IS lowered
    //    (-0.5). Likewise vanilla TOP slab: 0 on vanilla bottom, -1.0 on TS bottom. This asymmetry
    //    is the skip-offset rule (TS slabs are never themselves lowered, so a vanilla slab capping
    //    one is treated as the lowering object instead of a merge). Pinned, not "fixed".
    // ─────────────────────────────────────────────────────────────────────────────────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void vanillaBottomSlabOnVanillaBottom_notLowered(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), vSlab(SlabType.BOTTOM), vSlab(SlabType.BOTTOM)), 0.0,
                "SURPRISE: vanilla bottom slab on vanilla bottom slab merges (dy 0)");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void vanillaBottomSlabOnTsBottom_lowered(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), tsSlab(SlabType.BOTTOM), vSlab(SlabType.BOTTOM)), -0.5,
                "vanilla bottom slab on TS bottom slab combines flush (-0.5)");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void vanillaBottomSlabOnTsTop_notLowered(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), tsSlab(SlabType.TOP), vSlab(SlabType.BOTTOM)), 0.0,
                "vanilla bottom slab on TS TOP_LIKE (full-height support) = 0");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void vanillaTopSlabOnVanillaBottom_notLowered(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), vSlab(SlabType.BOTTOM), vSlab(SlabType.TOP)), 0.0,
                "SURPRISE: vanilla TOP slab on vanilla bottom slab not lowered (dy 0)");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void vanillaTopSlabOnTsBottom_compounds(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), tsSlab(SlabType.BOTTOM), vSlab(SlabType.TOP)), -1.0,
                "vanilla TOP slab on TS bottom slab compounds to -1.0");
        ctx.complete();
    }

    // ─────────────────────────────────────────────────────────────────────────────────────
    // C. Terrain Slabs slab AS OBJECT is never lowered (skip-offset) — dy 0 on any support.
    // ─────────────────────────────────────────────────────────────────────────────────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void tsBottomSlabObjectOnVanillaBottom_skipOffset(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), vSlab(SlabType.BOTTOM), tsSlab(SlabType.BOTTOM)), 0.0,
                "TS bottom slab AS OBJECT on a vanilla bottom slab stays dy 0 (skip-offset)");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void tsTopSlabObjectOnVanillaBottom_skipOffset(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), vSlab(SlabType.BOTTOM), tsSlab(SlabType.TOP)), 0.0,
                "TS top slab AS OBJECT on a vanilla bottom slab stays dy 0 (skip-offset)");
        ctx.complete();
    }

    // ─────────────────────────────────────────────────────────────────────────────────────
    // D. Compound / mixed (vanilla-bottom-slab capping a TS slab), then object on top.
    //    Lantern & functional/block-entity full blocks compound to -1.0.
    //    SURPRISE: a plain (non-block-entity) full block like STONE does NOT compound — it lowers
    //    -0.5 once. Only block-entity / functional full blocks (crafting table, furnace, chest, …)
    //    get the extra -0.5 compound. Pinned.
    // ─────────────────────────────────────────────────────────────────────────────────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void lanternOnMixedCompounds(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), tsSlab(SlabType.BOTTOM), vSlab(SlabType.BOTTOM), Blocks.LANTERN.getDefaultState()), -1.0,
                "lantern on a mixed (vanilla-on-TS) slab compounds to -1.0");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void craftingTableOnMixedCompounds(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), tsSlab(SlabType.BOTTOM), vSlab(SlabType.BOTTOM), Blocks.CRAFTING_TABLE.getDefaultState()), -1.0,
                "crafting table on a mixed slab compounds to -1.0");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void furnaceOnMixedCompounds(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), tsSlab(SlabType.BOTTOM), vSlab(SlabType.BOTTOM), Blocks.FURNACE.getDefaultState()), -1.0,
                "furnace on a mixed slab compounds to -1.0");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void plainStoneOnMixedDoesNotCompound(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), tsSlab(SlabType.BOTTOM), vSlab(SlabType.BOTTOM), Blocks.STONE.getDefaultState()), -0.5,
                "SURPRISE: plain stone (no block entity) on a mixed slab does NOT compound, stays -0.5");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void craftingTableOnSingleTsBottom_lowersOnce(TestContext ctx) {
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), tsSlab(SlabType.BOTTOM), Blocks.CRAFTING_TABLE.getDefaultState()), -0.5,
                "crafting table on a single TS bottom slab lowers -0.5 (no compound)");
        ctx.complete();
    }

    // ─────────────────────────────────────────────────────────────────────────────────────
    // E. 3-high stacks — the top sitter must continue to inherit the bottom slab's lowering.
    // ─────────────────────────────────────────────────────────────────────────────────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void threeHighTopStoneOnTsBottom(TestContext ctx) {
        BlockState stone = Blocks.STONE.getDefaultState();
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), tsSlab(SlabType.BOTTOM), stone, stone), -0.5,
                "top stone of a 3-high stack on a TS bottom slab is -0.5");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void threeHighTopStoneOnVanillaBottom(TestContext ctx) {
        BlockState stone = Blocks.STONE.getDefaultState();
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), vSlab(SlabType.BOTTOM), stone, stone), -0.5,
                "top stone of a 3-high stack on a vanilla bottom slab is -0.5");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void fourHighSlabCapOnTsStack(TestContext ctx) {
        BlockState stone = Blocks.STONE.getDefaultState();
        assertDy(ctx, stackDy(ctx.getWorld(), origin(ctx), tsSlab(SlabType.BOTTOM), stone, stone, vSlab(SlabType.BOTTOM)), -0.5,
                "vanilla bottom slab capping a 3-high TS-lowered stack is flush at -0.5 (no DODO)");
        ctx.complete();
    }

    // ─────────────────────────────────────────────────────────────────────────────────────
    // F. Lowered-carrier SIDE lane — a side slab beside a lowered stack must inherit the lowering.
    // ─────────────────────────────────────────────────────────────────────────────────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void sideSlabBesideTsLoweredStack(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = origin(ctx);
        w.setBlockState(base, tsSlab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        w.setBlockState(base.up(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(base.up(2), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos side = base.up(2).east();
        w.setBlockState(side, vSlab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        assertDy(ctx, SlabSupport.getYOffset(w, side, w.getBlockState(side)), -0.5,
                "side slab beside a TS-lowered 2-stack inherits the lowering (-0.5)");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void sideSlabBesideVanillaLoweredStack(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = origin(ctx);
        w.setBlockState(base, vSlab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        w.setBlockState(base.up(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(base.up(2), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos side = base.up(2).east();
        w.setBlockState(side, vSlab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        assertDy(ctx, SlabSupport.getYOffset(w, side, w.getBlockState(side)), -0.5,
                "side slab beside a vanilla-lowered 2-stack inherits the lowering (-0.5)");
        ctx.complete();
    }

    // ─────────────────────────────────────────────────────────────────────────────────────
    // G. Stepped connecting-block neighbors (fence/wall/pane) — connection breaks across a step,
    //    but two same-height connectors stay joined.
    // ─────────────────────────────────────────────────────────────────────────────────────

    private static boolean steppedLoweredVsGround(ServerWorld w, BlockPos o, BlockState connector) {
        w.setBlockState(o, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos ground = o.up();
        w.setBlockState(ground, connector, Block.NOTIFY_LISTENERS);
        BlockPos tsBase = o.east();
        w.setBlockState(tsBase, tsSlab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        BlockPos lowered = tsBase.up();
        w.setBlockState(lowered, connector, Block.NOTIFY_LISTENERS);
        return SlabSupport.isSteppedConnectingNeighbor(w, ground, w.getBlockState(ground), lowered, w.getBlockState(lowered));
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void fenceStepsAcrossLoweredNeighbor(TestContext ctx) {
        ctx.assertTrue(steppedLoweredVsGround(ctx.getWorld(), origin(ctx), Blocks.OAK_FENCE.getDefaultState()),
                "ground fence vs fence lowered onto a TS slab must be a stepped pair");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void wallStepsAcrossLoweredNeighbor(TestContext ctx) {
        ctx.assertTrue(steppedLoweredVsGround(ctx.getWorld(), origin(ctx), Blocks.COBBLESTONE_WALL.getDefaultState()),
                "ground wall vs wall lowered onto a TS slab must be a stepped pair");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void paneStepsAcrossLoweredNeighbor(TestContext ctx) {
        ctx.assertTrue(steppedLoweredVsGround(ctx.getWorld(), origin(ctx), Blocks.GLASS_PANE.getDefaultState()),
                "ground pane vs pane lowered onto a TS slab must be a stepped pair");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void twoLoweredFencesNotStepped(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = origin(ctx);
        w.setBlockState(base, tsSlab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        BlockPos f1 = base.up();
        w.setBlockState(f1, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos base2 = base.east();
        w.setBlockState(base2, tsSlab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        BlockPos f2 = base2.up();
        w.setBlockState(f2, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(!SlabSupport.isSteppedConnectingNeighbor(w, f1, w.getBlockState(f1), f2, w.getBlockState(f2)),
                "two fences both lowered onto TS slabs (same Y) must NOT be a stepped pair");
        ctx.complete();
    }
}
