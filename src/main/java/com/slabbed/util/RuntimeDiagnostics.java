package com.slabbed.util;

import com.slabbed.anchor.SlabAnchorMarker;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class RuntimeDiagnostics {
    private RuntimeDiagnostics() {
    }

    public static boolean isEnabled() {
        return false;
    }

    public static boolean isInspectEnabled() {
        return false;
    }

    public static boolean isBsFbLiveTraceEnabled() {
        return false;
    }

    public static boolean compoundLivePathEnabled() {
        return false;
    }

    public static void invoke(String methodName, Class<?>[] paramTypes, Object... args) {
    }

    public static void logInspectSessionStart() {
    }

    public static void logInspectClientTarget(
            Level world,
            Vec3 eye,
            Vec3 rayEnd,
            float yaw,
            float pitch,
            ItemStack held,
            HitResult initialTarget,
            HitResult finalTarget,
            String decision,
            boolean sideSlabRetargetFired
    ) {
    }

    public static void logInspectIntent(UseOnContext incoming, UseOnContext outgoing, String reason) {
    }

    public static void logInspectClickPair(UseOnContext context, ResourceLocation itemId) {
    }

    public static void clearInspectClickPair() {
    }

    public static void logInspectPlacement(
            String stage,
            Level world,
            ResourceLocation itemId,
            BlockPlaceContext ctx,
            BlockPos hitPos,
            BlockPos placePos,
            InteractionResult result
    ) {
    }

    public static void logInspectPlacementNoReturn(
            Level world,
            ResourceLocation itemId,
            Direction face,
            BlockPos hitPos,
            BlockPos placePos
    ) {
    }

    public static void initBsFbLiveTraceClient() {
    }

    public static void captureBsFbLiveTrace(Level world, BlockPos supportPos, BlockPos fullPos, String label) {
    }

    public static void captureClientBsFbLiveTrace(BlockPos supportPos, BlockPos fullPos, String label) {
    }

    public static void recordUseHead(ResourceLocation blockItemId, boolean heldIsSlab, UseOnContext context) {
    }

    public static void recordPlace(
            String phase,
            ResourceLocation blockItemId,
            boolean heldIsSlab,
            BlockPlaceContext context,
            InteractionResult result,
            String finalization
    ) {
    }

    public static void recordAfterTick(
            ResourceLocation blockItemId,
            boolean heldIsSlab,
            BlockPlaceContext context,
            InteractionResult result,
            String finalization
    ) {
    }

    public static void recordCompoundFinalization(
            String phase,
            ResourceLocation blockItemId,
            boolean heldIsSlab,
            BlockPlaceContext context,
            InteractionResult result,
            String branch,
            BlockPos sourcePos,
            boolean compoundSidecarBefore,
            boolean compoundSidecarAfter,
            boolean persistentAnchorBefore,
            boolean persistentAnchorAfter,
            String reason
    ) {
    }

    public static void logManualPlacementIntent(UseOnContext incoming, UseOnContext outgoing, String reason) {
    }

    public static void logManualServerTolerance(
            Level world,
            BlockHitResult hit,
            ItemStack heldStack,
            Vec3 centerBefore,
            Vec3 centerAfter,
            String reason
    ) {
    }

    public static void logSlabSupportDecision(
            BlockGetter world,
            BlockPos sourcePos,
            BlockState sourceState,
            Direction intendedDirection,
            Vec3 hitPos,
            Object decision
    ) {
    }

    public static void recordFenceWallClientTarget(
            Level world,
            Entity camera,
            Player player,
            Vec3 eye,
            Vec3 rayEnd,
            ItemStack held,
            HitResult initialTarget,
            HitResult finalTarget,
            String finalDecision
    ) {
    }

    public static void logFenceWallServerTolerance(
            Level world,
            Player player,
            BlockHitResult hit,
            ItemStack held,
            Vec3 validationCenter,
            Vec3 shiftedValidationCenter
    ) {
    }

    public static void recordSlabHeightClientTarget(
            Level world,
            Entity camera,
            Player player,
            Vec3 eye,
            Vec3 rayEnd,
            ItemStack held,
            HitResult initialTarget,
            HitResult finalTarget,
            String finalDecision
    ) {
    }

    public static void logSlabHeightServerTolerance(
            Level world,
            Player player,
            BlockHitResult hit,
            ItemStack held,
            Vec3 validationCenter,
            Vec3 shiftedValidationCenter
    ) {
    }

    public static void recordLiveTorchComfortTrace(Level world, String reason, BlockPos pos, BlockPos supportPos) {
    }

    public static boolean beta35SlabJumpSourceTruthEnabled() {
        return false;
    }

    public static void recordBeta35SlabJumpAnchorEvent(
            Level world,
            String action,
            SlabAnchorMarker type,
            BlockPos pos,
            BlockState oldState,
            BlockState newState
    ) {
    }

    public static void recordModelDyTrace(
            String method,
            BlockAndTintGetter world,
            BlockPos pos,
            BlockState state,
            double dy
    ) {
    }

    public static void recordBeta4ModelDyTrace(
            String method,
            BlockAndTintGetter world,
            BlockPos pos,
            BlockState state,
            double dy
    ) {
    }
}
