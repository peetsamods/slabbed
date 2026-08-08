package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * D1 port (audit STATE_DEFENSE_DIVERGENCE_2026-07-07, donor: 1.21.11 commit {@code 78ec0ac4} +
 * {@code StateChangeAnchorTest}): the state-change jitter defense. An in-place block-KIND transform to
 * another lock-eligible block (grass_block → dirt from a random tick, log → stripped, copper
 * oxidation) MUST keep the height-lock — on unfixed 26.2 the removal hook strips ALL seven attachments
 * unconditionally, so blocks placed on the maintainer's grass terrain un-lower/jitter WITH NO PLAYER ACTION. A
 * genuine break (→ air), a fluid, or a replacement with a non-lock block (a slab) must still clear.
 *
 * <p>Donor lesson preserved verbatim: the transform setBlock MUST use {@code Block.UPDATE_ALL} —
 * vanilla only fires the removal hook when (kind changed && (flags & 1)); UPDATE_LISTENERS-only
 * false-greened an earlier donor version (the NOTIFY_ALL vs NOTIFY_LISTENERS gametest lesson).
 */
public final class StateChangeAnchorTest {

    private static final double EPS = 1.0e-6;

    private static BlockState bottomSlab() {
        return Blocks.POLISHED_TUFF_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static void onPlaced(ServerLevel w, BlockPos pos) {
        SlabAnchorAttachment.addAnchor(w, pos, w.getBlockState(pos));
        SlabAnchorAttachment.freezeLoweredOnPlace(w, pos, w.getBlockState(pos));
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void inPlaceTransformKeepsAnchorAndDy(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos slab = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos block = slab.above();
        w.setBlock(slab, bottomSlab(), Block.UPDATE_CLIENTS);
        w.setBlock(block, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        onPlaced(w, block);
        if (!SlabAnchorAttachment.isAnchored(w, block)) {
            throw helper.assertionException("precondition: grass block on a bottom slab is anchored");
        }
        double before = SlabSupport.getYOffset(w, block, w.getBlockState(block));

        // Grass -> dirt: a block-KIND change at the SAME position (the tower conversion), with
        // UPDATE_ALL so the removal hook actually fires (the donor's false-green lesson).
        w.setBlock(block, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);

        if (!SlabAnchorAttachment.isAnchored(w, block)) {
            throw helper.assertionException(
                    "D1: the anchor MUST survive an in-place grass->dirt transform (WYSIWYG, no jitter)");
        }
        double after = SlabSupport.getYOffset(w, block, w.getBlockState(block));
        if (Math.abs(after - before) > EPS) {
            throw helper.assertionException(
                    "D1: dy must not jump on the in-place transform: before=" + before + " after=" + after);
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void inPlaceTransformKeepsFrozenFlat(GameTestHelper helper) {
        // 26.2 addition (audit D1 inverse case): FROZEN_FLAT must survive the same transform — the
        // unconditional strip also killed freeze-flat, letting live cantilever/column lanes sink a
        // converted block -0.5 later.
        ServerLevel w = helper.getLevel();
        BlockPos ground = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos block = ground.above();
        w.setBlock(ground, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        w.setBlock(block, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        onPlaced(w, block);
        if (!SlabAnchorAttachment.isFrozenFlat(w, block)) {
            throw helper.assertionException("precondition: flat-placed grass block is FROZEN_FLAT");
        }
        w.setBlock(block, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        if (!SlabAnchorAttachment.isFrozenFlat(w, block)) {
            throw helper.assertionException(
                    "D1: FROZEN_FLAT MUST survive an in-place grass->dirt transform (no later sink via live lanes)");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void genuineBreakToAirClearsTheAnchor(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos slab = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos block = slab.above();
        w.setBlock(slab, bottomSlab(), Block.UPDATE_CLIENTS);
        w.setBlock(block, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        onPlaced(w, block);
        if (!SlabAnchorAttachment.isAnchored(w, block)) {
            throw helper.assertionException("precondition: stone anchored");
        }
        // A real break MUST clear the anchor (destroyBlock = the player-break path).
        w.destroyBlock(block, false);
        if (SlabAnchorAttachment.isAnchored(w, block)) {
            throw helper.assertionException("breaking the block MUST clear its anchor");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void replaceAfterBreakLeavesNoStaleAnchor(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos slab = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos block = slab.above();
        w.setBlock(slab, bottomSlab(), Block.UPDATE_CLIENTS);
        w.setBlock(block, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        onPlaced(w, block);
        w.destroyBlock(block, false);
        w.setBlock(block, bottomSlab(), Block.UPDATE_CLIENTS);
        if (SlabAnchorAttachment.isAnchored(w, block)) {
            throw helper.assertionException("after a break the anchor must be gone (no stale anchor under the new slab)");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void inPlaceReplaceToNonLockBlockClearsTheAnchor(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos slab = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos block = slab.above();
        w.setBlock(slab, bottomSlab(), Block.UPDATE_CLIENTS);
        w.setBlock(block, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        onPlaced(w, block);
        if (!SlabAnchorAttachment.isAnchored(w, block)) {
            throw helper.assertionException("precondition: grass anchored");
        }
        // Direct in-place replace with a SLAB (non-lock-eligible, the donor's ruling): must CLEAR —
        // the new slab re-evaluates its own dy from scratch, never inherits a full-block anchor.
        w.setBlock(block, bottomSlab(), Block.UPDATE_ALL);
        if (SlabAnchorAttachment.isAnchored(w, block)) {
            throw helper.assertionException(
                    "replacing the anchored full block with a slab (non-lock) MUST clear the anchor");
        }
        helper.succeed();
    }
}
