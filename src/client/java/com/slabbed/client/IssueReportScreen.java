package com.slabbed.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** The single review boundary for both player entrypoints; construction performs no I/O. */
final class IssueReportScreen extends Screen {
    private final Screen parent;
    private final IssueReportSession session;

    IssueReportScreen(Screen parent, IssueReport report) {
        super(Component.translatable("slabbed.report.entry"));
        this.parent = parent;
        this.session = new IssueReportSession(report);
    }

    @Override
    protected void init() {
        int contentWidth = Math.min(320, Math.max(40, width - 40));
        var layout = new HeaderAndFooterLayout(this, 28, 30);
        layout.addTitleHeader(getTitle(), font);
        var content = LinearLayout.vertical().spacing(8);
        content.addChild(text(Component.translatable("slabbed.report.explanation"), contentWidth));
        content.addChild(text(Component.translatable("slabbed.report.versions"), contentWidth));
        content.addChild(text(Component.literal(session.report().diagnostics()), contentWidth));
        content.addChild(text(Component.translatable("slabbed.report.privacy"), contentWidth));
        content.addChild(text(Component.translatable(statusKey()), contentWidth));
        Button open = content.addChild(Button.builder(Component.translatable("slabbed.report.open"), button -> {
            session.open(IssueReportClient::openBrowser);
            rebuildWidgets();
        }).width(contentWidth).build());
        open.active = session.state() == IssueReportSession.State.REVIEW;
        content.addChild(Button.builder(Component.translatable("slabbed.report.copy"), button -> {
            minecraft.keyboardHandler.setClipboard(session.report().uri().toString());
            button.setMessage(Component.translatable("slabbed.report.copied"));
        }).width(contentWidth).build());
        content.addChild(text(Component.translatable("slabbed.report.safe_link"), contentWidth));
        var link = content.addChild(new EditBox(font, contentWidth, 20,
                Component.translatable("slabbed.report.safe_link")));
        link.setMaxLength(IssueReport.MAX_URL_LENGTH);
        link.setValue(session.report().uri().toString());
        link.setEditable(false);
        link.moveCursorToStart(false);
        var scroll = new ScrollableLayout(minecraft, content, Math.max(20, height - 66));
        scroll.setMinWidth(contentWidth);
        layout.addToContents(scroll);
        layout.addToFooter(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .width(Math.min(200, contentWidth)).build());
        layout.arrangeElements();
        layout.visitWidgets(this::addRenderableWidget);
    }

    private MultiLineTextWidget text(Component message, int maxWidth) {
        return new MultiLineTextWidget(message, font).setMaxWidth(maxWidth);
    }

    private String statusKey() {
        return switch (session.state()) {
            case REQUESTED -> "slabbed.report.requested";
            case FAILED -> "slabbed.report.failed";
            default -> "slabbed.report.ready";
        };
    }

    @Override
    public Component getNarrationMessage() {
        return getTitle().copy().append(". ")
                .append(Component.translatable("slabbed.report.explanation")).append(". ")
                .append(Component.literal(session.report().diagnostics())).append(". ")
                .append(Component.translatable("slabbed.report.privacy")).append(". ")
                .append(Component.translatable(statusKey()));
    }

    @Override
    public void onClose() {
        session.close();
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        session.close();
    }
}
