package com.slabbed.test;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/** Test-only final-vertex oracle for one real piston block-entity render dispatch. */
public final class PistonMovingRenderAudit {
    private static final double EPSILON = 1.0e-5d;

    private PistonMovingRenderAudit() {
    }

    public static Snapshot renderAndSnapshot(
            BlockEntityRenderDispatcher dispatcher,
            PistonBlockEntity blockEntity,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers
    ) {
        ArrayList<Vertex> vertices = new ArrayList<>();
        VertexConsumerProvider capturingProvider = layer ->
                new CapturingVertexConsumer(vertexConsumers.getBuffer(layer), vertices);
        dispatcher.render(blockEntity, tickDelta, matrices, capturingProvider);
        return Snapshot.of(
                blockEntity.getPos().toImmutable(),
                blockEntity.getPushedBlock(),
                blockEntity.getFacing(),
                blockEntity.isSource(),
                vertices);
    }

    /** Requires matching real render dispatches to differ by one stored Y translation only. */
    public static void assertStoredDepthDelta(Snapshot flat, Snapshot deep, double expectedDy) {
        if (flat == null || deep == null) {
            throw new AssertionError("moving-piston vertex snapshot is missing");
        }
        if (flat.vertexCount() == 0 || deep.vertexCount() == 0) {
            throw new AssertionError(
                    "moving-piston emitted no vertices: flat=" + flat.vertexCount()
                            + " deep=" + deep.vertexCount());
        }
        if (flat.vertexCount() != deep.vertexCount()) {
            throw new AssertionError(
                    "moving-piston vertex count changed with depth: flat=" + flat.vertexCount()
                            + " deep=" + deep.vertexCount());
        }
        if (flat.source() != deep.source()
                || flat.facing() != deep.facing()
                || flat.pushedState().getBlock() != deep.pushedState().getBlock()) {
            throw new AssertionError(
                    "moving-piston fixture identity changed: flat=" + flat.identity()
                            + " deep=" + deep.identity());
        }
        for (int index = 0; index < flat.vertexCount(); index++) {
            Vertex before = flat.vertices().get(index);
            Vertex after = deep.vertices().get(index);
            if (!near(after.x() - before.x(), 0.0d)
                    || !near(after.y() - before.y(), expectedDy)
                    || !near(after.z() - before.z(), 0.0d)) {
                throw new AssertionError(
                        "moving-piston vertex " + index + " delta=("
                                + (after.x() - before.x()) + ","
                                + (after.y() - before.y()) + ","
                                + (after.z() - before.z()) + ") wanted=(0,"
                                + expectedDy + ",0)");
            }
        }
        if (!near(deep.minX() - flat.minX(), 0.0d)
                || !near(deep.maxX() - flat.maxX(), 0.0d)
                || !near(deep.minY() - flat.minY(), expectedDy)
                || !near(deep.maxY() - flat.maxY(), expectedDy)
                || !near(deep.minZ() - flat.minZ(), 0.0d)
                || !near(deep.maxZ() - flat.maxZ(), 0.0d)) {
            throw new AssertionError(
                    "moving-piston bounds delta=" + deep.boundsDeltaFrom(flat)
                            + " wanted=(0," + expectedDy + ",0)");
        }
    }

    private static boolean near(double value, double expected) {
        return Math.abs(value - expected) <= EPSILON;
    }

    public record Vertex(float x, float y, float z) {
    }

    public record Snapshot(
            BlockPos movingPos,
            BlockState pushedState,
            Direction facing,
            boolean source,
            List<Vertex> vertices,
            int vertexCount,
            double minX,
            double maxX,
            double minY,
            double maxY,
            double minZ,
            double maxZ
    ) {
        private static Snapshot of(
                BlockPos movingPos,
                BlockState pushedState,
                Direction facing,
                boolean source,
                List<Vertex> observed
        ) {
            List<Vertex> vertices = List.copyOf(observed);
            double minX = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (Vertex vertex : vertices) {
                minX = Math.min(minX, vertex.x());
                maxX = Math.max(maxX, vertex.x());
                minY = Math.min(minY, vertex.y());
                maxY = Math.max(maxY, vertex.y());
                minZ = Math.min(minZ, vertex.z());
                maxZ = Math.max(maxZ, vertex.z());
            }
            return new Snapshot(
                    movingPos,
                    pushedState,
                    facing,
                    source,
                    vertices,
                    vertices.size(),
                    minX,
                    maxX,
                    minY,
                    maxY,
                    minZ,
                    maxZ);
        }

        private String identity() {
            return pushedState.getBlock() + "/" + facing + "/source=" + source;
        }

        private String boundsDeltaFrom(Snapshot flat) {
            return "min=(" + (minX - flat.minX) + "," + (minY - flat.minY) + ","
                    + (minZ - flat.minZ) + "),max=(" + (maxX - flat.maxX) + ","
                    + (maxY - flat.maxY) + "," + (maxZ - flat.maxZ) + ")";
        }
    }

    /** Forwards every primitive unchanged while recording already-transformed positions. */
    private static final class CapturingVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final List<Vertex> vertices;

        private CapturingVertexConsumer(VertexConsumer delegate, List<Vertex> vertices) {
            this.delegate = delegate;
            this.vertices = vertices;
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            vertices.add(new Vertex(x, y, z));
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            delegate.color(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            delegate.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            delegate.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            delegate.light(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }
    }
}
