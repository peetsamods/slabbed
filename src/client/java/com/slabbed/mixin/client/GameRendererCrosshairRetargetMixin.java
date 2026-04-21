package com.slabbed.mixin.client;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <p>Client-side raycast retarget for lowered block-entity-style blocks
 * (chests, etc.) placed on bottom slabs.
 *
 * <p>When the chest's visible lower half extends into {@code pos.down()}'s
 * voxel, vanilla per-voxel DDA raycast hits the slab below before it can
 * consider the chest's offset shape at {@code pos}. After vanilla
 * {@link GameRenderer#updateCrosshairTarget} has resolved the crosshair,
 * we re-test the ray against the owning block above (if it qualifies per
 * {@link SlabSupport#isLoweredBlockEntityVisual}) and, if the ray hits
 * its offset shape at an equal or closer distance, we replace
 * {@link MinecraftClient#crosshairTarget} with that result.
 *
 * <p>The shape tested is the block's <em>outline</em> shape, using the
 * camera entity's {@link ShapeContext}. This mirrors vanilla crosshair
 * targeting which uses {@code RaycastContext.ShapeType.OUTLINE}; using
 * the raycast shape instead would silently miss blocks (chests, barrels,
 * signs, etc.) whose {@code getRaycastShape} falls back to empty.
 *
 * <p>This retarget is the single ownership rule; the outline renderer
 * automatically follows because it reads {@code crosshairTarget}.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererCrosshairRetargetMixin {

    @Shadow @Final private MinecraftClient client;

    @Inject(method = "updateCrosshairTarget", at = @At("TAIL"))
    private void slabbed$retargetLoweredBlockEntity(float tickProgress, CallbackInfo ci) {
        HitResult ht = client.crosshairTarget;
        if (ht == null || ht.getType() != HitResult.Type.BLOCK) {
            return;
        }
        if (!(ht instanceof BlockHitResult slabHit)) {
            return;
        }

        ClientWorld world = client.world;
        Entity cam = client.getCameraEntity();
        if (world == null || cam == null) {
            return;
        }

        BlockPos abovePos = slabHit.getBlockPos().up();
        BlockState aboveState = world.getBlockState(abovePos);
        if (!SlabSupport.isLoweredBlockEntityVisual(world, abovePos, aboveState)
                && !SlabSupport.isLoweredTorchVisual(world, abovePos, aboveState)) {
            return;
        }

        Vec3d eye = cam.getCameraPosVec(tickProgress);
        Vec3d slabHitPos = slabHit.getPos();
        Vec3d dir = slabHitPos.subtract(eye);
        double slabDist = dir.length();
        if (slabDist <= 0.0) {
            return;
        }
        // Extend slightly past the original hit so shape.raycast can intersect
        // the chest's offset front face which may be marginally further along
        // the ray than the slab-top intersection.
        Vec3d end = eye.add(dir.normalize().multiply(slabDist + 0.5));

        // Mirror vanilla crosshair ownership: crosshair targeting uses
        // RaycastContext.ShapeType.OUTLINE, which resolves to getOutlineShape
        // with the camera entity's ShapeContext. Blocks whose native
        // getRaycastShape is empty (most BlockEntityProvider blocks) would
        // otherwise never retarget.
        VoxelShape shape = aboveState.getOutlineShape(world, abovePos, ShapeContext.of(cam));
        BlockHitResult chestHit = shape.raycast(eye, end, abovePos);
        if (chestHit == null) {
            return;
        }
        double chestDist2 = chestHit.getPos().squaredDistanceTo(eye);
        double slabDist2 = slabHitPos.squaredDistanceTo(eye);
        // Only retarget when the chest's offset shape is actually closer or
        // coincident with the slab hit — this is the overflow signature.
        if (chestDist2 > slabDist2 + 1.0e-6) {
            return;
        }

        client.crosshairTarget = chestHit;
    }
}
