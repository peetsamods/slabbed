package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.client.model.ExposedStepFlatLighting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.*;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.function.Consumer;

/** Exercises the actual flat-light correction with native reusable renderer quads. */
public final class ExposedStepFlatLightingClientGameTest implements FabricClientGameTest {
    private static final BlockPos ORIGIN = BlockPos.ZERO;
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private int cases;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            boolean originalAo = client.options.ambientOcclusion().get();
            try {
                for (Direction face : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                    for (int[] channels : new int[][]{{0, 14}, {0, 0}, {12, 0}, {3, 11}, {0, 14}}) {
                        check(face, -.5f, 0, false, STONE, STONE, AIR, channels[0], channels[1], q -> {}, true, false);
                        check(face, -.5f, 0, false, STONE, STONE, AIR, channels[0], channels[1], q -> q.shadeMode(ShadeMode.ENHANCED), true, false);
                        check(face, -.5f, 0, false, STONE, STONE, AIR, channels[0], channels[1], q -> {
                            for (int v=0;v<4;v++) q.normal(v,face.getStepX(),face.getStepY(),face.getStepZ());
                        }, true, false);
                        check(face, -.5f, 0, false, STONE, STONE, AIR, channels[0], channels[1], q -> {
                            q.shadeMode(ShadeMode.ENHANCED); q.normal(0,face.getStepX(),face.getStepY(),face.getStepZ());
                        }, true, false);
                    }
                    check(face, 0, 0, false, STONE, STONE, AIR, 0, 14, q -> {}, false, false);
                    check(face, -.5f, -.5f, false, STONE, STONE, AIR, 0, 14, q -> {}, false, false);
                    check(face, -.5f, 0, true, STONE, STONE, AIR, 0, 14, q -> {}, false, false);
                    check(face, -.5f, 0, false, STONE, AIR, AIR, 0, 14, q -> {}, false, false);
                    check(face, -.5f, 0, false, STONE, Blocks.GLASS.defaultBlockState(), AIR, 0, 14, q -> {}, false, false);
                    check(face, -.5f, 0, false, STONE, Blocks.STONE_SLAB.defaultBlockState(), AIR, 0, 14, q -> {}, false, false);
                    check(face, -.5f, 0, false, STONE, STONE, STONE, 0, 14, q -> {}, false, false);
                    check(face, -.5f, 0, false, Blocks.GLOWSTONE.defaultBlockState(), STONE, AIR, 0, 14, q -> {}, false, false);
                    check(face, -.5f, 0, false, STONE, STONE, AIR, 0, 14, q -> {}, false, true);
                }
                for (float dy : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -1}) {
                    check(Direction.NORTH, dy, 0, false, STONE, STONE, AIR, 0, 14, q -> {}, false, false);
                }
                for (Consumer<MutableQuadView> edit : edits()) {
                    check(Direction.NORTH, -.5f, 0, false, STONE, STONE, AIR, 0, 14, edit, false, false);
                }
                check(Direction.NORTH, -.5f, Float.NaN, false, STONE, STONE, AIR, 0, 14, q -> {}, false, false);
                check(Direction.NORTH, -.5f, Float.POSITIVE_INFINITY, false, STONE, STONE, AIR, 0, 14, q -> {}, false, false);
                check(Direction.UP, -.5f, 0, false, STONE, STONE, AIR, 0, 14, q -> {}, false, false);
                check(null, -.5f, 0, false, STONE, STONE, AIR, 0, 14, q -> {}, false, false);
                check(Direction.NORTH, -.5f, 0, false, Blocks.STONE_SLAB.defaultBlockState(), STONE, AIR, 0, 14, q -> {}, false, false);
                check(Direction.NORTH, -.5f, 0, false, STONE, STONE, AIR, -1, 14, q -> {}, false, false);
                checkUnknownHeight();
                checkReuse();
                Slabbed.LOGGER.info("CLIENT_GAMETEST | ExposedStepFlatLightingClientGameTest | HELPER_PASS cases={}", cases);
            } finally {
                client.options.ambientOcclusion().set(originalAo);
            }
        });
        checkProductionPath(context);
    }

    private void checkProductionPath(ClientGameTestContext context) {
        try (var world = context.worldBuilder().setUseConsistentSettings(true).create()) {
            com.slabbed.test.support.TestConnections.waitForChunksDownload(world);
            BlockPos pos = world.getServer().computeOnServer(server -> {
                var level = server.overworld();
                BlockPos p = server.getPlayerList().getPlayers().getFirst().blockPosition().above(5).immutable();
                level.setBlock(p, STONE, 3);
                com.slabbed.anchor.SlabAnchorAttachment.writePlacementDy(level, p, -.5d);
                for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                    level.setBlock(p.relative(d).below(), AIR, 3);
                }
                return p;
            });
            context.waitFor(client -> {
                double dy = com.slabbed.anchor.SlabAnchorAttachment.storedPlacementDy(client.level, pos);
                return client.level.getBlockState(pos).is(Blocks.STONE) && Double.isFinite(dy) && Math.abs(dy + .5d) < 1e-6;
            }, 400);
            boolean original = context.computeOnClient(client -> client.options.ambientOcclusion().get());
            try {
                context.runOnClient(client -> client.options.ambientOcclusion().set(false));
                for (int phase = 0; phase < 3; phase++) {
                    boolean neighborPresent = phase == 1;
                    world.getServer().runOnServer(server -> {
                        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                            server.overworld().setBlock(pos.relative(d), neighborPresent ? STONE : AIR, 3);
                            if (neighborPresent) com.slabbed.anchor.SlabAnchorAttachment.writePlacementDy(server.overworld(), pos.relative(d), 0);
                        }
                    });
                    context.waitFor(client -> phaseReady(client, pos, neighborPresent), 400);
                    context.waitTicks(10);
                    context.runOnClient(client -> {
                        if (!phaseReady(client, pos, neighborPresent)) throw new AssertionError("production phase changed");
                        var state = client.level.getBlockState(pos);
                        int[] faces = {0};
                        var renderer = Renderer.get();
                        var output = renderer.quadEmitter(q -> {
                            Direction face = q.lightFace();
                            if (face.getAxis() == Direction.Axis.Y) return;
                            faces[0]++;
                            int expected = LightCoordsUtil.getLightCoords(client.level,
                                    neighborPresent ? pos.relative(face).below() : pos.relative(face));
                            if (LightCoordsUtil.sky(expected) == 0) throw new AssertionError("unlit production control");
                            for (int vertex = 0; vertex < 4; vertex++) {
                                if (!Float.isFinite(q.y(vertex)) || (Math.abs(q.y(vertex) + .5f) > 1e-6 && Math.abs(q.y(vertex) - .5f) > 1e-6))
                                    throw new AssertionError("production model was not lowered");
                                if (q.lightmap(vertex) != expected) throw new AssertionError("production exposed-face light mismatch face=" + face + " neighborPresent=" + neighborPresent + " actual=" + q.lightmap(vertex) + " expected=" + expected + " shade=" + q.shadeMode() + " ao=" + q.ambientOcclusion());
                            }
                        });
                        renderer.altModelBlockRenderer(false, true, client.getBlockColors()).tesselateBlock(output,
                                0, 0, 0, client.level, pos, state, client.getModelManager().getBlockStateModelSet().get(state), 0);
                        if (faces[0] != 4) throw new AssertionError("normal culling dropped exposed faces");
                    });
                }
                Slabbed.LOGGER.info("CLIENT_GAMETEST | ExposedStepFlatLightingClientGameTest | PASS production=true");
            } finally {
                context.runOnClient(client -> client.options.ambientOcclusion().set(original));
            }
        }
    }

    private static boolean phaseReady(Minecraft client, BlockPos pos, boolean neighborPresent) {
        if (client.level == null || !client.level.getBlockState(pos).is(Blocks.STONE)) return false;
        double subjectDy = com.slabbed.anchor.SlabAnchorAttachment.storedPlacementDy(client.level, pos);
        if (!Double.isFinite(subjectDy) || Math.abs(subjectDy + .5d) > 1e-6) return false;
        for (Direction face : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos neighbor = pos.relative(face);
            if (!client.level.getBlockState(neighbor.below()).isAir()) return false;
            if (neighborPresent) {
                double dy = com.slabbed.anchor.SlabAnchorAttachment.storedPlacementDy(client.level, neighbor);
                if (!client.level.getBlockState(neighbor).is(Blocks.STONE) || !Double.isFinite(dy) || Math.abs(dy) > 1e-6) return false;
                int covered = LightCoordsUtil.getLightCoords(client.level, neighbor);
                int exposed = LightCoordsUtil.getLightCoords(client.level, neighbor.below());
                if (covered != 0 || LightCoordsUtil.sky(exposed) == 0 || covered == exposed) return false;
            } else if (!client.level.getBlockState(neighbor).isAir()) return false;
        }
        return true;
    }

    private static java.util.List<Consumer<MutableQuadView>> edits() {
        return java.util.List.of(q -> q.emissive(true), q -> q.ambientOcclusion(TriState.TRUE),
                q -> q.ambientOcclusion(TriState.FALSE), q -> q.chunkLayer(ChunkSectionLayer.CUTOUT),
                q -> q.chunkLayer(ChunkSectionLayer.TRANSLUCENT), q -> q.atlas(QuadAtlas.ITEM),
                q -> q.tintIndex(0), q -> q.tag(1), q -> q.normal(0, .2f, 0, -1), q -> q.normal(0, 0, 0, 1),
                q -> q.normal(0, 0, 0, Float.NaN),
                q -> { for(int v=0;v<4;v++) q.normal(v,0,0,-1); q.normal(2,0,1,0); },
                ExposedStepFlatLightingClientGameTest::alterDiffuseShade,
                q -> q.lightmap(0, LightCoordsUtil.pack(7, 3)), q -> q.color(0, 0xffaabbcc),
                q -> q.pos(0, .25f, q.y(0), q.z(0)),
                q -> { float x=q.x(0), y=q.y(0), z=q.z(0); q.pos(0,q.x(1),q.y(1),q.z(1)); q.pos(1,x,y,z); },
                q -> q.pos(0,q.x(1),q.y(1),q.z(1)), q -> q.pos(0, q.x(0), Float.NaN, q.z(0)));
    }

    private void check(Direction face, float dy, float neighborDy, boolean ao, BlockState subject,
                       BlockState neighbor, BlockState below, int block, int sky,
                       Consumer<MutableQuadView> edit, boolean eligible, boolean bounded) {
        Minecraft.getInstance().options.ambientOcclusion().set(ao);
        int[] reads = {0};
        BlockAndTintGetter view = view(face, neighbor, below, block, sky, reads, bounded);
        QuadEmitter emitter = Renderer.get().quadEmitter(q -> {
            edit.accept(q);
            String before = attributes(q);
            int[] lights = lights(q);
            ExposedStepFlatLighting.apply(view, ORIGIN, subject, dy, face, q, () -> neighborDy);
            if (!before.equals(attributes(q))) throw new AssertionError("non-light attributes changed");
            int[] expected = eligible ? new int[]{LightCoordsUtil.pack(block, sky), LightCoordsUtil.pack(block, sky),
                    LightCoordsUtil.pack(block, sky), LightCoordsUtil.pack(block, sky)} : lights;
            if (!Arrays.equals(expected, lights(q))) throw new AssertionError("incorrect sampled light: " + face);
            if (eligible && reads[0] != 2) throw new AssertionError("eligible zero/light must sample both channels");
        });
        populate(emitter, face == null ? Direction.NORTH : face).emit();
        cases++;
    }

    private void checkUnknownHeight() {
        Minecraft.getInstance().options.ambientOcclusion().set(false);
        int[] reads = {0};
        QuadEmitter emitter = Renderer.get().quadEmitter(q -> {
            ExposedStepFlatLighting.apply(view(Direction.NORTH, STONE, AIR, 0, 14, reads, false),
                    ORIGIN, STONE, -.5f, Direction.NORTH, q,
                    () -> { throw new IndexOutOfBoundsException("height outside view"); });
            if (!Arrays.equals(lights(q), new int[4]) || reads[0] != 0)
                throw new AssertionError("unknown neighbor height was treated as flat");
        });
        populate(emitter, Direction.NORTH).emit();
        cases++;
    }

    private void checkReuse() {
        Minecraft client = Minecraft.getInstance();
        int[] channels = {0, 14};
        int[] reads = {0};
        boolean[] ao = {false};
        QuadEmitter emitter = Renderer.get().quadEmitter(q -> {
            if (!Arrays.equals(lights(q), new int[4])) throw new AssertionError("emitter retained prior light");
            ExposedStepFlatLighting.apply(view(Direction.NORTH, STONE, AIR, channels[0], channels[1], reads, false),
                    ORIGIN, STONE, -.5f, Direction.NORTH, q, () -> 0);
            int expected = ao[0] ? 0 : LightCoordsUtil.pack(channels[0], channels[1]);
            for (int light : lights(q)) if (light != expected) throw new AssertionError("reused quad light leaked");
        });
        for (int[] next : new int[][]{{0, 14, 0}, {0, 0, 0}, {12, 0, 0}, {3, 11, 0}, {0, 14, 1}, {0, 14, 0}}) {
            channels[0] = next[0]; channels[1] = next[1]; ao[0] = next[2] != 0;
            client.options.ambientOcclusion().set(ao[0]);
            populate(emitter, Direction.NORTH).emit();
            cases++;
        }
    }

    private static QuadEmitter populate(QuadEmitter q, Direction face) {
        q.square(face, 0, 0, 1, 1, 0).translate(0, -.5f, 0);
        q.cullFace(face).nominalFace(face).chunkLayer(ChunkSectionLayer.SOLID).atlas(QuadAtlas.BLOCK)
                .shadeMode(ShadeMode.VANILLA);
        return q;
    }

    private static BlockAndTintGetter view(Direction face, BlockState neighbor, BlockState below,
                                           int block, int sky, int[] reads, boolean bounded) {
        return (BlockAndTintGetter) Proxy.newProxyInstance(BlockAndTintGetter.class.getClassLoader(),
                new Class<?>[]{BlockAndTintGetter.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getBlockState")) {
                        if (bounded) throw new IndexOutOfBoundsException("bounded view");
                        BlockPos p = (BlockPos) args[0];
                        if (p.equals(ORIGIN.relative(face))) return neighbor;
                        if (p.equals(ORIGIN.relative(face).below())) return below;
                        throw new AssertionError("unexpected block sample");
                    }
                    if (method.getName().equals("getBrightness")) {
                        if (block < 0) throw new IndexOutOfBoundsException("light outside view");
                        if (!args[1].equals(ORIGIN.relative(face).below())) throw new AssertionError("wrong light cell");
                        reads[0]++;
                        return args[0] == LightLayer.SKY ? sky : block;
                    }
                    throw new AssertionError("unexpected view method " + method.getName());
                });
    }

    private static int[] lights(MutableQuadView q) {
        return new int[]{q.lightmap(0), q.lightmap(1), q.lightmap(2), q.lightmap(3)};
    }

    private static void alterDiffuseShade(MutableQuadView quad) {
        try {
            if (com.slabbed.compat.MinecraftVersions.AT_LEAST_26_3)
                MutableQuadView.class.getMethod("shadeDirectionOverride", Direction.class).invoke(quad, Direction.UP);
            else MutableQuadView.class.getMethod("diffuseShade", boolean.class).invoke(quad, false);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("diffuse material setter unavailable", e);
        }
    }

    private static Object diffuseShade(QuadView quad) {
        try {
            return QuadView.class.getMethod(com.slabbed.compat.MinecraftVersions.AT_LEAST_26_3
                    ? "shadeDirectionOverride" : "diffuseShade").invoke(quad);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("diffuse material getter unavailable", e);
        }
    }

    private static String attributes(MutableQuadView q) {
        StringBuilder s = new StringBuilder().append(q.cullFace()).append(q.nominalFace()).append(q.atlas())
                .append(q.chunkLayer()).append(q.emissive()).append(q.ambientOcclusion()).append(q.shadeMode())
                .append(diffuseShade(q)).append(q.tintIndex()).append(q.tag());
        for (int i = 0; i < 4; i++) s.append(q.x(i)).append('/').append(q.y(i)).append('/').append(q.z(i))
                .append('/').append(q.u(i)).append('/').append(q.v(i)).append('/').append(q.color(i))
                .append('/').append(q.hasNormal(i)).append('/').append(q.normalX(i)).append('/').append(q.normalY(i)).append('/').append(q.normalZ(i));
        return s.toString();
    }
}
