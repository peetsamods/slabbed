package com.slabbed.client;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tier-2 client-side dy FINGERPRINT dump (companion to the headless
 * {@code Slabbed2612DyFingerprintTest}; see {@code docs/process/RELEASE_SANITY_CHECKLIST.md} §3).
 *
 * <p>The headless gametest fingerprint runs on the SERVER and is blind to one live-only class:
 * client-vs-server {@code getYOffset} divergence (the "snaps to the wrong dy after a delay" sync
 * bug, where the server anchors a value the client reads geometrically). On a keypress this scans a
 * box around the player and logs each lowered/raised block's CLIENT dy in the same
 * {@code SLABBED-FP | …} shape, so an operator can diff a built fixture's client capture against the
 * committed server baseline ({@code src/gametest/resources/dy-baseline.txt}).
 *
 * <p>Trigger: <b>numpad 1</b> (GLFW key poll on the window handle — this port's Loom setup does NOT
 * expose {@code fabric-key-binding-api-v1} to the client source set, only {@code lifecycle-events}
 * and {@code renderer}/{@code model-loading}, so a registered {@code KeyMapping} is unavailable;
 * we poll GLFW directly, mirroring how the HUD avoids {@code fabric-rendering-v1}). Rising-edge
 * debounced.
 *
 * <p>Dev-only and EXCLUDED from the release jar (see {@code build.gradle}). Initialized from
 * {@link SlabbedClient} only in a development environment.
 *
 * <p>Render-mesh desync (dy correct, mesh stale) is NOT visible here either — that stays a human
 * visual check (checklist Lane 3).
 */
public final class DyFingerprintDump {

    private static final double EPS = 1.0e-6;
    private static final int RADIUS_XZ = 6;
    private static final int RADIUS_Y = 4;
    private static final int DUMP_KEY = GLFW.GLFW_KEY_KP_1;

    private static boolean keyDownLastTick;

    private DyFingerprintDump() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null || client.getWindow() == null) {
                return;
            }
            boolean down = GLFW.glfwGetKey(client.getWindow().handle(), DUMP_KEY) == GLFW.GLFW_PRESS;
            if (down && !keyDownLastTick) {
                dump(client);
            }
            keyDownLastTick = down;
        });
    }

    private static void dump(Minecraft client) {
        if (client.level == null || client.player == null) {
            return;
        }
        Level level = client.level;
        LocalPlayer player = client.player;
        BlockPos origin = player.blockPosition();

        List<String> lines = new ArrayList<>();
        for (int dx = -RADIUS_XZ; dx <= RADIUS_XZ; dx++) {
            for (int dy = -RADIUS_Y; dy <= RADIUS_Y; dy++) {
                for (int dz = -RADIUS_XZ; dz <= RADIUS_XZ; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    double offset = SlabSupport.getYOffset(level, pos, state);
                    if (Math.abs(offset) <= EPS) {
                        continue;   // only report blocks the mod actually moves
                    }
                    lines.add(line(level, pos, state, offset));
                }
            }
        }

        Slabbed.LOGGER.info("SLABBED-FP-CLIENT === dump @ {} ({} offset blocks in r={}x{}) ===",
                origin.toShortString(), lines.size(), RADIUS_XZ, RADIUS_Y);
        for (String l : lines) {
            Slabbed.LOGGER.info(l);
        }
        Slabbed.LOGGER.info("SLABBED-FP-CLIENT === end dump (grep SLABBED-FP-CLIENT in latest.log) ===");
    }

    private static String line(Level level, BlockPos pos, BlockState state, double offset) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String name = id == null ? "?" : id.toString();
        return String.format(Locale.ROOT, "SLABBED-FP-CLIENT | %s | %s | dy=%.3f | src=%s",
                pos.toShortString(), name, offset, src(level, pos, state));
    }

    /** Same classification the /slabdy HUD's {@code src=} field uses, so captures line up. */
    private static String src(Level level, BlockPos pos, BlockState state) {
        if (SlabAnchorAttachment.isFrozenFlat(level, pos)) {
            return "FROZEN-FLAT";
        }
        if (SlabAnchorAttachment.isAnchored(level, pos)) {
            return "ANCHORED";
        }
        if (SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(level, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(level, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(level, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(level, pos, state)) {
            return "compound-side";
        }
        return "geometric";
    }
}
