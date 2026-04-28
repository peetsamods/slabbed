package com.slabbed.mixin.client;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <p>Client-side raycast retarget for lowered owners placed on bottom slabs.
 *
 * <p>When the lowered block's visible lower half extends into {@code pos.down()}'s
 * voxel, vanilla per-voxel DDA raycast hits the slab below before it can
 * consider the offset shape at {@code pos}. After vanilla
 * {@link GameRenderer#updateCrosshairTarget} has resolved the crosshair,
 * we re-test the ray against the owning block above (if it qualifies per
 * the lowered-owner helpers in {@link SlabSupport}) and, if the ray hits
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
        if (ht == null) {
            return;
        }
        if (!slabbed$isSlabPlacementIntent()) {
            BlockHitResult anchoredHit = slabbed$retargetAnchoredLoweredFullBlock(tickProgress, ht);
            if (anchoredHit != null) {
                client.crosshairTarget = anchoredHit;
                return;
            }
        }
        BlockHitResult loweredSlabHit = slabbed$retargetLoweredSideSlab(tickProgress, ht);
        if (loweredSlabHit != null) {
            client.crosshairTarget = loweredSlabHit;
            return;
        }
        if (ht.getType() == HitResult.Type.MISS) {
            return;
        }
        if (ht.getType() != HitResult.Type.BLOCK) {
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
        boolean loweredOwner =
                SlabSupport.isLoweredBlockEntityVisual(world, abovePos, aboveState)
                        || SlabSupport.isLoweredTorchVisual(world, abovePos, aboveState)
                        || SlabSupport.isLoweredBedVisual(world, abovePos, aboveState);
        if (!loweredOwner) {
            // Ordinary solid full blocks have an unambiguous owner signature:
            // a lowered full-cube outline directly above the slab hit. Keep
            // crafting tables out of this pass because they remain a no-go.
            net.minecraft.block.Block block = aboveState.getBlock();
            loweredOwner = aboveState.isSolidBlock(world, abovePos)
                    && !(block instanceof net.minecraft.block.BlockEntityProvider)
                    && !(block instanceof net.minecraft.block.CraftingTableBlock)
                    && SlabSupport.getYOffset(world, abovePos, aboveState) == -0.5;
        }
        if (!loweredOwner) {
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

    private boolean slabbed$isSlabPlacementIntent() {
        if (client.player == null) {
            return false;
        }
        ItemStack stack = client.player.getMainHandStack();
        return stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof SlabBlock;
    }

    private BlockHitResult slabbed$retargetLoweredSideSlab(float tickProgress, HitResult currentHit) {
        ClientWorld world = client.world;
        Entity cam = client.getCameraEntity();
        if (world == null || cam == null) {
            return null;
        }

        Vec3d eye = cam.getCameraPosVec(tickProgress);
        Vec3d dir = cam.getRotationVec(tickProgress);
        double reach = 6.0;
        Vec3d end = eye.add(dir.multiply(reach));
        double currentDist2 = Double.POSITIVE_INFINITY;
        if (currentHit != null && currentHit.getType() == HitResult.Type.BLOCK) {
            currentDist2 = currentHit.getPos().squaredDistanceTo(eye);
        }
        int steps = Math.max(16, (int) Math.ceil(reach / 0.05));

        BlockHitResult bestHit = null;
        double bestDist2 = currentDist2;
        for (int i = 1; i <= steps; i++) {
            double t = reach * i / steps;
            if (t * t > bestDist2 + 1.0e-6) {
                break;
            }
            Vec3d sample = eye.add(dir.multiply(t));
            BlockPos samplePos = BlockPos.ofFloored(sample);

            BlockHitResult hit = slabbed$raycastLoweredSideSlab(world, cam, eye, end, samplePos);
            if (hit != null) {
                double dist2 = hit.getPos().squaredDistanceTo(eye);
                if (dist2 <= bestDist2 + 1.0e-6) {
                    bestHit = hit;
                    bestDist2 = dist2;
                }
            }

            hit = slabbed$raycastLoweredSideSlab(world, cam, eye, end, samplePos.up());
            if (hit != null) {
                double dist2 = hit.getPos().squaredDistanceTo(eye);
                if (dist2 <= bestDist2 + 1.0e-6) {
                    bestHit = hit;
                    bestDist2 = dist2;
                }
            }
        }

        return bestHit;
    }

    private static BlockHitResult slabbed$raycastLoweredSideSlab(
            ClientWorld world, Entity cam, Vec3d eye, Vec3d end, BlockPos pos
    ) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof SlabBlock)
                || !state.contains(SlabBlock.TYPE)
                || state.get(SlabBlock.TYPE) != SlabType.BOTTOM
                || SlabSupport.getYOffset(world, pos, state) != -0.5) {
            return null;
        }
        VoxelShape shape = state.getOutlineShape(world, pos, ShapeContext.of(cam));
        BlockHitResult hit = shape.raycast(eye, end, pos);
        if (hit == null) {
            return null;
        }
        return hit.getPos().squaredDistanceTo(eye) <= end.squaredDistanceTo(eye) + 1.0e-6 ? hit : null;
    }

    private BlockHitResult slabbed$retargetAnchoredLoweredFullBlock(float tickProgress, HitResult currentHit) {
        ClientWorld world = client.world;
        Entity cam = client.getCameraEntity();
        if (world == null || cam == null) {
            return null;
        }

        Vec3d eye = cam.getCameraPosVec(tickProgress);
        Vec3d dir = cam.getRotationVec(tickProgress);
        double reach = 6.0;
        Vec3d end = eye.add(dir.multiply(reach));
        double currentDist2 = Double.POSITIVE_INFINITY;
        if (currentHit != null && currentHit.getType() == HitResult.Type.BLOCK) {
            currentDist2 = currentHit.getPos().squaredDistanceTo(eye);
        }
        int steps = Math.max(16, (int) Math.ceil(reach / 0.05));

        for (int i = 1; i <= steps; i++) {
            double t = reach * i / steps;
            if (t * t > currentDist2 + 1.0e-6) {
                break;
            }
            Vec3d sample = eye.add(dir.multiply(t));
            BlockPos samplePos = BlockPos.ofFloored(sample);

            BlockPos candidatePos = samplePos;
            BlockState candidateState = world.getBlockState(candidatePos);
            BlockHitResult hit = slabbed$raycastAnchoredLoweredFullBlock(world, cam, eye, end, candidatePos, candidateState);
            if (hit != null) {
                return hit;
            }

            candidatePos = samplePos.up();
            candidateState = world.getBlockState(candidatePos);
            hit = slabbed$raycastAnchoredLoweredFullBlock(world, cam, eye, end, candidatePos, candidateState);
            if (hit != null) {
                return hit;
            }
        }

        return null;
    }

    private static BlockHitResult slabbed$raycastAnchoredLoweredFullBlock(
            ClientWorld world, Entity cam, Vec3d eye, Vec3d end, BlockPos pos, BlockState state
    ) {
        if (!slabbed$isAnchoredLoweredFullBlock(world, pos, state)) {
            return null;
        }
        VoxelShape shape = state.getOutlineShape(world, pos, ShapeContext.of(cam));
        BlockHitResult hit = shape.raycast(eye, end, pos);
        if (hit == null) {
            return null;
        }
        return hit.getPos().squaredDistanceTo(eye) <= end.squaredDistanceTo(eye) + 1.0e-6 ? hit : null;
    }

    private static boolean slabbed$isAnchoredLoweredFullBlock(ClientWorld world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        if (!state.isSolidBlock(world, pos)) {
            return false;
        }

        net.minecraft.block.Block block = state.getBlock();
        if (block instanceof BlockEntityProvider || block instanceof CraftingTableBlock) {
            return false;
        }

        return SlabAnchorAttachment.isAnchored(world, pos)
                && SlabSupport.getYOffset(world, pos, state) == -0.5;
    }
}
