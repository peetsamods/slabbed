package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * PLACING INTO A REPLACEABLE CELL seats on the REAL support below the cell — never on a phantom
 * plane derived from the replaced plant itself.
 *
 * <p><b>The suspected defect this measures</b> (design-review finding; the suspected mechanism of
 * GH #48 "everything renders half a block low"): when the clicked cell IS the target cell (grass,
 * ferns, one-layer snow being replaced), the aim's owner is the replaceable block, and a landing
 * formula that treats it like a clicked NEIGHBOUR mints {@code ownerDy + topPlaneOffset(plant)} —
 * a height one whole cell wrong, which LAW 1 then freezes permanently.
 *
 * <p>The control row and the replacement row place the SAME stone against the SAME slab and must
 * freeze the SAME number; only the grass differs. That pairing isolates the same-cell path — if the
 * control passes and the replacement row diverges, the divergence IS the same-cell bug, with the
 * minted number in the failure message.
 */
public final class ReplaceableCellSeatTest {

    private static final double EPS = 1.0e-6;

    private static void placeStone(GameTestHelper h, BlockPos clicked, Direction face) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(Blocks.STONE.asItem());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(clicked)
                .add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, clicked, false)));
    }

    private double placedStoneDy(GameTestHelper helper, boolean grassInTheCell) {
        ServerLevel level = helper.getLevel();
        BlockPos slabRel = new BlockPos(2, 2, 2);
        BlockPos slab = helper.absolutePos(slabRel);
        BlockPos cell = slab.above();

        helper.setBlock(slabRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(slabRel,
                Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));

        if (grassInTheCell) {
            helper.setBlock(slabRel.above(), Blocks.SHORT_GRASS.defaultBlockState());
            // Vanilla replacement aim: the player points AT the grass, so the clicked cell IS the
            // target cell. This is the path under suspicion.
            placeStone(helper, cell, Direction.UP);
        } else {
            // Control aim: the player points at the slab's top face.
            placeStone(helper, slab, Direction.UP);
        }

        BlockState placed = level.getBlockState(cell);
        if (!placed.is(Blocks.STONE)) {
            throw helper.assertionException(helper.relativePos(cell),
                    "premise: the stone must fill the cell above the slab"
                            + (grassInTheCell ? " (replacing the grass)" : "") + ", got " + placed);
        }
        double stored = SlabAnchorAttachment.storedPlacementDy(level, cell);
        if (!Double.isFinite(stored)) {
            throw helper.assertionException(helper.relativePos(cell),
                    "premise: the placement must mint a stored fact; none exists");
        }
        return stored;
    }

    /** The control: stone on a flat bottom slab's top freezes -0.5. Proves the scene and the
     *  expected number before the replacement row leans on them. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void controlStoneOnBottomSlabFreezesAtItsTop(GameTestHelper helper) {
        double stored = placedStoneDy(helper, false);
        if (Math.abs(stored - (-0.5)) > EPS) {
            throw helper.assertionException(
                    "control: stone on a flat bottom slab must freeze -0.5, got " + stored);
        }
        helper.succeed();
    }

    /** The measurement: the SAME placement through grass must freeze the SAME -0.5. Any other
     *  number is the same-cell phantom plane, frozen forever. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void stoneThroughGrassFreezesOnTheSlabBelow(GameTestHelper helper) {
        double stored = placedStoneDy(helper, true);
        if (Math.abs(stored - (-0.5)) > EPS) {
            throw helper.assertionException(
                    "stone placed INTO a replaceable grass cell above a bottom slab must seat on the "
                            + "slab exactly like the control (-0.5), got " + stored
                            + " — the aim treated the replaced plant as a neighbour and minted a plane "
                            + "one cell wrong, which LAW 1 now preserves. This is the suspected GH #48 "
                            + "mechanism.");
        }
        helper.succeed();
    }
}
