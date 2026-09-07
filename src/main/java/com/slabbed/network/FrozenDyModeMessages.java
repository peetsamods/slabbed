package com.slabbed.network;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * The player-facing wording for a stored-height compatibility mismatch between a client and the
 * server it joined.
 *
 * <p><b>This class is COMMON on purpose — do not move it into the client source set.</b> The
 * wording is pure boolean-in, {@link Component}-out logic with no client dependency, and keeping it
 * here is what lets a server GameTest prove it. Moving it client-side would put the only testable
 * logic in this feature into a venue the suite-count gate does not run.
 *
 * <p>Two invariants the tests pin:
 * <ul>
 *   <li>The two directions must read differently and must each name only the side that is out of
 *       step — a message naming the wrong side sends the player to the wrong person.</li>
 *   <li>The text carries no internal vocabulary (the launch property's name, "attachment",
 *       "store-only"). It is read by players, and the fix usually belongs to whoever launches the
 *       other side (maintainer ruling, 2026-09-06).</li>
 * </ul>
 */
public final class FrozenDyModeMessages {

    private FrozenDyModeMessages() {
    }

    /**
     * The one condition that produces a warning: the two sides disagree.
     *
     * <p>A named seam rather than an inline {@code !=} at the call site, so the comparison has one
     * place to be proven and one place to break.
     */
    public static boolean sidesMismatch(boolean serverEnabled, boolean clientEnabled) {
        return serverEnabled != clientEnabled;
    }

    /**
     * The chat line shown when the sides disagree. The caller guards on
     * {@link #sidesMismatch(boolean, boolean)}; this method does not re-check, so a test may call it
     * with any pair.
     *
     * <p>Styled as a warning (yellow) rather than borrowing the beta notice's gray italic: this one
     * reports a real disagreement the player may need to act on, not a standing disclaimer.
     */
    public static Component mismatchMessage(boolean serverEnabled, boolean clientEnabled) {
        if (!serverEnabled) {
            return Component.literal(
                            "Slabbed: this server has the older block-height behaviour turned on and you"
                                    + " do not. Blocks can be drawn at a different height than the one the"
                                    + " server actually places you against. Ask whoever runs the server to"
                                    + " match your launch setting, or match theirs.")
                    .withStyle(ChatFormatting.YELLOW);
        }
        return Component.literal(
                        "Slabbed: your client has the older block-height behaviour turned on and this"
                                + " world does not. Blocks can be drawn at a different height than the one"
                                + " you are actually placed against. Restart your game with the same launch"
                                + " setting this world uses.")
                .withStyle(ChatFormatting.YELLOW);
    }
}
