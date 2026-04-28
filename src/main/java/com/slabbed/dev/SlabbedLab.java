package com.slabbed.dev;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.slabbed.debug.BsFbLiveTrace;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * Dev-only bootstrap for Slabbed Lab.
 *
 * /slablab                              — status probe
 * /slablab fixture basic                — places a 3-lane repro pad
 * /slablab fixture clear                — removes the fixture at the deterministic origin
 * /slablab fixture reset                — clears then rebuilds the fixture
 * /slablab action break-support [lane]    — removes support block(s), triggers neighbor updates
 * /slablab action restore-support [lane]  — restores support block(s), triggers neighbor updates
 * /slablab action neighbor-update [lane]  — place+remove stone pulse south of candidate cell(s)
 * /slablab status [lane]                  — read-only report: support/candidate/pulse state per lane
 * /slablab action sequence support-cycle  — deterministic 6-step support-loss repro sequence
 *
 * Optional lane selector: full | bottom_slab | top_slab (omit for all lanes)
 */
public final class SlabbedLab {

    private SlabbedLab() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                registerAll(dispatcher));
    }

    private static void registerAll(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                literal("slablab")
                        .requires(src -> src.getPermissions().hasPermission(
                                new Permission.Level(PermissionLevel.GAMEMASTERS)))
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(
                                    () -> Text.literal("[slablab] slabbed lab bootstrap ready."),
                                    false
                            );
                            return 1;
                        })
                        .then(literal("fixture")
                                .then(literal("basic")
                                        .executes(SlabbedLab::placeBasicFixture))
                                .then(literal("clear")
                                        .executes(SlabbedLab::clearFixture))
                                .then(literal("reset")
                                        .executes(SlabbedLab::resetFixture)))
                        .then(literal("status")
                                .executes(ctx -> statusFixture(ctx, null))
                                .then(literal("full")
                                        .executes(ctx -> statusFixture(ctx, "full")))
                                .then(literal("bottom_slab")
                                        .executes(ctx -> statusFixture(ctx, "bottom_slab")))
                                .then(literal("top_slab")
                                        .executes(ctx -> statusFixture(ctx, "top_slab"))))
                        .then(literal("action")
                                .then(literal("break-support")
                                        .executes(ctx -> actionBreakSupport(ctx, null))
                                        .then(literal("full")
                                                .executes(ctx -> actionBreakSupport(ctx, "full")))
                                        .then(literal("bottom_slab")
                                                .executes(ctx -> actionBreakSupport(ctx, "bottom_slab")))
                                        .then(literal("top_slab")
                                                .executes(ctx -> actionBreakSupport(ctx, "top_slab"))))
                                .then(literal("restore-support")
                                        .executes(ctx -> actionRestoreSupport(ctx, null))
                                        .then(literal("full")
                                                .executes(ctx -> actionRestoreSupport(ctx, "full")))
                                        .then(literal("bottom_slab")
                                                .executes(ctx -> actionRestoreSupport(ctx, "bottom_slab")))
                                        .then(literal("top_slab")
                                                .executes(ctx -> actionRestoreSupport(ctx, "top_slab"))))
                                .then(literal("inspect")
                                        .executes(ctx -> actionInspect(ctx, null))
                                        .then(literal("full")
                                                .executes(ctx -> actionInspect(ctx, "full")))
                                        .then(literal("bottom_slab")
                                                .executes(ctx -> actionInspect(ctx, "bottom_slab")))
                                        .then(literal("top_slab")
                                                .executes(ctx -> actionInspect(ctx, "top_slab"))))
                                .then(literal("neighbor-update")
                                        .executes(ctx -> actionNeighborUpdate(ctx, null))
                                        .then(literal("full")
                                                .executes(ctx -> actionNeighborUpdate(ctx, "full")))
                                        .then(literal("bottom_slab")
                                                .executes(ctx -> actionNeighborUpdate(ctx, "bottom_slab")))
                                        .then(literal("top_slab")
                                                .executes(ctx -> actionNeighborUpdate(ctx, "top_slab"))))
                                .then(literal("sequence")
                                        .then(literal("support-cycle")
                                                .executes(ctx -> actionSupportCycle(ctx, null))
                                                .then(literal("full")
                                                        .executes(ctx -> actionSupportCycle(ctx, "full")))
                                                .then(literal("bottom_slab")
                                                        .executes(ctx -> actionSupportCycle(ctx, "bottom_slab")))
                                                .then(literal("top_slab")
                                                        .executes(ctx -> actionSupportCycle(ctx, "top_slab"))))))
        );
    }

    // -------------------------------------------------------------------------
    // Shared origin — all fixture and action commands must use this method.
    // Mirrors the audit runner's offset style: 1 below feet, 3 in +Z.
    // -------------------------------------------------------------------------

    private static BlockPos fixtureOrigin(ServerCommandSource source) {
        return BlockPos.ofFloored(source.getPosition()).add(0, -1, 3);
    }

    // -------------------------------------------------------------------------
    // Fixture command handlers
    // -------------------------------------------------------------------------

    private static int placeBasicFixture(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();
        BlockPos origin = fixtureOrigin(source);

        SlabbedLabFixtures.rememberOrigin(origin);

        SlabbedLabFixtures.PlaceResult result = SlabbedLabFixtures.placeBasicFixture(world, origin);
        if (!result.ok()) {
            source.sendError(Text.literal("[slablab] " + result.error()));
            return 0;
        }

        StringBuilder msg = new StringBuilder("[slablab] basic fixture placed (dev-only).\n");
        for (Map.Entry<String, BlockPos> e : result.positions().entrySet()) {
            msg.append("  ").append(e.getKey()).append(" supportPos=").append(e.getValue().toShortString())
               .append(" fullPos=").append(e.getValue().up().toShortString()).append("\n");
        }
        msg.append("  Expected blocks: FULL=stone, BOTTOM_SLAB=bottom slab, TOP_SLAB=top slab.");

        String finalMsg = msg.toString();
        source.sendFeedback(() -> Text.literal(finalMsg), false);
        return 1;
    }

    private static int actionInspect(CommandContext<ServerCommandSource> ctx, String lane) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();
        BlockPos origin = SlabbedLabFixtures.activeOriginOr(fixtureOrigin(source));

        String laneName = lane == null ? "bottom_slab" : lane;
        String details = SlabbedLabFixtures.describeLaneInspection(world, origin, laneName);
        source.sendFeedback(() -> Text.literal("[slablab] inspect (dev-only). origin=" + origin.toShortString() + " " + details), false);

        // BS-FB Live Trace: pair the chat report with server+client log lines for the same fullPos.
        if (BsFbLiveTrace.ENABLED) {
            BlockPos supportPos = SlabbedLabFixtures.laneSupportPos(origin, laneName);
            if (supportPos != null) {
                BlockPos fullPos = supportPos.up();
                String label = "INSPECT_" + laneName.toUpperCase();
                BsFbLiveTrace.capture(world, supportPos, fullPos, label);
                BsFbLiveTrace.captureClient(supportPos, fullPos, label);
            }
        }

        return 1;
    }

    private static int clearFixture(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();
        BlockPos origin = SlabbedLabFixtures.activeOriginOr(fixtureOrigin(source));

        SlabbedLabFixtures.PlaceResult result = SlabbedLabFixtures.clearBasicFixture(world, origin);
        if (!result.ok()) {
            source.sendError(Text.literal("[slablab] " + result.error()));
            return 0;
        }

        StringBuilder msg = new StringBuilder("[slablab] basic fixture cleared (dev-only).\n");
        for (Map.Entry<String, BlockPos> e : result.positions().entrySet()) {
            msg.append("  ").append(e.getKey()).append(" supportPos=").append(e.getValue().toShortString())
               .append(" removed\n");
        }
        msg.append("  origin: ").append(origin.toShortString()).append(".");

        String finalMsg = msg.toString();
        source.sendFeedback(() -> Text.literal(finalMsg), false);
        return 1;
    }

    private static int resetFixture(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();
        BlockPos origin = fixtureOrigin(source);

        // Step 1: verify and clear existing fixture.
        SlabbedLabFixtures.PlaceResult clearResult = SlabbedLabFixtures.clearBasicFixture(world, origin);
        if (!clearResult.ok()) {
            source.sendError(Text.literal("[slablab] reset aborted — clear failed: " + clearResult.error()));
            return 0;
        }

        // Step 2: rebuild fixture at the same origin.
        SlabbedLabFixtures.rememberOrigin(origin);
        SlabbedLabFixtures.PlaceResult placeResult = SlabbedLabFixtures.placeBasicFixture(world, origin);
        if (!placeResult.ok()) {
            source.sendError(Text.literal("[slablab] reset: cleared but rebuild failed: " + placeResult.error()));
            return 0;
        }

        StringBuilder msg = new StringBuilder("[slablab] basic fixture reset (dev-only).\n");
        for (Map.Entry<String, BlockPos> e : placeResult.positions().entrySet()) {
            msg.append("  ").append(e.getKey()).append(" supportPos=").append(e.getValue().toShortString())
               .append(" fullPos=").append(e.getValue().up().toShortString()).append("\n");
        }
        msg.append("  Expected blocks: FULL=stone, BOTTOM_SLAB=bottom slab, TOP_SLAB=top slab.");

        String finalMsg = msg.toString();
        source.sendFeedback(() -> Text.literal(finalMsg), false);
        return 1;
    }

    // -------------------------------------------------------------------------
    // Action command handlers
    // -------------------------------------------------------------------------

    private static int actionBreakSupport(CommandContext<ServerCommandSource> ctx, String lane) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();
        BlockPos origin = SlabbedLabFixtures.activeOriginOr(fixtureOrigin(source));

        SlabbedLabFixtures.PlaceResult result = SlabbedLabFixtures.breakSupport(world, origin, lane);
        if (!result.ok()) {
            source.sendError(Text.literal("[slablab] " + result.error()));
            return 0;
        }

        StringBuilder msg = new StringBuilder("[slablab] break-support (dev-only).\n");
        for (Map.Entry<String, BlockPos> e : result.positions().entrySet()) {
            msg.append("  ").append(e.getKey()).append(" supportPos=").append(e.getValue().toShortString())
               .append(" fullPos=").append(e.getValue().up().toShortString())
               .append(" support removed\n");
        }
        msg.append("  Neighbor updates triggered.");

        String finalMsg = msg.toString();
        source.sendFeedback(() -> Text.literal(finalMsg), false);
        return 1;
    }

    private static int actionRestoreSupport(CommandContext<ServerCommandSource> ctx, String lane) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();
        BlockPos origin = SlabbedLabFixtures.activeOriginOr(fixtureOrigin(source));

        SlabbedLabFixtures.PlaceResult result = SlabbedLabFixtures.restoreSupport(world, origin, lane);
        if (!result.ok()) {
            source.sendError(Text.literal("[slablab] " + result.error()));
            return 0;
        }

        StringBuilder msg = new StringBuilder("[slablab] restore-support (dev-only).\n");
        for (Map.Entry<String, BlockPos> e : result.positions().entrySet()) {
            msg.append("  ").append(e.getKey()).append(" supportPos=").append(e.getValue().toShortString())
               .append(" fullPos=").append(e.getValue().up().toShortString())
               .append(" support restored\n");
        }
        msg.append("  Neighbor updates triggered.");

        String finalMsg = msg.toString();
        source.sendFeedback(() -> Text.literal(finalMsg), false);
        return 1;
    }

    private static int actionNeighborUpdate(CommandContext<ServerCommandSource> ctx, String lane) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();
        BlockPos origin = SlabbedLabFixtures.activeOriginOr(fixtureOrigin(source));

        SlabbedLabFixtures.PlaceResult result = SlabbedLabFixtures.neighborUpdatePulse(world, origin, lane);
        if (!result.ok()) {
            source.sendError(Text.literal("[slablab] " + result.error()));
            return 0;
        }

        StringBuilder msg = new StringBuilder("[slablab] neighbor-update (dev-only).\n");
        for (Map.Entry<String, BlockPos> e : result.positions().entrySet()) {
            msg.append("  ").append(e.getKey()).append(" candidatePos=").append(e.getValue().toShortString())
               .append(" pulsePos=").append(e.getValue().south().toShortString())
               .append(" candidate pulsed\n");
        }
        msg.append("  Pulse: stone placed+removed south of each candidate cell (NOTIFY_ALL).");

        String finalMsg = msg.toString();
        source.sendFeedback(() -> Text.literal(finalMsg), false);
        return 1;
    }

    // -------------------------------------------------------------------------
    // Sequence command handlers
    // -------------------------------------------------------------------------

    /**
     * Runs a deterministic 6-step support-loss repro cycle on the targeted lane(s).
     *
     * <p>Step order:
     * <ol>
     *   <li>initial-status   — snapshot via {@link SlabbedLabFixtures#queryStatus}
     *   <li>break-support    — {@link SlabbedLabFixtures#breakSupport}
     *   <li>neighbor-update  — {@link SlabbedLabFixtures#neighborUpdatePulse}
     *   <li>post-break status— snapshot via {@link SlabbedLabFixtures#queryStatus}
     *   <li>restore-support  — {@link SlabbedLabFixtures#restoreSupport}
     *   <li>final status     — snapshot via {@link SlabbedLabFixtures#queryStatus}
     * </ol>
     *
     * <p>Aborts on the first failure. No partial state is shown on abort; the error
     * message identifies the failed step and the underlying reason.
     */
    private static int actionSupportCycle(CommandContext<ServerCommandSource> ctx, String lane) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();
        BlockPos origin = fixtureOrigin(source);

        String laneLabel = (lane != null) ? lane.toUpperCase() : "ALL";

        // Step 1: initial status — also validates the lane selector (returns null for unknown lane).
        List<SlabbedLabFixtures.LaneStatus> initStatus = SlabbedLabFixtures.queryStatus(world, origin, lane);
        if (initStatus == null) {
            source.sendError(Text.literal("[slablab] unknown lane '" + lane + "'. Valid: full, bottom_slab, top_slab."));
            return 0;
        }

        // Step 2: break-support.
        SlabbedLabFixtures.PlaceResult breakResult = SlabbedLabFixtures.breakSupport(world, origin, lane);
        if (!breakResult.ok()) {
            source.sendError(Text.literal("[slablab] support-cycle[" + laneLabel + "] failed at break-support: " + breakResult.error()));
            return 0;
        }

        // Step 3: neighbor-update pulse.
        SlabbedLabFixtures.PlaceResult pulseResult = SlabbedLabFixtures.neighborUpdatePulse(world, origin, lane);
        if (!pulseResult.ok()) {
            source.sendError(Text.literal("[slablab] support-cycle[" + laneLabel + "] failed at neighbor-update: " + pulseResult.error()));
            return 0;
        }

        // Step 4: intermediate status snapshot.
        List<SlabbedLabFixtures.LaneStatus> midStatus = SlabbedLabFixtures.queryStatus(world, origin, lane);

        // Step 5: restore-support.
        SlabbedLabFixtures.PlaceResult restoreResult = SlabbedLabFixtures.restoreSupport(world, origin, lane);
        if (!restoreResult.ok()) {
            source.sendError(Text.literal("[slablab] support-cycle[" + laneLabel + "] failed at restore-support: " + restoreResult.error()));
            return 0;
        }

        // Step 6: final status snapshot.
        List<SlabbedLabFixtures.LaneStatus> finalStatus = SlabbedLabFixtures.queryStatus(world, origin, lane);

        // All steps succeeded — build the full report.
        StringBuilder msg = new StringBuilder("[slablab] support-cycle[")
                .append(laneLabel).append("] complete (dev-only). origin: ").append(origin.toShortString()).append("\n");

        msg.append("  [1/6] initial-status\n");
        appendStatusLines(msg, initStatus);

        msg.append("  [2/6] break-support\n");
        for (Map.Entry<String, BlockPos> e : breakResult.positions().entrySet()) {
            msg.append("    ").append(e.getKey()).append(" @ ").append(e.getValue().toShortString()).append(" removed\n");
        }

        msg.append("  [3/6] neighbor-update\n");
        for (Map.Entry<String, BlockPos> e : pulseResult.positions().entrySet()) {
            msg.append("    ").append(e.getKey()).append(" @ ").append(e.getValue().toShortString()).append(" pulsed\n");
        }

        msg.append("  [4/6] post-break status\n");
        appendStatusLines(msg, midStatus);

        msg.append("  [5/6] restore-support\n");
        for (Map.Entry<String, BlockPos> e : restoreResult.positions().entrySet()) {
            msg.append("    ").append(e.getKey()).append(" @ ").append(e.getValue().toShortString()).append(" restored\n");
        }

        msg.append("  [6/6] final status\n");
        appendStatusLines(msg, finalStatus);

        String finalMsg = msg.toString().stripTrailing();
        source.sendFeedback(() -> Text.literal(finalMsg), false);
        return 1;
    }

    /** Appends one compact status line per lane to {@code sb}. Used by the sequence report. */
    private static void appendStatusLines(StringBuilder sb, List<SlabbedLabFixtures.LaneStatus> statuses) {
        for (SlabbedLabFixtures.LaneStatus s : statuses) {
            sb.append("    ").append(s.laneName()).append(": ");
            if (s.supportMatch()) {
                sb.append("support[OK]");
            } else {
                sb.append("support[MISMATCH:")
                  .append(s.actualSupport().getBlock().getTranslationKey()).append("]");
            }
            sb.append("  candidate:").append(s.candidateFree() ? "[free]" : "[occupied]");
            sb.append("  pulse:").append(s.pulseFree() ? "[free]" : "[occupied]").append("\n");
        }
    }

    // -------------------------------------------------------------------------
    // Status command handler
    // -------------------------------------------------------------------------

    private static int statusFixture(CommandContext<ServerCommandSource> ctx, String lane) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();
        BlockPos origin = SlabbedLabFixtures.activeOriginOr(fixtureOrigin(source));

        List<SlabbedLabFixtures.LaneStatus> statuses = SlabbedLabFixtures.queryStatus(world, origin, lane);
        if (statuses == null) {
            source.sendError(Text.literal("[slablab] unknown lane '" + lane + "'. Valid: full, bottom_slab, top_slab."));
            return 0;
        }

        StringBuilder msg = new StringBuilder("[slablab] status (dev-only). origin: ")
                .append(origin.toShortString()).append("\n");

        for (SlabbedLabFixtures.LaneStatus s : statuses) {
            msg.append("  ").append(s.laneName()).append(" @ ").append(s.supportPos().toShortString()).append("\n");

            if (s.supportMatch()) {
                msg.append("    support: [OK] ").append(s.expectedSupport().getBlock().getTranslationKey()).append("\n");
            } else {
                msg.append("    support: [MISMATCH] expected ").append(s.expectedSupport().getBlock().getTranslationKey())
                   .append(", found ").append(s.actualSupport().getBlock().getTranslationKey()).append("\n");
            }

            msg.append("    candidate: ").append(s.candidatePos().toShortString())
               .append(s.candidateFree() ? " [free]" : " [occupied]").append("\n");

            msg.append("    pulse: ").append(s.pulsePos().toShortString())
               .append(s.pulseFree() ? " [free]" : " [occupied]").append("\n");
        }

        String finalMsg = msg.toString().stripTrailing();
        source.sendFeedback(() -> Text.literal(finalMsg), false);
        return 1;
    }
}
