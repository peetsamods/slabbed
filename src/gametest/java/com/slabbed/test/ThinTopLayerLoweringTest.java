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
 * BUG A (live 2026-08-06):
 * <b>thin top-layer blocks never lower — the maintainer's "placing too high".</b>
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
 * shape the maintainer's binding law of 2026-08-06 outlaws: <i>"everything should be able to lower; no
 * exceptions"</i> — eligibility follows GEOMETRY, never a block-class allow-list.
 *
 * <p><b>2026-08-06, second ruling: the snow exclusion is gone too.</b> {@code 112d1449} kept a
 * narrowed successor, {@code isEnvironmentDepositedSurfaceFill} ({@code Properties.LAYERS} plus
 * {@code PowderSnowBlock}), to preserve the one hazard the original guards actually closed —
 * weather laying snow across whole biomes, half of it dropping {@code -0.5} over a buried slab.
 * the maintainer has since ruled against that exclusion as well ("Snow blocks are not lowering", recorder
 * {@code 0ba17cf0}: {@code (306,-58,-56) powder_snow dy=0.000} over a {@code -0.5}
 * {@code stone_slab}). Her law admits no exceptions, so the predicate is removed and snow lowers by
 * geometry like everything else. <b>Three cells in this class were INVERTED by that ruling</b> —
 * {@link #snowLayerOnLoweredSupportFollowsItDown},
 * {@link #powderSnowOnLoweredSupportFollowsItDown} and
 * {@link #snowLayerOnTerrainSlabsSurfaceSeatsOnItsTopFace} previously asserted the exact opposite.
 *
 * <p><b>What now carries the hazard.</b> Not a block list — the bounded column walks' L5
 * natural-terrain stop ({@code cur.isOpaqueFullCube()} in {@code hasSlabInColumn} /
 * {@code slabColumnYOffset}, added by {@code 8d1b42ef} AFTER the powder-snow exclusion and closing
 * the same hazard generally). Snow lying on grass/dirt/stone cannot see a slab buried below that
 * terrain at all. {@link #snowLayerOverNaturalTerrainStaysFlush} pins it. The residual reachable
 * case — Terrain Slabs, which makes half-height surfaces out of natural terrain world-wide — is
 * documented in internal-notes 1k for a live judgement.
 *
 * <p>{@code isThinTopLayer} survives, meaning only what its name says — a THICKNESS statement, used
 * only by the bounded column walks that must not transmit a support's top face up through a
 * 1/16-tall cell.
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
                        + "— maintainer ruling: 'everything should be able to lower; no exceptions')");
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
     * RED — <b>the maintainer's 2026-08-06 ruling reverses this cell.</b> It previously asserted the exact
     * opposite ({@code dy == 0.0}, "a weather-deposited snow LAYER must stay flush"), pinning the
     * categorical exclusion that {@code 8d3f105f}/{@code 135d125f} installed. She reports "Snow
     * blocks are not lowering" as a BUG, and her binding law admits no exceptions, so a snow layer
     * resting on a support that itself renders {@code -0.5} must follow it down like every other
     * block. The hazard the old exclusion guarded is now carried by
     * {@link #snowLayerOverNaturalTerrainStaysFlush} instead — geometry, not a block list.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void snowLayerOnLoweredSupportFollowsItDown(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos support = loweredStoneSupport(ctx, 4, 4);

        BlockPos subject = support.up();
        BlockState snow = Blocks.SNOW.getDefaultState();
        ctx.assertTrue(snow.contains(Properties.LAYERS),
                "fixture: minecraft:snow must carry the LAYERS property — that accumulation "
                        + "BEHAVIOUR is what the removed isEnvironmentDepositedSurfaceFill keyed on");
        place(w, subject, snow);
        double dy = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "a snow LAYER resting on a stone block that renders -0.5 must read -0.5, got " + dy
                        + " — isEnvironmentDepositedSurfaceFill (LAYERS + PowderSnowBlock) still "
                        + "hard-excludes snow from lowering on a PROPERTY, so it floats half a block "
                        + "above the block it lies on (the maintainer 2026-08-06: 'Snow blocks are not "
                        + "lowering'; 'everything should be able to lower; no exceptions')");

        // Same claim on the other arrangement: a snow layer lying directly on a bottom slab.
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(1, 2, 6);
        place(w, ctx.getAbsolutePos(BlockPos.ORIGIN).add(1, 1, 6), Blocks.STONE.getDefaultState());
        place(w, slab, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));
        BlockPos onSlab = slab.up();
        place(w, onSlab, snow);
        double slabDy = SlabSupport.getYOffset(w, onSlab, w.getBlockState(onSlab));
        ctx.assertTrue(Math.abs(slabDy + 0.5) <= EPS,
                "a snow LAYER lying directly on a bottom slab must seat on its top face at -0.5, "
                        + "got " + slabDy);
        ctx.complete();
    }

    /**
     * RED — <b>maintainer ruling reverses this cell too.</b> It previously asserted {@code dy == 0.0}
     * for powder snow on a lowered support. This is the recorder's own arrangement:
     * {@code (306,-58,-56) powder_snow dy=0.000} sitting on a {@code stone_slab} — the cell she
     * flagged. {@code getYOffset} short-circuited {@code PowderSnowBlock} to {@code 0.0} at its
     * very first line ({@code 135d125f}), before any geometry ran.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void powderSnowOnBottomSlabSeatsOnItsTopFace(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(4, 1, 1);
        place(w, ground, Blocks.STONE.getDefaultState());
        BlockPos slab = ground.up();
        place(w, slab, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));

        BlockPos subject = slab.up();
        place(w, subject, Blocks.POWDER_SNOW.getDefaultState());
        double dy = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "powder snow resting on a bottom slab must seat on its top face at -0.5, got " + dy
                        + " — getYOffset still short-circuits PowderSnowBlock to 0.0 (135d125f), "
                        + "the live (306,-58,-56) dy=0.000 cell the maintainer reported");
        ctx.complete();
    }

    /**
     * {@code minecraft:snow_block} — the THIRD snow id, and the one the maintainer's wording most literally
     * names. It is a plain opaque full cube with no {@code LAYERS} property, so neither
     * {@code isThinTopLayer} nor its successor ever touched it: it has always resolved exactly like
     * stone, and it still does.
     *
     * <p>The cell asserts EQUALITY WITH STONE in two arrangements rather than a hardcoded number,
     * because "like any other full block" is the whole claim — including the arrangement where a
     * full block stays FLUSH on top of other terrain, which is the opaque-full-cube world-hole
     * guard and emphatically not a snow rule. Green before AND after.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void snowBlockBehavesExactlyLikeAnyFullBlock(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockState snowBlock = Blocks.SNOW_BLOCK.getDefaultState();
        ctx.assertTrue(!snowBlock.contains(Properties.LAYERS),
                "fixture: minecraft:snow_block is the FULL CUBE, not the layered deposit");

        // (a) directly on a bottom slab — a full block seats on the slab's top face.
        BlockPos slabA = ctx.getAbsolutePos(BlockPos.ORIGIN).add(1, 1, 6);
        place(w, slabA, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));
        BlockPos slabB = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 1, 6);
        place(w, slabB, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));
        double snowOnSlab = dyOf(w, slabA.up(), snowBlock);
        double stoneOnSlab = dyOf(w, slabB.up(), Blocks.STONE.getDefaultState());
        ctx.assertTrue(Math.abs(snowOnSlab - stoneOnSlab) <= EPS,
                "snow_block on a bottom slab must resolve exactly like stone: stone=" + stoneOnSlab
                        + " snow_block=" + snowOnSlab);
        ctx.assertTrue(Math.abs(snowOnSlab + 0.5) <= EPS,
                "fixture: a full block directly on a bottom slab seats at -0.5, got " + snowOnSlab);

        // (b) on top of a full block that is itself lowered — terrain stays flush, snow included.
        BlockPos supportA = loweredStoneSupport(ctx, 5, 6);
        BlockPos supportB = loweredStoneSupport(ctx, 7, 6);
        double snowOnTerrain = dyOf(w, supportA.up(), snowBlock);
        double stoneOnTerrain = dyOf(w, supportB.up(), Blocks.STONE.getDefaultState());
        ctx.assertTrue(Math.abs(snowOnTerrain - stoneOnTerrain) <= EPS,
                "snow_block resting on terrain must resolve exactly like stone: stone="
                        + stoneOnTerrain + " snow_block=" + snowOnTerrain);
        ctx.complete();
    }

    /**
     * HAZARD PIN — <b>the replacement for the categorical exclusion.</b> The DODO the old block-list
     * guarded is snow that WEATHER lays across continuous terrain: if it lowered wherever a slab
     * happened to lie somewhere beneath, half a snowy biome would render {@code -0.5} and half
     * {@code 0.0}, a step through terrain the player never placed.
     *
     * <p>That case is closed STRUCTURALLY, not by naming snow: the bounded column walks
     * ({@code hasSlabInColumn} / {@code slabColumnYOffset}) stop dead on the first
     * {@code isOpaqueFullCube} below the subject — the L5 world-hole guard, added by
     * {@code 8d1b42ef} AFTER the powder-snow exclusion and closing the same hazard generally. Snow
     * lying on grass/dirt/stone therefore cannot see a slab buried under that terrain at all, no
     * matter how deep the slab is. Only snow resting DIRECTLY on a slab (or on a lowered placed
     * object) lowers — and there it is geometrically correct, seating on the surface it lies on
     * instead of floating in vanilla's half-block gap.
     *
     * <p>Green before AND after: this is the invariant that lets the exclusion go.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void snowLayerOverNaturalTerrainStaysFlush(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(6, 1, 4);
        place(w, slab, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));
        // Two cells of ordinary opaque terrain over the slab — exactly the shape the natural-terrain
        // stop exists for.
        place(w, slab.up(), Blocks.DIRT.getDefaultState());
        BlockPos ground = slab.up(2);
        place(w, ground, Blocks.GRASS_BLOCK.getDefaultState());

        BlockPos subject = ground.up();
        place(w, subject, Blocks.SNOW.getDefaultState());
        double dy = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(dy) <= EPS,
                "weather-deposited snow lying on natural terrain must stay flush at 0.0 even with a "
                        + "slab buried below that terrain, got " + dy + " — the opaque-full-cube "
                        + "natural-terrain stop (8d1b42ef) is what makes it safe to let snow lower "
                        + "by geometry; if this ever reads -0.5 the snowy-terrain DODO is back");
        ctx.complete();
    }

    /**
     * The third SUBJECT-side site, {@code isDirectCustomSlabSupportSubject}: a carpet on a Terrain
     * Slabs {@code BOTTOM_LIKE} surface seats on its half-height top face exactly as it now does on
     * a vanilla bottom slab. Moved off {@code isThinTopLayer} together with the other two sites —
     * leaving one behind would be the shared-predicate half-fix trap (carpet lowering on vanilla
     * slabs but floating on TS terrain, in the maintainer's own TS-enabled live setup).
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
     * RED — <b>maintainer ruling reverses this cell as well.</b> It previously asserted {@code dy ==
     * 0.0} for a snow layer on a Terrain Slabs {@code BOTTOM_LIKE} surface. It is the third
     * SUBJECT-side site ({@code isDirectCustomSlabSupportSubject}); leaving it behind would be the
     * shared-predicate half-fix trap, with snow lowering on vanilla slabs but floating on TS terrain
     * in the maintainer's own TS-enabled setup.
     *
     * <p>NOTE for the live pass: this is the ONE arrangement where the old DODO argument still has
     * teeth, because Terrain Slabs makes half-height surfaces out of natural terrain WORLD-WIDE. See
     * internal-notes entry 1k.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void snowLayerOnTerrainSlabsSurfaceSeatsOnItsTopFace(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos surface = ctx.getAbsolutePos(BlockPos.ORIGIN).add(6, 2, 4);
        place(w, surface, TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState()
                .with(SlabBlock.TYPE, SlabType.BOTTOM));

        BlockPos subject = surface.up();
        place(w, subject, Blocks.SNOW.getDefaultState());
        double dy = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "a snow LAYER on a Terrain Slabs BOTTOM_LIKE surface must seat at -0.5, got " + dy
                        + " — isDirectCustomSlabSupportSubject still excludes it by property");
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

    /** Places {@code state} at {@code pos} and returns its resolved dy. */
    private static double dyOf(ServerWorld w, BlockPos pos, BlockState state) {
        place(w, pos, state);
        return SlabSupport.getYOffset(w, pos, w.getBlockState(pos));
    }

    private static void place(ServerWorld w, BlockPos pos, BlockState state) {
        w.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
    }
}
