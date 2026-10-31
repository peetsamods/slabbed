package com.slabbed.client;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Immutable reviewed text shared by the preview, clipboard and browser. */
record IssueReport(String minecraft, String loader, String slabbed, String fabricApi,
                   String title, String steps, String happened, WorldType worldType, ModList mods) {
    static final String DESTINATION = "https://github.com/peetsamods/slabbed/issues/new";
    static final int MAX_URL_LENGTH = 2048;
    static final int MAX_VERSION_LENGTH = 80;
    static final int MAX_TITLE_LENGTH = 100;
    static final int MAX_MOD_ROWS = 4096;
    private static final Set<String> STARTER_TEXT = Set.of(
            "describe the symptoms here.", "describe the symptoms here",
            "describe what you expected.", "describe what you expected",
            "tell me what you clicked or did, in order, so i can try it too.",
            "tell me what you clicked or did, in order, so i can try it too. 1.", "1.");

    enum WorldType {
        UNSPECIFIED("Choose one"), SINGLEPLAYER("Singleplayer"), MULTIPLAYER("Multiplayer"),
        MENUS("Before joining a world");
        private final String label;
        WorldType(String label) { this.label = label; }
        String label() { return label; }
    }

    /** Raw loaded-client metadata; no other metadata field is accepted by this boundary. */
    record ClientMod(String id, String version) { }

    static final class ModList {
        private final List<ClientMod> rows;
        private final int omitted;

        private ModList(List<ClientMod> rows, int omitted) {
            this.rows = List.copyOf(rows);
            this.omitted = omitted;
        }

        static ModList empty() { return new ModList(List.of(), 0); }
        List<ClientMod> rows() { return rows; }
        int omitted() { return omitted; }
        boolean hasContext() { return !rows.isEmpty() || omitted > 0; }
        String text() {
            return String.join("\n", rows.stream().map(row -> row.id() + " " + row.version()).toList());
        }
        ModList withAdditionalOmissions(int count) {
            return new ModList(rows, (int) Math.min(Integer.MAX_VALUE, (long) omitted + Math.max(0, count)));
        }
    }

    IssueReport(String minecraft, String loader, String slabbed, String fabricApi) {
        this(minecraft, loader, slabbed, fabricApi, "", "", "", WorldType.UNSPECIFIED, ModList.empty());
    }

    IssueReport {
        minecraft = safeVersion(minecraft);
        loader = safeVersion(loader);
        slabbed = safeVersion(slabbed);
        fabricApi = safeVersion(fabricApi);
        title = title == null ? "" : title.strip();
        steps = steps == null ? "" : steps.strip();
        happened = happened == null ? "" : happened.strip();
        worldType = worldType == null ? WorldType.UNSPECIFIED : worldType;
        mods = mods == null ? ModList.empty() : mods;
    }

    private static String safeVersion(String value) {
        // Reject, rather than truncate, malformed version context.
        return value != null && value.length() <= MAX_VERSION_LENGTH
                && value.matches("[0-9][0-9A-Za-z.+_-]*") ? value : "unavailable";
    }

    IssueReport withDetails(String title, String steps, String happened, WorldType type, ModList mods) {
        return new IssueReport(minecraft, loader, slabbed, fabricApi, title, steps, happened, type, mods);
    }

    IssueReport versionsOnly() { return new IssueReport(minecraft, loader, slabbed, fabricApi); }

    boolean valid() {
        return !title.isBlank() && title.length() <= MAX_TITLE_LENGTH && wellFormed(title)
                && meaningful(steps) && meaningful(happened) && worldType != WorldType.UNSPECIFIED;
    }

    static boolean meaningful(String text) {
        if (text == null || text.isBlank() || !wellFormed(text)) return false;
        String normalized = text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return !STARTER_TEXT.contains(normalized) && text.codePoints().anyMatch(Character::isLetterOrDigit);
    }

    private static boolean wellFormed(String text) {
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (Character.isHighSurrogate(value)) {
                if (++index >= text.length() || !Character.isLowSurrogate(text.charAt(index))) return false;
            } else if (Character.isLowSurrogate(value)) return false;
        }
        return true;
    }

    static ModList collectMods(Iterable<ClientMod> input) {
        var rows = new TreeMap<String, ClientMod>();
        int inspected = 0;
        int omitted = 0;
        if (input == null) return new ModList(List.of(), 1);
        for (ClientMod row : input) {
            if (inspected++ >= MAX_MOD_ROWS || row == null || row.id() == null || row.version() == null
                    || row.id().length() > 64 || row.version().length() > MAX_VERSION_LENGTH
                    || !row.id().matches("[a-z][a-z0-9_-]*")
                    || !row.version().matches("[0-9A-Za-z][0-9A-Za-z.+_-]*")) {
                omitted++;
            } else if (rows.putIfAbsent(row.id(), row) != null) omitted++;
        }
        return new ModList(List.copyOf(rows.values()), omitted);
    }

    static ModList editedMods(String text) {
        var rows = new ArrayList<ClientMod>();
        if (text != null) {
            text.lines().filter(line -> !line.isBlank()).forEach(line -> {
                String[] pair = line.strip().split("\\s+", 2);
                rows.add(new ClientMod(pair[0], pair.length == 2 ? pair[1] : null));
            });
        }
        return collectMods(rows);
    }

    String diagnostics() {
        return "Minecraft: " + minecraft + "\nFabric Loader: " + loader
                + "\nSlabbed: " + slabbed + "\nFabric API: " + fabricApi;
    }

    String body() {
        return "### Client versions\n" + diagnostics()
                + "\n\n### Where were you playing?\n" + worldType.label()
                + "\n\n### What did you do?\n" + steps
                + "\n\n### What happened?\n" + happened
                + (mods.hasContext() ? "\n\n### Included client mods\n"
                + "Client IDs and versions reviewed by the reporter; this list may be edited or partial."
                + " Server-only mods, resource packs, shaders and configuration are not automatically collected.\n"
                + (mods.rows().isEmpty() ? "No mod entries included.\n" : "~~~text\n" + mods.text() + "\n~~~\n")
                + (mods.omitted() == 0 ? "" : "Omitted " + mods.omitted()
                + " invalid, duplicate or over-limit entries; this is not a complete inventory.\n") : "") + "\n";
    }

    String copyText() { return "Title: " + title + "\n\n" + body(); }

    Optional<URI> prefilledUri() {
        if (!valid()) return Optional.empty();
        String body = body();
        // Encoding cannot make these strings shorter. Avoid encoding large clipboard-only reports.
        if (DESTINATION.length() + title.length() + body.length() > MAX_URL_LENGTH) return Optional.empty();
        String url = DESTINATION + "?title=" + URLEncoder.encode(title, StandardCharsets.UTF_8) + "&body="
                + URLEncoder.encode(body, StandardCharsets.UTF_8);
        return url.length() <= MAX_URL_LENGTH ? Optional.of(URI.create(url)) : Optional.empty();
    }

    URI browserUri() {
        if (!valid()) throw new IllegalStateException("An actual description must be reviewed first");
        return prefilledUri().orElseGet(() -> URI.create(DESTINATION));
    }
}
