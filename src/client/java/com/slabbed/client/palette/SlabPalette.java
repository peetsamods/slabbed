package com.slabbed.client.palette;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Wires the palette overlay: registers the bindable open key (default {@code V}) under a "Slabbed"
 * controls category and, each client tick, opens {@link SlabPaletteScreen} when the key is pressed
 * in-game.
 *
 * <p>{@code V} is the default because {@code Q} is vanilla "drop item" — the maintainer can rebind this to
 * {@code Q} in Controls if she also unbinds drop. Fabric's key-binding registry makes it appear in
 * the vanilla Controls screen automatically.
 */
public final class SlabPalette {

    /** Public so the open Screen can recognise its own key for press-again-to-close (honours rebinds). */
    public static final KeyMapping OPEN_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.slabbed.palette",
            GLFW.GLFW_KEY_V,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("slabbed", "slabbed"))));

    private SlabPalette() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(SlabPalette::onEndClientTick);
    }

    private static void onEndClientTick(Minecraft client) {
        // Drain EVERY queued press this tick (not just the first) so a swallowed press can't leave a
        // stale open queued for a later tick. consumeClick() only reports presses made in-game.
        boolean open = false;
        while (OPEN_KEY.consumeClick()) {
            open = true;
        }
        // Same-tick race guard: another screen (e.g. a menu opened by the same input burst) may have
        // appeared this very tick — never stack the palette over it. Repo precedent: DyFingerprintDump
        // gates on client.gui.screen().
        if (open
                && client.player != null
                && client.level != null
                && client.gui.screen() == null) {
            client.setScreenAndShow(new SlabPaletteScreen());
        }
    }
}
