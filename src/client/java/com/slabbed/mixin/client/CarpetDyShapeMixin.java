package com.slabbed.mixin.client;

import com.slabbed.client.ClientDy;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.MossyCarpetBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({CarpetBlock.class, MossyCarpetBlock.class})
public class CarpetDyShapeMixin {

    @Inject(method = "getShape", at = @At("RETURN"), cancellable = true)
    private void slabbed$offsetCarpetOutline(BlockState state, BlockGetter world, BlockPos pos, CollisionContext ctx,
                                             CallbackInfoReturnable<VoxelShape> cir) {
        // Collision delegates to this shape too; its owner applies the stored offset exactly once.
        if (SlabSupport.isCollisionShapeQuery() || SlabSupport.isVanillaCollisionShapeQuery()) {
            return;
        }
        cir.setReturnValue(ClientDy.offsetShape(world, pos, state, cir.getReturnValue()));
    }
}
