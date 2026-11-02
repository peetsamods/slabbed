package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.model.OffsetBlockStateModel;
import com.slabbed.test.support.TestConnections;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Saved heights preserve renderer-owned emitter identity and unwind their transform scope. */
public final class EmitterIdentityClientGameTest implements FabricClientGameTest {
    private static final int COLOR = 0xff6a92bc;
    private static final int LIGHT = 0x00f000a0;
    private static final int TAG = 37;
    private static final double EPSILON = 1.0e-6d;

    private record Vertex(float x, float y, float z, float u, float v, int color, int light,
                          boolean normal, float nx, float ny, float nz) { }

    private record Sample(float minY, float maxY, List<Vertex> vertices, int tag,
                          Direction cull, Direction nominal) {
        static Sample capture(QuadView quad) {
            float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
            List<Vertex> vertices = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                min = Math.min(min, quad.y(i));
                max = Math.max(max, quad.y(i));
                boolean normal = quad.hasNormal(i);
                vertices.add(new Vertex(quad.x(i), quad.y(i), quad.z(i), quad.u(i), quad.v(i),
                        quad.color(i), quad.lightmap(i), normal,
                        normal ? quad.normalX(i) : 0, normal ? quad.normalY(i) : 0,
                        normal ? quad.normalZ(i) : 0));
            }
            return new Sample(min, max, List.copyOf(vertices), quad.tag(),
                    quad.cullFace(), quad.nominalFace());
        }
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().setUseConsistentSettings(true).create()) {
            TestConnections.waitForChunksDownload(world);
            BlockPos lowered = world.getServer().computeOnServer(server -> {
                var level = server.overworld();
                BlockPos origin = server.getPlayerList().getPlayers().getFirst().blockPosition().above(4);
                for (int dx : new int[]{0, 1, 8}) {
                    level.setBlock(origin.east(dx), Blocks.STONE.defaultBlockState(), 3);
                    SlabAnchorAttachment.writePlacementDy(level, origin.east(dx), dx == 0 ? -0.5d : 0.0d);
                }
                return origin;
            });
            context.waitFor(client -> client.level != null && client.player != null
                    && client.level.getBlockState(lowered).is(Blocks.STONE)
                    && Math.abs(SlabAnchorAttachment.storedPlacementDy(client.level, lowered) + 0.5d) < EPSILON, 400);
            context.runOnClient(client -> {
                checkEmission(client, lowered, -0.5d, Direction.EAST, true, false, false);
                checkEmission(client, lowered, -0.5d, Direction.EAST, true, true, false);
                checkEmission(client, lowered.east(), 0.0d, Direction.WEST, true, false, false);
                checkEmission(client, lowered.east(8), 0.0d, Direction.EAST, false, false, false);
                checkEmission(client, lowered, -0.5d, Direction.EAST, true, false, true);
                checkOrdinary(client, lowered);
                checkExceptionCleanup(client, lowered);
            });
            Slabbed.LOGGER.info("CLIENT_GAMETEST | EmitterIdentityClientGameTest | PASS");
        }
    }

    private static void checkEmission(Minecraft client, BlockPos pos, double dy, Direction face,
                                      boolean clearCull, boolean delegating, boolean childScale) {
        Renderer renderer = Renderer.get();
        if (renderer == null) throw new AssertionError("renderer unavailable");
        List<Sample> samples = new ArrayList<>();
        List<Sample> nativeControl = new ArrayList<>();
        QuadEmitter nativeEmitter = renderer.quadEmitter(quad -> samples.add(Sample.capture(quad)));
        BlockStateModel base = client.getModelManager().getBlockStateModelSet().get(Blocks.STONE.defaultBlockState());
        BlockStateModel replacement = new WrapperBlockStateModel(base) {
            @Override
            public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos p, BlockState state,
                                  RandomSource random, Predicate<Direction> cullTest) {
                var mesh = controlMesh(renderer, base, face);
                mesh.outputTo(renderer.quadEmitter(quad -> nativeControl.add(Sample.capture(quad))));
                // Mesh output must work with the same native emitter handed to the outer model.
                if (childScale) emitter.pushTransform(quad -> {
                    for (int i = 0; i < 4; i++) quad.pos(i, quad.x(i), quad.y(i) * 2, quad.z(i));
                    return true;
                });
                try {
                    mesh.outputTo(emitter);
                } finally {
                    if (childScale) emitter.popTransform();
                }
                if (emitter != nativeEmitter) throw new AssertionError("renderer emitter identity changed");
            }
        };
        BlockStateModel child = delegating ? new WrapperBlockStateModel(replacement) { } : replacement;
        emit(new OffsetBlockStateModel(child), nativeEmitter, client, pos);
        if (samples.size() != 1) throw new AssertionError("replacement must emit exactly one quad");
        Sample sample = samples.getFirst();
        if (nativeControl.size() != 1) throw new AssertionError("native control must emit one quad");
        Sample control = nativeControl.getFirst();
        assertBounds(sample, dy, dy + (childScale ? 2.0d : 1.0d));
        assertAttributes(sample, control, dy, childScale ? 2 : 1, clearCull);
        controlMesh(renderer, base, face).outputTo(nativeEmitter);
        if (samples.size() != 2) throw new AssertionError("successful scope did not leave a reusable emitter");
        assertAttributes(samples.get(1), control, 0.0d, 1, false);
        Slabbed.LOGGER.info("EMITTER_IDENTITY dy={} delegating={} cullCleared={} childScale={} reuse=PASS",
                dy, delegating, clearCull, childScale);
    }

    private static MutableMesh controlMesh(Renderer renderer, BlockStateModel base, Direction face) {
        var mesh = renderer.mutableMesh();
        var emitter = mesh.emitter().square(Direction.SOUTH, 0, 0, 1, 1, 0)
                .materialBake(base.particleMaterial(), MutableQuadView.BAKE_LOCK_UV)
                .color(COLOR, 0xff8642a1, 0xff179051, 0xffde7a32)
                .lightmap(LIGHT, 0x00f00080, 0x00e000a0, 0x00d00070)
                .tag(TAG).cullFace(face).nominalFace(face);
        for (int i = 0; i < 4; i++) {
            emitter.uv(i, 0.13f + i * 0.17f, 0.91f - i * 0.11f);
            emitter.normal(i, 0.1f * (i + 1), 0.2f, 0.7f - i * 0.1f);
        }
        emitter.emit();
        return mesh;
    }

    private static void assertAttributes(Sample actual, Sample control, double dy, int scaleY,
                                         boolean clearCull) {
        for (int i = 0; i < 4; i++) {
            Vertex a = actual.vertices().get(i), b = control.vertices().get(i);
            if (Math.abs(a.x() - b.x()) > EPSILON || Math.abs(a.y() - (b.y() * scaleY + dy)) > EPSILON
                    || Math.abs(a.z() - b.z()) > EPSILON || Math.abs(a.u() - b.u()) > EPSILON
                    || Math.abs(a.v() - b.v()) > EPSILON || a.color() != b.color() || a.light() != b.light()
                    || a.normal() != b.normal() || Math.abs(a.nx() - b.nx()) > EPSILON
                    || Math.abs(a.ny() - b.ny()) > EPSILON || Math.abs(a.nz() - b.nz()) > EPSILON) {
                throw new AssertionError("vertex attributes or transform order changed at vertex " + i);
            }
        }
        if (actual.tag() != control.tag() || actual.cull() != (clearCull ? null : control.cull())
                || actual.nominal() != (clearCull ? control.cull() : control.nominal())) {
            throw new AssertionError("quad attributes or seam culling changed");
        }
    }

    private static void checkOrdinary(Minecraft client, BlockPos pos) {
        List<Sample> samples = new ArrayList<>();
        var emitter = Renderer.get().quadEmitter(quad -> samples.add(Sample.capture(quad)));
        var base = client.getModelManager().getBlockStateModelSet().get(Blocks.STONE.defaultBlockState());
        emit(base, emitter, client, pos);
        if (samples.isEmpty()) throw new AssertionError("ordinary model emitted no geometry");
        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
        for (Sample sample : samples) { min = Math.min(min, sample.minY()); max = Math.max(max, sample.maxY()); }
        if (Math.abs(min + 0.5d) > EPSILON || Math.abs(max - 0.5d) > EPSILON) {
            throw new AssertionError("ordinary model must receive one saved-height shift");
        }
    }

    private static void checkExceptionCleanup(Minecraft client, BlockPos pos) {
        List<Sample> samples = new ArrayList<>();
        QuadEmitter emitter = Renderer.get().quadEmitter(quad -> samples.add(Sample.capture(quad)));
        var base = client.getModelManager().getBlockStateModelSet().get(Blocks.STONE.defaultBlockState());
        BlockStateModel throwing = new WrapperBlockStateModel(base) {
            @Override
            public void emitQuads(QuadEmitter out, BlockAndTintGetter level, BlockPos p, BlockState state,
                                  RandomSource random, Predicate<Direction> cullTest) {
                throw new IllegalStateException("expected emitter cleanup control");
            }
        };
        BlockStateModel nested = new WrapperBlockStateModel(base) {
            @Override
            public void emitQuads(QuadEmitter out, BlockAndTintGetter level, BlockPos p, BlockState state,
                                  RandomSource random, Predicate<Direction> cullTest) {
                new OffsetBlockStateModel(throwing).emitQuads(out, level, p, state, random, cullTest);
            }
        };
        int[] callerTransforms = {0};
        emitter.pushTransform(quad -> { callerTransforms[0]++; return true; });
        try {
            try {
                emit(new OffsetBlockStateModel(nested), emitter, client, pos);
                throw new AssertionError("exception control did not execute");
            } catch (IllegalStateException expected) {
                if (!"expected emitter cleanup control".equals(expected.getMessage())) throw expected;
            }
            emitter.square(Direction.SOUTH, 0, 0, 1, 1, 0)
                    .materialBake(base.particleMaterial(), MutableQuadView.BAKE_LOCK_UV).emit();
            if (samples.size() != 1 || callerTransforms[0] != 1) {
                throw new AssertionError("nested failure did not preserve the caller transform");
            }
            assertBounds(samples.getFirst(), 0.0d, 1.0d);
        } finally {
            emitter.popTransform();
        }
        Slabbed.LOGGER.info("EMITTER_IDENTITY nested-exception-cleanup PASS");
    }

    private static void emit(BlockStateModel model, QuadEmitter emitter, Minecraft client, BlockPos pos) {
        model.emitQuads(emitter, client.level, pos, Blocks.STONE.defaultBlockState(),
                RandomSource.create(0), direction -> false);
    }

    private static void assertBounds(Sample sample, double min, double max) {
        if (Math.abs(sample.minY() - min) > EPSILON || Math.abs(sample.maxY() - max) > EPSILON) {
            throw new AssertionError("geometry must receive exactly one height shift: " + sample);
        }
    }
}
