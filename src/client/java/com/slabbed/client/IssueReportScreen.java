package com.slabbed.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.FittingMultiLineTextWidget;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** All entrypoints share the same editable draft and explicit review boundary. */
final class IssueReportScreen extends Screen {
    private final Screen parent;
    private final IssueReport versions;
    private final IssueReportSession session;
    private final Supplier<Iterable<IssueReport.ClientMod>> modSource;
    private final Consumer<String> clipboard;
    private final IssueReportSession.Browser browser;
    private String title = "";
    private String steps = "";
    private String happened = "";
    private IssueReport.WorldType worldType = IssueReport.WorldType.UNSPECIFIED;
    private boolean includeMods;
    private String modText = "";
    private int collectionOmissions;
    private Button reviewButton;

    IssueReportScreen(Screen parent, IssueReport versions) {
        this(parent, versions, IssueReportClient::loadedClientMods,
                text -> Minecraft.getInstance().keyboardHandler.setClipboard(text),
                IssueReportClient::openBrowser);
    }

    IssueReportScreen(Screen parent, IssueReport versions,
                      Supplier<Iterable<IssueReport.ClientMod>> modSource,
                      Consumer<String> clipboard, IssueReportSession.Browser browser) {
        super(Component.translatable("slabbed.report.entry"));
        this.parent = parent;
        this.versions = versions.versionsOnly();
        this.session = new IssueReportSession(this.versions);
        this.modSource = modSource;
        this.clipboard = clipboard;
        this.browser = browser;
    }

    @Override
    protected void init() {
        if (session.closed()) return;
        int contentWidth = Math.min(420, Math.max(80, width - 32));
        boolean stackedFooter = contentWidth < 260;
        int footerHeight = stackedFooter ? 52 : 30;
        var layout = new HeaderAndFooterLayout(this, 28, footerHeight);
        layout.addTitleHeader(getTitle(), font);
        var content = LinearLayout.vertical().spacing(6);
        if (session.state() == IssueReportSession.State.EDIT) addDraft(content, contentWidth);
        else addReview(content, contentWidth);
        var scroll = new ScrollableLayout(minecraft, content, Math.max(20, height - 36 - footerHeight));
        scroll.setMinWidth(contentWidth);
        layout.addToContents(scroll);
        var footer = stackedFooter ? LinearLayout.vertical().spacing(4) : LinearLayout.horizontal().spacing(4);
        int buttonWidth = stackedFooter ? contentWidth : (contentWidth - 4) / 2;
        if (session.state() == IssueReportSession.State.EDIT) {
            reviewButton = footer.addChild(Button.builder(label("review"), button -> review())
                    .width(buttonWidth).build());
            updateReviewButton();
        } else {
            footer.addChild(Button.builder(label("edit"), button -> {
                session.back();
                rebuildWidgets();
            }).width(buttonWidth).build());
        }
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .width(buttonWidth).build());
        layout.addToFooter(footer);
        layout.arrangeElements();
        layout.visitWidgets(this::addRenderableWidget);
    }

    private void addDraft(LinearLayout content, int width) {
        content.addChild(text(label("draft_explanation"), width));
        content.addChild(text(label("short_title"), width));
        EditBox titleBox = content.addChild(new EditBox(font, width, 20, label("short_title")));
        titleBox.setMaxLength(IssueReport.MAX_TITLE_LENGTH);
        titleBox.setHint(label("title_hint"));
        titleBox.setValue(title);
        titleBox.setResponder(value -> { title = value; updateReviewButton(); });
        content.addChild(text(label("steps"), width));
        content.addChild(editor("steps", "steps_hint", steps, width, 54,
                value -> { steps = value; updateReviewButton(); }));
        content.addChild(text(label("happened"), width));
        content.addChild(editor("happened", "happened_hint", happened, width, 54,
                value -> { happened = value; updateReviewButton(); }));
        content.addChild(CycleButton.<IssueReport.WorldType>builder(
                        value -> Component.literal(value.label()), worldType)
                .withValues(IssueReport.WorldType.values())
                .create(0, 0, width, 20, label("world_type"),
                        (button, value) -> { worldType = value; updateReviewButton(); }));
        content.addChild(text(label("mods_scope"), width));
        content.addChild(Checkbox.builder(label("include_mods"), font).maxWidth(width)
                .selected(includeMods).onValueChange((box, selected) -> toggleMods(selected)).build());
        if (includeMods) {
            content.addChild(text(label("mods_edit"), width));
            content.addChild(editor("mods_edit", "mods_hint", modText, width, 90, value -> modText = value));
            if (collectionOmissions > 0) content.addChild(text(
                    Component.translatable("slabbed.report.omitted", collectionOmissions), width));
        }
        content.addChild(text(label("required"), width));
    }

    private MultiLineEditBox editor(String name, String hint, String value, int width, int height,
                                   Consumer<String> changed) {
        MultiLineEditBox box = MultiLineEditBox.builder().setPlaceholder(label(hint))
                .build(font, width, height, label(name));
        box.setCharacterLimit(Integer.MAX_VALUE);
        box.setValue(value);
        box.setValueListener(changed);
        return box;
    }

    private boolean ready() {
        return versions.withDetails(title, steps, happened, worldType, IssueReport.ModList.empty()).valid();
    }

    private void updateReviewButton() {
        if (reviewButton != null) reviewButton.active = session.state() == IssueReportSession.State.EDIT && ready();
    }

    private IssueReport draft() {
        IssueReport.ModList mods = includeMods
                ? IssueReport.editedMods(modText).withAdditionalOmissions(collectionOmissions)
                : IssueReport.ModList.empty();
        return versions.withDetails(title, steps, happened, worldType, mods);
    }

    private void review() {
        if (!session.closed() && session.review(draft())) rebuildWidgets();
    }

    private void toggleMods(boolean selected) {
        if (session.closed() || includeMods == selected) return;
        includeMods = selected;
        if (selected) {
            IssueReport.ModList collected;
            try {
                collected = IssueReport.collectMods(modSource.get());
            } catch (RuntimeException | LinkageError failure) {
                collected = IssueReport.collectMods(null);
            }
            modText = collected.text();
            collectionOmissions = collected.omitted();
        } else clearMods();
        rebuildWidgets();
    }

    private void addReview(LinearLayout content, int width) {
        IssueReport report = session.report();
        boolean shortLink = report.prefilledUri().isPresent();
        content.addChild(text(label(shortLink ? "public_notice" : "long_notice"), width));
        content.addChild(new FittingMultiLineTextWidget(0, 0, width,
                Math.max(72, Math.min(144, height / 3)), Component.literal(report.copyText()), font));
        if (report.mods().hasContext()) {
            content.addChild(Button.builder(label("remove_mods"), button -> {
                if (session.closed()) return;
                includeMods = false;
                clearMods();
                session.back();
                session.review(draft());
                rebuildWidgets();
            }).width(width).build());
        }
        content.addChild(text(label(statusKey(shortLink)), width));
        Button open = content.addChild(Button.builder(label(shortLink ? "open" : "open_blank"), button -> {
            session.open(browser);
            rebuildWidgets();
        }).width(width).build());
        open.active = session.state() == IssueReportSession.State.REVIEW;
        content.addChild(Button.builder(label("copy_text"), button ->
                button.setMessage(label(session.copyText(clipboard) ? "text_copied" : "copy_failed")))
                .width(width).build());
        Button copyLink = content.addChild(Button.builder(label("copy"), button ->
                button.setMessage(label(session.copyLink(clipboard) ? "copied" : "copy_failed")))
                .width(width).build());
        copyLink.active = shortLink;
        content.addChild(text(label("safe_link"), width));
        EditBox link = content.addChild(new EditBox(font, width, 20, label("safe_link")));
        link.setMaxLength(IssueReport.MAX_URL_LENGTH);
        link.setValue(report.browserUri().toString());
        link.setEditable(false);
        link.moveCursorToStart(false);
    }

    private String statusKey(boolean shortLink) {
        return switch (session.state()) {
            case REQUESTED -> shortLink ? "requested" : "blank_requested";
            case FAILED -> "failed";
            default -> "ready";
        };
    }

    private Component label(String key) { return Component.translatable("slabbed.report." + key); }
    private MultiLineTextWidget text(Component message, int width) {
        return new MultiLineTextWidget(message, font).setMaxWidth(width);
    }
    private void clearMods() { modText = ""; collectionOmissions = 0; }

    @Override
    public Component getNarrationMessage() {
        String notice = session.state() == IssueReportSession.State.EDIT ? "draft_explanation"
                : session.report().prefilledUri().isPresent() ? "public_notice" : "long_notice";
        return getTitle().copy().append(". ").append(label(notice));
    }

    @Override
    public void onClose() {
        session.close();
        clearMods();
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        session.close();
        clearMods();
    }
}
