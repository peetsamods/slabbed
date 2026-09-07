package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.network.ManualDyAdjustServer;
import com.slabbed.util.ManualDyEnvelope;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * The manual height nudge, asserted against the REAL handler decisions.
 *
 * <p><b>VENUE (read before adding a row).</b> This JVM runs with the frozen store OFF, so
 * {@code SlabSupport.getYOffset} does NOT consult the store here — it falls through to the geometric
 * lanes. A row that asserts on {@code getYOffset} after a nudge measures those lanes, not this
 * feature, and would be green for the wrong reason. Every row below asserts on
 * {@code SlabAnchorAttachment.rawPlacementDyFact}, which contains no frozen-flag check. The two rows
 * that genuinely need the frozen read — the connector-settle row and the feedback-string row — flip
 * the flag in-process and restore it in a finally, the way the other law tests and the frozen scene
 * fixture do.
 *
 * <p><b>Mock players.</b> {@code makeMockServerPlayer} builds a server player directly, with no
 * connection — so no row may call anything that touches that connection. In particular
 * {@code sendSystemMessage} and the three-argument {@code snapTo} both dereference it, so rows call
 * {@code ManualDyAdjustServer.apply} / {@code describe} and never the notification step, and position
 * mocks with {@code snapTo(Vec3, float, float)}, which is not overridden. Every row positions its
 * mock beside its subject, because the default position would otherwise silently turn the row into a
 * range refusal.
 *
 * <p><b>Rate limit.</b> The handler allows one adjust per player per tick and a row body runs inside a
 * single tick, so any row that nudges twice uses a second mock — each mock has its own identity.
 */
public final class ManualDyAdjustLawTest {

    private static final double EPS = 1.0e-9d;

    // ── fixtures ────────────────────────────────────────────────────────────

    private static ServerPlayer mock(GameTestHelper h, GameType type) {
        if (!(h.makeMockServerPlayer(type) instanceof ServerPlayer player)) {
            throw h.assertionException("GameTest did not provide a server-backed mock player");
        }
        return player;
    }

    /** A mock standing one cell to the east of {@code subject}, comfortably inside interaction range. */
    private static ServerPlayer aimer(GameTestHelper h, GameType type, BlockPos subject) {
        ServerPlayer player = mock(h, type);
        player.snapTo(new Vec3(subject.getX() + 1.5d, subject.getY(), subject.getZ() + 0.5d), 0.0f, 0.0f);
        return player;
    }

    /** Real player placement through the production use-on path — never setBlock plus a hand-rolled write. */
    private static void realPlace(GameTestHelper h, Item item, BlockPos clicked, Direction face) {
        ItemStack stack = new ItemStack(item);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(clicked)
                .add(face.getStepX() * 0.5d, face.getStepY() * 0.5d, face.getStepZ() * 0.5d);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, clicked, false)));
    }

    /** Quiet scenery write: no neighbour notification, so nothing in the scene moves on its own. */
    private static void put(ServerLevel w, BlockPos pos, BlockState state) {
        w.setBlock(pos, state, 2);
    }

    private static void clear(ServerLevel w, BlockPos pos) {
        w.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
    }

    /** Seeds a chosen stored height. The only way to reach a deep or out-of-envelope value on purpose. */
    private static void authorFact(ServerLevel w, BlockPos pos, double dy) {
        SlabAnchorAttachment.writePlacementDy(w, pos, dy);
    }

    private static boolean factPresent(ServerLevel w, BlockPos pos) {
        return SlabAnchorAttachment.rawPlacementDyFact(w, pos).present();
    }

    private static long factBits(ServerLevel w, BlockPos pos) {
        return SlabAnchorAttachment.rawPlacementDyFact(w, pos).rawBits();
    }

    private static double factValue(ServerLevel w, BlockPos pos) {
        return SlabAnchorAttachment.rawPlacementDyFact(w, pos).valueOrNaN();
    }

    private static ManualDyAdjustServer.Result nudge(
            GameTestHelper h, ServerPlayer player, BlockPos pos, int direction
    ) {
        ServerLevel w = h.getLevel();
        return ManualDyAdjustServer.apply(player, pos, direction, Block.getId(w.getBlockState(pos)));
    }

    private static void expect(
            GameTestHelper h, BlockPos pos, ManualDyAdjustServer.Result result,
            ManualDyAdjustServer.Outcome wanted
    ) {
        if (result.outcome() != wanted) {
            throw h.assertionException(pos, "expected " + wanted + ", got " + result.outcome());
        }
    }

    private static void expectFact(GameTestHelper h, BlockPos pos, double wanted) {
        ServerLevel w = h.getLevel();
        if (!factPresent(w, pos)) {
            throw h.assertionException(pos, "expected a stored height of " + wanted + ", found none");
        }
        double actual = factValue(w, pos);
        if (Math.abs(actual - wanted) > EPS) {
            throw h.assertionException(pos, "expected a stored height of " + wanted + ", got " + actual);
        }
    }

    private interface FrozenBody {
        void run();
    }

    /** The two rows that must read through the frozen store run inside this and restore the flag. */
    private static void withFrozen(FrozenBody body) {
        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            body.run();
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previous;
        }
    }

    /** A flat player-placed stone at (2,2,2) with ground under it. Returns the placed cell. */
    private static BlockPos placedOnGround(GameTestHelper h, ServerLevel w) {
        BlockPos ground = h.absolutePos(new BlockPos(2, 1, 2));
        put(w, ground, Blocks.STONE.defaultBlockState());
        realPlace(h, Items.STONE, ground, Direction.UP);
        return ground.above();
    }

    /** The same, with the ground removed afterwards so the support gate cannot be the refusal. */
    private static BlockPos placedOverAir(GameTestHelper h, ServerLevel w) {
        BlockPos subject = placedOnGround(h, w);
        clear(w, subject.below());
        return subject;
    }

    /** A stone cell whose stored height is authored outright — no placement transaction involved. */
    private static BlockPos seededCell(GameTestHelper h, ServerLevel w, BlockPos relative, double dy) {
        BlockPos pos = h.absolutePos(relative);
        put(w, pos, Blocks.STONE.defaultBlockState());
        authorFact(w, pos, dy);
        return pos;
    }

    private static void requireSharedWorldVenue(GameTestHelper h) {
        // Without this the permission rows would be measuring the singleplayer branch, which is
        // permissive for everyone and would make the refusal rows pass for the wrong reason.
        if (ManualDyAdjustServer.effectivelySingleplayer(h.getLevel().getServer())) {
            throw h.assertionException(
                    "premise: this venue must be shared-world shaped or the permission rows measure nothing");
        }
    }

    // ── 1. the premise every other row rests on ─────────────────────────────

    /** Reddened by: making the placement path skip publishing a fact for a zero-height placement. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void flatFullBlockPlacedThroughUseOnCarriesAZeroFact(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos subject = placedOnGround(h, w);
        if (!w.getBlockState(subject).is(Blocks.STONE)) {
            throw h.assertionException(subject, "premise: the use-on placement did not land a stone block");
        }
        expectFact(h, subject, 0.0d);
        h.succeed();
    }

    // ── 2-5. the step itself and its two bounds ─────────────────────────────

    /** Reddened by: changing ManualDyEnvelope.STEP, or dropping the writePlacementDy call in apply. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void lowerStepsTheStoredFactByExactlyOneHalf(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos subject = placedOverAir(h, w);
        expectFact(h, subject, 0.0d);
        ManualDyAdjustServer.Result result = nudge(h, aimer(h, GameType.CREATIVE, subject), subject, -1);
        expect(h, subject, result, ManualDyAdjustServer.Outcome.APPLIED);
        if (factBits(w, subject) != Double.doubleToRawLongBits(-0.5d)) {
            throw h.assertionException(subject, "a lower nudge must store exactly -0.5, got " + factValue(w, subject));
        }
        h.succeed();
    }

    /** Reddened by: inverting the sign the raise key passes into ManualDyEnvelope.step. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void raiseStepsTheStoredFactBackUpBitIdentically(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos subject = placedOnGround(h, w);
        authorFact(w, subject, -1.0d);
        ManualDyAdjustServer.Result result = nudge(h, aimer(h, GameType.CREATIVE, subject), subject, 1);
        expect(h, subject, result, ManualDyAdjustServer.Outcome.APPLIED);
        if (factBits(w, subject) != Double.doubleToRawLongBits(-0.5d)) {
            throw h.assertionException(subject, "a raise nudge must store exactly -0.5, got " + factValue(w, subject));
        }
        h.succeed();
    }

    /** Reddened by: deepening ManualDyEnvelope.MIN_DY, or reporting APPLIED for a no-op step. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void lowerAtTheEnvelopeFloorClampsAndReportsTheFloor(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        if (!(ManualDyEnvelope.MIN_DY < 0.0d) || !Double.isFinite(ManualDyEnvelope.MIN_DY)) {
            throw h.assertionException("premise: the envelope floor must be a finite negative depth");
        }
        BlockPos subject = seededCell(h, w, new BlockPos(2, 3, 2), ManualDyEnvelope.MIN_DY);
        ManualDyAdjustServer.Result result = nudge(h, aimer(h, GameType.CREATIVE, subject), subject, -1);
        expect(h, subject, result, ManualDyAdjustServer.Outcome.CLAMPED_FLOOR);
        expectFact(h, subject, ManualDyEnvelope.MIN_DY);
        h.succeed();
    }

    /** Reddened by: raising ManualDyEnvelope.MAX_DY above flush, which would re-open positive heights. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void raiseAtTheEnvelopeCeilingClampsAndReportsTheCeiling(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos subject = seededCell(h, w, new BlockPos(2, 3, 2), ManualDyEnvelope.MAX_DY);
        ManualDyAdjustServer.Result result = nudge(h, aimer(h, GameType.CREATIVE, subject), subject, 1);
        expect(h, subject, result, ManualDyAdjustServer.Outcome.CLAMPED_CEILING);
        expectFact(h, subject, ManualDyEnvelope.MAX_DY);
        h.succeed();
    }

    // ── 6-7. the two refusals that protect the store ────────────────────────

    /** Reddened by: replacing the out-of-envelope refusal with a clamp into the envelope. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void anOutOfEnvelopeStoredHeightIsRefusedNotSnapped(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos subject = seededCell(h, w, new BlockPos(2, 3, 2), 1.0d);
        long before = factBits(w, subject);

        ManualDyAdjustServer.Result raised = nudge(h, aimer(h, GameType.CREATIVE, subject), subject, 1);
        expect(h, subject, raised, ManualDyAdjustServer.Outcome.DENIED_OUT_OF_ENVELOPE);
        ManualDyAdjustServer.Result lowered = nudge(h, aimer(h, GameType.CREATIVE, subject), subject, -1);
        expect(h, subject, lowered, ManualDyAdjustServer.Outcome.DENIED_OUT_OF_ENVELOPE);

        if (factBits(w, subject) != before) {
            throw h.assertionException(subject,
                    "a refused out-of-envelope height must be left byte-identical, got " + factValue(w, subject));
        }
        h.succeed();
    }

    /** Reddened by: minting a fact for a fact-less cell instead of refusing it. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aCellWithNoRecordedHeightIsRefusedAndMintsNothing(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos subject = h.absolutePos(new BlockPos(2, 3, 2));
        put(w, subject, Blocks.STONE.defaultBlockState());
        if (factPresent(w, subject)) {
            throw h.assertionException(subject, "premise: scenery placed with setBlock must carry no fact");
        }
        ManualDyAdjustServer.Result result = nudge(h, aimer(h, GameType.CREATIVE, subject), subject, -1);
        expect(h, subject, result, ManualDyAdjustServer.Outcome.DENIED_NO_FACT);
        if (factPresent(w, subject)) {
            throw h.assertionException(subject, "a refused fact-less cell must still carry no fact");
        }
        h.succeed();
    }

    // ── 8-9. the support gate ───────────────────────────────────────────────

    /** Reddened by: deleting the supported() check, which would let a block sink into the ground. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void loweringIntoTheSurfaceBelowIsRefused(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos subject = placedOnGround(h, w);
        expectFact(h, subject, 0.0d);
        ManualDyAdjustServer.Result result = nudge(h, aimer(h, GameType.CREATIVE, subject), subject, -1);
        expect(h, subject, result, ManualDyAdjustServer.Outcome.DENIED_NO_SUPPORT);
        expectFact(h, subject, 0.0d);
        h.succeed();
    }

    /** Reddened by: reading the support's grid top instead of its visible (stored) top. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void loweringOntoALoweredSupportIsAllowedDownToItsRealTop(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos support = seededCell(h, w, new BlockPos(2, 2, 2), -0.5d);
        BlockPos subject = seededCell(h, w, new BlockPos(2, 3, 2), 0.0d);

        ManualDyAdjustServer.Result onto = nudge(h, aimer(h, GameType.CREATIVE, subject), subject, -1);
        expect(h, subject, onto, ManualDyAdjustServer.Outcome.APPLIED);
        expectFact(h, subject, -0.5d);

        ManualDyAdjustServer.Result past = nudge(h, aimer(h, GameType.CREATIVE, subject), subject, -1);
        expect(h, subject, past, ManualDyAdjustServer.Outcome.DENIED_NO_SUPPORT);
        expectFact(h, subject, -0.5d);
        expectFact(h, support, -0.5d);
        h.succeed();
    }

    // ── 10-11. what the request must prove about itself ─────────────────────

    /** Reddened by: dropping the palette-state-id comparison from apply(). */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aStaleTargetStateIsRefused(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos subject = placedOverAir(h, w);
        int wrongId = Block.getId(Blocks.DIRT.defaultBlockState());
        if (wrongId == Block.getId(w.getBlockState(subject))) {
            throw h.assertionException(subject, "premise: the control state id must differ from the subject's");
        }
        ManualDyAdjustServer.Result stale = ManualDyAdjustServer.apply(
                aimer(h, GameType.CREATIVE, subject), subject, -1, wrongId);
        expect(h, subject, stale, ManualDyAdjustServer.Outcome.DENIED_STALE_TARGET);
        expectFact(h, subject, 0.0d);

        ManualDyAdjustServer.Result matching = nudge(h, aimer(h, GameType.CREATIVE, subject), subject, -1);
        if (matching.outcome() == ManualDyAdjustServer.Outcome.DENIED_STALE_TARGET) {
            throw h.assertionException(subject, "the matching state id must not be refused as stale");
        }
        h.succeed();
    }

    /** Reddened by: dropping the interaction-range check, or widening its padding past vanilla's. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void anOutOfReachCellIsRefused(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos subject = seededCell(h, w, new BlockPos(6, 6, 6), 0.0d);

        ServerPlayer far = mock(h, GameType.CREATIVE);
        BlockPos corner = h.absolutePos(new BlockPos(0, 1, 0));
        far.snapTo(new Vec3(corner.getX() + 0.5d, corner.getY(), corner.getZ() + 0.5d), 0.0f, 0.0f);
        expect(h, subject, nudge(h, far, subject, -1), ManualDyAdjustServer.Outcome.DENIED_RANGE);
        expectFact(h, subject, 0.0d);

        ManualDyAdjustServer.Result near = nudge(h, aimer(h, GameType.CREATIVE, subject), subject, -1);
        if (near.outcome() == ManualDyAdjustServer.Outcome.DENIED_RANGE) {
            throw h.assertionException(subject, "a mock standing beside the subject must be within reach");
        }
        h.succeed();
    }

    // ── 12-14. the permission ruling (maintainer ruling, 2026-09-06) ────────

    /** Reddened by: dropping the permission gate, or making multiplayerPermitted return true. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void multiplayerSurvivalNonOpIsRefused(GameTestHelper h) {
        requireSharedWorldVenue(h);
        ServerLevel w = h.getLevel();
        BlockPos subject = seededCell(h, w, new BlockPos(2, 3, 2), 0.0d);
        ServerPlayer player = aimer(h, GameType.SURVIVAL, subject);
        if (ManualDyAdjustServer.multiplayerPermitted(player)) {
            throw h.assertionException(subject, "premise: a survival non-op mock must not be permitted");
        }
        expect(h, subject, nudge(h, player, subject, -1), ManualDyAdjustServer.Outcome.DENIED_PERMISSION);
        expectFact(h, subject, 0.0d);
        h.succeed();
    }

    /** Reddened by: removing the creative disjunct from multiplayerPermitted. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void multiplayerCreativePlayerIsPermitted(GameTestHelper h) {
        requireSharedWorldVenue(h);
        ServerLevel w = h.getLevel();
        BlockPos subject = seededCell(h, w, new BlockPos(2, 3, 2), 0.0d);
        ServerPlayer player = aimer(h, GameType.CREATIVE, subject);
        if (!ManualDyAdjustServer.multiplayerPermitted(player)) {
            throw h.assertionException(subject, "a creative player must be permitted on a shared world");
        }
        expect(h, subject, nudge(h, player, subject, -1), ManualDyAdjustServer.Outcome.APPLIED);
        expectFact(h, subject, -0.5d);
        h.succeed();
    }

    /**
     * Reddened by: removing the permission-level disjunct, or moving the threshold off gamemaster in
     * either direction. The op level is pinned EXPLICITLY on both sides of the threshold: the
     * gametest server's default operator grant is level 0, so a bare {@code op(nameAndId)} here mints
     * an operator with no power and proves nothing about the ruling's "level 2 or higher".
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void multiplayerOppedSurvivalPlayerIsPermitted(GameTestHelper h) {
        requireSharedWorldVenue(h);
        ServerLevel w = h.getLevel();
        BlockPos subject = seededCell(h, w, new BlockPos(2, 3, 2), 0.0d);
        ServerPlayer player = aimer(h, GameType.SURVIVAL, subject);
        if (ManualDyAdjustServer.multiplayerPermitted(player)) {
            throw h.assertionException(subject, "premise: the mock must start un-opped");
        }
        PlayerList list = w.getServer().getPlayerList();
        try {
            // Control: one level BELOW the threshold is still refused, and a refused request records
            // no rate-limit tick, so the same mock can be re-tried below.
            list.op(player.nameAndId(), Optional.of(LevelBasedPermissionSet.MODERATOR), Optional.empty());
            if (ManualDyAdjustServer.multiplayerPermitted(player)) {
                throw h.assertionException(subject, "a level-1 operator in survival must not be permitted");
            }
            expect(h, subject, nudge(h, player, subject, -1), ManualDyAdjustServer.Outcome.DENIED_PERMISSION);
            expectFact(h, subject, 0.0d);

            list.deop(player.nameAndId());
            list.op(player.nameAndId(), Optional.of(LevelBasedPermissionSet.GAMEMASTER), Optional.empty());
            if (!ManualDyAdjustServer.multiplayerPermitted(player)) {
                throw h.assertionException(subject, "a level-2 operator in survival must be permitted");
            }
            expect(h, subject, nudge(h, player, subject, -1), ManualDyAdjustServer.Outcome.APPLIED);
            expectFact(h, subject, -0.5d);
        } finally {
            list.deop(player.nameAndId());
        }
        h.succeed();
    }

    // ── 15-16. what a nudge is allowed to touch ─────────────────────────────

    /** Reddened by: widening the write in apply() from one cell to a neighbourhood. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aNudgeMovesNoNeighbourFact(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos subject = seededCell(h, w, new BlockPos(2, 3, 2), 0.0d);
        BlockPos east = seededCell(h, w, new BlockPos(3, 3, 2), -1.0d);
        BlockPos north = seededCell(h, w, new BlockPos(2, 3, 1), -1.5d);
        BlockPos above = seededCell(h, w, new BlockPos(2, 4, 2), -1.0d);
        long eastBefore = factBits(w, east);
        long northBefore = factBits(w, north);
        long aboveBefore = factBits(w, above);

        expect(h, subject, nudge(h, aimer(h, GameType.CREATIVE, subject), subject, -1),
                ManualDyAdjustServer.Outcome.APPLIED);
        expectFact(h, subject, -0.5d);

        if (factBits(w, east) != eastBefore || factBits(w, north) != northBefore
                || factBits(w, above) != aboveBefore) {
            throw h.assertionException(subject, "a nudge must move no neighbour's stored height");
        }
        if (factPresent(w, subject.below())) {
            throw h.assertionException(subject.below(), "a nudge must not mint a fact under the subject");
        }
        h.succeed();
    }

    /**
     * Reddened by: deleting the ConnectorPlacementSettle call from apply(). Runs frozen-ON because the
     * connector rule reads the drawn height, and the drawn height only follows the store with the
     * frozen read enabled.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void connectorArmsAreSettledAfterANudge(GameTestHelper h) {
        withFrozen(() -> {
            ServerLevel w = h.getLevel();
            BlockPos a = h.absolutePos(new BlockPos(2, 3, 2));
            BlockPos b = a.east();
            put(w, b.below(), Blocks.STONE.defaultBlockState());
            put(w, a, Blocks.OAK_FENCE.defaultBlockState());
            put(w, b, Blocks.OAK_FENCE.defaultBlockState());
            authorFact(w, a, 0.0d);
            authorFact(w, b, 0.0d);
            // Bring both ends to the fixed point of vanilla's own arm derivation, so the scene starts
            // joined and only the nudge can break it.
            for (BlockPos cell : new BlockPos[]{a, b, a}) {
                put(w, cell, Block.updateFromNeighbourShapes(w.getBlockState(cell), w, cell));
            }
            if (!w.getBlockState(a).getValue(CrossCollisionBlock.EAST)
                    || !w.getBlockState(b).getValue(CrossCollisionBlock.WEST)) {
                throw h.assertionException(a, "premise: a level fence pair must start joined");
            }

            expect(h, a, nudge(h, aimer(h, GameType.CREATIVE, a), a, -1),
                    ManualDyAdjustServer.Outcome.APPLIED);

            if (w.getBlockState(a).getValue(CrossCollisionBlock.EAST)) {
                throw h.assertionException(a, "the nudged fence must drop its arm across the new step");
            }
            if (w.getBlockState(b).getValue(CrossCollisionBlock.WEST)) {
                throw h.assertionException(b, "the untouched neighbour's arm must be settled too");
            }
        });
        h.succeed();
    }

    // ── 17-18. the reply, and the rate limit ────────────────────────────────

    /** Reddened by: editing any of the four asserted strings, or the applied line splice. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void theFeedbackStringsAreTheOnesSpecified(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos subject = seededCell(h, w, new BlockPos(2, 3, 2), -1.0d);
        ServerPlayer player = aimer(h, GameType.CREATIVE, subject);

        withFrozen(() -> {
            String applied = ManualDyAdjustServer.describe(player, subject,
                    new ManualDyAdjustServer.Result(ManualDyAdjustServer.Outcome.APPLIED, -0.5d, -1.0d));
            if (!applied.startsWith("[slabdy] target=")) {
                throw h.assertionException(subject, "the applied reply must be the shipped row's head: " + applied);
            }
            if (!applied.contains("dy=-1.000")) {
                throw h.assertionException(subject, "the applied reply must carry the new height: " + applied);
            }
        });

        assertReply(h, player, subject, ManualDyAdjustServer.Outcome.CLAMPED_FLOOR,
                "[slabbed] already at the lowest height");
        assertReply(h, player, subject, ManualDyAdjustServer.Outcome.DENIED_PERMISSION,
                "[slabbed] adjusting heights here needs creative mode or op level 2");
        assertReply(h, player, subject, ManualDyAdjustServer.Outcome.DENIED_NO_FACT,
                "[slabbed] that block has no recorded height; break and place it to adjust it");
        h.succeed();
    }

    private static void assertReply(
            GameTestHelper h, ServerPlayer player, BlockPos pos,
            ManualDyAdjustServer.Outcome outcome, String wanted
    ) {
        String actual = ManualDyAdjustServer.describe(
                player, pos, new ManualDyAdjustServer.Result(outcome, Double.NaN, Double.NaN));
        if (!wanted.equals(actual)) {
            throw h.assertionException(pos, "reply for " + outcome + " must be \"" + wanted + "\", got \"" + actual + "\"");
        }
    }

    /** Reddened by: deleting the per-player tick guard, or keying it on something other than identity. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aSecondNudgeInTheSameTickIsRefused(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos first = seededCell(h, w, new BlockPos(2, 3, 2), 0.0d);
        BlockPos second = seededCell(h, w, new BlockPos(4, 3, 2), 0.0d);
        ServerPlayer player = aimer(h, GameType.CREATIVE, first);

        expect(h, first, nudge(h, player, first, -1), ManualDyAdjustServer.Outcome.APPLIED);
        expectFact(h, first, -0.5d);

        player.snapTo(new Vec3(second.getX() + 1.5d, second.getY(), second.getZ() + 0.5d), 0.0f, 0.0f);
        expect(h, second, nudge(h, player, second, -1), ManualDyAdjustServer.Outcome.DENIED_RATE);
        expectFact(h, second, 0.0d);

        // A different player is not rate-limited by the first player's adjust.
        expect(h, second, nudge(h, aimer(h, GameType.CREATIVE, second), second, -1),
                ManualDyAdjustServer.Outcome.APPLIED);
        h.succeed();
    }
}
