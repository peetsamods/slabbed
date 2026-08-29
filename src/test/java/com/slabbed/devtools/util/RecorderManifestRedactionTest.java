package com.slabbed.devtools.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

final class RecorderManifestRedactionTest {
    @Test
    void redactsEverySensitiveLaunchArgumentValue() {
        String command = "client.jar --username player --accessToken eyJhbGciOi.secret.value"
                + " --uuid 00000000-0000-0000-0000-000000000000 --xuid 123 --clientId abc"
                + " --session token --version 1.21.1";
        String redacted = LiveCursorIntentRecorder.redactJavaCommand(command);
        assertFalse(redacted.contains("eyJhbGciOi.secret.value"), redacted);
        assertFalse(redacted.contains("00000000-0000-0000-0000-000000000000"), redacted);
        assertEquals(
                "client.jar --username player --accessToken [REDACTED] --uuid [REDACTED]"
                        + " --xuid [REDACTED] --clientId [REDACTED] --session [REDACTED]"
                        + " --version 1.21.1",
                redacted);
    }

    @Test
    void leavesNonSensitiveCommandsUntouched() {
        assertEquals("server.jar nogui", LiveCursorIntentRecorder.redactJavaCommand("server.jar nogui"));
        assertEquals("", LiveCursorIntentRecorder.redactJavaCommand(""));
        assertNull(LiveCursorIntentRecorder.redactJavaCommand(null));
    }
}
