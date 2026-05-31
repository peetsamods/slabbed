package com.slabbed.test;

final class TerrainSlabsProofFocus {
    private static final String[] PROPERTIES = {
            "slabbed.terrainSlabsCompatDump",
            "slabbed.terrainSlabsDirectSupportRedOnly",
            "slabbed.terrainSlabsGeneratedDoubleDirectSupportRedOnly",
            "slabbed.terrainSlabsExactSeedTrace",
            "slabbed.terrainSlabsLivePlacementProof",
            "slabbed.terrainSlabsParticleProof",
    };

    private TerrainSlabsProofFocus() {
    }

    static boolean skipUnrelatedClientGameTest(String testName) {
        if (!enabled()) {
            return false;
        }
        System.out.println("TERRAIN_SLABS_PROOF_FOCUS_SKIP test=" + testName);
        return true;
    }

    private static boolean enabled() {
        for (String property : PROPERTIES) {
            if (Boolean.getBoolean(property)) {
                return true;
            }
        }
        return false;
    }
}
