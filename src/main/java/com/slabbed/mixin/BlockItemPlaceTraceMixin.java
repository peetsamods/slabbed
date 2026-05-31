package com.slabbed.mixin;

import com.slabbed.Slabbed;
import com.slabbed.util.Mc1211TrapdoorUnderBottomRecorder;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.RuntimeDiagnostics;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemPlaceTraceMixin {

    private record TraceCtx(String side, Identifier itemId, Direction face, BlockPos hitPos, BlockPos placePos) {}

    private static final ThreadLocal<TraceCtx> SLABBED$TRACE = new ThreadLocal<>();
    private static final ThreadLocal<TraceCtx> SLABBED$INSPECT_TRACE = new ThreadLocal<>();
    private static final ThreadLocal<TraceCtx> SLABBED$INSPECT_USE_TRACE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SLABBED$INSPECT_PLACE_CALLED = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SLABBED$MC1211_TRAPDOOR_UNDER_BOTTOM_USE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SLABBED$MC1211_TRAPDOOR_UNDER_BOTTOM_PLACE_CALLED =
            new ThreadLocal<>();

    private static boolean slabbed$isTracedBlock(Block block, World world, BlockPos pos) {
        return block instanceof SlabBlock || block instanceof CarpetBlock || block.getDefaultState().isOpaqueFullCube(world, pos);
    }

    @Inject(method = "place", at = @At("HEAD"))
    private void slabbed$tracePlaceHead(ItemPlacementContext ctx, CallbackInfoReturnable<ActionResult> cir) {
        BlockItem self = (BlockItem) (Object) this;
        World world = ctx.getWorld();
        Direction face = ctx.getSide();
        BlockPos placePos = ctx.getBlockPos();
        Identifier itemId = Registries.ITEM.getId(self);
        if (Mc1211TrapdoorUnderBottomRecorder.recordPlaceHead(ctx, itemId)) {
            SLABBED$MC1211_TRAPDOOR_UNDER_BOTTOM_PLACE_CALLED.set(Boolean.TRUE);
        }
        if (!slabbed$isTracedBlock(self.getBlock(), world, placePos)) return;
        BlockPos hitPos = placePos.offset(face.getOpposite());
        String side = world.isClient() ? "CLIENT" : "SERVER";
        boolean heldIsSlab = self.getBlock() instanceof SlabBlock;

        RuntimeDiagnostics.recordPlace(
                "place-head",
                itemId,
                heldIsSlab,
                ctx,
                null,
                "anchorFinalization=not_yet_returned");

        if (RuntimeDiagnostics.isInspectEnabled()) {
            SLABBED$INSPECT_TRACE.set(new TraceCtx(side, itemId, face, hitPos, placePos));
            SLABBED$INSPECT_PLACE_CALLED.set(Boolean.TRUE);
            RuntimeDiagnostics.logInspectPlacement("HEAD", world, itemId, ctx, hitPos, placePos, null);
        }

        if (!RuntimeDiagnostics.isEnabled()) return;

        RuntimeDiagnostics.invoke(
                "recordPlacementContext",
                new Class<?>[]{ItemPlacementContext.class},
                ctx);
        BlockState hitState = world.getBlockState(hitPos);
        BlockState placeState = world.getBlockState(placePos);

        double dyHit = SlabSupport.getYOffset(world, hitPos, hitState);
        double dyPlace = SlabSupport.getYOffset(world, placePos, placeState);
        if (dyHit == 0.0 && dyPlace == 0.0) return;

        SLABBED$TRACE.set(new TraceCtx(side, itemId, face, hitPos, placePos));

        Slabbed.LOGGER.info("[SBSB-TRACE][HEAD] side={} item={} face={} hitPos={} hitState={} dyHit={} placePos={} placeState={} dyPlace={} placeAbove={}",
                side,
                itemId,
                face,
                hitPos,
                hitState,
                dyHit,
                placePos,
                placeState,
                dyPlace,
                world.getBlockState(placePos.up()));
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void slabbed$tracePlaceReturn(ItemPlacementContext ctx, CallbackInfoReturnable<ActionResult> cir) {
        TraceCtx inspectTrace = SLABBED$INSPECT_TRACE.get();
        if (inspectTrace != null) {
            try {
                BlockItem self = (BlockItem) (Object) this;
                if (slabbed$isTracedBlock(self.getBlock(), ctx.getWorld(), inspectTrace.placePos())) {
                    RuntimeDiagnostics.logInspectPlacement(
                            "RETURN",
                            ctx.getWorld(),
                            inspectTrace.itemId(),
                            ctx,
                            inspectTrace.hitPos(),
                            inspectTrace.placePos(),
                            cir.getReturnValue());
                }
            } finally {
                SLABBED$INSPECT_TRACE.remove();
            }
        }

        BlockItem recorderSelf = (BlockItem) (Object) this;
        Identifier recorderItemId = Registries.ITEM.getId(recorderSelf);
        Mc1211TrapdoorUnderBottomRecorder.recordPlaceReturn(ctx, recorderItemId, cir.getReturnValue());
        if (slabbed$isTracedBlock(recorderSelf.getBlock(), ctx.getWorld(), ctx.getBlockPos())) {
            boolean heldIsSlab = recorderSelf.getBlock() instanceof SlabBlock;
            RuntimeDiagnostics.recordPlace(
                    "place-return",
                    recorderItemId,
                    heldIsSlab,
                    ctx,
                    cir.getReturnValue(),
                    "anchorFinalization=deferred_to_finalization_mixin");
            RuntimeDiagnostics.recordAfterTick(
                    recorderItemId,
                    heldIsSlab,
                    ctx,
                    cir.getReturnValue(),
                    "anchorFinalization=after_tick_observation");
        }

        TraceCtx trace = SLABBED$TRACE.get();
        if (trace == null) {
            return;
        }

        try {
            BlockItem self = (BlockItem) (Object) this;
            if (!slabbed$isTracedBlock(self.getBlock(), ctx.getWorld(), trace.placePos())) return;

            World world = ctx.getWorld();
            BlockPos placePos = trace.placePos();
            BlockState placeState = world.getBlockState(placePos);
            double dyPlace = SlabSupport.getYOffset(world, placePos, placeState);

            Slabbed.LOGGER.info("[SBSB-TRACE][RETURN] side={} item={} face={} hitPos={} placePos={} placeState={} dyPlace={} result={}",
                    trace.side(),
                    trace.itemId(),
                    trace.face(),
                    trace.hitPos(),
                    placePos,
                    placeState,
                    dyPlace,
                    cir.getReturnValue());
            RuntimeDiagnostics.invoke(
                    "recordPlacementResult",
                    new Class<?>[]{ItemPlacementContext.class, ActionResult.class},
                    ctx, cir.getReturnValue());
        } finally {
            SLABBED$TRACE.remove();
        }
    }

    @Inject(method = "useOnBlock", at = @At("HEAD"))
    private void slabbed$traceUseOnBlockHead(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        BlockItem self = (BlockItem) (Object) this;
        if (context == null || context.getWorld() == null) {
            return;
        }
        Identifier itemId = Registries.ITEM.getId(self);
        SLABBED$MC1211_TRAPDOOR_UNDER_BOTTOM_PLACE_CALLED.remove();
        if (Mc1211TrapdoorUnderBottomRecorder.recordUseHead(context, itemId)) {
            SLABBED$MC1211_TRAPDOOR_UNDER_BOTTOM_USE.set(Boolean.TRUE);
        }
        if (!slabbed$isTracedBlock(self.getBlock(), context.getWorld(), context.getBlockPos())) {
            return;
        }
        RuntimeDiagnostics.recordUseHead(itemId, self.getBlock() instanceof SlabBlock, context);

        if (!RuntimeDiagnostics.isInspectEnabled()) {
            return;
        }

        SLABBED$INSPECT_PLACE_CALLED.remove();
        RuntimeDiagnostics.logInspectClickPair(context, itemId);
        BlockPos placePos = context.getBlockPos().offset(context.getSide());
        SLABBED$INSPECT_USE_TRACE.set(new TraceCtx(
                context.getWorld().isClient() ? "CLIENT" : "SERVER",
                itemId,
                context.getSide(),
                context.getBlockPos(),
                placePos
        ));
    }

    @Inject(method = "useOnBlock", at = @At("RETURN"))
    private void slabbed$traceUseOnBlockReturn(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        try {
            if (Boolean.TRUE.equals(SLABBED$MC1211_TRAPDOOR_UNDER_BOTTOM_USE.get())
                    && context != null
                    && context.getWorld() != null) {
                BlockItem self = (BlockItem) (Object) this;
                Mc1211TrapdoorUnderBottomRecorder.recordUseReturn(
                        context,
                        Registries.ITEM.getId(self),
                        cir.getReturnValue(),
                        Boolean.TRUE.equals(SLABBED$MC1211_TRAPDOOR_UNDER_BOTTOM_PLACE_CALLED.get()));
            }
        } finally {
            SLABBED$MC1211_TRAPDOOR_UNDER_BOTTOM_USE.remove();
            SLABBED$MC1211_TRAPDOOR_UNDER_BOTTOM_PLACE_CALLED.remove();
        }

        if (!RuntimeDiagnostics.isInspectEnabled()) {
            return;
        }

        try {
            TraceCtx trace = SLABBED$INSPECT_USE_TRACE.get();
            if (trace != null
                    && !Boolean.TRUE.equals(SLABBED$INSPECT_PLACE_CALLED.get())
                    && trace.itemId() != null
                    && context != null
                    && context.getWorld() != null) {
                RuntimeDiagnostics.logInspectPlacementNoReturn(context.getWorld(), trace.itemId(), trace.face(), trace.hitPos(), trace.placePos());
            }
        } finally {
            SLABBED$INSPECT_USE_TRACE.remove();
            SLABBED$INSPECT_PLACE_CALLED.remove();
            RuntimeDiagnostics.clearInspectClickPair();
        }
    }
}
