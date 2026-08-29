package com.slabbed.mixin.client;

import com.slabbed.client.ClientDy;
import com.slabbed.util.SlabSupport;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CarpetBlock.class)
public class CarpetDyShapeMixin {

    @Inject(method = "getShape", at = @At("RETURN"), cancellable = true)
    private void slabbed$offsetCarpetOutline(BlockState state, BlockGetter world, BlockPos pos, CollisionContext ctx,
                                             CallbackInfoReturnable<VoxelShape> cir) {
        // Both guards, because this injector owns carpet's shape for BOTH consumers that ask.
        // The movement broadphase applies dy itself after reading the raw vanilla shape, so a
        // shape already moved here is moved twice - and only on the client, since this mixin does
        // not load server-side, which is a consumer disagreement DY_SPEC L1 forbids outright.
        // The shared state injector carries exactly this pair and steps aside for CarpetBlock;
        // taking ownership of the outline means taking its guards with it.
        if (SlabSupport.isRawShapeProbeActive() || SlabSupport.isVanillaCollisionShapeQuery()) {
            return;
        }
        double dy = ClientDy.dyFor(world, pos, state);
        if (dy != 0.0) {
            cir.setReturnValue(cir.getReturnValue().move(0.0, dy, 0.0));
        }
    }
}
