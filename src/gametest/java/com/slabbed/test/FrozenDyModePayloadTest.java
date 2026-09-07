package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.network.FrozenDyModeMessages;
import com.slabbed.network.FrozenDyModePayload;
import com.slabbed.network.FrozenDyModeServer;
import io.netty.buffer.Unpooled;
import java.util.Locale;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * Headless proof for the join-time stored-height compatibility notice: the payload survives the
 * wire in both directions, the server reports the flag LIVE rather than a copy taken at class load,
 * and the two mismatch messages each name the side that is actually out of step.
 *
 * <p>All three rows are pure logic and run entirely server-side — the notice needs no client venue
 * to be proven, which is why the message text lives in a common class.
 *
 * <p>What is NOT provable here: that a real cross-JVM mismatch fires the chat line. Both automated
 * venues force {@code slabbed.frozenDy} to a single value per JVM, and a client GameTest talks to an
 * integrated server in that same JVM, so the two sides can never disagree under this harness. That
 * one row is live-only.
 */
public final class FrozenDyModePayloadTest {

    private static final String[] FORBIDDEN_JARGON = {"frozendy", "attachment", "store-only"};

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void codecRoundTrips(GameTestHelper helper) {
        // BOTH directions are required: a write or read hardcoded to one constant round-trips
        // correctly for exactly one of them.
        assertRoundTrip(helper, true);
        assertRoundTrip(helper, false);
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void serverReportsLiveFlagTracksBothValues(GameTestHelper helper) {
        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        try {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
            if (!FrozenDyModeServer.currentPayload().frozenDyEnabled()) {
                throw helper.assertionException(
                        "the join payload must report the flag as it stands: the field was ON and the "
                                + "payload said OFF");
            }
            SlabAnchorAttachment.FROZEN_DY_ENABLED = false;
            if (FrozenDyModeServer.currentPayload().frozenDyEnabled()) {
                throw helper.assertionException(
                        "the join payload must report the flag as it stands: the field was OFF and the "
                                + "payload said ON");
            }
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previous;
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void mismatchMessageNamesTheOffSide(GameTestHelper helper) {
        String serverOff = lowerText(false, true);
        String clientOff = lowerText(true, false);
        if (serverOff.equals(clientOff)) {
            throw helper.assertionException(
                    "the two mismatch directions must not read identically — the player is told which "
                            + "side to fix");
        }
        if (!serverOff.contains("server") || serverOff.contains("client")) {
            throw helper.assertionException(
                    "the server-side-out-of-step message must name the server and only the server — got: "
                            + serverOff);
        }
        if (!clientOff.contains("client") || clientOff.contains("server")) {
            throw helper.assertionException(
                    "the client-side-out-of-step message must name the client and only the client — got: "
                            + clientOff);
        }
        assertPlainLanguage(helper, serverOff);
        assertPlainLanguage(helper, clientOff);
        helper.succeed();
    }

    private static void assertRoundTrip(GameTestHelper helper, boolean value) {
        RegistryFriendlyByteBuf buffer = newBuffer(helper);
        FrozenDyModePayload.CODEC.encode(buffer, new FrozenDyModePayload(value));
        FrozenDyModePayload decoded = FrozenDyModePayload.CODEC.decode(buffer);
        if (decoded.frozenDyEnabled() != value) {
            throw helper.assertionException(
                    "the frozen-dy mode payload must survive the wire verbatim: wrote " + value
                            + ", read back " + decoded.frozenDyEnabled());
        }
    }

    private static void assertPlainLanguage(GameTestHelper helper, String text) {
        for (String jargon : FORBIDDEN_JARGON) {
            if (text.contains(jargon)) {
                throw helper.assertionException(
                        "the mismatch message is read by players and must carry no internal vocabulary,"
                                + " but contains \"" + jargon + "\" — got: " + text);
            }
        }
    }

    private static String lowerText(boolean serverEnabled, boolean clientEnabled) {
        return FrozenDyModeMessages.mismatchMessage(serverEnabled, clientEnabled)
                .getString()
                .toLowerCase(Locale.ROOT);
    }

    private static RegistryFriendlyByteBuf newBuffer(GameTestHelper helper) {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
    }
}
