package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * BUG A (live 2026-08-06, recorder {@code eeac23d0-d632-4985-b540-5611d8a1fc4f}):
 * <b>thin top-layer blocks never lower — Maintainer's "placing too high".</b>
 *
 * <p><b>Live evidence.</b> {@code (219,-55,-34) minecraft:white_carpet visualDy=0.000} resting on
 * {@code (219,-56,-34) minecraft:stone visualDy=-0.500} — the carpet floats half a block above the
 * support it is lying on. Same at {@code (219,-55,-36)}, a carpet directly on a
 * {@code -0.500} {@code stone_slab}, where the recorder ALSO logged
 * {@code outlineMinY=-0.500 collisionMinY=-0.500} against {@code visualDy=0.000} — the dy triad
 * had split apart.
 *
 * <p><b>Root cause.</b> {@code SlabSupport.isThinTopLayer} — a pure CLASSNAME test
 * ({@code SnowBlock || CarpetBlock || PaleMossCarpetBlock}, added 2026-02-10 by {@code 8d3f105f})
 * — hard-excluded the whole family from lowering at three SUBJECT-side sites: {@code shouldOffset},
 * {@code getYOffsetInner}'s flush guard, and {@code isDirectCustomSlabSupportSubject}. That is the
 * shape Maintainer's binding law of 2026-08-06 outlaws: <i>"everything should be able to lower; no
 * exceptions"</i> — eligibility follows GEOMETRY, never a block-class allow-list.
 *
 * <p><b>The genuine hazard the exclusion was protecting, preserved BY BEHAVIOUR.</b> The DODO the
 * original guard (and its {@code PowderSnowBlock} sibling, {@code 135d125f}) actually closed is
 * <b>environment-deposited surface fill</b>: snow that WEATHER lays down across continuous terrain.
 * Half of it would sit at {@code -0.5} (over a slab) and half at {@code 0.0} (over full ground),
 * tearing a half-block step across a surface the player never placed and cannot align. Carpet and
 * pale moss carpet are never weather-deposited — they are player-placed decoration, and the
 * WYSIWYG law owns them. The new predicate {@code isEnvironmentDepositedSurfaceFill} keys on that
 * BEHAVIOUR ({@code Properties.LAYERS} — accumulates and melts in layers — plus powder snow, which
 * fills a whole cell of natural terrain), so the snow hazard stays closed while a carpet resting on
 * a lowered support follows it down. {@link #snowLayerOnLoweredSupportStaysFlush} and
 * {@link #powderSnowOnLoweredSupportStaysFlush} PIN that protection so it cannot be lost later.
 */
public final class ThinTopLayerLoweringTest {

    private static final double EPS = 1.0e-6;

    /**
     * RED — the exact live pair {@code (219,-55,-34)} / {@code (219,-56,-34)}: white carpet lying
     * on a stone block that itself renders {@code -0.5}. Read {@code 0.0} before the fix (the
     * carpet floated half a block above the stone it rests on).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void carpetOnLoweredFullBlockFollowsItDown(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos support = loweredStoneSupport(ctx, 1, 1);

        BlockPos subject = support.up();
        place(w, subject, Blocks.WHITE_CARPET.getDefaultState());
        double dy = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "white_carpet lying on a stone block that renders -0.5 must read -0.5, got " + dy
                        + " (live (219,-55,-34) visualDy=0.000 over (219,-56,-34) visualDy=-0.500: "
                        + "isThinTopLayer excludes the whole carpet/snow family from lowering on "
                        + "CLASSNAME, so the carpet floats half a block above the block it lies on "
                        + "— Maintainer: 'everything should be able to lower; no exceptions')");
        ctx.complete();
    }

    /**
     * RED — the second live cell {@code (219,-55,-36)}: white carpet directly on a bottom slab.
     * A bottom slab's top face is half a block below the grid ALWAYS (exclusion #13), so the
     * carpet seats there instead of floating in vanilla's half-block gap.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void carpetOnBottomSlabSeatsOnItsTopFace(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(4, 1, 1);
        place(w, ground, Blocks.STONE.getDefaultState());
        BlockPos slab = ground.up();
        place(w, slab, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));

        BlockPos subject = slab.up();
        place(w, subject, Blocks.WHITE_CARPET.getDefaultState());
        double dy = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "white_carpet resting directly on a bottom slab must seat on its top face at -0.5, "
                        + "got " + dy + " (live (219,-55,-36): visualDy=0.000 while the recorder's "
                        + "own outlineMinY/collisionMinY both read -0.500 — the dy triad split)");
        ctx.complete();
    }

    /**
     * RED — pale moss carpet is the same player-placed decoration and moves with carpet, or the
     * two thin decorations disagree with each other on the same support.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void paleMossCarpetOnLoweredFullBlockFollowsItDown(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos support = loweredStoneSupport(ctx, 1, 4);

        BlockPos subject = support.up();
        place(w, subject, Blocks.PALE_MOSS_CARPET.getDefaultState());
        double dy = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "pale_moss_carpet on a stone block that renders -0.5 must read -0.5, got " + dy);
        ctx.complete();
    }

    /**
     * HAZARD PIN — <b>the reason the exclusion existed, preserved by BEHAVIOUR.</b> A snow LAYER is
     * deposited and melted by weather across whole biomes, so lowering it wherever a slab happens
     * to lie beneath tears a half-block step across a surface the player never placed. It must stay
     * flush even on a support that is itself lowered. Green before AND after the fix.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void snowLayerOnLoweredSupportStaysFlush(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos support = loweredStoneSupport(ctx, 4, 4);

        BlockPos subject = support.up();
        BlockState snow = Blocks.SNOW.getDefaultState();
        ctx.assertTrue(snow.contains(Properties.LAYERS),
                "fixture: minecraft:snow must carry the LAYERS property — that accumulation "
                        + "BEHAVIOUR is what isEnvironmentDepositedSurfaceFill keys on");
        place(w, subject, snow);
        double dy = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(dy) <= EPS,
                "a weather-deposited snow LAYER must stay flush at 0.0 even over a lowered support, "
                        + "got " + dy + " — this is the snowy-terrain DODO the original "
                        + "isThinTopLayer exclusion (8d3f105f) and its powder-snow sibling "
                        + "(135d125f) closed, and it must stay closed");
        ctx.complete();
    }

    /**
     * HAZARD PIN — powder snow is a full cell of natural terrain fill and keeps its own explicit
     * guard. Same DODO, same protection. Green before AND after the fix.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void powderSnowOnLoweredSupportStaysFlush(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos support = loweredStoneSupport(ctx, 4, 1);

        BlockPos subject = support.up();
        place(w, subject, Blocks.POWDER_SNOW.getDefaultState());
        double dy = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(dy) <= EPS,
                "powder snow must stay flush at 0.0 over a lowered support, got " + dy
                        + " (135d125f: natural terrain fill — Terrain Slabs does not lower it "
                        + "either, and a half-block step across snowy terrain is the DODO)");
        ctx.complete();
    }

    /**
     * The third SUBJECT-side site, {@code isDirectCustomSlabSupportSubject}: a carpet on a Terrain
     * Slabs {@code BOTTOM_LIKE} surface seats on its half-height top face exactly as it now does on
     * a vanilla bottom slab. Moved off {@code isThinTopLayer} together with the other two sites —
     * leaving one behind would be the shared-predicate half-fix trap (carpet lowering on vanilla
     * slabs but floating on TS terrain, in Maintainer's own TS-enabled live setup).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void carpetOnTerrainSlabsSurfaceSeatsOnItsTopFace(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos surface = ctx.getAbsolutePos(BlockPos.ORIGIN).add(6, 2, 1);
        place(w, surface, TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState()
                .with(SlabBlock.TYPE, SlabType.BOTTOM));

        BlockPos subject = surface.up();
        place(w, subject, Blocks.WHITE_CARPET.getDefaultState());
        double dy = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "white_carpet on a Terrain Slabs BOTTOM_LIKE surface must seat at -0.5, got " + dy);
        ctx.complete();
    }

    /**
     * HAZARD PIN on the same TS lane — snow layers stay flush over Terrain Slabs terrain, which is
     * the exact surface the snowy-terrain DODO was reported against ({@code 135d125f}: "Terrain
     * Slabs likewise does not lower it").
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void snowLayerOnTerrainSlabsSurfaceStaysFlush(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos surface = ctx.getAbsolutePos(BlockPos.ORIGIN).add(6, 2, 4);
        place(w, surface, TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState()
                .with(SlabBlock.TYPE, SlabType.BOTTOM));

        BlockPos subject = surface.up();
        place(w, subject, Blocks.SNOW.getDefaultState());
        double dy = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(dy) <= EPS,
                "a weather-deposited snow LAYER must stay flush at 0.0 on a Terrain Slabs surface, "
                        + "got " + dy);
        ctx.complete();
    }

    // ------------------------------------------------------------------------

    /**
     * Builds {@code stone_slab(BOTTOM) / stone} at plot-relative {@code (x, z)} and returns the
     * stone, hard-asserting that it renders {@code -0.5}. This is the live
     * {@code (219,-57..-56,-34)} column: the support the carpet was floating above.
     */
    private BlockPos loweredStoneSupport(TestContext ctx, int x, int z) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(x, 1, z);
        place(w, slab, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));
        BlockPos support = slab.up();
        place(w, support, Blocks.STONE.getDefaultState());
        double supportDy = SlabSupport.getYOffset(w, support, w.getBlockState(support));
        ctx.assertTrue(Math.abs(supportDy + 0.5) <= EPS,
                "fixture: the stone support must itself render -0.5, got " + supportDy);
        return support;
    }

    private static void place(ServerWorld w, BlockPos pos, BlockState state) {
        w.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
    }
}
