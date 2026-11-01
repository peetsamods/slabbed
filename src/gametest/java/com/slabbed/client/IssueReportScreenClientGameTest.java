package com.slabbed.client;

import com.slabbed.Slabbed;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.FittingMultiLineTextWidget;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Real widgets with synthetic metadata and local effect recorders; no report data leaves the test. */
public final class IssueReportScreenClientGameTest implements FabricClientGameTest {
    private static IssueReport versions() {
        return new IssueReport("26.3", "0.19.5", "0.7.0-alpha+26.2-26.3", "0.160.5+26.3");
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        var reads = new AtomicInteger();
        var opened = new ArrayList<URI>();
        var copied = new ArrayList<String>();
        Screen parent = context.computeOnClient(client -> new TitleScreen());
        IssueReportScreen form = context.computeOnClient(client -> new IssueReportScreen(parent, versions(), () -> {
            reads.incrementAndGet();
            return List.of(new IssueReport.ClientMod("alpha_mod", "1.2.3"),
                    new IssueReport.ClientMod("beta_mod", "2.0"));
        }, copied::add, opened::add));
        int originalScale = context.computeOnClient(client -> client.options.guiScale().get());
        try {
            show(context, form);
            context.takeScreenshot("report-initial-structure");
            context.runOnClient(client -> {
                require(reads.get() == 0 && !find(form, Checkbox.class).selected(),
                        "opening the form must not collect a mod list");
                Button review = button(form, "review");
                require(!review.active, "empty descriptions must disable review");
                press(review);
                require(opened.isEmpty() && copied.isEmpty(), "untouched form must not trigger effects");
                fill(form);
                require(button(form, "review").active, "real descriptions and world type must enable review");
            });
            for (int scale : new int[]{1, 2}) {
                context.runOnClient(client -> {
                    client.options.guiScale().set(scale);
                    client.resizeGui();
                    require(client.getWindow().getGuiScale() == scale,
                            "the requested GUI scale must actually be active");
                    require(field(form, EditBox.class, "short_title").getValue().equals("Glass floats"),
                            "GUI scaling must retain the draft");
                    require(!find(form, Checkbox.class).selected() && reads.get() == 0,
                            "GUI scaling must not opt into or collect metadata");
                    checkFooter(form, "review");
                    Slabbed.LOGGER.info("REPORT_FORM_LAYOUT scale={} width={} height={}", scale, form.width, form.height);
                });
                context.takeScreenshot("report-form-scale-" + scale);
            }
            context.runOnClient(client -> {
                press(find(form, Checkbox.class));
                require(reads.get() == 1, "explicit opt-in must collect once");
                MultiLineEditBox mods = field(form, MultiLineEditBox.class, "mods_edit");
                require(mods.getValue().equals("alpha_mod 1.2.3\nbeta_mod 2.0"),
                        "the editor must display exactly the included IDs and versions");
                mods.setValue("alpha_mod 9.9");
                viewport(form).setScrollAmount(viewport(form).maxScrollAmount());
            });
            context.takeScreenshot("report-mod-editor");
            context.runOnClient(client -> {
                press(button(form, "review"));
                String preview = preview(form);
                require(preview.contains("alpha_mod 9.9") && !preview.contains("beta_mod"),
                        "review must reflect edited and removed rows");
                require(all(form).stream().anyMatch(node -> node instanceof net.minecraft.client.gui.components.MultiLineTextWidget widget
                                && widget.getMessage().getString().equals(
                                Component.translatable("slabbed.report.public_notice").getString())),
                        "public GitHub and pre-open transfer disclosure must be on the review");
                press(button(form, "copy_text"));
                require(copied.getLast().equals(preview), "copy text must equal the visible preview");
                press(button(form, "copy"));
                URI link = URI.create(copied.getLast());
                String rawBody = link.getRawQuery().split("&body=", 2)[1];
                require(URLDecoder.decode(rawBody, StandardCharsets.UTF_8)
                                .equals(preview.substring(preview.indexOf("\n\n") + 2)),
                        "the copied link body must equal the preview body");
            });
            context.takeScreenshot("report-review-before-open");
            context.runOnClient(client -> viewport(form).setScrollAmount(viewport(form).maxScrollAmount()));
            context.takeScreenshot("report-review-actions");
            context.runOnClient(client -> {
                URI link = URI.create(copied.getLast());
                Button open = button(form, "open");
                press(open);
                press(open);
                require(opened.size() == 1 && opened.getFirst().equals(link),
                        "only an explicit reviewed action may open one matching link");
                require(!button(form, "open").active, "repeated opening must be disabled");
            });
            context.takeScreenshot("report-reviewed-short");
            context.runOnClient(client -> {
                press(button(form, "edit"));
                require(reads.get() == 1 && find(form, Checkbox.class).selected(),
                        "back must preserve edits without recollecting");
                require(field(form, MultiLineEditBox.class, "mods_edit").getValue().equals("alpha_mod 9.9"),
                        "back must retain the edited list");
                press(button(form, "review"));
                press(button(form, "remove_mods"));
                require(!preview(form).contains("alpha_mod") && !preview(form).contains("Included client mods"),
                        "the entire list must be removable from review");
                press(button(form, "edit"));
                require(!find(form, Checkbox.class).selected(), "removing the list must turn off the opt-in");
                press(find(form, Checkbox.class));
                require(reads.get() == 2, "a new explicit opt-in may take a fresh snapshot");
                field(form, MultiLineEditBox.class, "mods_edit").setValue("");
                press(button(form, "review"));
                require(!preview(form).contains("Included client mods"), "deleting every row must remove the list");
                Button oldCopy = button(form, "copy_text");
                form.onClose();
                int before = copied.size();
                press(oldCopy);
                require(copied.size() == before && opened.size() == 1,
                        "closed review callbacks must not copy or open old data");
            });
            var largeReads = new AtomicInteger();
            var many = new ArrayList<IssueReport.ClientMod>();
            for (int index = 0; index < 1000; index++)
                many.add(new IssueReport.ClientMod("sample_" + index, "1.2.3"));
            IssueReportScreen large = context.computeOnClient(client -> new IssueReportScreen(parent, versions(), () -> {
                largeReads.incrementAndGet();
                return many;
            }, copied::add, opened::add));
            show(context, large);
            context.runOnClient(client -> {
                require(!find(large, Checkbox.class).selected() && largeReads.get() == 0
                                && field(large, EditBox.class, "short_title").getValue().isEmpty(),
                        "reopening starts a fresh draft with mod opt-in off");
                fill(large);
                press(find(large, Checkbox.class));
                require(field(large, MultiLineEditBox.class, "mods_edit").getValue().contains("sample_999 1.2.3"),
                        "a large list must not be silently clipped by the editor");
                press(button(large, "review"));
                require(!button(large, "copy").active, "an oversized report cannot produce a copied URL");
                require(field(large, EditBox.class, "safe_link").getValue().equals(IssueReport.DESTINATION),
                        "long-report link field must contain only the blank destination");
                press(button(large, "copy_text"));
                require(copied.getLast().equals(preview(large)) && copied.getLast().contains("sample_999 1.2.3"),
                        "long-report copy must contain the complete reviewed list");
                checkFooter(large, "edit");
                viewport(large).setScrollAmount(viewport(large).maxScrollAmount());
            });
            context.takeScreenshot("report-long-actions");
            context.runOnClient(client -> {
                FittingMultiLineTextWidget review = find(large, FittingMultiLineTextWidget.class);
                require(review.maxScrollAmount() > 0, "the large review must require inner scrolling");
                viewport(large).setScrollAmount(0);
                review.setScrollAmount(review.maxScrollAmount());
                require(Math.abs(review.scrollAmount() - review.maxScrollAmount()) < 0.001d,
                        "the review viewport must reach the end of the complete report");
                checkFooter(large, "edit");
                Slabbed.LOGGER.info("REPORT_PREVIEW_SCROLL final_row=sample_999 scroll={} max={}",
                        review.scrollAmount(), review.maxScrollAmount());
            });
            context.takeScreenshot("report-long-preview-end");
            context.runOnClient(client -> {
                viewport(large).setScrollAmount(viewport(large).maxScrollAmount());
                press(button(large, "open_blank"));
                require(opened.getLast().toString().equals(IssueReport.DESTINATION)
                                && opened.getLast().getRawQuery() == null,
                        "long reports open blank without transmitting report text");
                checkFooter(large, "edit");
            });
            context.takeScreenshot("report-long-preview");
            context.runOnClient(client -> large.onClose());
            Slabbed.LOGGER.info("CLIENT_GAMETEST | IssueReportScreenClientGameTest | PASS");
        } finally {
            context.runOnClient(client -> {
                client.gui.setScreen(parent);
                client.options.guiScale().set(originalScale);
                client.resizeGui();
            });
        }
    }

    private static void show(ClientGameTestContext context, IssueReportScreen screen) {
        context.runOnClient(client -> client.execute(() -> client.setScreenAndShow(screen)));
        context.waitFor(client -> client.gui.screen() == screen && !screen.children().isEmpty(), 400);
    }

    private static void fill(IssueReportScreen form) {
        field(form, EditBox.class, "short_title").setValue("Glass floats");
        field(form, MultiLineEditBox.class, "steps").setValue("Placed glass on a slab.");
        field(form, MultiLineEditBox.class, "happened").setValue("The glass floats above it.");
        press(find(form, CycleButton.class));
    }

    private static String preview(IssueReportScreen screen) {
        return find(screen, FittingMultiLineTextWidget.class).getMessage().getString();
    }

    private static Button button(Screen screen, String key) { return field(screen, Button.class, key); }

    private static <T extends net.minecraft.client.gui.components.AbstractWidget> T field(
            Screen screen, Class<T> type, String key) {
        Component label = Component.translatable("slabbed.report." + key);
        return all(screen).stream().filter(type::isInstance).map(type::cast)
                .filter(widget -> widget.getMessage().getString().equals(label.getString())).findFirst()
                .orElseThrow(() -> new AssertionError("missing report control: " + key));
    }

    private static <T> T find(Screen screen, Class<T> type) {
        return all(screen).stream().filter(type::isInstance).map(type::cast).findFirst()
                .orElseThrow(() -> new AssertionError("missing report widget: " + type.getSimpleName()));
    }

    private static List<GuiEventListener> all(Screen screen) {
        var result = new ArrayList<GuiEventListener>();
        Set<GuiEventListener> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        collect(screen, result, seen);
        return result;
    }

    private static void collect(GuiEventListener node, List<GuiEventListener> result, Set<GuiEventListener> seen) {
        if (!seen.add(node)) return;
        result.add(node);
        if (node instanceof ContainerEventHandler container)
            for (GuiEventListener child : container.children()) collect(child, result, seen);
    }

    private static void checkFooter(Screen screen, String action) {
        Button primary = button(screen, action);
        require(primary.getX() >= 0 && primary.getY() >= 0
                        && primary.getX() + primary.getWidth() <= screen.width
                        && primary.getY() + primary.getHeight() <= screen.height,
                "the primary action must remain inside the viewport");
        AbstractScrollArea viewport = viewport(screen);
        require(viewport.getY() >= 0 && viewport.getY() + viewport.getHeight() <= primary.getY(),
                "scrolling content must not cover the footer actions");
    }

    private static AbstractScrollArea viewport(Screen screen) {
        return all(screen).stream()
                .filter(node -> node instanceof AbstractScrollArea && node instanceof ContainerEventHandler)
                .map(node -> (AbstractScrollArea) node).findFirst()
                .orElseThrow(() -> new AssertionError("missing scrolling report viewport"));
    }

    private static void press(AbstractButton button) { button.onPress(new KeyEvent(0, 0, 0)); }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
