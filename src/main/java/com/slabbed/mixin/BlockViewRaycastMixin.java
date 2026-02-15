package com.slabbed.mixin;

import com.slabbed.client.ClientDy;
import com.slabbed.util.RaycastOffsetContext;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockView.class)
public interface BlockViewRaycastMixin {

    @Inject(method = "raycastBlock", at = @At("HEAD"), cancellable = true)
    private void slabbed$raycastBlockAdjusted(Vec3d start, Vec3d end, BlockPos pos, VoxelShape shape, BlockState state,
                                              CallbackInfoReturnable<BlockHitResult> cir) {
        if (!RaycastOffsetContext.isInRaycast()) {
            return;
        }
        BlockView world = (BlockView) (Object) this;
        if (ClientDy.dyFor(world, pos, state) != -0.5f) {
            return;
        }
        BlockPos adjustedPos = pos.down();
        VoxelShape adjustedShape = shape.offset(0.0D, 0.5D, 0.0D);
        BlockHitResult hit = adjustedShape.raycast(start, end, adjustedPos);
        if (hit != null) {
            VoxelShape rayShape = state.getRaycastShape(world, pos).offset(0.0D, 0.5D, 0.0D);
            BlockHitResult hit2 = rayShape.raycast(start, end, adjustedPos);
            if (hit2 != null && hit2.getPos().subtract(start).lengthSquared() < hit.getPos().subtract(start).lengthSquared()) {
                hit = hit.withSide(hit2.getSide());
            }
        }
        cir.setReturnValue(hit);
    }
}
