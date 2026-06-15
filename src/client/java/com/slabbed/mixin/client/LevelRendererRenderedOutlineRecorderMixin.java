package com.slabbed.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.slabbed.util.LiveCursorIntentRecorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashMap;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererRenderedOutlineRecorderMixin {
    @Inject(method = "renderHitOutline", at = @At("HEAD"))
    private void slabbed$recordRenderedOutline(
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            double cameraX,
            double cameraY,
            double cameraZ,
            BlockOutlineRenderState renderState,
            int color,
            float lineWidth,
            CallbackInfo ci
    ) {
        if (!LiveCursorIntentRecorder.enabled() || renderState == null) {
            return;
        }
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        BlockPos pos = renderState.pos();
        row.put("renderedOutlinePos", pos.toShortString());
        row.put("renderedOutlineCamera", slabbed$formatVec(cameraX, cameraY, cameraZ));
        row.put("renderedOutlineTranslucent", Boolean.toString(renderState.isTranslucent()));
        row.put("renderedOutlineHighContrast", Boolean.toString(renderState.highContrast()));
        row.put("renderedOutlineColor", Integer.toString(color));
        row.put("renderedOutlineLineWidth", slabbed$formatDouble(lineWidth));
        row.put("renderedOutlineBounds", slabbed$shapeBounds(renderState.shape()));
        row.put("renderedOutlineWorldBounds", slabbed$worldBounds(pos, renderState.shape()));
        row.put("renderedOutlineCameraRelativeBounds",
                slabbed$cameraRelativeBounds(pos, renderState.shape(), cameraX, cameraY, cameraZ));
        row.put("renderedOutlineCollisionBounds", slabbed$shapeBounds(renderState.collisionShape()));
        row.put("renderedOutlineOcclusionBounds", slabbed$shapeBounds(renderState.occlusionShape()));
        row.put("renderedOutlineInteractionBounds", slabbed$shapeBounds(renderState.interactionShape()));

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel world = minecraft.level;
        if (world == null) {
            row.put("renderedOutlineState", "unknown(no-world)");
        } else {
            BlockState state = world.getBlockState(pos);
            row.put("renderedOutlineState", state.toString());
        }

        HitResult hit = minecraft.hitResult;
        row.put("renderedOutlineHitType", hit == null ? "null" : hit.getType().toString());
        row.put("renderedOutlineHitVec", hit == null ? "none" : slabbed$formatVec(hit.getLocation()));
        if (hit instanceof BlockHitResult blockHit) {
            row.put("renderedOutlineHitPos", blockHit.getBlockPos().toShortString());
            row.put("renderedOutlineHitFace", blockHit.getDirection().toString());
        } else {
            row.put("renderedOutlineHitPos", "none");
            row.put("renderedOutlineHitFace", "none");
        }

        LiveCursorIntentRecorder.recordRenderedOutline(row);
    }

    private static String slabbed$shapeBounds(VoxelShape shape) {
        if (shape == null) {
            return "null";
        }
        if (shape.isEmpty()) {
            return "empty";
        }
        return slabbed$formatBox(shape.bounds());
    }

    private static String slabbed$worldBounds(BlockPos pos, VoxelShape shape) {
        if (shape == null) {
            return "null";
        }
        if (shape.isEmpty()) {
            return "empty";
        }
        AABB bounds = shape.bounds();
        return slabbed$formatBox(new AABB(
                pos.getX() + bounds.minX,
                pos.getY() + bounds.minY,
                pos.getZ() + bounds.minZ,
                pos.getX() + bounds.maxX,
                pos.getY() + bounds.maxY,
                pos.getZ() + bounds.maxZ));
    }

    private static String slabbed$cameraRelativeBounds(
            BlockPos pos,
            VoxelShape shape,
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
        if (shape == null) {
            return "null";
        }
        if (shape.isEmpty()) {
            return "empty";
        }
        AABB bounds = shape.bounds();
        return slabbed$formatBox(new AABB(
                pos.getX() + bounds.minX - cameraX,
                pos.getY() + bounds.minY - cameraY,
                pos.getZ() + bounds.minZ - cameraZ,
                pos.getX() + bounds.maxX - cameraX,
                pos.getY() + bounds.maxY - cameraY,
                pos.getZ() + bounds.maxZ - cameraZ));
    }

    private static String slabbed$formatBox(AABB box) {
        return "min=("
                + slabbed$formatDouble(box.minX) + ','
                + slabbed$formatDouble(box.minY) + ','
                + slabbed$formatDouble(box.minZ) + "),max=("
                + slabbed$formatDouble(box.maxX) + ','
                + slabbed$formatDouble(box.maxY) + ','
                + slabbed$formatDouble(box.maxZ) + ')';
    }

    private static String slabbed$formatVec(double x, double y, double z) {
        return slabbed$formatDouble(x) + ',' + slabbed$formatDouble(y) + ',' + slabbed$formatDouble(z);
    }

    private static String slabbed$formatVec(net.minecraft.world.phys.Vec3 vec) {
        return slabbed$formatVec(vec.x, vec.y, vec.z);
    }

    private static String slabbed$formatDouble(double value) {
        return String.format("%.6f", value);
    }
}
