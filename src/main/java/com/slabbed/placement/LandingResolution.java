package com.slabbed.placement;

import java.util.Objects;
import net.minecraft.core.BlockPos;

/** Named result of resolving one placement action; internal and deliberately diagnostics-free. */
public sealed interface LandingResolution permits
        LandingResolution.Place,
        LandingResolution.PreserveVanilla,
        LandingResolution.Reject {

    String reason();

    enum Lane {
        LOWERED,
        FLUSH,
        RAISED;

        public static Lane fromDy(double dy) {
            if (!Double.isFinite(dy)) {
                throw new IllegalArgumentException("landing dy must be finite, got " + dy);
            }
            if (dy < -1.0e-6d) {
                return LOWERED;
            }
            if (dy > 1.0e-6d) {
                return RAISED;
            }
            return FLUSH;
        }
    }

    /** A successful authored placement with exact target and raw stored-height bits. */
    record Place(BlockPos targetPos, long rawDyBits, Lane lane, String reason)
            implements LandingResolution {
        public Place {
            targetPos = Objects.requireNonNull(targetPos, "targetPos").immutable();
            lane = Objects.requireNonNull(lane, "lane");
            reason = requireReason(reason);
            if (!Double.isFinite(Double.longBitsToDouble(rawDyBits))) {
                throw new IllegalArgumentException("placed dy bits must encode a finite value");
            }
        }

        public double rawDy() {
            return Double.longBitsToDouble(rawDyBits);
        }
    }

    /** The action deliberately remains vanilla-owned and publishes no authored fact. */
    record PreserveVanilla(String reason) implements LandingResolution {
        public PreserveVanilla {
            reason = requireReason(reason);
        }
    }

    /** The action is refused and publishes no authored fact. */
    record Reject(String reason) implements LandingResolution {
        public Reject {
            reason = requireReason(reason);
        }
    }

    private static String requireReason(String value) {
        String reason = Objects.requireNonNull(value, "reason").trim();
        if (reason.isEmpty()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        return reason;
    }
}
