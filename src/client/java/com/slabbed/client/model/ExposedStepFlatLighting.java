package com.slabbed.client.model;

import com.slabbed.compat.MinecraftVersions;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadAtlas;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.DoubleSupplier;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Flat-lit exposed steps sample their adjacent air, not the opaque cell above it. */
public final class ExposedStepFlatLighting {
    private static final float EPSILON = 1.0e-6f;
    private static final MethodHandle DIFFUSE_SHADE = resolveDiffuseShade();

    private ExposedStepFlatLighting() {
    }

    public static void apply(BlockAndTintGetter view, BlockPos pos, BlockState state,
                             float dy, Direction face, MutableQuadView quad, DoubleSupplier neighborModelDy) {
        if (Minecraft.getInstance().options.ambientOcclusion().get()
                || !Float.isFinite(dy) || Math.abs(dy + 0.5f) > EPSILON
                || face == null || face.getAxis() == Direction.Axis.Y
                || !state.isSolidRender() || state.getLightEmission() != 0
                || !Block.isShapeFullBlock(state.getOcclusionShape())
                || !ordinaryFace(quad, face)) {
            return;
        }
        try {
            double neighborDy = neighborModelDy.getAsDouble();
            if (!Double.isFinite(neighborDy) || Math.abs(neighborDy) > EPSILON) return;
            BlockPos neighborPos = pos.relative(face);
            BlockState neighbor = view.getBlockState(neighborPos);
            // The upper half is hidden by this full opaque neighbor. Only the lower half is drawn.
            if (!neighbor.isSolidRender() || !Block.isShapeFullBlock(neighbor.getOcclusionShape())
                    || !view.getBlockState(neighborPos.below()).isAir()) {
                return;
            }
            int light = LightCoordsUtil.getLightCoords(view, neighborPos.below());
            // Zero is meaningful. Do not substitute full brightness or cache a previous light value.
            for (int vertex = 0; vertex < 4; vertex++) {
                quad.lightmap(vertex, light);
            }
        } catch (IndexOutOfBoundsException outsideRenderRegion) {
            // A section-border view may not include the physical light sample yet.
        }
    }

    private static boolean ordinaryFace(MutableQuadView quad, Direction face) {
        if (quad.emissive() || quad.ambientOcclusion() != TriState.DEFAULT
                || quad.atlas() != QuadAtlas.BLOCK || quad.chunkLayer() != ChunkSectionLayer.SOLID
                || quad.tintIndex() != -1 || quad.tag() != 0 || !defaultDiffuseShade(quad)
                || (quad.shadeMode() != ShadeMode.VANILLA && quad.shadeMode() != ShadeMode.ENHANCED)
                || quad.nominalFace() != face || quad.lightFace() != face) {
            return false;
        }
        int corners = 0;
        int firstCorner = -1;
        int previousCorner = -1;
        float plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0f : 0.0f;
        for (int vertex = 0; vertex < 4; vertex++) {
            if (quad.lightmap(vertex) != 0 || quad.color(vertex) != -1 || !cardinalNormal(quad, vertex, face)) {
                return false;
            }
            float perpendicular = face.getAxis() == Direction.Axis.X ? quad.x(vertex) : quad.z(vertex);
            float horizontal = face.getAxis() == Direction.Axis.X ? quad.z(vertex) : quad.x(vertex);
            float y = quad.y(vertex);
            if (!Float.isFinite(perpendicular) || Math.abs(perpendicular - plane) > EPSILON
                    || !Float.isFinite(horizontal) || !Float.isFinite(y)) {
                return false;
            }
            int xBit = near(horizontal, 0.0f) ? 0 : near(horizontal, 1.0f) ? 1 : -1;
            int yBit = near(y, -0.5f) ? 0 : near(y, 0.5f) ? 1 : -1;
            if (xBit < 0 || yBit < 0) return false;
            int corner = xBit + yBit * 2;
            if (previousCorner >= 0 && !edge(previousCorner, corner)) return false;
            if (firstCorner < 0) firstCorner = corner;
            previousCorner = corner;
            corners |= 1 << corner;
        }
        return corners == 15 && edge(previousCorner, firstCorner);
    }

    private static boolean cardinalNormal(QuadView quad, int vertex, Direction face) {
        if (!quad.hasNormal(vertex)) return true;
        float x = quad.normalX(vertex), y = quad.normalY(vertex), z = quad.normalZ(vertex);
        return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z)
                && near(x, face.getStepX()) && near(y, face.getStepY()) && near(z, face.getStepZ());
    }

    private static MethodHandle resolveDiffuseShade() {
        try {
            return MinecraftVersions.AT_LEAST_26_3
                    ? MethodHandles.publicLookup().findVirtual(QuadView.class, "shadeDirectionOverride", MethodType.methodType(Direction.class))
                    : MethodHandles.publicLookup().findVirtual(QuadView.class, "diffuseShade", MethodType.methodType(boolean.class));
        } catch (ReflectiveOperationException unsupportedApi) {
            return null;
        }
    }

    private static boolean defaultDiffuseShade(QuadView quad) {
        if (DIFFUSE_SHADE == null) return false;
        try {
            return MinecraftVersions.AT_LEAST_26_3
                    ? (Direction) DIFFUSE_SHADE.invokeExact(quad) == null
                    : (boolean) DIFFUSE_SHADE.invokeExact(quad);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable unsupportedApi) {
            return false;
        }
    }

    private static boolean edge(int a, int b) {
        int changed = a ^ b;
        return changed == 1 || changed == 2;
    }

    private static boolean near(float actual, float expected) {
        return Math.abs(actual - expected) <= EPSILON;
    }
}
