package com.slabbed.mixin.client;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.util.SlabbedDiagnosticsBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashMap;

/**
 * Client-side placement prediction. The instant the local client predicts a block
 * placement, optimistically apply the same anchor/carrier/freeze markers the server
 * will set (routed into the render mirror by {@link SlabAnchorAttachment#predictClientPlacement}),
 * so the offset model bakes at the correct lowered dy on the FIRST frame instead of
 * baking un-lowered and snapping down when the authoritative sync arrives a few ticks
 * later. Runs on both sides but no-ops off the client (guarded on {@code isClientSide}).
 */
@Mixin(BlockItem.class)
public abstract class BlockItemClientPredictMixin {
    private static final ThreadLocal<PlacementSnapshot> SLABBED_DIAGNOSTIC_SNAPSHOT =
            new ThreadLocal<>();

    private record PlacementSnapshot(
            BlockPos placementPos,
            BlockPos clickedOwnerPos,
            Direction clickedFace,
            String heldItem,
            String playerUuid,
            String dimensionId,
            String beforeState,
            double beforeDy) {
    }

    @Inject(
            method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"))
    private void slabbed$armDiagnosticsBeforeClientPlacement(
            BlockPlaceContext ctx,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        SLABBED_DIAGNOSTIC_SNAPSHOT.remove();
        Level level = ctx.getLevel();
        if (!level.isClientSide() || !SlabbedDiagnosticsBridge.isRecorderEnabled()) {
            return;
        }
        BlockPos placementPos = ctx.getClickedPos().immutable();
        BlockState beforeState = level.getBlockState(placementPos);
        Direction face = ctx.getClickedFace();
        BlockPos clickedOwner = ctx.replacingClickedOnBlock()
                ? placementPos
                : placementPos.relative(face.getOpposite());
        String playerUuid = ctx.getPlayer() == null
                ? "none" : ctx.getPlayer().getUUID().toString();
        SLABBED_DIAGNOSTIC_SNAPSHOT.set(new PlacementSnapshot(
                placementPos,
                clickedOwner.immutable(),
                face,
                ctx.getItemInHand().isEmpty()
                        ? "empty" : ctx.getItemInHand().getItem().toString(),
                playerUuid,
                level.dimension().location().toString(),
                beforeState.toString(),
                ClientDy.dyFor(level, placementPos, beforeState)));
        SlabbedDiagnosticsBridge.armPlacement(level, placementPos, level.getGameTime());
    }

    @Inject(
            method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At("RETURN"))
    private void slabbed$predictClientPlacement(
            BlockPlaceContext ctx,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        PlacementSnapshot diagnostic = SLABBED_DIAGNOSTIC_SNAPSHOT.get();
        SLABBED_DIAGNOSTIC_SNAPSHOT.remove();
        if (!cir.getReturnValue().consumesAction()) {
            return;
        }
        Level level = ctx.getLevel();
        if (!level.isClientSide()) {
            return;
        }
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        SlabAnchorAttachment.predictClientPlacement(level, pos, state);
        if (diagnostic != null && SlabbedDiagnosticsBridge.isRecorderEnabled()) {
            double afterDy = ClientDy.dyFor(level, pos, state);
            double storedDy = SlabAnchorAttachment.storedPlacementDy(level, pos);
            LinkedHashMap<String, String> row = new LinkedHashMap<>();
            row.put("side", "client");
            row.put("actionType", "place_block");
            row.put("originHint", SlabbedDiagnosticsBridge.PLAYER_AUTHORED);
            row.put("heldItem", diagnostic.heldItem());
            row.put("playerUuid", diagnostic.playerUuid());
            row.put("dimensionId", diagnostic.dimensionId());
            row.put("clickedOwnerPos", diagnostic.clickedOwnerPos().toShortString());
            row.put("clickedFace", diagnostic.clickedFace().getName());
            row.put("placementPos", diagnostic.placementPos().toShortString());
            row.put("placeBeforeState", diagnostic.beforeState());
            row.put("placeBeforeDy", Double.toString(diagnostic.beforeDy()));
            row.put("afterState", state.toString());
            row.put("afterDy", Double.toString(afterDy));
            row.put("afterStoredDy", Double.toString(storedDy));
            row.put("afterStoredDyBits",
                    Long.toUnsignedString(Double.doubleToRawLongBits(storedDy)));
            row.put("actualResult", cir.getReturnValue().toString());
            SlabbedDiagnosticsBridge.recordAction(row);
        }
    }
}
