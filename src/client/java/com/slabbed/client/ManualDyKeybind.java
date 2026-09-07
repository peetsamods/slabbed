package com.slabbed.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.slabbed.network.ManualDyAdjustPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Two key mappings, both UNBOUND by default, that ask the server to step the aimed block's stored
 * height by one half-step.
 *
 * <p><b>Two mappings, not one key plus a sneak modifier.</b> {@code KeyMapping} carries no modifier
 * concept on this line, so a modifier would be invisible in the Controls screen and unrebindable; and
 * sneak is held continuously while building, so a single key would silently invert itself for as long
 * as a player is crouched on a scaffold — the worst failure mode for a tool whose whole job is precise
 * height. Two rows are discoverable, independently rebindable, and independently leave-able unbound.
 *
 * <p><b>Inert until bound and pressed.</b> The tick body's first act is the unbound check, then two
 * click drains; with nothing bound it returns before reading the crosshair, the level or the network.
 * Vanilla only feeds key clicks while no screen is open, so clicks cannot pool behind chat and burst
 * on close; the screen check below is belt-and-braces.
 *
 * <p>Ships in every jar, default off — the standing debug-tooling rule's shape: registered
 * unconditionally from {@link SlabbedClient} with no development-environment guard and no reflection,
 * because either one is exactly how the shipped debug commands came to be unreachable on this line.
 */
public final class ManualDyKeybind {

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("slabbed", "slabbed"));

    private static KeyMapping lower;
    private static KeyMapping raise;

    private ManualDyKeybind() {
    }

    /**
     * MUST be called from the client entrypoint: Fabric's key-mapping registry refuses once the game's
     * options object exists, so this cannot be deferred to the first tick.
     */
    public static void init() {
        // The unbound key code on this line is InputConstants.UNKNOWN, which is the KEYBOARD key 0 —
        // not the -1 older lines used. Read it from the constant; never write a literal.
        lower = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.slabbed.manual_dy_lower",
                InputConstants.Type.KEYBOARD,
                InputConstants.UNKNOWN.getValue(),
                CATEGORY));
        raise = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.slabbed.manual_dy_raise",
                InputConstants.Type.KEYBOARD,
                InputConstants.UNKNOWN.getValue(),
                CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(ManualDyKeybind::tick);
    }

    private static void tick(Minecraft client) {
        if (lower == null || raise == null) {
            return;
        }
        if (lower.isUnbound() && raise.isUnbound()) {
            return;                                  // nothing bound: the whole feature is dead weight
        }
        boolean lowerFired = drain(lower);
        boolean raiseFired = drain(raise);
        // Both in one tick cancel. Sending both would make the server answer the second with a
        // rate-limit message for two distinct, deliberate keypresses.
        if (lowerFired == raiseFired) {
            return;
        }
        if (client == null || client.level == null || client.player == null
                || client.gui.screen() != null) {
            return;
        }
        HitResult hit = client.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
            return;
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = client.level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        ClientPlayNetworking.send(new ManualDyAdjustPayload(
                pos.asLong(), (byte) (lowerFired ? -1 : 1), Block.getId(state)));
    }

    /** Rising edges only, and drain the whole queue so a held key cannot bank steps. */
    private static boolean drain(KeyMapping mapping) {
        boolean fired = false;
        while (mapping.consumeClick()) {
            fired = true;
        }
        return fired;
    }
}
