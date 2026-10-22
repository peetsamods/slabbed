package com.slabbed.client;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Immutable, version-only snapshot shared by the preview, clipboard and browser. */
record IssueReport(String minecraft, String loader, String slabbed, String fabricApi) {
    static final String DESTINATION = "https://github.com/peetsamods/slabbed/issues/new";
    static final int MAX_URL_LENGTH = 2048;
    static final int MAX_VERSION_LENGTH = 80;

    IssueReport {
        minecraft = safeVersion(minecraft);
        loader = safeVersion(loader);
        slabbed = safeVersion(slabbed);
        fabricApi = safeVersion(fabricApi);
    }

    private static String safeVersion(String value) {
        // Reject, rather than truncate, malformed metadata. No free-form input enters the report.
        return value != null && value.length() <= MAX_VERSION_LENGTH
                && value.matches("[0-9][0-9A-Za-z.+_-]*") ? value : "unavailable";
    }

    String diagnostics() {
        return "Minecraft: " + minecraft + "\nFabric Loader: " + loader
                + "\nSlabbed: " + slabbed + "\nFabric API: " + fabricApi;
    }

    String body() {
        return "### Client versions\n" + diagnostics()
                + "\n\n### What happened?\nDescribe the symptoms here."
                + "\n\n### Steps to reproduce\n1. \n"
                + "\n### Expected behavior\nDescribe what you expected.\n";
    }

    URI uri() {
        String url = DESTINATION + "?title=Slabbed+issue&body="
                + URLEncoder.encode(body(), StandardCharsets.UTF_8);
        if (url.length() > MAX_URL_LENGTH) {
            throw new IllegalStateException("Version report exceeds the safe URL limit");
        }
        return URI.create(url);
    }
}
