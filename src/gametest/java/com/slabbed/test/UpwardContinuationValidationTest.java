package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.placement.LandingHitValidationPolicy;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * TEST40: independently selectable upward-continuation classification at frozen dy=-1.5.
 *
 * <p>The real {@link ItemStack#useOn(UseOnContext)} calls prove placement capture and the law's
 * frozen-height guarantee. The policy assertion is deliberately separate: Minecraft's packet
 * component-distance guard runs before {@code useOn}, so a real-useOn result cannot claim packet
 * admission on its own.
 */
public final class UpwardContinuationValidationTest {
    private static final double OWNER_DY = -1.5d;
    private static final double COMPONENT_TOLERANCE = 1.0000001d;

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void pointedDripstoneUpwardPolicyRed(GameTestHelper h) {
        FamilyResult result = exercise(h, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.UP), Items.POINTED_DRIPSTONE);

        double outsideShape = LandingHitValidationPolicy.shiftedCenterDy(
                result.owner(), result.ownerState(), OWNER_DY, Direction.UP,
                result.hit().add(1.1d, 0.0d, 0.0d), result.heldState());
        double flatOwner = LandingHitValidationPolicy.shiftedCenterDy(
                result.owner(), result.ownerState(), 0.0d, Direction.UP, result.hit(), result.heldState());
        double unsupportedHeld = LandingHitValidationPolicy.shiftedCenterDy(
                result.owner(), result.ownerState(), OWNER_DY, Direction.UP, result.hit(),
                Blocks.AIR.defaultBlockState());
        double mismatchedFace = LandingHitValidationPolicy.shiftedCenterDy(
                result.owner(), result.ownerState(), OWNER_DY, Direction.DOWN, result.hit(), result.heldState());
        if (!Double.isNaN(outsideShape) || !Double.isNaN(flatOwner)
                || !Double.isNaN(unsupportedHeld) || !Double.isNaN(mismatchedFace)) {
            throw h.assertionException(result.owner(), "TEST40 pointed-dripstone policy negative widened: "
                    + "outside=" + outsideShape + " flat=" + flatOwner + " unsupported=" + unsupportedHeld
                    + " mismatched=" + mismatchedFace);
        }
        requireShift(h, result, "pointed-dripstone UP");
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void verticalIronChain(GameTestHelper h) {
        requireVanillaCenter(h, exercise(h, Blocks.IRON_CHAIN.defaultBlockState(), Items.IRON_CHAIN),
                "vertical iron chain");
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void isolatedOakFence(GameTestHelper h) {
        requireVanillaCenter(h, exercise(h, Blocks.OAK_FENCE.defaultBlockState(), Items.OAK_FENCE),
                "isolated oak fence");
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void isolatedCobblestoneWall(GameTestHelper h) {
        requireVanillaCenter(h, exercise(h, Blocks.COBBLESTONE_WALL.defaultBlockState(), Items.COBBLESTONE_WALL),
                "isolated cobblestone wall");
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void isolatedIronBars(GameTestHelper h) {
        requireVanillaCenter(h, exercise(h, Blocks.IRON_BARS.defaultBlockState(), Items.IRON_BARS),
                "isolated iron bars");
        h.succeed();
    }

    private static FamilyResult exercise(GameTestHelper h, BlockState ownerState, Item heldItem) {
        ServerLevel world = h.getLevel();
        BlockPos owner = h.absolutePos(new BlockPos(3, 3, 3));
        BlockPos target = owner.above();
        boolean previousFrozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            world.setBlock(owner, ownerState, Block.UPDATE_ALL);
            world.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            PlacementCaptureBoundaryGameTest.forceStore(world, owner, OWNER_DY);

            // The active selection shape is already translated by the frozen owner dy.
            double shapeTop = ownerState.getShape(world, owner, CollisionContext.empty()).max(Direction.Axis.Y);
            Vec3 hit = new Vec3(owner.getX() + 0.5d, owner.getY() + shapeTop, owner.getZ() + 0.5d);
            double componentDistance = Math.abs(hit.y - Vec3.atCenterOf(owner).y);
            InteractionResult result = realUseOn(h, new ItemStack(heldItem), owner, hit);
            double storedBefore = SlabAnchorAttachment.storedPlacementDy(world, target);
            double liveBefore = SlabSupport.getYOffset(world, target, world.getBlockState(target));
            if (!result.consumesAction() || !world.getBlockState(target).is(heldItem.getDefaultInstance().getItem()
                    instanceof net.minecraft.world.item.BlockItem blockItem ? blockItem.getBlock() : Blocks.AIR)
                    || rawBits(storedBefore) != rawBits(OWNER_DY) || rawBits(liveBefore) != rawBits(OWNER_DY)) {
                throw h.assertionException(target, "TEST40 real-useOn placement failed: result=" + result
                        + " state=" + world.getBlockState(target) + " stored=" + storedBefore + " live=" + liveBefore);
            }

            BlockPos lateral = target.east();
            world.setBlock(lateral, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            world.setBlock(lateral, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            double storedAfter = SlabAnchorAttachment.storedPlacementDy(world, target);
            double liveAfter = SlabSupport.getYOffset(world, target, world.getBlockState(target));
            if (rawBits(storedAfter) != rawBits(storedBefore) || rawBits(liveAfter) != rawBits(liveBefore)) {
                throw h.assertionException(target, "TEST40 neighbor edit moved placed block: stored before="
                        + storedBefore + " after=" + storedAfter + " live before=" + liveBefore + " after=" + liveAfter);
            }
            return new FamilyResult(owner, ownerState, heldItem.getDefaultInstance().getItem()
                    instanceof net.minecraft.world.item.BlockItem blockItem ? blockItem.getBlock().defaultBlockState()
                    : Blocks.AIR.defaultBlockState(), hit, componentDistance, storedBefore, liveBefore);
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previousFrozen;
        }
    }

    private static InteractionResult realUseOn(GameTestHelper h, ItemStack stack, BlockPos owner, Vec3 hit) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(hit, Direction.UP, owner, false)));
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previous;
        }
    }

    private static void requireVanillaCenter(GameTestHelper h, FamilyResult result, String family) {
        double shift = policy(result);
        Slabbed.LOGGER.info("TEST40 {} | distance={} shift={} stored={} live={} | PASS",
                family, result.componentDistance(), shift, result.storedDy(), result.liveDy());
        if (result.componentDistance() > COMPONENT_TOLERANCE || !Double.isNaN(shift)) {
            throw h.assertionException(result.owner(), "TEST40 " + family + " classification expected vanilla center: "
                    + "distance=" + result.componentDistance() + " shift=" + shift
                    + " stored=" + result.storedDy() + " live=" + result.liveDy());
        }
    }

    private static void requireShift(GameTestHelper h, FamilyResult result, String family) {
        double shift = policy(result);
        Slabbed.LOGGER.info("TEST40 {} | distance={} shift={} stored={} live={} | expected RED",
                family, result.componentDistance(), shift, result.storedDy(), result.liveDy());
        if (result.componentDistance() <= COMPONENT_TOLERANCE || rawBits(shift) != rawBits(OWNER_DY)) {
            throw h.assertionException(result.owner(), "TEST40 RED " + family + " requires shifted validation: "
                    + "distance=" + result.componentDistance() + " tolerance=" + COMPONENT_TOLERANCE
                    + " shift=" + shift + " shiftRaw=" + String.format("%016x", rawBits(shift))
                    + " expectedRaw=" + String.format("%016x", rawBits(OWNER_DY))
                    + " stored=" + result.storedDy() + " live=" + result.liveDy());
        }
    }

    private static double policy(FamilyResult result) {
        return LandingHitValidationPolicy.shiftedCenterDy(result.owner(), result.ownerState(), OWNER_DY,
                Direction.UP, result.hit(), result.heldState());
    }

    private static long rawBits(double value) {
        return Double.doubleToRawLongBits(value);
    }

    private record FamilyResult(
            BlockPos owner,
            BlockState ownerState,
            BlockState heldState,
            Vec3 hit,
            double componentDistance,
            double storedDy,
            double liveDy
    ) {
    }
}
