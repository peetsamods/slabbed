package com.slabbed.test.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.slabbed.compat.terrainslabs.TerrainSlabsCompat;
import com.slabbed.test.BottomSlabSpawnProofTest;
import com.slabbed.test.CompatEligibilityPredicateTest;
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
    // Both gates, because isLoaded() is the OUTER door: CompatHooks asks it first and returns false
    // without ever calling the predicate, so a gate that opens only the inner LOADED reads opens
    // nothing. Either gate alone still scopes to its own rows - each is a ThreadLocal that is false
    // everywhere else.
    @ModifyReturnValue(method = "isLoaded", at = @At("RETURN"), remap = false)
    private static boolean slabbed$terrainSlabsLoadedForSpawnProof(boolean original) {
        return original
                || BottomSlabSpawnProofTest.terrainSlabsClassifierTestGate()
                || CompatEligibilityPredicateTest.eligibilityClassifierTestGate();
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

    // The ELIGIBILITY predicates need their own redirects, and the reason is easy to miss: the
    // isLoaded() ModifyReturnValue above is what lets CompatHooks REACH shouldSkipOffset, but that
    // method then guards on its own LOADED read, which the redirect above covers only inside
    // customSlabSurfaceKind. Without the two below it returns false before reaching isTerrainSlabsId,
    // so the shipped classification rule stays unexecutable and a mutation of it is invisible.
    // Kept on a separate gate from the spawn-proof one: these open the rule that decides LOWERING, so
    // a test must opt into that specifically rather than inherit it from an unrelated fixture.
    @Redirect(
            method = "shouldSkipOffset",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/slabbed/compat/terrainslabs/TerrainSlabsCompat;LOADED:Z",
                    opcode = Opcodes.GETSTATIC
            ),
            remap = false
    )
    private static boolean slabbed$terrainSlabsLoadedForEligibility() {
        return TerrainSlabsCompat.isLoaded()
                || CompatEligibilityPredicateTest.eligibilityClassifierTestGate();
    }

    @Redirect(
            method = "handlesObjectOffset",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/slabbed/compat/terrainslabs/TerrainSlabsCompat;LOADED:Z",
                    opcode = Opcodes.GETSTATIC
            ),
            remap = false
    )
    private static boolean slabbed$terrainSlabsLoadedForObjectOffset() {
        return TerrainSlabsCompat.isLoaded()
                || CompatEligibilityPredicateTest.eligibilityClassifierTestGate();
    }
}
