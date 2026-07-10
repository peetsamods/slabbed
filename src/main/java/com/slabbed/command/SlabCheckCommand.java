package com.slabbed.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /slabcheck [radius]} — the on-demand height-honesty scanner. It reads every cell within
 * {@code radius} of the runner that carries a STORED placement height (the value the block was placed
 * at, frozen into the chunk attachment at placement time) and reports three separate populations. What
 * each one means depends on the active frozen-dy mode, and the summary line states the mode plainly:
 *
 * <ul>
 *   <li><b>store-desyncs (hard violations)</b> — cells whose stored placement value differs from the
 *       height they are DRAWN at right now ({@link SlabSupport#getYOffset}, the render authority). When
 *       frozen-dy is ON the render authority IS the store, so this is 0 by construction — nothing on
 *       screen can have drifted from where it was placed. When frozen-dy is OFF the render authority is
 *       the live reading, so a store-desync is a genuine on-screen law violation (a placed block whose
 *       drawn height moved). This population drives the return code (non-zero desyncs → command fails).
 *   <li><b>would-move-if-unfrozen</b> — cells whose stored value differs from the un-stored reading
 *       ({@link SlabSupport#getUnstoredYOffset}). These are the cells the store is actively holding in
 *       place: unfreeze and they would jump. Meaningful in BOTH modes; in frozen mode it is the honest
 *       counterpart to the (necessarily zero) desync count, in unfrozen mode it equals the desyncs.
 *   <li><b>unfrozen lowered cells</b> — cells with NO stored value whose {@link SlabSupport#getYOffset}
 *       is non-zero: the population that has no placement pin and can therefore still move even while
 *       frozen-dy is ON. Up to ten are listed with positions.
 * </ul>
 *
 * <p>Dev tooling that SHIPS in every jar (present, behaviour-neutral until invoked, op-gated). It only
 * READS — it authors nothing and mutates no height — so it cannot itself change what it measures. Radius
 * is capped at {@value #MAX_RADIUS}; beyond {@value #SYNC_LOAD_RADIUS} it may synchronously load
 * unexplored chunks (see the prefixed warning).
 */
public final class SlabCheckCommand {

    private SlabCheckCommand() {
    }

    private static final int MAX_RADIUS = 64;
    private static final int DEFAULT_RADIUS = 16;
    private static final double EPS = 1.0e-6;
    /** Above this radius the scan can pull in unexplored chunks synchronously; warn first. */
    private static final int SYNC_LOAD_RADIUS = 32;
    /** Cap the number of individual store-desyncs echoed to chat (the summary still counts them all). */
    private static final int MAX_LISTED = 40;
    /** Cap the number of unfrozen-lowered cells echoed to chat. */
    private static final int MAX_LOWERED_LISTED = 10;

    /** Wires {@code /slabcheck} into the server dispatcher for every world. */
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                register(dispatcher));
    }

    /** Builds the {@code slabcheck} node into {@code dispatcher}. Visible for the smoke test. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("slabcheck")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> check(ctx, DEFAULT_RADIUS))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
                                .executes(ctx -> check(ctx, IntegerArgumentType.getInteger(ctx, "radius")))));
    }

    private static int check(CommandContext<CommandSourceStack> ctx, int radius) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel world = source.getLevel();
        int r = Math.min(radius, MAX_RADIUS);
        BlockPos centre = BlockPos.containing(source.getPosition());
        boolean frozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;

        // Fix 5: a large radius pulls unexplored chunks in on the server thread — warn before the scan.
        if (r > SYNC_LOAD_RADIUS) {
            source.sendSuccess(() -> Component.literal(
                    "[slabcheck] radius " + r + " > " + SYNC_LOAD_RADIUS + ": unexplored chunks will be "
                            + "loaded synchronously (op dev tool; may pause the server briefly).")
                    .withStyle(ChatFormatting.YELLOW), false);
        }

        int scanned = 0;
        int desyncs = 0;
        int wouldMove = 0;
        int unfrozenLowered = 0;
        List<String> desyncLines = new ArrayList<>();
        List<String> loweredLines = new ArrayList<>();

        int minY = world.getMinY();
        int maxY = world.getMaxY();
        // Column-major (x,z outer): fetch each column's chunk ONCE and read block states from it, rather
        // than re-resolving the chunk for every Y (fix 5).
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int x = centre.getX() + dx;
                int z = centre.getZ() + dz;
                ChunkAccess chunk = world.getChunk(x >> 4, z >> 4);
                for (int dy = -r; dy <= r; dy++) {
                    int y = centre.getY() + dy;
                    if (y < minY || y > maxY) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    double stored = SlabAnchorAttachment.storedPlacementDy(world, pos);
                    BlockState state = chunk.getBlockState(pos);
                    if (Double.isNaN(stored)) {
                        // No placement pin here: is it a cell that can still move while frozen?
                        double live = SlabSupport.getYOffset(world, pos, state);
                        if (!state.isAir() && Math.abs(live) > EPS) {
                            unfrozenLowered++;
                            if (loweredLines.size() < MAX_LOWERED_LISTED) {
                                loweredLines.add(pos.toShortString() + ": reads " + live);
                            }
                        }
                        continue;
                    }
                    scanned++;
                    double render = SlabSupport.getYOffset(world, pos, state);
                    double unstored = SlabSupport.getUnstoredYOffset(world, pos, state);
                    if (Math.abs(stored - render) > EPS) {
                        desyncs++;
                        if (desyncLines.size() < MAX_LISTED) {
                            desyncLines.add(pos.toShortString() + ": stored " + stored + " but draws " + render);
                        }
                    }
                    if (Math.abs(stored - unstored) > EPS) {
                        wouldMove++;
                    }
                }
            }
        }

        // Mode-honest summary header: name the active mode and what the scan can see.
        if (frozen) {
            source.sendSuccess(() -> Component.literal(
                    "[slabcheck] frozen=ON — stored heights are the render authority; showing store-desyncs, "
                            + "would-move-if-unfrozen, and unfrozen lowered cells."), false);
        } else {
            source.sendSuccess(() -> Component.literal(
                    "[slabcheck] frozen=OFF — the live reading is the render authority; store-desyncs are "
                            + "on-screen law violations."), false);
        }

        for (String line : desyncLines) {
            source.sendSuccess(() -> Component.literal("  [slabcheck] DESYNC @" + line)
                    .withStyle(ChatFormatting.RED), false);
        }
        for (String line : loweredLines) {
            source.sendSuccess(() -> Component.literal("  [slabcheck] unfrozen-lowered @" + line)
                    .withStyle(ChatFormatting.GRAY), false);
        }

        final int scannedFinal = scanned;
        final int desyncsFinal = desyncs;
        final int wouldMoveFinal = wouldMove;
        final int loweredFinal = unfrozenLowered;
        String summary = "[slabcheck] " + scannedFinal + " placed cells scanned — store-desyncs: "
                + desyncsFinal + ", would-move-if-unfrozen: " + wouldMoveFinal + ", unfrozen lowered cells: "
                + loweredFinal + ".";
        if (desyncs > 0) {
            source.sendFailure(Component.literal(summary));
        } else {
            source.sendSuccess(() -> Component.literal(summary), false);
        }
        return desyncs > 0 ? 0 : 1;
    }
}
