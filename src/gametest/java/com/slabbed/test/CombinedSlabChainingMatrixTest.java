package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
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
 * Comprehensive "combined-slab chaining" diagnostic matrix.
 *
 * <p>For every block in many slab-combination columns this records the ACTUAL
 * {@link SlabSupport#getYOffset} and compares it to the geometrically-correct
 * "flush" value derived from the rendering law:
 *
 * <pre>
 * Visual TOP of a support (block at nominal Y, rendered translated DOWN by dy):
 *   full block / vanilla TOP / vanilla DOUBLE / terrain TOP_LIKE / terrain DOUBLE_LIKE
 *       top = Y + 1   + dy
 *   vanilla BOTTOM slab / terrain BOTTOM_LIKE
 *       top = Y + 0.5 + dy
 * Visual BOTTOM of the thing resting on it (standing object / full block / BOTTOM
 * slab) at nominal Y' = Y + 1:
 *       bottom = Y' + dy' = Y + 1 + dy'
 *   (a TOP slab's visual bottom is Y' + 0.5 + dy'.)
 * Flush  =>  thing.visualBottom == support.visualTop
 *   EXPECTED dy' = dy_support + (support_is_bottom_type ? -0.5 : 0)
 * where support_is_bottom_type = vanilla BOTTOM slab OR terrain BOTTOM_LIKE.
 * </pre>
 *
 * <p>This class adds NO production behaviour and modifies nothing under
 * {@code src/main} or {@code src/client}. It only reads {@code getYOffset} and
 * prints a {@code [MATRIX]} line per block. It HARD-ASSERTS only the invariants
 * already known-green on HEAD (the vanilla-on-custom mixed-slab cases and
 * object/block/fence-on-mixed-slab matching the law). Everything else is
 * recorded and classified but NOT asserted, so the suite stays green:
 * <ul>
 *   <li>{@code DEFERRED: custom-slab skip-offset} — a terrain (custom) slab is
 *       skip-offset, so it will not lower onto a vanilla slab (actual 0.0 while
 *       the flush law wants a negative value). Documented gap.</li>
 *   <li>{@code BY-DESIGN: slab on non-lowered slab / no vertical accumulate} —
 *       production only lowers a slab when its support is itself lowered, and
 *       propagates a single -0.5 step rather than accumulating per level. "Broad
 *       slab-mixing is out of scope" per {@code SlabSupport}'s own comments.</li>
 * </ul>
 */
public final class CombinedSlabChainingMatrixTest {

    private static final double EPS = 1.0e-9;

    // ── block fixtures ────────────────────────────────────────────────────
    private static Block terrainGrassSlab() {
        return Registries.BLOCK.get(Identifier.of("terrainslabs", "grass_slab"));
    }

    private static BlockState vanillaBottom() {
        return Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static BlockState vanillaTop() {
        return Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);
    }

    private static BlockState vanillaDouble() {
        return Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE);
    }

    private static BlockState terrainSlab(SlabType type) {
        BlockState s = terrainGrassSlab().getDefaultState();
        if (s.contains(SlabBlock.TYPE)) {
            s = s.with(SlabBlock.TYPE, type);
        }
        return s;
    }

    // ── classification of a MISMATCH vs the naive geometric-flush law ──────
    private enum Kind {
        /** OK, or the law value is the genuinely intended value — a real candidate bug. */
        STRICT,
        /** Custom (terrain) slab is skip-offset: won't lower onto a vanilla slab → actual 0.0. */
        DEFERRED_CUSTOM,
        /** Slab on a NON-lowered slab, or a vertical stack that does not accumulate. By design. */
        BY_DESIGN
    }

    // ── tiny logging helpers ──────────────────────────────────────────────
    private static String id(BlockState s) {
        Identifier i = Registries.BLOCK.getId(s.getBlock());
        String base = i == null ? String.valueOf(s.getBlock()) : i.toString();
        if (s.contains(SlabBlock.TYPE)) {
            base += "[" + s.get(SlabBlock.TYPE) + "]";
        }
        return base;
    }

    private static boolean approx(double a, double b) {
        return Math.abs(a - b) < EPS;
    }

    /**
     * Records and prints one column-cell. {@code expected} is the geometric-flush law value;
     * {@code anchoredDy} is the re-read after forcing an anchor (NaN = not tested / unchanged).
     * Returns the live actual dy.
     */
    private static double record(String cfg, String level, ServerWorld world, BlockPos p,
                                 double expected, Kind kind, double anchoredDy) {
        BlockState s = world.getBlockState(p);
        double actual = SlabSupport.getYOffset(world, p, s);
        boolean match = approx(actual, expected);
        StringBuilder sb = new StringBuilder("[MATRIX] ");
        sb.append(cfg).append(" | ").append(level)
          .append(" | block=").append(id(s))
          .append(" | expected=").append(expected)
          .append(" | actual=").append(actual)
          .append(" | ").append(match ? "OK" : "MISMATCH");
        if (!match) {
            switch (kind) {
                case DEFERRED_CUSTOM -> sb.append(" (DEFERRED: custom-slab skip-offset)");
                case BY_DESIGN -> sb.append(" (BY-DESIGN: slab on non-lowered slab / no vertical accumulate)");
                case STRICT -> { /* genuine candidate bug — left bare */ }
            }
        }
        if (!Double.isNaN(anchoredDy) && !approx(anchoredDy, actual)) {
            sb.append(" anchoredDy=").append(anchoredDy);
        }
        System.out.println(sb);
        return actual;
    }

    private static double record(String cfg, String level, ServerWorld world, BlockPos p,
                                 double expected, Kind kind) {
        return record(cfg, level, world, p, expected, kind, Double.NaN);
    }

    /** Re-reads dy after forcing a placement anchor on {@code p}. */
    private static double anchoredReread(ServerWorld world, BlockPos p) {
        BlockState s = world.getBlockState(p);
        SlabAnchorAttachment.addAnchor(world, p, s);
        return SlabSupport.getYOffset(world, p, s);
    }

    private static void place(ServerWorld w, BlockPos p, BlockState s) {
        w.setBlockState(p, s, Block.NOTIFY_LISTENERS);
    }

    // ──────────────────────────────────────────────────────────────────────
    // One test method enumerates every column. Columns are spaced apart in X/Z
    // so they never interact (each lane = 3 blocks in X; caps live in dedicated
    // +Z sub-lanes built from fresh support copies).
    // ──────────────────────────────────────────────────────────────────────
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 400)
    public void combinedSlabChainingMatrix(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        Block tsGrass = terrainGrassSlab();
        ctx.assertTrue(tsGrass != Blocks.AIR,
                "fixture: terrainslabs:grass_slab must be registered (Terrain Slabs loaded under runGameTest)");
        ctx.assertTrue(
                CompatHooks.customSlabSurfaceKind(terrainSlab(SlabType.BOTTOM)) == CompatSlabSurfaceKind.BOTTOM_LIKE,
                "fixture: terrain BOTTOM slab must classify BOTTOM_LIKE");
        ctx.assertTrue(
                CompatHooks.customSlabSurfaceKind(terrainSlab(SlabType.TOP)) == CompatSlabSurfaceKind.TOP_LIKE,
                "fixture: terrain TOP slab must classify TOP_LIKE");
        ctx.assertTrue(
                CompatHooks.customSlabSurfaceKind(terrainSlab(SlabType.DOUBLE)) == CompatSlabSurfaceKind.DOUBLE_LIKE,
                "fixture: terrain DOUBLE slab must classify DOUBLE_LIKE");

        int lane = 0;

        // 1. vanilla BOTTOM on terrain BOTTOM ("mixed slab"; recently-fixed, baseline-correct).
        mixedSlabColumn(ctx, world, origin, lane++);

        // 2. terrain BOTTOM on vanilla BOTTOM (Julia's failing case — custom slab on vanilla).
        terrainOnVanillaColumn(ctx, world, origin, lane++);

        // 3. vanilla BOTTOM on vanilla BOTTOM (two stacked vanilla bottom slabs).
        twoVanillaBottomColumn(ctx, world, origin, lane++);

        // 4. terrain BOTTOM on terrain BOTTOM.
        twoTerrainBottomColumn(ctx, world, origin, lane++);

        // 5a. vanilla TOP on terrain BOTTOM.  5b. terrain TOP on vanilla BOTTOM.
        topSlabColumns(ctx, world, origin, lane++);

        // 6. DOUBLE combos: vanilla DOUBLE on terrain BOTTOM; terrain DOUBLE on vanilla BOTTOM.
        doubleCombosColumns(ctx, world, origin, lane++);

        // 7. 3-high chain: terrain BOTTOM -> vanilla BOTTOM -> vanilla BOTTOM (+ lantern cap).
        threeHighMixedChainColumn(ctx, world, origin, lane++);

        // 7b. pure-vanilla 3-high bottom-slab stack (+ lantern cap).
        threeHighVanillaChainColumn(ctx, world, origin, lane++);

        // 8. fence: (a) fence on a mixed slab; (b) fence on a lowered full block.
        fenceColumns(ctx, world, origin, lane++);

        ctx.complete();
    }

    // ── 1. mixed slab (vanilla bottom on terrain bottom) ──────────────────
    private void mixedSlabColumn(TestContext ctx, ServerWorld world, BlockPos origin, int lane) {
        String cfg = "1.mixed(vanillaBOTTOM/terrainBOTTOM)";
        int x = lane * 3;
        BlockPos basePos = origin.add(x, 1, 0);
        BlockPos slabPos = basePos.up();
        place(world, basePos, terrainSlab(SlabType.BOTTOM));
        place(world, slabPos, vanillaBottom());

        record(cfg, "base(terrainBOTTOM)", world, basePos, 0.0, Kind.STRICT);
        // terrain BOTTOM is BOTTOM_LIKE (top Y+0.5); the vanilla bottom slab on it drops -0.5.
        double slabDy = record(cfg, "combinedSlab(vanillaBOTTOM)", world, slabPos, -0.5, Kind.STRICT);
        ctx.assertTrue(approx(slabDy, -0.5),
                cfg + " vanilla bottom slab on terrain bottom should be -0.5, got " + slabDy);

        // The mixed slab is bottom-type AND lowered -0.5, so a thing on it drops a further -0.5
        // to total -1.0. lantern / full block / fence are the recently-fixed compound cases →
        // HARD-ASSERT -1.0 (and that a forced anchor on the full block does not pop it back up).
        capOnMixed(ctx, world, basePos.south(1), cfg, "cap=lantern", Blocks.LANTERN.getDefaultState(), false);
        capOnMixed(ctx, world, basePos.south(2), cfg, "cap=fullBlock", Blocks.STONE.getDefaultState(), true);
        capOnMixed(ctx, world, basePos.south(3), cfg, "cap=fence", Blocks.OAK_FENCE.getDefaultState(), false);

        // A vanilla BOTTOM slab capping the mixed slab: broad slab-mixing is out of scope, so
        // production gives a single -0.5 step (the upper slab follows the lowered support once),
        // NOT the -1.0 vertical accumulation. Record vs the accumulate law (-1.0), BY_DESIGN.
        BlockPos sBase = basePos.south(4);
        place(world, sBase, terrainSlab(SlabType.BOTTOM));
        place(world, sBase.up(), vanillaBottom());
        place(world, sBase.up(2), vanillaBottom());
        record(cfg, "cap=vanillaBottomSlab", world, sBase.up(2), -1.0, Kind.BY_DESIGN);
    }

    private void capOnMixed(TestContext ctx, ServerWorld world, BlockPos basePos, String cfg,
                            String capLabel, BlockState cap, boolean forceAnchor) {
        BlockPos slabPos = basePos.up();
        BlockPos capPos = slabPos.up();
        place(world, basePos, terrainSlab(SlabType.BOTTOM));
        place(world, slabPos, vanillaBottom());
        place(world, capPos, cap);
        double anchored = forceAnchor ? anchoredReread(world, capPos) : Double.NaN;
        double dy = record(cfg, capLabel, world, capPos, -1.0, Kind.STRICT, anchored);
        ctx.assertTrue(approx(dy, -1.0),
                cfg + " " + capLabel + " on mixed slab should be -1.0, got " + dy);
        if (!Double.isNaN(anchored)) {
            ctx.assertTrue(approx(anchored, -1.0),
                    cfg + " " + capLabel + " on mixed slab anchored dy should stay -1.0 (no pop), got " + anchored);
        }
    }

    // ── 2. terrain bottom on vanilla bottom (Julia's case) ────────────────
    private void terrainOnVanillaColumn(TestContext ctx, ServerWorld world, BlockPos origin, int lane) {
        String cfg = "2.terrainBOTTOM/vanillaBOTTOM";
        int x = lane * 3;
        BlockPos basePos = origin.add(x, 1, 0);
        BlockPos terrainPos = basePos.up();
        place(world, basePos, vanillaBottom());
        place(world, terrainPos, terrainSlab(SlabType.BOTTOM));

        record(cfg, "base(vanillaBOTTOM)", world, basePos, 0.0, Kind.STRICT);
        // Law wants -0.5 (terrain slab should drop onto the vanilla bottom slab's top), but
        // terrain slabs are skip-offset → actual 0.0. DEFERRED.
        record(cfg, "combinedTerrainSlab", world, terrainPos, -0.5, Kind.DEFERRED_CUSTOM);

        // lantern on the terrain slab: terrain BOTTOM is BOTTOM_LIKE so direct-custom gives a
        // flat -0.5 sit; the full flush law (if the terrain slab also lowered) wants -1.0.
        // Because the terrain slab does NOT lower, this stays -0.5 → record vs -1.0 DEFERRED.
        BlockPos capBase = basePos.south();
        place(world, capBase, vanillaBottom());
        place(world, capBase.up(), terrainSlab(SlabType.BOTTOM));
        place(world, capBase.up(2), Blocks.LANTERN.getDefaultState());
        record(cfg, "cap=lantern", world, capBase.up(2), -1.0, Kind.DEFERRED_CUSTOM);
    }

    // ── 3. two vanilla bottom slabs ───────────────────────────────────────
    private void twoVanillaBottomColumn(TestContext ctx, ServerWorld world, BlockPos origin, int lane) {
        String cfg = "3.vanillaBOTTOM/vanillaBOTTOM";
        int x = lane * 3;
        BlockPos lowerPos = origin.add(x, 1, 0);
        BlockPos upperPos = lowerPos.up();
        place(world, lowerPos, vanillaBottom());
        place(world, upperPos, vanillaBottom());

        record(cfg, "lowerSlab", world, lowerPos, 0.0, Kind.STRICT);
        // Upper vanilla bottom slab on a NON-lowered vanilla bottom slab. Production lowers a
        // slab only when its support is itself lowered, so the upper slab stays at 0.0 — both
        // slabs render at their natural vanilla heights (a normal half-block gap, no artifact).
        // The naive "flush onto the lower slab top" law (-0.5) does NOT apply here. BY_DESIGN.
        record(cfg, "upperSlab", world, upperPos, -0.5, Kind.BY_DESIGN);

        // lantern / full block on the upper (un-lowered) slab. Since the upper slab is at 0.0,
        // an object sitting directly on it is flush at -0.5 (its own sit-on-bottom-slab drop).
        // That IS the correct flush value → STRICT, and it matches.
        BlockPos capL = lowerPos.south();
        place(world, capL, vanillaBottom());
        place(world, capL.up(), vanillaBottom());
        place(world, capL.up(2), Blocks.LANTERN.getDefaultState());
        record(cfg, "cap=lantern", world, capL.up(2), -0.5, Kind.STRICT);

        BlockPos capB = lowerPos.south(2);
        place(world, capB, vanillaBottom());
        place(world, capB.up(), vanillaBottom());
        place(world, capB.up(2), Blocks.STONE.getDefaultState());
        double fbAnchored = anchoredReread(world, capB.up(2));
        record(cfg, "cap=fullBlock", world, capB.up(2), -0.5, Kind.STRICT, fbAnchored);
    }

    // ── 4. two terrain bottom slabs ───────────────────────────────────────
    private void twoTerrainBottomColumn(TestContext ctx, ServerWorld world, BlockPos origin, int lane) {
        String cfg = "4.terrainBOTTOM/terrainBOTTOM";
        int x = lane * 3;
        BlockPos lowerPos = origin.add(x, 1, 0);
        BlockPos upperPos = lowerPos.up();
        place(world, lowerPos, terrainSlab(SlabType.BOTTOM));
        place(world, upperPos, terrainSlab(SlabType.BOTTOM));

        record(cfg, "lowerTerrainSlab", world, lowerPos, 0.0, Kind.STRICT);
        // upper terrain slab: flush law -0.5 but skip-offset → 0.0. DEFERRED.
        record(cfg, "upperTerrainSlab", world, upperPos, -0.5, Kind.DEFERRED_CUSTOM);

        // lantern on the upper terrain slab: BOTTOM_LIKE direct-custom gives a flat -0.5 sit.
        // The full flush law (if the terrain slab lowered) wants -1.0 → record vs -1.0 DEFERRED.
        BlockPos capBase = lowerPos.south();
        place(world, capBase, terrainSlab(SlabType.BOTTOM));
        place(world, capBase.up(), terrainSlab(SlabType.BOTTOM));
        place(world, capBase.up(2), Blocks.LANTERN.getDefaultState());
        record(cfg, "cap=lantern", world, capBase.up(2), -1.0, Kind.DEFERRED_CUSTOM);
    }

    // ── 5. TOP-slab combos ────────────────────────────────────────────────
    private void topSlabColumns(TestContext ctx, ServerWorld world, BlockPos origin, int lane) {
        int x = lane * 3;

        // 5a. vanilla TOP slab on terrain BOTTOM. terrain top = Y+0.5; a vanilla TOP slab's
        // visual bottom is Y'+0.5+dy', flush onto Y+0.5 ⇒ dy' = -1.0. Production lowers it only
        // one step (-0.5), leaving a half-block gap under the top slab → genuine candidate gap.
        String cfgA = "5a.vanillaTOP/terrainBOTTOM";
        BlockPos aBase = origin.add(x, 1, 0);
        BlockPos aTop = aBase.up();
        place(world, aBase, terrainSlab(SlabType.BOTTOM));
        place(world, aTop, vanillaTop());
        record(cfgA, "base(terrainBOTTOM)", world, aBase, 0.0, Kind.STRICT);
        record(cfgA, "vanillaTOP", world, aTop, -1.0, Kind.STRICT);

        // 5b. terrain TOP slab on vanilla BOTTOM. flush law -1.0 but skip-offset → 0.0 DEFERRED.
        String cfgB = "5b.terrainTOP/vanillaBOTTOM";
        BlockPos bBase = origin.add(x, 1, 2);
        BlockPos bTop = bBase.up();
        place(world, bBase, vanillaBottom());
        place(world, bTop, terrainSlab(SlabType.TOP));
        record(cfgB, "base(vanillaBOTTOM)", world, bBase, 0.0, Kind.STRICT);
        record(cfgB, "terrainTOP", world, bTop, -1.0, Kind.DEFERRED_CUSTOM);
    }

    // ── 6. DOUBLE combos ──────────────────────────────────────────────────
    private void doubleCombosColumns(TestContext ctx, ServerWorld world, BlockPos origin, int lane) {
        int x = lane * 3;

        // (a) vanilla DOUBLE on terrain BOTTOM. A DOUBLE is full-height: visual bottom Y'+0+dy',
        // flush onto terrain top Y+0.5 ⇒ dy' = -0.5.
        String cfgA = "6a.vanillaDOUBLE/terrainBOTTOM";
        BlockPos aBase = origin.add(x, 1, 0);
        BlockPos aDouble = aBase.up();
        place(world, aBase, terrainSlab(SlabType.BOTTOM));
        place(world, aDouble, vanillaDouble());
        record(cfgA, "base(terrainBOTTOM)", world, aBase, 0.0, Kind.STRICT);
        record(cfgA, "vanillaDOUBLE", world, aDouble, -0.5, Kind.STRICT);

        // (b) terrain DOUBLE on vanilla BOTTOM. flush law -0.5 but skip-offset → 0.0 DEFERRED.
        String cfgB = "6b.terrainDOUBLE/vanillaBOTTOM";
        BlockPos bBase = origin.add(x, 1, 2);
        BlockPos bDouble = bBase.up();
        place(world, bBase, vanillaBottom());
        place(world, bDouble, terrainSlab(SlabType.DOUBLE));
        record(cfgB, "base(vanillaBOTTOM)", world, bBase, 0.0, Kind.STRICT);
        record(cfgB, "terrainDOUBLE", world, bDouble, -0.5, Kind.DEFERRED_CUSTOM);
    }

    // ── 7. 3-high mixed chain: terrain BOTTOM -> vanilla BOTTOM -> vanilla BOTTOM ──
    private void threeHighMixedChainColumn(TestContext ctx, ServerWorld world, BlockPos origin, int lane) {
        String cfg = "7.chain(terrainBOTTOM/vanBOTTOM/vanBOTTOM)";
        int x = lane * 3;
        BlockPos l0 = origin.add(x, 1, 0);   // terrain bottom
        BlockPos l1 = l0.up();               // vanilla bottom (mixed → -0.5)
        BlockPos l2 = l1.up();               // vanilla bottom
        place(world, l0, terrainSlab(SlabType.BOTTOM));
        place(world, l1, vanillaBottom());
        place(world, l2, vanillaBottom());

        record(cfg, "L0(terrainBOTTOM)", world, l0, 0.0, Kind.STRICT);
        double l1dy = record(cfg, "L1(vanillaBOTTOM=mixed)", world, l1, -0.5, Kind.STRICT);
        ctx.assertTrue(approx(l1dy, -0.5),
                cfg + " L1 vanilla bottom on terrain bottom (mixed) should be -0.5, got " + l1dy);
        // L2 on the LOWERED mixed slab. Production propagates a single -0.5 step (L2 follows L1's
        // lowering once) but does NOT accumulate the compound to -1.0. Full vertical accumulation
        // law wants -1.0 → record vs -1.0, BY_DESIGN (no vertical accumulate).
        record(cfg, "L2(vanillaBOTTOM)", world, l2, -1.0, Kind.BY_DESIGN);

        // lantern capping the chain (L3). If accumulation held it would be -1.5; production gives
        // the single-step compound (-1.0). Record vs -1.5, BY_DESIGN.
        BlockPos capBase = l0.south();
        place(world, capBase, terrainSlab(SlabType.BOTTOM));
        place(world, capBase.up(), vanillaBottom());
        place(world, capBase.up(2), vanillaBottom());
        place(world, capBase.up(3), Blocks.LANTERN.getDefaultState());
        record(cfg, "L3=lantern", world, capBase.up(3), -1.5, Kind.BY_DESIGN);
    }

    // ── 7b. pure-vanilla 3-high bottom slab stack ─────────────────────────
    private void threeHighVanillaChainColumn(TestContext ctx, ServerWorld world, BlockPos origin, int lane) {
        String cfg = "7b.chain(vanBOTTOM/vanBOTTOM/vanBOTTOM)";
        int x = lane * 3;
        BlockPos l0 = origin.add(x, 1, 0);
        BlockPos l1 = l0.up();
        BlockPos l2 = l1.up();
        place(world, l0, vanillaBottom());
        place(world, l1, vanillaBottom());
        place(world, l2, vanillaBottom());

        record(cfg, "L0(vanillaBOTTOM)", world, l0, 0.0, Kind.STRICT);
        // No lowered source at the base of a pure-vanilla stack, so nothing lowers — all slabs
        // sit at their natural vanilla heights. The accumulate law (-0.5 / -1.0) does not apply.
        record(cfg, "L1(vanillaBOTTOM)", world, l1, -0.5, Kind.BY_DESIGN);
        record(cfg, "L2(vanillaBOTTOM)", world, l2, -1.0, Kind.BY_DESIGN);

        BlockPos capBase = l0.south();
        place(world, capBase, vanillaBottom());
        place(world, capBase.up(), vanillaBottom());
        place(world, capBase.up(2), vanillaBottom());
        place(world, capBase.up(3), Blocks.LANTERN.getDefaultState());
        // Lantern sits on the top (un-lowered) bottom slab → flush -0.5; the accumulate law wants
        // -1.5. Record vs -1.5, BY_DESIGN.
        record(cfg, "L3=lantern", world, capBase.up(3), -1.5, Kind.BY_DESIGN);
    }

    // ── 8. fence cases ────────────────────────────────────────────────────
    private void fenceColumns(TestContext ctx, ServerWorld world, BlockPos origin, int lane) {
        int x = lane * 3;

        // (a) fence on a mixed slab → object follows the mixed slab's compound drop to -1.0.
        String cfgA = "8a.fence/mixedSlab";
        BlockPos aBase = origin.add(x, 1, 0);          // terrain bottom
        BlockPos aSlab = aBase.up();                   // vanilla bottom (mixed)
        BlockPos aFence = aSlab.up();
        place(world, aBase, terrainSlab(SlabType.BOTTOM));
        place(world, aSlab, vanillaBottom());
        place(world, aFence, Blocks.OAK_FENCE.getDefaultState());
        record(cfgA, "base(terrainBOTTOM)", world, aBase, 0.0, Kind.STRICT);
        record(cfgA, "mixedSlab", world, aSlab, -0.5, Kind.STRICT);
        double aFenceDy = record(cfgA, "fence", world, aFence, -1.0, Kind.STRICT);
        // HARD-ASSERT: fence on a mixed slab must follow the compound drop to -1.0 (the "fence
        // not chaining" symptom Julia reported — pinned green).
        ctx.assertTrue(approx(aFenceDy, -1.0),
                cfgA + " fence on mixed slab should be -1.0, got " + aFenceDy);

        // (b) fence on a lowered full block: vanilla bottom slab → stone (anchored, -0.5) → fence.
        // The full block lowers -0.5 (sits on bottom slab); a fence on a lowered FULL block (top-
        // type surface, not bottom) follows to -0.5 per the law.
        String cfgB = "8b.fence/loweredFullBlock";
        BlockPos bBase = origin.add(x, 1, 2);          // vanilla bottom slab
        BlockPos bFull = bBase.up();                   // stone
        BlockPos bFence = bFull.up();
        place(world, bBase, vanillaBottom());
        place(world, bFull, Blocks.STONE.getDefaultState());
        SlabAnchorAttachment.addAnchor(world, bFull, world.getBlockState(bFull));
        place(world, bFence, Blocks.OAK_FENCE.getDefaultState());
        record(cfgB, "base(vanillaBOTTOM)", world, bBase, 0.0, Kind.STRICT);
        double bFullDy = record(cfgB, "fullBlock", world, bFull, -0.5, Kind.STRICT);
        ctx.assertTrue(approx(bFullDy, -0.5),
                cfgB + " full block on bottom slab should be -0.5, got " + bFullDy);
        // Fence on a lowered full block → law -0.5. HARD-ASSERT: this is the "fence not chaining
        // on a lowered full block" question; it currently holds, so pin it green.
        double bFenceDy = record(cfgB, "fence", world, bFence, -0.5, Kind.STRICT);
        ctx.assertTrue(approx(bFenceDy, -0.5),
                cfgB + " fence on a lowered full block should follow to -0.5, got " + bFenceDy);
    }
}
