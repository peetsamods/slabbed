package com.slabbed.client;

import com.slabbed.config.SlabbedConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The mod's settings screen, opened by {@code /slabdy settings}: a title, one control per setting,
 * and a Done button that saves.
 *
 * <p>Constructed only when the command is typed. No tick hook, no HUD element, no lifecycle
 * listener; it reads the loaded config and writes it back on Done.
 *
 * <p>Nothing is written until Done: closing the screen any other way leaves the active config and
 * the file exactly as they were.
 *
 * <p>This class must stay in the client source set. The game-test source set compiles against client
 * output, but a dedicated-server run cannot load vanilla GUI classes at runtime.
 */
public final class SlabbedSettingsScreen extends Screen {

    private static final int CONTROL_WIDTH = 200;
    private static final int CONTROL_HEIGHT = 20;

    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

    /** The pending value. Applied to the config only when Done is pressed. */
    private SlabbedConfig.PotSeat potSeat = SlabbedConfig.get().potSeat();

    public SlabbedSettingsScreen() {
        super(Component.translatable("slabbed.settings.title"));
    }

    @Override
    protected void init() {
        layout.addTitleHeader(getTitle(), font);

        CycleButton<SlabbedConfig.PotSeat> seat =
                CycleButton.<SlabbedConfig.PotSeat>builder(SlabbedSettingsScreen::potSeatLabel, potSeat)
                        .withValues(SlabbedConfig.PotSeat.values())
                        .withTooltip(value -> Tooltip.create(
                                Component.translatable(potSeatKey(value) + ".tooltip")))
                        .create(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT,
                                Component.translatable("slabbed.settings.pot_seat"),
                                (button, value) -> potSeat = value);
        layout.addToContents(seat);

        layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> onDone())
                .width(CONTROL_WIDTH)
                .build());

        // visitWidgets, never visitChildren: addRenderableWidget's bound is
        // GuiEventListener & Renderable & NarratableEntry, which a bare LayoutElement does not satisfy.
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
    }

    private void onDone() {
        if (potSeat != SlabbedConfig.get().potSeat()) {
            SlabbedConfig.apply(SlabbedConfig.withPotSeat(potSeat));
        }
        onClose();
    }

    private static Component potSeatLabel(SlabbedConfig.PotSeat value) {
        return Component.translatable(potSeatKey(value));
    }

    /** One key per enum constant, so a new constant cannot silently reuse another's wording. */
    private static String potSeatKey(SlabbedConfig.PotSeat value) {
        return "slabbed.settings.pot_seat." + value.name().toLowerCase(Locale.ROOT);
    }
}
