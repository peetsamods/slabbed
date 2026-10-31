package com.slabbed.client;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Synthetic report contracts run without Minecraft, native libraries, network or a desktop. */
public final class IssueReportTest {
    private static int checks;
    public static void main(String[] args) {
        versionContext();
        untouchedDescriptionsCannotReachExternalEffects();
        collectedMetadataCannotInjectReportStructure();
        editedModListsCanBeChangedAndRemoved();
        encodedPayloadUsesOnlyTheReviewedDestinationAndText();
        longReportsCopyInFullAndOpenOnlyABlankIssue();
        reviewBackAndCloseGuardEveryExternalEffect();
        failureAndDeferredEntryRemainFailClosed();
        noticeCommand();
        deferredEntry();
        placement();
        titleMenuPlacement();
        pauseMenuNearbyPlacement();
        System.out.println("ISSUE_REPORT_TEST_PASS checks=" + checks);
    }

    private static void versionContext() {
        check(versions().diagnostics().equals("Minecraft: 26.3\nFabric Loader: 0.19.5\nSlabbed: 0.6.9-alpha+26.2-26.3\nFabric API: 0.160.5+26.3"), "version context and platform qualifiers preserved");
        for (String invalid : new String[]{null, "", "1\nextra", "1&body=override", "1#fragment", "1/path", "1\\path", "1 ", "世界", "1".repeat(81)}) {
            var snapshot = new IssueReport(invalid, invalid, invalid, invalid);
            check(snapshot.diagnostics().equals("Minecraft: unavailable\nFabric Loader: unavailable\nSlabbed: unavailable\nFabric API: unavailable"), "malformed version context rejected");
            check(snapshot.prefilledUri().isEmpty(), "a snapshot alone cannot open a report");
        }
        String maximum = "1" + "+".repeat(79);
        var report = new IssueReport(maximum, maximum, maximum, maximum).withDetails("Glass", "Placed it.", "It floated.", IssueReport.WorldType.SINGLEPLAYER, null);
        check(report.minecraft().equals(maximum) && report.browserUri().toString().length() <= 2048, "maximum version metadata keeps the URL cap");
    }

    private static IssueReport versions() {
        return new IssueReport("26.3", "0.19.5", "0.6.9-alpha+26.2-26.3", "0.160.5+26.3");
    }

    private static IssueReport complete(IssueReport.ModList mods) {
        return versions().withDetails("Glass floats", "Placed glass on a slab.",
                "The glass floats above the slab.", IssueReport.WorldType.SINGLEPLAYER, mods);
    }

    private static void untouchedDescriptionsCannotReachExternalEffects() {
        check(!versions().valid(), "a version snapshot is not a report description");
        for (String text : List.of("", "  ", "Describe the symptoms here.",
                "Tell me what you clicked or did, in order, so I can try it too.\n1. ", "1.")) {
            check(!versions().withDetails("Glass issue", text, text,
                    IssueReport.WorldType.SINGLEPLAYER, IssueReport.ModList.empty()).valid(),
                    "empty and starter descriptions must not pass review");
        }
        check(!versions().withDetails("Glass issue", "Placed glass.", "It floated.",
                IssueReport.WorldType.UNSPECIFIED, null).valid(), "world type must be chosen");
        check(versions().withDetails("Menu issue", "Opened the settings menu.", "A control is missing.",
                IssueReport.WorldType.MENUS, null).valid(), "title-menu reports need no invented world type");
        var calls = new AtomicInteger();
        var session = new IssueReportSession(versions());
        session.open(uri -> calls.incrementAndGet());
        check(!session.copyText(value -> calls.incrementAndGet()), "unreviewed text cannot be copied");
        check(!session.review(null) && !session.review(versions()) && calls.get() == 0,
                "missing descriptions must not cause any external effect");
    }

    private static void collectedMetadataCannotInjectReportStructure() {
        var raw = new ArrayList<IssueReport.ClientMod>();
        raw.add(new IssueReport.ClientMod("zeta_mod", "2.0+build"));
        raw.add(new IssueReport.ClientMod("alpha_mod", "1.2.3"));
        raw.add(new IssueReport.ClientMod("alpha_mod", "9.9"));
        raw.add(new IssueReport.ClientMod("bad\n### heading", "1.0"));
        raw.add(new IssueReport.ClientMod("sample_mod", "1&title=changed"));
        raw.add(new IssueReport.ClientMod("sample_two", "1\n~~~\nextra"));
        raw.add(new IssueReport.ClientMod("sample_three", "x".repeat(81)));
        raw.add(null);
        IssueReport.ModList mods = IssueReport.collectMods(raw);
        check(mods.text().equals("alpha_mod 1.2.3\nzeta_mod 2.0+build") && mods.omitted() == 6,
                "only bounded ID/version tokens may be included, with explicit omissions");
        String body = complete(mods).body();
        check(body.contains("~~~text\nalpha_mod 1.2.3\nzeta_mod 2.0+build\n~~~")
                        && !body.contains("title=changed") && !body.contains("### heading")
                        && body.contains("not a complete inventory"),
                "metadata must remain literal rows rather than headings or URL fields");
        boolean immutable = false;
        try { mods.rows().add(new IssueReport.ClientMod("other_mod", "1")); }
        catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "reviewed metadata must not mutate behind the preview");
    }

    private static void editedModListsCanBeChangedAndRemoved() {
        IssueReport.ModList mods = IssueReport.editedMods(
                "zeta_mod 2.0\nalpha_mod 9.9\nbad/value 1.0\n# heading");
        check(mods.text().equals("alpha_mod 9.9\nzeta_mod 2.0") && mods.omitted() == 2,
                "edited rows must use the same metadata boundary");
        check(IssueReport.editedMods("").rows().isEmpty(), "all rows can be removed");
        IssueReport withoutMods = complete(IssueReport.ModList.empty());
        check(!withoutMods.body().contains("Included client mods")
                        && !withoutMods.body().contains("alpha_mod"), "removal must remove the whole list");
        check(complete(mods.withAdditionalOmissions(3)).body().contains("Omitted 5"),
                "collection and edit omissions must remain visible");
    }

    private static void encodedPayloadUsesOnlyTheReviewedDestinationAndText() {
        IssueReport report = versions().withDetails("Glass & snow ☃",
                "Placed glass.\nLooked at it? x=1&body=changed",
                "It floats. 🧱", IssueReport.WorldType.MULTIPLAYER, IssueReport.ModList.empty());
        URI uri = report.prefilledUri().orElseThrow();
        check(uri.getScheme().equals("https") && uri.getHost().equals("github.com")
                        && uri.getPath().equals("/peetsamods/slabbed/issues/new")
                        && uri.getUserInfo() == null && uri.getFragment() == null,
                "user text cannot change the destination");
        var query = new HashMap<String, String>();
        for (String pair : uri.getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            query.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        check(query.size() == 2 && query.get("title").equals("Glass & snow ☃")
                        && query.get("body").equals(report.body())
                        && query.get("body").contains("It floats. 🧱"),
                "one UTF-8 encoding must preserve exactly the reviewed title and body");
    }

    private static void longReportsCopyInFullAndOpenOnlyABlankIssue() {
        int boundary = -1;
        for (int length = 1; length < 4096; length++) {
            var report = versions().withDetails("Glass", "Placed it.", "x".repeat(length),
                    IssueReport.WorldType.SINGLEPLAYER, null);
            if (report.prefilledUri().map(uri -> uri.toString().length() == IssueReport.MAX_URL_LENGTH)
                    .orElse(false)) { boundary = length; break; }
        }
        check(boundary > 0, "the exact URL boundary must be exercised");
        var tooLong = versions().withDetails("Glass", "Placed it.", "x".repeat(boundary + 1),
                IssueReport.WorldType.SINGLEPLAYER, null);
        check(tooLong.prefilledUri().isEmpty()
                        && tooLong.browserUri().toString().equals(IssueReport.DESTINATION)
                        && tooLong.copyText().contains("x".repeat(boundary + 1)),
                "over-limit URLs must become blank destinations without truncating copied text");
        var many = new ArrayList<IssueReport.ClientMod>();
        for (int index = 0; index < IssueReport.MAX_MOD_ROWS + 2; index++)
            many.add(new IssueReport.ClientMod("example_" + index, "1.2.3"));
        var mods = IssueReport.collectMods(many);
        check(mods.rows().size() == IssueReport.MAX_MOD_ROWS && mods.omitted() == 2,
                "large metadata collections must disclose their limit");
        var largeReport = complete(mods);
        check(largeReport.prefilledUri().isEmpty()
                        && largeReport.copyText().contains("example_4095 1.2.3")
                        && largeReport.copyText().contains("Omitted 2"),
                "the full reviewed list and omission notice must survive the text-copy fallback");
    }

    private static void reviewBackAndCloseGuardEveryExternalEffect() {
        var session = new IssueReportSession(versions());
        var opened = new ArrayList<URI>();
        var copied = new ArrayList<String>();
        IssueReport report = complete(IssueReport.editedMods("example_mod 1.2.3"));
        check(session.review(report), "an actual description can be reviewed");
        check(session.copyText(copied::add) && copied.getFirst().equals(report.copyText()),
                "copy text must match the immutable preview");
        check(session.copyLink(copied::add)
                        && copied.getLast().equals(report.prefilledUri().orElseThrow().toString()),
                "copy link must use the same reviewed report");
        session.open(opened::add);
        session.open(opened::add);
        check(opened.size() == 1, "repeated clicks must not open twice");
        session.back();
        check(!session.copyText(copied::add), "going back invalidates the old review");
        check(session.review(complete(IssueReport.ModList.empty())), "an edited report can be reviewed again");
        session.open(opened::add);
        check(opened.size() == 2 && !opened.getLast().toString().contains("example_mod"),
                "removed mod data must not reappear in a new review");
        session.close();
        session.open(opened::add);
        check(!session.review(report) && !session.copyText(copied::add)
                        && !session.copyLink(copied::add) && opened.size() == 2,
                "closed callbacks must neither send nor copy an old report");
    }

    private static void failureAndDeferredEntryRemainFailClosed() {
        var session = new IssueReportSession(versions());
        session.review(complete(IssueReport.ModList.empty()));
        session.open(uri -> { throw new IllegalStateException("synthetic opener failure"); });
        check(session.state() == IssueReportSession.State.FAILED
                        && session.copyText(value -> { }), "browser failure must retain local copy recovery");
        check(!session.copyText(value -> { throw new IllegalStateException("synthetic clipboard failure"); }),
                "clipboard failure must not pretend success");
        Object level = new Object(), screen = new Object();
        check(IssueReportSession.canOpenRequest(level, level, screen, screen, true)
                        && IssueReportSession.canOpenRequest(level, level, screen, null, true)
                        && !IssueReportSession.canOpenRequest(level, new Object(), screen, screen, true)
                        && !IssueReportSession.canOpenRequest(level, level, screen, new Object(), true)
                        && !IssueReportSession.canOpenRequest(level, level, screen, screen, false),
                "deferred chat entry must reject a replaced screen, world or player");
        check(IssueReportSession.isReportCommand("/slabbed_report_issue")
                        && !IssueReportSession.isReportCommand("/slabbed_report_issue extra"),
                "only the exact local report action may be consumed");
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

    private static void pauseMenuNearbyPlacement() {
        for (int[] size : new int[][] {{976, 640}, {854, 480}, {640, 360}, {427, 240}, {320, 240}, {320, 180}}) {
            int center = size[0] / 2;
            int firstRow = (size[1] - 116) / 4;
            var occupied = new ArrayList<IssueReportPlacement.Box>();
            occupied.add(new IssueReportPlacement.Box(center - 100, 32, 200, 20));
            occupied.add(new IssueReportPlacement.Box(center - 102, firstRow, 204, 20));
            for (int row = 1; row < 4; row++) {
                occupied.add(new IssueReportPlacement.Box(center - 102, firstRow + row * 24, 98, 20));
                occupied.add(new IssueReportPlacement.Box(center + 4, firstRow + row * 24, 98, 20));
            }
            var disconnect = new IssueReportPlacement.Box(center - 102, firstRow + 96, 204, 20);
            occupied.add(disconnect);
            int preferredY = disconnect.y() + disconnect.height() + 8;
            var button = IssueReportPlacement.findNear(size[0], size[1], 176, preferredY, occupied).orElseThrow();
            check(occupied.stream().noneMatch(button::overlaps), "nearby pause entry avoids all menu controls");
            check(button.x() == center - 88 && button.y() == preferredY,
                    "pause report stays centered just below Save and Quit at every tested scale");
            occupied.add(new IssueReportPlacement.Box(button.x(), button.y(), 40, 20));
            var relocated = IssueReportPlacement.findNear(size[0], size[1], 176, preferredY, occupied).orElseThrow();
            check(occupied.stream().noneMatch(relocated::overlaps), "nearby entry moves around another mod");
        }
        check(IssueReportPlacement.findNear(320, 240, 176, 140,
                List.of(new IssueReportPlacement.Box(0, 0, 320, 240))).isEmpty(),
                "fully crowded nearby placement never overlaps controls");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
