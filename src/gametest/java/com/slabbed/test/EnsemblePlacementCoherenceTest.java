package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 2a of ENSEMBLE_COHERENCE_DESIGN.md — EMPIRICAL RESULT (2026-07-07): written as a RED against
 * an expected placement-time interpenetration bug (TEST (7) measured hopper/chest stacks clashing at
 * depth 0.5), this suite came back GREEN unfixed — the existing freeze-flat rails ALREADY prevent a
 * new block placed onto a flush face-contact support from sinking into it via a side lane. These two
 * tests therefore stand as permanent PINS of that coherence, and the finding re-scopes the lane: the
 * remaining placement-sourced clashes are the OCCLUDED class (WYSIWYG-correct but invisible — Phase 2b
 * placement-remap territory) and new-LOWER build-order inversions, which have NO lawful coherent
 * candidate under NEVER-POP + WYSIWYG and belong to Phase 3 (render tiling) by law.
 */
public final class EnsemblePlacementCoherenceTest {

    private static final double EPS = 1.0e-6;

    /** Places via the REAL useOn path (mock player), never a setBlock shortcut. */
    private static void place(GameTestHelper helper, ItemStack stack, BlockPos clicked, Direction face) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(clicked).add(0, 0.5, 0), face, clicked, false);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    private static void onPlaced(ServerLevel w, BlockPos pos) {
        SlabAnchorAttachment.addAnchor(w, pos, w.getBlockState(pos));
        SlabAnchorAttachment.freezeLoweredOnPlace(w, pos, w.getBlockState(pos));
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hopperPlacedOnFlushHopperMeetsTheStackNotTheSideCarrier(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        // Flush support hopper on solid ground (frozen flat, the ba80d735 law).
        BlockPos ground = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos support = helper.absolutePos(new BlockPos(2, 2, 2));
        w.setBlock(ground, Blocks.STONE.defaultBlockState(), 2);
        w.setBlock(support, Blocks.HOPPER.defaultBlockState(), 2);
        onPlaced(w, support);
        if (Math.abs(SlabSupport.getYOffset(w, support, w.getBlockState(support))) > EPS) {
            throw helper.assertionException("premise: support hopper must be flush");
        }
        // A LOWERED block-entity beside the upper cell — the side lane that drags a new BE to -0.5
        // (adjacentLoweredBlockEntityMagnitude, the hopper/chest cantilever lane).
        BlockPos sideSlab = helper.absolutePos(new BlockPos(3, 2, 2));
        BlockPos sideBe = helper.absolutePos(new BlockPos(3, 3, 2));
        w.setBlock(sideSlab, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(sideBe, Blocks.HOPPER.defaultBlockState(), 2);
        onPlaced(w, sideBe);
        if (Math.abs(SlabSupport.getYOffset(w, sideBe, w.getBlockState(sideBe)) + 0.5) > EPS) {
            throw helper.assertionException("premise: side hopper on the bottom slab must be lowered -0.5");
        }
        // THE PLACEMENT: a hopper onto the flush support's top face (the measured hopper-under-hopper
        // clash). Its geometric dy is dragged -0.5 by the side BE lane while its support is flush.
        BlockPos placed = support.above();
        place(helper, new ItemStack(Items.HOPPER), support, Direction.UP);
        if (w.getBlockState(placed).isAir()) {
            throw helper.assertionException("premise: the hopper placement must succeed");
        }
        onPlaced(w, placed);
        double dy = SlabSupport.getYOffset(w, placed, w.getBlockState(placed));
        if (Math.abs(dy) > EPS) {
            throw helper.assertionException(
                    "PIN: a block placed onto a FLUSH face-contact support meets its stack (the existing "
                            + "freeze-flat rails guarantee it) — a -0.5 here means an ensemble regression; got dy=" + dy);
        }
        // And the support itself must be untouched (never-pop absolute).
        if (Math.abs(SlabSupport.getYOffset(w, support, w.getBlockState(support))) > EPS) {
            throw helper.assertionException("the existing support must never move");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void clickingOccludedSurfacePlacesIntoTheApparentSpace(GameTestHelper helper) {
        // Phase 2b (the t=98 five-refused-clicks scene, reconstructed): a lowered log carries an
        // OCCLUDED slab (renders level with the log top — its own cell looks empty). Clicking the
        // visible surface must place the new block into the APPARENT space (the cell above the
        // occluded occupant) at the deep-follow dy — instead of vanilla refusing on the occupied cell.
        ServerLevel w = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos log = base.above();
        w.setBlock(base, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(log, Blocks.OAK_LOG.defaultBlockState(), 2);
        onPlaced(w, log);
        if (Math.abs(SlabSupport.getYOffset(w, log, w.getBlockState(log)) + 0.5) > EPS) {
            throw helper.assertionException("premise: the log must be lowered -0.5");
        }
        place(helper, new ItemStack(Items.OAK_SLAB), log, Direction.UP);
        BlockPos occluded = log.above();
        if (w.getBlockState(occluded).isAir()) {
            throw helper.assertionException("premise: the slab must land on the log top");
        }
        onPlaced(w, occluded);
        double slabDy = SlabSupport.getYOffset(w, occluded, w.getBlockState(occluded));
        if (Math.abs(slabDy + 0.5) > EPS
                || !com.slabbed.util.SlabEnsembleCoherence.isOccludedOccupancy(w, occluded, slabDy)) {
            throw helper.assertionException("premise: the slab must be OCCLUDED at -0.5, got dy=" + slabDy);
        }
        // THE CLICK: the t=98 action — up-face of the log, target cell occupied by the invisible slab.
        place(helper, new ItemStack(Items.STONE), log, Direction.UP);
        BlockPos apparent = occluded.above();
        if (w.getBlockState(apparent).isAir()) {
            throw helper.assertionException(
                    "Phase 2b: clicking the visible surface over an OCCLUDED occupant must place into the apparent space, not be refused");
        }
        double dy = SlabSupport.getYOffset(w, apparent, w.getBlockState(apparent));
        if (Math.abs(dy + 1.0) > EPS) {
            throw helper.assertionException("the remapped placement must deep-follow -1.0 to fill the visible gap, got " + dy);
        }
        if (Math.abs(SlabSupport.getYOffset(w, occluded, w.getBlockState(occluded)) + 0.5) > EPS) {
            throw helper.assertionException("the occluded occupant must be untouched (never-pop)");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void nonOccludedOccupiedTargetStillRefuses(GameTestHelper helper) {
        // Surgical guard (the S11 hijack lesson): the remap fires ONLY for occluded occupants — an
        // ordinary occupied target keeps vanilla refusal, and nothing lands anywhere.
        ServerLevel w = helper.getLevel();
        BlockPos ground = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos occupant = ground.above();
        w.setBlock(ground, Blocks.STONE.defaultBlockState(), 2);
        w.setBlock(occupant, Blocks.STONE.defaultBlockState(), 2);
        place(helper, new ItemStack(Items.HOPPER), ground, Direction.UP);
        if (!w.getBlockState(occupant.above()).isAir()) {
            throw helper.assertionException("a non-occluded occupied target must stay a vanilla refusal — nothing may be remapped");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void loweredSupportStillFollowedNormally(GameTestHelper helper) {
        // Control: the coherence rule must ONLY fire on clash shapes — a block on a genuinely LOWERED
        // support keeps following it down (WYSIWYG on the stack), no freeze-flat regression.
        ServerLevel w = helper.getLevel();
        BlockPos slab = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos hopper = slab.above();
        w.setBlock(slab, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        place(helper, new ItemStack(Items.HOPPER), slab, Direction.UP);
        if (w.getBlockState(hopper).isAir()) {
            throw helper.assertionException("premise: hopper placement on the slab must succeed");
        }
        onPlaced(w, hopper);
        double dy = SlabSupport.getYOffset(w, hopper, w.getBlockState(hopper));
        if (Math.abs(dy + 0.5) > EPS) {
            throw helper.assertionException("a hopper on a bottom slab must still follow it to -0.5, got " + dy);
        }
        helper.succeed();
    }
}
