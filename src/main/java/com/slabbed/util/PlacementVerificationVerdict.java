package com.slabbed.util;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure placement-verification reducer. It classifies already-captured evidence and never reads or
 * changes Minecraft state.
 */
public final class PlacementVerificationVerdict {
    public static final String MUST_REFUSE_VANILLA = "MUST_REFUSE_VANILLA";

    public enum FinalVerdict {
        GREEN("LIVE_PLACEMENT_VERDICT_GREEN"),
        RED("LIVE_PLACEMENT_VERDICT_RED"),
        INCONCLUSIVE("LIVE_PLACEMENT_VERDICT_INCONCLUSIVE"),
        EXPECTED_REFUSAL("LIVE_PLACEMENT_VERDICT_EXPECTED_REFUSAL"),
        UNCLASSIFIED_FAILURE("LIVE_PLACEMENT_VERDICT_UNCLASSIFIED_FAILURE");

        private final String marker;

        FinalVerdict(String marker) {
            this.marker = marker;
        }

        public String marker() {
            return marker;
        }
    }

    public enum Component {
        PLACED("placedVerdict"),
        ANCHOR("anchorVerdict"),
        MODEL("modelVerdict"),
        COLLISION("collisionVerdict"),
        RAYCAST("raycastVerdict"),
        OUTLINE("outlineVerdict"),
        STABILITY("stabilityVerdict");

        private final String fieldName;

        Component(String fieldName) {
            this.fieldName = fieldName;
        }

        public String fieldName() {
            return fieldName;
        }
    }

    public enum ComponentStatus {
        PASS,
        FAIL,
        UNKNOWN,
        MISSING,
        NOT_RUN,
        NOT_APPLICABLE;

        private boolean isMissingRequiredEvidence() {
            return this == UNKNOWN
                    || this == MISSING
                    || this == NOT_RUN
                    || this == NOT_APPLICABLE;
        }
    }

    public static final class Result {
        private final FinalVerdict finalVerdict;
        private final EnumMap<Component, ComponentStatus> components;
        private final String intentDy;
        private final String storedDy;
        private final String modelDy;
        private final String collisionDy;
        private final String raycastDy;
        private final String outlineDy;
        private final String expectedSupportPlane;
        private final String actualContactPlane;
        private final String seatError;
        private final String placementRoute;
        private final String landingAuthority;
        private final String rigCaseId;
        private final String expectedRefusalReason;
        private final String actualRefusalReason;
        private final List<Component> missingRequiredComponents;
        private final List<String> failureClasses;

        private Result(
                FinalVerdict finalVerdict,
                EnumMap<Component, ComponentStatus> components,
                String intentDy,
                String storedDy,
                String modelDy,
                String collisionDy,
                String raycastDy,
                String outlineDy,
                String expectedSupportPlane,
                String actualContactPlane,
                String seatError,
                String placementRoute,
                String landingAuthority,
                String rigCaseId,
                String expectedRefusalReason,
                String actualRefusalReason,
                List<Component> missingRequiredComponents,
                List<String> failureClasses) {
            this.finalVerdict = finalVerdict;
            this.components = new EnumMap<>(components);
            this.intentDy = intentDy;
            this.storedDy = storedDy;
            this.modelDy = modelDy;
            this.collisionDy = collisionDy;
            this.raycastDy = raycastDy;
            this.outlineDy = outlineDy;
            this.expectedSupportPlane = expectedSupportPlane;
            this.actualContactPlane = actualContactPlane;
            this.seatError = seatError;
            this.placementRoute = placementRoute;
            this.landingAuthority = landingAuthority;
            this.rigCaseId = rigCaseId;
            this.expectedRefusalReason = expectedRefusalReason;
            this.actualRefusalReason = actualRefusalReason;
            this.missingRequiredComponents = List.copyOf(missingRequiredComponents);
            this.failureClasses = List.copyOf(failureClasses);
        }

        public FinalVerdict finalVerdict() {
            return finalVerdict;
        }

        public ComponentStatus componentStatus(Component component) {
            return components.get(component);
        }

        public List<Component> missingRequiredComponents() {
            return missingRequiredComponents;
        }

        public List<String> failureClasses() {
            return failureClasses;
        }

        /** Returns a new scalar-only map suitable for appending to one recorder action row. */
        public LinkedHashMap<String, String> canonicalFields() {
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("finalVerdict", finalVerdict.name());
            for (Component component : Component.values()) {
                fields.put(component.fieldName(), components.get(component).name());
            }
            fields.put("intentDy", intentDy);
            fields.put("storedDy", storedDy);
            fields.put("modelDy", modelDy);
            fields.put("collisionDy", collisionDy);
            fields.put("raycastDy", raycastDy);
            fields.put("outlineDy", outlineDy);
            fields.put("expectedSupportPlane", expectedSupportPlane);
            fields.put("actualContactPlane", actualContactPlane);
            fields.put("seatError", seatError);
            fields.put("placementRoute", placementRoute);
            fields.put("landingAuthority", landingAuthority);
            fields.put("rigCaseId", rigCaseId);
            fields.put("expectedRefusalReason", expectedRefusalReason);
            fields.put("actualRefusalReason", actualRefusalReason);
            fields.put("missingRequiredComponents", joinComponents(missingRequiredComponents));
            fields.put("failureClasses", joinStrings(failureClasses));
            return fields;
        }
    }

    private PlacementVerificationVerdict() {
    }

    /**
     * Reduces one action's captured evidence. Expected/after dy values are accepted only as intent or
     * legacy diagnostics; a component can pass only from its own actual evidence.
     */
    public static Result reduce(Map<String, String> evidence) {
        Map<String, String> row = evidence == null ? Map.of() : evidence;
        String explicitIntentDy = firstEvidence(row, "intentDy");
        String expectedAfterDy = firstEvidence(row, "expectedAfterDy");
        boolean intentDyAliasConflict = !"unknown".equals(explicitIntentDy)
                && !"unknown".equals(expectedAfterDy)
                && !sameIntentDy(explicitIntentDy, expectedAfterDy);
        String intentDy = intentDyAliasConflict
                ? "unknown"
                : !"unknown".equals(explicitIntentDy) ? explicitIntentDy : expectedAfterDy;
        String storedDy = firstEvidence(row, "storedDy", "afterStoredDy");
        String modelDy = firstEvidence(row, "modelDy");
        String collisionDy = firstEvidence(row, "collisionDy");
        String raycastDy = firstEvidence(row, "raycastDy");
        String outlineDy = firstEvidence(row, "outlineDy");
        String expectedSupportPlane = firstEvidence(row, "expectedSupportPlane");
        String actualContactPlane = firstEvidence(row, "actualContactPlane");
        String seatError = firstEvidence(row, "seatError");
        String placementRoute = firstEvidence(row, "placementRoute");
        String landingAuthority = firstEvidence(row, "landingAuthority");
        String rigCaseId = firstEvidence(row, "rigCaseId");
        String expectedRefusalReason = firstEvidence(row, "expectedRefusalReason");
        String actualResult = row.get("actualResult");
        if (actualResult == null) {
            actualResult = "";
        }
        String explicitActualRefusalReason = firstEvidence(row, "actualRefusalReason");
        String parsedActualRefusalReason = parseActualRefusalReason(actualResult);
        boolean actualRefusalReasonConflict = !"unknown".equals(explicitActualRefusalReason)
                && !"unknown".equals(parsedActualRefusalReason)
                && !explicitActualRefusalReason.equals(parsedActualRefusalReason);
        String actualRefusalReason = actualRefusalReasonConflict
                ? "unknown"
                : !"unknown".equals(explicitActualRefusalReason)
                        ? explicitActualRefusalReason
                        : parsedActualRefusalReason;

        EnumMap<Component, ComponentStatus> components = new EnumMap<>(Component.class);
        LinkedHashSet<String> failureClasses = new LinkedHashSet<>();
        boolean expectedRefusal = MUST_REFUSE_VANILLA.equals(row.get("expectedResult"))
                || MUST_REFUSE_VANILLA.equals(row.get("placementContract"))
                || MUST_REFUSE_VANILLA.equals(row.get("refusalContract"));
        boolean refusalOccurred = actualResult.startsWith("Fail[");

        if (refusalOccurred && actualRefusalReasonConflict) {
            putAll(components, ComponentStatus.NOT_APPLICABLE);
            components.put(Component.PLACED, ComponentStatus.FAIL);
            failureClasses.add("ACTUAL_REFUSAL_REASON_CONFLICT");
            if (!expectedRefusal) {
                failureClasses.add("UNDECLARED_PLACEMENT_FAILURE");
            }
            return result(
                    FinalVerdict.UNCLASSIFIED_FAILURE, components,
                    intentDy, storedDy, modelDy, collisionDy, raycastDy, outlineDy,
                    expectedSupportPlane, actualContactPlane, seatError,
                    placementRoute, landingAuthority, rigCaseId,
                    expectedRefusalReason, actualRefusalReason,
                    List.of(), failureClasses);
        }

        if (expectedRefusal && refusalOccurred) {
            putAll(components, ComponentStatus.NOT_APPLICABLE);
            if ("unknown".equals(expectedRefusalReason)
                    || "unknown".equals(actualRefusalReason)) {
                components.put(Component.PLACED, ComponentStatus.FAIL);
                failureClasses.add("EXPECTED_REFUSAL_REASON_MISSING");
                return result(
                        FinalVerdict.UNCLASSIFIED_FAILURE, components,
                        intentDy, storedDy, modelDy, collisionDy, raycastDy, outlineDy,
                        expectedSupportPlane, actualContactPlane, seatError,
                        placementRoute, landingAuthority, rigCaseId,
                        expectedRefusalReason, actualRefusalReason,
                        List.of(), failureClasses);
            }
            if (!expectedRefusalReason.equals(actualRefusalReason)) {
                components.put(Component.PLACED, ComponentStatus.FAIL);
                failureClasses.add("EXPECTED_REFUSAL_REASON_MISMATCH");
                return result(
                        FinalVerdict.UNCLASSIFIED_FAILURE, components,
                        intentDy, storedDy, modelDy, collisionDy, raycastDy, outlineDy,
                        expectedSupportPlane, actualContactPlane, seatError,
                        placementRoute, landingAuthority, rigCaseId,
                        expectedRefusalReason, actualRefusalReason,
                        List.of(), failureClasses);
            }
            return result(
                    FinalVerdict.EXPECTED_REFUSAL, components,
                    intentDy, storedDy, modelDy, collisionDy, raycastDy, outlineDy,
                    expectedSupportPlane, actualContactPlane, seatError,
                    placementRoute, landingAuthority, rigCaseId,
                    expectedRefusalReason, actualRefusalReason,
                    List.of(), failureClasses);
        }

        if (!expectedRefusal && refusalOccurred) {
            putAll(components, ComponentStatus.NOT_APPLICABLE);
            components.put(Component.PLACED, ComponentStatus.FAIL);
            failureClasses.add("UNDECLARED_PLACEMENT_FAILURE");
            return result(
                    FinalVerdict.UNCLASSIFIED_FAILURE, components,
                    intentDy, storedDy, modelDy, collisionDy, raycastDy, outlineDy,
                    expectedSupportPlane, actualContactPlane, seatError,
                    placementRoute, landingAuthority, rigCaseId,
                    expectedRefusalReason, actualRefusalReason,
                    List.of(), failureClasses);
        }

        boolean placementAction = "place_block".equals(row.get("actionType"));
        boolean placementSucceeded = placementAction && isSuccessfulResult(actualResult);
        boolean placedFailure = false;
        if (intentDyAliasConflict) {
            failureClasses.add("INTENT_DY_ALIAS_CONFLICT");
            placedFailure = true;
        }
        if (expectedRefusal && placementSucceeded) {
            failureClasses.add("EXPECTED_REFUSAL_DID_NOT_OCCUR");
            placedFailure = true;
        } else if (!intentDyAliasConflict
                && placementSucceeded
                && isFinite(intentDy)
                && isFinite(row.get("afterDy"))
                && !sameNumber(intentDy, row.get("afterDy"))) {
            failureClasses.add("PLACED_ACTION_DY_MISMATCH");
            placedFailure = true;
        }
        components.put(Component.PLACED,
                placedFailure
                        ? ComponentStatus.FAIL
                        : placementSucceeded ? ComponentStatus.PASS : ComponentStatus.MISSING);

        components.put(Component.ANCHOR,
                dyComponent(row, Component.ANCHOR, storedDy, intentDy, failureClasses));
        components.put(Component.MODEL,
                dyComponent(row, Component.MODEL, modelDy, intentDy, failureClasses));
        components.put(Component.COLLISION,
                collisionComponent(
                        row, collisionDy, intentDy,
                        expectedSupportPlane, actualContactPlane, seatError, failureClasses));
        components.put(Component.RAYCAST,
                dyComponent(row, Component.RAYCAST, raycastDy, intentDy, failureClasses));
        components.put(Component.OUTLINE,
                dyComponent(row, Component.OUTLINE, outlineDy, intentDy, failureClasses));
        components.put(Component.STABILITY, stabilityComponent(row, failureClasses));

        List<Component> missingRequiredComponents = List.of(Component.values()).stream()
                .filter(component -> components.get(component).isMissingRequiredEvidence())
                .toList();
        boolean failedComponent = components.containsValue(ComponentStatus.FAIL);
        FinalVerdict finalVerdict = failedComponent
                ? FinalVerdict.RED
                : missingRequiredComponents.isEmpty()
                        ? FinalVerdict.GREEN
                        : FinalVerdict.INCONCLUSIVE;
        return result(
                finalVerdict, components,
                intentDy, storedDy, modelDy, collisionDy, raycastDy, outlineDy,
                expectedSupportPlane, actualContactPlane, seatError,
                placementRoute, landingAuthority, rigCaseId,
                expectedRefusalReason, actualRefusalReason,
                missingRequiredComponents, failureClasses);
    }

    private static ComponentStatus collisionComponent(
            Map<String, String> row,
            String collisionDy,
            String intentDy,
            String expectedSupportPlane,
            String actualContactPlane,
            String seatError,
            Set<String> failureClasses) {
        ComponentStatus explicit = explicitStatus(row, Component.COLLISION);
        if (explicit == ComponentStatus.FAIL) {
            failureClasses.add("COLLISION_COMPONENT_FAILURE");
            return explicit;
        }

        boolean actualObservable = !"unknown".equals(collisionDy)
                || !"unknown".equals(actualContactPlane)
                || !"unknown".equals(seatError);
        boolean passedObservable = false;
        boolean unresolvedObservable = false;
        if (isFinite(collisionDy)) {
            if (isFinite(intentDy)) {
                if (!sameNumber(intentDy, collisionDy)) {
                    failureClasses.add("COLLISION_DY_MISMATCH");
                    return ComponentStatus.FAIL;
                }
                passedObservable = true;
            } else {
                unresolvedObservable = true;
            }
        } else if (!"unknown".equals(collisionDy)) {
            unresolvedObservable = true;
        }

        if (isFinite(expectedSupportPlane) && isFinite(actualContactPlane)) {
            if (!sameNumber(expectedSupportPlane, actualContactPlane)) {
                failureClasses.add("COLLISION_CONTACT_PLANE_MISMATCH");
                return ComponentStatus.FAIL;
            }
            passedObservable = true;
        } else if (!"unknown".equals(expectedSupportPlane)
                || !"unknown".equals(actualContactPlane)) {
            unresolvedObservable = true;
        }

        if (isFinite(seatError)) {
            if (Math.abs(Double.parseDouble(seatError)) > 1.0e-6d) {
                failureClasses.add("COLLISION_SEAT_ERROR");
                return ComponentStatus.FAIL;
            }
            passedObservable = true;
        } else if (!"unknown".equals(seatError)) {
            unresolvedObservable = true;
        }

        if (actualObservable) {
            if (unresolvedObservable) {
                return ComponentStatus.UNKNOWN;
            }
            return passedObservable ? ComponentStatus.PASS : ComponentStatus.UNKNOWN;
        }
        if (explicit == ComponentStatus.NOT_APPLICABLE) {
            return ComponentStatus.MISSING;
        }
        if (unresolvedObservable) {
            return ComponentStatus.UNKNOWN;
        }
        return unavailableStatus(explicit, collisionDy);
    }

    private static ComponentStatus dyComponent(
            Map<String, String> row,
            Component component,
            String actualDy,
            String intentDy,
            Set<String> failureClasses) {
        ComponentStatus explicit = explicitStatus(row, component);
        if (explicit == ComponentStatus.FAIL) {
            failureClasses.add(component.name() + "_COMPONENT_FAILURE");
            return explicit;
        }
        if ("unknown".equals(actualDy)
                && (explicit == ComponentStatus.NOT_APPLICABLE
                        || component == Component.MODEL
                        || component == Component.RAYCAST
                        || component == Component.OUTLINE)) {
            return ComponentStatus.MISSING;
        }
        if (!"unknown".equals(actualDy)) {
            if (isFinite(actualDy) && isFinite(intentDy)) {
                if (sameNumber(actualDy, intentDy)) {
                    return ComponentStatus.PASS;
                }
                failureClasses.add(component == Component.ANCHOR
                        ? "ANCHOR_STORED_DY_MISMATCH"
                        : component.name() + "_DY_MISMATCH");
                return ComponentStatus.FAIL;
            }
            return ComponentStatus.UNKNOWN;
        }
        if (explicit == ComponentStatus.NOT_APPLICABLE) {
            return explicit;
        }
        return unavailableStatus(explicit, actualDy);
    }

    private static ComponentStatus stabilityComponent(
            Map<String, String> row,
            Set<String> failureClasses) {
        ComponentStatus explicit = explicitStatus(row, Component.STABILITY);
        if (explicit == ComponentStatus.FAIL) {
            failureClasses.add("STABILITY_COMPONENT_FAILURE");
        }
        if (explicit == ComponentStatus.NOT_APPLICABLE) {
            return ComponentStatus.MISSING;
        }
        return explicit == null ? ComponentStatus.NOT_RUN : explicit;
    }

    private static ComponentStatus unavailableStatus(ComponentStatus explicit, String actual) {
        if (explicit == ComponentStatus.UNKNOWN
                || explicit == ComponentStatus.MISSING
                || explicit == ComponentStatus.NOT_RUN) {
            return explicit;
        }
        return "unknown".equals(actual) ? ComponentStatus.MISSING : ComponentStatus.UNKNOWN;
    }

    private static ComponentStatus explicitStatus(Map<String, String> row, Component component) {
        String value = row.get(component.fieldName());
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        if ("UNKNOWN/MISSING".equals(normalized)) {
            return ComponentStatus.MISSING;
        }
        try {
            return ComponentStatus.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String parseActualRefusalReason(String actualResult) {
        if (!actualResult.startsWith("Fail[")
                || !actualResult.endsWith("]")) {
            return "unknown";
        }
        String capturedReason = actualResult.substring("Fail[".length(), actualResult.length() - 1);
        return isEvidence(capturedReason) ? capturedReason.trim() : "unknown";
    }

    private static boolean sameIntentDy(String left, String right) {
        if (isFinite(left) && isFinite(right)) {
            return sameNumber(left, right);
        }
        return left.equals(right);
    }

    private static boolean isSuccessfulResult(String actualResult) {
        String normalized = actualResult == null
                ? ""
                : actualResult.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("success")
                || normalized.startsWith("success[")
                || normalized.equals("consume")
                || normalized.startsWith("consume[");
    }

    private static String firstEvidence(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String value = row.get(key);
            if (isEvidence(value)) {
                return value.trim();
            }
        }
        return "unknown";
    }

    private static boolean isEvidence(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return !normalized.equals("unknown")
                && !normalized.equals("none")
                && !normalized.equals("missing")
                && !normalized.equals("not_run")
                && !normalized.equals("not_applicable")
                && !normalized.equals("null");
    }

    private static boolean isFinite(String value) {
        try {
            return Double.isFinite(Double.parseDouble(value));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean sameNumber(String expected, String actual) {
        return Math.abs(Double.parseDouble(expected) - Double.parseDouble(actual)) <= 1.0e-6d;
    }

    private static void putAll(
            EnumMap<Component, ComponentStatus> components,
            ComponentStatus status) {
        for (Component component : Component.values()) {
            components.put(component, status);
        }
    }

    private static Result result(
            FinalVerdict finalVerdict,
            EnumMap<Component, ComponentStatus> components,
            String intentDy,
            String storedDy,
            String modelDy,
            String collisionDy,
            String raycastDy,
            String outlineDy,
            String expectedSupportPlane,
            String actualContactPlane,
            String seatError,
            String placementRoute,
            String landingAuthority,
            String rigCaseId,
            String expectedRefusalReason,
            String actualRefusalReason,
            List<Component> missingRequiredComponents,
            Set<String> failureClasses) {
        return new Result(
                finalVerdict, components,
                intentDy, storedDy, modelDy, collisionDy, raycastDy, outlineDy,
                expectedSupportPlane, actualContactPlane, seatError,
                placementRoute, landingAuthority, rigCaseId,
                expectedRefusalReason, actualRefusalReason,
                missingRequiredComponents, List.copyOf(failureClasses));
    }

    private static String joinComponents(List<Component> components) {
        return components.isEmpty()
                ? "none"
                : components.stream().map(Component::name).reduce((left, right) -> left + "," + right).orElse("none");
    }

    private static String joinStrings(List<String> values) {
        return values.isEmpty() ? "none" : String.join(",", values);
    }
}
