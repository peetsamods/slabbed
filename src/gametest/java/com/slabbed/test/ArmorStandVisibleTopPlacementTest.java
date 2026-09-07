package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * An armor stand is checked for room where it will actually stand (maintainer ruling, 2026-09-06).
 *
 * <p>WHERE THE STAND LANDS was already right and must stay right: vanilla drops a new stand onto the
 * highest COLLISION surface within a block of its spawn cell, and a lowered block's collision already
 * follows its drawn body. What was wrong is the free-space test that runs first — it built one box at
 * the spawn cell's GRID floor, so over a lowered block the game asked about a band the stand will
 * never occupy and refused stands that plainly fit.
 *
 * <p>The load-bearing row is {@link #aStandIsAcceptedWhenOnlyTheGridBandIsBlocked}. The others bound
 * it: one pins that the LANDING is untouched (a second drop applied here would sink every stand half
 * a block), one pins that the support is the block under the SPAWN CELL rather than the clicked
 * block, and one pins that only a STORED height moves the check — a block that merely looks short
 * keeps vanilla's answer exactly.
 *
 * <p>FIXTURE NOTE: the gametest JVM runs with the frozen store OFF, so a cell that must read a
 * recorded height authors that fact and is read back with the store forced ON — the established
 * pattern on this line. Every row asserts before any tick, so gravity cannot mask a result.
 */
public final class ArmorStandVisibleTopPlacementTest {

    private static final double EPS = 1.0e-6d;
    private static final double LOWERED = -0.5d;

    private interface FrozenBody {
        void run();
    }

    private static void withFrozen(FrozenBody body) {
        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            body.run();
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previous;
        }
    }

    /** Solid ground with a recorded lowered height. Returns its ABSOLUTE position. */
    private static BlockPos loweredSupport(GameTestHelper helper, BlockPos rel, double dy) {
        helper.setBlock(rel, Blocks.STONE.defaultBlockState());
        BlockPos abs = helper.absolutePos(rel);
        SlabAnchorAttachment.writePlacementDy(helper.getLevel(), abs, dy);
        return abs;
    }

    private static BlockState topSlab() {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
    }

    /** The real item-use path, the same one the placement law rows drive. */
    private static InteractionResult placeStandOn(GameTestHelper helper, BlockPos clicked, Direction face) {
        Player mock = helper.makeMockServerPlayer(GameType.SURVIVAL);
        if (!(mock instanceof ServerPlayer player)) {
            throw helper.assertionException("premise: the fixture did not create a ServerPlayer");
        }
        ItemStack stack = new ItemStack(Items.ARMOR_STAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(clicked).add(
                face.getStepX() * 0.5d, face.getStepY() * 0.5d, face.getStepZ() * 0.5d);
        return player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, clicked, false));
    }

    private static List<ArmorStand> standsIn(GameTestHelper helper, BlockPos around) {
        AABB box = new AABB(around).inflate(1.5d);
        return helper.getLevel().getEntitiesOfClass(ArmorStand.class, box);
    }

    private static double standHeight() {
        return EntityTypes.ARMOR_STAND.getDimensions().height();
    }

    /**
     * THE LOAD-BEARING ROW. A stand whose visible band is clear but whose grid band is not must be
     * accepted — the direction the old check got wrong, and the one a merely-stricter check would
     * silently keep getting wrong.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aStandIsAcceptedWhenOnlyTheGridBandIsBlocked(GameTestHelper helper) {
        withFrozen(() -> {
            BlockPos supportRel = new BlockPos(2, 2, 2);
            BlockPos support = loweredSupport(helper, supportRel, LOWERED);
            helper.setBlock(supportRel.above(2), topSlab());

            double height = standHeight();
            double blockerBottom = support.getY() + 2.5d;
            double gridBandTop = support.getY() + 1.0d + height;
            double drawnBandTop = support.getY() + 1.0d + LOWERED + height;
            if (!(gridBandTop > blockerBottom + EPS) || !(drawnBandTop < blockerBottom - EPS)) {
                throw helper.assertionException("premise: this fixture must block the GRID band and leave "
                        + "the DRAWN band clear; stand height " + height + " grid top " + gridBandTop
                        + " drawn top " + drawnBandTop + " blocker bottom " + blockerBottom);
            }

            InteractionResult result = placeStandOn(helper, support, Direction.UP);
            List<ArmorStand> stands = standsIn(helper, support);
            if (result == null || !result.consumesAction() || stands.size() != 1) {
                throw helper.assertionException("a stand whose visible band is clear must be accepted; "
                        + "result " + result + " stands " + stands.size()
                        + " (the free-space check is still reading the grid band)");
            }
        });
        helper.succeed();
    }

    /**
     * The LANDING is vanilla's and must stay vanilla's: a stand settles on the drawn top by itself,
     * exactly the seat below the flush control. This is the tripwire for a second drop being applied
     * on the placement side, which would sink every stand half a block into the ground.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aStandOnALoweredSupportStandsOnItsDrawnTop(GameTestHelper helper) {
        withFrozen(() -> {
            BlockPos loweredRel = new BlockPos(2, 2, 2);
            BlockPos controlRel = new BlockPos(2, 2, 6);
            BlockPos lowered = loweredSupport(helper, loweredRel, LOWERED);
            helper.setBlock(controlRel, Blocks.STONE.defaultBlockState());
            BlockPos control = helper.absolutePos(controlRel);

            if (!placeStandOn(helper, lowered, Direction.UP).consumesAction()
                    || !placeStandOn(helper, control, Direction.UP).consumesAction()) {
                throw helper.assertionException("premise: both placements must be accepted");
            }
            List<ArmorStand> loweredStands = standsIn(helper, lowered.above(1));
            List<ArmorStand> controlStands = standsIn(helper, control.above(1));
            if (loweredStands.size() != 1 || controlStands.size() != 1) {
                throw helper.assertionException("premise: expected exactly one stand on each support; "
                        + "lowered " + loweredStands.size() + " control " + controlStands.size());
            }
            ArmorStand loweredStand = loweredStands.getFirst();
            ArmorStand controlStand = controlStands.getFirst();

            if (Math.abs(controlStand.getY() - (control.getY() + 1.0d)) > EPS) {
                throw helper.assertionException("control: a stand on a flush block must stand on its top "
                        + (control.getY() + 1.0d) + ", got " + controlStand.getY());
            }
            if (Math.abs(loweredStand.getY() - (lowered.getY() + 1.0d + LOWERED)) > EPS) {
                throw helper.assertionException("a stand on a lowered block must stand on its DRAWN top "
                        + (lowered.getY() + 1.0d + LOWERED) + ", got " + loweredStand.getY()
                        + " — a drop applied on the placement side would double the seat");
            }
            if (Math.abs(loweredStand.getBoundingBox().minY - loweredStand.getY()) > EPS) {
                throw helper.assertionException("the stand's real box must start at its feet; box minY "
                        + loweredStand.getBoundingBox().minY + " position " + loweredStand.getY());
            }
        });
        helper.succeed();
    }

    /**
     * The support is the block under the SPAWN CELL, not the clicked block. They differ whenever the
     * clicked block is replaceable — here a single snow layer growing on the lowered block, where the
     * spawn cell IS the clicked cell.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aStandOnAReplaceableClickedBlockUsesTheSupportBelowIt(GameTestHelper helper) {
        withFrozen(() -> {
            BlockPos supportRel = new BlockPos(2, 2, 2);
            BlockPos support = loweredSupport(helper, supportRel, LOWERED);
            helper.setBlock(supportRel.above(), Blocks.SNOW.defaultBlockState());
            helper.setBlock(supportRel.above(2), topSlab());
            BlockPos snow = support.above();

            InteractionResult result = placeStandOn(helper, snow, Direction.UP);
            List<ArmorStand> stands = standsIn(helper, support);
            if (result == null || !result.consumesAction() || stands.size() != 1) {
                throw helper.assertionException("clicking a replaceable block over a lowered support must "
                        + "still be accepted; result " + result + " stands " + stands.size()
                        + " (the support was taken from the clicked cell, which carries no height)");
            }
            double drawnTop = support.getY() + 1.0d + LOWERED;
            if (Math.abs(stands.getFirst().getY() - drawnTop) > EPS) {
                throw helper.assertionException("the stand must stand on the block UNDER the snow, at its "
                        + "drawn top " + drawnTop + ", got " + stands.getFirst().getY());
            }
        });
        helper.succeed();
    }

    /**
     * A block that merely LOOKS short does not move the check. An ordinary bottom slab has no recorded
     * height, so vanilla's refusal here is preserved exactly — this is the store gate's tripwire, not
     * a claim that the refusal is desirable.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aStandOnAnUnloweredSlabKeepsVanillasRefusal(GameTestHelper helper) {
        withFrozen(() -> {
            BlockPos slabRel = new BlockPos(2, 2, 2);
            helper.setBlock(slabRel,
                    Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            helper.setBlock(slabRel.above(2), topSlab());
            BlockPos slab = helper.absolutePos(slabRel);

            InteractionResult result = placeStandOn(helper, slab, Direction.UP);
            List<ArmorStand> stands = standsIn(helper, slab);
            if ((result != null && result.consumesAction()) || !stands.isEmpty()) {
                throw helper.assertionException("a factless slab must keep vanilla's answer — the check "
                        + "must NOT follow its shape; result " + result + " stands " + stands.size());
            }
        });
        helper.succeed();
    }
}
