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
 * Port of 1.21.11 {@code 7f213cb1} ("height-lock lowered block entities — hopper/chest snap"), adapted
 * to 26.2's Mojang-mapped {@code GameTestHelper} idiom and its DIFFERENT anchor architecture.
 *
 * <p>The reported hopper snap: a block-entity block (hopper/chest/furnace) placed on flush ground
 * re-derived its dy from a live column walk (block entities are excluded from
 * {@code isOrdinaryFullBlockAnchorCandidate}, yet {@code isSlabSitCandidate} lowers every
 * {@code EntityBlock} onto a slab), so a slab shoved under it later toggled it 0 &rarr; -0.5 — "places
 * too high, then snaps down when a block is placed underneath". A placed block entity must be
 * height-locked (frozen-flat) like any other structural piece so a later cell change can't move it.
 * Ceiling-hung block entities (hanging signs) must stay EXCLUDED — they follow their support.
 *
 * <p><b>26.2 note (differs from 1.21.11):</b> the LOWERED case is already covered structurally by this
 * branch's {@code freezeLoweredOnPlace} unchecked {@code dy<0 -> addAnchorUnchecked} branch (the same
 * mechanism {@code DecorativeObjectSupportAnchorTest} pins), so no dedicated
 * {@code qualifiesForBlockEntityLoweredAnchor} anchor lane was needed here. The FLAT case is the real
 * 26.2 gap: {@code freezeLoweredOnPlace}'s structural freeze-flat gate excluded {@code EntityBlock}. The
 * fix adds {@code (EntityBlock && !isAlwaysCeilingHungDecoration)} to that gate. Reverting only that
 * clause fails {@code flatHopperIsNotPulledDownByASlabShovedUnder} exactly.
 */
public final class BlockEntityNeverPopTest {

    private static final double EPS = 1.0e-6;

    private static BlockState bottomSlab() {
        return Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    /** Mirrors the real placement finalization: addAnchor + freezeLoweredOnPlace (Block#setPlacedBy HEAD). */
    private static void onPlaced(ServerLevel w, BlockPos pos) {
        SlabAnchorAttachment.addAnchor(w, pos, w.getBlockState(pos));
        SlabAnchorAttachment.freezeLoweredOnPlace(w, pos, w.getBlockState(pos));
    }

    private void loweredBeIsLocked(GameTestHelper helper, BlockState be, String label) {
        ServerLevel w = helper.getLevel();
        BlockPos slabRel = new BlockPos(2, 2, 2);
        BlockPos posRel = slabRel.above();
        BlockPos slab = helper.absolutePos(slabRel);
        BlockPos pos = helper.absolutePos(posRel);
        w.setBlock(slab, bottomSlab(), 2);
        w.setBlock(pos, be, 2);
        onPlaced(w, pos);

        double placed = SlabSupport.getYOffset(w, pos, w.getBlockState(pos));
        if (placed >= -EPS) {
            throw helper.assertionException(posRel, label + " on a bottom slab must place lowered, got " + placed);
        }
        if (!SlabAnchorAttachment.isAnchored(w, pos)) {
            throw helper.assertionException(posRel,
                    label + " placed lowered MUST be height-locked (else it snaps when the column changes)");
        }

        // Edit the column below WITHOUT touching the block entity — a geometric one would toggle.
        w.setBlock(slab, Blocks.AIR.defaultBlockState(), 2);
        double after = SlabSupport.getYOffset(w, pos, w.getBlockState(pos));
        if (Math.abs(after - placed) > EPS) {
            throw helper.assertionException(posRel,
                    label + " must STAY at " + placed + " when the cell below changes, got " + after + " (snap)");
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hopperLoweredOnSlabIsHeightLocked(GameTestHelper helper) {
        loweredBeIsLocked(helper, Blocks.HOPPER.defaultBlockState(), "hopper");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chestAndFurnaceLoweredOnSlabAreHeightLocked(GameTestHelper helper) {
        loweredBeIsLocked(helper, Blocks.CHEST.defaultBlockState(), "chest");
        loweredBeIsLocked(helper, Blocks.FURNACE.defaultBlockState(), "furnace");
        loweredBeIsLocked(helper, Blocks.BARREL.defaultBlockState(), "barrel");
        helper.succeed();
    }

    /**
     * THE 26.2 GAP: a hopper placed FLAT on solid ground must NOT be pulled down by a slab shoved under
     * it later. Without the freeze-flat gate widening (EntityBlock clause), the flat hopper is not frozen
     * and its dy toggles to -0.5 the instant the slab appears — the reported snap. Reverting only the new
     * clause fails HERE.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void flatHopperIsNotPulledDownByASlabShovedUnder(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos groundRel = new BlockPos(2, 2, 2);
        BlockPos posRel = groundRel.above();
        BlockPos ground = helper.absolutePos(groundRel);
        BlockPos pos = helper.absolutePos(posRel);
        w.setBlock(ground, Blocks.STONE.defaultBlockState(), 2);
        w.setBlock(pos, Blocks.HOPPER.defaultBlockState(), 2);
        onPlaced(w, pos);
        double placed = SlabSupport.getYOffset(w, pos, w.getBlockState(pos));
        if (Math.abs(placed) > EPS) {
            throw helper.assertionException(posRel, "hopper placed flat must be dy 0, got " + placed);
        }
        if (!SlabAnchorAttachment.isFrozenFlat(w, pos)) {
            throw helper.assertionException(posRel,
                    "a flat-placed hopper MUST be frozen-flat at placement, or a slab shoved under it later "
                            + "pulls it down (the reported snap)");
        }

        w.setBlock(ground, bottomSlab(), 2);
        double after = SlabSupport.getYOffset(w, pos, w.getBlockState(pos));
        if (Math.abs(after) > EPS) {
            throw helper.assertionException(posRel,
                    "a flat-placed hopper must NOT be pulled down by a slab added under it, got " + after);
        }
        helper.succeed();
    }

    /**
     * Regression guard: a hanging sign is a block entity too, but it HANGS and must keep following its
     * support — it must NOT be height-locked (anchored or frozen-flat) by the new block-entity clause.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingSignBlockEntityIsNotHeightLocked(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos supportRel = new BlockPos(2, 3, 2);
        BlockPos posRel = supportRel.below();
        BlockPos support = helper.absolutePos(supportRel);
        BlockPos pos = helper.absolutePos(posRel);
        w.setBlock(support, Blocks.STONE.defaultBlockState(), 2);
        w.setBlock(pos, Blocks.OAK_HANGING_SIGN.defaultBlockState(), 2);
        onPlaced(w, pos);
        if (SlabAnchorAttachment.isAnchored(w, pos)) {
            throw helper.assertionException(posRel,
                    "a hanging sign (ceiling-hung block entity) must NOT be anchored — it follows its support");
        }
        if (SlabAnchorAttachment.isFrozenFlat(w, pos)) {
            throw helper.assertionException(posRel,
                    "a hanging sign must NOT be frozen-flat either — the EntityBlock freeze-flat clause "
                            + "excludes always-ceiling-hung decorations");
        }
        helper.succeed();
    }
}
