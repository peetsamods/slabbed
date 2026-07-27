package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import javax.annotation.Nullable;

/**
 * DIAGNOSTIC — Phase I §A follow-up. Confirms or refutes the suspected exponential blowup in the
 * lowered-carrier derivation cycle.
 *
 * <p>Phase I audit found this closed cycle in {@link SlabSupport}:
 * {@code isLoweredCarrier (:1819) -> isAdjacentSideSlabLowered (:1842) -> hasLoweredSlabLaneSupport
 * (:2319) -> hasExplicitLoweredSlabLaneAuthority (:2273) -> hasLoweredCarrierBelow (:2290) ->
 * isLoweredCarrier (:2200)}. The {@code depth} argument decrements only on the vertical below-walk
 * ({@code :1846}), never on the lateral hops, so each lateral re-entry restarts a fresh bounded
 * search with a fresh visited set.
 *
 * <p>RESULT: CONFIRMED exponential at base 2. Measured curve, run-to-run identical and unchanged
 * after relocating the fixture: 22 / 62 / 143 / 304 / 625 / 1266 / 2547 / 5108 / 10229 / 20470 /
 * 40951 / 81912 block reads at heights 1..12, with the ratio locking at exactly x2.0 from height 6
 * and never falling off. Extrapolated: ~21M reads at height 20, ~335M at height 24.
 *
 * <p>FIXED: the per-query memo in {@link SlabSupport} ({@code DY_QUERY_MEMO}) collapses the walk
 * to quadratic — 22 / 144 / 588 / 1352 at heights 1/4/8/12, second differences constant. This test
 * now runs GREEN against {@link #READ_BUDGET_AT_MAX_HEIGHT} and stands as the regression guard.
 *
 * <p>NOTE ON THE ORIGINAL ESTIMATE. The scope document modelled this as ~17x per layer
 * (1,455 / 24,624 / 417,596 / 7,094,362 at heights 2/3/4/5) from a paper trace that was never run.
 * Measurement puts those at 62 / 143 / 304 / 625 -- the model overstated height 4 by ~1,400x. The
 * mechanism was real; the arithmetic was not. Base 2 is still exponential, so the conclusion holds
 * and only the freeze threshold moved.
 *
 * <p>MEASUREMENT NOTE. An earlier revision of this test timed the query with {@code System.nanoTime}
 * and produced a non-monotonic, noise-dominated curve (15us / 44us / 4us / 142us) whose worst ratio
 * was almost certainly a JIT or GC artifact. Wall-clock at microsecond scale cannot answer this
 * question. This revision counts {@code getBlockState} calls through a delegating
 * {@link BlockGetter}, which is exact, deterministic, run-to-run stable, and directly comparable to
 * the modelled figures above.
 *
 * <p>The fixture is identical in shape at every height and the resulting {@code dy} is asserted
 * equal across heights, so the same code path is measured each time.
 *
 * <p>This test changes no production behaviour. It measures existing behaviour only.
 */
@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class ForgeLoweredCarrierRecursionBlowupGameTest {

    /** Heights probed. Twelve is enough to show the ratio locking at x2.0 and holding. */
    private static final int MAX_HEIGHT = 12;

    /** Abort if a single query exceeds this many reads, rather than hanging the suite. */
    private static final long ABORT_READS = 20_000_000L;

    /**
     * Read budget for a single query at {@link #MAX_HEIGHT}. Linear growth from the measured
     * height-1 cost of 22 reads would put height 12 near 264; mild polynomial growth near 1,000.
     * Measured actual is 81,912 -- clean exponential at x2.0 per layer. This budget is deliberately
     * generous so it fails only on genuinely exponential behaviour, and is the RED that a fix must
     * turn GREEN.
     */
    private static final long READ_BUDGET_AT_MAX_HEIGHT = 5_000L;

    /** Fixtures are spaced well apart so one height cannot arm the next height's lateral lanes. */
    private static final int FIXTURE_SPACING = 6;

    /**
     * Dedicated z-lane for this diagnostic. The gametest world is SHARED: sibling tests build at
     * z=1..2, and an earlier revision of this test spread fixtures across x=32..98 at z=2 and
     * destroyed two of them. Keep this lane clear of every other fixture.
     */
    private static final int FIXTURE_Z_LANE = 10;

    @GameTest(template = "empty")
    public void loweredCarrierDerivationDoesNotGrowExponentiallyWithPillarHeight(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        try {
            measureAndEvaluate(ctx, world);
        } finally {
            // STRIKE THE SET. The gametest world is shared and PERSISTS across runs, so scenery
            // left standing here outlives this test and this run. This diagnostic's own history
            // proves the hazard twice: its first revision trampled two sibling fixtures live, and
            // stale per-chunk markers under layout-shifted plots later flipped a sibling matrix
            // row across runs. Restore every touched block to air even when the measurement
            // aborts or asserts.
            for (int height = 1; height <= MAX_HEIGHT; height++) {
                clearRegion(world, fixtureBase(ctx, height), height);
            }
        }
    }

    /** One fixture origin per height, all on the dedicated z-lane. */
    private static BlockPos fixtureBase(GameTestHelper ctx, int height) {
        return ctx.absolutePos(new BlockPos(2 + (height * FIXTURE_SPACING), 3, FIXTURE_Z_LANE));
    }

    private static void measureAndEvaluate(GameTestHelper ctx, ServerLevel world) {
        long[] reads = new long[MAX_HEIGHT + 1];
        double[] dys = new double[MAX_HEIGHT + 1];
        boolean aborted = false;
        int abortedAt = -1;

        for (int height = 1; height <= MAX_HEIGHT; height++) {
            BlockPos base = fixtureBase(ctx, height);
            clearRegion(world, base, height);
            buildPillarOverAir(world, base, height);

            BlockPos probe = base.above(height - 1);
            BlockState probeState = world.getBlockState(probe);

            CountingBlockGetter counter = new CountingBlockGetter(world, ABORT_READS);
            double dy;
            try {
                dy = SlabSupport.getYOffset(counter, probe, probeState);
            } catch (ReadBudgetExceeded e) {
                reads[height] = e.reads;
                aborted = true;
                abortedAt = height;
                Slabbed.LOGGER.info("[RECURSION_BLOWUP] height={} ABORTED after {} block reads",
                        height, e.reads);
                break;
            }

            reads[height] = counter.reads;
            dys[height] = dy;

            Slabbed.LOGGER.info("[RECURSION_BLOWUP] height={} probe={} state={} dy={} blockReads={}",
                    height, shortPos(probe), probeState, dy, counter.reads);
        }

        int last = aborted ? abortedAt : MAX_HEIGHT;

        // The measurement is only meaningful if every height exercised the same code path.
        if (!aborted) {
            for (int h = 2; h <= last; h++) {
                if (Double.compare(dys[h], dys[1]) != 0) {
                    throw new AssertionError("INCONCLUSIVE: fixture is not equivalent across "
                            + "heights -- dy at height 1 was " + dys[1] + " but at height " + h
                            + " was " + dys[h] + ". Different code paths were measured, so the "
                            + "growth ratio is meaningless. Fix the fixture before drawing any "
                            + "conclusion about recursion cost.");
                }
            }
        }

        StringBuilder curve = new StringBuilder();
        double worstRatio = 0.0d;
        int worstAt = -1;
        for (int h = 1; h <= last; h++) {
            curve.append(h).append("=").append(reads[h]).append("reads");
            if (h > 1 && reads[h - 1] > 0L) {
                double ratio = (double) reads[h] / (double) reads[h - 1];
                curve.append("(x").append(String.format("%.1f", ratio)).append(")");
                if (ratio > worstRatio) {
                    worstRatio = ratio;
                    worstAt = h;
                }
            }
            if (h < last) {
                curve.append(" ");
            }
        }

        Slabbed.LOGGER.info("[RECURSION_BLOWUP] curve: {}", curve);
        Slabbed.LOGGER.info("[RECURSION_BLOWUP] worst layer-over-layer read ratio: x{} at height {}",
                String.format("%.1f", worstRatio), worstAt);

        if (aborted) {
            throw new AssertionError("CONFIRMED: a single getYOffset query at pillar height "
                    + abortedAt + " exceeded " + ABORT_READS + " block reads. The lowered-carrier "
                    + "derivation cycle blows up with pillar height. Curve: " + curve);
        }

        double tailRatio = reads[last - 1] > 0L ? (double) reads[last] / (double) reads[last - 1] : 0.0d;
        Slabbed.LOGGER.info("[RECURSION_BLOWUP] sustained tail ratio at height {}: x{}",
                last, String.format("%.2f", tailRatio));

        if (reads[last] > READ_BUDGET_AT_MAX_HEIGHT) {
            throw new AssertionError("CONFIRMED: one getYOffset query on a height-" + last
                    + " double-slab pillar over air costs " + reads[last] + " block reads (budget "
                    + READ_BUDGET_AT_MAX_HEIGHT + "), with dy stable at " + dys[1] + " throughout "
                    + "and a sustained x" + String.format("%.2f", tailRatio) + " per added layer. "
                    + "That is exponential, not polynomial: each further layer DOUBLES the cost. "
                    + "Curve: " + curve);
        }

        ctx.succeed();
    }

    /** Clears a generous box so a previous height's fixture cannot arm the next one's lanes. */
    private static void clearRegion(ServerLevel world, BlockPos base, int height) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -3; dy <= height + 3; dy++) {
                    world.setBlock(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_NONE);
                }
            }
        }
    }

    /**
     * The suspect shape: a DOUBLE-slab pillar standing over air, each layer flanked by a bottom
     * slab so {@code isAdjacentSideSlabLowered} has a lateral lane to walk. Built with
     * {@code UPDATE_NONE} so construction cost is excluded from the measured query.
     */
    private static void buildPillarOverAir(ServerLevel world, BlockPos base, int height) {
        BlockState doubleSlab = Blocks.OAK_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.DOUBLE);
        BlockState bottomSlab = Blocks.OAK_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM);

        // Explicit air beneath the pillar base -- the "over a drop" half of the shape.
        world.setBlock(base.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_NONE);
        world.setBlock(base.below(2), Blocks.AIR.defaultBlockState(), Block.UPDATE_NONE);

        for (int i = 0; i < height; i++) {
            BlockPos layer = base.above(i);
            world.setBlock(layer, doubleSlab, Block.UPDATE_NONE);
            world.setBlock(layer.north(), bottomSlab, Block.UPDATE_NONE);
            world.setBlock(layer.east(), bottomSlab, Block.UPDATE_NONE);
        }
    }

    private static String shortPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /** Thrown to escape a runaway derivation rather than hang the suite. */
    private static final class ReadBudgetExceeded extends RuntimeException {
        private final long reads;

        private ReadBudgetExceeded(long reads) {
            super("block-read budget exceeded after " + reads + " reads", null, false, false);
            this.reads = reads;
        }
    }

    /**
     * Delegating {@link BlockGetter} that counts world reads. Exact and deterministic, unlike
     * wall-clock timing. {@code SlabSupport.getYOffset} accepts a {@code BlockGetter}, so this
     * measures the real production derivation with no production change.
     */
    private static final class CountingBlockGetter implements BlockGetter {
        private final BlockGetter delegate;
        private final long budget;
        private long reads;

        private CountingBlockGetter(BlockGetter delegate, long budget) {
            this.delegate = delegate;
            this.budget = budget;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            reads++;
            if (reads > budget) {
                throw new ReadBudgetExceeded(reads);
            }
            return delegate.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            reads++;
            if (reads > budget) {
                throw new ReadBudgetExceeded(reads);
            }
            return delegate.getFluidState(pos);
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return delegate.getBlockEntity(pos);
        }

        @Override
        public int getHeight() {
            return delegate.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return delegate.getMinBuildHeight();
        }
    }
}
