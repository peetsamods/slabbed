package com.slabbed.test;

import com.slabbed.test.support.TestConnections;
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
import net.minecraft.core.Direction;
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
 * The drawn rail really is on the fitted profile: the shipped model wrapper emits a lowered rail's
 * quads rising to meet its flush neighbour (on both axes), a ramp refitted shorter onto a lowered
 * rail (whether or not its own cell is a step seam), a lowered ramp refitted longer onto a flush
 * rail, and a rail in a dip as a V resting on its support at the middle — and a rail whose
 * neighbour is removed draws flat again. The headless twin proves the RULE; only a real client can
 * prove the rule reaches the vertices the chunk mesh is built from.
 *
 * <p>For each subject rail the emitted quads are collected straight from the block-state model
 * set's model for that state — the same wrapped model the chunk mesher uses — and every vertex is
 * classified by its position along the rail's axis: the negative edge, the middle, or the positive
 * edge. A vertex anywhere else is a premise failure (vanilla's rail is one plane spanning the cell;
 * a split V adds vertices exactly at the middle).
 *
 * <p>STAGING: waiting a tick is NOT a barrier against the server. This row polls until the client
 * holds every rail with the shape vanilla gave it AND every authored seat, and fails on timeout
 * rather than measuring a scene that is not there yet.
 *
 * <p>MUTATIONS that must redden this test: withhold {@code RailSlopeGeometry} from
 * {@code OffsetBlockStateModel.emitQuads} (every scene), or emit a kinked profile as one plane
 * instead of two (the dip scene finds no middle vertices).
 */
public final class RailSlopeRenderClientGameTest implements FabricClientGameTest {

    private static final String CLIENT_GAMETEST_PASS =
            "CLIENT_GAMETEST | RailSlopeRenderClientGameTest | PASS";
    private static final double LOWERED = -0.5d;
    /** Vanilla draws a rail 1/16 above its cell floor. */
    private static final double RAIL_LIFT = 1.0d / 16.0d;
    private static final double LOWERED_BASE = RAIL_LIFT + LOWERED;
    private static final double EPS = 1.0e-4d;
    private static final double EDGE = 1.0e-3d;
    private static final double NO_MIDDLE = Double.NaN;
    private static final int SCENE_SPACING = 4;

    /**
     * One scene: the subject rail, its neighbour on the positive side of the axis (south or east),
     * optionally a second neighbour on the negative side (the dip), the neighbours' grid step
     * (0 for the same row, 1 for one cell up), the seats, whether the neighbours' supports are
     * seated too (a seated support is a step seam on the subject's face; an unseated one is not),
     * and what the subject must draw at its negative edge, its middle and its positive edge,
     * relative to its cell floor.
     */
    private record Scene(String name, Direction.Axis axis, boolean bothSides, int neighbourStep,
                         double subjectSeat, double neighbourSeat, boolean seatSupports,
                         RailShape subjectShape,
                         double expectedNegative, double expectedMiddle, double expectedPositive) {

        Direction positive() {
            return axis == Direction.Axis.Z ? Direction.SOUTH : Direction.EAST;
        }
    }

    private static final List<Scene> SCENES = List.of(
            // A lowered flat rail rises half a block to its flush neighbour.
            new Scene("flat lifts", Direction.Axis.Z, false, 0, LOWERED, 0.0d, true, RailShape.NORTH_SOUTH,
                    LOWERED_BASE, NO_MIDDLE, LOWERED_BASE + 0.5d),
            // A flush ramp onto a lowered rail is only half as steep.
            new Scene("ramp shorter", Direction.Axis.Z, false, 1, 0.0d, LOWERED, true, RailShape.ASCENDING_SOUTH,
                    RAIL_LIFT, NO_MIDDLE, RAIL_LIFT + 0.5d),
            // A lowered ramp onto a flush rail climbs a block and a half.
            new Scene("ramp longer", Direction.Axis.Z, false, 1, LOWERED, 0.0d, true, RailShape.ASCENDING_SOUTH,
                    LOWERED_BASE, NO_MIDDLE, LOWERED_BASE + 1.5d),
            // The same lift along the east-west axis.
            new Scene("flat lifts east-west", Direction.Axis.X, false, 0, LOWERED, 0.0d, true, RailShape.EAST_WEST,
                    LOWERED_BASE, NO_MIDDLE, LOWERED_BASE + 0.5d),
            // A rail in a dip: a V that rests on its support at the middle and meets both neighbours.
            new Scene("dip is a V", Direction.Axis.Z, true, 0, LOWERED, 0.0d, true, RailShape.NORTH_SOUTH,
                    LOWERED_BASE + 0.5d, LOWERED_BASE, LOWERED_BASE + 0.5d),
            // The flush ramp again with an unseated support under its neighbour: the subject's own
            // cell is then no step seam at all, the other branch of the ordinary render path.
            new Scene("ramp shorter, no seam", Direction.Axis.Z, false, 1, 0.0d, LOWERED, false, RailShape.ASCENDING_SOUTH,
                    RAIL_LIFT, NO_MIDDLE, RAIL_LIFT + 0.5d));

    private record Fixture(List<BlockPos> subjects, List<BlockPos> neighbours) {
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            TestConnections.waitForChunksDownload(singleplayer);
            ctx.waitFor(client -> client.level != null && client.player != null, 400);

            Fixture fixture = singleplayer.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                ServerLevel level = server.overworld();
                BlockPos origin = player.blockPosition().relative(player.getDirection(), 6).above(2).immutable();
                return buildFixture(level, origin);
            });

            ctx.waitFor(client -> client.level != null && sceneArrived(client.level, fixture), 400);
            ctx.waitTicks(5);

            ctx.runOnClient(client -> {
                for (int i = 0; i < SCENES.size(); i++) {
                    measureScene(client, SCENES.get(i), fixture.subjects().get(i), fixture.neighbours().get(i));
                }
            });

            // The first scene's neighbour goes away: the subject must draw flat again. This is the
            // live sequence — a rail already drawn, then a neighbour edit — through the client's view.
            BlockPos removed = fixture.neighbours().getFirst();
            singleplayer.getServer().runOnServer(server ->
                    server.overworld().setBlock(removed, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
            ctx.waitFor(client -> client.level != null && client.level.getBlockState(removed).isAir(), 400);
            ctx.waitTicks(5);
            ctx.runOnClient(client -> {
                Scene scene = SCENES.getFirst();
                BlockPos subject = fixture.subjects().getFirst();
                double[] heights = profileHeights(client, subject, client.level.getBlockState(subject),
                        scene.axis(), "after removal");
                requireHeight(scene, "negative edge after the neighbour is removed", heights[0], LOWERED_BASE);
                requireHeight(scene, "positive edge after the neighbour is removed", heights[2], LOWERED_BASE);
                Slabbed.LOGGER.info(CLIENT_GAMETEST_PASS);
            });
        }
    }

    private static void measureScene(Minecraft client, Scene scene, BlockPos subject, BlockPos neighbour) {
        BlockState subjectState = client.level.getBlockState(subject);
        if (subjectState.getValue(RailBlock.SHAPE) != scene.subjectShape()) {
            throw new AssertionError("premise (" + scene.name() + "): vanilla shaped the subject as "
                    + subjectState.getValue(RailBlock.SHAPE) + ", the scene needs " + scene.subjectShape());
        }
        double[] heights = profileHeights(client, subject, subjectState, scene.axis(), scene.name());
        requireHeight(scene, "negative edge", heights[0], scene.expectedNegative());
        if (!Double.isNaN(scene.expectedMiddle())) {
            if (Double.isNaN(heights[1])) {
                throw new AssertionError("(" + scene.name() + ") a V must be drawn as two planes meeting at"
                        + " the middle of the cell, but no vertex was emitted there");
            }
            requireHeight(scene, "middle", heights[1], scene.expectedMiddle());
        }
        requireHeight(scene, "positive edge", heights[2], scene.expectedPositive());

        // The neighbour is the higher rail of its seam and keeps vanilla's flat plane.
        BlockState neighbourState = client.level.getBlockState(neighbour);
        double[] neighbourHeights = profileHeights(client, neighbour, neighbourState, scene.axis(),
                scene.name() + " neighbour");
        double flat = RAIL_LIFT + scene.neighbourSeat();
        requireHeight(scene, "neighbour negative edge", neighbourHeights[0], flat);
        requireHeight(scene, "neighbour positive edge", neighbourHeights[2], flat);
    }

    private static Fixture buildFixture(ServerLevel level, BlockPos origin) {
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-3, -2, -3),
                origin.offset(SCENES.size() * SCENE_SPACING + 3, 4, 3))) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        List<BlockPos> subjects = new ArrayList<>();
        List<BlockPos> neighbours = new ArrayList<>();
        for (int i = 0; i < SCENES.size(); i++) {
            Scene scene = SCENES.get(i);
            BlockPos subject = origin.east(i * SCENE_SPACING).immutable();
            BlockPos neighbour = subject.relative(scene.positive()).above(scene.neighbourStep()).immutable();
            // The neighbour first, so vanilla's own shape update makes the subject a ramp when it must.
            rail(level, neighbour, scene.neighbourSeat(), scene.seatSupports());
            if (scene.bothSides()) {
                rail(level, subject.relative(scene.positive().getOpposite()).above(scene.neighbourStep()),
                        scene.neighbourSeat(), scene.seatSupports());
            }
            rail(level, subject, scene.subjectSeat(), true);
            subjects.add(subject);
            neighbours.add(neighbour);
        }
        return new Fixture(subjects, neighbours);
    }

    /** A rail on stone, seated at {@code dy} (0 authors nothing); the support seated too when asked. */
    private static void rail(ServerLevel level, BlockPos pos, double dy, boolean seatSupport) {
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos, Blocks.RAIL.defaultBlockState(), Block.UPDATE_ALL);
        if (dy != 0.0d) {
            if (seatSupport) {
                SlabAnchorAttachment.writePlacementDy(level, pos.below(), dy);
            }
            SlabAnchorAttachment.writePlacementDy(level, pos, dy);
        }
    }

    /** True once the client holds every rail with vanilla's shape and every authored seat. */
    private static boolean sceneArrived(Level level, Fixture fixture) {
        for (int i = 0; i < SCENES.size(); i++) {
            Scene scene = SCENES.get(i);
            BlockPos subject = fixture.subjects().get(i);
            RailShape neighbourShape = scene.axis() == Direction.Axis.Z ? RailShape.NORTH_SOUTH : RailShape.EAST_WEST;
            if (!railArrived(level, subject, scene.subjectShape(), scene.subjectSeat())
                    || !railArrived(level, fixture.neighbours().get(i), neighbourShape, scene.neighbourSeat())) {
                return false;
            }
            if (scene.bothSides() && !railArrived(level,
                    subject.relative(scene.positive().getOpposite()).above(scene.neighbourStep()),
                    neighbourShape, scene.neighbourSeat())) {
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
     * The drawn height at the rail's negative edge, its middle, and its positive edge: the mean
     * vertex Y over every emitted vertex lying there, relative to the cell floor. The middle is NaN
     * when no vertex lies there (one plane). A vertex anywhere else, or a rail that emits nothing at
     * an edge, is a premise failure.
     */
    private static double[] profileHeights(Minecraft client, BlockPos pos, BlockState state,
                                           Direction.Axis axis, String label) {
        Renderer renderer = Renderer.get();
        if (renderer == null) {
            throw new AssertionError("premise: no renderer API implementation is registered on this client");
        }
        BlockStateModel model = client.getModelManager().getBlockStateModelSet().get(state);
        if (!(model instanceof FabricBlockStateModel fabric)) {
            throw new AssertionError("premise (" + label + "): the rail's model is not a Fabric block-state model: "
                    + model);
        }
        double[] sum = {0.0d, 0.0d, 0.0d};
        int[] count = {0, 0, 0};
        List<String> stray = new ArrayList<>();
        boolean alongZ = axis == Direction.Axis.Z;
        QuadEmitter emitter = renderer.quadEmitter(quad -> {
            for (int i = 0; i < 4; i++) {
                float t = alongZ ? quad.z(i) : quad.x(i);
                int bucket = Math.abs(t) < EDGE ? 0 : Math.abs(t - 0.5f) < EDGE ? 1 : Math.abs(t - 1.0f) < EDGE ? 2 : -1;
                if (bucket < 0) {
                    stray.add("(" + quad.x(i) + ", " + quad.y(i) + ", " + quad.z(i) + ")");
                    continue;
                }
                sum[bucket] += quad.y(i);
                count[bucket]++;
            }
        });
        fabric.emitQuads(emitter, client.level, pos, state, RandomSource.create(42L), direction -> false);
        if (count[0] == 0 || count[2] == 0) {
            throw new AssertionError("premise (" + label + "): the rail at " + pos + " emitted no vertex on its"
                    + " negative edge (" + count[0] + ") or positive edge (" + count[2] + ")");
        }
        if (!stray.isEmpty()) {
            throw new AssertionError("premise (" + label + "): the rail at " + pos + " emitted vertices off its"
                    + " edges and middle, so a mean there is not its drawn height: " + stray);
        }
        return new double[] {
                sum[0] / count[0],
                count[1] == 0 ? NO_MIDDLE : sum[1] / count[1],
                sum[2] / count[2]};
    }

    private static void requireHeight(Scene scene, String where, double actual, double expected) {
        if (Math.abs(actual - expected) > EPS) {
            throw new AssertionError("(" + scene.name() + ") the rail's " + where + " must draw at "
                    + expected + " above its cell floor, got " + actual);
        }
    }
}
