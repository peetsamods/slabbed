package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * Combined-slab chaining diagnostic matrix — Fabric 1.21.1 behavior-port of
 * main's (Fabric 1.21.11) {@code CombinedSlabChainingMatrixTest} (a8bdadf2),
 * re-derived idiomatically for this branch.
 *
 * <p>For every block in each column this records the ACTUAL
 * {@link SlabSupport#getYOffset} as a {@code [MATRIX]} line and compares it to
 * the geometric flush law:
 *
 * <pre>
 * support visual top:  full block / TOP / DOUBLE slab -> Y + 1 + dy;  BOTTOM slab -> Y + 0.5 + dy
 * thing visual bottom: standing block at Y' = Y + 1   -> Y' + dy'
 * flush  =>  EXPECTED dy' = dy_support + (support is BOTTOM slab ? -0.5 : 0)
 * </pre>
 *
 * <p>This class adds NO production behaviour and modifies nothing under
 * {@code src/main} or {@code src/client}. Donor deviations, all deliberate:
 * <ul>
 *   <li>Terrain Slabs lanes are dropped: there is no headless TS harness on
 *       1.21.1 (HANDOFF — the TS path here is live-pending only), so the donor's
 *       terrain columns (1, 2, 4, 5, 6, 7, 8a) are not expressible. Ported:
 *       vanilla-expressible donor columns 3, 7b, 8b, plus native lowered-full-block
 *       deep-stack columns that answer this branch's open class-10 question
 *       ("uniform -0.5 column" claimed by the c43b6a76/b5bd1fc9 lineage, but no
 *       &gt;3-high test existed).</li>
 *   <li>The donor packed all columns into one test; here each column family is its
 *       own {@code fabric-gametest-api-v1:empty} (8x8x8) test so no lane leaves the
 *       structure bounds.</li>
 *   <li>Donor used NOTIFY_LISTENERS; sibling tests on this branch use NOTIFY_ALL,
 *       kept for branch consistency.</li>
 *   <li>The donor's DEFERRED_CUSTOM classification leaves with the TS lanes; the
 *       global designed bound {@code dy >= -1.0} (this branch's "-1.0 clamp") is
 *       asserted on every cell instead.</li>
 * </ul>
 *
 * <p>Assertion policy (donor philosophy): HARD-ASSERT only lanes that are legal
 * grammar on THIS branch — grounded slabs at 0.0, anchored ordinary full block on
 * a bottom slab at -0.5, every full block of a lowered vertical chain staying
 * flush at -0.5 (the class-10 law), object caps' own -0.5 sit, and fence following
 * a lowered full block at -0.5 — plus the global -1.0 bound. Everything else is
 * recorded and classified ({@code BY-DESIGN}: production propagates a single -0.5
 * step and never accumulates; a slab above a lowered full block lowers only with
 * proven carrier truth, which raw {@code setBlockState} never authors) but NOT
 * asserted, so the matrix still emits full evidence without asserting design
 * non-goals.
 */
public final class CombinedSlabChainingMatrixTest {

    private static final double EPS = 1.0e-9;
    /** Designed lower bound on this branch: the -1.0 compound clamp. */
    private static final double DY_LOWER_BOUND = -1.0;

    // ── classification of a MISMATCH vs the naive geometric-flush law ──────
    private enum Kind {
        /** OK, or the law value is the genuinely intended value — a real candidate bug. */
        STRICT,
        /**
         * Slab on a non-lowered slab, vertical stacks that do not accumulate, or a
         * slab above a lowered full block without authored carrier truth. By design.
         */
        BY_DESIGN
    }

    // ── block fixtures ────────────────────────────────────────────────────
    private static BlockState vanillaBottom() {
        return Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    // ── tiny logging helpers ──────────────────────────────────────────────
    private static String id(BlockState s) {
        String base = String.valueOf(Registries.BLOCK.getId(s.getBlock()));
        if (s.contains(SlabBlock.TYPE)) {
            base += "[" + s.get(SlabBlock.TYPE) + "]";
        }
        return base;
    }

    private static boolean approx(double a, double b) {
        return Math.abs(a - b) < EPS;
    }

    private static void place(ServerWorld w, BlockPos p, BlockState s) {
        w.setBlockState(p, s, Block.NOTIFY_ALL);
    }

    /** Re-reads dy after forcing a placement anchor on {@code p} (mirrors live BlockItem authoring). */
    private static double anchoredReread(ServerWorld world, BlockPos p) {
        BlockState s = world.getBlockState(p);
        SlabAnchorAttachment.addAnchor(world, p, s);
        return SlabSupport.getYOffset(world, p, s);
    }

    /**
     * Records one matrix cell: prints the [MATRIX] line, enforces the global designed
     * bound dy >= -1.0, and returns the live actual dy. {@code anchoredDy} is the
     * re-read after forcing an anchor (NaN = not tested / unchanged).
     */
    private static double record(TestContext ctx, String cfg, String level, ServerWorld world,
                                 BlockPos p, double expected, Kind kind, double anchoredDy) {
        BlockState s = world.getBlockState(p);
        double actual = SlabSupport.getYOffset(world, p, s);
        boolean match = approx(actual, expected);
        StringBuilder sb = new StringBuilder("[MATRIX] ");
        sb.append(cfg).append(" | ").append(level)
          .append(" | block=").append(id(s))
          .append(" | expected=").append(expected)
          .append(" | actual=").append(actual)
          .append(" | ").append(match ? "OK" : "MISMATCH");
        if (!match && kind == Kind.BY_DESIGN) {
            sb.append(" (BY-DESIGN: no vertical accumulate / carrier-truth-gated slab follow)");
        }
        if (!Double.isNaN(anchoredDy) && !approx(anchoredDy, actual)) {
            sb.append(" anchoredDy=").append(anchoredDy);
        }
        System.out.println(sb);
        ctx.assertTrue(actual >= DY_LOWER_BOUND - EPS,
                cfg + " " + level + " violates the designed -1.0 clamp: dy=" + actual);
        return actual;
    }

    private static double record(TestContext ctx, String cfg, String level, ServerWorld world,
                                 BlockPos p, double expected, Kind kind) {
        return record(ctx, cfg, level, world, p, expected, kind, Double.NaN);
    }

    // ── donor columns 3 + 7b: pure-vanilla bottom-slab stacks ─────────────
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void vanillaBottomSlabStacks(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(new BlockPos(1, 1, 1));

        // 3. two stacked vanilla bottom slabs: production lowers a slab only when its
        // support is itself lowered, so the upper slab stays 0.0 (both render at their
        // natural vanilla heights — the half-block gap is the vanilla look, not an
        // artifact). Law -0.5 recorded BY-DESIGN.
        String cfg3 = "3.vanillaBOTTOM/vanillaBOTTOM";
        BlockPos lower = origin;
        place(world, lower, vanillaBottom());
        place(world, lower.up(), vanillaBottom());
        double lowerDy = record(ctx, cfg3, "lowerSlab", world, lower, 0.0, Kind.STRICT);
        ctx.assertTrue(approx(lowerDy, 0.0), cfg3 + " grounded lower slab must be 0.0, got " + lowerDy);
        record(ctx, cfg3, "upperSlab", world, lower.up(), -0.5, Kind.BY_DESIGN);

        // caps on the (un-lowered) upper slab: object flush = its own -0.5 sit. STRICT law.
        BlockPos capL = origin.add(0, 0, 2);
        place(world, capL, vanillaBottom());
        place(world, capL.up(), vanillaBottom());
        place(world, capL.up(2), Blocks.LANTERN.getDefaultState());
        double lanternDy = record(ctx, cfg3, "cap=lantern", world, capL.up(2), -0.5, Kind.STRICT);
        ctx.assertTrue(approx(lanternDy, -0.5),
                cfg3 + " lantern on the upper bottom slab must sit at -0.5, got " + lanternDy);

        BlockPos capB = origin.add(0, 0, 4);
        place(world, capB, vanillaBottom());
        place(world, capB.up(), vanillaBottom());
        place(world, capB.up(2), Blocks.STONE.getDefaultState());
        double fbAnchored = anchoredReread(world, capB.up(2));
        double fbDy = record(ctx, cfg3, "cap=fullBlock", world, capB.up(2), -0.5, Kind.STRICT, fbAnchored);
        ctx.assertTrue(approx(fbDy, -0.5),
                cfg3 + " full block on the upper bottom slab must be -0.5, got " + fbDy);
        ctx.assertTrue(approx(fbAnchored, -0.5),
                cfg3 + " anchored full block on the upper bottom slab must stay -0.5 (no pop), got " + fbAnchored);

        // 7b. pure-vanilla 3-high bottom-slab stack: no lowered source anywhere, nothing
        // lowers, and the accumulate law (-0.5/-1.0/-1.5 per level) does not apply.
        String cfg7 = "7b.chain(vanBOTTOM/vanBOTTOM/vanBOTTOM)";
        BlockPos l0 = origin.add(3, 0, 0);
        place(world, l0, vanillaBottom());
        place(world, l0.up(), vanillaBottom());
        place(world, l0.up(2), vanillaBottom());
        double l0Dy = record(ctx, cfg7, "L0", world, l0, 0.0, Kind.STRICT);
        ctx.assertTrue(approx(l0Dy, 0.0), cfg7 + " grounded L0 slab must be 0.0, got " + l0Dy);
        record(ctx, cfg7, "L1", world, l0.up(), -0.5, Kind.BY_DESIGN);
        record(ctx, cfg7, "L2", world, l0.up(2), -1.0, Kind.BY_DESIGN);

        BlockPos cap7 = origin.add(3, 0, 3);
        place(world, cap7, vanillaBottom());
        place(world, cap7.up(), vanillaBottom());
        place(world, cap7.up(2), vanillaBottom());
        place(world, cap7.up(3), Blocks.LANTERN.getDefaultState());
        // Lantern sits on the top (un-lowered) bottom slab → its own -0.5 sit; the
        // accumulate law wants -1.5. Recorded vs -1.5 BY-DESIGN.
        record(ctx, cfg7, "L3=lantern", world, cap7.up(3), -1.5, Kind.BY_DESIGN);

        ctx.complete();
    }

    // ── NATIVE deep-stack resolver (class 10): lowered-FB vertical chain ──
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void loweredFullBlockDeepStack(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(new BlockPos(1, 1, 1));

        // bottomSlab -> anchored stone (-0.5, the legal "FB anchored on bottom slab"
        // state) -> 3 more stone above (5-high column, deeper than any existing test).
        // The legal-state list names "ordinary full block in a proven lowered vertical
        // chain with dy=-0.5"; the flush law demands every level stay -0.5 (each support
        // is a full block, so no further half-step). A 0.0 anywhere in the chain is a
        // visible half-block window — the class-10 "deep-stack gap".
        String cfg = "D.deepChain(vanBOTTOM/FB*4)";
        place(world, base, vanillaBottom());
        BlockPos fb1 = base.up();
        place(world, fb1, Blocks.STONE.getDefaultState());
        SlabAnchorAttachment.addAnchor(world, fb1, world.getBlockState(fb1));

        double baseDy = record(ctx, cfg, "L0(bottomSlab)", world, base, 0.0, Kind.STRICT);
        ctx.assertTrue(approx(baseDy, 0.0), cfg + " grounded base slab must be 0.0, got " + baseDy);
        double fb1Dy = record(ctx, cfg, "L1(anchoredFB)", world, fb1, -0.5, Kind.STRICT);
        ctx.assertTrue(approx(fb1Dy, -0.5),
                cfg + " anchored FB on bottom slab must be -0.5, got " + fb1Dy);

        BlockPos prev = fb1;
        for (int level = 2; level <= 4; level++) {
            BlockPos p = prev.up();
            place(world, p, Blocks.STONE.getDefaultState());
            double dy = record(ctx, cfg, "L" + level + "(FB)", world, p, -0.5, Kind.STRICT);
            ctx.assertTrue(approx(dy, -0.5),
                    cfg + " L" + level + " FB in the lowered vertical chain must stay flush at -0.5"
                            + " (0.0 here is the class-10 deep-stack gap), got " + dy);
            prev = p;
        }

        // lantern topping the chain: flush law -0.5 (support is a lowered FB, top-type
        // surface). Recorded, not asserted — decorative followers have their own lanes.
        BlockPos cap = prev.up();
        place(world, cap, Blocks.LANTERN.getDefaultState());
        record(ctx, cfg, "L5=lantern", world, cap, -0.5, Kind.STRICT);

        // Slab-above-lowered-FB variant: the legal-state list allows a lowered BOTTOM
        // slab above an anchored full block "only when persistent carrier truth is
        // explicitly proven" — raw setBlockState authors none, so the actual value here
        // is the diagnostic (recorded, not asserted). The second slab above is the
        // no-accumulate lane.
        String cfgS = "D.slabOnLoweredFB";
        BlockPos sBase = ctx.getAbsolutePos(new BlockPos(4, 1, 1));
        place(world, sBase, vanillaBottom());
        BlockPos sFb = sBase.up();
        place(world, sFb, Blocks.STONE.getDefaultState());
        SlabAnchorAttachment.addAnchor(world, sFb, world.getBlockState(sFb));
        place(world, sFb.up(), vanillaBottom());
        place(world, sFb.up(2), vanillaBottom());
        record(ctx, cfgS, "L0(bottomSlab)", world, sBase, 0.0, Kind.STRICT);
        record(ctx, cfgS, "L1(anchoredFB)", world, sFb, -0.5, Kind.STRICT);
        record(ctx, cfgS, "L2(bottomSlab)", world, sFb.up(), -0.5, Kind.BY_DESIGN);
        record(ctx, cfgS, "L3(bottomSlab)", world, sFb.up(2), -1.0, Kind.BY_DESIGN);

        ctx.complete();
    }

    // ── donor column 8b: fence on a lowered full block ────────────────────
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void fenceOnLoweredFullBlock(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        String cfg = "8b.fence/loweredFullBlock";
        BlockPos base = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos full = base.up();
        BlockPos fence = full.up();
        place(world, base, vanillaBottom());
        place(world, full, Blocks.STONE.getDefaultState());
        SlabAnchorAttachment.addAnchor(world, full, world.getBlockState(full));
        place(world, fence, Blocks.OAK_FENCE.getDefaultState());

        double baseDy = record(ctx, cfg, "base(vanillaBOTTOM)", world, base, 0.0, Kind.STRICT);
        ctx.assertTrue(approx(baseDy, 0.0), cfg + " grounded base slab must be 0.0, got " + baseDy);
        double fullDy = record(ctx, cfg, "fullBlock", world, full, -0.5, Kind.STRICT);
        ctx.assertTrue(approx(fullDy, -0.5),
                cfg + " full block on bottom slab should be -0.5, got " + fullDy);
        // HARD-ASSERT: the "fence not chaining on a lowered full block" question — the
        // donor pinned this green and this branch's class-5 claim says it follows.
        double fenceDy = record(ctx, cfg, "fence", world, fence, -0.5, Kind.STRICT);
        ctx.assertTrue(approx(fenceDy, -0.5),
                cfg + " fence on a lowered full block should follow to -0.5, got " + fenceDy);
        ctx.complete();
    }
}
