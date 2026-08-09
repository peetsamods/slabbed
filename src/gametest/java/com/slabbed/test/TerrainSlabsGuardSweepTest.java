package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.compat.CompatHooks;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

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
 * OWN qualifying shapes and are not these fixtures. A positive control proves the guard does not
 * over-block: a vanilla follower ON a TS surface must still lower — that is the compat feature.
 *
 * <p><b>Confirmed exception, live-ruled (maintainer, 2026-08-09):</b> a TS-owned slab placed
 * VERTICALLY on a vanilla bottom slab is one of the deliberate exceptions above, not a leak. Live
 * pass in the dev environment confirmed the TS slab visually seats flush on the vanilla slab's
 * top face — real WYSIWYG seating, not the double-offset stacking this sweep otherwise guards
 * against. {@link #tsOnVanillaBottomSlabAnchorsAndSeatsAtHalfDrop} pins the anchored `dy=-0.5`
 * reading as the correct, intended result for this exact shape.
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

    // ── positive control: the guard must not over-block ──────────────────────────────────────
    // A vanilla follower ON a TS bottom surface lowering to seat on it IS the compat feature; a
    // sweep that passed with the guard over-applied would be a false green.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void vanillaFollowerOnTsSurfaceStillLowers(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(5, 2, 5);
        w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos ts = ground.up();
        w.setBlockState(ts, tsBottom(), Block.NOTIFY_LISTENERS);
        BlockPos follower = ts.up();
        w.setBlockState(follower, Blocks.LANTERN.getDefaultState(), Block.NOTIFY_LISTENERS);
        double dy = SlabSupport.getYOffset(w, follower, w.getBlockState(follower));
        ctx.assertTrue(dy < -EPS,
                "CONTROL: a vanilla follower resting on a TS bottom surface must still lower "
                        + "(the guard protects the TS block's OWN dy, never its followers), got " + dy);
        ctx.complete();
    }
}
