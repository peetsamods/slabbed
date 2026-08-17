package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.anchor.SlabPlacementDyAttachment;
import com.slabbed.compat.CompatHooks;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * The shouldSkipOffset CONSUMER SWEEP — the shared-predicate leak class, machine-gated.
 *
 * <p>The recurring cross-port failure: a lowering predicate is widened (a new anchor lane, a new
 * geometric walk) and one consumer forgets the {@code CompatHooks.shouldSkipOffset} guard, so a
 * Terrain-Slabs-owned block gets Slabbed's offset stacked on TS's own — the −1.0 double-offset
 * family. Each instance was fixed at the leaking call site; nothing asserted the INVARIANT across
 * all entry points, so every widened predicate could re-open it (the open broader entry).
 *
 * <p>This suite pins the invariant using the headless {@link TerrainSlabsTestShim} block: a
 * TS-owned SLAB placed in shapes that WOULD lower a vanilla slab must read dy 0.0 from every
 * Slabbed entry point — its offset belongs to TS. The deliberate exceptions (the slab-chain
 * cantilever lane and an explicitly-anchored TS slab, see {@code getYOffset}'s head) build their
 * OWN qualifying shapes and are not these fixtures.
 *
 * <p><b>Confirmed exception, live-ruled (maintainer, 2026-08-09):</b> a TS-owned slab placed
 * VERTICALLY on a vanilla bottom slab is one of the deliberate exceptions above, not a leak. Live
 * pass in the dev environment confirmed the TS slab visually seats flush on the vanilla slab's
 * top face — real WYSIWYG seating, not the double-offset stacking this sweep otherwise guards
 * against. {@link #tsOnVanillaBottomSlabAnchorsAndSeatsAtHalfDrop} pins the anchored `dy=-0.5`
 * reading as the correct, intended result for this exact shape.
 *
 * <p><b>AUTHORSHIP BOUNDARY (maintainer ruling, 2026-08-17):</b> a plain opaque cube written by
 * worldgen or {@code setBlockState} stays flush so the natural-terrain world-hole guard remains
 * intact. The same cube placed by a player on a BOTTOM-like Terrain surface is different: the
 * placement transaction owns an explicit numeric fact and seats it at the visible top plane,
 * {@code -0.5}, without widening Terrain anchor propagation. The stored number lets model,
 * collision and culling distinguish authored construction from unauthored terrain.
 *
 * <p><b>NOT Root Cause A, confirmed correct behaviour (live-tested 2026-08-09):</b> a STANDING
 * OBJECT (lantern, torch, etc.) resting directly on a TS bottom slab legitimately reads
 * {@code -0.5} and looks flush — the intended, working {@code directCustomSlabSupportDy}/
 * {@code isSlabSitCandidate} compat lane, unrelated to Root Cause A. This class's earlier
 * "positive control" (a follower must still lower on TS) was right the first time; a subsequent
 * "correction" briefly flipped it to expect flush before this exact live test disproved that —
 * see {@link #standingObjectDirectlyOnTsBottomSlabLowersCorrectly}.
 *
 * <p>An anchored TS-owned slab is not a horizontal lowering source for an adjacent vanilla slab;
 * {@link #anchoredTsSlabDoesNotLowerAHorizontallyAdjacentVanillaSlab} pins that boundary without
 * changing the TS slab's own legitimate anchored height.
 */
public final class TerrainSlabsGuardSweepTest {

    private static final double EPS = 1.0e-6;

    private static BlockState tsBottom() {
        return TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    /** A TS bottom slab resting on a vanilla bottom slab — the shape that lowers a vanilla slab. */
    private static BlockPos buildTsOnVanillaSlab(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 2, 2);
        w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos vSlab = ground.up();
        w.setBlockState(vSlab, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos ts = vSlab.up();
        w.setBlockState(ts, tsBottom(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(CompatHooks.shouldSkipOffset(w.getBlockState(ts)),
                "fixture: the shim block must be recognized as TS-owned (shouldSkipOffset true)");
        return ts;
    }

    // ── entry point 1+2: the dy resolvers themselves ─────────────────────────────────────────
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void tsSlabOwnDyIsTsOwnedThroughBothResolvers(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ts = buildTsOnVanillaSlab(ctx);
        double dy = SlabSupport.getYOffset(w, ts, w.getBlockState(ts));
        ctx.assertTrue(Math.abs(dy) <= EPS,
                "GUARD: a TS-owned slab in a would-lower shape must read getYOffset 0.0 from "
                        + "Slabbed (TS owns its offset; anything else is the double-offset leak), got " + dy);
        double visual = SlabSupport.getVisualYOffset(w, ts, w.getBlockState(ts));
        ctx.assertTrue(Math.abs(visual) <= EPS,
                "GUARD: getVisualYOffset must agree (the published per-position value), got " + visual);
        ctx.complete();
    }

    // ── entry point 3: the shouldOffset gate ─────────────────────────────────────────────────
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void tsSlabNeverPassesShouldOffset(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ts = buildTsOnVanillaSlab(ctx);
        ctx.assertTrue(!SlabSupport.shouldOffset(w, ts, w.getBlockState(ts)),
                "GUARD: shouldOffset must refuse a TS-owned block outright (the CompatHooks "
                        + "early return) — a widened predicate must not reach past it");
        ctx.complete();
    }

    // ── entry point 4: the placement-time anchor qualifier lanes — PINNED (maintainer ruling,
    //    live-confirmed 2026-08-09) ─────────────────────────────────────────────────────────
    // A TS-owned slab placed VERTICALLY on a vanilla bottom slab IS one of the deliberate
    // exceptions this class's javadoc names (the "explicitly-anchored TS slab" lane at
    // getYOffset's head) — not the double-offset leak this sweep otherwise guards against. Live
    // pass in the dev environment: the TS slab visually seats flush on the vanilla slab's top
    // face. The onPlaced anchor chain capturing it (dy=-0.5, anchored=true) is therefore CORRECT
    // WYSIWYG seating, and this row pins that reading rather than guarding against it.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void tsOnVanillaBottomSlabAnchorsAndSeatsAtHalfDrop(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ts = buildTsOnVanillaSlab(ctx);
        BlockState state = w.getBlockState(ts);
        SlabAnchorAttachment.addAnchor(w, ts, state);
        SlabAnchorAttachment.freezeLoweredOnPlace(w, ts, state);
        double dy = SlabSupport.getYOffset(w, ts, w.getBlockState(ts));
        ctx.assertTrue(Math.abs(dy - (-0.5)) <= EPS,
                "PINNED: a TS-owned slab placed on a vanilla bottom slab must anchor and seat at "
                        + "dy=-0.5 (live-confirmed correct WYSIWYG seating, maintainer ruling "
                        + "2026-08-09) — the onPlaced chain must capture this shape, got dy=" + dy);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, ts),
                "PINNED: this shape must anchor — LAW 1 requires the height survive a later "
                        + "neighbour change, and only an anchor (or a stored placement height) "
                        + "provides that");
        ctx.complete();
    }

    // ── positive control, corrected back (live-tested 2026-08-09) ────────────────────────────
    // A standing object (lantern) resting directly on a TS bottom slab DOES legitimately lower
    // -0.5 and looks flush — confirmed live. This is the directCustomSlabSupportDy/
    // isSlabSitCandidate compat lane working as designed; NOT part of Root Cause A.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void standingObjectDirectlyOnTsBottomSlabLowersCorrectly(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(5, 2, 5);
        w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos ts = ground.up();
        w.setBlockState(ts, tsBottom(), Block.NOTIFY_LISTENERS);
        BlockPos follower = ts.up();
        w.setBlockState(follower, Blocks.LANTERN.getDefaultState(), Block.NOTIFY_LISTENERS);
        double dy = SlabSupport.getYOffset(w, follower, w.getBlockState(follower));
        ctx.assertTrue(dy < -EPS,
                "CONTROL (live-confirmed 2026-08-09): a standing object resting directly on a TS "
                        + "bottom slab must still lower — the working compat lane, distinct from "
                        + "Root Cause A's plain-solid-cube leak — got dy=" + dy);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void floorObjectsPlacedOnTsBottomSlabPublishHalfSeat(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(4, 2, 4);
        BlockPos ts = ground.up();
        BlockPos placedPos = ts.up();
        w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(ts, tsBottom(), Block.NOTIFY_LISTENERS);

        for (Block block : new Block[]{Blocks.TORCH, Blocks.LANTERN, Blocks.REPEATER, Blocks.COMPARATOR}) {
            w.setBlockState(placedPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabPlacementDyAttachment.clear(w, placedPos);
            PlayerEntity player = PlacementHarness.mockPlayerHolding(
                    ctx, ts.north(3), new net.minecraft.item.ItemStack(block.asItem(), 1));
            Vec3d hit = new Vec3d(ts.getX() + 0.5, ts.getY() + 0.5, ts.getZ() + 0.5);

            ActionResult result = PlacementHarness.useHeldItem(w, player, ts, Direction.UP, hit);

            ctx.assertTrue(result.isAccepted(),
                    "floor-supported placement on Terrain bottom slab must succeed for " + block
                            + "; got " + result);
            ctx.assertTrue(w.getBlockState(placedPos).isOf(block),
                    "the expected floor-supported block must occupy the destination for " + block);
            double stored = SlabPlacementDyAttachment.storedDy(w, placedPos);
            ctx.assertTrue(Double.doubleToRawLongBits(stored)
                            == Double.doubleToRawLongBits(-0.5d),
                    "player-authored floor object on Terrain dy=0 must freeze at -0.5 for "
                            + block + "; got " + stored);
            double visual = SlabSupport.getYOffset(w, placedPos, w.getBlockState(placedPos));
            ctx.assertTrue(Math.abs(visual - (-0.5d)) <= EPS,
                    "floor object must visibly seat on the Terrain bottom slab for " + block
                            + "; got dy=" + visual);
        }
        ctx.complete();
    }

    // ── Unauthored geometric path — a plain solid terrain cube must stay flush ───────────────
    // isSlabSitCandidate excludes unauthored opaque cubes from the standing-object lane. With no
    // placement fact this remains the natural-terrain world-hole guard; the player-authored row
    // below is distinguished only by its transaction-owned numeric fact.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void plainFullBlockDirectlyOnTsBottomSlabStaysFlushUnanchored(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(6, 2, 6);
        w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos ts = ground.up();
        w.setBlockState(ts, tsBottom(), Block.NOTIFY_LISTENERS);
        BlockPos above = ts.up();
        w.setBlockState(above, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        double dy = SlabSupport.getYOffset(w, above, w.getBlockState(above));
        ctx.assertTrue(Math.abs(dy) <= EPS,
                "an unauthored plain full block above Terrain must stay flush for world-hole safety; "
                        + "got dy=" + dy);
        ctx.complete();
    }

    // ── Player-authored path — the transaction stores the visible half-seat without an anchor ─
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void playerAuthoredFullBlockOnTsBottomSlabPublishesHalfSeat(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(7, 2, 7);
        w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos ts = ground.up();
        w.setBlockState(ts, tsBottom(), Block.NOTIFY_LISTENERS);
        BlockPos above = ts.up();
        ctx.assertTrue(w.getBlockState(above).isAir(), "fixture: the cell above the TS slab must start as air");

        PlayerEntity player = PlacementHarness.mockPlayerHolding(ctx, ts.north(3), new net.minecraft.item.ItemStack(Blocks.STONE.asItem(), 16));
        Vec3d hit = new Vec3d(ts.getX() + 0.5, ts.getY() + 0.5, ts.getZ() + 0.5);
        ActionResult result = PlacementHarness.useHeldItem(w, player, ts, Direction.UP, hit);
        ctx.assertTrue(result.isAccepted(), "fixture: useOn on the TS slab's up face must place, got " + result);

        BlockState placed = w.getBlockState(above);
        ctx.assertTrue(placed.isOf(Blocks.STONE),
                "fixture: stone must land in the cell above the TS slab, got " + placed);
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, above),
                "a transaction-owned numeric height must not widen Terrain anchor propagation");
        double stored = SlabPlacementDyAttachment.storedDy(w, above);
        ctx.assertTrue(Double.doubleToRawLongBits(stored) == Double.doubleToRawLongBits(-0.5d),
                "player-authored full block on Terrain dy=0 must freeze at -0.5; got " + stored);
        double dy = SlabSupport.getYOffset(w, above, w.getBlockState(above));
        ctx.assertTrue(Math.abs(dy - (-0.5d)) <= EPS,
                "player-authored full block must seat on the Terrain bottom slab; got dy=" + dy);

        w.setBlockState(ts, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        ctx.assertTrue(w.getBlockState(above).isOf(Blocks.STONE),
                "removing the authored support must not replace the full block");
        double afterRemoval = SlabSupport.getYOffset(w, above, w.getBlockState(above));
        ctx.assertTrue(Math.abs(afterRemoval - (-0.5d)) <= EPS,
                "the stored authored height must survive later support removal; got " + afterRemoval);
        ctx.complete();
    }

    // An anchored TS slab may keep its own height, but cannot lower an adjacent vanilla slab.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void anchoredTsSlabDoesNotLowerAHorizontallyAdjacentVanillaSlab(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ts = buildTsOnVanillaSlab(ctx);
        SlabAnchorAttachment.addAnchor(w, ts, w.getBlockState(ts));
        SlabAnchorAttachment.freezeLoweredOnPlace(w, ts, w.getBlockState(ts));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, ts),
                "fixture: the TS slab must hold the anchor this test exercises "
                        + "(the same shape tsOnVanillaBottomSlabAnchorsAndSeatsAtHalfDrop pins)");

        BlockPos subjectGround = ts.down(2).north();
        w.setBlockState(subjectGround, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos subject = subjectGround.up(2);
        ctx.assertTrue(subject.equals(ts.north()), "fixture: the subject slab must be horizontally adjacent to the TS slab");
        w.setBlockState(subject, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);

        double dy = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(dy) <= EPS,
                "a vanilla slab horizontally adjacent to an anchored TS slab and resting on "
                        + "ordinary flush ground must stay flush; it must not inherit the TS "
                        + "slab's anchor as a horizontal lowering source, got dy=" + dy);
        ctx.complete();
    }
}
