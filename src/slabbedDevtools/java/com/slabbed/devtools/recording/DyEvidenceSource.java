package com.slabbed.devtools.recording;

/** Pure source-label law shared by the overlay and schema contract. */
public final class DyEvidenceSource {
    private static final double EPSILON = 1.0e-6d;

    private DyEvidenceSource() {
    }

    public static String classify(
            boolean frozenFlat,
            boolean storedPresent,
            boolean anchored,
            boolean compoundSide,
            double dy) {
        if (frozenFlat) {
            return "FROZEN-FLAT";
        }
        if (storedPresent) {
            return "STORED";
        }
        if (anchored) {
            return "ANCHORED";
        }
        if (compoundSide) {
            return "compound-side";
        }
        return Math.abs(dy) > EPSILON ? "GEOMETRIC" : "-";
    }
}
