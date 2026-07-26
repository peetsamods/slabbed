package com.slabbed.test.mixin;

import com.slabbed.compat.terrainslabs.TerrainSlabsCompat;
import com.slabbed.test.BottomSlabSpawnProofTest;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Enables the real Terrain Slabs classifier only inside the synchronous Issue #39 GameTest gate. */
@Mixin(TerrainSlabsCompat.class)
public abstract class TerrainSlabsCompatGameTestMixin {
    @Inject(method = "isLoaded", at = @At("RETURN"), cancellable = true, remap = false)
    private static void slabbed$terrainSlabsLoadedForSpawnProof(
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (BottomSlabSpawnProofTest.terrainSlabsClassifierTestGate()) {
            cir.setReturnValue(true);
        }
    }

    @Redirect(
            method = "customSlabSurfaceKind",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/slabbed/compat/terrainslabs/TerrainSlabsCompat;LOADED:Z",
                    opcode = Opcodes.GETSTATIC
            ),
            remap = false
    )
    private static boolean slabbed$terrainSlabsLoadedForSpawnProof() {
        return TerrainSlabsCompat.isLoaded()
                || BottomSlabSpawnProofTest.terrainSlabsClassifierTestGate();
    }
}
