package com.slabbed.client;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.slabbed.Slabbed;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exercises rendered menu entrypoints with native client mouse and keyboard events. */
public final class IssueReportEntryClientGameTest implements FabricClientGameTest {
    private final List<Map<String, Object>> rows = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext context) {
        boolean baseline = Boolean.getBoolean("slabbed.reportUiBaseline");
        boolean modMenu = FabricLoader.getInstance().isModLoaded("modmenu");
        int[][] sizes = baseline ? new int[][]{{1920, 1080, 4}}
                : new int[][]{{854, 480, 1}, {854, 480, 2}, {1280, 720, 3},
                        {1280, 960, 4}, {1920, 1080, 4}};
        try {
            require(IssueReportPreferences.setShowPauseButton(true), "Enable default for test");
            Path prefs = Files.createTempDirectory("slabbed-menu-test").resolve("menu.json");
            require(IssueReportPreferences.read(prefs), "Missing file preserves shipped default");
            Files.writeString(prefs, "{broken");
            require(IssueReportPreferences.read(prefs), "Malformed file preserves shipped default");
            Files.writeString(prefs, "{\"showPauseButton\":\"false\"}");
            require(IssueReportPreferences.read(prefs), "Wrong type preserves shipped default");
            require(IssueReportPreferences.write(prefs, false) && !IssueReportPreferences.read(prefs),
                    "Disabled preference persists");
            require(IssueReportPreferences.write(prefs, true) && IssueReportPreferences.read(prefs),
                    "Reenabled preference persists");
            require(!IssueReportPreferences.write(prefs.resolve("blocked.json"), false),
                    "Write failure must be reported");
            for (int index = 0; index < sizes.length; index++) {
                int[] size = sizes[index];
                context.getInput().resizeWindow(size[0], size[1]);
                context.runOnClient(client -> {
                    client.options.guiScale().set(size[2]);
                    client.resizeGui();
                });
                context.setScreen(TitleScreen::new);
                context.waitForScreen(TitleScreen.class);
                context.waitFor(client -> client.gui.overlay() == null, 200);
                context.waitTicks(5);
                String prefix = "entry-" + index + "-scale-" + size[2];
                context.takeScreenshot(prefix + "-title");
                Map<String, Object> row = context.computeOnClient(client -> {
                    Screen screen = client.gui.screen();
                    var value = new LinkedHashMap<String, Object>();
                    value.put("requested_width", size[0]);
                    value.put("requested_height", size[1]);
                    value.put("requested_scale", size[2]);
                    value.put("actual_scale", client.getWindow().getGuiScale());
                    value.put("framebuffer_width", client.getWindow().getWidth());
                    value.put("framebuffer_height", client.getWindow().getHeight());
                    value.put("gui_width", screen.width);
                    value.put("gui_height", screen.height);
                    value.put("modmenu", modMenu);
                    value.put("title_report_buttons", reports(screen).size());
                    return value;
                });
                rows.add(row);
                write();
                if (baseline) {
                    require(((Number) row.get("title_report_buttons")).intValue() > 0,
                            "Baseline must expose the reported title-screen entry");
                    Slabbed.LOGGER.info("REPORT_ENTRY_BASELINE title_button_present=true actual_scale={}", row.get("actual_scale"));
                    return;
                }
                require(((Number) row.get("title_report_buttons")).intValue() == 0,
                        "The title screen must contain no Slabbed report entry");
                if (size[2] == 4) require(((Number) row.get("actual_scale")).doubleValue() == 4,
                        "The large-window case must really use GUI scale four");

                Screen parent;
                Button entry;
                if (modMenu) {
                    Button mods = context.computeOnClient(client -> buttons(client.gui.screen()).stream()
                            .filter(button -> button.getMessage().getString().startsWith("Mods"))
                            .findFirst().orElseThrow(() -> new AssertionError("Missing Mod Menu title entry")));
                    click(context, mods, prefix + "-mods");
                    context.waitFor(client -> client.gui.screen().getClass().getName()
                            .equals("com.terraformersmc.modmenu.gui.ModsScreen"), 200);
                    parent = context.computeOnClient(client -> client.gui.screen());
                    EditBox search = context.computeOnClient(client -> (EditBox) field(parent, "searchBox"));
                    click(context, search, prefix + "-search");
                    context.takeScreenshot(prefix + "-before-search-text");
                    context.getInput().typeChars("slabbed");
                    context.waitTicks(5);
                    double[] point = context.computeOnClient(client -> {
                        Object list = field(parent, "modList");
                        Object selectedRow = null;
                        for (Object child : (List<?>) list.getClass().getMethod("children").invoke(list)) {
                            Object mod = child.getClass().getMethod("getMod").invoke(child);
                            if ("slabbed".equals(mod.getClass().getMethod("getId").invoke(mod))) selectedRow = child;
                        }
                        require(selectedRow != null, "The filtered native list must contain Slabbed");
                        LayoutElement layout = (LayoutElement) selectedRow;
                        double x = layout.getX() + layout.getWidth() / 2d;
                        double y = layout.getY() + layout.getHeight() / 2d;
                        require(list.getClass().getMethod("getEntryAtPos", double.class, double.class)
                                .invoke(list, x, y) == selectedRow,
                                "The native list hit test must identify the intended Slabbed row");
                        return cursor(client.getWindow().getScreenWidth(), client.getWindow().getScreenHeight(),
                                parent, x, y);
                    });
                    context.takeScreenshot(prefix + "-before-select-mod");
                    context.getInput().setCursorPos(point[0], point[1]);
                    context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
                    context.waitTicks(3);
                    entry = context.computeOnClient(client -> {
                        Object selected = parent.getClass().getMethod("getSelectedEntry").invoke(parent);
                        require(selected != null, "Native list click must select Slabbed");
                        Object mod = selected.getClass().getMethod("getMod").invoke(selected);
                        require("slabbed".equals(mod.getClass().getMethod("getId").invoke(mod)),
                                "The selected native Mod Menu row must be Slabbed");
                        return (Button) field(parent, "configureButton");
                    });
                } else {
                    Button options = context.computeOnClient(client -> buttons(client.gui.screen()).stream()
                            .filter(button -> button.getMessage().getString().equals(Component.translatable("menu.options").getString()))
                            .findFirst().orElseThrow(() -> new AssertionError("Missing Options button")));
                    click(context, options, prefix + "-options");
                    context.waitForScreen(OptionsScreen.class);
                    parent = context.computeOnClient(client -> client.gui.screen());
                    entry = context.computeOnClient(client -> reports(parent).stream().findFirst()
                            .orElseThrow(() -> new AssertionError("Fallback report access disappeared")));
                }
                final Button target = entry;
                context.runOnClient(client -> {
                    require(target.visible && target.active, "Report entry must be visible and active");
                    require(target.getX() >= 0 && target.getY() >= 0
                            && target.getX() + target.getWidth() <= parent.width
                            && target.getY() + target.getHeight() <= parent.height,
                            "Report entry must fit the viewport");
                    if (!modMenu) for (Button other : buttons(parent)) {
                        if (other == target || !other.visible) continue;
                        require(!overlaps(target, other), "Fallback must not overlap another menu control");
                    }
                });
                context.takeScreenshot(prefix + "-entry");
                click(context, target, prefix + "-open-report");
                context.waitForScreen(IssueReportScreen.class);
                context.takeScreenshot(prefix + "-report");
                if (index == 0) {
                    Screen draft = context.computeOnClient(client -> client.gui.screen());
                    Checkbox toggle = context.computeOnClient(client -> preference(draft));
                    require(toggle.selected(), "Pause entry starts enabled");
                    click(context, toggle, prefix + "-disable-pause");
                    context.runOnClient(client -> {
                        IssueReportPreferences.load();
                        require(!IssueReportPreferences.showPauseButton(), "Disabled choice survives reload");
                        require(!preference(draft).selected(), "UI reflects saved state");
                    });

                } else {
                    require(context.computeOnClient(client -> !preference(client.gui.screen()).selected()),
                            "Reporter remains accessible while pause entry is hidden");
                }
                key(context, InputConstants.KEY_ESCAPE, prefix + "-back");
                context.waitFor(client -> client.gui.screen() == parent, 200);
                Button keyboardTarget = context.computeOnClient(client -> modMenu
                        ? (Button) field(parent, "configureButton")
                        : reports(parent).stream().findFirst().orElseThrow());

                boolean focused = false;
                for (int step = 0; step < 32; step++) {
                    if (context.computeOnClient(client -> keyboardTarget.isFocused())) { focused = true; break; }
                    context.takeScreenshot(prefix + "-before-tab-" + step);
                    context.getInput().pressKey(InputConstants.KEY_TAB);
                    context.waitTick();
                }
                require(focused, "Report entry must be reachable by keyboard");
                key(context, InputConstants.KEY_RETURN, prefix + "-keyboard-open");
                context.waitForScreen(IssueReportScreen.class);
                context.takeScreenshot(prefix + "-reopened-report");
                key(context, InputConstants.KEY_ESCAPE, prefix + "-keyboard-back");
                context.waitFor(client -> client.gui.screen() == parent, 200);
                row.put("mouse_open_back", true);
                row.put("keyboard_open_back_reopen", true);
                row.put("passed", true);
                write();
            }
            try (var world = context.worldBuilder().setUseConsistentSettings(true).create()) {
                context.waitFor(client -> client.player != null && client.level != null
                        && client.gui.screen() == null, 400);
                context.setScreen(() -> new PauseScreen(true));
                context.waitForScreen(PauseScreen.class);
                context.waitTicks(3);
                Screen pause = context.computeOnClient(client -> client.gui.screen());
                require(context.computeOnClient(client -> reports(pause).isEmpty()),
                        "Disabled pause button must not exist in a real world");
                context.takeScreenshot("pause-hidden");
                context.setScreen(() -> IssueReportClient.createScreen(pause));
                context.waitForScreen(IssueReportScreen.class);
                Checkbox toggle = context.computeOnClient(client -> preference(client.gui.screen()));
                click(context, toggle, "reenable-pause");
                context.runOnClient(client -> {
                    IssueReportPreferences.load();
                    require(IssueReportPreferences.showPauseButton(), "Enabled choice survives reload");
                });
                key(context, InputConstants.KEY_ESCAPE, "return-reenabled-pause");
                context.waitForScreen(PauseScreen.class);
                context.waitTicks(3);
                Button pauseEntry = context.computeOnClient(client -> reports(client.gui.screen()).stream()
                        .findFirst().orElseThrow(() -> new AssertionError("Reenabled pause entry missing")));
                context.takeScreenshot("pause-reenabled");
                click(context, pauseEntry, "pause-reenabled-open");
                context.waitForScreen(IssueReportScreen.class);
                Checkbox hide = context.computeOnClient(client -> preference(client.gui.screen()));
                click(context, hide, "hide-existing-pause-entry");
                key(context, InputConstants.KEY_ESCAPE, "pause-reenabled-back");
                context.waitForScreen(PauseScreen.class);
                require(context.computeOnClient(client -> reports(client.gui.screen()).isEmpty()),
                        "Returning from reporter must remove the previously visible pause entry");
                context.takeScreenshot("pause-hidden-after-return");
                context.runOnClient(client -> require(IssueReportPreferences.setShowPauseButton(true),
                        "Restore test default"));
            }
            context.setScreen(TitleScreen::new);
            context.waitForScreen(TitleScreen.class);
            Slabbed.LOGGER.info("CLIENT_GAMETEST | IssueReportEntryClientGameTest | PASS modmenu={} rows={}", modMenu, rows.size());
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
    }

    private static Checkbox preference(Screen screen) {
        return IssueReportScreenClientGameTest.all(screen).stream().filter(Checkbox.class::isInstance)
                .map(Checkbox.class::cast).filter(box -> box.getMessage().getString().equals(
                        Component.translatable("slabbed.report.show_pause_button").getString()))
                .findFirst().orElseThrow();
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static List<Button> buttons(Screen screen) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).toList();
    }

    private static List<Button> reports(Screen screen) {
        return buttons(screen).stream().filter(button -> button.getMessage().getString().equals(
                Component.translatable("slabbed.report.entry").getString()) || button.getMessage().getString().equals(
                Component.translatable("slabbed.report.compact_entry").getString())).toList();
    }

    private static boolean overlaps(AbstractWidget first, AbstractWidget second) {
        return first.getX() < second.getX() + second.getWidth() && first.getX() + first.getWidth() > second.getX()
                && first.getY() < second.getY() + second.getHeight() && first.getY() + first.getHeight() > second.getY();
    }

    private static double[] cursor(int width, int height, Screen screen, double x, double y) {
        return new double[]{x * width / screen.width, y * height / screen.height};
    }

    private static void click(ClientGameTestContext context, AbstractWidget widget, String name) {
        context.takeScreenshot(name + "-before");
        double[] point = context.computeOnClient(client -> cursor(client.getWindow().getScreenWidth(),
                client.getWindow().getScreenHeight(), client.gui.screen(),
                widget.getX() + widget.getWidth() / 2d, widget.getY() + widget.getHeight() / 2d));
        context.getInput().setCursorPos(point[0], point[1]);
        context.waitTicks(12);
        context.takeScreenshot(name + "-hover");
        require(context.computeOnClient(client -> widget.isHovered()),
                "Native cursor must hover the intended control before clicking: " + name);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(3);
        context.takeScreenshot(name + "-after");
    }

    private static void key(ClientGameTestContext context, int key, String name) {
        context.takeScreenshot(name + "-before");
        context.getInput().pressKey(key);
        context.waitTicks(3);
        context.takeScreenshot(name + "-after");
    }

    private void write() throws Exception {
        String destination = System.getProperty("slabbed.reportUiOutput");
        if (destination == null) return;
        Path path = Path.of(destination);
        Files.createDirectories(path.getParent());
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(rows));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
