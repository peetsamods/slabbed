package com.slabbed.mixin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ServerInteractionOwnershipContractTest {
    private static final Path SERVER_MIXIN = Path.of(
            "src/main/java/com/slabbed/mixin/ServerInteractBlockHitToleranceMixin.java");
    private static final Path BLOCK_ITEM_MIXIN = Path.of(
            "src/main/java/com/slabbed/mixin/BlockItemPlacementIntentMixin.java");
    private static final Path RAYCAST = Path.of(
            "src/main/java/com/slabbed/util/SlabbedOffsetRaycast.java");
    private static final Path DEPTH_POLICY = Path.of(
            "src/main/java/com/slabbed/util/PlacementDepthPolicy.java");
    private static final Path SERVER_POLICY = Path.of(
            "src/main/java/com/slabbed/util/SlabbedServerHitValidation.java");

    @Test
    void serverValidatesTheSelectedOwnerButVanillaOwnsTheMutation() throws IOException {
        String server = Files.readString(SERVER_MIXIN);

        assertAll(
                () -> assertTrue(server.contains("SlabbedServerHitValidation.validationCenter"),
                        "packet handling must delegate to the shared shifted-owner validator"),
                () -> assertFalse(server.contains("world.setBlock("),
                        "packet validation must not write a block itself"),
                () -> assertFalse(server.contains(".consume("),
                        "packet validation must not consume an item itself"),
                () -> assertFalse(server.contains(".swing("),
                        "packet validation must not synthesize a second hand animation"),
                () -> assertFalse(server.contains("ci.cancel("),
                        "packet validation must not cancel vanilla after a private mutation"),
                () -> assertFalse(server.contains("@Inject"),
                        "the server bridge must remain a validation-center redirect only"));
    }

    @Test
    void clientPickServerValidationAndHeldUseShareOneTypedDepthPolicy() throws IOException {
        assertTrue(Files.isRegularFile(DEPTH_POLICY),
                "the phase must define one client-neutral typed depth policy");
        assertTrue(Files.isRegularFile(SERVER_POLICY),
                "the server must expose a pure shifted-owner validation seam");

        String depthPolicy = Files.readString(DEPTH_POLICY);
        String serverPolicy = Files.readString(SERVER_POLICY);
        String raycast = Files.readString(RAYCAST);
        String heldUse = Files.readString(BLOCK_ITEM_MIXIN);

        assertAll(
                () -> assertTrue(depthPolicy.contains("MIN_TARGETABLE_DY = -3.0d"),
                        "exactly -3.0 must be the shared lower interaction boundary"),
                () -> assertTrue(depthPolicy.contains("REFUSED_BELOW_TARGETABLE_FLOOR"),
                        "below-envelope admission must have a named typed refusal"),
                () -> assertTrue(raycast.contains("PlacementDepthPolicy.ownerWindowRadius()"),
                        "client targeting must derive its owner window from the shared policy"),
                () -> assertTrue(serverPolicy.contains("PlacementDepthPolicy.classify"),
                        "server validation must classify the same owner depth"),
                () -> assertTrue(heldUse.contains("PlacementDepthPolicy.classify"),
                        "real held use must apply the same decision before placement mutation"),
                () -> assertTrue(heldUse.contains("InteractionResult.FAIL"),
                        "refusal must preserve its typed interaction result"));
    }
}
