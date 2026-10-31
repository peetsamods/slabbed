package com.slabbed.client;

import java.net.URI;
import java.util.function.Consumer;

/** External effects are permitted only by explicit actions on an active review. */
final class IssueReportSession {
    enum State { EDIT, REVIEW, REQUESTED, FAILED, CLOSED }
    @FunctionalInterface
    interface Browser { void open(URI uri) throws Exception; }

    private final IssueReport versions;
    private IssueReport report;
    private State state = State.EDIT;

    IssueReportSession(IssueReport versions) {
        this.versions = versions.versionsOnly();
        this.report = this.versions;
    }
    IssueReport report() { return report; }
    State state() { return state; }
    boolean closed() { return state == State.CLOSED; }

    boolean review(IssueReport candidate) {
        if (state != State.EDIT || candidate == null || !candidate.valid()) return false;
        report = candidate;
        state = State.REVIEW;
        return true;
    }

    void back() {
        if (closed()) return;
        report = versions;
        state = State.EDIT;
    }

    void open(Browser browser) {
        if (state != State.REVIEW) return;
        // Claim the action before handing control to the OS: repeated clicks cannot open twice.
        state = State.REQUESTED;
        try {
            browser.open(report.browserUri());
        } catch (Exception | LinkageError failure) {
            state = State.FAILED;
        }
    }

    boolean copyText(Consumer<String> clipboard) { return copy(clipboard, false); }
    boolean copyLink(Consumer<String> clipboard) { return copy(clipboard, true); }

    private boolean copy(Consumer<String> clipboard, boolean link) {
        if (state == State.EDIT || closed() || !report.valid()
                || (link && report.prefilledUri().isEmpty())) return false;
        try {
            clipboard.accept(link ? report.prefilledUri().orElseThrow().toString() : report.copyText());
            return true;
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }

    static boolean isReportCommand(String command) {
        return "/slabbed_report_issue".equals(command) || "slabbed_report_issue".equals(command);
    }

    static boolean canOpenRequest(Object requestedLevel, Object currentLevel,
                                  Object requestedScreen, Object currentScreen, boolean playerPresent) {
        return playerPresent && requestedLevel != null && requestedLevel == currentLevel
                && (currentScreen == null || currentScreen == requestedScreen);
    }

    void close() {
        state = State.CLOSED;
        report = versions;
    }
}
