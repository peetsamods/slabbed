package com.slabbed.mixin;

import com.slabbed.Slabbed;
import com.slabbed.dev.audit.LoweredSideLiveHitRemapRuntimeAudit;
import com.slabbed.util.SlabbedDebug;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.item.BlockItem;
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

    private static boolean slabbed$isTracedBlock(Block block) {
        return block instanceof SlabBlock || block instanceof CarpetBlock || block.getDefaultState().isOpaqueFullCube();
    }

    @Inject(method = "place", at = @At("HEAD"))
    private void slabbed$tracePlaceHead(ItemPlacementContext ctx, CallbackInfoReturnable<ActionResult> cir) {
        if (!SlabbedDebug.DEBUG_SBSB) return;
        BlockItem self = (BlockItem) (Object) this;
        if (!slabbed$isTracedBlock(self.getBlock())) return;

        World world = ctx.getWorld();
        Direction face = ctx.getSide();
        BlockPos placePos = ctx.getBlockPos();
        LoweredSideLiveHitRemapRuntimeAudit.recordPlacementContext(ctx);
        BlockPos hitPos = placePos.offset(face.getOpposite());
        BlockState hitState = world.getBlockState(hitPos);
        BlockState placeState = world.getBlockState(placePos);

        double dyHit = SlabSupport.getYOffset(world, hitPos, hitState);
        double dyPlace = SlabSupport.getYOffset(world, placePos, placeState);
        if (dyHit == 0.0 && dyPlace == 0.0) return;

        String side = world.isClient() ? "CLIENT" : "SERVER";
        Identifier itemId = Registries.ITEM.getId(self);
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
        TraceCtx trace = SLABBED$TRACE.get();
        if (trace == null) {
            return;
        }

        try {
            BlockItem self = (BlockItem) (Object) this;
            if (!slabbed$isTracedBlock(self.getBlock())) return;

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
            LoweredSideLiveHitRemapRuntimeAudit.recordPlacementResult(ctx, cir.getReturnValue());
        } finally {
            SLABBED$TRACE.remove();
        }
    }
}
