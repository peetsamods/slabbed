package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Combined-slab chaining diagnostic matrix — Forge 1.20.1 port of the main-branch
 * (1.21.11) CombinedSlabChainingMatrixTest discipline (donor family a8bdadf2/b9f5325c):
 * for every column in a set of slab/full-block chain configurations, record the ACTUAL
 * {@link SlabSupport#getYOffset} against the geometrically-correct "flush" value from
 * the rendering law, print one {@code [MATRIX]} line per cell, HARD-ASSERT only the
 * invariants this branch claims fixed, and leave every other mismatch visible in the
 * log as a bare candidate.
 *
 * <pre>
 * Flush law (all values are visual, dy-translated):
 *   support visual top:  full block = Y + 1 + dy;   BOTTOM slab = Y + 0.5 + dy
 *   thing visual bottom: full block / standing object / BOTTOM slab = Y' + dy'
 *   flush  =>  dy' = dy_support + (support_is_bottom_slab ? -0.5 : 0)
 * </pre>
 *
 * <p>Every subject block is placed through the REAL item-placement path
 * ({@link ForgeHooks#onPlaceItemIntoWorld} with a fake player) so all placement-time
 * authoring (EntityPlaceEvent anchor + persistent-carrier truth) fires exactly as in
 * live play — per this branch's proof rule, no anchor/carrier truth is written manually.
 * Only inert base fixtures (the first flat support slab of a column) use setBlock.
 *
 * <p>SCOPE HONESTY: the donor matrix also enumerates Terrain Slabs orderings
 * (vanilla-on-terrain, terrain-on-vanilla, terrain-on-terrain). The Forge
 * runGameTestServer environment loads NO Terrain Slabs jar, so those orderings are
 * untestable headless here and remain covered only by the TS compat code paths +
 * Maintainer's live route. This port covers the branch's OWN combined chains: slab-under-
 * full-block, cantilevered slab lanes, the compound -1.0 lane, stacked slab columns,
 * and 3-high chains.
 *
 * <p>Adds NO production behaviour; nothing under src/main or src/client changes.
 */
@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class ForgeCombinedSlabChainingMatrixGameTest {
    private static final double EPSILON = 1.0e-6d;

    /** Classification of a MISMATCH vs the naive geometric-flush law. */
    private enum Kind {
        /** OK, or the law value is the genuinely intended value — a bare mismatch is a candidate bug. */
        STRICT,
        /** Production deliberately does not lower this cell (e.g. slab on a NON-lowered slab). */
        BY_DESIGN
    }

    // ── column 1: caps on a lowered full block (bottom slab -> stone -> cap) ──
    @GameTest(template = "empty")
    public void capsOnLoweredFullBlockFollowFlush(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        String cfg = "F1.caps/loweredFullBlock";

        // lane A: lantern cap
        BlockPos aStone = buildSlabStoneRig(ctx, world, cfg, new BlockPos(1, 1, 1));
        BlockState lantern = placeBlockItemOnTop(world, aStone, Blocks.LANTERN);
        ctx.assertTrue(lantern.is(Blocks.LANTERN), cfg + " lantern cap must place at " + shortPos(aStone.above()));
        double lanternDy = record(cfg, "cap=lantern", world, aStone.above(), -0.5d, Kind.STRICT);
        ctx.assertTrue(near(lanternDy, -0.5d),
                cfg + " lantern on a lowered full block must follow to -0.5, got " + text(lanternDy));

        // lane B: fence cap
        BlockPos bStone = buildSlabStoneRig(ctx, world, cfg, new BlockPos(3, 1, 1));
        BlockState fence = placeBlockItemOnTop(world, bStone, Blocks.OAK_FENCE);
        ctx.assertTrue(fence.is(Blocks.OAK_FENCE), cfg + " fence cap must place at " + shortPos(bStone.above()));
        double fenceDy = record(cfg, "cap=fence", world, bStone.above(), -0.5d, Kind.STRICT);
        ctx.assertTrue(near(fenceDy, -0.5d),
                cfg + " fence on a lowered full block must follow to -0.5, got " + text(fenceDy));

        // lane C: full-block cap (vertical chain: stone on lowered stone)
        BlockPos cStone = buildSlabStoneRig(ctx, world, cfg, new BlockPos(5, 1, 1));
        BlockState stone2 = placeBlockItemOnTop(world, cStone, Blocks.STONE);
        ctx.assertTrue(stone2.is(Blocks.STONE), cfg + " stone cap must place at " + shortPos(cStone.above()));
        boolean chainAnchored = SlabAnchorAttachment.isAnchored(world, cStone.above());
        double chainDy = record(cfg, "cap=fullBlock(chainAnchored=" + chainAnchored + ")",
                world, cStone.above(), -0.5d, Kind.STRICT);
        ctx.assertTrue(chainAnchored, cfg + " stone on a lowered stone must chain-anchor");
        ctx.assertTrue(near(chainDy, -0.5d),
                cfg + " stone on a lowered stone must stay flush at -0.5, got " + text(chainDy));

        // lane D: vanilla BOTTOM slab cap on the lowered stone
        BlockPos dStone = buildSlabStoneRig(ctx, world, cfg, new BlockPos(7, 1, 1));
        BlockState capSlab = placeBlockItemOnTop(world, dStone, Blocks.OAK_SLAB);
        ctx.assertTrue(capSlab.getBlock() instanceof SlabBlock,
                cfg + " slab cap must place at " + shortPos(dStone.above()));
        double capSlabDy = record(cfg, "cap=vanillaBottomSlab", world, dStone.above(), -0.5d, Kind.STRICT);
        ctx.assertTrue(near(capSlabDy, -0.5d),
                cfg + " slab on a lowered full block must follow to -0.5, got " + text(capSlabDy));

        ctx.succeed();
    }

    // ── column 2: the compound -1.0 chain (3-high + lantern cap) ─────────────
    @GameTest(template = "empty")
    public void compoundLoweredFullBlockChain(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        String cfg = "F2.compoundChain";

        BlockPos stone1 = buildSlabStoneRig(ctx, world, cfg, new BlockPos(2, 1, 1));
        BlockPos slabB = placeBottomCantileverSlab(ctx, world, cfg, stone1, Direction.WEST);

        // stone2 on the lowered BOTTOM slab carrier: the branch's named legal compound
        // state — must drop the full -1.0 to sit flush on slabB's visual top.
        BlockState stone2State = placeBlockItemOnTop(world, slabB, Blocks.STONE);
        ctx.assertTrue(stone2State.is(Blocks.STONE), cfg + " stone2 must place at " + shortPos(slabB.above()));
        BlockPos stone2 = slabB.above();
        boolean stone2Anchored = SlabAnchorAttachment.isAnchored(world, stone2);
        double stone2Dy = record(cfg, "L2=compoundFullBlock(anchored=" + stone2Anchored + ")",
                world, stone2, -1.0d, Kind.STRICT);
        ctx.assertTrue(stone2Anchored, cfg + " compound stone2 must be anchored");
        ctx.assertTrue(near(stone2Dy, -1.0d),
                cfg + " full block on a lowered bottom-slab carrier must compound to -1.0, got " + text(stone2Dy));

        // L3: stone3 on the compound stone. 3-high chain cell — previously unverified on
        // this branch. Flush law wants -1.0 (stone2 visual top is at its own cell base).
        // KNOWN CANDIDATE (recorded bare, not asserted): the vertical chain walk gives a
        // constant -0.5, so the block DIRECTLY above a -1.0 compound floats 0.5 above its
        // support's visual top. Same family as the slab cap in F3.
        BlockState stone3State = placeBlockItemOnTop(world, stone2, Blocks.STONE);
        ctx.assertTrue(stone3State.is(Blocks.STONE), cfg + " stone3 must place at " + shortPos(stone2.above()));
        BlockPos stone3 = stone2.above();
        boolean stone3Anchored = SlabAnchorAttachment.isAnchored(world, stone3);
        record(cfg, "L3=fullBlock(anchored=" + stone3Anchored + ")", world, stone3, -1.0d, Kind.STRICT);

        // L4: lantern capping the 3-high chain must at least follow whatever L3 actually
        // is (relative flush pinned, so the top of the chain never detaches from L3).
        BlockState lantern = placeBlockItemOnTop(world, stone3, Blocks.LANTERN);
        ctx.assertTrue(lantern.is(Blocks.LANTERN), cfg + " lantern cap must place at " + shortPos(stone3.above()));
        double stone3Dy = SlabSupport.getYOffset(world, stone3, world.getBlockState(stone3));
        double lanternDy = record(cfg, "L4=lantern(followsL3)", world, stone3.above(), stone3Dy, Kind.STRICT);
        ctx.assertTrue(near(lanternDy, stone3Dy),
                cfg + " lantern must follow its full-block support's actual dy, got "
                        + text(lanternDy) + " vs support " + text(stone3Dy));

        ctx.succeed();
    }

    // ── column 3: compound side probe (floating-DODO law) + slab cap ─────────
    @GameTest(template = "empty")
    public void compoundSideProbeAndSlabCap(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        String cfg = "F3.compoundSideProbe";

        BlockPos stone1 = buildSlabStoneRig(ctx, world, cfg, new BlockPos(2, 1, 1));
        BlockPos slabB = placeBottomCantileverSlab(ctx, world, cfg, stone1, Direction.WEST);
        BlockState stone2State = placeBlockItemOnTop(world, slabB, Blocks.STONE);
        ctx.assertTrue(stone2State.is(Blocks.STONE), cfg + " stone2 must place at " + shortPos(slabB.above()));
        BlockPos stone2 = slabB.above();
        double stone2Dy = record(cfg, "compoundFullBlock", world, stone2, -1.0d, Kind.STRICT);
        ctx.assertTrue(near(stone2Dy, -1.0d), cfg + " compound stone2 must be -1.0, got " + text(stone2Dy));

        // DODO probe: a slab cantilevered off the -1.0 compound block with air below must
        // take the single -0.5 side step, NOT inherit the neighbour's -1.0 magnitude
        // (the floating-slab DODO fixed by the never-pop hardening at HEAD~4). Aim at the
        // visible LOWER half of stone2's west face (visual span [Y-1, Y] at dy=-1.0).
        BlockPos probePos = stone2.west();
        BlockState probeState = placeSlabAgainstFace(world, stone2, probePos, Direction.WEST,
                stone2.getY() - 0.7d);
        ctx.assertTrue(probeState.getBlock() instanceof SlabBlock,
                cfg + " DODO probe slab must place at " + shortPos(probePos));
        double probeDy = record(cfg, "sideProbeSlab[" + slabTypeText(probeState) + "]",
                world, probePos, -0.5d, Kind.STRICT);
        ctx.assertTrue(near(probeDy, -0.5d),
                cfg + " slab beside a -1.0 compound block must take the single -0.5 side step"
                        + " (not inherit -1.0), got " + text(probeDy));

        // slab capping the compound block: flush law wants -1.0 (stone2 visual top = cell
        // base). KNOWN CANDIDATE (recorded bare, not asserted): production gives the
        // single -0.5 step, so the cap floats 0.5 above the compound's visual top — the
        // same block-directly-above-a-compound family as F2's L3.
        BlockState capSlab = placeBlockItemOnTop(world, stone2, Blocks.OAK_SLAB);
        ctx.assertTrue(capSlab.getBlock() instanceof SlabBlock,
                cfg + " cap slab must place at " + shortPos(stone2.above()));
        record(cfg, "cap=vanillaBottomSlab[" + slabTypeText(capSlab) + "]",
                world, stone2.above(), -1.0d, Kind.STRICT);

        ctx.succeed();
    }

    // ── column 4: compound sidecar survives source-slab removal (never-pop) ──
    @GameTest(template = "empty")
    public void compoundSidecarSurvivesSourceRemoval(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        String cfg = "F4.sidecarSurvival";

        BlockPos stone1 = buildSlabStoneRig(ctx, world, cfg, new BlockPos(2, 1, 1));
        BlockPos slabB = placeBottomCantileverSlab(ctx, world, cfg, stone1, Direction.WEST);
        BlockState stone2State = placeBlockItemOnTop(world, slabB, Blocks.STONE);
        ctx.assertTrue(stone2State.is(Blocks.STONE), cfg + " stone2 must place at " + shortPos(slabB.above()));
        BlockPos stone2 = slabB.above();
        double before = SlabSupport.getYOffset(world, stone2, world.getBlockState(stone2));
        ctx.assertTrue(near(before, -1.0d), cfg + " compound stone2 must start at -1.0, got " + text(before));

        // Break the source slab under the compound block. The beta4 sidecar records the
        // authored -1.0 lane precisely so source removal cannot renormalize it upward.
        world.destroyBlock(slabB, false);
        double after = record(cfg, "afterSourceSlabRemoval", world, stone2, -1.0d, Kind.STRICT);
        ctx.assertTrue(near(after, -1.0d),
                cfg + " compound full block must KEEP dy=-1.0 after its source slab is removed"
                        + " (sidecar never-pop), got " + text(after));

        ctx.succeed();
    }

    // ── column 5: stacked slab columns (BY-DESIGN lanes + flat-stack caps) ───
    @GameTest(template = "empty")
    public void stackedSlabColumns(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        String cfg = "F5.stackedSlabs";

        // NOTE: the slab STACKS here are setBlock fixtures. A slab ITEM clicked on a
        // bottom slab's top face merges it into a DOUBLE at the same cell (vanilla
        // combine — verified in this harness), so stacked bottom slabs cannot be
        // item-authored top-face; the flat stacks are inert fixtures like the donor's.

        // lane A: slab on a NON-lowered slab — production only lowers a slab whose support
        // is itself lowered, so the upper slab stays 0.0 (both at natural vanilla heights).
        // The naive flush law (-0.5) does NOT apply: BY_DESIGN (donor column 3).
        BlockPos aLower = ctx.absolutePos(new BlockPos(1, 1, 1));
        world.setBlock(aLower, bottomSlab(), Block.UPDATE_ALL);
        world.setBlock(aLower.above(), bottomSlab(), Block.UPDATE_ALL);
        record(cfg, "upperSlabOnFlatSlab", world, aLower.above(), -0.5d, Kind.BY_DESIGN);

        // lane B: 3-high pure-vanilla bottom-slab stack — nothing lowers (no lowered source).
        BlockPos bL0 = ctx.absolutePos(new BlockPos(3, 1, 1));
        world.setBlock(bL0, bottomSlab(), Block.UPDATE_ALL);
        world.setBlock(bL0.above(), bottomSlab(), Block.UPDATE_ALL);
        world.setBlock(bL0.above(2), bottomSlab(), Block.UPDATE_ALL);
        record(cfg, "stack3.L1", world, bL0.above(), -0.5d, Kind.BY_DESIGN);
        record(cfg, "stack3.L2", world, bL0.above(2), -1.0d, Kind.BY_DESIGN);

        // lane C: lantern on the (un-lowered) top of a 2-slab stack: its own
        // sit-on-bottom-slab drop, flush at -0.5 — core Slabbed behaviour, pinned.
        BlockPos cL0 = ctx.absolutePos(new BlockPos(5, 1, 1));
        world.setBlock(cL0, bottomSlab(), Block.UPDATE_ALL);
        world.setBlock(cL0.above(), bottomSlab(), Block.UPDATE_ALL);
        BlockState cCap = placeBlockItemOnTop(world, cL0.above(), Blocks.LANTERN);
        ctx.assertTrue(cCap.is(Blocks.LANTERN), cfg + " lantern cap must place");
        double cDy = record(cfg, "cap=lanternOnFlatStack", world, cL0.above(2), -0.5d, Kind.STRICT);
        ctx.assertTrue(near(cDy, -0.5d),
                cfg + " lantern on a flat bottom-slab stack must sit at -0.5, got " + text(cDy));

        // lane D: stone on the (un-lowered) top of a 2-slab stack: direct anchor one level
        // up — same family as the baseline fixture, pinned (-0.5 + anchored).
        BlockPos dL0 = ctx.absolutePos(new BlockPos(7, 1, 1));
        world.setBlock(dL0, bottomSlab(), Block.UPDATE_ALL);
        world.setBlock(dL0.above(), bottomSlab(), Block.UPDATE_ALL);
        BlockState dCap = placeBlockItemOnTop(world, dL0.above(), Blocks.STONE);
        ctx.assertTrue(dCap.is(Blocks.STONE), cfg + " stone cap must place");
        boolean dAnchored = SlabAnchorAttachment.isAnchored(world, dL0.above(2));
        double dDy = record(cfg, "cap=fullBlockOnFlatStack(anchored=" + dAnchored + ")",
                world, dL0.above(2), -0.5d, Kind.STRICT);
        ctx.assertTrue(dAnchored, cfg + " stone on the stack top must be anchored");
        ctx.assertTrue(near(dDy, -0.5d),
                cfg + " stone on a flat bottom-slab stack must sit at -0.5, got " + text(dDy));

        ctx.succeed();
    }

    // ── column 6: lowered TOP-slab cantilever orderings ───────────────────────
    @GameTest(template = "empty")
    public void loweredTopSlabStack(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        String cfg = "F6.loweredTopSlabStack";

        // rig A: stone stacked on a lowered TOP cantilever (flush law -0.5).
        BlockPos aStone1 = buildSlabStoneRig(ctx, world, cfg, new BlockPos(2, 1, 1));
        BlockPos aSlabB = aStone1.west();
        BlockState aTop = placeSlabAgainstFace(world, aStone1, aSlabB, Direction.WEST,
                aStone1.getY() + 0.8d);
        ctx.assertTrue(aTop.getBlock() instanceof SlabBlock
                        && aTop.getValue(SlabBlock.TYPE) == SlabType.TOP,
                cfg + " cantilever must be TYPE=TOP, got " + slabTypeText(aTop));
        double aTopDy = record(cfg, "cantileverTopSlab", world, aSlabB, -0.5d, Kind.STRICT);
        ctx.assertTrue(near(aTopDy, -0.5d),
                cfg + " TOP cantilever must be lowered -0.5, got " + text(aTopDy));
        BlockState aCap = placeBlockItemOnTop(world, aSlabB, Blocks.STONE);
        ctx.assertTrue(aCap.is(Blocks.STONE), cfg + " stone must place on the TOP cantilever");
        double aCapDy = record(cfg, "fullBlockOnLoweredTopSlab", world, aSlabB.above(), -0.5d, Kind.STRICT);
        ctx.assertTrue(near(aCapDy, -0.5d),
                cfg + " full block on a lowered TOP slab must follow to -0.5, got " + text(aCapDy));

        // rig B: lantern standing on a lowered TOP cantilever (flush law -0.5).
        BlockPos bStone1 = buildSlabStoneRig(ctx, world, cfg, new BlockPos(2, 1, 4));
        BlockPos bSlabB = bStone1.west();
        BlockState bTop = placeSlabAgainstFace(world, bStone1, bSlabB, Direction.WEST,
                bStone1.getY() + 0.8d);
        ctx.assertTrue(bTop.getBlock() instanceof SlabBlock
                        && bTop.getValue(SlabBlock.TYPE) == SlabType.TOP,
                cfg + " cantilever must be TYPE=TOP, got " + slabTypeText(bTop));
        BlockState bCap = placeBlockItemOnTop(world, bSlabB, Blocks.LANTERN);
        ctx.assertTrue(bCap.is(Blocks.LANTERN), cfg + " lantern must place on the TOP cantilever");
        double bCapDy = record(cfg, "lanternOnLoweredTopSlab", world, bSlabB.above(), -0.5d, Kind.STRICT);
        ctx.assertTrue(near(bCapDy, -0.5d),
                cfg + " lantern on a lowered TOP slab must follow to -0.5, got " + text(bCapDy));

        ctx.succeed();
    }

    // ── column 7: EXACT 462c8bb2 donor fixture — object on a cantilever-lowered
    //    full block with air under the ENTIRE support column ─────────────────────
    /**
     * Pins the exact main-branch (1.21.11) {@code 462c8bb2} bug fixture on this port:
     * a standing lantern on a full block that is lowered ONLY by the geometric
     * cantilever lane ({@code SlabSupport.isCantileverLoweredFullBlock} — adjacency
     * to a lowered tower, AIR under the whole support column, no anchor of its own).
     * The donor bug: the object's support-column walks ({@code hasSlabInColumn})
     * stop at the air gap under the cantilever and never see the support's
     * adjacency-based lowering, so the object floats at 0 above a -0.5 support.
     *
     * <p>Nearby cells (F1 lantern on lowered-FB-with-slab-below, F2 L4, F6 rig B)
     * all give the object a slab/anchor in its own column; only THIS fixture
     * exercises the object-follow across the air gap.
     *
     * <p>GROUND TRUTH ON THIS BRANCH (2026-07-02 run): the support lane is green
     * (cantilever planks -0.5, unanchored) but the lantern reads 0.0 — the donor
     * bug IS present here; the donor's fix lane (462c8bb2's non-solid-object
     * branch in getYOffsetInner) was never ported to this getYOffsetInner. Per
     * this class's discipline the support preconditions are HARD-ASSERTED and the
     * object-follow is recorded as a bare [MATRIX] MISMATCH candidate (suite stays
     * green) until the fix behavior is ported.
     */
    @GameTest(template = "empty")
    public void lanternOnCantileveredSupportLowers(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        String cfg = "F7.objectOnCantileverFullBlock";

        BlockPos stone = buildSlabStoneRig(ctx, world, cfg, new BlockPos(2, 1, 1));
        // Cantilever a full block west of the lowered stone through the REAL item path.
        BlockPos planks = stone.west();
        BlockState planksState = placeBlockItemAgainstFace(
                world, stone, planks, Direction.WEST, stone.getY() + 0.2d, Blocks.OAK_PLANKS);
        ctx.assertTrue(planksState.is(Blocks.OAK_PLANKS),
                cfg + " cantilever planks must place at " + shortPos(planks));
        // Fixture-shape honesty guards: the ENTIRE in-template support column below the
        // cantilever is air, and the cantilever carries NO anchor — placement authoring
        // does not qualify it (air below), so this cell covers the GEOMETRIC lane, the
        // same lane the donor fixture exercised via setBlockState. If a future authoring
        // change anchors side placements, this assert flags that the cell silently
        // changed lanes instead of letting it false-green.
        ctx.assertTrue(world.getBlockState(planks.below()).isAir()
                        && world.getBlockState(planks.below(2)).isAir(),
                cfg + " support column below the cantilever must be air");
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(world, planks),
                cfg + " cantilever must NOT be anchored (geometric lane only)");
        double planksDy = record(cfg, "cantileverFullBlock", world, planks, -0.5d, Kind.STRICT);
        ctx.assertTrue(near(planksDy, -0.5d),
                cfg + " cantilevered full block must lower -0.5 (isCantileverLoweredFullBlock lane), got "
                        + text(planksDy));

        // The 462c8bb2 bug case: a standing lantern on that support should follow to
        // -0.5 instead of floating at 0 above its lowered support. KNOWN CANDIDATE
        // (recorded bare, not asserted — RED-cell discipline): actual today is 0.0.
        // hasSlabInColumn / the object walks stop at the air gap under the cantilever
        // and no lane reads the support's adjacency-based lowering for a non-solid
        // object; the donor fix (462c8bb2 getYOffsetInner non-solid-object branch on
        // 1.21.11) has no counterpart here. Flip this row to a hard assert when the
        // object-follow behavior is ported.
        BlockState lantern = placeBlockItemOnTop(world, planks, Blocks.LANTERN);
        ctx.assertTrue(lantern.is(Blocks.LANTERN),
                cfg + " lantern must place at " + shortPos(planks.above()));
        record(cfg, "cap=lanternOnCantilever", world, planks.above(), -0.5d, Kind.STRICT);

        ctx.succeed();
    }

    // ── shared rig builders ───────────────────────────────────────────────────

    /**
     * Base rig for most columns: flat bottom slab (setBlock) + stone item-placed on it.
     * Asserts the baseline invariant (stone anchored at -0.5 — the branch's proven
     * fixture) and returns the stone's position.
     */
    private static BlockPos buildSlabStoneRig(GameTestHelper ctx, ServerLevel world, String cfg, BlockPos relSlab) {
        BlockPos slabPos = ctx.absolutePos(relSlab);
        world.setBlock(slabPos, bottomSlab(), Block.UPDATE_ALL);
        BlockState stoneState = placeBlockItemOnTop(world, slabPos, Blocks.STONE);
        BlockPos stonePos = slabPos.above();
        if (!stoneState.is(Blocks.STONE)) {
            throw new AssertionError(cfg + " rig stone did not place at " + shortPos(stonePos));
        }
        double stoneDy = SlabSupport.getYOffset(world, stonePos, stoneState);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(world, stonePos),
                cfg + " rig stone must be anchored at " + shortPos(stonePos));
        ctx.assertTrue(near(stoneDy, -0.5d),
                cfg + " rig stone must be lowered -0.5 at " + shortPos(stonePos) + ", got " + text(stoneDy));
        return stonePos;
    }

    /**
     * Cantilevers a BOTTOM slab off {@code face} of a lowered (-0.5) full block by
     * clicking the visible LOWER half of its side face, asserts TYPE=BOTTOM and the
     * -0.5 adjacent-side step, and returns the slab's position.
     */
    private static BlockPos placeBottomCantileverSlab(
            GameTestHelper ctx, ServerLevel world, String cfg, BlockPos stonePos, Direction face) {
        BlockPos slabPos = stonePos.relative(face);
        // stone dy=-0.5 -> visual span [Y-0.5, Y+0.5]; click below cell base for BOTTOM.
        BlockState slabState = placeSlabAgainstFace(world, stonePos, slabPos, face, stonePos.getY() - 0.2d);
        ctx.assertTrue(slabState.getBlock() instanceof SlabBlock
                        && slabState.getValue(SlabBlock.TYPE) == SlabType.BOTTOM,
                cfg + " cantilever slab must be TYPE=BOTTOM at " + shortPos(slabPos)
                        + ", got " + slabTypeText(slabState));
        double slabDy = SlabSupport.getYOffset(world, slabPos, slabState);
        ctx.assertTrue(near(slabDy, -0.5d),
                cfg + " cantilever slab must be lowered -0.5 at " + shortPos(slabPos) + ", got " + text(slabDy));
        return slabPos;
    }

    // ── placement helpers (real item path; mirrors the proven fixture idiom) ──

    private static BlockState placeBlockItemOnTop(ServerLevel world, BlockPos supportPos, Block itemBlock) {
        Player player = FakePlayerFactory.getMinecraft(world);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(itemBlock));
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(supportPos).add(0.0d, 0.5d, 0.0d),
                Direction.UP,
                supportPos,
                false);
        InteractionResult result = ForgeHooks.onPlaceItemIntoWorld(
                new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        if (!result.consumesAction()) {
            throw new AssertionError("item placement did not consume action, item=" + itemBlock
                    + " support=" + supportPos + " result=" + result);
        }
        return world.getBlockState(supportPos.above());
    }

    private static BlockState placeSlabAgainstFace(
            ServerLevel world, BlockPos targetPos, BlockPos newPos, Direction face, double clickY) {
        return placeBlockItemAgainstFace(world, targetPos, newPos, face, clickY, Blocks.OAK_SLAB);
    }

    private static BlockState placeBlockItemAgainstFace(
            ServerLevel world, BlockPos targetPos, BlockPos newPos, Direction face, double clickY,
            Block itemBlock) {
        Player player = FakePlayerFactory.getMinecraft(world);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(itemBlock));
        Vec3 hitLoc = new Vec3(
                targetPos.getX() + 0.5d + face.getStepX() * 0.5d,
                clickY,
                targetPos.getZ() + 0.5d + face.getStepZ() * 0.5d);
        BlockHitResult hit = new BlockHitResult(hitLoc, face, targetPos, false);
        InteractionResult result = ForgeHooks.onPlaceItemIntoWorld(
                new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        if (!result.consumesAction()) {
            throw new AssertionError("item placement did not consume action, item=" + itemBlock
                    + " result=" + result + " target=" + targetPos + " face=" + face);
        }
        return world.getBlockState(newPos);
    }

    // ── matrix recording (donor [MATRIX] line shape, greppable cross-branch) ──

    private static double record(String cfg, String level, ServerLevel world, BlockPos pos,
                                 double expected, Kind kind) {
        BlockState state = world.getBlockState(pos);
        double actual = SlabSupport.getYOffset(world, pos, state);
        boolean match = near(actual, expected);
        StringBuilder sb = new StringBuilder("[MATRIX] ");
        sb.append(cfg).append(" | ").append(level)
                .append(" | block=").append(state.getBlock())
                .append(" | expected=").append(text(expected))
                .append(" | actual=").append(text(actual))
                .append(" | ").append(match ? "OK" : "MISMATCH");
        if (!match && kind == Kind.BY_DESIGN) {
            sb.append(" (BY-DESIGN: slab on non-lowered slab / no vertical accumulate)");
        }
        System.out.println(sb);
        return actual;
    }

    // ── tiny helpers ──────────────────────────────────────────────────────────

    private static BlockState bottomSlab() {
        return Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static String slabTypeText(BlockState state) {
        return state.hasProperty(SlabBlock.TYPE) ? state.getValue(SlabBlock.TYPE).toString() : String.valueOf(state);
    }

    private static boolean near(double actual, double expected) {
        return Double.isFinite(actual)
                && Double.isFinite(expected)
                && Math.abs(actual - expected) <= EPSILON;
    }

    private static String text(double value) {
        if (!Double.isFinite(value)) {
            return Double.toString(value);
        }
        return String.format(java.util.Locale.ROOT, "%.6f", value);
    }

    private static String shortPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
