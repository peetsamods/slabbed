package com.slabbed.mixin.client;

import com.slabbed.util.SlabbedOffsetRaycast;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Jade's block trace must consider a nearer block whose visible body is below its grid cell. */
@Pseudo
@Mixin(targets = "snownee.jade.overlay.RayTracing", remap = false)
public abstract class JadeOffsetRaycastMixin {
    @Redirect(
            method = "rayTrace",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/World;raycast(Lnet/minecraft/world/RaycastContext;)Lnet/minecraft/util/hit/BlockHitResult;",
                    remap = true),
            require = 0,
            remap = false
    )
    private BlockHitResult slabbed$nearestLoweredBlock(World world, RaycastContext context,
                                                     Entity camera, double reach) {
        BlockHitResult original = world.raycast(context);
        BlockHitResult lowered = SlabbedOffsetRaycast.raycast(
                world, context.getStart(), context.getEnd(), ShapeContext.of(camera));
        if (lowered.getType() != HitResult.Type.BLOCK
                || SlabSupport.getYOffset(world, lowered.getBlockPos(), world.getBlockState(lowered.getBlockPos())) >= 0.0) {
            return original;
        }
        // The original trace retains nearer fluid hits. Jade compares the selected block with
        // its entity trace after this call, so entity selection keeps its existing authority.
        if (original.getType() == HitResult.Type.BLOCK
                && original.getPos().squaredDistanceTo(context.getStart())
                <= lowered.getPos().squaredDistanceTo(context.getStart()) + 1.0e-8) {
            return original;
        }
        return lowered;
    }
}
