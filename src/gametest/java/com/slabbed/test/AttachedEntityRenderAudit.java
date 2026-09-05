package com.slabbed.test;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/** Test-only capture of the final model matrix consumed by an attached-entity renderer. */
public final class AttachedEntityRenderAudit {
    private static final Map<Integer, MutableSample> ACTIVE = new HashMap<>();

    private AttachedEntityRenderAudit() {
    }

    public static synchronized void reset(int... entityIds) {
        ACTIVE.clear();
        for (int entityId : entityIds) {
            ACTIVE.put(entityId, new MutableSample());
        }
    }

    public static synchronized void record(Entity entity, MatrixStack matrices) {
        MutableSample sample = ACTIVE.get(entity.getId());
        if (sample == null) {
            return;
        }
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        sample.frames++;
        sample.x = matrix.m30();
        sample.y = matrix.m31();
        sample.z = matrix.m32();
    }

    public static synchronized Sample snapshot(int entityId) {
        MutableSample sample = ACTIVE.get(entityId);
        return sample == null
                ? new Sample(false, 0, Double.NaN, Double.NaN, Double.NaN)
                : new Sample(sample.frames > 0, sample.frames, sample.x, sample.y, sample.z);
    }

    public record Sample(boolean seen, int frames, double matrixX, double matrixY, double matrixZ) {
    }

    private static final class MutableSample {
        private int frames;
        private double x = Double.NaN;
        private double y = Double.NaN;
        private double z = Double.NaN;
    }
}
