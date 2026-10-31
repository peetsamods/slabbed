package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.test.support.TestConnections;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Late replacement geometry and delegating wrappers each receive one saved-height transform. */
public final class ReplacementModelOffsetClientGameTest implements FabricClientGameTest {
    private static final double EPSILON = 1.0e-6d;

    public static final class Fixtures implements ClientModInitializer {
        @Override
        public void onInitializeClient() {
            ModelLoadingPlugin.register(plugin -> plugin.modifyBlockModelAfterBake().register(
                    ModelModifier.WRAP_LAST_PHASE, (model, context) -> {
                        boolean replacement = context.state().is(Blocks.GLASS);
                        boolean delegating = context.state().is(Blocks.GOLD_BLOCK);
                        if (!replacement && !delegating) return model;
                        return new WrapperBlockStateModel(model) {
                            @Override
                            public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level,
                                                  BlockPos pos, BlockState state, RandomSource random,
                                                  Predicate<Direction> cullTest) {
                                if (delegating) {
                                    model.emitQuads(emitter, level, pos, state, random, cullTest);
                                } else {
                                    emitter.square(Direction.SOUTH, 0, 0, 1, 1, 0)
                                            .materialBake(model.particleMaterial(), MutableQuadView.BAKE_LOCK_UV)
                                            .emit();
                                }
                            }
                        };
                    }));
        }
    }

    private record Subject(String name, BlockPos pos, BlockState state, double dy) {}

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(true).create()) {
            TestConnections.waitForChunksDownload(world);
            context.waitFor(client -> client.level != null && client.player != null, 400);
            List<Subject> subjects = world.getServer().computeOnServer(server -> {
                var level = server.overworld();
                BlockPos origin = server.getPlayerList().getPlayers().getFirst().blockPosition().above(4);
                List<Subject> rows = List.of(
                        new Subject("replacement", origin, Blocks.GLASS.defaultBlockState(), -0.5d),
                        new Subject("delegating", origin.east(2), Blocks.GOLD_BLOCK.defaultBlockState(), -0.5d),
                        new Subject("ordinary", origin.east(4), Blocks.STONE.defaultBlockState(), -0.5d),
                        new Subject("flat-replacement", origin.east(6), Blocks.GLASS.defaultBlockState(), 0.0d));
                for (Subject row : rows) {
                    level.setBlock(row.pos(), row.state(), 3);
                    SlabAnchorAttachment.writePlacementDy(level, row.pos(), row.dy());
                }
                return rows;
            });
            context.waitFor(client -> subjects.stream().allMatch(row ->
                    client.level.getBlockState(row.pos()).equals(row.state())
                            && Math.abs(SlabAnchorAttachment.storedPlacementDy(client.level, row.pos())
                            - row.dy()) < EPSILON), 400);
            List<String> failures = context.computeOnClient(client -> {
                List<String> errors = new ArrayList<>();
                for (Subject row : subjects) {
                    float[] bounds = emitBounds(client, row);
                    Slabbed.LOGGER.info("REPLACEMENT_MODEL_OFFSET kind={} expected={} min={} max={} quads={}",
                            row.name(), row.dy(), bounds[0], bounds[1], bounds[2]);
                    if (bounds[2] == 0 || Math.abs(bounds[0] - row.dy()) > EPSILON
                            || Math.abs(bounds[1] - (1.0d + row.dy())) > EPSILON) {
                        errors.add(row.name() + " must receive exactly one height transform: expected "
                                + row.dy() + ".." + (1.0d + row.dy()) + ", got "
                                + bounds[0] + ".." + bounds[1] + ", quads=" + bounds[2]);
                    }
                }
                return errors;
            });
            if (!failures.isEmpty()) throw new AssertionError(String.join("\n", failures));
            Slabbed.LOGGER.info("CLIENT_GAMETEST | ReplacementModelOffsetClientGameTest | PASS");
        }
    }

    private static float[] emitBounds(Minecraft client, Subject row) {
        Renderer renderer = Renderer.get();
        if (renderer == null) throw new AssertionError("a renderer must be active for the emission proof");
        float[] bounds = {Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0};
        QuadEmitter emitter = renderer.quadEmitter(quad -> {
            bounds[2]++;
            for (int vertex = 0; vertex < 4; vertex++) {
                bounds[0] = Math.min(bounds[0], quad.y(vertex));
                bounds[1] = Math.max(bounds[1], quad.y(vertex));
            }
        });
        client.getModelManager().getBlockStateModelSet().get(row.state()).emitQuads(
                emitter, client.level, row.pos(), row.state(), RandomSource.create(0), direction -> false);
        return bounds;
    }
}
