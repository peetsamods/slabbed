package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

import java.util.ArrayList;
import java.util.List;

/**
 * The drawn rail really is sheared to the fitted profile: the shipped model wrapper emits a
 * lowered rail's quads rising to meet its flush neighbour, a ramp refitted shorter onto a lowered
 * rail, and a lowered ramp refitted longer onto a flush rail. The headless twin proves the RULE;
 * only a real client can prove the rule reaches the vertices the chunk mesh is built from.
 *
 * <p>Three scenes, side by side, each a north-south pair. For each subject rail the emitted quads
 * are collected straight from the block-state model set's model for that state — the same wrapped
 * model the chunk mesher uses — and the vertex heights at the rail's north and south edges are
 * compared to the fitted profile plus the seat plus vanilla's 1/16 rail lift.
 *
 * <p>STAGING: waiting a tick is NOT a barrier against the server. This row polls until the client
 * holds every rail with the shape vanilla gave it AND every authored seat, and fails on timeout
 * rather than measuring a scene that is not there yet.
 *
 * <p>MUTATION that must redden this test: withhold {@code RailSlopeShear} from
 * {@code OffsetBlockStateModel.emitQuads}.
 */
public final class RailSlopeRenderClientGameTest implements FabricClientGameTest {

    private static final String CLIENT_GAMETEST_PASS =
            "CLIENT_GAMETEST | RailSlopeRenderClientGameTest | PASS";
    private static final double LOWERED = -0.5d;
    /** Vanilla draws a rail 1/16 above its cell floor. */
    private static final double RAIL_LIFT = 1.0d / 16.0d;
    private static final double EPS = 1.0e-4d;
    private static final double EDGE = 1.0e-3d;

    /**
     * One north-south pair: the subject rail and the rail south of it, with the neighbour's grid
     * step (0 for the same row, 1 for one cell up) and both seats, plus what the subject's north and
     * south edges must draw at, relative to the subject's cell floor.
     */
    private record Scene(String name, int neighbourStep, double subjectSeat, double neighbourSeat,
                         RailShape subjectShape, double expectedNorth, double expectedSouth) {
    }

    private static final List<Scene> SCENES = List.of(
            // A lowered flat rail rises half a block to its flush neighbour.
            new Scene("flat lifts", 0, LOWERED, 0.0d, RailShape.NORTH_SOUTH,
                    RAIL_LIFT + LOWERED, RAIL_LIFT + LOWERED + 0.5d),
            // A flush ramp onto a lowered rail is only half as steep.
            new Scene("ramp shorter", 1, 0.0d, LOWERED, RailShape.ASCENDING_SOUTH,
                    RAIL_LIFT, RAIL_LIFT + 0.5d),
            // A lowered ramp onto a flush rail climbs a block and a half.
            new Scene("ramp longer", 1, LOWERED, 0.0d, RailShape.ASCENDING_SOUTH,
                    RAIL_LIFT + LOWERED, RAIL_LIFT + LOWERED + 1.5d));

    private record Fixture(List<BlockPos> subjects, List<BlockPos> neighbours) {
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            singleplayer.getConnection().waitForChunksDownload();
            ctx.waitFor(client -> client.level != null && client.player != null, 400);

            Fixture fixture = singleplayer.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                ServerLevel level = server.overworld();
                BlockPos origin = player.blockPosition().relative(player.getDirection(), 5).above(2).immutable();
                return buildFixture(level, origin);
            });

            ctx.waitFor(client -> client.level != null && sceneArrived(client.level, fixture), 400);
            ctx.waitTicks(5);

            ctx.runOnClient(client -> {
                for (int i = 0; i < SCENES.size(); i++) {
                    Scene scene = SCENES.get(i);
                    BlockPos subject = fixture.subjects().get(i);
                    BlockPos neighbour = fixture.neighbours().get(i);
                    BlockState subjectState = client.level.getBlockState(subject);
                    if (subjectState.getValue(RailBlock.SHAPE) != scene.subjectShape()) {
                        throw new AssertionError("premise (" + scene.name() + "): vanilla shaped the subject as "
                                + subjectState.getValue(RailBlock.SHAPE) + ", the scene needs " + scene.subjectShape());
                    }
                    double[] subjectEdges = edgeHeights(client, subject, subjectState, scene.name());
                    requireEdge(scene, "north", subjectEdges[0], scene.expectedNorth());
                    requireEdge(scene, "south", subjectEdges[1], scene.expectedSouth());

                    // The neighbour is the higher rail of its seam and keeps vanilla's flat plane.
                    BlockState neighbourState = client.level.getBlockState(neighbour);
                    double[] neighbourEdges = edgeHeights(client, neighbour, neighbourState, scene.name() + " neighbour");
                    double flat = RAIL_LIFT + scene.neighbourSeat();
                    requireEdge(scene, "neighbour north", neighbourEdges[0], flat);
                    requireEdge(scene, "neighbour south", neighbourEdges[1], flat);
                }
                Slabbed.LOGGER.info(CLIENT_GAMETEST_PASS);
            });
        }
    }

    private static Fixture buildFixture(ServerLevel level, BlockPos origin) {
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-2, -2, -2), origin.offset(SCENES.size() * 3 + 2, 4, 3))) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        List<BlockPos> subjects = new ArrayList<>();
        List<BlockPos> neighbours = new ArrayList<>();
        for (int i = 0; i < SCENES.size(); i++) {
            Scene scene = SCENES.get(i);
            BlockPos subject = origin.east(i * 3).immutable();
            BlockPos neighbour = subject.south().above(scene.neighbourStep()).immutable();
            // The neighbour first, so vanilla's own shape update makes the subject a ramp when it must.
            rail(level, neighbour, scene.neighbourSeat());
            rail(level, subject, scene.subjectSeat());
            subjects.add(subject);
            neighbours.add(neighbour);
        }
        return new Fixture(subjects, neighbours);
    }

    /** A rail on stone, seated at {@code dy} on the rail and its support (0 authors nothing). */
    private static void rail(ServerLevel level, BlockPos pos, double dy) {
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos, Blocks.RAIL.defaultBlockState(), Block.UPDATE_ALL);
        if (dy != 0.0d) {
            SlabAnchorAttachment.writePlacementDy(level, pos.below(), dy);
            SlabAnchorAttachment.writePlacementDy(level, pos, dy);
        }
    }

    /** True once the client holds every rail with vanilla's shape and every authored seat. */
    private static boolean sceneArrived(Level level, Fixture fixture) {
        for (int i = 0; i < SCENES.size(); i++) {
            Scene scene = SCENES.get(i);
            if (!railArrived(level, fixture.subjects().get(i), scene.subjectShape(), scene.subjectSeat())
                    || !railArrived(level, fixture.neighbours().get(i), RailShape.NORTH_SOUTH, scene.neighbourSeat())) {
                return false;
            }
        }
        return true;
    }

    private static boolean railArrived(Level level, BlockPos pos, RailShape shape, double seat) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof RailBlock) || state.getValue(RailBlock.SHAPE) != shape) {
            return false;
        }
        if (seat == 0.0d) {
            return true;
        }
        double stored = SlabAnchorAttachment.storedPlacementDy(level, pos);
        return Double.isFinite(stored) && Math.abs(stored - seat) < EPS;
    }

    /**
     * The drawn height of the rail's north edge and south edge: the mean vertex Y over every emitted
     * vertex lying on that edge, relative to the cell floor. Every vertex of every rail quad is on one
     * edge or the other (vanilla's rail is a single 16-wide plane), so a vertex on neither is a
     * premise failure, and a rail that emits nothing is one too.
     */
    private static double[] edgeHeights(Minecraft client, BlockPos pos, BlockState state, String label) {
        Renderer renderer = Renderer.get();
        if (renderer == null) {
            throw new AssertionError("premise: no renderer API implementation is registered on this client");
        }
        BlockStateModel model = client.getModelManager().getBlockStateModelSet().get(state);
        if (!(model instanceof FabricBlockStateModel fabric)) {
            throw new AssertionError("premise (" + label + "): the rail's model is not a Fabric block-state model: "
                    + model);
        }
        double[] sum = {0.0d, 0.0d};
        int[] count = {0, 0};
        List<String> stray = new ArrayList<>();
        QuadEmitter emitter = renderer.quadEmitter(quad -> {
            for (int i = 0; i < 4; i++) {
                float z = quad.z(i);
                if (Math.abs(z) < EDGE) {
                    sum[0] += quad.y(i);
                    count[0]++;
                } else if (Math.abs(z - 1.0f) < EDGE) {
                    sum[1] += quad.y(i);
                    count[1]++;
                } else {
                    stray.add("(" + quad.x(i) + ", " + quad.y(i) + ", " + z + ")");
                }
            }
        });
        fabric.emitQuads(emitter, client.level, pos, state, RandomSource.create(42L), direction -> false);
        if (count[0] == 0 || count[1] == 0) {
            throw new AssertionError("premise (" + label + "): the rail at " + pos + " emitted no vertex on its"
                    + " north edge (" + count[0] + ") or south edge (" + count[1] + ")");
        }
        if (!stray.isEmpty()) {
            throw new AssertionError("premise (" + label + "): the rail at " + pos + " emitted vertices off both"
                    + " edges, so an edge mean is not its drawn height: " + stray);
        }
        return new double[] {sum[0] / count[0], sum[1] / count[1]};
    }

    private static void requireEdge(Scene scene, String edge, double actual, double expected) {
        if (Math.abs(actual - expected) > EPS) {
            throw new AssertionError("(" + scene.name() + ") the rail's " + edge + " edge must draw at "
                    + expected + " above its cell floor, got " + actual);
        }
    }
}
