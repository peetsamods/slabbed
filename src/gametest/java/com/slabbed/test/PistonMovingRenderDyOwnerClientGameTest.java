package com.slabbed.test;

import com.mojang.blaze3d.vertex.PoseStack;
import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.model.OffsetBlockStateModel;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.blockentity.state.PistonHeadRenderState;
import net.minecraft.client.renderer.feature.MovingBlockFeatureRenderer;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A moving piston body is lowered exactly once. Only two things can apply a stored height to one: the
 * block-entity dispatcher's pose translate and the block model. The chunk mesh cannot, because a moving
 * piston is drawn from its block entity and never from a section mesh.
 *
 * <p>HONEST LIMIT, stated up front rather than in a footnote: this row invokes the shipped draw path
 * itself — the dispatcher's own extract and submit, and the same alternate block renderer the renderer
 * API substitutes inside the moving-block feature renderer — instead of waiting for the frame pipeline
 * to reach the body. Whether the visible-section walk, the section mesh's block-entity snapshot and the
 * frustum actually deliver the body into a drawn frame, and where the final pixels land, stay LIVE
 * observations. What is measured here is the two CONTRIBUTIONS at their production decision points, and
 * that exactly one of them is non-zero. Neither row proves the conjunction alone: the first measures
 * what the pose contributed, the second measures what the model contributed, and only together do they
 * say "lowered exactly once".
 *
 * <p>Do not re-add a capture mixin on the feature renderer for this. A moving body reaches that call
 * only when its section mesh happens to have been recompiled while the body existed, which is a
 * two-tick window racing an asynchronous re-mesh behind a visibility cut — so an instrument there that
 * never fires is the normal case rather than a fault, and cannot be told apart from a real one.
 *
 * <p>OUT OF SCOPE, stated rather than left silently absent: a STICKY retract's pulled bodies. A default
 * piston's retract never runs the piston move capture at all, so the pulled bodies' draw ownership is
 * observed neither here nor anywhere else; the store side of that pull is covered headlessly.
 *
 * <p>This row depends on the shipped frozen-height default being ON in the client test JVM: the stored
 * fact is only returned by the height read under that default. Do not run it with the store forced off.
 */
public final class PistonMovingRenderDyOwnerClientGameTest implements FabricClientGameTest {

    private static final String CLIENT_GAMETEST_PASS =
            "CLIENT_GAMETEST | PistonMovingRenderDyOwnerClientGameTest | PASS";
    private static final double LOWERED = -0.5d;
    /** Vanilla's piston extend block event. */
    private static final int EVENT_EXTEND = 0;
    /** Vanilla's piston contract block event. */
    private static final int EVENT_CONTRACT = 1;
    /** Generous; the poll breaks as soon as the body has come and gone. */
    private static final int POLL_TICKS = 60;
    /** The body must be seen EARLY, not merely at some point: a two-tick animation cannot start late. */
    private static final int FIRST_SIGHTING_BOUND = 5;
    /**
     * Emitted vertex Y is float arithmetic through the emitter chain. A -0.5f shift on the dyadic
     * vertex coordinates these models use is exact, so this only guards a future non-dyadic height.
     */
    private static final float GEOMETRY_EPSILON = 1.0e-6f;

    private record Fixture(BlockPos base, BlockPos head, BlockPos pushPiston, BlockPos pushSource,
                           BlockPos pushLanding) {
    }

    /**
     * One moving-body submission, read from the shipped draw path within a single client tick.
     *
     * @param tick                      poll index the sample was taken at, so "first" is measured
     * @param entityCell                the block entity's own cell: the cell the body is drawn in, and
     *                                  the cell the dispatcher reads a height for
     * @param referenceCell             the cell the moving-body view NAMES — a lighting/tint reference,
     *                                  not the drawn cell
     * @param storedDyAtEntityCell      what the CLIENT holds at the drawn cell in this same sample
     * @param storedDyAtReferenceCell   what the CLIENT holds at the reference cell in this same sample
     * @param poseDy                    the y translation in the matrix the renderer recorded, measured
     *                                  from an identity pose, so it is exactly what this submit added
     * @param animationYOffset          the piston's own animation offset; an EAST rig pins it to 0
     * @param movingViewMinY            lowest emitted vertex Y through the production moving-body view
     * @param ordinaryViewMinY          lowest emitted vertex Y for the SAME model, cell, state and seed
     *                                  read through an ordinary client-level view, in this same sample
     * @param movingViewEmitted         whether the moving-body emission produced any quad at all
     * @param ordinaryViewEmitted       whether the ordinary emission produced any quad at all
     * @param liveModelDyOnMovingView   the model path's own height answer, ignoring the moving-body
     *                                  rule, so a store that had gone blind cannot read as suppression
     * @param appliedModelDyMovingView  what the shared applied-height helper says the model contributed
     *                                  for the moving-body view
     * @param appliedModelDyOrdinary    the same helper's answer for the ordinary view
     */
    private record Body(int tick, BlockPos entityCell, BlockPos referenceCell,
                        double storedDyAtEntityCell, double storedDyAtReferenceCell,
                        double poseDy, float animationYOffset,
                        float movingViewMinY, float ordinaryViewMinY,
                        boolean movingViewEmitted, boolean ordinaryViewEmitted,
                        float liveModelDyOnMovingView,
                        float appliedModelDyMovingView, float appliedModelDyOrdinary) {
    }

    /** One client tick's reading at the watched cell. */
    private record Sample(int tick, boolean bodyPresent, boolean extracted, boolean blockBodySet,
                          List<Body> bodies) {
    }

    /** Everything one phase produced, plus the server's own account of what it did. */
    private record Phase(String name, BlockPos watched, List<Sample> samples, String serverOutcome) {
        List<Body> bodies() {
            List<Body> out = new ArrayList<>();
            for (Sample sample : samples) {
                out.addAll(sample.bodies());
            }
            return out;
        }

        int firstSightingTick() {
            for (Sample sample : samples) {
                if (sample.bodyPresent()) {
                    return sample.tick();
                }
            }
            return -1;
        }
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
                BlockPos origin = player.blockPosition()
                        .relative(player.getDirection(), 5).above(2).immutable();
                return buildFixture(level, origin);
            });

            // STAGING: waiting a tick is not a barrier against the server. Poll until the CLIENT holds
            // both authored heights, so no row can mistake an unsynced fixture for a lost height.
            ctx.waitFor(client -> client.level != null
                    && clientStoredDy(client.level, fixture.base()) == LOWERED
                    && clientStoredDy(client.level, fixture.pushSource()) == LOWERED, 400);

            // Drive every phase first, collecting observations; assert afterwards. An assertion must not
            // stop the world from advancing, or a first-row failure would hide the second row and the
            // "reddens it alone" claim would be unobservable in a single run.
            Phase extending = drive(ctx, singleplayer, fixture.base(), EVENT_EXTEND,
                    fixture.head(), "extending head");
            singleplayer.getServer().runOnServer(server -> unpower(server.overworld(), fixture.base()));
            Phase retracting = drive(ctx, singleplayer, fixture.base(), EVENT_CONTRACT,
                    fixture.base(), "retracting base");
            Phase pushing = drive(ctx, singleplayer, fixture.pushPiston(), EVENT_EXTEND,
                    fixture.pushLanding(), "pushed block destination");

            List<String> failures = new ArrayList<>();
            record(failures, "dispatcherOwnsAMovingBodysHeight",
                    () -> dispatcherOwnsAMovingBodysHeight(extending, pushing));
            record(failures, "movingBodyTakesNoHeightFromTheBlockModel",
                    () -> movingBodyTakesNoHeightFromTheBlockModel(fixture, extending, retracting, pushing));
            if (!failures.isEmpty()) {
                throw new AssertionError(String.join("\n\n", failures));
            }
            Slabbed.LOGGER.info(CLIENT_GAMETEST_PASS);
        }
    }

    private static void record(List<String> failures, String row, Runnable body) {
        try {
            body.run();
        } catch (AssertionError failure) {
            failures.add("ROW " + row + " FAILED: " + failure.getMessage());
        }
    }

    // -- row 1 -----------------------------------------------------------------------------------

    /**
     * The block-entity dispatcher is the ONE owner of a moving body's height, it reads the cell the body
     * is DRAWN in (not the cell the moving-body view names), and it already has the right answer on the
     * first tick the client can draw the body there. A later-correct, first-tick-flat result FAILS: that
     * only moves the visible jump from the end of the animation to the start.
     *
     * <p>The two cells genuinely differ. An extending head's moving-body view names the cell the block
     * came FROM, while the block entity — and therefore the height the dispatcher reads — sits at the
     * cell the body is arriving in.
     *
     * <p>MUTATION that must redden this row alone: delete the pose translate in the block-entity
     * dispatcher offset mixin ({@code BlockEntityOffsetMixin}, the guarded
     * {@code matrices.translate(0.0, yOff, 0.0)}). Every recorded pose y then reads 0.0 while the stored
     * height still reads the lowered value. Nothing else in this repository asserts on that mixin, and
     * the model row below reads emitted vertex geometry rather than the pose, so it stays green.
     *
     * <p>Also reddened, but deliberately NOT the naming mutation because the headless piston carry rows
     * go red on it too: deferring the height publish out of the piston move's return. That failure lands
     * on the SEPARATE stored-height premise below, which names the store rather than the dispatcher, so
     * the two causes are never confused.
     *
     * <p>DEPENDENCY, named so a future narrowing is not rediscovered from scratch: the extend and push
     * phases both keep their destination height across the landing only because of the MOVING_PISTON
     * hand-off keep guard in {@code SlabAnchorAttachment} — a cell handing off from the animation
     * stand-in to a real occupant keeps its height (maintainer ruling, 2026-09-06). Narrow that guard
     * and this row goes red for that reason.
     */
    private void dispatcherOwnsAMovingBodysHeight(Phase extending, Phase pushing) {
        for (Phase phase : List.of(extending, pushing)) {
            requireObserved(phase);
            requireSightedEarly(phase);
            // An extend and a push each carry one body; the piston base body stays null on both.
            requireSubmitCount(phase, 1);
            Body first = phase.bodies().getFirst();
            requireHorizontalRig(phase, first);
            // Premise, separated so a sync/publish regression cannot read as a dispatcher regression.
            requireBits(phase, first.storedDyAtEntityCell(), LOWERED,
                    "premise: on the FIRST tick the client can draw the body at " + first.entityCell()
                            + ", the client must already hold the stored height there");
            for (Body body : phase.bodies()) {
                requireBits(phase, body.poseDy(), body.storedDyAtEntityCell(),
                        "the dispatcher must position a moving body at the height stored for the cell it"
                                + " is DRAWN in (" + body.entityCell() + "), not the cell its view names ("
                                + body.referenceCell() + ")");
            }
        }
    }

    // -- row 2 -----------------------------------------------------------------------------------

    /**
     * A moving body takes NO height from the block model, while an ordinary view of the same block at
     * the same cell still does. Measured as emitted geometry through the shipped path, not as a
     * predicate readback: the alternate block renderer used here is the one the renderer API's own
     * redirect substitutes for the moving-block feature renderer's tesselate call, so this IS the
     * emission the frame would have produced. The retract phase is the double-offset scenario itself —
     * the retracting head and the piston base ride the SAME dispatcher-translated stack, so a model that
     * also applied the stored height would sink the pair twice (maintainer ruling, 2026-09-06).
     *
     * <p>The row also pins the shared applied-height helper ({@code OffsetBlockStateModel.appliedModelDy})
     * to the geometry it claims to describe: the helper must answer zero for the moving-body view, and
     * the difference between the two helper answers must equal the difference the two emissions actually
     * produced. The helper and the emitter therefore cannot drift apart silently.
     *
     * <p>MUTATION that must redden this row alone: make the moving-body view test in
     * {@code OffsetBlockStateModel} answer false. It is the single point both the emission and the
     * applied-height helper read, so the moving emission then takes the ordinary branch, the emitter
     * shifts every vertex by the stored height, and the equality below breaks. The dispatcher row reads
     * only the pose matrix and stays green; no headless row exercises the client model path at all.
     *
     * <p>DEPENDENCY: the retract phase leans on the piston base cell's own height surviving the in-place
     * transform to the animation stand-in, and the extend and push phases lean on the MOVING_PISTON
     * hand-off keep guard in {@code SlabAnchorAttachment}. Narrow either and this row goes red for that
     * reason rather than for a model reason.
     */
    private void movingBodyTakesNoHeightFromTheBlockModel(
            Fixture fixture, Phase extending, Phase retracting, Phase pushing) {
        requireObserved(retracting);
        // Shape premise: on a retract the render state carries BOTH bodies, at the head and the base.
        // Without this, a regression that stopped producing the base body would satisfy every
        // universally-quantified assertion below and still be a real defect.
        requireSubmitCount(retracting, 2);
        Set<BlockPos> retractCells = new LinkedHashSet<>();
        for (Body body : retracting.samples().getFirst().bodies()) {
            retractCells.add(body.referenceCell());
        }
        Set<BlockPos> expectedCells = new LinkedHashSet<>(List.of(fixture.head(), fixture.base()));
        require(retractCells.equals(expectedCells),
                "premise: a retract must submit the head body AND the piston base body; the reference"
                        + " cells were " + retractCells + " rather than " + expectedCells);

        List<Body> all = new ArrayList<>();
        all.addAll(extending.bodies());
        all.addAll(retracting.bodies());
        all.addAll(pushing.bodies());
        require(!all.isEmpty(), "premise: no moving body was observed in any phase");

        boolean sawSomethingToSuppress = false;
        for (Body body : all) {
            require(body.movingViewEmitted() && body.ordinaryViewEmitted(),
                    "premise: the block model emitted no geometry for " + body.referenceCell()
                            + " (moving=" + body.movingViewEmitted()
                            + " ordinary=" + body.ordinaryViewEmitted() + "), so this row would be"
                            + " measuring nothing");
            // The whole claim, in one equality: for an ordinary view the model applied exactly the
            // stored height; for the moving view it applied exactly zero.
            float expectedOrdinary = body.movingViewMinY() + (float) body.storedDyAtReferenceCell();
            require(Math.abs(body.ordinaryViewMinY() - expectedOrdinary) <= GEOMETRY_EPSILON,
                    "a moving body must take no height from the block model while an ordinary view of"
                            + " the same block at " + body.referenceCell() + " takes "
                            + body.storedDyAtReferenceCell() + ": moving emitted minY="
                            + body.movingViewMinY() + ", ordinary emitted minY="
                            + body.ordinaryViewMinY() + ", expected " + expectedOrdinary);
            // The shared applied-height helper must agree with the geometry that was actually emitted,
            // so it stays a live description of this path rather than an unread second opinion.
            requireFloatBits(body.appliedModelDyMovingView(), 0.0f,
                    "the applied-height helper must answer zero for a moving-body view at "
                            + body.referenceCell());
            float measuredDelta = body.ordinaryViewMinY() - body.movingViewMinY();
            float helperDelta = body.appliedModelDyOrdinary() - body.appliedModelDyMovingView();
            require(Math.abs(measuredDelta - helperDelta) <= GEOMETRY_EPSILON,
                    "the applied-height helper must agree with the geometry the model emitted at "
                            + body.referenceCell() + ": emitted difference " + measuredDelta
                            + " against helper difference " + helperDelta + " (moving="
                            + body.appliedModelDyMovingView() + " ordinary="
                            + body.appliedModelDyOrdinary() + ")");
            if (body.storedDyAtReferenceCell() != 0.0d) {
                sawSomethingToSuppress = true;
                // Non-vacuity, stated twice on purpose: the model function itself must agree that there
                // WAS a height here, so a store that had gone blind cannot read as suppression.
                require(body.liveModelDyOnMovingView() != 0.0f,
                        "premise: at " + body.referenceCell() + " the model path must see a non-zero"
                                + " height to suppress, or this row proves nothing");
            }
        }
        require(sawSomethingToSuppress,
                "premise: every observed moving body referenced a cell with no stored height, so the"
                        + " moving-body rule had nothing to suppress anywhere in this run");
    }

    // -- driving and observing -------------------------------------------------------------------

    /**
     * Fires the piston through the SERVER's block-event queue and samples every client tick until the
     * body has come and gone.
     *
     * <p>The queue is not a detail. The server level's block-event call enqueues; draining that queue is
     * what broadcasts the block-event packet, and that packet is the ONLY thing that makes the client
     * build its own moving-piston block entity — the moving-piston block's block-entity factory answers
     * null, so the synced block state alone creates nothing to draw. Triggering the event on the block
     * state directly moves the blocks on the server and tells the client nothing. Do not re-add a direct
     * trigger here.
     *
     * <p>Sampling every tick rather than once after a fixed wait: a client task runs after that
     * iteration's packets and before its tick and frame, so the first sample that sees the block entity
     * is at or before the first frame the body could be drawn in.
     */
    private static Phase drive(ClientGameTestContext ctx, TestSingleplayerContext singleplayer,
                               BlockPos pistonPos, int event, BlockPos watched, String name) {
        String before = singleplayer.getServer().computeOnServer(server ->
                describe(server.overworld(), pistonPos));
        // The event is refused silently when the signal does not match the direction: an extend needs a
        // live signal, a contract needs none, and a refused event broadcasts nothing at all. Assert the
        // precondition so a fixture fault never reports as "the draw path produced nothing".
        singleplayer.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            BlockState state = level.getBlockState(pistonPos);
            boolean signal = level.hasNeighborSignal(pistonPos);
            boolean extended = state.hasProperty(BlockStateProperties.EXTENDED)
                    && state.getValue(BlockStateProperties.EXTENDED);
            if (event == EVENT_EXTEND && (!signal || extended)) {
                throw new AssertionError("fixture: an extend needs an unextended, powered piston at "
                        + pistonPos + "; " + describe(level, pistonPos));
            }
            if (event == EVENT_CONTRACT && signal) {
                throw new AssertionError("fixture: a contract needs the signal gone at " + pistonPos
                        + "; " + describe(level, pistonPos));
            }
            level.blockEvent(pistonPos, state.getBlock(), event, Direction.EAST.get3DDataValue());
        });

        List<Sample> samples = new ArrayList<>();
        boolean seenAny = false;
        for (int tick = 0; tick < POLL_TICKS; tick++) {
            ctx.waitTick();
            final int index = tick;
            Sample sample = ctx.computeOnClient(client -> readMovingBody(client, watched, index));
            if (sample.bodyPresent()) {
                seenAny = true;
                samples.add(sample);
            } else if (seenAny) {
                break;  // the block entity has come and gone — the animation is over
            }
        }
        String after = singleplayer.getServer().computeOnServer(server ->
                describe(server.overworld(), pistonPos));
        return new Phase(name, watched, samples, "server before=" + before + " after=" + after);
    }

    /**
     * The production draw decisions for the moving body at {@code cell} in one client tick.
     *
     * <p>Presence is keyed on the BLOCK ENTITY, never on whether anything was submitted: a present body
     * that submits nothing is a real anomaly and must fail loudly, not read as "the animation ended" and
     * silently truncate the window the first-tick assertion reads from.
     *
     * <p>The dispatcher's own extract entry point is used rather than a hand-rolled one: it applies the
     * shipped gates — renderer lookup, block-entity type validity, the off-screen flag and the
     * view-distance test against the real camera position. A refusal is recorded and reported rather
     * than swallowed.
     *
     * <p>The pose starts at identity, so the y translation the renderer records IS what this submit call
     * added: the dispatcher offset plus the piston's own animation offset, and the rows pin the latter to
     * zero by using an EAST-facing rig.
     */
    private static Sample readMovingBody(Minecraft client, BlockPos cell, int tick) {
        ClientLevel level = client.level;
        if (level == null || !(level.getBlockEntity(cell) instanceof PistonMovingBlockEntity moving)) {
            return new Sample(tick, false, false, false, List.of());
        }
        BlockEntityRenderDispatcher dispatcher = client.getBlockEntityRenderDispatcher();
        BlockEntityRenderState extracted = dispatcher.tryExtractRenderState(moving, 1.0f, null, false);
        if (!(extracted instanceof PistonHeadRenderState pistonState)) {
            return new Sample(tick, true, false, false, List.of());
        }
        CameraRenderState camera = new CameraRenderState();
        camera.blockPos = cell;
        camera.pos = Vec3.atCenterOf(cell);
        SubmitNodeStorage storage = new SubmitNodeStorage();
        dispatcher.submit(pistonState, new PoseStack(), storage, camera);

        List<Body> bodies = new ArrayList<>();
        for (MovingBlockFeatureRenderer.Submit submit : movingBlockSubmits(storage)) {
            MovingBlockRenderState view = submit.movingBlockRenderState();
            if (view == null || view.blockPos == null || view.blockState == null) {
                continue;
            }
            BlockPos reference = view.blockPos.immutable();
            BlockState drawnState = view.blockState;
            long seed = drawnState.getSeed(view.randomSeedPos);
            float[] movingY = emitMinY(view, reference, drawnState, seed);
            float[] ordinaryY = emitMinY(level, reference, drawnState, seed);
            bodies.add(new Body(tick, cell, reference,
                    clientStoredDy(level, cell), clientStoredDy(level, reference),
                    // JOML names cells column-then-row: the fourth column is the translation, and
                    // m31 is its Y component (m32 would be Z).
                    submit.pose().m31(), pistonState.yOffset,
                    movingY[0], ordinaryY[0], movingY[1] != 0.0f, ordinaryY[1] != 0.0f,
                    OffsetBlockStateModel.liveModelDy(view, reference, drawnState),
                    OffsetBlockStateModel.appliedModelDy(view, reference, drawnState),
                    OffsetBlockStateModel.appliedModelDy(level, reference, drawnState)));
        }
        return new Sample(tick, true, true, pistonState.block != null, bodies);
    }

    /** Every moving-block submission the collector received; the storage is drained once and dropped. */
    private static List<MovingBlockFeatureRenderer.Submit> movingBlockSubmits(SubmitNodeStorage storage) {
        List<MovingBlockFeatureRenderer.Submit> out = new ArrayList<>();
        FeatureRenderPhase.Output sink = (node, translucent) -> {
            if (node instanceof MovingBlockFeatureRenderer.Submit submit) {
                out.add(submit);
            }
        };
        // Draining removes each collection it visits, so this storage must never be read twice.
        storage.drainPhases(phase -> phase.sortInto(sink));
        return out;
    }

    /**
     * The lowest vertex Y the block model emits for {@code view}, and whether it emitted at all.
     *
     * <p>This is the shipped moving-body emission, not a lookalike: the renderer API's own mixin
     * redirects the moving-block feature renderer's tesselate call to this alternate renderer, so the
     * arguments below are the arguments that path passes. Ambient occlusion is off because it changes
     * shading rather than vertex positions; culling is off because the production call passes it off too.
     *
     * @return {@code {minY, emittedFlag}} — index 1 is 1.0f when at least one quad was emitted
     */
    private static float[] emitMinY(BlockAndTintGetter view, BlockPos pos, BlockState state, long seed) {
        Renderer renderer = Renderer.get();
        if (renderer == null) {
            throw new AssertionError("premise: no renderer API implementation is registered on this"
                    + " client, so the moving-body emission cannot be measured");
        }
        float[] result = {Float.POSITIVE_INFINITY, 0.0f};
        QuadEmitter emitter = renderer.quadEmitter(quad -> {
            result[1] = 1.0f;
            for (int i = 0; i < 4; i++) {
                result[0] = Math.min(result[0], quad.y(i));
            }
        });
        BlockStateModel model =
                Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        renderer.altModelBlockRenderer(false, false, Minecraft.getInstance().getBlockColors())
                .tesselateBlock(emitter, 0.0f, 0.0f, 0.0f, view, pos, state, model, seed);
        return result;
    }

    // -- shared assertions -----------------------------------------------------------------------

    /** An empty phase must FAIL, and must say whether the fixture or the draw path is at fault. */
    private static void requireObserved(Phase phase) {
        if (phase.samples().isEmpty()) {
            throw new AssertionError("instrument never fired (" + phase.name() + "): no moving piston"
                    + " block entity ever existed at " + phase.watched() + " within " + POLL_TICKS
                    + " client ticks. " + phase.serverOutcome());
        }
        for (Sample sample : phase.samples()) {
            if (!sample.extracted()) {
                throw new AssertionError("(" + phase.name() + ") the block entity was present at "
                        + phase.watched() + " on tick " + sample.tick() + " but the dispatcher produced"
                        + " no piston head render state — a renderer, type-validity or view-distance"
                        + " refusal, not an animation that ended. " + phase.serverOutcome());
            }
            if (sample.bodies().isEmpty()) {
                throw new AssertionError("(" + phase.name() + ") the block entity was present at "
                        + phase.watched() + " on tick " + sample.tick() + " and extracted (moving body"
                        + " set=" + sample.blockBodySet() + ") but submitted no moving body. "
                        + phase.serverOutcome());
            }
        }
    }

    /** A two-tick animation cannot begin late; a late first sighting means the window was not the first. */
    private static void requireSightedEarly(Phase phase) {
        int first = phase.firstSightingTick();
        if (first < 0 || first > FIRST_SIGHTING_BOUND) {
            throw new AssertionError("(" + phase.name() + ") the body was first seen on poll tick "
                    + first + ", past the " + FIRST_SIGHTING_BOUND + "-tick bound, so the sample this"
                    + " row calls \"first\" may not be the first drawable tick. " + phase.serverOutcome());
        }
    }

    /** The submission SHAPE, so a body that stopped being produced cannot pass by absence. */
    private static void requireSubmitCount(Phase phase, int expected) {
        for (Sample sample : phase.samples()) {
            if (sample.bodies().size() != expected) {
                throw new AssertionError("(" + phase.name() + ") expected " + expected
                        + " moving-body submission(s) per tick, got " + sample.bodies().size()
                        + " on tick " + sample.tick() + ". " + phase.serverOutcome());
            }
        }
    }

    /**
     * The rig faces EAST, so no animation offset may hide in the y translation. This is provable, not
     * hopeful — the piston's own y offset is its direction's vertical step times the extended progress,
     * and EAST's vertical step is 0 — but it is asserted anyway so a wrong assumption fails visibly
     * instead of quietly contaminating the dispatcher reading.
     */
    private static void requireHorizontalRig(Phase phase, Body body) {
        require(body.animationYOffset() == 0.0f,
                "(" + phase.name() + ") premise: an EAST-facing rig must carry no animation y offset,"
                        + " got " + body.animationYOffset());
    }

    private static void requireBits(Phase phase, double actual, double expected, String message) {
        if (Double.doubleToRawLongBits(actual) != Double.doubleToRawLongBits(expected)) {
            throw new AssertionError("(" + phase.name() + ") " + message + ": read " + actual
                    + " rather than " + expected + ". " + phase.serverOutcome());
        }
    }

    private static void requireFloatBits(float actual, float expected, String message) {
        if (Float.floatToRawIntBits(actual) != Float.floatToRawIntBits(expected)) {
            throw new AssertionError(message + ": read " + actual + " rather than " + expected);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static double clientStoredDy(ClientLevel level, BlockPos pos) {
        return SlabSupport.getYOffset(level, pos, level.getBlockState(pos));
    }

    // -- fixture and server-side drivers ----------------------------------------------------------

    private static Fixture buildFixture(ServerLevel level, BlockPos origin) {
        // Bounded so the player's own cell is never inside it, whichever way they happen to face: the
        // origin is five cells ahead of them and this box reaches at most four cells further.
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-1, -1, -4), origin.offset(4, 3, 1))) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            SlabAnchorAttachment.removeAnchor(level, p);
        }

        BlockPos base = origin;
        BlockPos head = base.east();
        level.setBlock(base, facingEast(Blocks.PISTON), Block.UPDATE_CLIENTS);
        SlabAnchorAttachment.writePlacementDy(level, base, LOWERED);

        // Three cells away, so no neighbour update from the first piston's own extend (which reaches one
        // cell) can set this one off before its own phase fires.
        BlockPos pushPiston = origin.north(3);
        BlockPos pushSource = pushPiston.east();
        BlockPos pushLanding = pushSource.east();
        level.setBlock(pushPiston, facingEast(Blocks.PISTON), Block.UPDATE_CLIENTS);
        level.setBlock(pushSource, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        SlabAnchorAttachment.writePlacementDy(level, pushSource, LOWERED);

        // Both pistons stay powered for the whole run; only the base is unpowered, once, to retract it.
        power(level, base);
        power(level, pushPiston);
        return new Fixture(base.immutable(), head.immutable(), pushPiston.immutable(),
                pushSource.immutable(), pushLanding.immutable());
    }

    private static BlockState facingEast(Block kind) {
        return kind.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST);
    }

    /**
     * Powers the piston WITHOUT a neighbour update, so vanilla queues no block event of its own and the
     * fired event is the only move in the phase. The piston reads its signal live from the world, so a
     * quiet write is still seen by the event's own signal check.
     */
    private static void power(ServerLevel level, BlockPos pistonPos) {
        level.setBlock(pistonPos.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(),
                Block.UPDATE_CLIENTS);
    }

    private static void unpower(ServerLevel level, BlockPos pistonPos) {
        level.setBlock(pistonPos.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    /** The server's own account of the piston, quoted in every failure so fixture faults are visible. */
    private static String describe(ServerLevel level, BlockPos pistonPos) {
        BlockState state = level.getBlockState(pistonPos);
        return "[" + pistonPos + " block=" + state.getBlock()
                + " extended=" + (state.hasProperty(BlockStateProperties.EXTENDED)
                        ? String.valueOf(state.getValue(BlockStateProperties.EXTENDED)) : "n/a")
                + " signal=" + level.hasNeighborSignal(pistonPos)
                + " storedDy=" + SlabAnchorAttachment.storedPlacementDy(level, pistonPos) + "]";
    }
}
