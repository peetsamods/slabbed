package com.slabbed.util;

/** Shared interaction envelope for offset targeting, admission, and validation. */
public final class PlacementDepthPolicy {
    public static final double MIN_TARGETABLE_DY = -3.0d;
    private static final double HALF_STEP = 0.5d;

    private PlacementDepthPolicy() {
    }

    /**
     * Classifies an immutable owner or landing offset before any placement mutation.
     * The lower boundary is strict: exactly {@code -3.0} is supported.
     */
    public static Decision classify(double dy) {
        if (!Double.isFinite(dy)) {
            return Decision.REFUSED_NON_FINITE;
        }
        if (dy < MIN_TARGETABLE_DY) {
            return Decision.REFUSED_BELOW_TARGETABLE_FLOOR;
        }
        double halfSteps = dy / HALF_STEP;
        if (Math.rint(halfSteps) != halfSteps) {
            return Decision.REFUSED_NON_CANONICAL;
        }
        return dy == 0.0d ? Decision.VANILLA_OWNED : Decision.SUPPORTED;
    }

    /** Number of logical owner cells targeting must inspect above and below each marched cell. */
    public static int ownerWindowRadius() {
        return (int) Math.ceil(Math.abs(Math.min(0.0d, MIN_TARGETABLE_DY)));
    }

    public enum Decision {
        VANILLA_OWNED(false, false),
        SUPPORTED(true, false),
        REFUSED_BELOW_TARGETABLE_FLOOR(false, true),
        REFUSED_NON_FINITE(false, true),
        REFUSED_NON_CANONICAL(false, true);

        private final boolean shiftedOwner;
        private final boolean refusesPlacement;

        Decision(boolean shiftedOwner, boolean refusesPlacement) {
            this.shiftedOwner = shiftedOwner;
            this.refusesPlacement = refusesPlacement;
        }

        public boolean shiftedOwner() {
            return shiftedOwner;
        }

        public boolean refusesPlacement() {
            return refusesPlacement;
        }
    }
}
