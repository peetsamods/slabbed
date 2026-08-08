package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Port of 1.21.11 {@code 8b485767} ("block entity beside a lowered block entity placed upward at vanilla
 * height"), adapted to 26.2's Mojang-mapped {@code GameTestHelper} idiom and its DIFFERENT cantilever
 * architecture (26.2 has no {@code isAdjacentToLoweredSupport}; its family is the
 * {@code adjacentLoweredSideMagnitude} / cantilever-BFS readers, so the fix is a block-entity sibling
 * reader {@code adjacentLoweredBlockEntityMagnitude}, not a verbatim cherry-pick).
 *
 * <p>Live bug (the maintainer): "a lowered chest with lowered hoppers next to it — the next horizontally chained
 * hopper places upward into vanilla." {@code getYOffsetInner}'s cantilever lanes structurally excluded
 * {@code EntityBlock} both as SUBJECT ({@code isCantileverFullBlockCandidate} /
 * {@code isCantileverConnectingCandidate} both exclude it) and as a recognised NEIGHBOUR, so a hopper
 * with air below it, beside a lowered chest/hopper, never inherited the lowered dy — placed detached at
 * vanilla height.
 *
 * <p><b>26.2 bonus (same as the sibling):</b> once the live check returns -0.5, this branch's
 * {@code freezeLoweredOnPlace} unchecked {@code dy<0 -> addAnchorUnchecked} branch ALSO records the
 * persisted anchor for a chained block entity automatically — verified directly in
 * {@code cantileverHopperGetsPersistedAnchor}, with no separate anchor-side change.
 */
public final class BlockEntityCantileverTest {

    private static final double EPS = 1.0e-6;

    private static BlockState bottomSlab() {
        return Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    /** Place a chest lowered on a bottom slab and finalize placement (anchored via the unchecked freeze). */
    private static BlockPos placeLoweredChest(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos slab = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos chestPos = slab.above();
        w.setBlock(slab, bottomSlab(), 2);
        w.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, chestPos, w.getBlockState(chestPos));
        SlabAnchorAttachment.freezeLoweredOnPlace(w, chestPos, w.getBlockState(chestPos));
        return chestPos;
    }

    // THE LIVE REPRO: chest on a slab (lowered -0.5, anchored). Hopper #1 placed beside the chest, air
    // below, no slab of its own. Hopper #1 must inherit -0.5, not place at vanilla 0.0.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hopperBesideLoweredChestInheritsDy(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos chestPos = placeLoweredChest(helper);

        double chestDy = SlabSupport.getYOffset(w, chestPos, w.getBlockState(chestPos));
        if (Math.abs(chestDy + 0.5) > EPS) {
            throw helper.assertionException(helper.relativePos(chestPos),
                    "setup: chest on a slab, anchored, should render -0.5, got " + chestDy);
        }

        BlockPos hopper1 = chestPos.east(); // air below, no slab of its own
        w.setBlock(hopper1, Blocks.HOPPER.defaultBlockState(), 2);
        double dy1 = SlabSupport.getYOffset(w, hopper1, w.getBlockState(hopper1));
        if (Math.abs(dy1 + 0.5) > EPS) {
            throw helper.assertionException(helper.relativePos(hopper1),
                    "a hopper placed beside a lowered chest (air below, no slab of its own) must inherit "
                            + "-0.5 (live bug: placed upward into vanilla); got " + dy1);
        }
        helper.succeed();
    }

    // THE CHAIN: a second hopper beside the first (adjacency-lowered) must also inherit -0.5 — the
    // reported "NEXT horizontally chained hopper".
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void secondHopperChainedBeyondFirstInheritsDy(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos chestPos = placeLoweredChest(helper);

        BlockPos hopper1 = chestPos.east();
        w.setBlock(hopper1, Blocks.HOPPER.defaultBlockState(), 2);
        // hopper1 is live-lowered by adjacency to the chest; anchor it as real placement would.
        SlabAnchorAttachment.addAnchor(w, hopper1, w.getBlockState(hopper1));
        SlabAnchorAttachment.freezeLoweredOnPlace(w, hopper1, w.getBlockState(hopper1));

        BlockPos hopper2 = hopper1.east();
        w.setBlock(hopper2, Blocks.HOPPER.defaultBlockState(), 2);
        double dy2 = SlabSupport.getYOffset(w, hopper2, w.getBlockState(hopper2));
        if (Math.abs(dy2 + 0.5) > EPS) {
            throw helper.assertionException(helper.relativePos(hopper2),
                    "the SECOND chained hopper (beside the first, anchored, hopper) must also inherit "
                            + "-0.5; got " + dy2);
        }
        helper.succeed();
    }

    // THE PERSISTED ANCHOR: once the live check returns -0.5, freezeLoweredOnPlace's unchecked dy<0
    // branch must ALSO persist the anchor for a cantilever hopper (not just direct/slab placements).
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void cantileverHopperGetsPersistedAnchor(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos chestPos = placeLoweredChest(helper);

        BlockPos hopper1 = chestPos.east();
        w.setBlock(hopper1, Blocks.HOPPER.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, hopper1, w.getBlockState(hopper1));
        SlabAnchorAttachment.freezeLoweredOnPlace(w, hopper1, w.getBlockState(hopper1));
        if (!SlabAnchorAttachment.isAnchored(w, hopper1)) {
            throw helper.assertionException(helper.relativePos(hopper1),
                    "a cantilever-lowered hopper beside a lowered chest must get a PERSISTED anchor "
                            + "(freezeLoweredOnPlace's unchecked dy<0 branch, driven by the now-fixed live getYOffset)");
        }
        helper.succeed();
    }

    // NEVER-POP RAIL: a hopper on its OWN solid ground beside a lowered chest must NOT sink — the
    // air-gate inside isCantileverBlockEntityCandidate keeps it flush.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hopperOnSolidGroundBesideLoweredChestStaysFlush(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos chestPos = placeLoweredChest(helper);

        BlockPos hopper = chestPos.east();
        BlockPos ground = hopper.below();
        w.setBlock(ground, Blocks.STONE.defaultBlockState(), 2); // its OWN solid ground (not air below)
        w.setBlock(hopper, Blocks.HOPPER.defaultBlockState(), 2);
        double dy = SlabSupport.getYOffset(w, hopper, w.getBlockState(hopper));
        if (Math.abs(dy) > EPS) {
            throw helper.assertionException(helper.relativePos(hopper),
                    "a hopper on its OWN solid ground beside a lowered chest must stay flush (dy=0), got " + dy
                            + " — the block-entity cantilever lane is air-gated (NEVER-POP rail)");
        }
        helper.succeed();
    }
}
