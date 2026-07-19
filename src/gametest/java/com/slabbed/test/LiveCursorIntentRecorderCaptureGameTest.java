package com.slabbed.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.slabbed.Slabbed;
import com.slabbed.util.LiveCursorIntentRecorder;
import com.slabbed.util.PlacementVerificationVerdict;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * HEADLESS capture-path proof for the revived {@link LiveCursorIntentRecorder}.
 *
 * <p>The pre-existing contract test ({@code SlabbedLabLiveCursorIntentRecorderContractClientGameTest})
 * is a {@link net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest} and needs the client
 * gametest harness ({@code runClientGameTest}); this branch's legacy client tests are Yarn-mapped and
 * deferred, so that harness is not exercised in the normal {@code runGameTest} suite. This headless
 * server {@code GameTest} drives the recorder's public API directly and asserts the on-disk capture
 * files, so the revival's core capture path is proven by the reliable server harness regardless of
 * the client harness's state.
 *
 * <p>Against the pre-revival inert stub every {@code record*} method was a no-op, so NONE of the
 * asserted files would ever be written — this test fails RED. After the revival it goes GREEN.
 */
public final class LiveCursorIntentRecorderCaptureGameTest {

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void c3_early_non_success_none_shape(GameTestHelper helper) {
        Path dir = Path.of("build", "c3-recorder-early-none", "run-" + System.nanoTime());
        System.setProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY, "true");
        System.setProperty(LiveCursorIntentRecorder.DIR_PROPERTY, dir.toString());
        try {
            LiveCursorIntentRecorder.resetForTests();
            BlockPos owner = helper.absolutePos(new BlockPos(3, 3, 3));
            BlockPos blocked = owner.above();
            helper.getLevel().setBlock(owner, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            helper.getLevel().setBlock(blocked, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            ItemStack stack = new ItemStack(Items.STONE);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            Vec3 hit = Vec3.atCenterOf(owner).add(0.0d, 0.5d, 0.0d);
            stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(hit, Direction.UP, owner, false)));
            LiveCursorIntentRecorder.flushSummaryForTests();

            String json = Files.readString(dir.resolve("session.jsonl"));
            String tsv = Files.readString(dir.resolve("actions.tsv"));
            for (String needle : new String[]{
                    "\"clickedOwnerPos\":\"" + owner.toShortString() + "\"",
                    "\"clickedFace\":\"up\"",
                    "\"placementPos\":\"none\"",
                    "\"afterState\":\"none\"",
                    "\"afterDy\":\"none\"",
                    "\"afterStoredDy\":\"none\"",
                    "\"afterStoredDyBits\":\"none\"",
                    "\"pairPos\":\"none\"",
                    "\"pairPart\":\"none\"",
                    "\"pairState\":\"none\"",
                    "\"pairAfterDy\":\"none\"",
                    "\"pairStoredDy\":\"none\"",
                    "\"pairStoredDyBits\":\"none\""
            }) {
                if (!json.contains(needle)) {
                    throw helper.assertionException("real early non-success action missing " + needle);
                }
            }
            String expectedHeader = "actionId\tcursorRowId\tactionType\tactionOrigin\theldItem"
                    + "\tclickedOwnerPos\tclickedFace\tplacementPos\texpectedAfterDy\tafterDy"
                    + "\texpectedAfterLaneKind\tafterLaneKind\tmarker\tafterStoredDy"
                    + "\tafterStoredDyBits\tpairPos\tpairPart\tpairState\tpairAfterDy"
                    + "\tpairStoredDy\tpairStoredDyBits\tlogicalAttemptId\tphase\tplayerProof";
            String[] tsvLines = tsv.strip().split("\\R");
            String[] actionColumns = tsvLines.length == 2 ? tsvLines[1].split("\t", -1) : new String[0];
            if (tsvLines.length != 2
                    || !tsvLines[0].equals(expectedHeader)
                    || actionColumns.length != 24
                    || !actionColumns[5].equals(owner.toShortString())
                    || !actionColumns[6].equals("up")
                    || !actionColumns[7].equals("none")
                    || !actionColumns[8].equals("unknown")
                    || !actionColumns[9].equals("none")
                    || !actionColumns[10].equals("unknown")
                    || !actionColumns[11].equals("none")
                    || !actionColumns[12].equals("LIVE_PLACEMENT_UNCLASSIFIED_FAILURE")
                    || !java.util.Arrays.stream(actionColumns, 13, 21).allMatch("none"::equals)
                    || actionColumns[21].isBlank()
                    || !actionColumns[22].equals("SERVER_AUTHORITY")
                    || !actionColumns[23].equals("PRESENT")) {
                throw helper.assertionException("real early non-success TSV did not preserve root plus exact none shape");
            }
            if (!json.contains("\"marker\":\"LIVE_PLACEMENT_UNCLASSIFIED_FAILURE\"")) {
                throw helper.assertionException("real early non-success action was not marked red");
            }
            assertContains(helper, dir.resolve("mismatches.tsv"),
                    "LIVE_PLACEMENT_UNCLASSIFIED_FAILURE");
            assertContains(helper, dir.resolve("summary.md"),
                    "placementUnclassifiedFailureRows=1");
            Slabbed.LOGGER.info("C3_FOCUSED | slabbed_gametest:live_cursor_intent_recorder_capture_game_test_c3_early_non_success_none_shape | PASS");
            helper.succeed();
        } catch (IOException exception) {
            throw helper.assertionException("C3 early non-success recorder proof failed: " + exception);
        } finally {
            System.clearProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY);
            System.clearProperty(LiveCursorIntentRecorder.DIR_PROPERTY);
            LiveCursorIntentRecorder.resetForTests();
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void verifier_truth_action_only_placement_is_inconclusive(GameTestHelper helper) {
        Path dir = Path.of("build", "verifier-truth-recorder-action-only", "run-" + System.nanoTime());
        System.setProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY, "true");
        System.setProperty(LiveCursorIntentRecorder.DIR_PROPERTY, dir.toString());
        try {
            LiveCursorIntentRecorder.resetForTests();
            String callerActionId = "verifier-truth-action-only";
            LinkedHashMap<String, String> action = ordinaryAction(
                    "minecraft:stone", "70,64,70", "70,65,70",
                    "-0.500000", "-0.500000", "SUCCESS");
            action.put("actionId", callerActionId);
            StringBuilder fixtureObservableKeys = new StringBuilder();
            for (String key : action.keySet()) {
                boolean actualObservablePayload = key.startsWith("stored")
                        || key.startsWith("afterStored")
                        || key.equals("modelDy")
                        || key.equals("collisionDy")
                        || key.equals("raycastDy")
                        || key.equals("outlineDy")
                        || key.equals("actualContactPlane")
                        || key.equals("seatError")
                        || key.equals("actualRefusalReason")
                        || key.startsWith("stability");
                if (actualObservablePayload) {
                    appendCsv(fixtureObservableKeys, key);
                }
            }
            if (!fixtureObservableKeys.isEmpty()) {
                throw helper.assertionException(
                        "action-only verifier fixture must not pre-seed actual observable payload keys: "
                                + fixtureObservableKeys);
            }
            LiveCursorIntentRecorder.recordAction(action);
            LiveCursorIntentRecorder.flushSummaryForTests();

            String sessionEvidence = read(helper, dir.resolve("session.jsonl")).strip();
            java.util.ArrayList<JsonObject> sessionRows = new java.util.ArrayList<>();
            for (String row : sessionEvidence.split("\\R")) {
                if (!row.isBlank()) {
                    sessionRows.add(parseJsonObject(helper, row));
                }
            }

            JsonObject actionEvidence = null;
            int matchingActionRows = 0;
            for (JsonObject row : sessionRows) {
                if (exactJsonString(row, "type", "action")) {
                    matchingActionRows++;
                    actionEvidence = row;
                }
            }
            String actionId = actionEvidence == null
                    ? null
                    : jsonString(actionEvidence, "actionId");
            if (matchingActionRows != 1
                    || actionEvidence == null
                    || !isRecorderOwnedActionId(actionId)
                    || callerActionId.equals(actionId)
                    || !exactJsonString(actionEvidence, "actionType", "place_block")
                    || !exactJsonString(actionEvidence, "actionOrigin", "PLAYER_AUTHORED")
                    || !exactJsonString(actionEvidence, "actualResult", "SUCCESS")
                    || !exactJsonString(actionEvidence, "expectedAfterDy", "-0.500000")
                    || !exactJsonString(actionEvidence, "afterDy", "-0.500000")
                    || !exactJsonString(actionEvidence, "afterLaneKind", "anchored_full_block")) {
                throw helper.assertionException(
                        "verifier-truth fixture did not preserve the successful lawful action-only placement: "
                                + actionEvidence);
            }

            String[] canonicalVerdictFields = {
                    "finalVerdict",
                    "placedVerdict", "anchorVerdict", "modelVerdict", "collisionVerdict",
                    "raycastVerdict", "outlineVerdict", "stabilityVerdict",
                    "intentDy", "storedDy", "modelDy", "collisionDy", "raycastDy", "outlineDy",
                    "expectedSupportPlane", "actualContactPlane", "seatError", "placementRoute",
                    "landingAuthority", "rigCaseId", "expectedRefusalReason", "actualRefusalReason"
            };
            JsonObject verdictEvidence = null;
            int linkedVerdictRows = 0;
            boolean falseGreen = hasMarkerToken(actionEvidence, "LIVE_GREEN_PLACEMENT_AUTHORING");
            for (JsonObject row : sessionRows) {
                boolean sameActionRow = row == actionEvidence;
                boolean exactLinkedRow = exactJsonString(row, "sourceActionId", actionId)
                        || exactJsonString(row, "forActionId", actionId)
                        || (!sameActionRow && exactJsonString(row, "actionId", actionId));
                if (!sameActionRow && !exactLinkedRow) {
                    continue;
                }
                falseGreen |= hasMarkerToken(row, "LIVE_GREEN_PLACEMENT_AUTHORING");
                if (hasAnyJsonField(row, canonicalVerdictFields)) {
                    linkedVerdictRows++;
                    verdictEvidence = row;
                }
            }
            JsonObject inspectedVerdict = verdictEvidence == null ? actionEvidence : verdictEvidence;
            String finalVerdict = jsonString(inspectedVerdict, "finalVerdict");
            boolean inconclusive = "INCONCLUSIVE".equals(finalVerdict);

            String[][] componentContracts = {
                    {"placedVerdict", "PASS", "UNKNOWN", "MISSING"},
                    {"anchorVerdict", "UNKNOWN", "MISSING"},
                    {"modelVerdict", "UNKNOWN", "MISSING"},
                    {"collisionVerdict", "UNKNOWN", "MISSING"},
                    {"raycastVerdict", "UNKNOWN", "MISSING"},
                    {"outlineVerdict", "UNKNOWN", "MISSING"},
                    {"stabilityVerdict", "UNKNOWN", "MISSING", "NOT_RUN"}
            };
            StringBuilder invalidComponentStates = new StringBuilder();
            StringBuilder forbiddenPassComponents = new StringBuilder();
            for (String[] contract : componentContracts) {
                String field = contract[0];
                String state = jsonString(inspectedVerdict, field);
                boolean allowed = false;
                for (int index = 1; index < contract.length; index++) {
                    allowed |= contract[index].equals(state);
                }
                if (!allowed) {
                    appendCsv(invalidComponentStates,
                            field + "=" + (state == null ? "<missing-or-non-string>" : state));
                }
                if (!field.equals("placedVerdict") && "PASS".equals(state)) {
                    appendCsv(forbiddenPassComponents, field);
                }
            }

            String[] canonicalEvidenceFields = {
                    "intentDy", "storedDy", "modelDy", "collisionDy", "raycastDy", "outlineDy",
                    "expectedSupportPlane", "actualContactPlane", "seatError", "placementRoute",
                    "landingAuthority", "rigCaseId", "expectedRefusalReason", "actualRefusalReason"
            };
            StringBuilder missingCanonicalFields = new StringBuilder();
            StringBuilder nonScalarCanonicalFields = new StringBuilder();
            StringBuilder canonicalFieldsManufacturedAsPass = new StringBuilder();
            for (String field : canonicalEvidenceFields) {
                JsonElement value = inspectedVerdict.get(field);
                if (value == null || value.isJsonNull()) {
                    appendCsv(missingCanonicalFields, field);
                } else if (!value.isJsonPrimitive()) {
                    appendCsv(nonScalarCanonicalFields, field);
                } else if (value.getAsJsonPrimitive().isString()
                        && "PASS".equals(value.getAsString())) {
                    appendCsv(canonicalFieldsManufacturedAsPass, field);
                }
            }

            if (linkedVerdictRows != 1
                    || !inconclusive
                    || !invalidComponentStates.isEmpty()
                    || !forbiddenPassComponents.isEmpty()
                    || !missingCanonicalFields.isEmpty()
                    || !nonScalarCanonicalFields.isEmpty()
                    || !canonicalFieldsManufacturedAsPass.isEmpty()
                    || falseGreen) {
                throw helper.assertionException(
                        "canonical action-linked verifier verdict must be structurally INCONCLUSIVE with "
                                + "explicit component states and evidence fields, and must not emit the exact "
                                + "LIVE_GREEN_PLACEMENT_AUTHORING marker token; linkedVerdictRows="
                                + linkedVerdictRows + ", finalVerdict="
                                + (finalVerdict == null ? "<missing-or-non-string>" : finalVerdict)
                                + ", invalidComponentStates="
                                + (invalidComponentStates.isEmpty() ? "none" : invalidComponentStates)
                                + ", forbiddenPassComponents="
                                + (forbiddenPassComponents.isEmpty() ? "none" : forbiddenPassComponents)
                                + ", missingCanonicalFields="
                                + (missingCanonicalFields.isEmpty() ? "none" : missingCanonicalFields)
                                + ", nonScalarCanonicalFields="
                                + (nonScalarCanonicalFields.isEmpty() ? "none" : nonScalarCanonicalFields)
                                + ", canonicalFieldsManufacturedAsPass="
                                + (canonicalFieldsManufacturedAsPass.isEmpty()
                                        ? "none" : canonicalFieldsManufacturedAsPass)
                                + ", falseGreenMarker=" + falseGreen
                                + ", actionEvidence=" + actionEvidence
                                + ", verdictEvidence="
                                + (verdictEvidence == null ? "none" : verdictEvidence));
            }
            helper.succeed();
        } finally {
            System.clearProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY);
            System.clearProperty(LiveCursorIntentRecorder.DIR_PROPERTY);
            LiveCursorIntentRecorder.resetForTests();
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void verifier_truth_reducer_matrix_all_final_outcomes_and_precedence(GameTestHelper helper) {
        LinkedHashMap<String, String> greenEvidence = fullyObservedVerifierAction();
        PlacementVerificationVerdict.Result green =
                PlacementVerificationVerdict.reduce(greenEvidence);
        assertFinalVerdict(helper, "green", green, PlacementVerificationVerdict.FinalVerdict.GREEN);
        for (PlacementVerificationVerdict.Component component
                : PlacementVerificationVerdict.Component.values()) {
            assertComponentVerdict(
                    helper, "green", green, component,
                    PlacementVerificationVerdict.ComponentStatus.PASS);
        }

        LinkedHashMap<String, String> missingOutlineWithForgedNotApplicableEvidence =
                fullyObservedVerifierAction();
        missingOutlineWithForgedNotApplicableEvidence.remove("outlineDy");
        missingOutlineWithForgedNotApplicableEvidence.put(
                "outlineVerdict", "NOT_APPLICABLE");
        PlacementVerificationVerdict.Result missingOutlineWithForgedNotApplicable =
                PlacementVerificationVerdict.reduce(
                        missingOutlineWithForgedNotApplicableEvidence);
        assertFinalVerdict(
                helper, "missing-outline-with-forged-not-applicable",
                missingOutlineWithForgedNotApplicable,
                PlacementVerificationVerdict.FinalVerdict.INCONCLUSIVE);
        assertComponentVerdict(
                helper, "missing-outline-with-forged-not-applicable",
                missingOutlineWithForgedNotApplicable,
                PlacementVerificationVerdict.Component.OUTLINE,
                PlacementVerificationVerdict.ComponentStatus.MISSING);
        assertContainsValue(
                helper, "missing-outline-with-forged-not-applicable required component",
                missingOutlineWithForgedNotApplicable.missingRequiredComponents(),
                PlacementVerificationVerdict.Component.OUTLINE);

        LinkedHashMap<String, String> missingModelWithForgedPassEvidence =
                fullyObservedVerifierAction();
        missingModelWithForgedPassEvidence.remove("modelDy");
        missingModelWithForgedPassEvidence.put("modelVerdict", "PASS");
        PlacementVerificationVerdict.Result missingModelWithForgedPass =
                PlacementVerificationVerdict.reduce(missingModelWithForgedPassEvidence);
        assertFinalVerdict(
                helper, "missing-model-with-forged-pass", missingModelWithForgedPass,
                PlacementVerificationVerdict.FinalVerdict.INCONCLUSIVE);
        assertComponentVerdict(
                helper, "missing-model-with-forged-pass", missingModelWithForgedPass,
                PlacementVerificationVerdict.Component.MODEL,
                PlacementVerificationVerdict.ComponentStatus.MISSING);
        assertContainsValue(
                helper, "missing-model-with-forged-pass required component",
                missingModelWithForgedPass.missingRequiredComponents(),
                PlacementVerificationVerdict.Component.MODEL);

        LinkedHashMap<String, String> missingRaycastWithForgedNotApplicableEvidence =
                fullyObservedVerifierAction();
        missingRaycastWithForgedNotApplicableEvidence.remove("raycastDy");
        missingRaycastWithForgedNotApplicableEvidence.put(
                "raycastVerdict", "NOT_APPLICABLE");
        PlacementVerificationVerdict.Result missingRaycastWithForgedNotApplicable =
                PlacementVerificationVerdict.reduce(
                        missingRaycastWithForgedNotApplicableEvidence);
        assertFinalVerdict(
                helper, "missing-raycast-with-forged-not-applicable",
                missingRaycastWithForgedNotApplicable,
                PlacementVerificationVerdict.FinalVerdict.INCONCLUSIVE);
        assertComponentVerdict(
                helper, "missing-raycast-with-forged-not-applicable",
                missingRaycastWithForgedNotApplicable,
                PlacementVerificationVerdict.Component.RAYCAST,
                PlacementVerificationVerdict.ComponentStatus.MISSING);
        assertContainsValue(
                helper, "missing-raycast-with-forged-not-applicable required component",
                missingRaycastWithForgedNotApplicable.missingRequiredComponents(),
                PlacementVerificationVerdict.Component.RAYCAST);

        LinkedHashMap<String, String> missingAnchorWithForgedNotApplicableEvidence =
                fullyObservedVerifierAction();
        missingAnchorWithForgedNotApplicableEvidence.remove("afterStoredDy");
        missingAnchorWithForgedNotApplicableEvidence.put(
                "anchorVerdict", "NOT_APPLICABLE");
        PlacementVerificationVerdict.Result missingAnchorWithForgedNotApplicable =
                PlacementVerificationVerdict.reduce(
                        missingAnchorWithForgedNotApplicableEvidence);
        assertFinalVerdict(
                helper, "missing-anchor-with-forged-not-applicable",
                missingAnchorWithForgedNotApplicable,
                PlacementVerificationVerdict.FinalVerdict.INCONCLUSIVE);
        assertComponentVerdict(
                helper, "missing-anchor-with-forged-not-applicable",
                missingAnchorWithForgedNotApplicable,
                PlacementVerificationVerdict.Component.ANCHOR,
                PlacementVerificationVerdict.ComponentStatus.MISSING);
        assertContainsValue(
                helper, "missing-anchor-with-forged-not-applicable required component",
                missingAnchorWithForgedNotApplicable.missingRequiredComponents(),
                PlacementVerificationVerdict.Component.ANCHOR);

        LinkedHashMap<String, String> missingCollisionWithForgedNotApplicableEvidence =
                fullyObservedVerifierAction();
        missingCollisionWithForgedNotApplicableEvidence.remove("collisionDy");
        missingCollisionWithForgedNotApplicableEvidence.remove("actualContactPlane");
        missingCollisionWithForgedNotApplicableEvidence.remove("seatError");
        missingCollisionWithForgedNotApplicableEvidence.put(
                "collisionVerdict", "NOT_APPLICABLE");
        PlacementVerificationVerdict.Result missingCollisionWithForgedNotApplicable =
                PlacementVerificationVerdict.reduce(
                        missingCollisionWithForgedNotApplicableEvidence);
        assertFinalVerdict(
                helper, "missing-collision-with-forged-not-applicable",
                missingCollisionWithForgedNotApplicable,
                PlacementVerificationVerdict.FinalVerdict.INCONCLUSIVE);
        assertComponentVerdict(
                helper, "missing-collision-with-forged-not-applicable",
                missingCollisionWithForgedNotApplicable,
                PlacementVerificationVerdict.Component.COLLISION,
                PlacementVerificationVerdict.ComponentStatus.MISSING);
        assertContainsValue(
                helper, "missing-collision-with-forged-not-applicable required component",
                missingCollisionWithForgedNotApplicable.missingRequiredComponents(),
                PlacementVerificationVerdict.Component.COLLISION);

        LinkedHashMap<String, String> missingStabilityWithForgedNotApplicableEvidence =
                fullyObservedVerifierAction();
        missingStabilityWithForgedNotApplicableEvidence.put(
                "stabilityVerdict", "NOT_APPLICABLE");
        PlacementVerificationVerdict.Result missingStabilityWithForgedNotApplicable =
                PlacementVerificationVerdict.reduce(
                        missingStabilityWithForgedNotApplicableEvidence);
        assertFinalVerdict(
                helper, "missing-stability-with-forged-not-applicable",
                missingStabilityWithForgedNotApplicable,
                PlacementVerificationVerdict.FinalVerdict.INCONCLUSIVE);
        assertComponentVerdict(
                helper, "missing-stability-with-forged-not-applicable",
                missingStabilityWithForgedNotApplicable,
                PlacementVerificationVerdict.Component.STABILITY,
                PlacementVerificationVerdict.ComponentStatus.MISSING);
        assertContainsValue(
                helper, "missing-stability-with-forged-not-applicable required component",
                missingStabilityWithForgedNotApplicable.missingRequiredComponents(),
                PlacementVerificationVerdict.Component.STABILITY);

        LinkedHashMap<String, String> notApplicableWithMatchingOutlineEvidence =
                fullyObservedVerifierAction();
        notApplicableWithMatchingOutlineEvidence.put("outlineVerdict", "NOT_APPLICABLE");
        PlacementVerificationVerdict.Result notApplicableWithMatchingOutline =
                PlacementVerificationVerdict.reduce(notApplicableWithMatchingOutlineEvidence);
        assertFinalVerdict(
                helper, "not-applicable-with-matching-outline",
                notApplicableWithMatchingOutline,
                PlacementVerificationVerdict.FinalVerdict.GREEN);
        assertComponentVerdict(
                helper, "not-applicable-with-matching-outline",
                notApplicableWithMatchingOutline,
                PlacementVerificationVerdict.Component.OUTLINE,
                PlacementVerificationVerdict.ComponentStatus.PASS);

        LinkedHashMap<String, String> notApplicableWithMismatchingOutlineEvidence =
                fullyObservedVerifierAction();
        notApplicableWithMismatchingOutlineEvidence.put("outlineVerdict", "NOT_APPLICABLE");
        notApplicableWithMismatchingOutlineEvidence.put("outlineDy", "-0.250000");
        PlacementVerificationVerdict.Result notApplicableWithMismatchingOutline =
                PlacementVerificationVerdict.reduce(notApplicableWithMismatchingOutlineEvidence);
        assertFinalVerdict(
                helper, "not-applicable-with-mismatching-outline",
                notApplicableWithMismatchingOutline,
                PlacementVerificationVerdict.FinalVerdict.RED);
        assertComponentVerdict(
                helper, "not-applicable-with-mismatching-outline",
                notApplicableWithMismatchingOutline,
                PlacementVerificationVerdict.Component.OUTLINE,
                PlacementVerificationVerdict.ComponentStatus.FAIL);
        assertContainsValue(
                helper, "not-applicable outline mismatch failure class",
                notApplicableWithMismatchingOutline.failureClasses(),
                "OUTLINE_DY_MISMATCH");

        LinkedHashMap<String, String> notApplicableWithMatchingCollisionEvidence =
                fullyObservedVerifierAction();
        notApplicableWithMatchingCollisionEvidence.put(
                "collisionVerdict", "NOT_APPLICABLE");
        PlacementVerificationVerdict.Result notApplicableWithMatchingCollision =
                PlacementVerificationVerdict.reduce(notApplicableWithMatchingCollisionEvidence);
        assertFinalVerdict(
                helper, "not-applicable-with-matching-collision",
                notApplicableWithMatchingCollision,
                PlacementVerificationVerdict.FinalVerdict.GREEN);
        assertComponentVerdict(
                helper, "not-applicable-with-matching-collision",
                notApplicableWithMatchingCollision,
                PlacementVerificationVerdict.Component.COLLISION,
                PlacementVerificationVerdict.ComponentStatus.PASS);

        LinkedHashMap<String, String> notApplicableWithContactConflictEvidence =
                fullyObservedVerifierAction();
        notApplicableWithContactConflictEvidence.put(
                "collisionVerdict", "NOT_APPLICABLE");
        notApplicableWithContactConflictEvidence.put(
                "actualContactPlane", "64.750000");
        PlacementVerificationVerdict.Result notApplicableWithContactConflict =
                PlacementVerificationVerdict.reduce(notApplicableWithContactConflictEvidence);
        assertFinalVerdict(
                helper, "not-applicable-with-contact-conflict",
                notApplicableWithContactConflict,
                PlacementVerificationVerdict.FinalVerdict.RED);
        assertComponentVerdict(
                helper, "not-applicable-with-contact-conflict",
                notApplicableWithContactConflict,
                PlacementVerificationVerdict.Component.COLLISION,
                PlacementVerificationVerdict.ComponentStatus.FAIL);
        assertContainsValue(
                helper, "not-applicable contact conflict failure class",
                notApplicableWithContactConflict.failureClasses(),
                "COLLISION_CONTACT_PLANE_MISMATCH");

        LinkedHashMap<String, String> notApplicableWithSeatConflictEvidence =
                fullyObservedVerifierAction();
        notApplicableWithSeatConflictEvidence.put(
                "collisionVerdict", "NOT_APPLICABLE");
        notApplicableWithSeatConflictEvidence.put("seatError", "0.125000");
        PlacementVerificationVerdict.Result notApplicableWithSeatConflict =
                PlacementVerificationVerdict.reduce(notApplicableWithSeatConflictEvidence);
        assertFinalVerdict(
                helper, "not-applicable-with-seat-conflict",
                notApplicableWithSeatConflict,
                PlacementVerificationVerdict.FinalVerdict.RED);
        assertComponentVerdict(
                helper, "not-applicable-with-seat-conflict",
                notApplicableWithSeatConflict,
                PlacementVerificationVerdict.Component.COLLISION,
                PlacementVerificationVerdict.ComponentStatus.FAIL);
        assertContainsValue(
                helper, "not-applicable seat conflict failure class",
                notApplicableWithSeatConflict.failureClasses(),
                "COLLISION_SEAT_ERROR");

        LinkedHashMap<String, String> redEvidence = fullyObservedVerifierAction();
        redEvidence.put("modelDy", "-0.250000");
        redEvidence.remove("raycastDy");
        PlacementVerificationVerdict.Result red =
                PlacementVerificationVerdict.reduce(redEvidence);
        assertFinalVerdict(helper, "red-beats-missing", red,
                PlacementVerificationVerdict.FinalVerdict.RED);
        assertComponentVerdict(
                helper, "red-beats-missing", red,
                PlacementVerificationVerdict.Component.MODEL,
                PlacementVerificationVerdict.ComponentStatus.FAIL);
        assertComponentVerdict(
                helper, "red-beats-missing", red,
                PlacementVerificationVerdict.Component.RAYCAST,
                PlacementVerificationVerdict.ComponentStatus.MISSING);
        assertContainsValue(
                helper, "red-beats-missing failure class", red.failureClasses(),
                "MODEL_DY_MISMATCH");
        assertContainsValue(
                helper, "red-beats-missing missing component",
                red.missingRequiredComponents(),
                PlacementVerificationVerdict.Component.RAYCAST);

        LinkedHashMap<String, String> collisionConflictEvidence =
                fullyObservedVerifierAction();
        collisionConflictEvidence.put("actualContactPlane", "64.750000");
        PlacementVerificationVerdict.Result collisionConflict =
                PlacementVerificationVerdict.reduce(collisionConflictEvidence);
        assertFinalVerdict(helper, "collision-conflict", collisionConflict,
                PlacementVerificationVerdict.FinalVerdict.RED);
        assertComponentVerdict(
                helper, "collision-conflict", collisionConflict,
                PlacementVerificationVerdict.Component.COLLISION,
                PlacementVerificationVerdict.ComponentStatus.FAIL);
        assertContainsValue(
                helper, "collision conflict failure class", collisionConflict.failureClasses(),
                "COLLISION_CONTACT_PLANE_MISMATCH");

        LinkedHashMap<String, String> actionDyMismatchEvidence =
                fullyObservedVerifierAction();
        actionDyMismatchEvidence.put("afterDy", "-0.250000");
        PlacementVerificationVerdict.Result actionDyMismatch =
                PlacementVerificationVerdict.reduce(actionDyMismatchEvidence);
        assertFinalVerdict(helper, "action-dy-mismatch", actionDyMismatch,
                PlacementVerificationVerdict.FinalVerdict.RED);
        assertComponentVerdict(
                helper, "action-dy-mismatch", actionDyMismatch,
                PlacementVerificationVerdict.Component.PLACED,
                PlacementVerificationVerdict.ComponentStatus.FAIL);
        assertContainsValue(
                helper, "action dy mismatch failure class", actionDyMismatch.failureClasses(),
                "PLACED_ACTION_DY_MISMATCH");

        LinkedHashMap<String, String> conflictingIntentEvidence =
                fullyObservedVerifierAction();
        conflictingIntentEvidence.put("intentDy", "-0.250000");
        PlacementVerificationVerdict.Result conflictingIntent =
                PlacementVerificationVerdict.reduce(conflictingIntentEvidence);
        assertFinalVerdict(
                helper, "conflicting-intent-aliases", conflictingIntent,
                PlacementVerificationVerdict.FinalVerdict.RED);
        assertComponentVerdict(
                helper, "conflicting-intent-aliases", conflictingIntent,
                PlacementVerificationVerdict.Component.PLACED,
                PlacementVerificationVerdict.ComponentStatus.FAIL);
        assertContainsValue(
                helper, "conflicting intent aliases failure class",
                conflictingIntent.failureClasses(),
                "INTENT_DY_ALIAS_CONFLICT");
        assertCanonicalField(
                helper, "conflicting-intent-aliases", conflictingIntent,
                "intentDy", "unknown");

        LinkedHashMap<String, String> inconclusiveEvidence = ordinaryAction(
                "minecraft:stone", "71,64,71", "71,65,71",
                "-0.500000", "-0.500000", "SUCCESS");
        PlacementVerificationVerdict.Result inconclusive =
                PlacementVerificationVerdict.reduce(inconclusiveEvidence);
        assertFinalVerdict(helper, "inconclusive", inconclusive,
                PlacementVerificationVerdict.FinalVerdict.INCONCLUSIVE);
        assertComponentVerdict(
                helper, "inconclusive", inconclusive,
                PlacementVerificationVerdict.Component.PLACED,
                PlacementVerificationVerdict.ComponentStatus.PASS);
        assertComponentVerdict(
                helper, "inconclusive", inconclusive,
                PlacementVerificationVerdict.Component.ANCHOR,
                PlacementVerificationVerdict.ComponentStatus.MISSING);
        assertComponentVerdict(
                helper, "inconclusive", inconclusive,
                PlacementVerificationVerdict.Component.MODEL,
                PlacementVerificationVerdict.ComponentStatus.MISSING);

        LinkedHashMap<String, String> nullResultEvidence = ordinaryAction(
                "minecraft:stone", "71,64,72", "71,65,72",
                "-0.500000", "-0.500000", "SUCCESS");
        nullResultEvidence.put("actualResult", null);
        PlacementVerificationVerdict.Result nullResult =
                PlacementVerificationVerdict.reduce(nullResultEvidence);
        assertFinalVerdict(
                helper, "null-actual-result", nullResult,
                PlacementVerificationVerdict.FinalVerdict.INCONCLUSIVE);
        assertComponentVerdict(
                helper, "null-actual-result", nullResult,
                PlacementVerificationVerdict.Component.PLACED,
                PlacementVerificationVerdict.ComponentStatus.MISSING);

        String expectedVanillaRefusalReason = "VANILLA_SUPPORT_REQUIRED";
        LinkedHashMap<String, String> expectedRefusalEvidence = ordinaryAction(
                "minecraft:stone", "72,64,72", "none",
                "unknown", "none", "Fail[VANILLA_SUPPORT_REQUIRED]");
        expectedRefusalEvidence.put(
                "expectedResult", PlacementVerificationVerdict.MUST_REFUSE_VANILLA);
        expectedRefusalEvidence.put("expectedRefusalReason", expectedVanillaRefusalReason);
        expectedRefusalEvidence.put("actualRefusalReason", expectedVanillaRefusalReason);
        PlacementVerificationVerdict.Result expectedRefusal =
                PlacementVerificationVerdict.reduce(expectedRefusalEvidence);
        assertFinalVerdict(helper, "expected-refusal", expectedRefusal,
                PlacementVerificationVerdict.FinalVerdict.EXPECTED_REFUSAL);
        assertCanonicalField(
                helper, "expected-refusal", expectedRefusal,
                "expectedRefusalReason", expectedVanillaRefusalReason);
        assertCanonicalField(
                helper, "expected-refusal", expectedRefusal,
                "actualRefusalReason", expectedVanillaRefusalReason);

        LinkedHashMap<String, String> parsedOnlyRefusalEvidence = ordinaryAction(
                "minecraft:stone", "72,64,73", "none",
                "unknown", "none", "Fail[VANILLA_SUPPORT_REQUIRED]");
        parsedOnlyRefusalEvidence.put(
                "expectedResult", PlacementVerificationVerdict.MUST_REFUSE_VANILLA);
        parsedOnlyRefusalEvidence.put(
                "expectedRefusalReason", expectedVanillaRefusalReason);
        PlacementVerificationVerdict.Result parsedOnlyRefusal =
                PlacementVerificationVerdict.reduce(parsedOnlyRefusalEvidence);
        assertFinalVerdict(
                helper, "parsed-only-expected-refusal", parsedOnlyRefusal,
                PlacementVerificationVerdict.FinalVerdict.EXPECTED_REFUSAL);
        assertCanonicalField(
                helper, "parsed-only-expected-refusal", parsedOnlyRefusal,
                "actualRefusalReason", expectedVanillaRefusalReason);

        LinkedHashMap<String, String> explicitOnlyRefusalEvidence = ordinaryAction(
                "minecraft:stone", "72,64,74", "none",
                "unknown", "none", "Fail[]");
        explicitOnlyRefusalEvidence.put(
                "expectedResult", PlacementVerificationVerdict.MUST_REFUSE_VANILLA);
        explicitOnlyRefusalEvidence.put(
                "expectedRefusalReason", expectedVanillaRefusalReason);
        explicitOnlyRefusalEvidence.put(
                "actualRefusalReason", expectedVanillaRefusalReason);
        PlacementVerificationVerdict.Result explicitOnlyRefusal =
                PlacementVerificationVerdict.reduce(explicitOnlyRefusalEvidence);
        assertFinalVerdict(
                helper, "explicit-only-expected-refusal", explicitOnlyRefusal,
                PlacementVerificationVerdict.FinalVerdict.EXPECTED_REFUSAL);
        assertCanonicalField(
                helper, "explicit-only-expected-refusal", explicitOnlyRefusal,
                "actualRefusalReason", expectedVanillaRefusalReason);

        LinkedHashMap<String, String> missingRefusalReasonEvidence = ordinaryAction(
                "minecraft:stone", "73,64,73", "none",
                "unknown", "none", "Fail[]");
        missingRefusalReasonEvidence.put(
                "expectedResult", PlacementVerificationVerdict.MUST_REFUSE_VANILLA);
        missingRefusalReasonEvidence.put(
                "expectedRefusalReason", expectedVanillaRefusalReason);
        PlacementVerificationVerdict.Result missingRefusalReason =
                PlacementVerificationVerdict.reduce(missingRefusalReasonEvidence);
        assertFinalVerdict(helper, "missing-refusal-reason", missingRefusalReason,
                PlacementVerificationVerdict.FinalVerdict.UNCLASSIFIED_FAILURE);
        assertContainsValue(
                helper, "missing refusal reason failure class",
                missingRefusalReason.failureClasses(),
                "EXPECTED_REFUSAL_REASON_MISSING");
        assertCanonicalField(
                helper, "missing-refusal-reason", missingRefusalReason,
                "actualRefusalReason", "unknown");

        LinkedHashMap<String, String> wrongRefusalReasonEvidence = ordinaryAction(
                "minecraft:stone", "74,64,74", "none",
                "unknown", "none", "Fail[VANILLA_DIFFERENT_REASON]");
        wrongRefusalReasonEvidence.put(
                "expectedResult", PlacementVerificationVerdict.MUST_REFUSE_VANILLA);
        wrongRefusalReasonEvidence.put(
                "expectedRefusalReason", expectedVanillaRefusalReason);
        PlacementVerificationVerdict.Result wrongRefusalReason =
                PlacementVerificationVerdict.reduce(wrongRefusalReasonEvidence);
        assertFinalVerdict(helper, "wrong-refusal-reason", wrongRefusalReason,
                PlacementVerificationVerdict.FinalVerdict.UNCLASSIFIED_FAILURE);
        assertContainsValue(
                helper, "wrong refusal reason failure class",
                wrongRefusalReason.failureClasses(),
                "EXPECTED_REFUSAL_REASON_MISMATCH");
        assertCanonicalField(
                helper, "wrong-refusal-reason", wrongRefusalReason,
                "actualRefusalReason", "VANILLA_DIFFERENT_REASON");

        LinkedHashMap<String, String> conflictingRefusalSourcesEvidence = ordinaryAction(
                "minecraft:stone", "74,64,75", "none",
                "unknown", "none", "Fail[VANILLA_DIFFERENT_REASON]");
        conflictingRefusalSourcesEvidence.put(
                "expectedResult", PlacementVerificationVerdict.MUST_REFUSE_VANILLA);
        conflictingRefusalSourcesEvidence.put(
                "expectedRefusalReason", expectedVanillaRefusalReason);
        conflictingRefusalSourcesEvidence.put(
                "actualRefusalReason", expectedVanillaRefusalReason);
        PlacementVerificationVerdict.Result conflictingRefusalSources =
                PlacementVerificationVerdict.reduce(conflictingRefusalSourcesEvidence);
        assertFinalVerdict(
                helper, "conflicting-refusal-sources", conflictingRefusalSources,
                PlacementVerificationVerdict.FinalVerdict.UNCLASSIFIED_FAILURE);
        assertContainsValue(
                helper, "conflicting refusal sources failure class",
                conflictingRefusalSources.failureClasses(),
                "ACTUAL_REFUSAL_REASON_CONFLICT");
        assertCanonicalField(
                helper, "conflicting-refusal-sources", conflictingRefusalSources,
                "actualRefusalReason", "unknown");

        LinkedHashMap<String, String> undeclaredRefusalEvidence = ordinaryAction(
                "minecraft:stone", "75,64,75", "none",
                "unknown", "none", "Fail[VANILLA_SUPPORT_REQUIRED]");
        PlacementVerificationVerdict.Result undeclaredRefusal =
                PlacementVerificationVerdict.reduce(undeclaredRefusalEvidence);
        assertFinalVerdict(helper, "undeclared-refusal", undeclaredRefusal,
                PlacementVerificationVerdict.FinalVerdict.UNCLASSIFIED_FAILURE);
        assertContainsValue(
                helper, "undeclared refusal failure class", undeclaredRefusal.failureClasses(),
                "UNDECLARED_PLACEMENT_FAILURE");
        assertCanonicalField(
                helper, "undeclared-refusal", undeclaredRefusal,
                "actualRefusalReason", expectedVanillaRefusalReason);

        LinkedHashMap<String, String> undeclaredRefusalConflictEvidence = ordinaryAction(
                "minecraft:stone", "75,64,76", "none",
                "unknown", "none", "Fail[VANILLA_PARSED_REASON]");
        undeclaredRefusalConflictEvidence.put(
                "actualRefusalReason", "VANILLA_EXPLICIT_REASON");
        PlacementVerificationVerdict.Result undeclaredRefusalConflict =
                PlacementVerificationVerdict.reduce(undeclaredRefusalConflictEvidence);
        assertFinalVerdict(
                helper, "undeclared-refusal-conflict", undeclaredRefusalConflict,
                PlacementVerificationVerdict.FinalVerdict.UNCLASSIFIED_FAILURE);
        assertContainsValue(
                helper, "undeclared refusal conflict class",
                undeclaredRefusalConflict.failureClasses(),
                "ACTUAL_REFUSAL_REASON_CONFLICT");
        assertContainsValue(
                helper, "undeclared refusal classification",
                undeclaredRefusalConflict.failureClasses(),
                "UNDECLARED_PLACEMENT_FAILURE");
        assertCanonicalField(
                helper, "undeclared-refusal-conflict", undeclaredRefusalConflict,
                "actualRefusalReason", "unknown");

        LinkedHashMap<String, String> unexpectedRefusalSuccessEvidence =
                fullyObservedVerifierAction();
        unexpectedRefusalSuccessEvidence.put(
                "expectedResult", PlacementVerificationVerdict.MUST_REFUSE_VANILLA);
        unexpectedRefusalSuccessEvidence.put(
                "expectedRefusalReason", expectedVanillaRefusalReason);
        PlacementVerificationVerdict.Result unexpectedRefusalSuccess =
                PlacementVerificationVerdict.reduce(unexpectedRefusalSuccessEvidence);
        assertFinalVerdict(helper, "unexpected-refusal-success", unexpectedRefusalSuccess,
                PlacementVerificationVerdict.FinalVerdict.RED);
        assertContainsValue(
                helper, "unexpected refusal success failure class",
                unexpectedRefusalSuccess.failureClasses(),
                "EXPECTED_REFUSAL_DID_NOT_OCCUR");

        LinkedHashMap<String, String> anchorMismatchEvidence = fullyObservedVerifierAction();
        anchorMismatchEvidence.put("afterStoredDy", "-0.250000");
        PlacementVerificationVerdict.Result anchorMismatch =
                PlacementVerificationVerdict.reduce(anchorMismatchEvidence);
        assertFinalVerdict(helper, "anchor-mismatch", anchorMismatch,
                PlacementVerificationVerdict.FinalVerdict.RED);
        assertComponentVerdict(
                helper, "anchor-mismatch", anchorMismatch,
                PlacementVerificationVerdict.Component.ANCHOR,
                PlacementVerificationVerdict.ComponentStatus.FAIL);
        assertContainsValue(
                helper, "anchor mismatch failure class", anchorMismatch.failureClasses(),
                "ANCHOR_STORED_DY_MISMATCH");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void verifier_truth_reducer_only_red_is_discoverable(GameTestHelper helper) {
        Path dir = Path.of("build", "verifier-truth-recorder-reducer-red", "run-" + System.nanoTime());
        System.setProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY, "true");
        System.setProperty(LiveCursorIntentRecorder.DIR_PROPERTY, dir.toString());
        try {
            LiveCursorIntentRecorder.resetForTests();
            String callerActionId = "verifier-truth-anchor-only-red";
            LinkedHashMap<String, String> action = ordinaryAction(
                    "minecraft:stone", "76,64,76", "76,65,76",
                    "-0.500000", "-0.500000", "SUCCESS");
            action.put("actionId", callerActionId);
            action.put("afterStoredDy", "-0.250000");
            LiveCursorIntentRecorder.recordAction(action);
            LiveCursorIntentRecorder.flushSummaryForTests();

            JsonObject actionEvidence = null;
            for (String row : read(helper, dir.resolve("session.jsonl")).split("\\R")) {
                if (!row.isBlank()) {
                    JsonObject parsed = parseJsonObject(helper, row);
                    if (exactJsonString(parsed, "type", "action")) {
                        actionEvidence = parsed;
                    }
                }
            }
            String actionId = actionEvidence == null
                    ? null
                    : jsonString(actionEvidence, "actionId");
            String redMarker = PlacementVerificationVerdict.FinalVerdict.RED.marker();
            String failureClass = "ANCHOR_STORED_DY_MISMATCH";
            if (actionEvidence == null
                    || !isRecorderOwnedActionId(actionId)
                    || callerActionId.equals(actionId)
                    || !exactJsonString(actionEvidence, "finalVerdict", "RED")
                    || !exactJsonString(actionEvidence, "verdictMarker", redMarker)
                    || !exactJsonString(actionEvidence, "anchorVerdict", "FAIL")
                    || !exactJsonString(actionEvidence, "failureClasses", failureClass)
                    || !exactJsonString(actionEvidence, "marker", redMarker)) {
                throw helper.assertionException(
                        "reducer-only RED must remain exact and discoverable on the action row: "
                                + actionEvidence);
            }

            String[] mismatchLines = read(helper, dir.resolve("mismatches.tsv"))
                    .strip()
                    .split("\\R");
            String expectedMismatchHeader =
                    "type\trowOrActionId\tmarker\tpos\theldItem\tfailureClasses";
            String[] mismatchColumns = mismatchLines.length == 3
                    ? mismatchLines[1].split("\t", -1)
                    : new String[0];
            String[] terminalMismatchColumns = mismatchLines.length == 3
                    ? mismatchLines[2].split("\t", -1)
                    : new String[0];
            if (mismatchLines.length != 3
                    || !mismatchLines[0].equals(expectedMismatchHeader)
                    || mismatchColumns.length != 6
                    || !mismatchColumns[0].equals("action")
                    || !mismatchColumns[1].equals(actionId)
                    || !mismatchColumns[2].equals(redMarker)
                    || !mismatchColumns[3].equals("76,64,76")
                    || !mismatchColumns[4].equals("minecraft:stone")
                    || !mismatchColumns[5].equals(failureClass)
                    || terminalMismatchColumns.length != 6
                    || !terminalMismatchColumns[0].equals("placement_attempt")
                    || !terminalMismatchColumns[1].startsWith("attempt:")
                    || !terminalMismatchColumns[2].equals(redMarker)
                    || !terminalMismatchColumns[3].equals("76,64,76")
                    || !terminalMismatchColumns[4].equals("minecraft:stone")
                    || !terminalMismatchColumns[5].equals(failureClass)) {
                throw helper.assertionException(
                        "raw and terminal REDs must route marker plus failure class to mismatches.tsv: "
                                + java.util.Arrays.toString(mismatchLines));
            }
            assertContains(
                    helper, dir.resolve("summary.md"),
                    "placementVerdictRedRows=1");
            helper.succeed();
        } finally {
            System.clearProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY);
            System.clearProperty(LiveCursorIntentRecorder.DIR_PROPERTY);
            LiveCursorIntentRecorder.resetForTests();
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void verifier_truth_green_never_routes_to_mismatches(GameTestHelper helper) {
        Path dir = Path.of("build", "verifier-truth-recorder-green", "run-" + System.nanoTime());
        System.setProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY, "true");
        System.setProperty(LiveCursorIntentRecorder.DIR_PROPERTY, dir.toString());
        try {
            LiveCursorIntentRecorder.resetForTests();
            String callerActionId = "verifier-truth-canonical-intent-green";
            LinkedHashMap<String, String> action = fullyObservedVerifierAction();
            action.put("actionId", callerActionId);
            action.put("expectedAfterDy", "unknown");
            action.put("intentDy", "-0.500000");
            LiveCursorIntentRecorder.recordAction(action);
            LiveCursorIntentRecorder.flushSummaryForTests();

            JsonObject actionEvidence = null;
            for (String row : read(helper, dir.resolve("session.jsonl")).split("\\R")) {
                if (!row.isBlank()) {
                    JsonObject parsed = parseJsonObject(helper, row);
                    if (exactJsonString(parsed, "type", "action")) {
                        actionEvidence = parsed;
                    }
                }
            }
            String actionId = actionEvidence == null
                    ? null
                    : jsonString(actionEvidence, "actionId");
            String greenVerdictMarker =
                    PlacementVerificationVerdict.FinalVerdict.GREEN.marker();
            if (actionEvidence == null
                    || !isRecorderOwnedActionId(actionId)
                    || callerActionId.equals(actionId)
                    || !exactJsonString(actionEvidence, "finalVerdict", "GREEN")
                    || !exactJsonString(
                            actionEvidence, "verdictMarker", greenVerdictMarker)
                    || !exactJsonString(actionEvidence, "intentDy", "-0.500000")
                    || !exactJsonString(
                            actionEvidence, "marker", "LIVE_GREEN_PLACEMENT_AUTHORING")
                    || hasMarkerToken(
                            actionEvidence, "LIVE_PLACEMENT_EXPECTED_DY_MISMATCH")
                    || hasMarkerToken(
                            actionEvidence, PlacementVerificationVerdict.FinalVerdict.RED.marker())) {
                throw helper.assertionException(
                        "canonical-intent GREEN must have only its green authoring marker: "
                                + actionEvidence);
            }

            String mismatchText = read(helper, dir.resolve("mismatches.tsv"));
            String expectedMismatchHeader =
                    "type\trowOrActionId\tmarker\tpos\theldItem\tfailureClasses";
            if (!mismatchText.strip().equals(expectedMismatchHeader)
                    || mismatchText.contains(actionId)) {
                throw helper.assertionException(
                        "final GREEN must not write a mismatch row: " + mismatchText);
            }
            assertContains(
                    helper, dir.resolve("summary.md"),
                    "placementVerdictGreenRows=1");
            assertContains(
                    helper, dir.resolve("summary.md"),
                    "liveGreenPlacementRows=1");
            helper.succeed();
        } finally {
            System.clearProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY);
            System.clearProperty(LiveCursorIntentRecorder.DIR_PROPERTY);
            LiveCursorIntentRecorder.resetForTests();
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void verifier_truth_client_server_rows_merge_into_one_logical_attempt(
            GameTestHelper helper) {
        Path dir = Path.of(
                "build", "verifier-truth-logical-attempt-merge", "run-" + System.nanoTime());
        System.setProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY, "true");
        System.setProperty(LiveCursorIntentRecorder.DIR_PROPERTY, dir.toString());
        try {
            LiveCursorIntentRecorder.resetForTests();
            String callerClientActionId = "9001";
            String callerServerActionId = "9002";
            String callerProxyActionId = "9003";
            String heldItem = "minecraft:stone";
            String ownerPos = "90,64,90";
            String placementPos = "90,65,90";
            String expectedDy = "-0.500000";
            String supportPlane = "64.500000";
            String placementRoute = "TOP_SEAT";
            String landingAuthority = "CANONICAL_STORED_DY";
            String rigCaseId = "verifier-truth-logical-attempt";

            LinkedHashMap<String, String> clientAction = ordinaryAction(
                    heldItem, ownerPos, placementPos, expectedDy, expectedDy, "SUCCESS");
            clientAction.put("actionId", callerClientActionId);
            clientAction.put("tick", "5000");
            clientAction.put("side", "client");
            clientAction.put("modelDy", expectedDy);
            clientAction.put("collisionDy", expectedDy);
            clientAction.put("raycastDy", expectedDy);
            clientAction.put("outlineDy", expectedDy);
            clientAction.put("expectedSupportPlane", supportPlane);
            clientAction.put("actualContactPlane", supportPlane);
            clientAction.put("seatError", "0.000000");
            clientAction.put("placementRoute", placementRoute);
            clientAction.put("landingAuthority", landingAuthority);
            clientAction.put("rigCaseId", rigCaseId);

            LinkedHashMap<String, String> serverAction = ordinaryAction(
                    heldItem, ownerPos, placementPos, expectedDy, expectedDy, "SUCCESS");
            serverAction.put("actionId", callerServerActionId);
            serverAction.put("tick", "5001");
            serverAction.put("side", "server");
            serverAction.put("afterStoredDy", expectedDy);
            serverAction.put("stabilityVerdict", "PASS");
            serverAction.put("placementRoute", placementRoute);
            serverAction.put("landingAuthority", landingAuthority);
            serverAction.put("rigCaseId", rigCaseId);

            LinkedHashMap<String, String> proxyAction = ordinaryAction(
                    heldItem, ownerPos, placementPos, expectedDy, expectedDy, "SUCCESS");
            proxyAction.put("actionId", callerProxyActionId);
            proxyAction.put("tick", "5002");
            proxyAction.put("side", "server");
            proxyAction.put("afterStoredDy", expectedDy);
            proxyAction.put("modelDy", expectedDy);
            proxyAction.put("collisionDy", expectedDy);
            proxyAction.put("raycastDy", expectedDy);
            proxyAction.put("outlineDy", expectedDy);
            proxyAction.put("stabilityVerdict", "PASS");
            proxyAction.put("expectedSupportPlane", supportPlane);
            proxyAction.put("actualContactPlane", supportPlane);
            proxyAction.put("seatError", "0.000000");
            proxyAction.put("placementRoute", placementRoute);
            proxyAction.put("landingAuthority", landingAuthority);
            proxyAction.put("rigCaseId", rigCaseId);

            for (String forbiddenClientField : new String[]{
                    "storedDy", "afterStoredDy", "stabilityVerdict"
            }) {
                if (clientAction.containsKey(forbiddenClientField)) {
                    throw helper.assertionException(
                            "client fixture must not pre-seed server evidence "
                                    + forbiddenClientField);
                }
            }
            for (String forbiddenServerField : new String[]{
                    "modelDy", "collisionDy", "raycastDy", "outlineDy",
                    "expectedSupportPlane", "actualContactPlane", "seatError"
            }) {
                if (serverAction.containsKey(forbiddenServerField)) {
                    throw helper.assertionException(
                            "server fixture must not pre-seed client evidence "
                                    + forbiddenServerField);
                }
            }

            LiveCursorIntentRecorder.recordAction(clientAction);
            LiveCursorIntentRecorder.recordAction(serverAction);
            LiveCursorIntentRecorder.withActionOrigin(
                    LiveCursorIntentRecorder.ActionOrigin.AUTO_USEON_PROXY,
                    () -> LiveCursorIntentRecorder.recordAction(proxyAction));
            LiveCursorIntentRecorder.flushSummaryForTests();

            java.util.ArrayList<JsonObject> sessionRows = new java.util.ArrayList<>();
            for (String row : read(helper, dir.resolve("session.jsonl")).split("\\R")) {
                if (!row.isBlank()) {
                    sessionRows.add(parseJsonObject(helper, row));
                }
            }

            JsonObject clientRow = null;
            JsonObject serverRow = null;
            JsonObject proxyRow = null;
            int clientActionRows = 0;
            int serverActionRows = 0;
            int proxyActionRows = 0;
            int rawActionRows = 0;
            java.util.ArrayList<JsonObject> terminalAttemptRows = new java.util.ArrayList<>();
            for (JsonObject row : sessionRows) {
                if (exactJsonString(row, "type", "action")) {
                    rawActionRows++;
                    if (exactJsonString(row, "phase", "CLIENT_PREDICTION")
                            && exactJsonString(row, "actionOrigin", "PLAYER_AUTHORED")) {
                        clientActionRows++;
                        clientRow = row;
                    } else if (exactJsonString(row, "phase", "SERVER_AUTHORITY")
                            && exactJsonString(row, "actionOrigin", "PLAYER_AUTHORED")) {
                        serverActionRows++;
                        serverRow = row;
                    } else if (exactJsonString(row, "phase", "AUTO_PROXY")
                            && exactJsonString(row, "actionOrigin", "AUTO_USEON_PROXY")) {
                        proxyActionRows++;
                        proxyRow = row;
                    }
                } else if (exactJsonString(row, "type", "placement_attempt")) {
                    terminalAttemptRows.add(row);
                }
            }
            if (rawActionRows != 3
                    || clientActionRows != 1
                    || serverActionRows != 1
                    || proxyActionRows != 1
                    || clientRow == null
                    || serverRow == null
                    || proxyRow == null) {
                throw helper.assertionException(
                        "logical-attempt fixture must preserve exactly three uniquely identified raw "
                                + "action rows; actionRows=" + rawActionRows
                                + ", clientRows=" + clientActionRows
                                + ", serverRows=" + serverActionRows
                                + ", proxyRows=" + proxyActionRows);
            }

            String clientActionId = jsonString(clientRow, "actionId");
            String serverActionId = jsonString(serverRow, "actionId");
            String proxyActionId = jsonString(proxyRow, "actionId");
            if (!"1".equals(clientActionId)
                    || !"2".equals(serverActionId)
                    || !"3".equals(proxyActionId)
                    || callerClientActionId.equals(clientActionId)
                    || callerServerActionId.equals(serverActionId)
                    || callerProxyActionId.equals(proxyActionId)) {
                throw helper.assertionException(
                        "the recorder must replace caller action IDs with its own ordered numeric "
                                + "sequence; client=" + clientActionId
                                + ", server=" + serverActionId
                                + ", proxy=" + proxyActionId);
            }

            String playerLogicalAttemptId = jsonString(clientRow, "logicalAttemptId");
            String serverLogicalAttemptId = jsonString(serverRow, "logicalAttemptId");
            String proxyLogicalAttemptId = jsonString(proxyRow, "logicalAttemptId");
            boolean playerLogicalAttemptIdPresent = playerLogicalAttemptId != null
                    && !playerLogicalAttemptId.isBlank()
                    && !playerLogicalAttemptId.equals("none")
                    && !playerLogicalAttemptId.equals("unknown");
            boolean proxyLogicalAttemptIdPresent = proxyLogicalAttemptId != null
                    && !proxyLogicalAttemptId.isBlank()
                    && !proxyLogicalAttemptId.equals("none")
                    && !proxyLogicalAttemptId.equals("unknown");
            if (!playerLogicalAttemptIdPresent
                    || !playerLogicalAttemptId.equals(serverLogicalAttemptId)
                    || !proxyLogicalAttemptIdPresent
                    || proxyLogicalAttemptId.equals(playerLogicalAttemptId)
                    || !exactJsonString(clientRow, "phase", "CLIENT_PREDICTION")
                    || !exactJsonString(serverRow, "phase", "SERVER_AUTHORITY")
                    || !exactJsonString(proxyRow, "phase", "AUTO_PROXY")
                    || !exactJsonString(clientRow, "playerProof", "PRESENT")
                    || !exactJsonString(serverRow, "playerProof", "PRESENT")
                    || !exactJsonString(proxyRow, "playerProof", "ABSENT")) {
                throw helper.assertionException(
                        "logical-attempt raw-row schema must assign one nonempty player ID across "
                                + "CLIENT_PREDICTION/SERVER_AUTHORITY and a different AUTO_PROXY ID "
                                + "without player proof; client=" + clientRow
                                + ", server=" + serverRow
                                + ", proxy=" + proxyRow);
            }

            for (JsonObject row : java.util.List.of(clientRow, serverRow, proxyRow)) {
                if (!exactJsonString(row, "heldItem", heldItem)
                        || !exactJsonString(row, "clickedOwnerPos", ownerPos)
                        || !exactJsonString(row, "clickedFace", "UP")
                        || !exactJsonString(row, "placementPos", placementPos)
                        || !exactJsonString(row, "expectedAfterDy", expectedDy)
                        || !exactJsonString(row, "afterDy", expectedDy)
                        || !exactJsonString(row, "placementRoute", placementRoute)
                        || !exactJsonString(row, "landingAuthority", landingAuthority)
                        || !exactJsonString(row, "rigCaseId", rigCaseId)) {
                    throw helper.assertionException(
                            "all three raw rows must preserve the identical placement key and "
                                    + "canonical intent fields: " + row);
                }
            }
            if (!exactJsonString(clientRow, "actionOrigin", "PLAYER_AUTHORED")
                    || !exactJsonString(serverRow, "actionOrigin", "PLAYER_AUTHORED")
                    || !exactJsonString(proxyRow, "actionOrigin", "AUTO_USEON_PROXY")
                    || !exactJsonString(clientRow, "side", "client")
                    || !exactJsonString(serverRow, "side", "server")
                    || !exactJsonString(proxyRow, "side", "server")
                    || !exactJsonString(clientRow, "finalVerdict", "INCONCLUSIVE")
                    || !exactJsonString(serverRow, "finalVerdict", "INCONCLUSIVE")
                    || !exactJsonString(proxyRow, "finalVerdict", "GREEN")
                    || !exactJsonString(clientRow, "afterStoredDy", "none")
                    || !exactJsonString(clientRow, "storedDy", "unknown")
                    || !exactJsonString(clientRow, "anchorVerdict", "MISSING")
                    || !exactJsonString(clientRow, "stabilityVerdict", "NOT_RUN")
                    || !exactJsonString(clientRow, "modelDy", expectedDy)
                    || !exactJsonString(clientRow, "collisionDy", expectedDy)
                    || !exactJsonString(clientRow, "raycastDy", expectedDy)
                    || !exactJsonString(clientRow, "outlineDy", expectedDy)
                    || !exactJsonString(clientRow, "expectedSupportPlane", supportPlane)
                    || !exactJsonString(clientRow, "actualContactPlane", supportPlane)
                    || !exactJsonString(clientRow, "seatError", "0.000000")
                    || !exactJsonString(serverRow, "afterStoredDy", expectedDy)
                    || !exactJsonString(serverRow, "storedDy", expectedDy)
                    || !exactJsonString(serverRow, "stabilityVerdict", "PASS")
                    || !exactJsonString(serverRow, "modelDy", "unknown")
                    || !exactJsonString(serverRow, "collisionDy", "unknown")
                    || !exactJsonString(serverRow, "raycastDy", "unknown")
                    || !exactJsonString(serverRow, "outlineDy", "unknown")
                    || !exactJsonString(serverRow, "expectedSupportPlane", "unknown")
                    || !exactJsonString(serverRow, "actualContactPlane", "unknown")
                    || !exactJsonString(serverRow, "seatError", "unknown")) {
                throw helper.assertionException(
                        "raw player rows must remain individually incomplete while the proxy row "
                                + "remains independently complete; client=" + clientRow
                                + ", server=" + serverRow
                                + ", proxy=" + proxyRow);
            }

            JsonObject playerAttempt = null;
            JsonObject proxyAttempt = null;
            int playerAttemptRows = 0;
            int proxyAttemptRows = 0;
            for (JsonObject row : terminalAttemptRows) {
                if (exactJsonString(row, "logicalAttemptId", playerLogicalAttemptId)) {
                    playerAttemptRows++;
                    playerAttempt = row;
                } else if (exactJsonString(row, "logicalAttemptId", proxyLogicalAttemptId)) {
                    proxyAttemptRows++;
                    proxyAttempt = row;
                }
            }
            if (terminalAttemptRows.size() != 2
                    || playerAttemptRows != 1
                    || proxyAttemptRows != 1
                    || playerAttempt == null
                    || proxyAttempt == null) {
                throw helper.assertionException(
                        "session must retain exactly two terminal placement_attempt rows, one player "
                                + "merge and one isolated proxy; terminalRows="
                                + terminalAttemptRows);
            }

            String[][] mergedPlayerContract = {
                    {"type", "placement_attempt"},
                    {"logicalAttemptId", playerLogicalAttemptId},
                    {"attemptStatus", "MERGED_CLIENT_SERVER"},
                    {"terminal", "true"},
                    {"clientActionId", clientActionId},
                    {"serverActionId", serverActionId},
                    {"actionCount", "2"},
                    {"playerProof", "PRESENT"},
                    {"finalVerdict", "GREEN"},
                    {"placedVerdict", "PASS"},
                    {"verdictMarker", "LIVE_PLACEMENT_VERDICT_GREEN"},
                    {"failureClasses", "none"},
                    {"missingRequiredComponents", "none"},
                    {"intentDy", expectedDy},
                    {"afterStoredDy", expectedDy},
                    {"storedDy", expectedDy},
                    {"modelDy", expectedDy},
                    {"collisionDy", expectedDy},
                    {"raycastDy", expectedDy},
                    {"outlineDy", expectedDy},
                    {"expectedSupportPlane", supportPlane},
                    {"actualContactPlane", supportPlane},
                    {"seatError", "0.000000"},
                    {"placementRoute", placementRoute},
                    {"landingAuthority", landingAuthority},
                    {"rigCaseId", rigCaseId},
                    {"anchorVerdict", "PASS"},
                    {"modelVerdict", "PASS"},
                    {"collisionVerdict", "PASS"},
                    {"raycastVerdict", "PASS"},
                    {"outlineVerdict", "PASS"},
                    {"stabilityVerdict", "PASS"}
            };
            StringBuilder mergedPlayerMismatches = new StringBuilder();
            for (String[] contract : mergedPlayerContract) {
                if (!exactJsonString(playerAttempt, contract[0], contract[1])) {
                    appendCsv(
                            mergedPlayerMismatches,
                            contract[0] + "=" + jsonString(playerAttempt, contract[0]));
                }
            }
            if (!mergedPlayerMismatches.isEmpty()) {
                throw helper.assertionException(
                        "merged player terminal row must preserve canonical evidence from both "
                                + "sources and reduce GREEN; mismatches=" + mergedPlayerMismatches
                                + ", row=" + playerAttempt);
            }

            String[][] proxyTerminalContract = {
                    {"type", "placement_attempt"},
                    {"logicalAttemptId", proxyLogicalAttemptId},
                    {"attemptStatus", "AUTO_PROXY"},
                    {"terminal", "true"},
                    {"clientActionId", "none"},
                    {"serverActionId", proxyActionId},
                    {"actionCount", "1"},
                    {"playerProof", "ABSENT"},
                    {"finalVerdict", "GREEN"},
                    {"placedVerdict", "PASS"},
                    {"anchorVerdict", "PASS"},
                    {"modelVerdict", "PASS"},
                    {"collisionVerdict", "PASS"},
                    {"raycastVerdict", "PASS"},
                    {"outlineVerdict", "PASS"},
                    {"stabilityVerdict", "PASS"},
                    {"verdictMarker", "LIVE_PLACEMENT_VERDICT_GREEN"},
                    {"failureClasses", "none"},
                    {"missingRequiredComponents", "none"},
                    {"intentDy", expectedDy},
                    {"afterStoredDy", expectedDy},
                    {"storedDy", expectedDy},
                    {"modelDy", expectedDy},
                    {"collisionDy", expectedDy},
                    {"raycastDy", expectedDy},
                    {"outlineDy", expectedDy},
                    {"expectedSupportPlane", supportPlane},
                    {"actualContactPlane", supportPlane},
                    {"seatError", "0.000000"},
                    {"placementRoute", placementRoute},
                    {"landingAuthority", landingAuthority},
                    {"rigCaseId", rigCaseId}
            };
            StringBuilder proxyTerminalMismatches = new StringBuilder();
            for (String[] contract : proxyTerminalContract) {
                if (!exactJsonString(proxyAttempt, contract[0], contract[1])) {
                    appendCsv(
                            proxyTerminalMismatches,
                            contract[0] + "=" + jsonString(proxyAttempt, contract[0]));
                }
            }
            if (!proxyTerminalMismatches.isEmpty()) {
                throw helper.assertionException(
                        "proxy evidence must remain one separate terminal AUTO_PROXY attempt that "
                                + "cannot count as player proof and must retain complete GREEN "
                                + "evidence; mismatches=" + proxyTerminalMismatches
                                + ", row=" + proxyAttempt);
            }

            LinkedHashMap<String, String> summaryValues = new LinkedHashMap<>();
            boolean summaryHeadingSeen = false;
            for (String summaryLine
                    : read(helper, dir.resolve("summary.md")).split("\\R", -1)) {
                if (summaryLine.isBlank()) {
                    continue;
                }
                if (summaryLine.equals("# Slabbed Live Cursor Intent Recorder Summary")) {
                    if (summaryHeadingSeen) {
                        throw helper.assertionException(
                                "logical-attempt summary must contain exactly one heading");
                    }
                    summaryHeadingSeen = true;
                    continue;
                }
                int separator = summaryLine.indexOf('=');
                if (separator <= 0
                        || separator != summaryLine.lastIndexOf('=')
                        || separator == summaryLine.length() - 1) {
                    throw helper.assertionException(
                            "logical-attempt summary line must be one exact key=value pair: "
                                    + summaryLine);
                }
                String key = summaryLine.substring(0, separator);
                String value = summaryLine.substring(separator + 1);
                if (!key.matches("[A-Za-z][A-Za-z0-9]*")
                        || !value.matches("[0-9]+")) {
                    throw helper.assertionException(
                            "logical-attempt summary counter must use an exact identifier and "
                                    + "nonnegative integer value: " + summaryLine);
                }
                String priorValue = summaryValues.putIfAbsent(key, value);
                if (priorValue != null) {
                    throw helper.assertionException(
                            "logical-attempt summary must reject duplicate key " + key
                                    + "; first=" + priorValue + ", duplicate=" + value);
                }
            }
            if (!summaryHeadingSeen) {
                throw helper.assertionException(
                        "logical-attempt summary is missing its exact heading");
            }

            String[][] expectedSummaryValues = {
                    {"actionRows", "3"},
                    {"playerAuthoredActionRows", "2"},
                    {"autoUseOnProxyActionRows", "1"},
                    {"placementVerdictGreenRows", "1"},
                    {"placementVerdictRedRows", "0"},
                    {"placementVerdictInconclusiveRows", "2"},
                    {"placementVerdictExpectedRefusalRows", "0"},
                    {"placementVerdictUnclassifiedFailureRows", "0"},
                    {"logicalAttemptRows", "2"},
                    {"mergedClientServerAttemptRows", "1"},
                    {"autoProxyLogicalAttemptRows", "1"},
                    {"playerProofLogicalAttemptRows", "1"},
                    {"logicalAttemptVerdictGreenRows", "2"},
                    {"playerProofGreenLogicalAttemptRows", "1"}
            };
            StringBuilder summaryMismatches = new StringBuilder();
            for (String[] contract : expectedSummaryValues) {
                String actual = summaryValues.get(contract[0]);
                if (!contract[1].equals(actual)) {
                    appendCsv(
                            summaryMismatches,
                            contract[0] + "=" + (actual == null ? "<missing>" : actual));
                }
            }
            if (!summaryMismatches.isEmpty()) {
                throw helper.assertionException(
                        "logical-attempt summary must preserve the exact raw verdict partition and "
                                + "terminal-attempt counts without duplicate keys; mismatches="
                                + summaryMismatches);
            }
            helper.succeed();
        } finally {
            System.clearProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY);
            System.clearProperty(LiveCursorIntentRecorder.DIR_PROPERTY);
            LiveCursorIntentRecorder.resetForTests();
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void verifier_truth_expired_attempt_precedes_new_action_timestamp(
            GameTestHelper helper) {
        Path dir = Path.of(
                "build", "verifier-truth-expiry-order", "run-" + System.nanoTime());
        System.setProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY, "true");
        System.setProperty(LiveCursorIntentRecorder.DIR_PROPERTY, dir.toString());
        try {
            LiveCursorIntentRecorder.resetForTests();

            LinkedHashMap<String, String> pendingClient = ordinaryAction(
                    "minecraft:stone",
                    "110,64,110",
                    "110,65,110",
                    "-0.500000",
                    "-0.500000",
                    "SUCCESS");
            pendingClient.put("side", "client");
            pendingClient.put("rigCaseId", "verifier-truth-expiry-order-pending");
            LiveCursorIntentRecorder.recordAction(pendingClient);

            try {
                Thread.sleep(1_100L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw helper.assertionException(
                        "interrupted while aging the pending client attempt");
            }

            LinkedHashMap<String, String> trigger = ordinaryAction(
                    "minecraft:stone",
                    "111,64,111",
                    "111,65,111",
                    "-0.500000",
                    "-0.500000",
                    "SUCCESS");
            trigger.put("side", "server");
            trigger.put("rigCaseId", "verifier-truth-expiry-order-trigger");
            LiveCursorIntentRecorder.recordAction(trigger);
            LiveCursorIntentRecorder.flushSummaryForTests();

            List<String> sessionLines = Files.readAllLines(dir.resolve("session.jsonl"));
            Instant previousRecordedAt = null;
            int placementAttemptRows = 0;
            boolean expiredClientAttemptSeen = false;
            for (int index = 0; index < sessionLines.size(); index++) {
                JsonObject row = parseJsonObject(helper, sessionLines.get(index));
                Instant recordedAt = Instant.parse(jsonString(row, "recordedAt"));
                if (previousRecordedAt != null && recordedAt.isBefore(previousRecordedAt)) {
                    throw helper.assertionException(
                            "recordedAt decreased across session rows at index " + index
                                    + ": previous=" + previousRecordedAt
                                    + ", current=" + recordedAt
                                    + ", row=" + row);
                }
                previousRecordedAt = recordedAt;
                if (exactJsonString(row, "type", "placement_attempt")) {
                    placementAttemptRows++;
                    expiredClientAttemptSeen |=
                            exactJsonString(row, "attemptStatus", "CLIENT_ONLY");
                }
            }
            if (sessionLines.size() != 4
                    || placementAttemptRows != 2
                    || !expiredClientAttemptSeen) {
                throw helper.assertionException(
                        "expiry-order fixture must retain two raw actions and two terminal attempts, "
                                + "including the expired CLIENT_ONLY attempt; rows=" + sessionLines);
            }
            helper.succeed();
        } catch (IOException exception) {
            throw helper.assertionException(
                    "could not inspect expiry-order recorder artifact: " + exception);
        } finally {
            System.clearProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY);
            System.clearProperty(LiveCursorIntentRecorder.DIR_PROPERTY);
            LiveCursorIntentRecorder.resetForTests();
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void verifier_truth_conflict_overlay_rebuilds_missing_components(
            GameTestHelper helper) {
        Path dir = Path.of(
                "build", "verifier-truth-conflict-missing", "run-" + System.nanoTime());
        System.setProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY, "true");
        System.setProperty(LiveCursorIntentRecorder.DIR_PROPERTY, dir.toString());
        try {
            LiveCursorIntentRecorder.resetForTests();
            String rigCaseId = "verifier-truth-conflict-missing";

            LinkedHashMap<String, String> clientAction = ordinaryAction(
                    "minecraft:stone",
                    "120,64,120",
                    "120,65,120",
                    "-0.500000",
                    "-0.500000",
                    "SUCCESS");
            clientAction.put("side", "client");
            clientAction.put("rigCaseId", rigCaseId);
            clientAction.put("actualContactPlane", "64.500000");

            LinkedHashMap<String, String> serverAction = ordinaryAction(
                    "minecraft:stone",
                    "120,64,120",
                    "120,65,120",
                    "-0.500000",
                    "-0.500000",
                    "SUCCESS");
            serverAction.put("side", "server");
            serverAction.put("rigCaseId", rigCaseId);
            serverAction.put("actualContactPlane", "64.750000");

            LiveCursorIntentRecorder.recordAction(clientAction);
            LiveCursorIntentRecorder.recordAction(serverAction);
            LiveCursorIntentRecorder.flushSummaryForTests();

            JsonObject terminal = null;
            int terminalRows = 0;
            for (String line : Files.readAllLines(dir.resolve("session.jsonl"))) {
                JsonObject row = parseJsonObject(helper, line);
                if (exactJsonString(row, "type", "placement_attempt")) {
                    terminalRows++;
                    terminal = row;
                }
            }
            String expectedMissing = "ANCHOR,MODEL,RAYCAST,OUTLINE,STABILITY";
            if (terminalRows != 1
                    || terminal == null
                    || !exactJsonString(terminal, "attemptStatus", "MERGED_CLIENT_SERVER")
                    || !exactJsonString(terminal, "finalVerdict", "RED")
                    || !exactJsonString(terminal, "collisionVerdict", "FAIL")
                    || !exactJsonString(
                            terminal,
                            "failureClasses",
                            "LOGICAL_ATTEMPT_ACTUAL_CONTACT_PLANE_CONFLICT")
                    || !exactJsonString(
                            terminal, "missingRequiredComponents", expectedMissing)) {
                throw helper.assertionException(
                        "logical conflict overlay must remove the now-FAIL COLLISION component while "
                                + "retaining every other still-missing component in enum order; "
                                + "expectedMissing=" + expectedMissing + ", terminal=" + terminal);
            }
            helper.succeed();
        } catch (IOException exception) {
            throw helper.assertionException(
                    "could not inspect conflict/missing recorder artifact: " + exception);
        } finally {
            System.clearProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY);
            System.clearProperty(LiveCursorIntentRecorder.DIR_PROPERTY);
            LiveCursorIntentRecorder.resetForTests();
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void recorderExposesOrdinaryNumericHeightFailures(GameTestHelper helper) {
        Path dir = Path.of("build", "c4-recorder-truth", "run-" + System.nanoTime());
        System.setProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY, "true");
        System.setProperty(LiveCursorIntentRecorder.DIR_PROPERTY, dir.toString());
        try {
            LiveCursorIntentRecorder.resetForTests();
            LiveCursorIntentRecorder.recordAction(ordinaryAction(
                    "minecraft:bamboo_button", "10,64,10", "10,65,10",
                    "-1.500000", "-0.500000", "SUCCESS"));
            LiveCursorIntentRecorder.recordAction(ordinaryAction(
                    "minecraft:flower_pot", "20,64,20", "20,65,20",
                    "-1.500000", "-1.000000", "SUCCESS"));
            LiveCursorIntentRecorder.withActionOrigin(
                    LiveCursorIntentRecorder.ActionOrigin.AUTO_USEON_PROXY,
                    () -> LiveCursorIntentRecorder.recordAction(ordinaryAction(
                            "minecraft:oak_fence", "30,64,30", "30,65,30",
                            "0.000000", "-0.500000", "SUCCESS")));
            LiveCursorIntentRecorder.withActionOrigin(
                    LiveCursorIntentRecorder.ActionOrigin.AUTO_USEON_PROXY,
                    () -> LiveCursorIntentRecorder.recordAction(ordinaryAction(
                            "minecraft:conduit", "40,64,40", "40,65,40",
                            "-0.500000", "0.000000", "SUCCESS")));

            LinkedHashMap<String, String> unknown = ordinaryAction(
                    "minecraft:lantern", "50,64,50", "50,65,50",
                    "unknown", "-1.000000", "SUCCESS");
            unknown.remove("expectedAfterDy");
            LiveCursorIntentRecorder.recordAction(unknown);

            LinkedHashMap<String, String> failed = ordinaryAction(
                    "minecraft:stone", "60,64,60", "none",
                    "unknown", "none", "Fail[]");
            failed.put("actionType", "use_block");
            failed.put("afterState", "none");
            failed.put("afterLaneKind", "none");
            LiveCursorIntentRecorder.withActionOrigin(
                    LiveCursorIntentRecorder.ActionOrigin.AUTO_USEON_PROXY,
                    () -> LiveCursorIntentRecorder.recordAction(failed));
            LiveCursorIntentRecorder.flushSummaryForTests();

            assertContains(helper, dir.resolve("actions.tsv"),
                    "minecraft:bamboo_button\t10,64,10\tUP\t10,65,10\t-1.500000\t-0.500000");
            assertContains(helper, dir.resolve("actions.tsv"),
                    "minecraft:flower_pot\t20,64,20\tUP\t20,65,20\t-1.500000\t-1.000000");
            assertContains(helper, dir.resolve("actions.tsv"),
                    "minecraft:oak_fence\t30,64,30\tUP\t30,65,30\t0.000000\t-0.500000");
            assertContains(helper, dir.resolve("actions.tsv"),
                    "minecraft:conduit\t40,64,40\tUP\t40,65,40\t-0.500000\t0.000000");
            assertContains(helper, dir.resolve("actions.tsv"),
                    "minecraft:lantern\t50,64,50\tUP\t50,65,50\tunknown\t-1.000000"
                            + "\tunknown\tanchored_full_block\tnone");
            assertContains(helper, dir.resolve("mismatches.tsv"),
                    "LIVE_PLACEMENT_EXPECTED_DY_MISMATCH");
            assertContains(helper, dir.resolve("mismatches.tsv"),
                    "LIVE_PLACEMENT_UNCLASSIFIED_FAILURE");
            assertContains(helper, dir.resolve("summary.md"),
                    "placementExpectedDyMismatchRows=4");
            assertContains(helper, dir.resolve("summary.md"),
                    "placementUnclassifiedFailureRows=1");
            assertContains(helper, dir.resolve("summary.md"), "playerAuthoredActionRows=3");
            assertContains(helper, dir.resolve("summary.md"), "autoUseOnProxyActionRows=3");
            helper.succeed();
        } finally {
            System.clearProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY);
            System.clearProperty(LiveCursorIntentRecorder.DIR_PROPERTY);
            LiveCursorIntentRecorder.resetForTests();
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void recorderWritesSessionAndSummaryWhenEnabled(GameTestHelper helper) {
        Path dir = Path.of("build", "gametest-live-cursor-recorder", "capture-" + System.nanoTime());
        System.setProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY, "true");
        System.setProperty(LiveCursorIntentRecorder.DIR_PROPERTY, dir.toString());
        try {
            LiveCursorIntentRecorder.resetForTests();
            if (!LiveCursorIntentRecorder.enabled()) {
                throw helper.assertionException(
                        "recorder must report enabled() when the JVM property is set — the volatile "
                                + "field should track the property at class-init");
            }

            LinkedHashMap<String, String> cursor = new LinkedHashMap<>();
            cursor.put("tick", "1");
            cursor.put("heldItem", "minecraft:stone");
            cursor.put("finalHitType", "BLOCK");
            cursor.put("finalHitPos", "4,-60,30");
            cursor.put("finalOwnerLaneKind", "persistent_lowered_slab_carrier");
            cursor.put("finalOutlineReplayHit", "hit=true pos=4,-60,30 side=east");
            cursor.put("finalRaycastReplayHit", "miss(empty)");
            cursor.put("outlineBounds", "min=(0.000000,0.000000,0.000000),max=(1.000000,1.000000,1.000000)");
            LiveCursorIntentRecorder.recordCursor(cursor);

            LinkedHashMap<String, String> action = new LinkedHashMap<>();
            action.put("actionType", "place_block");
            action.put("heldItem", "minecraft:stone_slab");
            action.put("clickedOwnerPos", "4,-60,30");
            action.put("clickedFace", "EAST");
            action.put("clickedOwnerLaneKind", "persistent_lowered_slab_carrier");
            action.put("placementPos", "5,-60,30");
            action.put("afterDy", "0.000000");
            LiveCursorIntentRecorder.recordAction(action);
            LiveCursorIntentRecorder.flushSummaryForTests();

            assertContains(helper, dir.resolve("session.jsonl"), "\"type\":\"cursor\"");
            assertContains(helper, dir.resolve("session.jsonl"), "LIVE_CURSOR_GHOST_SURFACE");
            assertContains(helper, dir.resolve("actions.tsv"), "place_block");
            assertContains(helper, dir.resolve("actions.tsv"), "PLAYER_AUTHORED");
            assertContains(helper, dir.resolve("actions.tsv"),
                    "LIVE_PLACEMENT_VANILLA_DY_FROM_LOWERED_OWNER");
            assertContains(helper, dir.resolve("summary.md"), "cursorRows=1");
            assertContains(helper, dir.resolve("summary.md"), "actionRows=1");
            assertContains(helper, dir.resolve("summary.md"), "playerAuthoredActionRows=1");
            assertContains(helper, dir.resolve("summary.md"), "autoUseOnProxyActionRows=0");
            assertContains(helper, dir.resolve("summary.md"), "ghostSurfaceRows=1");
        } finally {
            System.clearProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY);
            System.clearProperty(LiveCursorIntentRecorder.DIR_PROPERTY);
            LiveCursorIntentRecorder.resetForTests();
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void recorderOwnsAllNonterminalIdsAndWriteOrder(GameTestHelper helper) {
        Path dir = Path.of(
                "build",
                "gametest-live-cursor-recorder",
                "owned-id-order-" + System.nanoTime());
        BlockPos breakPos = new BlockPos(2, 2, 2);
        helper.getLevel().setBlock(
                breakPos,
                Blocks.STONE.defaultBlockState(),
                Block.UPDATE_ALL);

        System.clearProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY);
        System.clearProperty(LiveCursorIntentRecorder.DIR_PROPERTY);
        LiveCursorIntentRecorder.resetForTests();
        LiveCursorIntentRecorder.recordCursor(hostileCursor("disabled-cursor"));
        LiveCursorIntentRecorder.recordRenderedOutline(hostileOutline("disabled-outline"));
        LiveCursorIntentRecorder.recordSentinel(hostileSentinel("disabled-sentinel", 0));
        LiveCursorIntentRecorder.recordBreakEvent(
                helper.getLevel(),
                breakPos,
                helper.getLevel().getBlockState(breakPos),
                "disabled");
        LinkedHashMap<String, String> disabledAction = ordinaryAction(
                "minecraft:stone_slab",
                "2,2,2",
                "2,3,2",
                "-1.000000",
                "-1.000000",
                "SUCCESS");
        disabledAction.put("actionId", "disabled-action");
        LiveCursorIntentRecorder.recordAction(disabledAction);

        System.setProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY, "true");
        System.setProperty(LiveCursorIntentRecorder.DIR_PROPERTY, dir.toString());
        try {
            LiveCursorIntentRecorder.resetForTests();
            LiveCursorIntentRecorder.recordCursor(hostileCursor("caller-cursor"));
            LiveCursorIntentRecorder.recordRenderedOutline(hostileOutline("caller-outline"));
            LiveCursorIntentRecorder.recordSentinel(hostileSentinel("caller-sentinel", 1));
            LiveCursorIntentRecorder.recordBreakEvent(
                    helper.getLevel(),
                    breakPos,
                    helper.getLevel().getBlockState(breakPos),
                    "caller-break");
            LinkedHashMap<String, String> action = ordinaryAction(
                    "minecraft:stone_slab",
                    "2,2,2",
                    "2,3,2",
                    "-1.000000",
                    "-1.000000",
                    "SUCCESS");
            action.put("actionId", "caller-action");
            action.put("cursorRowId", "caller-cursor-reference");
            action.put("recordedAt", "2000-01-01T00:00:00Z");
            LiveCursorIntentRecorder.recordAction(action);

            int threadCount = 8;
            int rowsPerThread = 8;
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threadCount);
            List<Thread> threads = new ArrayList<>();
            for (int threadIndex = 0; threadIndex < threadCount; threadIndex++) {
                int capturedThread = threadIndex;
                Thread thread = new Thread(() -> {
                    try {
                        start.await();
                        for (int rowIndex = 0; rowIndex < rowsPerThread; rowIndex++) {
                            LiveCursorIntentRecorder.recordSentinel(hostileSentinel(
                                    "caller-concurrent-" + capturedThread + "-" + rowIndex,
                                    rowIndex));
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                }, "slabbed-recorder-order-" + threadIndex);
                thread.start();
                threads.add(thread);
            }
            start.countDown();
            try {
                done.await();
                for (Thread thread : threads) {
                    thread.join();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw helper.assertionException(
                        "interrupted while waiting for recorder ordering workers");
            }
            LiveCursorIntentRecorder.flushSummaryForTests();

            List<String> sessionLines = Files.readAllLines(dir.resolve("session.jsonl"));
            long expectedNumericId = 1L;
            Instant previousRecordedAt = null;
            for (String line : sessionLines) {
                JsonObject row = parseJsonObject(helper, line);
                if (exactJsonString(row, "type", "placement_attempt")) {
                    continue;
                }
                String idField = exactJsonString(row, "type", "action")
                        ? "actionId"
                        : exactJsonString(row, "type", "rendered_outline")
                        ? "outlineRenderId"
                        : "rowId";
                String rawId = jsonString(row, idField);
                if (!Long.toString(expectedNumericId).equals(rawId)) {
                    throw helper.assertionException(
                            "nonterminal recorder ID order expected " + expectedNumericId
                                    + " but got " + rawId + " in " + line);
                }
                Instant recordedAt = Instant.parse(jsonString(row, "recordedAt"));
                if (previousRecordedAt != null && recordedAt.isBefore(previousRecordedAt)) {
                    throw helper.assertionException(
                            "recordedAt decreased at recorder ID " + rawId);
                }
                previousRecordedAt = recordedAt;
                expectedNumericId++;
            }
            long expectedRows = 5L + (long) threadCount * rowsPerThread;
            if (expectedNumericId - 1L != expectedRows) {
                throw helper.assertionException(
                        "expected " + expectedRows + " nonterminal rows but found "
                                + (expectedNumericId - 1L));
            }

            JsonObject cursor = parseJsonObject(helper, sessionLines.get(0));
            JsonObject outline = parseJsonObject(helper, sessionLines.get(1));
            if (!"1".equals(jsonString(cursor, "rowId"))
                    || !"2".equals(jsonString(outline, "outlineRenderId"))
                    || !"1".equals(jsonString(outline, "cursorRowId"))
                    || !"minecraft:stone".equals(jsonString(outline, "cursorHeldItem"))) {
                throw helper.assertionException(
                        "outline did not capture the lock-ordered recorder cursor snapshot");
            }
            JsonObject actionRow = sessionLines.stream()
                    .map(line -> parseJsonObject(helper, line))
                    .filter(row -> exactJsonString(row, "type", "action"))
                    .findFirst()
                    .orElseThrow(() -> helper.assertionException("missing action row"));
            if (!"5".equals(jsonString(actionRow, "actionId"))
                    || !"1".equals(jsonString(actionRow, "cursorRowId"))) {
                throw helper.assertionException(
                        "action did not receive recorder-owned ID and cursor reference");
            }
        } catch (IOException exception) {
            throw helper.assertionException(
                    "could not inspect recorder ordering artifact: " + exception);
        } finally {
            System.clearProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY);
            System.clearProperty(LiveCursorIntentRecorder.DIR_PROPERTY);
            LiveCursorIntentRecorder.resetForTests();
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void manifestIsWrittenWithRedactedJavaCommand(GameTestHelper helper) {
        Path dir = Path.of("build", "gametest-live-cursor-recorder", "manifest-" + System.nanoTime());
        System.setProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY, "true");
        System.setProperty(LiveCursorIntentRecorder.DIR_PROPERTY, dir.toString());
        String priorCommand = System.getProperty("sun.java.command");
        System.setProperty("sun.java.command",
                "net.minecraft.client.main.Main --username Maintainer "
                        + "--accessToken eyJhbGciOiJSUzI1NiJ9.super-secret-jwt-token --userType msa");
        try {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw helper.assertionException("could not create legacy recorder fixture: " + e);
            }
            String legacyHeader = "actionId\tcursorRowId\tactionType\theldItem\nlegacy-schema-2-row\n";
            String legacyManifest = "{\"schemaVersion\":\"2\"}\n";
            write(helper, dir.resolve("actions.tsv"), legacyHeader);
            write(helper, dir.resolve("manifest.json"), legacyManifest);
            LiveCursorIntentRecorder.resetForTests();
            // bootstrap() must preserve the existing schema-2 evidence and choose a fresh schema-6
            // child directory instead of appending a 13-column row beneath the 12-column header.
            LiveCursorIntentRecorder.bootstrap();

            Path actualDir = Path.of(LiveCursorIntentRecorder.currentLogPathDisplay());
            Path requestedDir = dir.toAbsolutePath().normalize();
            if (actualDir.equals(requestedDir) || !actualDir.getParent().equals(requestedDir)
                    || !actualDir.getFileName().toString().startsWith("schema-6-")) {
                throw helper.assertionException(
                        "existing recorder evidence must trigger non-destructive schema-6 child isolation; got "
                                + actualDir);
            }
            if (!read(helper, dir.resolve("actions.tsv")).equals(legacyHeader)
                    || !read(helper, dir.resolve("manifest.json")).equals(legacyManifest)) {
                throw helper.assertionException("schema isolation must preserve old evidence byte-for-byte");
            }
            Path manifest = actualDir.resolve("manifest.json");
            assertContains(helper, manifest, "\"recorder\":\"LiveCursorIntentRecorder\"");
            assertContains(helper, manifest, "\"schemaVersion\":\"6\"");
            assertContains(helper, manifest,
                    "\"actionOriginContract\":\"PLAYER_AUTHORED|AUTO_USEON_PROXY\"");
            assertContains(helper, manifest,
                    "\"placementVerdictContract\":\"PlacementVerificationVerdict-v3\"");
            assertContains(helper, manifest,
                    "\"logicalAttemptContract\":\"LogicalPlacementAttempt-v1\"");
            assertContains(helper, actualDir.resolve("actions.tsv"),
                    "actionId\tcursorRowId\tactionType\tactionOrigin\theldItem");
            assertContains(helper, actualDir.resolve("actions.tsv"),
                    "logicalAttemptId\tphase\tplayerProof");
            assertContains(helper, manifest, "--accessToken [REDACTED]");
            String text = read(helper, manifest);
            if (text.contains("super-secret-jwt-token")) {
                throw helper.assertionException(
                        "manifest.json must NOT contain the raw accessToken value — got: " + text);
            }
            if (!text.contains("--username Maintainer")) {
                throw helper.assertionException(
                        "manifest.json's javaCommand must keep non-sensitive args — got: " + text);
            }
        } finally {
            if (priorCommand == null) {
                System.clearProperty("sun.java.command");
            } else {
                System.setProperty("sun.java.command", priorCommand);
            }
            System.clearProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY);
            System.clearProperty(LiveCursorIntentRecorder.DIR_PROPERTY);
            LiveCursorIntentRecorder.resetForTests();
        }
        helper.succeed();
    }

    private static JsonObject parseJsonObject(GameTestHelper helper, String row) {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(row);
        } catch (RuntimeException exception) {
            throw helper.assertionException(
                    "recorder session row was not valid JSON: " + row + " (" + exception + ")");
        }
        if (!parsed.isJsonObject()) {
            throw helper.assertionException("recorder session row was not a JSON object: " + row);
        }
        return parsed.getAsJsonObject();
    }

    private static String jsonString(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            return null;
        }
        return value.getAsString();
    }

    private static boolean exactJsonString(JsonObject object, String field, String expected) {
        return expected.equals(jsonString(object, field));
    }

    private static boolean hasAnyJsonField(JsonObject object, String[] fields) {
        for (String field : fields) {
            if (object.has(field)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMarkerToken(JsonObject object, String expected) {
        String marker = jsonString(object, "marker");
        if (marker == null) {
            return false;
        }
        for (String token : marker.split("\\|", -1)) {
            if (token.equals(expected)) {
                return true;
            }
        }
        return false;
    }

    private static void appendCsv(StringBuilder values, String value) {
        if (!values.isEmpty()) {
            values.append(',');
        }
        values.append(value);
    }

    private static void assertFinalVerdict(
            GameTestHelper helper,
            String label,
            PlacementVerificationVerdict.Result result,
            PlacementVerificationVerdict.FinalVerdict expected) {
        if (result.finalVerdict() != expected) {
            throw helper.assertionException(
                    label + " expected final verdict " + expected + " but got "
                            + result.finalVerdict() + " with " + result.canonicalFields());
        }
    }

    private static void assertCanonicalField(
            GameTestHelper helper,
            String label,
            PlacementVerificationVerdict.Result result,
            String field,
            String expected) {
        String actual = result.canonicalFields().get(field);
        if (!expected.equals(actual)) {
            throw helper.assertionException(
                    label + " expected " + field + "=" + expected + " but got "
                            + actual + " with " + result.canonicalFields());
        }
    }

    private static void assertComponentVerdict(
            GameTestHelper helper,
            String label,
            PlacementVerificationVerdict.Result result,
            PlacementVerificationVerdict.Component component,
            PlacementVerificationVerdict.ComponentStatus expected) {
        PlacementVerificationVerdict.ComponentStatus actual =
                result.componentStatus(component);
        if (actual != expected) {
            throw helper.assertionException(
                    label + " expected " + component + "=" + expected + " but got "
                            + actual + " with " + result.canonicalFields());
        }
    }

    private static void assertContainsValue(
            GameTestHelper helper,
            String label,
            java.util.List<?> values,
            Object expected) {
        if (!values.contains(expected)) {
            throw helper.assertionException(
                    label + " expected " + expected + " in " + values);
        }
    }

    private static boolean isRecorderOwnedActionId(String value) {
        return value != null && value.matches("[1-9][0-9]*");
    }

    private static String read(GameTestHelper helper, Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw helper.assertionException("expected recorder output file to exist and be readable: "
                    + path + " (" + e + ")");
        }
    }

    private static void write(GameTestHelper helper, Path path, String text) {
        try {
            Files.writeString(path, text);
        } catch (IOException e) {
            throw helper.assertionException("could not write recorder fixture " + path + ": " + e);
        }
    }

    private static void assertContains(GameTestHelper helper, Path path, String needle) {
        String text = read(helper, path);
        if (!text.contains(needle)) {
            throw helper.assertionException("missing '" + needle + "' in " + path);
        }
    }

    private static LinkedHashMap<String, String> hostileCursor(String callerId) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("type", "cursor");
        row.put("rowId", callerId);
        row.put("recordedAt", "2000-01-01T00:00:00Z");
        row.put("heldItem", "minecraft:stone");
        row.put("finalHitType", "BLOCK");
        row.put("finalHitPos", "2,2,2");
        row.put("finalHitState", "Block{minecraft:stone}");
        row.put("finalHitVec", "2.500000,3.000000,2.500000");
        row.put("finalHitFace", "up");
        row.put("outlineBounds",
                "min=(0.000000,0.000000,0.000000),max=(1.000000,1.000000,1.000000)");
        return row;
    }

    private static LinkedHashMap<String, String> hostileOutline(String callerId) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("type", "rendered_outline");
        row.put("outlineRenderId", callerId);
        row.put("cursorRowId", callerId);
        row.put("recordedAt", "2000-01-01T00:00:00Z");
        row.put("renderedOutlinePos", "2,2,2");
        row.put("renderedOutlineBounds",
                "min=(0.000000,0.000000,0.000000),max=(1.000000,1.000000,1.000000)");
        return row;
    }

    private static LinkedHashMap<String, String> hostileSentinel(
            String callerId,
            int sample) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("type", "model_stale_sentinel");
        row.put("rowId", callerId);
        row.put("recordedAt", "2000-01-01T00:00:00Z");
        row.put("kind", "MODEL_STALE_DIVERGENT");
        row.put("sample", Integer.toString(sample));
        return row;
    }

    private static LinkedHashMap<String, String> ordinaryAction(
            String heldItem,
            String ownerPos,
            String placementPos,
            String expectedDy,
            String afterDy,
            String actualResult) {
        LinkedHashMap<String, String> action = new LinkedHashMap<>();
        action.put("actionType", "place_block");
        action.put("side", "server");
        action.put("heldItem", heldItem);
        action.put("clickedOwnerPos", ownerPos);
        action.put("clickedFace", "UP");
        action.put("clickedOwnerLaneKind", "anchored_full_block");
        action.put("beforeDy", "-1.500000");
        action.put("placementPos", placementPos);
        action.put("expectedAfterDy", expectedDy);
        action.put("expectedAfterLaneKind", "unknown");
        action.put("afterState", "Block{" + heldItem + "}");
        action.put("afterDy", afterDy);
        action.put("afterLaneKind", "anchored_full_block");
        action.put("actualResult", actualResult);
        return action;
    }

    private static LinkedHashMap<String, String> fullyObservedVerifierAction() {
        LinkedHashMap<String, String> action = ordinaryAction(
                "minecraft:stone", "80,64,80", "80,65,80",
                "-0.500000", "-0.500000", "SUCCESS");
        action.put("afterStoredDy", "-0.500000");
        action.put("modelDy", "-0.500000");
        action.put("collisionDy", "-0.500000");
        action.put("raycastDy", "-0.500000");
        action.put("outlineDy", "-0.500000");
        action.put("stabilityVerdict", "PASS");
        action.put("expectedSupportPlane", "64.500000");
        action.put("actualContactPlane", "64.500000");
        action.put("seatError", "0.000000");
        action.put("placementRoute", "TOP_SEAT");
        action.put("landingAuthority", "CANONICAL_STORED_DY");
        action.put("rigCaseId", "verifier-truth-matrix");
        return action;
    }
}
