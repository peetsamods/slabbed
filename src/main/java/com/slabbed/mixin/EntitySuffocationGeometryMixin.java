package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Suffocation tests the occupied body, including lowered owners outside the eye's logical cell. */
@Mixin(Entity.class)
public abstract class EntitySuffocationGeometryMixin {
    // Match the owner window used by SlabSupport.withHangingLoweredCollisionFromAbove.
    @Unique
    private static final int SLABBED$SUFFOCATION_OWNER_DEPTH = 16;

    @Redirect(method = "lambda$isInWall$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape slabbed$suffocationBody(BlockState state, BlockGetter world, BlockPos pos) {
        if (SlabSupport.getYOffset(world, pos, state) < -1.0e-6) {
            return state.getCollisionShape(world, pos, CollisionContext.empty());
        }
        return state.getCollisionShape(world, pos);
    }

    @Inject(method = "isInWall", at = @At("RETURN"), cancellable = true)
    private void slabbed$includeLoweredSuffocatingOwners(CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;
        if (cir.getReturnValueZ() || entity.noPhysics) {
            return;
        }
        double width = entity.getBbWidth() * 0.8F;
        AABB eye = AABB.ofSize(entity.getEyePosition(), width, 1.0e-6, width);
        VoxelShape eyeShape = Shapes.create(eye);
        var world = entity.level();
        for (BlockPos cell : BlockPos.betweenClosed(eye)) {
            for (int depth = 1; depth <= SLABBED$SUFFOCATION_OWNER_DEPTH; depth++) {
                BlockPos owner = cell.above(depth);
                BlockState state = world.getBlockState(owner);
                if (state.isAir()) {
                    if (SlabAnchorAttachment.FROZEN_DY_ENABLED
                            && SlabAnchorAttachment.anyStoredOwnerOverflowsInto(world, cell,
                            depth + 1, SLABBED$SUFFOCATION_OWNER_DEPTH)) {
                        continue;
                    }
                    break;
                }
                if (!state.isSuffocating(world, owner)
                        || SlabSupport.getYOffset(world, owner, state) >= -1.0e-6) {
                    continue;
                }
                VoxelShape body = state.getCollisionShape(world, owner, CollisionContext.empty()).move(owner);
                if (Shapes.joinIsNotEmpty(body, eyeShape, BooleanOp.AND)) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }
}
