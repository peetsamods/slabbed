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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * ASK WHETHER A BLOCK IS HANGING, NOT WHETHER ITS TYPE COULD BE (upstream lane-C ruling): a lever on
 * the FLOOR of a lowered support, or a Y-chain STANDING on one with open air above, hangs from
 * nothing — it seats on the surface below it, exactly like the stone in {@code LoweredSeatFreezeTest}.
 *
 * <p><b>The defect shape this measures.</b> {@code SlabSupport.isCeilingAttached} answers by block
 * TYPE for bells, levers, buttons, Y-chains and TOP-half trapdoors — "can all be ceiling-mounted" —
 * so a floor lever and a standing chain both answer "hangs from above". Upstream, that misroute
 * denied such blocks their anchor, left them deriving live from the block below, and popped them
 * when it broke (S-2 lane C). On this line the store is authoritative, so the question becomes
 * whether the misroute reaches the number LAW 1 FREEZES at placement. These rows measure exactly
 * that: real {@code useOn} placements, asserting the stored fact.
 *
 * <p>The expected values are the ones the stone rows already proved for the same supports, so a
 * divergence here isolates the subject's routing — not the seat arithmetic.
 */
public final class FloorMountedIsNotHangingTest {

    private static final double EPS = 1.0e-6;

    private static void placeOnTop(GameTestHelper h, Item item, BlockPos clicked) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(clicked).add(0.0, 0.5, 0.0);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, Direction.UP, clicked, false)));
    }

    private void floorSeatRow(GameTestHelper helper, Item item, String what, double expectedDy) {
        ServerLevel level = helper.getLevel();
        BlockPos supportRel = new BlockPos(2, 2, 2);
        BlockPos support = helper.absolutePos(supportRel);
        BlockPos placed = support.above();

        helper.setBlock(supportRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(supportRel,
                Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        SlabAnchorAttachment.writePlacementDy(level, support, -0.5d);

        placeOnTop(helper, item, support);

        BlockState placedState = level.getBlockState(placed);
        if (placedState.isAir()) {
            throw helper.assertionException(helper.relativePos(placed),
                    "premise: the " + what + " must survive placement on the bottom slab's top");
        }
        double stored = SlabAnchorAttachment.storedPlacementDy(level, placed);
        if (!Double.isFinite(stored)) {
            throw helper.assertionException(helper.relativePos(placed),
                    "premise: the " + what + " placement must mint a stored fact; none exists");
        }
        if (Math.abs(stored - expectedDy) > EPS) {
            throw helper.assertionException(helper.relativePos(placed),
                    "a " + what + " placed ON TOP of a lowered support hangs from nothing and must "
                            + "freeze at the support's real top like any floor object, expected "
                            + expectedDy + " got " + stored + " — a divergence here is the "
                            + "type-COULD-hang misroute reaching the frozen number");
        }
        helper.succeed();
    }

    /** A floor lever seats like the stone control did on the same support: -1.0. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void floorLeverOnLoweredBottomSlabSeatsOnIt(GameTestHelper helper) {
        floorSeatRow(helper, Blocks.LEVER.asItem(), "floor lever", -1.0);
    }

    /** A standing Y-chain with open air above hangs from nothing and seats the same way. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void standingChainOnLoweredBottomSlabSeatsOnIt(GameTestHelper helper) {
        // 26.x split chains into IRON_CHAIN/COPPER_CHAIN; there is no plain CHAIN constant.
        floorSeatRow(helper, Blocks.IRON_CHAIN.asItem(), "standing chain", -1.0);
    }

    /** Premise sanity for the lever row: the placed lever really is FLOOR-attached, so the row above
     *  is arguing about a floor subject and not a wall one. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void placedLeverIsFloorAttached(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos supportRel = new BlockPos(2, 2, 2);
        BlockPos support = helper.absolutePos(supportRel);
        helper.setBlock(supportRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(supportRel,
                Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        SlabAnchorAttachment.writePlacementDy(level, support, -0.5d);
        placeOnTop(helper, Blocks.LEVER.asItem(), support);

        BlockState lever = level.getBlockState(support.above());
        if (!lever.hasProperty(BlockStateProperties.ATTACH_FACE)
                || lever.getValue(BlockStateProperties.ATTACH_FACE) != AttachFace.FLOOR) {
            throw helper.assertionException(
                    "premise: an UP-face lever placement must produce ATTACH_FACE=FLOOR, got " + lever);
        }
        // Document the misclassification the seat rows are probing: the type-shaped predicate DOES
        // claim this floor lever hangs from above. If this row ever fails, isCeilingAttached has been
        // fixed to ask the state — update this class's javadoc and retire the comment, but KEEP the
        // seat rows: they pin the frozen number either way.
        if (!SlabSupport.isCeilingAttached(lever)) {
            throw helper.assertionException(
                    "isCeilingAttached no longer claims a FLOOR lever — the type-COULD-hang "
                            + "misclassification appears fixed; update FloorMountedIsNotHangingTest's "
                            + "javadoc to match (the seat rows above remain valid as-is)");
        }
        helper.succeed();
    }
}
