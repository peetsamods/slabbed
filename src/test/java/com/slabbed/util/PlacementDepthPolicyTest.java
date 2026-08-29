package com.slabbed.util;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlacementDepthPolicyTest {
    @Test
    void classifiesTheCanonicalInteractionEnvelopeExactly() {
        assertEquals(PlacementDepthPolicy.Decision.VANILLA_OWNED,
                PlacementDepthPolicy.classify(0.0d));
        assertEquals(PlacementDepthPolicy.Decision.SUPPORTED,
                PlacementDepthPolicy.classify(-0.5d));
        assertEquals(PlacementDepthPolicy.Decision.SUPPORTED,
                PlacementDepthPolicy.classify(-1.0d));
        assertEquals(PlacementDepthPolicy.Decision.SUPPORTED,
                PlacementDepthPolicy.classify(-2.0d));
        // The boundary itself, derived: exactly the floor is legal, one half step past it is
        // not. Pinning literals here would stop measuring the boundary once the floor moved.
        assertEquals(PlacementDepthPolicy.Decision.SUPPORTED,
                PlacementDepthPolicy.classify(PlacementDepthPolicy.MIN_TARGETABLE_DY));
        assertEquals(PlacementDepthPolicy.Decision.REFUSED_BELOW_TARGETABLE_FLOOR,
                PlacementDepthPolicy.classify(PlacementDepthPolicy.MIN_TARGETABLE_DY - 0.5d));
        assertEquals(PlacementDepthPolicy.Decision.REFUSED_NON_CANONICAL,
                PlacementDepthPolicy.classify(-1.25d));
        assertEquals(PlacementDepthPolicy.Decision.REFUSED_NON_FINITE,
                PlacementDepthPolicy.classify(Double.NaN));
        assertEquals(PlacementDepthPolicy.Decision.REFUSED_NON_FINITE,
                PlacementDepthPolicy.classify(Double.NEGATIVE_INFINITY));

        assertFalse(PlacementDepthPolicy.classify(
                PlacementDepthPolicy.MIN_TARGETABLE_DY).refusesPlacement());
        assertTrue(PlacementDepthPolicy.classify(
                PlacementDepthPolicy.MIN_TARGETABLE_DY - 0.5d).refusesPlacement());
        assertEquals((int) Math.ceil(Math.abs(PlacementDepthPolicy.MIN_TARGETABLE_DY)),
                PlacementDepthPolicy.ownerWindowRadius(),
                "the pick window must be derived from the exact supported lower depth");
    }

    @Test
    void serverValidationConsumesTheSameTypedDecision() {
        Vec3 vanillaCenter = new Vec3(8.5d, 40.5d, -3.5d);
        assertEquals(vanillaCenter.add(0.0d, -2.0d, 0.0d),
                SlabbedServerHitValidation.validationCenter(vanillaCenter, -2.0d, null));
        assertSame(vanillaCenter,
                SlabbedServerHitValidation.validationCenter(vanillaCenter, 0.0d, null));
        assertSame(vanillaCenter,
                SlabbedServerHitValidation.validationCenter(
                        vanillaCenter, PlacementDepthPolicy.MIN_TARGETABLE_DY - 0.5d, null),
                "a refused owner must not gain a shifted server-admission center");
    }

    @Test
    void packetGateAcceptsEitherTheVanillaOrTheShiftedEnvelope() {
        Vec3 center = new Vec3(8.5d, 40.5d, -3.5d);

        // A hit on a lowered candle's visual box: outside the vanilla envelope, inside the
        // shifted one — the shift must make it admissible.
        Vec3 loweredVisualHit = new Vec3(8.687d, 39.105d, -3.65d);
        Vec3 chosenForLowered =
                SlabbedServerHitValidation.validationCenter(center, -1.0d, loweredVisualHit);
        assertTrue(withinTolerance(loweredVisualHit, chosenForLowered),
                "a lowered-visual hit must pass against the chosen center");

        // A hit vanilla itself would accept (a bridged chain's top face at local y 1.5 on a
        // -0.5 owner): the shift must never re-reject it. This exact shape was the observed
        // rejection class before the union rule (maintainer ruling, 2026-08-17).
        Vec3 vanillaLegalHit = new Vec3(8.47d, 41.5d, -3.43d);
        Vec3 chosenForVanilla =
                SlabbedServerHitValidation.validationCenter(center, -0.5d, vanillaLegalHit);
        assertTrue(withinTolerance(vanillaLegalHit, chosenForVanilla),
                "a vanilla-legal hit must never be re-rejected by the shift");
        assertSame(center, chosenForVanilla,
                "inside the vanilla envelope the vanilla center is returned verbatim");
    }

    private static boolean withinTolerance(Vec3 hit, Vec3 center) {
        return Math.abs(hit.x - center.x) < SlabbedServerHitValidation.VANILLA_HIT_TOLERANCE
                && Math.abs(hit.y - center.y) < SlabbedServerHitValidation.VANILLA_HIT_TOLERANCE
                && Math.abs(hit.z - center.z) < SlabbedServerHitValidation.VANILLA_HIT_TOLERANCE;
    }
}
