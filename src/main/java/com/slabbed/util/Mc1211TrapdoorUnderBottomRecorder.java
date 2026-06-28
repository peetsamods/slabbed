package com.slabbed.util;

import com.slabbed.Slabbed;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class Mc1211TrapdoorUnderBottomRecorder {
    private static final String OPT_IN = "slabbed.mc1211.trapdoorLoweredSeamMp4Red";
    private static final String TRUE_BOTTOM_OPT_IN = "slabbed.mc1211.trapdoorUnderBottomPlacementRed";
    private static final String LOWERED_SIDE_EXTENSION_OPT_IN =
            "slabbed.mc1211.trapdoorUnderLoweredBottomPlacementRed";
    private static final AtomicInteger NEXT_CLICK_INDEX = new AtomicInteger();

    private Mc1211TrapdoorUnderBottomRecorder() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(OPT_IN)
                || Boolean.getBoolean(TRUE_BOTTOM_OPT_IN)
                || Boolean.getBoolean(LOWERED_SIDE_EXTENSION_OPT_IN);
    }

    public static ClickSnapshot startClientClick(
            World world,
            ItemStack heldStack,
            BlockHitResult hit,
            HitResult crosshairTarget
    ) {
        if (!isOakTrapdoorClick(world, heldStack, hit)) {
            return null;
        }
        int clickIndex = NEXT_CLICK_INDEX.incrementAndGet();
        ClickSnapshot snapshot = new ClickSnapshot(
                clickIndex,
                hit.getBlockPos(),
                hit.getSide(),
                hit.getPos(),
                expectedTrapdoorPos(hit),
                heldItem(heldStack));
        Slabbed.LOGGER.info("[MC1211_TRAPDOOR_PLACEMENT_CLICK_START]"
                + " clickIndex=" + clickIndex
                + commonClickFields(world, snapshot)
                + " crosshairTargetBefore=" + formatHit(world, crosshairTarget)
                + " sourceTruth=manual-live-recorder"
                + " gameplayPatch=false");
        return snapshot;
    }

    public static void finishClientClick(World world, ClickSnapshot snapshot, ActionResult result) {
        if (!enabled() || world == null || snapshot == null) {
            return;
        }
        BlockState expectedState = world.getBlockState(snapshot.expectedTrapdoorPos());
        Slabbed.LOGGER.info("[MC1211_TRAPDOOR_PLACEMENT_CLICK_RETURN]"
                + " clickIndex=" + snapshot.clickIndex()
                + commonClickFields(world, snapshot)
                + " interactionResult=" + result
                + " interactionAccepted=" + (result != null && result.isAccepted())
                + " expectedTrapdoorState=" + expectedState
                + " expectedTrapdoorDy=" + SlabSupport.getYOffset(world, snapshot.expectedTrapdoorPos(), expectedState)
                + " trapdoorPresent=" + expectedState.isOf(Blocks.OAK_TRAPDOOR)
                + " trapdoorResolvedToAir=" + expectedState.isAir()
                + " sourceTruth=manual-live-recorder"
                + " gameplayPatch=false");
    }

    public static boolean recordUseHead(ItemUsageContext context, Identifier itemId) {
        if (!isOakTrapdoorUse(context, itemId)) {
            return false;
        }
        World world = context.getWorld();
        BlockPos expected = context.getBlockPos().offset(context.getSide());
        Slabbed.LOGGER.info("[MC1211_TRAPDOOR_PLACEMENT_USE_HEAD]"
                + useFields("useOnBlock-head", world, itemId, context.getBlockPos(), context.getSide(),
                context.getHitPos(), expected)
                + " gameplayPatch=false");
        return true;
    }

    public static void recordUseReturn(
            ItemUsageContext context,
            Identifier itemId,
            ActionResult result,
            boolean placeCalled
    ) {
        if (!isOakTrapdoorUse(context, itemId)) {
            return;
        }
        World world = context.getWorld();
        BlockPos expected = context.getBlockPos().offset(context.getSide());
        BlockState expectedState = world.getBlockState(expected);
        Slabbed.LOGGER.info("[MC1211_TRAPDOOR_PLACEMENT_USE_RETURN]"
                + useFields("useOnBlock-return", world, itemId, context.getBlockPos(), context.getSide(),
                context.getHitPos(), expected)
                + " useOnBlockResult=" + result
                + " useOnBlockAccepted=" + (result != null && result.isAccepted())
                + " blockItemPlaceCalled=" + placeCalled
                + " expectedTrapdoorState=" + expectedState
                + " trapdoorPresent=" + expectedState.isOf(Blocks.OAK_TRAPDOOR)
                + " trapdoorResolvedToAir=" + expectedState.isAir()
                + " gameplayPatch=false");
    }

    public static boolean recordPlaceHead(ItemPlacementContext context, Identifier itemId) {
        if (!isOakTrapdoorPlace(context, itemId)) {
            return false;
        }
        World world = context.getWorld();
        Direction face = context.getSide();
        BlockPos placePos = context.getBlockPos();
        BlockPos clickedPos = placePos.offset(face.getOpposite());
        Slabbed.LOGGER.info("[MC1211_TRAPDOOR_PLACEMENT_PLACE_HEAD]"
                + placeFields("place-head", world, itemId, clickedPos, face, context.getHitPos(), placePos)
                + " gameplayPatch=false");
        return true;
    }

    public static void recordPlaceReturn(ItemPlacementContext context, Identifier itemId, ActionResult result) {
        if (!isOakTrapdoorPlace(context, itemId)) {
            return;
        }
        World world = context.getWorld();
        Direction face = context.getSide();
        BlockPos placePos = context.getBlockPos();
        BlockPos clickedPos = placePos.offset(face.getOpposite());
        logPlaceReturn("place-return", world, itemId, clickedPos, face, context.getHitPos(), placePos, result);
        if (!world.isClient() && world.getServer() != null) {
            world.getServer().execute(() -> logPlaceReturn(
                    "server-after-queued-tick",
                    world,
                    itemId,
                    clickedPos,
                    face,
                    context.getHitPos(),
                    placePos,
                    result));
        }
    }

    private static void logPlaceReturn(
            String phase,
            World world,
            Identifier itemId,
            BlockPos clickedPos,
            Direction face,
            Vec3d hit,
            BlockPos placePos,
            ActionResult result
    ) {
        BlockState finalState = world.getBlockState(placePos);
        Slabbed.LOGGER.info("[MC1211_TRAPDOOR_PLACEMENT_PLACE_RETURN]"
                + placeFields(phase, world, itemId, clickedPos, face, hit, placePos)
                + " placementResult=" + result
                + " placementAccepted=" + (result != null && result.isAccepted())
                + " finalTrapdoorState=" + finalState
                + " finalTrapdoorDy=" + SlabSupport.getYOffset(world, placePos, finalState)
                + " trapdoorPresent=" + finalState.isOf(Blocks.OAK_TRAPDOOR)
                + " trapdoorResolvedToAir=" + finalState.isAir()
                + " classification=" + placeClassification(result, finalState)
                + " gameplayPatch=false");
    }

    private static boolean isOakTrapdoorClick(World world, ItemStack heldStack, BlockHitResult hit) {
        if (!enabled() || world == null || heldStack == null || hit == null) {
            return false;
        }
        return heldStack.getItem() instanceof BlockItem blockItem && blockItem.getBlock().equals(Blocks.OAK_TRAPDOOR);
    }

    private static boolean isOakTrapdoorUse(ItemUsageContext context, Identifier itemId) {
        if (!enabled() || context == null || context.getWorld() == null) {
            return false;
        }
        return isOakTrapdoor(itemId);
    }

    private static boolean isOakTrapdoorPlace(ItemPlacementContext context, Identifier itemId) {
        if (!enabled() || context == null || context.getWorld() == null) {
            return false;
        }
        return isOakTrapdoor(itemId);
    }

    private static boolean isOakTrapdoor(Identifier itemId) {
        return "minecraft:oak_trapdoor".equals(String.valueOf(itemId));
    }

    private static boolean isBottomSlab(BlockState state) {
        return state != null
                && state.getBlock() instanceof SlabBlock
                && state.contains(SlabBlock.TYPE)
                && state.get(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    private static BlockPos expectedTrapdoorPos(BlockHitResult hit) {
        return hit.getBlockPos().offset(hit.getSide());
    }

    private static String commonClickFields(World world, ClickSnapshot snapshot) {
        BlockState targetState = world.getBlockState(snapshot.targetPos());
        BlockState expectedState = world.getBlockState(snapshot.expectedTrapdoorPos());
        return " side=" + side(world)
                + " heldItem=" + snapshot.heldItem()
                + " targetPos=" + shortPos(snapshot.targetPos())
                + " targetState=" + targetState
                + " targetDy=" + SlabSupport.getYOffset(world, snapshot.targetPos(), targetState)
                + " targetFace=" + snapshot.face()
                + " bottomSlabUndersideCandidate=" + bottomSlabUnderside(snapshot.face(), targetState)
                + " hitVec=" + formatVec(snapshot.hit())
                + " expectedTrapdoorPos=" + shortPos(snapshot.expectedTrapdoorPos())
                + " expectedTrapdoorState=" + expectedState
                + " expectedTrapdoorDy=" + SlabSupport.getYOffset(world, snapshot.expectedTrapdoorPos(), expectedState);
    }

    private static String useFields(
            String phase,
            World world,
            Identifier itemId,
            BlockPos clickedPos,
            Direction face,
            Vec3d hit,
            BlockPos expected
    ) {
        BlockState clickedState = world.getBlockState(clickedPos);
        BlockState expectedState = world.getBlockState(expected);
        return " phase=" + phase
                + " side=" + side(world)
                + " heldItem=" + itemId
                + " targetPos=" + shortPos(clickedPos)
                + " targetState=" + clickedState
                + " targetDy=" + SlabSupport.getYOffset(world, clickedPos, clickedState)
                + " targetFace=" + face
                + " bottomSlabUndersideCandidate=" + bottomSlabUnderside(face, clickedState)
                + " hitVec=" + formatVec(hit)
                + " expectedTrapdoorPos=" + shortPos(expected)
                + " expectedTrapdoorState=" + expectedState
                + " expectedTrapdoorDy=" + SlabSupport.getYOffset(world, expected, expectedState);
    }

    private static String placeFields(
            String phase,
            World world,
            Identifier itemId,
            BlockPos clickedPos,
            Direction face,
            Vec3d hit,
            BlockPos placePos
    ) {
        BlockState clickedState = world.getBlockState(clickedPos);
        BlockState placeState = world.getBlockState(placePos);
        return " phase=" + phase
                + " side=" + side(world)
                + " heldItem=" + itemId
                + " targetPos=" + shortPos(clickedPos)
                + " targetState=" + clickedState
                + " targetDy=" + SlabSupport.getYOffset(world, clickedPos, clickedState)
                + " targetFace=" + face
                + " bottomSlabUndersideCandidate=" + bottomSlabUnderside(face, clickedState)
                + " hitVec=" + formatVec(hit)
                + " expectedTrapdoorPos=" + shortPos(clickedPos.offset(face))
                + " nativePlacePos=" + shortPos(placePos)
                + " nativePlaceState=" + placeState
                + " nativePlaceDy=" + SlabSupport.getYOffset(world, placePos, placeState);
    }

    private static String placeClassification(ActionResult result, BlockState finalState) {
        if (finalState.isOf(Blocks.OAK_TRAPDOOR)) {
            return "TRAPDOOR_PRESENT";
        }
        if (result != null && result.isAccepted() && finalState.isAir()) {
            return "ACCEPTED_BUT_AIR";
        }
        if (result != null && !result.isAccepted()) {
            return "PLACEMENT_REJECTED";
        }
        return "TRAPDOOR_ABSENT";
    }

    private static boolean bottomSlabUnderside(Direction face, BlockState state) {
        return face == Direction.DOWN && isBottomSlab(state);
    }

    private static String formatHit(World world, HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit)) {
            return hit == null ? "null" : hit.getType().toString();
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        return "{pos=" + shortPos(pos)
                + " face=" + blockHit.getSide()
                + " hit=" + formatVec(blockHit.getPos())
                + " state=" + state
                + " dy=" + SlabSupport.getYOffset(world, pos, state)
                + "}";
    }

    private static String side(World world) {
        return world.isClient() ? "CLIENT" : "SERVER";
    }

    private static String heldItem(ItemStack stack) {
        return stack == null || stack.isEmpty() ? "empty" : String.valueOf(Registries.ITEM.getId(stack.getItem()));
    }

    private static String shortPos(BlockPos pos) {
        return pos == null ? "null" : pos.toShortString().replace(" ", "");
    }

    private static String formatVec(Vec3d vec) {
        if (vec == null) {
            return "null";
        }
        return String.format("%.3f,%.3f,%.3f", vec.x, vec.y, vec.z);
    }

    public record ClickSnapshot(
            int clickIndex,
            BlockPos targetPos,
            Direction face,
            Vec3d hit,
            BlockPos expectedTrapdoorPos,
            String heldItem
    ) {
    }
}
