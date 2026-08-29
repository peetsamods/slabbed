package com.slabbed.client;

import com.slabbed.util.PlacementDepthPolicy;
import com.slabbed.util.SlabSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DeepDyConsentClientSyncTest {
    /**
     * This row guarded against a client carrying one server's consent into the next connection,
     * back when consent chose the floor. The floor is now the same for every save, so there is no
     * per-connection value left to leak - which is what this asserts: the floor a client resolves
     * with is the envelope before a connection, during one, and after logging out of one.
     */
    @Test
    void noConnectionCanLeaveTheClientOnADifferentFloor() {
        try {
            SlabSupport.armDeepAlphabet(true);
            assertEquals(PlacementDepthPolicy.MIN_TARGETABLE_DY, SlabSupport.minResolvedDy(),
                    "a connected client resolves on the envelope");

            DeepDyConsentClientSync.onLoggingOut(null);

            assertEquals(PlacementDepthPolicy.MIN_TARGETABLE_DY, SlabSupport.minResolvedDy(),
                    "logging out must leave the client on the same floor it joined with");
        } finally {
            SlabSupport.armDeepAlphabet(false);
        }
    }
}
