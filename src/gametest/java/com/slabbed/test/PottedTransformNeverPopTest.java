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
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * BUG B (live 2026-08-06):
 * <b>a placed block JUMPS when the pot beneath it is potted/unpotted</b> — a never-pop violation.
 *
 * <p><b>Live evidence, one position, repeatedly.</b> {@code (203,-54,-34) minecraft:stone} went
 * {@code -0.5 → 0.0 → 0.0 → -0.5 → -0.5 → -0.5 → 0.0 → 0.0}, correlating EXACTLY with the block
 * below it:
 * <ul>
 *   <li>below = {@code minecraft:flower_pot} (empty) → the stone reads {@code -0.5};</li>
 *   <li>below = {@code minecraft:potted_cornflower} → the stone reads {@code 0.0}.</li>
 * </ul>
 * The pot's OWN dy is {@code -0.500} in both frames — the support never moved. Only its block
 * IDENTITY changed, and the block resting on it jumped half a block.
 *
 * <p><b>Root cause.</b> {@code SlabSupport.hasSlabInColumn}'s per-cell lowering-source test was
 * {@code SlabAnchorAttachment.isAnchored(world, cursor)} — <b>an anchor boolean standing in for
 * "is this cell lowered"</b>, the same bug class as L13/L14/L15. Potting a flower is an in-place
 * block-KIND change ({@code flower_pot} → {@code potted_*}), so {@code onStateReplaced} fires and
 * {@code replacementPreservesAnchor} clears the pot's anchor (internal-notes 1j) —
 * while the pot's RENDERED HEIGHT is untouched, because it comes from a wholly different lane
 * (the "non-solid object standing on a lowered full-block support" branch of
 * {@code getYOffsetInner}). The flag vanished, the geometry did not, and everything stacked above
 * re-derived to {@code 0.0}.
 *
 * <p><b>Fix.</b> {@code hasSlabInColumn} and its magnitude twin {@code slabColumnYOffset} now also
 * accept a cell that is a {@code isLoweredStandingObject} — a standing OBJECT whose own support
 * resolves lowered — so the column's answer is a function of RESOLVED HEIGHT, not of which flower
 * is in the pot. internal-notes 1j (the pot losing its own anchor) is deliberately NOT
 * repaired here; this makes the recorded sequence impossible regardless of it.
 */
public final class PottedTransformNeverPopTest {

    private static final double EPS = 1.0e-6;

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
}
