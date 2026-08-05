package com.slabbed.test.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.slabbed.compat.terrainslabs.TerrainSlabsCompat;
import com.slabbed.test.BottomSlabSpawnProofTest;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Enables the real Terrain Slabs classifier only inside the synchronous Issue #39 GameTest gate. */
@Mixin(TerrainSlabsCompat.class)
public abstract class TerrainSlabsCompatGameTestMixin {
    // ModifyReturnValue, not a cancellable RETURN Inject: the latter allocates one
    // CallbackInfoReturnable per call, and isLoaded() sits on the frozen-dy read that the render
    // allocation gate pins at zero. That garbage is gametest-only, but it forced the gate to carry a
    // per-call allowance that could mask a real regression.
    @ModifyReturnValue(method = "isLoaded", at = @At("RETURN"), remap = false)
    private static boolean slabbed$terrainSlabsLoadedForSpawnProof(boolean original) {
        return original || BottomSlabSpawnProofTest.terrainSlabsClassifierTestGate();
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
