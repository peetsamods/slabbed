package com.slabbed.devtools.client;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.client.model.OffsetBlockStateModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.ColorResolver;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.ArrayList;

/** Native client proof of per-context height reuse, quad bounds, and seam-face conservation. */
public final class MeshDyReuseProof {
    private static final boolean ENABLED = Boolean.getBoolean("slabbed.meshDyReuseProof");
    private static boolean ran;
    private static final BlockPos SUBJECT = new BlockPos(8, 64, 8);

    private MeshDyReuseProof() { }

    public static void tick(Minecraft client) {
        if (!ENABLED || ran || client.getOverlay() != null || client.level != null || client.player != null) return;
        if (!(client.getModelManager().getBlockModelShaper().getBlockModel(Blocks.STONE.defaultBlockState()) instanceof OffsetBlockStateModel)) return;
        ran = true;
        try {
            if (Boolean.getBoolean("slabbed.meshDyBenchmark")) {
                benchmark(client);
                return;
            }
            run(client);
            Slabbed.LOGGER.info("MESH_DY_REUSE PASS rows=18 model=stone sides=6 repeatedPasses=2");
        } catch (Throwable failure) {
            Slabbed.LOGGER.error("MESH_DY_REUSE FAIL", failure);
        } finally {
            client.stop();
        }
    }

    private static void run(Minecraft client) {
        var state = Blocks.STONE.defaultBlockState();
        var model = client.getModelManager().getBlockModelShaper().getBlockModel(state);
        require(model instanceof OffsetBlockStateModel, "actual stone model must be wrapped");
        var originalVertices = new java.util.IdentityHashMap<int[], int[]>();
        for (Direction face : Direction.values()) {
            for (BakedQuad quad : model.getQuads(state, face, RandomSource.create(42), ModelData.EMPTY, null))
                originalVertices.put(quad.getVertices(), quad.getVertices().clone());
        }
        require(originalVertices.size() == 6, "six original cube faces");
        var previousLookup = SlabPlacementHeightAttachment.installClientRenderHalfStepsLookup(null);
        var previousPresence = SlabPlacementHeightAttachment.installClientRenderChunkFactPresence(key -> true);
        var previousAnchor = SlabAnchorAttachment.clientAnchorLookup;
        var previousAnchorPresence = SlabAnchorAttachment.clientChunkAnchorPresence;
        SlabAnchorAttachment.clientAnchorLookup = pos -> false;
        SlabAnchorAttachment.clientChunkAnchorPresence = key -> false;
        try {
            for (int halfSteps : new int[]{0, -1, -2}) {
                for (int pattern = 0; pattern < 6; pattern++) {
                    final int shape = pattern;
                    int[] reads = {0};
                    SlabPlacementHeightAttachment.installClientRenderHalfStepsLookup(packed -> {
                        if (packed == SUBJECT.asLong()) { reads[0]++; return halfSteps; }
                        BlockPos pos = BlockPos.of(packed);
                        for (Direction face : Direction.Plane.HORIZONTAL) {
                            if (pos.equals(SUBJECT.relative(face))) {
                                if (shape == 5 && face == Direction.SOUTH) return halfSteps - 1;
                                return shape == 0 || (shape == 1 && face.getAxis() == Direction.Axis.X) ? halfSteps : 0;
                            }
                        }
                        return SlabPlacementHeightAttachment.ABSENT_HALF_STEPS;
                    });
                    FakeView view = new FakeView(pattern);
                    double dy = ClientDy.dyFor(view, SUBJECT, state);
                    int oneResolution = reads[0];
                    require(dy == halfSteps * 0.5 && oneResolution > 0, "authored-height premise");
                    var data = model.getModelData(view, SUBJECT, state, ModelData.EMPTY);
                    reads[0] = 0;
                    var quads = new ArrayList<BakedQuad>();
                    var unculled = model.getQuads(state, null, RandomSource.create(42), data, null);
                    quads.addAll(unculled);
                    int expectedSeams = pattern >= 3 && pattern != 5 || pattern == 0 ? 0
                            : pattern == 5 ? (halfSteps == 0 ? 1 : 4) : halfSteps == 0 ? 0 : pattern == 1 ? 2 : 4;
                    require(unculled.size() == expectedSeams, "unculled seam count pattern=" + pattern);
                    for (Direction face : Direction.values()) {
                        var side = model.getQuads(state, face, RandomSource.create(42), data, null);
                        boolean seam = face.getAxis().isHorizontal() && pattern != 0 && pattern != 3 && pattern != 4
                                && (halfSteps != 0 || pattern == 5 && face == Direction.SOUTH)
                                && (pattern != 1 || face.getAxis() == Direction.Axis.Z);
                        require(side.size() == (seam ? 0 : 1), "culled side conservation " + face);
                        quads.addAll(side);
                    }
                    require(quads.size() == 6, "all six cube faces remain present");
                    double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
                    for (BakedQuad quad : quads) {
                        int[] vertices = quad.getVertices();
                        int stride = vertices.length / 4;
                        for (int vertex = 0; vertex < 4; vertex++) {
                            float y = Float.intBitsToFloat(vertices[vertex * stride + 1]);
                            min = Math.min(min, y); max = Math.max(max, y);
                        }
                    }
                    require(Math.abs(min - dy) < 1e-6 && Math.abs(max - dy - 1) < 1e-6, "model bounds");
                    int resolvedReads = reads[0];
                    for (int pass = 0; pass < 2; pass++) {
                        model.getQuads(state, null, RandomSource.create(42), data, null);
                        for (Direction side : Direction.values()) model.getQuads(state, side, RandomSource.create(42), data, null);
                    }
                    require(reads[0] == resolvedReads, "same context must not re-resolve on another pass");
                    Slabbed.LOGGER.info("MESH_DY_REUSE row halfSteps={} pattern={} selfReads={} oneResolution={} seams={} min={} max={}",
                            halfSteps, pattern, resolvedReads, oneResolution, expectedSeams, min, max);
                    require(resolvedReads == oneResolution, "subject height must resolve once: got " + resolvedReads + " expected " + oneResolution);
                }
            }
            originalVertices.forEach((current, original) -> require(java.util.Arrays.equals(current, original), "source quad vertices must remain unchanged"));
        } finally {
            SlabPlacementHeightAttachment.installClientRenderHalfStepsLookup(previousLookup);
            SlabPlacementHeightAttachment.installClientRenderChunkFactPresence(previousPresence);
            SlabAnchorAttachment.clientAnchorLookup = previousAnchor;
            SlabAnchorAttachment.clientChunkAnchorPresence = previousAnchorPresence;
        }
    }

    /** A fixed native model workload; this does not measure whole-world frame rate. */
    private static void benchmark(Minecraft client) {
        var state = Blocks.STONE.defaultBlockState();
        var model = client.getModelManager().getBlockModelShaper().getBlockModel(state);
        var view = new FakeView(6);
        var oldLookup = SlabPlacementHeightAttachment.installClientRenderHalfStepsLookup(
                key -> SlabPlacementHeightAttachment.ABSENT_HALF_STEPS);
        var oldPresence = SlabPlacementHeightAttachment.installClientRenderChunkFactPresence(key -> false);
        var oldAnchor = SlabAnchorAttachment.clientAnchorLookup;
        var oldAnchorPresence = SlabAnchorAttachment.clientChunkAnchorPresence;
        SlabAnchorAttachment.clientAnchorLookup = pos -> false;
        SlabAnchorAttachment.clientChunkAnchorPresence = key -> false;
        try {
            require(ClientDy.dyFor(view, SUBJECT, state) == -0.5, "legacy slab-supported model benchmark premise");
            var cpu = java.lang.management.ManagementFactory.getThreadMXBean();
            var random = RandomSource.create(42);
            long checksum = 0;
            for (int sample = -20; sample < 7; sample++) {
                int blocks = sample < 0 ? 10000 : 20000;
                long startedWall = System.nanoTime();
                long startedCpu = cpu.getCurrentThreadCpuTime();
                for (int i = 0; i < blocks; i++) {
                    var data = model.getModelData(view, SUBJECT, state, ModelData.EMPTY);
                    checksum += model.getQuads(state, null, random, data, null).size();
                    for (Direction side : Direction.values()) checksum += model.getQuads(state, side, random, data, null).size();
                }
                long cpuNanos = cpu.getCurrentThreadCpuTime() - startedCpu;
                long wallNanos = System.nanoTime() - startedWall;
                if (sample >= 0) Slabbed.LOGGER.info("MESH_DY_BENCH sample={} blocks={} cpuNanos={} wallNanos={}", sample, blocks, cpuNanos, wallNanos);
            }
            require(checksum == 2040000, "benchmark must consume six faces per context");
            Slabbed.LOGGER.info("MESH_DY_BENCH DONE checksum={} measuredContexts=140000", checksum);
        } finally {
            SlabPlacementHeightAttachment.installClientRenderHalfStepsLookup(oldLookup);
            SlabPlacementHeightAttachment.installClientRenderChunkFactPresence(oldPresence);
            SlabAnchorAttachment.clientAnchorLookup = oldAnchor;
            SlabAnchorAttachment.clientChunkAnchorPresence = oldAnchorPresence;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record FakeView(int pattern) implements BlockAndTintGetter {
        @Override public BlockState getBlockState(BlockPos pos) {
            if (pattern == 6 && pos.getY() == SUBJECT.getY() - 1) return Blocks.OAK_SLAB.defaultBlockState();
            if (pattern == 6 && pos.getY() > SUBJECT.getY()) return Blocks.AIR.defaultBlockState();
            if (pattern == 4 && pos.getY() == SUBJECT.getY() && !pos.equals(SUBJECT)) throw new IndexOutOfBoundsException("bounded view edge");
            if (pattern == 3 && pos.getY() == SUBJECT.getY() && !pos.equals(SUBJECT)) return Blocks.AIR.defaultBlockState();
            return Blocks.STONE.defaultBlockState();
        }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public FluidState getFluidState(BlockPos pos) { return Fluids.EMPTY.defaultFluidState(); }
        @Override public int getHeight() { return 384; }
        @Override public int getMinBuildHeight() { return -64; }
        @Override public float getShade(Direction direction, boolean shade) { return 1; }
        @Override public LevelLightEngine getLightEngine() { return null; }
        @Override public int getBlockTint(BlockPos pos, ColorResolver resolver) { return 0xffffff; }
    }
}
