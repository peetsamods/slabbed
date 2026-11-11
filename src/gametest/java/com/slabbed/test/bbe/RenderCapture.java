package com.slabbed.test.bbe;

import net.minecraft.core.BlockPos;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Test-only observations at the actual Sodium buffer and vanilla submit boundaries. */
public final class RenderCapture {
    public static final Map<BlockPos, Double> mesh = new ConcurrentHashMap<>();
    public static final Map<BlockPos, Double> vanilla = new ConcurrentHashMap<>();
    public static final Map<BlockPos, Integer> vanillaCount = new ConcurrentHashMap<>();
    public static final Map<BlockPos, Integer> meshCount = new ConcurrentHashMap<>();
    public static final Set<BlockPos> watch = ConcurrentHashMap.newKeySet();

    public static void clearSamples() {
        mesh.clear();
        vanilla.clear();
        meshCount.clear();
        vanillaCount.clear();
    }

    private static Object field(Object value, String name) {
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(value);
            } catch (NoSuchFieldException ignored) {
                // The source cell is inherited from Sodium's shared block render context.
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("Missing observed field: " + name);
    }

    public static void mesh(Object renderer, Object quad, Vector3f origin) {
        BlockPos pos = (BlockPos) field(renderer, "pos");
        if (!watch.contains(pos)) return;
        try {
            var vertexY = quad.getClass().getMethod("getY", int.class);
            double minimum = Double.POSITIVE_INFINITY;
            for (int vertex = 0; vertex < 4; vertex++)
                minimum = Math.min(minimum, ((Number) vertexY.invoke(quad, vertex)).doubleValue());
            // Sodium adds this origin immediately after the capture, before encoding section vertices.
            double localMinimum = minimum + origin.y - (pos.getY() & 15);
            mesh.merge(pos.immutable(), localMinimum, Math::min);
            meshCount.merge(pos.immutable(), 1, Integer::sum);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
