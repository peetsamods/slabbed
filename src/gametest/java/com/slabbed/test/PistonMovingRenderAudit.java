package com.slabbed.test;

import com.slabbed.client.ClientDy;
import com.slabbed.client.runtime.PistonMovingRenderScope;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
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

    /** Exercises Fabric's real non-vanilla model handoff while retaining stack and final-vertex evidence. */
    public static ModelSnapshot renderModelAndSnapshot(
            MinecraftClient client,
            BlockPos pos,
            BlockState state
    ) {
        BakedModel model = client.getBlockRenderManager().getModel(state);
        BlockModelRenderer renderer = client.getBlockRenderManager().getModelRenderer();
        VertexConsumer delegate = client.getBufferBuilders().getEntityVertexConsumers()
                .getBuffer(RenderLayers.getBlockLayer(state));
        ArrayList<Vertex> vertices = new ArrayList<>();
        VertexConsumer capture = new CapturingVertexConsumer(delegate, vertices);
        long seed = state.getRenderingSeed(pos);
        double selectedDyBefore = ClientDy.dyFor(client.world, pos, state);
        boolean suppressedBefore = PistonMovingRenderScope.suppressNestedDy();

        MatrixStack matrices = new MatrixStack();
        MatrixStack.Entry initialEntry = matrices.peek();
        renderer.render(client.world, model, state, pos, matrices, capture, false,
                Random.create(), seed, OverlayTexture.DEFAULT_UV);
        boolean renderBalanced = matrices.peek() == initialEntry;
        double selectedDyAfter = ClientDy.dyFor(client.world, pos, state);
        boolean suppressedAfter = PistonMovingRenderScope.suppressNestedDy();

        MatrixStack smoothMatrices = new MatrixStack();
        MatrixStack.Entry smoothInitial = smoothMatrices.peek();
        ArrayList<Vertex> smoothVertices = new ArrayList<>();
        renderer.renderSmooth(client.world, model, state, pos, smoothMatrices,
                new CapturingVertexConsumer(delegate, smoothVertices), false,
                Random.create(), seed, OverlayTexture.DEFAULT_UV);
        boolean smoothBalanced = smoothMatrices.peek() == smoothInitial;

        MatrixStack flatMatrices = new MatrixStack();
        MatrixStack.Entry flatInitial = flatMatrices.peek();
        ArrayList<Vertex> flatVertices = new ArrayList<>();
        renderer.renderFlat(client.world, model, state, pos, flatMatrices,
                new CapturingVertexConsumer(delegate, flatVertices), false,
                Random.create(), seed, OverlayTexture.DEFAULT_UV);
        boolean flatBalanced = flatMatrices.peek() == flatInitial;

        MatrixStack earlyMatrices = new MatrixStack();
        MatrixStack.Entry earlyInitial = earlyMatrices.peek();
        ArrayList<Vertex> earlyVertices = new ArrayList<>();
        VertexConsumer earlyCapture = new CapturingVertexConsumer(delegate, earlyVertices);
        invokeOuterHead(renderer, client.world, model, state, pos, earlyMatrices, earlyCapture,
                false, Random.create(), seed, OverlayTexture.DEFAULT_UV);
        boolean earlyHookBalanced = earlyMatrices.peek() == earlyInitial;
        renderer.render(client.world, model, state, pos, earlyMatrices, earlyCapture, false,
                Random.create(), seed, OverlayTexture.DEFAULT_UV);
        boolean earlyRenderBalanced = earlyMatrices.peek() == earlyInitial;

        boolean fabricModel = model instanceof FabricBakedModel;
        boolean vanillaAdapter = fabricModel && ((FabricBakedModel) model).isVanillaAdapter();
        return ModelSnapshot.of(selectedDyBefore, selectedDyAfter, suppressedBefore, suppressedAfter,
                fabricModel, vanillaAdapter, renderBalanced, smoothBalanced, flatBalanced,
                earlyHookBalanced, earlyRenderBalanced,
                vertices, smoothVertices, flatVertices, earlyVertices);
    }

    public static void assertDirectModelDepthDelta(
            ModelSnapshot flat,
            ModelSnapshot deep,
            double expectedDy
    ) {
        if (!flat.fabricModel() || !deep.fabricModel()
                || flat.vanillaAdapter() || deep.vanillaAdapter()) {
            throw new AssertionError("direct model did not use Fabric's non-vanilla adapter route");
        }
        if (!near(flat.selectedDyBefore(), 0.0d) || !near(flat.selectedDyAfter(), 0.0d)
                || !near(deep.selectedDyBefore(), expectedDy)
                || !near(deep.selectedDyAfter(), expectedDy)) {
            throw new AssertionError("direct model selected dy changed or did not match 0/" + expectedDy);
        }
        if (flat.suppressedBefore() || flat.suppressedAfter()
                || deep.suppressedBefore() || deep.suppressedAfter()) {
            throw new AssertionError("direct model unexpectedly ran inside piston suppression scope");
        }
        if (!flat.renderBalanced() || !deep.renderBalanced()
                || !flat.smoothBalanced() || !deep.smoothBalanced()
                || !flat.flatBalanced() || !deep.flatBalanced()) {
            throw new AssertionError("direct model stack balance was render="
                    + flat.renderBalanced() + "/" + deep.renderBalanced()
                    + " smooth=" + flat.smoothBalanced() + "/" + deep.smoothBalanced()
                    + " flat=" + flat.flatBalanced() + "/" + deep.flatBalanced());
        }
        if (!flat.earlyHookBalanced() || !deep.earlyHookBalanced()
                || !flat.earlyRenderBalanced() || !deep.earlyRenderBalanced()) {
            throw new AssertionError("test-controlled early hook stack balance was hook="
                    + flat.earlyHookBalanced() + "/" + deep.earlyHookBalanced()
                    + " render=" + flat.earlyRenderBalanced() + "/" + deep.earlyRenderBalanced());
        }
        assertVertexDelta(flat.vertices(), deep.vertices(), expectedDy, "direct model");
        assertVertexDelta(flat.smoothVertices(), deep.smoothVertices(), expectedDy, "direct smooth model");
        assertVertexDelta(flat.flatVertices(), deep.flatVertices(), expectedDy, "direct flat model");
        assertVertexDelta(flat.earlyVertices(), deep.earlyVertices(), expectedDy,
                "test-controlled early hook model");
    }

    /** Invokes the transformed outer HEAD callback without changing renderer or global state. */
    private static void invokeOuterHead(
            BlockModelRenderer renderer,
            BlockRenderView world,
            BakedModel model,
            BlockState state,
            BlockPos pos,
            MatrixStack matrices,
            VertexConsumer vertexConsumer,
            boolean cull,
            Random random,
            long seed,
            int overlay
    ) {
        Class<?>[] signature = {
                BlockRenderView.class, BakedModel.class, BlockState.class, BlockPos.class,
                MatrixStack.class, VertexConsumer.class, boolean.class, Random.class,
                long.class, int.class, CallbackInfo.class
        };
        List<Method> matches = Arrays.stream(renderer.getClass().getDeclaredMethods())
                .filter(method -> method.getName().endsWith("$slabbed$pushDy"))
                .filter(method -> method.getReturnType() == void.class)
                .filter(method -> Arrays.equals(method.getParameterTypes(), signature))
                .toList();
        if (matches.size() != 1) {
            throw new AssertionError("outer HEAD callback fixture expected one exact match but found "
                    + matches.size());
        }
        Method method = matches.getFirst();
        if (!method.trySetAccessible()) {
            throw new AssertionError("outer HEAD callback fixture was not accessible");
        }
        try {
            method.invoke(renderer, world, model, state, pos, matrices, vertexConsumer, cull,
                    random, seed, overlay, new CallbackInfo("render", false));
        } catch (IllegalAccessException failure) {
            throw new AssertionError("outer HEAD callback fixture access failed", failure);
        } catch (InvocationTargetException failure) {
            throw new AssertionError("outer HEAD callback fixture invocation failed", failure.getCause());
        }
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

    private static void assertVertexDelta(
            List<Vertex> flat,
            List<Vertex> deep,
            double expectedDy,
            String label
    ) {
        if (flat.isEmpty() || deep.isEmpty() || flat.size() != deep.size()) {
            throw new AssertionError(label + " vertex counts were flat=" + flat.size()
                    + " deep=" + deep.size());
        }
        for (int index = 0; index < flat.size(); index++) {
            Vertex before = flat.get(index);
            Vertex after = deep.get(index);
            if (!near(after.x() - before.x(), 0.0d)
                    || !near(after.y() - before.y(), expectedDy)
                    || !near(after.z() - before.z(), 0.0d)) {
                throw new AssertionError(label + " vertex " + index + " delta=("
                        + (after.x() - before.x()) + "," + (after.y() - before.y()) + ","
                        + (after.z() - before.z()) + ") wanted=(0," + expectedDy + ",0)");
            }
        }
    }

    public record Vertex(float x, float y, float z) {
    }

    public record ModelSnapshot(
            double selectedDyBefore,
            double selectedDyAfter,
            boolean suppressedBefore,
            boolean suppressedAfter,
            boolean fabricModel,
            boolean vanillaAdapter,
            boolean renderBalanced,
            boolean smoothBalanced,
            boolean flatBalanced,
            boolean earlyHookBalanced,
            boolean earlyRenderBalanced,
            List<Vertex> vertices,
            List<Vertex> smoothVertices,
            List<Vertex> flatVertices,
            List<Vertex> earlyVertices,
            int vertexCount,
            double minY,
            double maxY,
            int smoothVertexCount,
            double smoothMinY,
            double smoothMaxY,
            int flatVertexCount,
            double flatMinY,
            double flatMaxY,
            int earlyVertexCount,
            double earlyMinY,
            double earlyMaxY
    ) {
        private static ModelSnapshot of(
                double selectedDyBefore,
                double selectedDyAfter,
                boolean suppressedBefore,
                boolean suppressedAfter,
                boolean fabricModel,
                boolean vanillaAdapter,
                boolean renderBalanced,
                boolean smoothBalanced,
                boolean flatBalanced,
                boolean earlyHookBalanced,
                boolean earlyRenderBalanced,
                List<Vertex> observed,
                List<Vertex> observedSmooth,
                List<Vertex> observedFlat,
                List<Vertex> observedEarly
        ) {
            List<Vertex> vertices = List.copyOf(observed);
            List<Vertex> smoothVertices = List.copyOf(observedSmooth);
            List<Vertex> flatVertices = List.copyOf(observedFlat);
            List<Vertex> earlyVertices = List.copyOf(observedEarly);
            return new ModelSnapshot(selectedDyBefore, selectedDyAfter, suppressedBefore, suppressedAfter,
                    fabricModel, vanillaAdapter, renderBalanced, smoothBalanced, flatBalanced,
                    earlyHookBalanced, earlyRenderBalanced,
                    vertices, smoothVertices, flatVertices, earlyVertices,
                    vertices.size(), minY(vertices), maxY(vertices),
                    smoothVertices.size(), minY(smoothVertices), maxY(smoothVertices),
                    flatVertices.size(), minY(flatVertices), maxY(flatVertices),
                    earlyVertices.size(), minY(earlyVertices), maxY(earlyVertices));
        }

        private static double minY(List<Vertex> vertices) {
            return vertices.stream().mapToDouble(Vertex::y).min().orElse(Double.NaN);
        }

        private static double maxY(List<Vertex> vertices) {
            return vertices.stream().mapToDouble(Vertex::y).max().orElse(Double.NaN);
        }
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
