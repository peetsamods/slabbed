package com.slabbed.client;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs without Minecraft, native libraries, network or a desktop. */
public final class IssueReportTest {
    private static int checks;

    public static void main(String[] args) {
        versionsAndEncoding();
        explicitActionsOnly();
        noticeCommand();
        deferredEntry();
        placement();
        titleMenuPlacement();
        System.out.println("ISSUE_REPORT_TEST_PASS checks=" + checks);
    }

    private static void versionsAndEncoding() {
        var report = new IssueReport("26.3", "0.19.5", "0.6.4-alpha+26.2-26.3", "0.160.5+26.3");
        check(report.diagnostics().equals("Minecraft: 26.3\nFabric Loader: 0.19.5"
                + "\nSlabbed: 0.6.4-alpha+26.2-26.3\nFabric API: 0.160.5+26.3"), "exact allowlist");
        URI uri = report.uri();
        check(uri.getScheme().equals("https") && uri.getHost().equals("github.com")
                && uri.getPath().equals("/peetsamods/slabbed/issues/new"), "fixed HTTPS destination");
        check(uri.getUserInfo() == null && uri.getFragment() == null, "no credentials or fragment");
        String[] params = uri.getRawQuery().split("&");
        check(params.length == 2 && params[0].equals("title=Slabbed+issue"), "only title and body parameters");
        String decoded = URLDecoder.decode(params[1].substring(5), StandardCharsets.UTF_8);
        check(decoded.equals(report.body()), "percent encoding round trip, including literal plus");
        check(decoded.contains("0.6.4-alpha+26.2-26.3"), "platform qualifier preserved");
        check(decoded.contains("### What were you doing when the problem happened?\n"
                + "Tell us what you clicked or did, in order, so we can try it too.\n"),
                "plain-language action question and help text stay together");
        check(!decoded.toLowerCase(java.util.Locale.ROOT).contains("steps to reproduce"),
                "generated player draft contains no reproduction jargon");
        check(decoded.contains("Expected behavior"), "expected behavior prompt preserved");
        for (String invalid : new String[] {null, "", "0.1\nprivate-data", "1.0&body=override", "1.0#fragment",
                "https://invalid.example", "1.0/path", "1.0\\path", "1.0 ", "世界", "1".repeat(81)}) {
            var unsafe = new IssueReport(invalid, invalid, invalid, invalid);
            check(unsafe.diagnostics().equals("Minecraft: unavailable\nFabric Loader: unavailable"
                    + "\nSlabbed: unavailable\nFabric API: unavailable"), "malformed version rejected");
            check(unsafe.uri().toString().length() <= IssueReport.MAX_URL_LENGTH, "invalid metadata bounded");
        }
        String maximum = "1" + "+".repeat(79);
        var bounded = new IssueReport(maximum, maximum, maximum, maximum);
        check(bounded.minecraft().equals(maximum), "maximum metadata accepted");
        check(bounded.uri().toASCIIString().length() <= 2048, "worst-case encoded report <= 2048");
        check(new IssueReport("1.21.11", "0.18.4", "0.6.2-alpha+1.21.9-1.21.11", "0.141.3+1.21.11")
                .uri().getHost().equals("github.com"), "other semantic version shapes");
    }

    private static void explicitActionsOnly() {
        var report = new IssueReport("26.3", "0.19.5", "0.6.4-alpha+26.2-26.3", "0.160.5+26.3");
        AtomicInteger calls = new AtomicInteger();
        IssueReportSession.Browser browser = uri -> { check(uri.equals(report.uri()), "preview equals outbound snapshot"); calls.incrementAndGet(); };
        var cancelled = new IssueReportSession(report);
        check(calls.get() == 0, "construction performs no I/O");
        cancelled.report().uri();
        check(calls.get() == 0, "copy/preview generation performs no I/O");
        cancelled.close();
        cancelled.open(browser);
        check(calls.get() == 0 && cancelled.state() == IssueReportSession.State.CLOSED, "Cancel/Back before confirmation sends nothing");
        var active = new IssueReportSession(report);
        active.open(browser);
        active.open(browser);
        check(calls.get() == 1 && active.state() == IssueReportSession.State.REQUESTED, "repeated Open calls open once");
        active.close();
        active.open(browser);
        check(calls.get() == 1, "interrupted/closed screen never opens again");
        var reopened = new IssueReportSession(report);
        reopened.open(browser);
        check(calls.get() == 2, "fresh repeated entry requires a new explicit confirmation");
        AtomicInteger failedCalls = new AtomicInteger();
        var failed = new IssueReportSession(report);
        failed.open(uri -> { failedCalls.incrementAndGet(); throw new java.io.IOException("unavailable"); });
        failed.open(browser);
        check(failedCalls.get() == 1 && calls.get() == 2 && failed.state() == IssueReportSession.State.FAILED, "failure never retries silently");
        check(failed.report().uri().equals(report.uri()), "failure retains copyable safe link");
        var missingNative = new IssueReportSession(report);
        missingNative.open(uri -> { throw new UnsatisfiedLinkError("unavailable"); });
        check(missingNative.state() == IssueReportSession.State.FAILED, "native failure retains fallback");
    }

    private static void noticeCommand() {
        check(IssueReportSession.isReportCommand("/slabbed_report_issue"), "notice report command is handled locally");
        check(IssueReportSession.isReportCommand("slabbed_report_issue"), "prefix-free vanilla command is handled locally");
        for (String other : new String[] {null, "", "/slabbed_report_issue extra", "/slabbed_report_issue_other",
                "/slabbed_dismiss_beta_notice", "/help", " /slabbed_report_issue"}) {
            check(!IssueReportSession.isReportCommand(other), "unrelated or extended commands are left untouched");
        }
    }

    private static void deferredEntry() {
        Object level = new Object(), chat = new Object();
        check(IssueReportSession.canOpenRequest(level, level, chat, chat, true), "clicked command with retained chat reaches review");
        check(IssueReportSession.canOpenRequest(level, level, chat, null, true), "typed command with closed chat reaches review");
        check(!IssueReportSession.canOpenRequest(level, level, chat, new Object(), true), "replacement screen interrupts pending entry");
        check(!IssueReportSession.canOpenRequest(level, new Object(), chat, chat, true), "world replacement interrupts pending entry");
        check(!IssueReportSession.canOpenRequest(level, null, chat, null, true), "disconnect interrupts pending entry");
        check(!IssueReportSession.canOpenRequest(null, null, chat, null, true), "entry outside a world is ignored");
        check(!IssueReportSession.canOpenRequest(level, level, chat, null, false), "missing player interrupts pending entry");
    }

    private static void placement() {
        for (int[] size : new int[][] {{320, 240}, {427, 240}, {640, 360}, {854, 480}, {320, 180}, {240, 180}}) {
            var occupied = new ArrayList<IssueReportPlacement.Box>();
            int center = size[0] / 2;
            occupied.add(new IssueReportPlacement.Box(center - 100, 32, 200, 20));
            for (int y = 64; y < size[1] - 30; y += 24) {
                occupied.add(new IssueReportPlacement.Box(center - 102, y, 98, 20));
                occupied.add(new IssueReportPlacement.Box(center + 4, y, 98, 20));
            }
            var box = IssueReportPlacement.find(size[0], size[1], 176, occupied).orElseThrow();
            check(box.x() >= 8 && box.y() >= 8 && box.x() + box.width() <= size[0] - 8
                    && box.y() + box.height() <= size[1] - 4, "button on screen at narrow scale");
            check(occupied.stream().noneMatch(box::overlaps), "no vanilla control overlap");
            occupied.add(new IssueReportPlacement.Box(box.x(), box.y(), 40, box.height()));
            var relocated = IssueReportPlacement.find(size[0], size[1], 100, occupied).orElseThrow();
            check(occupied.stream().noneMatch(relocated::overlaps), "relocates around another mod's button");
        }
        check(IssueReportPlacement.find(320, 240, 176, List.of(new IssueReportPlacement.Box(0, 0, 320, 240))).isEmpty(),
                "fully occupied menu never overlays another mod");
        check(IssueReportPlacement.find(30, 30, 176, List.of()).isEmpty(), "impossible size safely rejected");
    }

    private static void titleMenuPlacement() {
        for (int[] size : new int[][] {{320, 240}, {427, 240}, {640, 360}, {854, 480}}) {
            int center = size[0] / 2;
            int firstRow = size[1] / 4 + 48;
            var occupied = new ArrayList<IssueReportPlacement.Box>();
            occupied.add(new IssueReportPlacement.Box(center - 155, 30, 310, 90));
            occupied.add(new IssueReportPlacement.Box(0, size[1] - 14, size[0], 14));
            for (int row = 0; row < 3; row++) {
                occupied.add(new IssueReportPlacement.Box(center - 100, firstRow + row * 24, 200, 20));
            }
            occupied.add(new IssueReportPlacement.Box(center - 124, firstRow + 84, 248, 20));
            var button = IssueReportPlacement.find(size[0], size[1], 176, occupied).orElseThrow();
            check(occupied.stream().noneMatch(button::overlaps), "title entry clears logo, footer and vanilla controls");
            check(button.y() >= 8 && button.y() + button.height() <= size[1] - 4,
                    "title entry stays visible at normal and narrow scales");
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
