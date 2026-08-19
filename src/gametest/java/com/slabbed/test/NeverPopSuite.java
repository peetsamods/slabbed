package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * The never-pop suite — LAW 1's perturbation coverage: a placed block's height survives every
 * class of neighbour/world change. Subjects x perturbations: the block-palette matrix under
 * support-break, fences, block entities, in-place block-KIND transforms, state-change jitter,
 * and decorative followers. Merged 2026-08-07 from six single-perturbation classes — every test
 * preserved verbatim; four identical copies of bottomSlab()/onPlaced() (the drift-prone shape)
 * deduped to one. See LAW.md; NeighborUpdateInvarianceTest (S-2) remains the law gate itself and
 * is deliberately NOT part of this suite. Original class docs follow, per section.
 */
public final class NeverPopSuite {

    // ═══ the matrix base — LOWERING_CANDIDATES x break-support (NeverPopMatrixTest) ═══
    // /**
    //  * The AUTO half of RELEASE_REGRESSION_TRIGGERS.md never-pop / WYSIWYG law, as a matrix over
    //  * block categories so a port regression on ANY of them fails the suite with zero live testing.
    //  *
    //  * <p>Universal invariant asserted per block: whatever dy a block is placed at (after its
    //  * onPlaced anchor/freeze runs), it STAYS at that dy when its supporting slab is later removed —
    //  * it must not pop up or down. This is exactly the class of bug that keeps regressing per port
    //  * (fences, gates, walls); the matrix locks the whole family at once.
    //  */
    private static final double EPS = 1.0e-6;


    private static final List<BlockState> LOWERING_CANDIDATES = List.of(
            Blocks.STONE.getDefaultState(),
            Blocks.DIRT.getDefaultState(),
            Blocks.GRASS_BLOCK.getDefaultState(),
            Blocks.OAK_PLANKS.getDefaultState(),
            Blocks.OAK_FENCE.getDefaultState(),
            Blocks.SPRUCE_FENCE.getDefaultState(),
            Blocks.OAK_FENCE_GATE.getDefaultState(),
            Blocks.COBBLESTONE_WALL.getDefaultState(),
            Blocks.MOSSY_COBBLESTONE_WALL.getDefaultState(),
            Blocks.GLASS_PANE.getDefaultState(),
            Blocks.IRON_BARS.getDefaultState()
    );

    private static BlockState bottomSlab() {
        return Blocks.POLISHED_TUFF_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static void onPlaced(ServerWorld w, BlockPos pos, BlockState state) {
        SlabAnchorAttachment.addAnchor(w, pos, state);
        SlabAnchorAttachment.freezeLoweredOnPlace(w, pos, state);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void placedBlockNeverPopsWhenSupportRemoved(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        BlockPos pos = slab.up();

        StringBuilder fails = new StringBuilder();
        for (BlockState state : LOWERING_CANDIDATES) {
            w.setBlockState(slab, bottomSlab(), Block.NOTIFY_LISTENERS);
            w.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
            onPlaced(w, pos, w.getBlockState(pos));

            double dyPlaced = SlabSupport.getYOffset(w, pos, w.getBlockState(pos));
            // Remove the support: a geometric (un-locked) block would recompute and pop.
            w.setBlockState(slab, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            double dyAfter = SlabSupport.getYOffset(w, pos, w.getBlockState(pos));

            String id = state.getBlock().getTranslationKey();
            if (Math.abs(dyAfter - dyPlaced) > 1.0e-6) {
                fails.append("\n  ").append(id).append(": placed dy=").append(fmt(dyPlaced))
                        .append(" popped to ").append(fmt(dyAfter));
            }
            // reset
            w.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        ctx.assertTrue(fails.length() == 0,
                "these placed blocks POPPED when their support was removed (never-pop / WYSIWYG):" + fails);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void placedFlatBlockNeverGetsPulledDownByALaterSlab(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        BlockPos pos = ground.up();

        StringBuilder fails = new StringBuilder();
        for (BlockState state : LOWERING_CANDIDATES) {
            // Placed FLAT on solid ground (dy 0).
            w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            w.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
            onPlaced(w, pos, w.getBlockState(pos));
            double dyPlaced = SlabSupport.getYOffset(w, pos, w.getBlockState(pos));

            // Now put a bottom slab under it (replace the ground) — a live geometric block would
            // inherit -0.5; a frozen-flat one must stay put.
            w.setBlockState(ground, bottomSlab(), Block.NOTIFY_LISTENERS);
            double dyAfter = SlabSupport.getYOffset(w, pos, w.getBlockState(pos));

            String id = state.getBlock().getTranslationKey();
            if (Math.abs(dyAfter - dyPlaced) > 1.0e-6) {
                fails.append("\n  ").append(id).append(": flat dy=").append(fmt(dyPlaced))
                        .append(" pulled down to ").append(fmt(dyAfter));
            }
            w.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        ctx.assertTrue(fails.length() == 0,
                "these flat-placed blocks got PULLED DOWN by a later slab (never-pop / WYSIWYG):" + fails);
        ctx.complete();
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }

    // ═══ fences (connecting shape) (FenceNeverPopTest) ═══
    // /**
    //  * WYSIWYG / never-pop for fences, walls, panes and gates. Recorder session bb138275 caught
    //  * these popping between dy -0.5 and 0.0 as neighbours changed — because they are not solid
    //  * blocks, {@code isOrdinaryAnchorCandidate} excluded them, so they were never anchored or
    //  * frozen and their dy stayed live (geometric). A placed connecting block must stay put.
    //  */

    /** Simulate what BlockOnPlacedAnchorMixin.onPlaced does, server-side. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fencePlacedLoweredOnSlabIsHeightLocked(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        BlockPos fence = slab.up();
        w.setBlockState(slab, bottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(fence, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);
        onPlaced(w, fence, w.getBlockState(fence));

        double dyPlaced = SlabSupport.getYOffset(w, fence, w.getBlockState(fence));
        ctx.assertTrue(Math.abs(dyPlaced + 0.5) < 1.0e-6, "a fence on a bottom slab is placed lowered -0.5, got " + dyPlaced);
        ctx.assertTrue(
                SlabAnchorAttachment.isAnchored(w, fence) || SlabAnchorAttachment.isFrozenFlat(w, fence),
                "a fence placed lowered MUST be height-locked (anchored or frozen), else it pops (WYSIWYG)");

        // The actual pop scenario: remove the slab below. A geometric fence would recompute to 0.0
        // and pop UP; a height-locked one stays at -0.5.
        w.setBlockState(slab, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        double dyAfter = SlabSupport.getYOffset(w, fence, w.getBlockState(fence));
        ctx.assertTrue(Math.abs(dyAfter + 0.5) < 1.0e-6,
                "the placed fence must STAY at -0.5 after its support changes, got " + dyAfter + " (it popped)");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void wallPlacedLoweredOnSlabIsHeightLocked(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        BlockPos wall = slab.up();
        w.setBlockState(slab, bottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(wall, Blocks.COBBLESTONE_WALL.getDefaultState(), Block.NOTIFY_LISTENERS);
        onPlaced(w, wall, w.getBlockState(wall));
        ctx.assertTrue(
                SlabAnchorAttachment.isAnchored(w, wall) || SlabAnchorAttachment.isFrozenFlat(w, wall),
                "a wall placed lowered MUST be height-locked");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void anchoredFenceSurvivesAConnectionStateUpdate(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        BlockPos fence = slab.up();
        w.setBlockState(slab, bottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(fence, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);
        onPlaced(w, fence, w.getBlockState(fence));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, fence) || SlabAnchorAttachment.isFrozenFlat(w, fence),
                "precondition: fence is height-locked after placement");

        // Mutate a connection property (what a neighbour update does) — same block kind.
        BlockState connected = w.getBlockState(fence).with(Properties.NORTH, true);
        w.setBlockState(fence, connected, Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, fence) || SlabAnchorAttachment.isFrozenFlat(w, fence),
                "the height-lock MUST survive a fence connection-state update (property-only change)");
        double dy = SlabSupport.getYOffset(w, fence, w.getBlockState(fence));
        ctx.assertTrue(Math.abs(dy + 0.5) < 1.0e-6, "still -0.5 after the connection update, got " + dy);
        ctx.complete();
    }

    // ═══ block entities (BlockEntityNeverPopTest) ═══
    // /**
    //  * The reported hopper snap: a block-entity block (hopper/chest/furnace) placed lowered on a slab
    //  * re-derived its dy from a live column walk (it was excluded from the never-pop anchor lanes), so
    //  * editing the column below toggled it between 0 and -0.5 — "places too high, then snaps down when
    //  * a block is placed underneath". A placed block entity must be height-locked like any other block.
    //  * Ceiling-hung block entities (hanging signs) must stay EXCLUDED — they follow their support.
    //  */

    private static void loweredBeIsLocked(TestContext ctx, BlockState be, String label) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        BlockPos pos = slab.up();
        w.setBlockState(slab, bottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(pos, be, Block.NOTIFY_LISTENERS);
        onPlaced(w, pos, w.getBlockState(pos));

        double placed = SlabSupport.getYOffset(w, pos, w.getBlockState(pos));
        ctx.assertTrue(placed < -1.0e-6, label + " on a bottom slab must place lowered, got " + placed);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, pos),
                label + " placed lowered MUST be height-locked (else it snaps when the column changes)");

        // Edit the column below WITHOUT touching the block entity — a geometric one would toggle.
        w.setBlockState(slab, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        double after = SlabSupport.getYOffset(w, pos, w.getBlockState(pos));
        ctx.assertTrue(Math.abs(after - placed) < 1.0e-6,
                label + " must STAY at " + placed + " when the cell below changes, got " + after + " (snap)");

        w.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(slab, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hopperLoweredOnSlabIsHeightLocked(TestContext ctx) {
        loweredBeIsLocked(ctx, Blocks.HOPPER.getDefaultState(), "hopper");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chestAndFurnaceLoweredOnSlabAreHeightLocked(TestContext ctx) {
        loweredBeIsLocked(ctx, Blocks.CHEST.getDefaultState(), "chest");
        loweredBeIsLocked(ctx, Blocks.FURNACE.getDefaultState(), "furnace");
        loweredBeIsLocked(ctx, Blocks.BARREL.getDefaultState(), "barrel");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void flatHopperIsNotPulledDownByASlabShovedUnder(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        BlockPos pos = ground.up();
        w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(pos, Blocks.HOPPER.getDefaultState(), Block.NOTIFY_LISTENERS);
        onPlaced(w, pos, w.getBlockState(pos));
        double placed = SlabSupport.getYOffset(w, pos, w.getBlockState(pos));
        ctx.assertTrue(Math.abs(placed) < 1.0e-6, "hopper placed flat must be dy 0, got " + placed);

        w.setBlockState(ground, bottomSlab(), Block.NOTIFY_LISTENERS);
        double after = SlabSupport.getYOffset(w, pos, w.getBlockState(pos));
        ctx.assertTrue(Math.abs(after) < 1.0e-6,
                "a flat-placed hopper must NOT be pulled down by a slab added under it, got " + after);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingSignBlockEntityIsNotAnchored(TestContext ctx) {
        // Regression guard: a hanging sign is a block entity too, but it HANGS and must keep
        // following its support — it must NOT be height-locked by the new block-entity lane.
        ServerWorld w = ctx.getWorld();
        BlockPos support = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos pos = support.down();
        w.setBlockState(support, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(pos, Blocks.OAK_HANGING_SIGN.getDefaultState(), Block.NOTIFY_LISTENERS);
        onPlaced(w, pos, w.getBlockState(pos));
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, pos),
                "a hanging sign (ceiling-hung block entity) must NOT be anchored — it follows its support");
        ctx.assertTrue(!SlabAnchorAttachment.isFrozenFlat(w, pos),
                "a hanging sign must NOT be frozen-flat either");
        ctx.complete();
    }

    // ═══ in-place block-KIND transforms (pot<->potted) (PottedTransformNeverPopTest) ═══
    // /**
    //  * BUG B (live 2026-08-06):
    //  * <b>a placed block JUMPS when the pot beneath it is potted/unpotted</b> — a never-pop violation.
    //  *
    //  * <p><b>Live evidence, one position, repeatedly.</b> {@code (203,-54,-34) minecraft:stone} went
    //  * {@code -0.5 → 0.0 → 0.0 → -0.5 → -0.5 → -0.5 → 0.0 → 0.0}, correlating EXACTLY with the block
    //  * below it:
    //  * <ul>
    //  *   <li>below = {@code minecraft:flower_pot} (empty) → the stone reads {@code -0.5};</li>
    //  *   <li>below = {@code minecraft:potted_cornflower} → the stone reads {@code 0.0}.</li>
    //  * </ul>
    //  * The pot's OWN dy is {@code -0.500} in both frames — the support never moved. Only its block
    //  * IDENTITY changed, and the block resting on it jumped half a block.
    //  *
    //  * <p><b>Root cause.</b> {@code SlabSupport.hasSlabInColumn}'s per-cell lowering-source test was
    //  * {@code SlabAnchorAttachment.isAnchored(world, cursor)} — <b>an anchor boolean standing in for
    //  * "is this cell lowered"</b>, the same bug class as L13/L14/L15. Potting a flower is an in-place
    //  * block-KIND change ({@code flower_pot} → {@code potted_*}), so {@code onStateReplaced} fires and
    //  * {@code replacementPreservesAnchor} clears the pot's anchor (internal-notes 1j) —
    //  * while the pot's RENDERED HEIGHT is untouched, because it comes from a wholly different lane
    //  * (the "non-solid object standing on a lowered full-block support" branch of
    //  * {@code getYOffsetInner}). The flag vanished, the geometry did not, and everything stacked above
    //  * re-derived to {@code 0.0}.
    //  *
    //  * <p><b>Fix.</b> {@code hasSlabInColumn} and its magnitude twin {@code slabColumnYOffset} now also
    //  * accept a cell that is a {@code isLoweredStandingObject} — a standing OBJECT whose own support
    //  * resolves lowered — so the column's answer is a function of RESOLVED HEIGHT, not of which flower
    //  * is in the pot. internal-notes 1j (the pot losing its own anchor) is deliberately NOT
    //  * repaired here; this makes the recorded sequence impossible regardless of it.
    //  */

    /**
     * RED — <b>the recorded sequence, on the recorded anchor state</b>: the pot is
     * {@code ANCHORED} and the stone above it is {@code anchor=none}, exactly as every
     * {@code (203,-55,-34)} / {@code (203,-54,-34)} frame reports. Place the stone, assert its dy,
     * pot the flower (an in-place state swap on the SUPPORT), assert the dy is UNCHANGED. That is
     * the never-pop assertion.
     *
     * <p>The unanchored stone is what makes this reachable: it re-derives on every neighbour
     * change, and before the fix the only thing making it read {@code -0.5} was the pot's ANCHOR
     * FLAG — which potting clears.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void geometricBlockAbovePotKeepsItsDyWhenTheFlowerIsPotted(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos pot = buildPotOnLoweredColumn(ctx, 1, 1, true);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, pot),
                "fixture: the pot must start ANCHORED, as the recorder reports for (203,-55,-34)");

        BlockPos subject = pot.up();
        place(w, subject, Blocks.STONE.getDefaultState());
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, subject),
                "fixture: the subject must carry NO anchor, as the recorder reports for "
                        + "(203,-54,-34) — that is WHY it re-derives on every neighbour change");
        double before = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(before + 0.5) <= EPS,
                "fixture: the stone resting on the lowered flower_pot must start at -0.5, got "
                        + before);

        // Potting: FlowerPotBlock's own use action is a plain in-place setBlockState to potted_*.
        place(w, pot, Blocks.POTTED_CORNFLOWER.getDefaultState());

        double potDy = SlabSupport.getYOffset(w, pot, w.getBlockState(pot));
        ctx.assertTrue(Math.abs(potDy + 0.5) <= EPS,
                "fixture: THE SUPPORT DID NOT MOVE — the potted pot must still render -0.5 (the "
                        + "recorder shows -0.500 in both frames), got " + potDy);

        double after = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(after - before) <= EPS,
                "NEVER-POP: potting the flower below must not move the block above. Was " + before
                        + ", now " + after + " (live (203,-54,-34): -0.5 -> 0.0 the instant "
                        + "flower_pot became potted_cornflower, while the pot's own dy stayed "
                        + "-0.500 — hasSlabInColumn used the pot's ANCHOR FLAG as a proxy for "
                        + "'is this cell lowered', and potting clears that flag)"
                        + " [subjectAnchored=" + SlabAnchorAttachment.isAnchored(w, subject)
                        + " potAnchored=" + SlabAnchorAttachment.isAnchored(w, pot) + "]");
        ctx.complete();
    }

    /**
     * RED (real placement path) — same sequence, but the pot and the stone are placed through
     * {@code onPlaced}, i.e. exactly what a player click fires. Pins that the anchor lanes do not
     * silently rescue the geometric defect (and that whichever lane does fire is not itself
     * identity-dependent).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void placedBlockAbovePotKeepsItsDyWhenTheFlowerIsPotted(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos pot = buildPotOnLoweredColumn(ctx, 4, 1, true);

        BlockPos subject = pot.up();
        placeWithOnPlaced(w, subject, Blocks.STONE.getDefaultState());
        double before = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(before + 0.5) <= EPS,
                "fixture: the stone placed on the lowered flower_pot must start at -0.5, got "
                        + before);

        place(w, pot, Blocks.POTTED_CORNFLOWER.getDefaultState());

        double after = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(after - before) <= EPS,
                "NEVER-POP (player-placed): potting the flower below must not move the block above. "
                        + "Was " + before + ", now " + after
                        + " [subjectAnchored=" + SlabAnchorAttachment.isAnchored(w, subject)
                        + " potAnchored=" + SlabAnchorAttachment.isAnchored(w, pot) + "]");
        ctx.complete();
    }

    /**
     * The other direction of the recorded oscillation: TAKING the flower out
     * ({@code potted_* → flower_pot}) must not move the block above either. The pot starts
     * unanchored here, so this cell rests purely on the geometric lane.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void blockAbovePotKeepsItsDyWhenTheFlowerIsRemoved(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos pot = buildPotOnLoweredColumn(ctx, 1, 4, false);
        place(w, pot, Blocks.POTTED_CORNFLOWER.getDefaultState());
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, pot),
                "fixture: no anchor anywhere — this cell exercises the GEOMETRIC lane");

        BlockPos subject = pot.up();
        place(w, subject, Blocks.STONE.getDefaultState());
        double before = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));

        place(w, pot, Blocks.FLOWER_POT.getDefaultState());
        double after = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));

        ctx.assertTrue(Math.abs(after - before) <= EPS,
                "NEVER-POP: emptying the pot below must not move the block above. Was " + before
                        + ", now " + after);
        ctx.assertTrue(Math.abs(after + 0.5) <= EPS,
                "and the settled value must be -0.5 (the whole column is lowered), got " + after);
        ctx.complete();
    }

    /**
     * GitHub #67 ("pots falling when breaking the blocks beneath them"): a pot must survive its
     * support being broken, because vanilla FlowerPotBlock/DecoratedPotBlock never override
     * canPlaceAt (the inherited AbstractBlock default is always true), so neither ever requires
     * support or self-destructs on a neighbour update. Reuses the lowered-column pot fixture; the
     * support broken here is the cell directly beneath the pot, not the slab beneath that.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void potSurvivesWhenItsSupportIsBroken(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos pot = buildPotOnLoweredColumn(ctx, 4, 4, true);
        BlockPos support = pot.down();

        double dyBefore = SlabSupport.getYOffset(w, pot, w.getBlockState(pot));
        ctx.assertTrue(Math.abs(dyBefore + 0.5) <= EPS,
                "fixture: the placed pot must start at -0.5, got " + dyBefore);

        // Break the block directly beneath the pot with a real neighbour update, exactly like the
        // other never-pop "remove support" rows in this suite.
        w.setBlockState(support, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);

        BlockState potAfterState = w.getBlockState(pot);
        ctx.assertTrue(potAfterState.isOf(Blocks.FLOWER_POT),
                "#67: a pot must survive its support breaking (vanilla FlowerPotBlock has no "
                        + "canPlaceAt override, so it never needs support); found " + potAfterState
                        + " at " + pot + " instead of flower_pot");

        double dyAfter = SlabSupport.getYOffset(w, pot, w.getBlockState(pot));
        ctx.assertTrue(Math.abs(dyAfter - dyBefore) <= EPS,
                "#67: the surviving pot's height must not change when its support breaks. Was "
                        + dyBefore + ", now " + dyAfter
                        + " [potAnchored=" + SlabAnchorAttachment.isAnchored(w, pot) + "]");
        ctx.complete();
    }

    // ------------------------------------------------------------------------

    /**
     * Builds the live {@code (203,-57..-55,-34)} column at plot-relative {@code (x, z)}:
     * {@code stone_slab(BOTTOM) / stone / flower_pot}, and returns the pot. Hard-asserts the
     * support stone at {@code -0.5} and the pot at {@code -0.5} so no cell can pass vacuously.
     */
    private BlockPos buildPotOnLoweredColumn(TestContext ctx, int x, int z, boolean placePot) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(x, 1, z);
        place(w, slab, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));

        BlockPos support = slab.up();
        place(w, support, Blocks.STONE.getDefaultState());
        double supportDy = SlabSupport.getYOffset(w, support, w.getBlockState(support));
        ctx.assertTrue(Math.abs(supportDy + 0.5) <= EPS,
                "fixture: the stone under the pot must render -0.5, got " + supportDy);

        BlockPos pot = support.up();
        if (placePot) {
            placeWithOnPlaced(w, pot, Blocks.FLOWER_POT.getDefaultState());
        } else {
            place(w, pot, Blocks.FLOWER_POT.getDefaultState());
        }
        double potDy = SlabSupport.getYOffset(w, pot, w.getBlockState(pot));
        ctx.assertTrue(Math.abs(potDy + 0.5) <= EPS,
                "fixture: the flower_pot must render -0.5 (live (203,-55,-34) visualDy=-0.500), got "
                        + potDy);
        return pot;
    }

    private static void place(ServerWorld w, BlockPos pos, BlockState state) {
        w.setBlockState(pos, state, Block.NOTIFY_ALL);
    }

    private static void placeWithOnPlaced(ServerWorld w, BlockPos pos, BlockState state) {
        w.setBlockState(pos, state, Block.NOTIFY_ALL);
        state.getBlock().onPlaced(w, pos, state, null, ItemStack.EMPTY);
    }

    // ═══ state-change jitter (grass->dirt conversions) (StateChangeAnchorTest) ═══
    // /**
    //  * State-change jitter (the maintainer's grass-tower report): a grass block anchored/lowered on a slab
    //  * converts to DIRT in place (a block-KIND change firing onStateReplaced). The anchor must
    //  * SURVIVE an in-place transform to another anchor-eligible block, or the dirt un-lowers and
    //  * jitters/merges/jumps. A genuine break (-> air) must still clear the anchor.
    //  */

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void inPlaceTransformKeepsAnchorAndDy(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        BlockPos block = slab.up();
        w.setBlockState(slab, bottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(block, Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
        onPlaced(w, block, w.getBlockState(block));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, block),
                "precondition: grass block on a bottom slab is anchored");
        double before = SlabSupport.getYOffset(w, block, w.getBlockState(block));

        // Grass -> dirt: a block-KIND change at the SAME position (the tower conversion).
        // MUST use NOTIFY_ALL so vanilla actually fires AbstractBlock.onStateReplaced (the
        // path the anchor-clear mixin hooks) — NOTIFY_LISTENERS does not, which false-greened
        // an earlier version of this test.
        w.setBlockState(block, Blocks.DIRT.getDefaultState(), Block.NOTIFY_ALL);

        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, block),
                "the anchor MUST survive an in-place grass->dirt transform (WYSIWYG, no jitter)");
        double after = SlabSupport.getYOffset(w, block, w.getBlockState(block));
        ctx.assertTrue(Math.abs(after - before) < 1.0e-6,
                "dy must not jump on the in-place transform: before=" + before + " after=" + after);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void genuineBreakToAirClearsTheAnchor(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        BlockPos block = slab.up();
        w.setBlockState(slab, bottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(block, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        onPlaced(w, block, w.getBlockState(block));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, block), "precondition: stone anchored");

        // A real break MUST clear the anchor so a fresh placement re-evaluates. Use breakBlock
        // (the player-break path that actually fires onStateReplaced), not a raw setBlockState.
        w.breakBlock(block, false);
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, block),
                "breaking the block MUST clear its anchor");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void replaceWithNonAnchorBlockClearsTheAnchor(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        BlockPos block = slab.up();
        w.setBlockState(slab, bottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(block, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        onPlaced(w, block, w.getBlockState(block));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, block), "precondition: stone anchored");

        // Break, then place a non-ordinary block (a slab) — the anchor must not linger stale.
        w.breakBlock(block, false);
        w.setBlockState(block, bottomSlab(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, block),
                "after a break the anchor must be gone (no stale anchor under the new slab)");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void inPlaceReplaceToNonLockBlockClearsTheAnchor(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        BlockPos block = slab.up();
        w.setBlockState(slab, bottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(block, Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
        onPlaced(w, block, w.getBlockState(block));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, block), "precondition: grass anchored");

        // Directly replace grass with a SLAB in place (NOTIFY_ALL fires onStateReplaced). A slab
        // is NOT a lock-eligible block, so the gate must CLEAR the stale grass anchor rather than
        // preserve it — the new slab must re-evaluate its own dy from scratch.
        w.setBlockState(block, bottomSlab(), Block.NOTIFY_ALL);
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, block),
                "replacing the anchored full block with a slab (non-lock) MUST clear the anchor");
        ctx.complete();
    }

    // ═══ decorative followers on anchored supports (DecorativeObjectSupportAnchorTest) ═══
    // /**
    //  * Live-reported bug (2026-07-04 recorder session, "pop upon breaking at the end"): a decorative,
    //  * non-solid, non-connecting object resting on top of a lowered/flush-bottom-slab support popped
    //  * from -0.5 to 0.0 the instant that support was broken, having been {@code anchor=none} for its
    //  * entire lifetime.
    //  *
    //  * <ul>
    //  *   <li><b>Candle on a birch bottom slab</b> ({@code (1,-59,27)} in the recorder): the SLAB below
    //  *       was itself flush (not lowered) — a candle resting DIRECTLY on any bottom slab already
    //  *       gets -0.5 via {@code SlabSupport.shouldOffset}'s generic branch, this mod's oldest, most
    //  *       basic "object sits on a slab" mechanic. Breaking that slab popped the candle to 0.0.</li>
    //  *   <li><b>Birch trapdoor on a lowered/anchored spruce fence</b> ({@code (1,-58,30)}): the fence
    //  *       below WAS anchored/lowered (-0.5). Breaking the fence popped the trapdoor to 0.0.</li>
    //  * </ul>
    //  *
    //  * <p>Root cause: candles, trapdoors, and every other non-solid, non-connecting decorative object
    //  * are rejected by {@code isOrdinaryAnchorCandidate} (not a solid block), are not a
    //  * {@code SlabBlock} or {@code BlockEntityProvider}, so NONE of the existing anchor qualifier
    //  * lanes cover them — their -0.5 was PURELY a live read with no persisted anchor at all, unlike
    //  * every other subject category this mod protects.
    //  *
    //  * <p>Fix: {@code qualifiesForDecorativeObjectAnchor} — a subject not already covered by another
    //  * lane, not a ceiling-hanging/follow-type decoration (which must keep dynamically tracking its
    //  * support), whose live {@code getYOffset} is negative, now anchors at placement.
    //  */

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void candleRestingOnBottomSlabDoesNotPopWhenSlabBreaks(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos candlePos = slabPos.up();

        w.setBlockState(slabPos, Blocks.BIRCH_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(candlePos, Blocks.CANDLE.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, candlePos, w.getBlockState(candlePos));

        double candleDyBefore = SlabSupport.getYOffset(w, candlePos, w.getBlockState(candlePos));
        ctx.assertTrue(Math.abs(candleDyBefore + 0.5) <= EPS,
                "setup: candle resting on a bottom slab should render -0.5, got " + candleDyBefore);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, candlePos),
                "THE FIX: a candle resting on a bottom slab must anchor at placement time, or "
                        + "breaking the slab later pops it back to flush "
                        + "(live-reported 'pop upon breaking at the end')");

        w.setBlockState(slabPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        double candleDyAfter = SlabSupport.getYOffset(w, candlePos, w.getBlockState(candlePos));
        ctx.assertTrue(Math.abs(candleDyAfter + 0.5) <= EPS,
                "never-pop violation: candle popped from -0.5 to " + candleDyAfter
                        + " after its support was broken, even though it was never re-placed");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void trapdoorRestingOnLoweredFenceDoesNotPopWhenFenceBreaks(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos vanillaBottomSlabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 4);
        BlockPos dirtPos = vanillaBottomSlabPos.up();
        BlockPos fencePos = dirtPos.east();
        BlockPos trapdoorPos = fencePos.up();

        w.setBlockState(vanillaBottomSlabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(dirtPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, dirtPos, w.getBlockState(dirtPos));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, dirtPos), "setup: dirt must anchor on the bottom slab");

        w.setBlockState(fencePos, Blocks.SPRUCE_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, fencePos, w.getBlockState(fencePos));
        double fenceDy = SlabSupport.getYOffset(w, fencePos, w.getBlockState(fencePos));
        ctx.assertTrue(Math.abs(fenceDy + 0.5) <= EPS,
                "setup: fence beside anchored dirt should render -0.5, got " + fenceDy);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, fencePos), "setup: fence must anchor via the horizontal-adjacency lane");

        w.setBlockState(trapdoorPos, Blocks.BIRCH_TRAPDOOR.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, trapdoorPos, w.getBlockState(trapdoorPos));
        double trapdoorDyBefore = SlabSupport.getYOffset(w, trapdoorPos, w.getBlockState(trapdoorPos));
        ctx.assertTrue(Math.abs(trapdoorDyBefore + 0.5) <= EPS,
                "setup: trapdoor resting on the lowered fence should render -0.5, got " + trapdoorDyBefore);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, trapdoorPos),
                "THE FIX: a trapdoor resting on a lowered fence must anchor at placement time, or "
                        + "breaking the fence later pops it back to flush");

        w.setBlockState(fencePos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        double trapdoorDyAfter = SlabSupport.getYOffset(w, trapdoorPos, w.getBlockState(trapdoorPos));
        ctx.assertTrue(Math.abs(trapdoorDyAfter + 0.5) <= EPS,
                "never-pop violation: trapdoor popped from -0.5 to " + trapdoorDyAfter
                        + " after its support was broken, even though it was never re-placed");
        ctx.complete();
    }

    // REGRESSION GUARD: a flat (never-lowered) candle must never gain a spurious anchor.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void flatCandleNeverAnchors(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 5);
        w.setBlockState(pos, Blocks.CANDLE.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, pos, w.getBlockState(pos));
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, pos),
                "regression: a candle on ordinary ground (not lowered) must not anchor");
        ctx.complete();
    }

    // REGRESSION GUARD: a hanging lantern (ceiling-attached, must keep dynamically following its
    // support) must NOT be captured by this new lane, or it would freeze instead of tracking.
    // The lantern's support (the dirt above it) must itself be genuinely lowered, or the lantern's
    // own dy would already be 0 for an unrelated reason and this test would prove nothing.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingLanternNeverAnchorsViaDecorativeLane(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos vanillaBottomSlabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 6);
        BlockPos dirtAnchorPos = vanillaBottomSlabPos.up();
        BlockPos supportPos = dirtAnchorPos.east();   // stone beside anchored dirt, air below it (cantilevered)
        BlockPos lanternPos = supportPos.down();      // lantern hangs from supportPos's underside

        w.setBlockState(vanillaBottomSlabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(dirtAnchorPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, dirtAnchorPos, w.getBlockState(dirtAnchorPos));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, dirtAnchorPos), "setup: dirt must anchor on the bottom slab");

        w.setBlockState(supportPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, supportPos, w.getBlockState(supportPos));
        double supportDy = SlabSupport.getYOffset(w, supportPos, w.getBlockState(supportPos));
        ctx.assertTrue(Math.abs(supportDy + 0.5) <= EPS, "setup: cantilevered stone beside anchored dirt should read -0.5, got " + supportDy);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, supportPos), "setup: cantilevered stone must anchor via the adjacent-lowered-full-block lane");

        w.setBlockState(lanternPos, Blocks.LANTERN.getDefaultState().with(net.minecraft.state.property.Properties.HANGING, true),
                Block.NOTIFY_LISTENERS);
        double lanternDy = SlabSupport.getYOffset(w, lanternPos, w.getBlockState(lanternPos));
        ctx.assertTrue(Math.abs(lanternDy + 0.5) <= EPS,
                "setup: hanging lantern below the anchored support must itself read -0.5 "
                        + "(hanger-follow), got " + lanternDy + " -- otherwise this test proves nothing");

        SlabAnchorAttachment.addAnchor(w, lanternPos, w.getBlockState(lanternPos));
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, lanternPos),
                "regression: a hanging lantern must keep dynamically following its support, not freeze via the decorative lane");
        ctx.complete();
    }
}
