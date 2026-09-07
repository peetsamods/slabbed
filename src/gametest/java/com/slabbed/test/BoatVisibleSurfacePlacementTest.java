package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A boat aimed at a lowered block's drawn top lands on that drawn top.
 *
 * <p>This is a REGRESSION PIN, not a proof of new code, and the row says so rather than borrowing
 * credit. It is green today because the outline a player's aim is resolved against is already lowered
 * to the drawn body, so vanilla's own aim-to-hit chain answers the visible surface. What the row adds
 * is boat-specific coverage of that chain end to end — the aim, the boat's anchoring at the hit point,
 * and its own free-space gate — which no other row exercises.
 *
 * <p>MUTATION that reddens it: stop moving the outline shape to the drawn body. The aim then answers
 * the grid top and the boat lands half a block high.
 *
 * <p>FIXTURE NOTE: the gametest JVM runs with the frozen store OFF, so the lowered cell authors its
 * recorded height and is read back with the store forced ON. Asserted before any tick, so the boat
 * has not yet fallen.
 */
public final class BoatVisibleSurfacePlacementTest {

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

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aBoatAimedDownAtALoweredTopLandsOnThatTop(GameTestHelper helper) {
        withFrozen(() -> {
            BlockPos targetRel = new BlockPos(3, 2, 3);
            helper.setBlock(targetRel, Blocks.STONE.defaultBlockState());
            BlockPos target = helper.absolutePos(targetRel);
            SlabAnchorAttachment.writePlacementDy(helper.getLevel(), target, LOWERED);

            Player mock = helper.makeMockServerPlayer(GameType.SURVIVAL);
            if (!(mock instanceof ServerPlayer player)) {
                throw helper.assertionException("premise: the fixture did not create a ServerPlayer");
            }
            // Standing two blocks above the target, looking straight down its centre.
            player.snapTo(target.getX() + 0.5d, target.getY() + 2.0d, target.getZ() + 0.5d, 0.0f, 90.0f);

            double drawnTop = target.getY() + 1.0d + LOWERED;

            // PREMISE, so the row's meaning stays honest: the aim itself already answers the drawn top.
            Vec3 eye = player.getEyePosition();
            Vec3 end = eye.add(Player.calculateViewVector(player.getXRot(), player.getYRot())
                    .scale(player.blockInteractionRange()));
            BlockHitResult aim = helper.getLevel().clip(new ClipContext(
                    eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
            if (aim.getType() != HitResult.Type.BLOCK
                    || !aim.getBlockPos().equals(target)
                    || Math.abs(aim.getLocation().y - drawnTop) > EPS) {
                throw helper.assertionException("premise: the aim must already resolve the lowered block's "
                        + "drawn top " + drawnTop + "; type " + aim.getType() + " pos " + aim.getBlockPos()
                        + " y " + aim.getLocation().y);
            }

            ItemStack boatStack = new ItemStack(Items.OAK_BOAT);
            player.setItemInHand(InteractionHand.MAIN_HAND, boatStack);
            InteractionResult result = player.gameMode.useItem(
                    player, helper.getLevel(), boatStack, InteractionHand.MAIN_HAND);

            List<AbstractBoat> boats = helper.getLevel().getEntitiesOfClass(
                    AbstractBoat.class, new AABB(target).inflate(3.0d));
            if (result == null || !result.consumesAction() || boats.size() != 1) {
                throw helper.assertionException("a boat aimed at a lowered block's drawn top must be "
                        + "placed; result " + result + " boats " + boats.size());
            }
            AbstractBoat boat = boats.getFirst();
            if (Math.abs(boat.getY() - drawnTop) > EPS) {
                throw helper.assertionException("the boat must land on the drawn top " + drawnTop
                        + ", got " + boat.getY());
            }
            if (Math.abs(boat.getBoundingBox().minY - drawnTop) > EPS) {
                throw helper.assertionException("the boat's hull must sit on the drawn top; box minY "
                        + boat.getBoundingBox().minY + " vs " + drawnTop);
            }
        });
        helper.succeed();
    }
}
