package com.slabbed.client;

import java.util.Objects;

/**
 * Release-safe client-side seam between the shipped {@code /slabdy} and {@code /slabdev} commands
 * and the two client debug tools they drive — the target-dy overlay and the live-cursor recorder.
 *
 * <p>Both tools live in the development-only diagnostics companion mod and are, by design, absent
 * from a release jar. The shipped commands must still be invocable there (the maintainer's 2026-08-07
 * ruling), and they must not throw when the implementation is missing — so they never name those
 * classes at all. They call this seam; the companion installs a provider into it at client init.
 * With no provider installed {@link #available()} is false and the commands report
 * "not available in this build" and change nothing.
 *
 * <p>This is the same architecture {@code com.slabbed.util.SlabbedDiagnosticsBridge} already
 * establishes for the recorder's data path, applied to the command surface. It is deliberately the
 * client-side twin rather than more methods on that bridge: the overlay is a client-only tool and
 * that bridge's single provider slot is owned by the companion's <em>common</em> initializer.
 *
 * <p>Inert by construction: no hooks, no I/O, one volatile field read per command invocation.
 */
public final class SlabbedDebugToolBridge {

    /** Everything the shipped commands can ask of the development-only client debug tools. */
    public interface Provider {
        default boolean overlayEnabled() {
            return false;
        }

        default void setOverlayEnabled(boolean enabled) {
        }

        default boolean recorderEnabled() {
            return false;
        }

        default void setRecorderEnabled(boolean enabled) {
        }

        /** Where the recorder is writing, for the command's feedback line. Never null. */
        default String recorderStatus() {
            return "";
        }
    }

    private static volatile Provider provider;

    private SlabbedDebugToolBridge() {
    }

    public static void install(Provider next) {
        provider = Objects.requireNonNull(next, "next");
    }

    /** False in a release jar: the tools behind this seam are not shipped. */
    public static boolean available() {
        return provider != null;
    }

    public static boolean overlayEnabled() {
        Provider current = provider;
        return current != null && current.overlayEnabled();
    }

    public static void setOverlayEnabled(boolean enabled) {
        Provider current = provider;
        if (current != null) {
            current.setOverlayEnabled(enabled);
        }
    }

    public static boolean recorderEnabled() {
        Provider current = provider;
        return current != null && current.recorderEnabled();
    }

    public static void setRecorderEnabled(boolean enabled) {
        Provider current = provider;
        if (current != null) {
            current.setRecorderEnabled(enabled);
        }
    }

    public static String recorderStatus() {
        Provider current = provider;
        return current == null ? "" : current.recorderStatus();
    }
}
