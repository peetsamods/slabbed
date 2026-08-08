package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * PKG-20260710-depth-cap-removal — pins the "SBSB...S deep tower disjoints past -1.0" bug
 * (TEST 15, sentinel row 131: stone at dy=-1.0 directly under a just-placed slab at dy=-0.5, a
 * live logical 0.5 gap). Root cause per the package: placement-time dy is floored at -1.0
 * ({@code SlabSupport.java} ~2106 and ~2612, {@code Math.max(-1.0, supportDy - 0.5)}). Alternating
 * bottom-slab/full-block columns accumulate -0.5 per merge; past -1.0 total the floor engages and
 * every placement above lands 0.5 shallow, opening a gap that never closes (everything above the
 * floor keeps stacking flush on the WRONG, floored value).
 *
 * <p>This test builds the tower via REAL placement ({@code ItemStack#useOn}, never {@code setBlock}
 * + a hand-rolled state — the exact false-green shape the law post-mortem calls out), the same
 * pattern as {@link NeighborUpdateInvarianceTest}. Every placement is: click the TOP face of the
 * previously-placed cell, alternating STONE_SLAB (produces a BOTTOM slab on a full-block top) and
 * STONE (a full block resting on a bottom slab).
 *
 * <p><b>Expected dy sequence (uncapped, per aim-decides / LAW.md — this is what a placement-time
 * accumulator with NO floor must produce):</b>
 * <pre>
 *   ground (stone, dy n/a)
 *   slab0  on ground stone      -> dy  0.0   (flush: bottom slab on a full-block top, no drop)
 *   stone1 on slab0              -> dy -0.5   (full block rests IN the lowered slab: slab.dy - 0.5)
 *   slab2  on stone1             -> dy -0.5   (flush: bottom slab on a full-block top, no drop)
 *   stone2 on slab2               -> dy -1.0   (slab2.dy - 0.5)   <- shallow-regime cap boundary
 *   slab3  on stone2              -> dy -1.0   (flush)
 *   stone3 on slab3                -> dy -1.5   (slab3.dy - 0.5)  <- FLOOR ENGAGES HERE (bug)
 *   slab4  on stone3                -> dy -1.5   (flush)
 *   stone4 on slab4                  -> dy -2.0   (slab4.dy - 0.5)
 * </pre>
 * The general recursion is {@code dy(full-on-slab) = dy(slab) - 0.5} and
 * {@code dy(slab-on-full) = dy(full)} (flush, no extra drop resting on a solid top).
 *
 * <p><b>What is actually observed today (recorded by this test, not hand-derived):</b> the live gap
 * is NOT a clean "-1.0 instead of -1.5" floor. Reading {@code SlabSupport.java}'s
 * {@code loweredFullBlockMagnitude} / {@code floorTorchBottomSlabSupportDy} pair: a full block gets
 * its TRUE accumulated depth only via a placement-time "compound full-block anchor" marker that
 * recurses into the support's real {@code getYOffsetInner}; when the support slab is ITSELF already
 * sitting at the floor, that marker does not get set on the new placement, so the new full block
 * falls through to a flat, non-compounding {@code -0.5} default instead of even the naively-floored
 * {@code -1.0}. So the observed gap past the boundary is larger than 0.5 and does not visibly grow
 * further (everything above stacks flush on that same wrong, shallow value) — still exactly the
 * disjointed-tower symptom the maintainer reported live ("as soon as you pass like 5 blocks from the ground,
 * the entire law is completely broken"), just with a bigger live gap than the package's one-line
 * {@code Math.max(-1.0, ...)} summary implies in isolation. The failure messages below report the
 * exact observed vs. expected numbers every run — treat those as ground truth over this comment.
 */
public final class DeepCompoundTowerLawTest {

    private static final double EPS = 1.0e-6;

    // ── real-useOn placement (matches NeighborUpdateInvarianceTest's `place`) ──────────────────
    private static void place(GameTestHelper h, Item item, BlockPos clicked, Direction face, double yNudge) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(clicked)
                .add(face.getStepX() * 0.5, face.getStepY() * 0.5 + yNudge, face.getStepZ() * 0.5);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, clicked, false)));
    }

    private static double dy(ServerLevel w, BlockPos p) {
        return SlabSupport.getYOffset(w, p, w.getBlockState(p));
    }

    /** Exact height identity (byte-identical intent), with -0.0 normalized to 0.0. */
    private static boolean sameHeight(double a, double b) {
        return Double.doubleToRawLongBits(a == 0.0 ? 0.0 : a)
                == Double.doubleToRawLongBits(b == 0.0 ? 0.0 : b);
    }

    /**
     * Builds an alternating SBSB...S tower of {@code cellCount} cells above a fresh stone ground,
     * via real useOn placement. Cell 0 (index into the returned list) is the first slab placed on
     * the ground; even indices are bottom slabs, odd indices are full stone blocks. Returns the
     * absolute BlockPos of each placed cell, bottom-to-top.
     */
    private static List<BlockPos> buildTower(GameTestHelper h, ServerLevel w, BlockPos groundAbs, int cellCount) {
        w.setBlock(groundAbs, Blocks.STONE.defaultBlockState(), 2);
        List<BlockPos> cells = new ArrayList<>();
        BlockPos cursor = groundAbs;
        for (int i = 0; i < cellCount; i++) {
            boolean isSlab = (i % 2 == 0);
            Item item = isSlab ? Items.STONE_SLAB : Items.STONE;
            place(h, item, cursor, Direction.UP, 0.0);
            BlockPos placed = cursor.above();
            // Identity check, not just non-air: the fabric-gametest-api-v1:empty structure is only
            // 8 cells tall (y 0..7 interior); one cell above that is the framework's own opaque
            // boundary barrier. A failed placement there would otherwise read as a false "success"
            // (non-air, but the PRE-EXISTING barrier, not our item) — this premise check turns that
            // into a loud failure instead of silently corrupting the dy reading.
            boolean placedRight = isSlab
                    ? w.getBlockState(placed).getBlock() == Blocks.STONE_SLAB
                    : w.getBlockState(placed).getBlock() == Blocks.STONE;
            if (!placedRight) {
                throw h.assertionException(placed, "premise: tower cell " + i
                        + " (" + item + ") failed to place on " + cursor + " — got "
                        + w.getBlockState(placed).getBlock()
                        + " (likely out of the empty structure's 8-tall bound; reduce cellCount)");
            }
            cells.add(placed);
            cursor = placed;
        }
        return cells;
    }

    /**
     * Asserts the tower's dy sequence against the expected UNCAPPED accumulation
     * (slab flush / full-block -0.5-per-merge, no floor). Reports each mismatch with the exact
     * gap, so a floor-induced 0.5 shortfall is unambiguous in the failure message.
     */
    private void assertExpectedSequence(GameTestHelper h, ServerLevel w, List<BlockPos> cells) {
        List<String> mismatches = new ArrayList<>();
        double expected = 0.0; // cell 0 (slab on ground) is flush: 0.0
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                boolean isFullOnSlab = (i % 2 == 1); // odd index = STONE placed on the previous slab
                expected = isFullOnSlab ? expected - 0.5 : expected; // flush when slab-on-full
            }
            double actual = dy(w, cells.get(i));
            Slabbed.LOGGER.info("DEEP-TOWER | cell {} ({}) expected={} actual={}",
                    i, w.getBlockState(cells.get(i)).getBlock(), expected, actual);
            if (Math.abs(actual - expected) > EPS) {
                mismatches.add("cell " + i + " (" + w.getBlockState(cells.get(i)).getBlock() + " at "
                        + cells.get(i) + "): expected dy=" + expected + " got dy=" + actual
                        + " (gap=" + (actual - expected) + ")");
            }
        }
        if (!mismatches.isEmpty()) {
            throw h.assertionException(cells.get(cells.size() - 1),
                    "DEPTH-CAP GAP — accumulated dy disjointed from the expected uncapped sequence "
                            + "(Math.max(-1.0, supportDy-0.5) floor engaging past -1.0 total lowering):\n  "
                            + String.join("\n  ", mismatches));
        }
    }

    /**
     * CONTROL — shallow regime (total lowering stays within -1.0), must be GREEN today. Tower:
     * slab0(0.0) stone1(-0.5) slab2(-0.5) stone2(-1.0). This is exactly the already-covered
     * {@code deepAlternatingStackClampsAtMinusOne} range, but built via real placement instead of
     * {@code setBlock}, and asserted as a law-conformant flush sequence rather than "clamped".
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void towerDepthWithinCapFlush(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos ground = h.absolutePos(new BlockPos(3, 1, 3));
        List<BlockPos> cells = buildTower(h, w, ground, 4); // slab0, stone1, slab2, stone2
        assertExpectedSequence(h, w, cells);
        h.succeed();
    }

    /**
     * DEEP — crosses the -1.0 boundary by one merge (total accumulated lowering -1.5). Tower:
     * ...stone2(-1.0) slab3(-1.0) stone3(-1.5 expected). EXPECTED RED today — NOT a clean -1.0
     * floor as the package's {@code Math.max(-1.0, supportDy-0.5)} read alone would suggest.
     * Ground-truthed via the {@code loweredFullBlockMagnitude}/{@code floorTorchBottomSlabSupportDy}
     * pair in {@code SlabSupport.java}: once the SUPPORT slab (slab3) is itself already sitting at
     * the -1.0 floor, the placement-time compound-anchor marking that would let stone3 read the
     * support's TRUE accumulated dy does not engage, and stone3 falls through to the flat,
     * non-compounding {@code -0.5} default instead — so the live gap is actually {@code 1.0}
     * (expected -1.5, observed -0.5), not the {@code 0.5} a naive single floor would produce. The
     * assertion reports whatever the live gap is; do not hand-tune this comment's number without
     * re-running the test — record the exact observed value in the RED report to the architect.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void towerDepthMinus15Flush(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos ground = h.absolutePos(new BlockPos(3, 0, 3));
        List<BlockPos> cells = buildTower(h, w, ground, 6); // slab0..stone3
        assertExpectedSequence(h, w, cells);
        h.succeed();
    }

    /**
     * DEEP — one merge further (total accumulated lowering -1.5 continuing to slab4, flush on
     * stone3). This is the deepest tower that fits inside the {@code fabric-gametest-api-v1:empty}
     * structure (8 cells tall, interior y 0..7; no taller structure ships in fabric-gametest-api-v1,
     * and {@code @GameTest} has no template-size override) — ground occupies row 0, leaving 7 rows
     * for the tower, so a true stone4/-2.0 cell (which needs an 8th tower row) cannot be built here
     * without a larger structure. Still demonstrates the key claim from the package: the gap does
     * NOT self-correct or grow cleanly — everything placed above the first bad cell keeps stacking
     * on the WRONG value, so slab4 also reads shallow instead of the expected -1.5. EXPECTED RED.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void towerDepthMinus20Flush(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos ground = h.absolutePos(new BlockPos(3, 0, 3));
        List<BlockPos> cells = buildTower(h, w, ground, 7); // slab0..stone3,slab4 (max that fits)
        assertExpectedSequence(h, w, cells);
        h.succeed();
    }

    /**
     * S-2 pattern (byte-identical LAW invariance) applied to a deep tower: once placed, every
     * cell's dy must survive a neighbor mutation at the tower's BASE untouched — whatever value it
     * was frozen at (correct or floored) must not additionally move on a neighbor edit. This is
     * orthogonal to the depth-cap bug: it is RED only if a neighbor edit ALSO reintroduces
     * recomputation, which would be a second, independent LAW violation stacked on top of the cap.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void towerStaysAfterBaseMutation(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos ground = h.absolutePos(new BlockPos(3, 0, 3));
        List<BlockPos> cells = buildTower(h, w, ground, 6); // slab0..stone3

        double[] before = new double[cells.size()];
        for (int i = 0; i < cells.size(); i++) {
            before[i] = dy(w, cells.get(i));
        }

        // S-2 base mutation: add a full block beside the tower's base, never touching the tower cells.
        w.setBlock(ground.north(), Blocks.STONE.defaultBlockState(), 2);

        List<String> violations = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            BlockPos p = cells.get(i);
            if (w.getBlockState(p).isAir()) {
                continue; // genuine vanilla removal carve-out (LAW.md #4)
            }
            double after = dy(w, p);
            if (!sameHeight(before[i], after)) {
                violations.add("cell " + i + " at " + p + ": dy " + before[i] + " -> " + after);
            }
        }
        if (!violations.isEmpty()) {
            throw h.assertionException(cells.get(cells.size() - 1),
                    "LAW VIOLATION — deep tower moved on a base neighbor edit "
                            + "(placed height must survive byte-identical):\n  "
                            + String.join("\n  ", violations));
        }
        h.succeed();
    }

    /**
     * S-2 pattern applied to the SUPPORT COLUMN itself (fix-round MAJOR-A): edits AT HEIGHT beside the
     * column, plus a MID-column break, must leave every remaining cell's dy byte-identical. This is the
     * invariance envelope of the deep-rest read the depth-cap-removal pass added: the deep cells read
     * their height through the column below, so this leg proves that side noise at altitude and a
     * mid-column removal (whose fallback chain re-reads to the SAME values: markers and anchors keep
     * every surviving cell where it was) cannot move anything. The one genuinely-losing edit — breaking
     * the cell DIRECTLY below a deep cell — is covered by NeighborUpdateInvarianceTest's
     * slab_on_deep_lowered_full_block subject (expected red with frozen OFF, the documented hole class).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void towerStaysAfterSupportColumnSideMutation(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos ground = h.absolutePos(new BlockPos(3, 0, 3));
        List<BlockPos> cells = buildTower(h, w, ground, 7); // slab0..stone3,slab4

        double[] before = new double[cells.size()];
        for (int i = 0; i < cells.size(); i++) {
            before[i] = dy(w, cells.get(i));
        }

        // side edits AT HEIGHT: a slab beside the -1.0 slab3 and a full block beside the -1.5 stone3
        w.setBlock(cells.get(4).north(), Blocks.STONE_SLAB.defaultBlockState(), 2);
        w.setBlock(cells.get(5).east(), Blocks.STONE.defaultBlockState(), 2);
        // MID-column break: remove stone1; every cell ABOVE keeps its dy (slab2's anchored floor is the
        // same -0.5 it was placed at, and every deeper cell reads through markers that survive).
        w.destroyBlock(cells.get(1), false);

        List<String> violations = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            BlockPos p = cells.get(i);
            if (w.getBlockState(p).isAir()) {
                continue; // the removed cell itself (and any genuine vanilla removal, LAW.md #4)
            }
            double after = dy(w, p);
            if (!sameHeight(before[i], after)) {
                violations.add("cell " + i + " at " + p + ": dy " + before[i] + " -> " + after);
            }
        }
        if (!violations.isEmpty()) {
            throw h.assertionException(cells.get(cells.size() - 1),
                    "LAW VIOLATION — deep tower moved on a support-column edit "
                            + "(placed height must survive byte-identical):\n  "
                            + String.join("\n  ", violations));
        }
        h.succeed();
    }

    /**
     * The documented deep-rest hole and its heal, in one green test (fix-round MAJOR-A verification):
     * with frozen dy OFF, breaking the stone DIRECTLY below the -1.5 top slab drops its live read to
     * the -0.5 anchored floor (the same never-pop-on-direct-support-removal hole class the fence_gate
     * S-2 subject pins — the anchor stores presence, not depth). With the frozen-dy store consulted
     * ({@code -Dslabbed.frozenDy}, flipped here in-process via the public switch, single-threaded
     * server tick so the try/finally scope is exact), the SAME cell keeps its placed -1.5 byte-
     * identical — the store was written at placement regardless of the flag. The live post-break value
     * is logged, not asserted: the hole's exact magnitude is pinned by the expected-red matrix subject,
     * and this test must stay green when the hole is eventually fixed.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void deepRestBreakBelowHoleHealsWithFrozenDy(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos ground = h.absolutePos(new BlockPos(3, 0, 3));
        List<BlockPos> cells = buildTower(h, w, ground, 7); // slab0..stone3,slab4
        BlockPos topSlab = cells.get(6);
        BlockPos deepStone = cells.get(5);

        double placedDy = dy(w, topSlab);
        if (Math.abs(placedDy + 1.5) > EPS) {
            throw h.assertionException(topSlab,
                    "premise: top slab should read the uncapped -1.5, got " + placedDy);
        }
        double stored = com.slabbed.anchor.SlabAnchorAttachment.storedPlacementDy(w, topSlab);
        if (Math.abs(stored + 1.5) > EPS) {
            throw h.assertionException(topSlab,
                    "premise: placement store should hold -1.5 for the top slab, got " + stored);
        }

        w.destroyBlock(deepStone, false);
        double liveAfter = dy(w, topSlab); // frozen OFF: the documented hole (logged, not asserted)
        Slabbed.LOGGER.info("DEEP-TOWER | break-below live read with frozen OFF: placed={} live={}",
                placedDy, liveAfter);

        boolean prev = com.slabbed.anchor.SlabAnchorAttachment.FROZEN_DY_ENABLED;
        double frozenAfter;
        com.slabbed.anchor.SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            frozenAfter = dy(w, topSlab);
        } finally {
            com.slabbed.anchor.SlabAnchorAttachment.FROZEN_DY_ENABLED = prev;
        }
        if (!sameHeight(placedDy, frozenAfter)) {
            throw h.assertionException(topSlab,
                    "FROZEN-DY HEAL FAILED — with the store consulted the deep-rest slab must keep its "
                            + "placed height across a break directly below: placed=" + placedDy
                            + " frozenRead=" + frozenAfter + " (liveRead=" + liveAfter + ")");
        }
        h.succeed();
    }

    /**
     * LIVE CRASH PIN (TEST 16, StackOverflowError on the render/setupTerrain thread). An anchored
     * TOP slab resting directly on a ceiling-attached object (an iron_chain, Y-axis) below it used
     * to infinitely ping-pong: the deep-rest lane ({@code SlabSupport} ~2539) read the chain's dy;
     * the chain's own ceiling-hung lane (~2894) read back UP to this exact slab (its "above", since
     * a TOP slab qualifies via {@code isTopSlab}); that reentered the deep-rest lane, which read the
     * chain again — forever, since neither call goes through the guarded public {@code getYOffset}
     * entry (the {@code IN_GET_Y_OFFSET} reentrancy flag never engages between two direct
     * {@code getYOffsetInner} calls). Fixed by restricting the deep-rest lane to
     * {@code state.isSolidRender()} supports (opaque full cubes only — matching the lane's own
     * documented intent), which a ceiling-attached decoration never is. This test just needs to
     * NOT overflow the stack.
     *
     * <p>The anchor is forced directly via {@code SlabAnchorAttachment.addAnchor} rather than
     * relying on natural placement-intent to produce one over a flush (dy=0) chain — the live crash
     * happened inside a complex multi-tower build where the anchoring context is hard to reproduce
     * minimally, and the recursion itself only depends on the anchored-slab branch being reached
     * with a ceiling-attached, non-solid support below, not on HOW the anchor got there. The public
     * {@code addAnchor} gate (qualifiesForAnchor) rejects this synthetic setup since nothing here
     * naturally lowers the slab, so the test forces the package-private {@code addAnchorUnchecked}
     * via reflection — test-only, does not touch production visibility.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void anchoredTopSlabOverCeilingAttachedChainDoesNotRecurse(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos groundAbs = h.absolutePos(new BlockPos(3, 1, 3));
        w.setBlock(groundAbs, Blocks.STONE.defaultBlockState(), 2);
        BlockPos chainPos = groundAbs.above();
        place(h, Items.IRON_CHAIN, groundAbs, Direction.UP, 0.0);
        if (w.getBlockState(chainPos).getBlock() != Blocks.IRON_CHAIN) {
            throw h.assertionException(chainPos,
                    "premise: chain failed to place on ground — got " + w.getBlockState(chainPos).getBlock());
        }
        // A TOP slab clicked on the chain's own top face seats directly above it (isCeilingAttached
        // + isTopSlab(above) is exactly the shape that used to recurse).
        place(h, Items.STONE_SLAB, chainPos, Direction.UP, 0.999);
        BlockPos slabPos = chainPos.above();
        if (w.getBlockState(slabPos).getBlock() != Blocks.STONE_SLAB) {
            throw h.assertionException(slabPos,
                    "premise: top slab failed to place on the chain — got " + w.getBlockState(slabPos).getBlock());
        }
        forceAnchorUnchecked(w, slabPos);
        if (!com.slabbed.anchor.SlabAnchorAttachment.isAnchored(w, slabPos)) {
            throw h.assertionException(slabPos, "premise: forced anchor did not take");
        }
        // The call that used to StackOverflow. If we get past this line, the recursion is fixed.
        double slabDy = dy(w, slabPos);
        Slabbed.LOGGER.info("DEEP-TOWER | anchored top slab over ceiling-attached chain: dy={}", slabDy);
        double chainDy = dy(w, chainPos);
        Slabbed.LOGGER.info("DEEP-TOWER | ceiling-attached chain under that slab: dy={}", chainDy);
        h.succeed();
    }

    /** Reflection into the private {@code addAnchorUnchecked} — see the test's javadoc for why. */
    private static void forceAnchorUnchecked(ServerLevel w, BlockPos pos) {
        try {
            java.lang.reflect.Method m = com.slabbed.anchor.SlabAnchorAttachment.class
                    .getDeclaredMethod("addAnchorUnchecked", net.minecraft.world.level.Level.class, BlockPos.class);
            m.setAccessible(true);
            m.invoke(null, w, pos);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
