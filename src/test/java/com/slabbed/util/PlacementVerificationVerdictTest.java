package com.slabbed.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

final class PlacementVerificationVerdictTest {
    @Test
    void rejectsResolvedHeightBelowAdvertisedProductFloor() {
        PlacementVerificationVerdict.Result below = PlacementVerificationVerdict.reduce(
                placement("-1.500000", "-1.000000"));
        assertTrue(below.failureClasses().contains("RESOLVED_DY_BELOW_PRODUCT_FLOOR"));
        assertTrue(below.finalVerdict() == PlacementVerificationVerdict.FinalVerdict.RED);

        PlacementVerificationVerdict.Result boundary = PlacementVerificationVerdict.reduce(
                placement("-1.000000", "-1.000000"));
        assertFalse(boundary.failureClasses().contains("RESOLVED_DY_BELOW_PRODUCT_FLOOR"));
    }

    @Test
    void envelopeFloorAdmitsFlushLandingsAndStillRejectsBelowEnvelope() {
        // The live capture reports the targetable envelope (-2.0) as the floor; legal flush
        // landings between -1.0 and -2.0 must not red on the floor class.
        PlacementVerificationVerdict.Result flush = PlacementVerificationVerdict.reduce(
                placement("-1.500000", "-2.000000"));
        assertFalse(flush.failureClasses().contains("RESOLVED_DY_BELOW_PRODUCT_FLOOR"));

        PlacementVerificationVerdict.Result belowEnvelope = PlacementVerificationVerdict.reduce(
                placement("-2.500000", "-2.000000"));
        assertTrue(belowEnvelope.failureClasses().contains("RESOLVED_DY_BELOW_PRODUCT_FLOOR"));
        assertTrue(belowEnvelope.finalVerdict() == PlacementVerificationVerdict.FinalVerdict.RED);
    }

    private static LinkedHashMap<String, String> placement(String afterDy, String floorDy) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("actionType", "place_block");
        row.put("actualResult", "SUCCESS");
        row.put("afterDy", afterDy);
        row.put("resolvedFloorDy", floorDy);
        return row;
    }
}
