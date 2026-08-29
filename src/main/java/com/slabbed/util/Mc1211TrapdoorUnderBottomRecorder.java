package com.slabbed.util;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

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
            Level world,
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
                hit.getDirection(),
                hit.getLocation(),
                expectedTrapdoorPos(hit),
                heldItem(heldStack));
        System.out.println("[MC1211_TRAPDOOR_PLACEMENT_CLICK_START]"
                + " clickIndex=" + clickIndex
                + commonClickFields(world, snapshot)
                + " crosshairTargetBefore=" + formatHit(world, crosshairTarget)
                + " sourceTruth=manual-live-recorder"
                + " gameplayPatch=false");
        return snapshot;
    }

    public static void finishClientClick(Level world, ClickSnapshot snapshot, InteractionResult result) {
        if (!enabled() || world == null || snapshot == null) {
            return;
        }
        BlockState expectedState = world.getBlockState(snapshot.expectedTrapdoorPos());
        System.out.println("[MC1211_TRAPDOOR_PLACEMENT_CLICK_RETURN]"
                + " clickIndex=" + snapshot.clickIndex()
                + commonClickFields(world, snapshot)
                + " interactionResult=" + result
                + " interactionAccepted=" + (result != null && result.consumesAction())
                + " expectedTrapdoorState=" + expectedState
                + " expectedTrapdoorDy=" + SlabSupport.getYOffset(world, snapshot.expectedTrapdoorPos(), expectedState)
                + " trapdoorPresent=" + expectedState.is(Blocks.OAK_TRAPDOOR)
                + " trapdoorResolvedToAir=" + expectedState.isAir()
                + " sourceTruth=manual-live-recorder"
                + " gameplayPatch=false");
    }

    public static boolean recordUseHead(UseOnContext context, ResourceLocation itemId) {
        if (!isOakTrapdoorUse(context, itemId)) {
            return false;
        }
        Level world = context.getLevel();
        BlockPos expected = context.getClickedPos().relative(context.getClickedFace());
        System.out.println("[MC1211_TRAPDOOR_PLACEMENT_USE_HEAD]"
                + useFields("useOnBlock-head", world, itemId, context.getClickedPos(), context.getClickedFace(),
                context.getClickLocation(), expected)
                + " gameplayPatch=false");
        return true;
    }

    public static void recordUseReturn(
            UseOnContext context,
            ResourceLocation itemId,
            InteractionResult result,
            boolean placeCalled
    ) {
        if (!isOakTrapdoorUse(context, itemId)) {
            return;
        }
        Level world = context.getLevel();
        BlockPos expected = context.getClickedPos().relative(context.getClickedFace());
        BlockState expectedState = world.getBlockState(expected);
        System.out.println("[MC1211_TRAPDOOR_PLACEMENT_USE_RETURN]"
                + useFields("useOnBlock-return", world, itemId, context.getClickedPos(), context.getClickedFace(),
                context.getClickLocation(), expected)
                + " useOnBlockResult=" + result
                + " useOnBlockAccepted=" + (result != null && result.consumesAction())
                + " blockItemPlaceCalled=" + placeCalled
                + " expectedTrapdoorState=" + expectedState
                + " trapdoorPresent=" + expectedState.is(Blocks.OAK_TRAPDOOR)
                + " trapdoorResolvedToAir=" + expectedState.isAir()
                + " gameplayPatch=false");
    }

    public static boolean recordPlaceHead(BlockPlaceContext context, ResourceLocation itemId) {
        if (!isOakTrapdoorPlace(context, itemId)) {
            return false;
        }
        Level world = context.getLevel();
        Direction face = context.getClickedFace();
        BlockPos placePos = context.getClickedPos();
        BlockPos clickedPos = placePos.relative(face.getOpposite());
        System.out.println("[MC1211_TRAPDOOR_PLACEMENT_PLACE_HEAD]"
                + placeFields("place-head", world, itemId, clickedPos, face, context.getClickLocation(), placePos)
                + " gameplayPatch=false");
        return true;
    }

    public static void recordPlaceReturn(BlockPlaceContext context, ResourceLocation itemId, InteractionResult result) {
        if (!isOakTrapdoorPlace(context, itemId)) {
            return;
        }
        Level world = context.getLevel();
        Direction face = context.getClickedFace();
        BlockPos placePos = context.getClickedPos();
        BlockPos clickedPos = placePos.relative(face.getOpposite());
        logPlaceReturn("place-return", world, itemId, clickedPos, face, context.getClickLocation(), placePos, result);
        if (!world.isClientSide() && world.getServer() != null) {
            world.getServer().execute(() -> logPlaceReturn(
                    "server-after-queued-tick",
                    world,
                    itemId,
                    clickedPos,
                    face,
                    context.getClickLocation(),
                    placePos,
                    result));
        }
    }

    private static void logPlaceReturn(
            String phase,
            Level world,
            ResourceLocation itemId,
            BlockPos clickedPos,
            Direction face,
            Vec3 hit,
            BlockPos placePos,
            InteractionResult result
    ) {
        BlockState finalState = world.getBlockState(placePos);
        System.out.println("[MC1211_TRAPDOOR_PLACEMENT_PLACE_RETURN]"
                + placeFields(phase, world, itemId, clickedPos, face, hit, placePos)
                + " placementResult=" + result
                + " placementAccepted=" + (result != null && result.consumesAction())
                + " finalTrapdoorState=" + finalState
                + " finalTrapdoorDy=" + SlabSupport.getYOffset(world, placePos, finalState)
                + " trapdoorPresent=" + finalState.is(Blocks.OAK_TRAPDOOR)
                + " trapdoorResolvedToAir=" + finalState.isAir()
                + " classification=" + placeClassification(result, finalState)
                + " gameplayPatch=false");
    }

    private static boolean isOakTrapdoorClick(Level world, ItemStack heldStack, BlockHitResult hit) {
        if (!enabled() || world == null || heldStack == null || hit == null) {
            return false;
        }
        return heldStack.getItem() instanceof BlockItem blockItem && blockItem.getBlock().equals(Blocks.OAK_TRAPDOOR);
    }

    private static boolean isOakTrapdoorUse(UseOnContext context, ResourceLocation itemId) {
        if (!enabled() || context == null || context.getLevel() == null) {
            return false;
        }
        return isOakTrapdoor(itemId);
    }

    private static boolean isOakTrapdoorPlace(BlockPlaceContext context, ResourceLocation itemId) {
        if (!enabled() || context == null || context.getLevel() == null) {
            return false;
        }
        return isOakTrapdoor(itemId);
    }

    private static boolean isOakTrapdoor(ResourceLocation itemId) {
        return "minecraft:oak_trapdoor".equals(String.valueOf(itemId));
    }

    private static boolean isBottomSlab(BlockState state) {
        return state != null
                && state.getBlock() instanceof SlabBlock
                && state.hasProperty(SlabBlock.TYPE)
                && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    private static BlockPos expectedTrapdoorPos(BlockHitResult hit) {
        return hit.getBlockPos().relative(hit.getDirection());
    }

    private static String commonClickFields(Level world, ClickSnapshot snapshot) {
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
            Level world,
            ResourceLocation itemId,
            BlockPos clickedPos,
            Direction face,
            Vec3 hit,
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
            Level world,
            ResourceLocation itemId,
            BlockPos clickedPos,
            Direction face,
            Vec3 hit,
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
                + " expectedTrapdoorPos=" + shortPos(clickedPos.relative(face))
                + " nativePlacePos=" + shortPos(placePos)
                + " nativePlaceState=" + placeState
                + " nativePlaceDy=" + SlabSupport.getYOffset(world, placePos, placeState);
    }

    private static String placeClassification(InteractionResult result, BlockState finalState) {
        if (finalState.is(Blocks.OAK_TRAPDOOR)) {
            return "TRAPDOOR_PRESENT";
        }
        if (result != null && result.consumesAction() && finalState.isAir()) {
            return "ACCEPTED_BUT_AIR";
        }
        if (result != null && !result.consumesAction()) {
            return "PLACEMENT_REJECTED";
        }
        return "TRAPDOOR_ABSENT";
    }

    private static boolean bottomSlabUnderside(Direction face, BlockState state) {
        return face == Direction.DOWN && isBottomSlab(state);
    }

    private static String formatHit(Level world, HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit)) {
            return hit == null ? "null" : hit.getType().toString();
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        return "{pos=" + shortPos(pos)
                + " face=" + blockHit.getDirection()
                + " hit=" + formatVec(blockHit.getLocation())
                + " state=" + state
                + " dy=" + SlabSupport.getYOffset(world, pos, state)
                + "}";
    }

    private static String side(Level world) {
        return world.isClientSide() ? "CLIENT" : "SERVER";
    }

    private static String heldItem(ItemStack stack) {
        return stack == null || stack.isEmpty() ? "empty" : String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static String shortPos(BlockPos pos) {
        return pos == null ? "null" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String formatVec(Vec3 vec) {
        if (vec == null) {
            return "null";
        }
        return String.format("%.3f,%.3f,%.3f", vec.x, vec.y, vec.z);
    }

    public record ClickSnapshot(
            int clickIndex,
            BlockPos targetPos,
            Direction face,
            Vec3 hit,
            BlockPos expectedTrapdoorPos,
            String heldItem
    ) {
    }
}
