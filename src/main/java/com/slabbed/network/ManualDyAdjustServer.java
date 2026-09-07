package com.slabbed.network;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.placement.ConnectorPlacementSettle;
import com.slabbed.placement.LandingResolver;
import com.slabbed.util.ManualDyEnvelope;
import com.slabbed.util.SlabdyRowFormatter;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server side of the manual height nudge: the player aims at a block they placed and steps its
 * stored height by one half-step.
 *
 * <p><b>LAW.md.</b> This writes ONE cell — the cell the player is aiming at — and no other. It is not
 * a neighbour update, and no read anywhere re-derives a height because of it: the value goes into the
 * same store placement writes to, and every later read returns it verbatim. A deliberate player act ON
 * a block is the same category as the original placement, not the neighbour edit LAW 1 prohibits
 * (maintainer ruling, 2026-09-06).
 *
 * <p><b>Permission.</b> In singleplayer everyone may nudge; on a shared world it takes creative mode
 * or permission level 2 (maintainer ruling, 2026-09-06).
 *
 * <p><b>Envelope.</b> {@link ManualDyEnvelope}. A stored value already OUTSIDE the envelope is refused
 * rather than snapped: clamping one would invert the key's direction, and snapping would rewrite a
 * historical defect by an amount the player did not ask for. Break and re-place such a block instead.
 *
 * <p><b>Support.</b> A nudge may not sink the block's visible bottom plane below the visible top plane
 * of the first non-air cell beneath it. That is the same seat placement itself computes, so this tool
 * can never author a depth the placement path would not have produced — which matters because
 * collision is compensated only for movement, and clips would keep seeing the unlowered box.
 *
 * <p><b>Split.</b> {@link #apply} is a pure decision that returns a {@link Result} and sends nothing;
 * {@code notify} is a separate step. A headless mock server player has no connection, so any path that
 * sent a message would be untestable — the decisions are the part worth testing.
 */
public final class ManualDyAdjustServer {

    /** Vanilla pads its own block-interaction range checks by exactly this at both call sites. */
    private static final double BLOCK_RANGE_PADDING = 1.0d;

    /** At least {@code |MIN_DY| + 1} cells, so the search cannot miss the surface a nudge could reach. */
    private static final int SUPPORT_SEARCH_DEPTH = 4;

    private static final double EPS = 1.0e-9d;

    /** One adjust per player per tick. Pruned to the current tick on every adjust, so it stays bounded. */
    private static final Map<UUID, Long> LAST_ADJUST_TICK = new HashMap<>();

    private ManualDyAdjustServer() {
    }

    public enum Outcome {
        APPLIED,
        CLAMPED_FLOOR,
        CLAMPED_CEILING,
        DENIED_PERMISSION,
        DENIED_RANGE,
        DENIED_UNLOADED,
        DENIED_AIR,
        DENIED_STALE_TARGET,
        DENIED_NO_FACT,
        DENIED_OUT_OF_ENVELOPE,
        DENIED_NO_SUPPORT,
        DENIED_RATE
    }

    /** {@code oldDy}/{@code newDy} are NaN where no stored value was in play. */
    public record Result(Outcome outcome, double oldDy, double newDy) {
        static Result denied(Outcome outcome) {
            return new Result(outcome, Double.NaN, Double.NaN);
        }
    }

    // ---- the permission ruling, as typed predicates ----
    // Deliberately NOT one function of three same-typed booleans: that shape makes the call site's
    // argument order untestable, because swapping two arguments reddens nothing. With typed
    // parameters there is nothing left to swap.

    /**
     * A world is "effectively singleplayer" only while nobody else can join it. An integrated server
     * keeps its singleplayer profile after Open-to-LAN, so {@code isSingleplayer()} alone would hand
     * the tool to every guest.
     */
    public static boolean effectivelySingleplayer(MinecraftServer server) {
        return server != null && server.isSingleplayer() && !server.isPublished();
    }

    /** Shared-world half of the ruling: creative mode OR permission level 2 or higher. */
    public static boolean multiplayerPermitted(ServerPlayer player) {
        return player != null
                && (player.isCreative()
                || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
    }

    public static boolean permitted(MinecraftServer server, ServerPlayer player) {
        return effectivelySingleplayer(server) || multiplayerPermitted(player);
    }

    // ---- the transaction ----

    public static void registerReceiver() {
        // Fabric dispatches this on the server main thread: the networking module routes a custom
        // payload through MinecraftServer.packetProcessor().scheduleIfPossible(listener, packet)
        // before the handler runs — the same contract PlacementDyCorrectionServer already relies on
        // when it mutates its own static map from a receiver.
        ServerPlayNetworking.registerGlobalReceiver(ManualDyAdjustPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (player == null) {
                return;
            }
            BlockPos pos = BlockPos.of(payload.pos());
            Result result = apply(player, pos, payload.direction(), payload.stateId());
            notify(player, pos, result);
        });
    }

    public static void clearPlayer(ServerPlayer player) {
        if (player != null) {
            LAST_ADJUST_TICK.remove(player.getUUID());
        }
    }

    /**
     * Validates and applies the nudge. Returns what happened and sends NOTHING — the notification is a
     * separate step so a gametest can drive this with a mock player that has no connection.
     */
    public static Result apply(ServerPlayer player, BlockPos pos, int direction, int expectedStateId) {
        if (player == null || pos == null || (direction != 1 && direction != -1)) {
            return Result.denied(Outcome.DENIED_PERMISSION);
        }
        ServerLevel level = player.level();
        if (!permitted(level.getServer(), player)) {
            return Result.denied(Outcome.DENIED_PERMISSION);
        }
        if (!player.isWithinBlockInteractionRange(pos, BLOCK_RANGE_PADDING)) {
            return Result.denied(Outcome.DENIED_RANGE);
        }
        if (!level.isLoaded(pos)) {
            return Result.denied(Outcome.DENIED_UNLOADED);
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return Result.denied(Outcome.DENIED_AIR);
        }
        // The cell must still hold the block the player was aiming at. Without this, a break-and-
        // replace racing the packet lands the nudge on a stranger while the stale fact still reads
        // present().
        if (Block.getId(state) != expectedStateId) {
            return Result.denied(Outcome.DENIED_STALE_TARGET);
        }

        // Only a cell that ALREADY carries a recorded placement height may be nudged; a fact-less cell
        // is refused and mints nothing. A stored fact exists only for a cell that ran a placement
        // transaction, and the frozen read lane depends on that premise — minting one here would open
        // the compat world-hole guard by hand.
        SlabAnchorAttachment.PlacementDyFact fact = SlabAnchorAttachment.rawPlacementDyFact(level, pos);
        if (!fact.present()) {
            return Result.denied(Outcome.DENIED_NO_FACT);
        }
        double current = fact.valueOrNaN();
        if (!ManualDyEnvelope.inEnvelope(current)) {
            // Refused, never snapped — see the class javadoc.
            return new Result(Outcome.DENIED_OUT_OF_ENVELOPE, current, current);
        }

        double next = ManualDyEnvelope.step(current, direction);
        if (next == current) {
            return new Result(direction < 0 ? Outcome.CLAMPED_FLOOR : Outcome.CLAMPED_CEILING, current, current);
        }
        if (next < current && !supported(level, pos, state, next)) {
            return new Result(Outcome.DENIED_NO_SUPPORT, current, current);
        }

        long tick = level.getGameTime();
        LAST_ADJUST_TICK.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() != tick);
        if (LAST_ADJUST_TICK.putIfAbsent(player.getUUID(), tick) != null) {
            return Result.denied(Outcome.DENIED_RATE);
        }

        // Server-guarded and NaN-guarded inside; publishes the chunk attachment once, which Fabric
        // resyncs to every tracking client. The client's existing attachment listener re-meshes exactly
        // this cell, so no block update or remesh nudge is issued from here.
        SlabAnchorAttachment.writePlacementDy(level, pos, next);
        // MANDATORY companion of any published height change. Connector arms (fence / wall / pane) are
        // baked blockstate properties decided from the OLD height, and a pure attachment write revisits
        // nothing. This is the same pass the placement path runs after it publishes. Do not remove it:
        // without it a same-height pair refuses to join, or a stepped pair stays joined.
        ConnectorPlacementSettle.settlePublishedPlacements(level, Set.of(pos.immutable()));
        return new Result(Outcome.APPLIED, current, next);
    }

    /**
     * True when a block drawn at {@code candidateDy} still rests on (or above) the first real surface
     * beneath it. Uses the placement-time authority, so this tool can never author a depth placement
     * itself would refuse.
     */
    static boolean supported(ServerLevel level, BlockPos pos, BlockState state, double candidateDy) {
        double bottom = pos.getY() + candidateDy + LandingResolver.bottomPlaneOffset(state);
        for (int depth = 1; depth <= SUPPORT_SEARCH_DEPTH; depth++) {
            BlockPos below = pos.below(depth);
            BlockState belowState = level.getBlockState(below);
            if (belowState.isAir()) {
                continue;
            }
            double top = below.getY()
                    + LandingResolver.visibleOwnerDy(level, below, belowState)
                    + LandingResolver.topPlaneOffset(belowState);
            return bottom >= top - EPS;
        }
        return true;   // nothing beneath within reach: only the envelope constrains it
    }

    private static void notify(ServerPlayer player, BlockPos pos, Result result) {
        // OVERLAY (action bar), not chat. acceptsSystemMessages returns the overlay flag when a
        // player's chat visibility is HIDDEN, so this is the only channel that reaches such a player —
        // and a transient per-keypress result belongs on the action bar anyway.
        player.sendSystemMessage(Component.literal(describe(player, pos, result)), true);
    }

    /** Public so a headless row can assert the exact strings without a player connection. */
    public static String describe(ServerPlayer player, BlockPos pos, Result result) {
        return switch (result.outcome()) {
            case APPLIED -> {
                ServerLevel level = player.level();
                List<String> row = SlabdyRowFormatter.formatRow(
                        level, pos, level.getBlockState(pos), null, null, player.getMainHandItem());
                // Index-safe by construction: the formatter's line count is not this class's contract.
                yield String.join(" ", row.subList(0, Math.min(2, row.size()))).replace("  ", " ").trim();
            }
            case CLAMPED_FLOOR -> "[slabbed] already at the lowest height";
            case CLAMPED_CEILING -> "[slabbed] already at full height";
            case DENIED_PERMISSION -> "[slabbed] adjusting heights here needs creative mode or op level 2";
            case DENIED_RANGE -> "[slabbed] that block is out of reach";
            case DENIED_UNLOADED -> "[slabbed] that block is not loaded";
            case DENIED_AIR -> "[slabbed] there is no block there";
            case DENIED_STALE_TARGET -> "[slabbed] that block changed; aim again";
            case DENIED_NO_FACT -> "[slabbed] that block has no recorded height; break and place it to adjust it";
            case DENIED_OUT_OF_ENVELOPE -> "[slabbed] that block's height is outside the adjustable range";
            case DENIED_NO_SUPPORT -> "[slabbed] there is no room to lower it further";
            case DENIED_RATE -> "[slabbed] slow down";
        };
    }
}
