package com.slabbed.client;

import java.net.URI;

/** External effects are permitted only by explicit actions on an active review. */
final class IssueReportSession {
    enum State { REVIEW, REQUESTED, FAILED, CLOSED }
    @FunctionalInterface
    interface Browser { void open(URI uri) throws Exception; }

    private final IssueReport report;
    private State state = State.REVIEW;

    IssueReportSession(IssueReport report) { this.report = report; }
    IssueReport report() { return report; }
    State state() { return state; }

    void open(Browser browser) {
        if (state != State.REVIEW) return;
        // Claim the action before handing control to the OS: repeated clicks cannot open twice.
        state = State.REQUESTED;
        try {
            browser.open(report.uri());
        } catch (Exception | LinkageError failure) {
            state = State.FAILED;
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

    void close() { state = State.CLOSED; }
}
