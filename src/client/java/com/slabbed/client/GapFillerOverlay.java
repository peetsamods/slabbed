package com.slabbed.client;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;

public final class GapFillerOverlay {
    private static final double INSET = 0.06;
    private static final float R = 0.6f, G = 0.6f, B = 0.6f, A = 0.4f;
    private static final float LINE_WIDTH = 1.0f;
    private static final int RADIUS = 6;
    private static final int MAX_CHECKS = (RADIUS * 2 + 1) * (RADIUS * 2 + 1) * (RADIUS * 2 + 1);

    private GapFillerOverlay() {}

    public static void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(GapFillerOverlay::onRender);
    }

    private static void onRender(WorldRenderContext context) {
        if (!SlabbedClientFlags.GAP_FILL) return;
        if (ScreenshotCaptureContext.captureActive() && ScreenshotCaptureContext.currentProfile() == CaptureProfile.CLEAN) return;

        MinecraftClient client = MinecraftClient.getInstance();
        World world = client.world;
        if (world == null) return;

        Vec3d camPos = client.gameRenderer.getCamera().getPos();
        int camX = (int) Math.floor(camPos.x);
        int camY = (int) Math.floor(camPos.y);
        int camZ = (int) Math.floor(camPos.z);

        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) return;

        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
        Matrix4f mat = matrices.peek().getPositionMatrix();

        int checked = 0;
        for (int dx = -RADIUS; dx <= RADIUS && checked < MAX_CHECKS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS && checked < MAX_CHECKS; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS && checked < MAX_CHECKS; dz++) {
                    checked++;
                    BlockPos pos = new BlockPos(camX + dx, camY + dy, camZ + dz);
                    BlockState state = world.getBlockState(pos);
                    if (!state.isAir()) continue;

                    BlockPos below = pos.down();
                    BlockPos above = pos.up();
                    BlockState belowState = world.getBlockState(below);
                    BlockState aboveState = world.getBlockState(above);

                    double dyBelow = SlabSupport.getYOffset(world, below, belowState);
                    double dyAbove = SlabSupport.getYOffset(world, above, aboveState);
                    if (dyBelow == 0.0 && dyAbove == 0.0) continue;

                    double ox = pos.getX() - camPos.x;
                    double oy = pos.getY() - camPos.y;
                    double oz = pos.getZ() - camPos.z;

                    drawBox(lines, mat,
                            ox + INSET, oy + INSET, oz + INSET,
                            ox + 1 - INSET, oy + 1 - INSET, oz + 1 - INSET);
                }
            }
        }
    }

    private static void drawBox(VertexConsumer lines, Matrix4f mat,
                                 double x0, double y0, double z0,
                                 double x1, double y1, double z1) {
        float fx0 = (float) x0, fy0 = (float) y0, fz0 = (float) z0;
        float fx1 = (float) x1, fy1 = (float) y1, fz1 = (float) z1;

        line(lines, mat, fx0, fy0, fz0, fx1, fy0, fz0);
        line(lines, mat, fx1, fy0, fz0, fx1, fy0, fz1);
        line(lines, mat, fx1, fy0, fz1, fx0, fy0, fz1);
        line(lines, mat, fx0, fy0, fz1, fx0, fy0, fz0);

        line(lines, mat, fx0, fy1, fz0, fx1, fy1, fz0);
        line(lines, mat, fx1, fy1, fz0, fx1, fy1, fz1);
        line(lines, mat, fx1, fy1, fz1, fx0, fy1, fz1);
        line(lines, mat, fx0, fy1, fz1, fx0, fy1, fz0);

        line(lines, mat, fx0, fy0, fz0, fx0, fy1, fz0);
        line(lines, mat, fx1, fy0, fz0, fx1, fy1, fz0);
        line(lines, mat, fx1, fy0, fz1, fx1, fy1, fz1);
        line(lines, mat, fx0, fy0, fz1, fx0, fy1, fz1);
    }

    private static void line(VertexConsumer lines, Matrix4f mat,
                              float x0, float y0, float z0,
                              float x1, float y1, float z1) {
        float nx = x1 - x0, ny = y1 - y0, nz = z1 - z0;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len == 0) return;
        nx /= len; ny /= len; nz /= len;
        lines.vertex(mat, x0, y0, z0).color(R, G, B, A).normal(nx, ny, nz);
        lines.vertex(mat, x1, y1, z1).color(R, G, B, A).normal(nx, ny, nz);
    }
}
