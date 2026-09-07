package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.client.SlabbedSettingsScreen;
import com.slabbed.config.SlabbedConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * THE SETTINGS SCREEN'S ONLY AUTOMATED PROOF. Everything else about this feature is covered
 * headlessly — the command routing by {@code ShippedDebugCommandsTest}, the file contract by
 * {@code SlabbedConfigFileTest}, the placement values by {@code PotSeatOptionTest}. What is left is
 * the widget wiring, and only a client run can see it.
 *
 * <p>It GATES NOTHING: {@code runClientGameTest} is invoked by no build task, so this row is proof
 * only when a run's log carries its PASS line.
 *
 * <p>WHAT IT CANNOT ASSERT, and does not claim to: that typing the command into the chat box survives
 * the chat screen closing itself in the same call. No harness types into the chat box, so the queued
 * open stays a live-only row. It also says nothing about how the screen LOOKS.
 */
public final class SlabbedSettingsScreenClientGameTest implements FabricClientGameTest {

    private static final String CLIENT_GAMETEST_PASS =
            "CLIENT_GAMETEST | SlabbedSettingsScreenClientGameTest | PASS";

    @Override
    public void runTest(ClientGameTestContext ctx) {
        SlabbedConfig restore = SlabbedConfig.setActiveForTesting(
                SlabbedConfig.withPotSeat(SlabbedConfig.PotSeat.FLUSH));
        try {
            // Open exactly the way the shipped command does.
            ctx.runOnClient(client -> client.execute(() -> client.setScreenAndShow(new SlabbedSettingsScreen())));
            ctx.waitFor(client -> client.gui.screen() instanceof SlabbedSettingsScreen, 400);

            // Cycling the control moves the PENDING value only: nothing is saved until Done.
            ctx.runOnClient(client -> {
                press(optionButton(client));
                if (SlabbedConfig.get().potSeat() != SlabbedConfig.PotSeat.FLUSH) {
                    throw new AssertionError("cycling the control must not change the active config before "
                            + "Done is pressed; it already reads " + SlabbedConfig.get().potSeat());
                }
            });

            // Closing any other way discards the pending value.
            ctx.runOnClient(client -> requireScreen(client).onClose());
            ctx.waitFor(client -> !(client.gui.screen() instanceof SlabbedSettingsScreen), 400);
            ctx.runOnClient(client -> {
                if (SlabbedConfig.get().potSeat() != SlabbedConfig.PotSeat.FLUSH) {
                    throw new AssertionError("closing the screen without pressing Done must leave the config "
                            + "untouched; it now reads " + SlabbedConfig.get().potSeat());
                }
            });

            // Reopen, cycle, and press Done: now it is applied AND persisted.
            ctx.runOnClient(client -> client.execute(() -> client.setScreenAndShow(new SlabbedSettingsScreen())));
            ctx.waitFor(client -> client.gui.screen() instanceof SlabbedSettingsScreen, 400);
            ctx.runOnClient(client -> {
                press(optionButton(client));
                press(doneButton(client));
            });
            ctx.waitFor(client -> !(client.gui.screen() instanceof SlabbedSettingsScreen), 400);
            ctx.runOnClient(client -> {
                if (SlabbedConfig.get().potSeat() != SlabbedConfig.PotSeat.VANILLA_FLOAT) {
                    throw new AssertionError("pressing Done must apply the pending value; the active config "
                            + "still reads " + SlabbedConfig.get().potSeat());
                }
                Path file = FabricLoader.getInstance().getConfigDir().resolve("slabbed.json");
                if (!Files.exists(file)) {
                    throw new AssertionError("pressing Done must persist the setting, but no config file was "
                            + "written under this run's config directory");
                }
            });
            Slabbed.LOGGER.info(CLIENT_GAMETEST_PASS);
        } finally {
            SlabbedConfig.setActiveForTesting(restore);
        }
    }

    private static SlabbedSettingsScreen requireScreen(Minecraft client) {
        Screen screen = client.gui.screen();
        if (!(screen instanceof SlabbedSettingsScreen settings)) {
            throw new AssertionError("the settings screen is not open; the current screen is " + screen);
        }
        return settings;
    }

    private static CycleButton<?> optionButton(Minecraft client) {
        for (GuiEventListener child : requireScreen(client).children()) {
            if (child instanceof CycleButton<?> cycle) {
                return cycle;
            }
        }
        throw new AssertionError("the settings screen has no option control; its widgets were never added "
                + "to the screen (visitWidgets is what puts the layout's widgets into children())");
    }

    private static Button doneButton(Minecraft client) {
        for (GuiEventListener child : requireScreen(client).children()) {
            if (child instanceof Button button) {
                return button;
            }
        }
        throw new AssertionError("the settings screen has no Done button");
    }

    /** Presses a button the way a keyboard confirmation does; no mouse position is involved. */
    private static void press(AbstractButton button) {
        button.onPress(new KeyEvent(0, 0, 0));
    }
}
