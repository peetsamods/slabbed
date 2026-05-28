package com.slabbed.mixin;

import com.slabbed.compat.CompatHooks;
import com.slabbed.compat.CompatSlabSurfaceKind;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class TerrainSlabsFaceCullingMixin {
    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
    private static void slabbed$drawTerrainTopFaceAgainstDirectTerrainSlabSupport(
            BlockState state,
            BlockState neighborState,
            Direction side,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (side == Direction.UP
                && state.isOpaqueFullCube()
                && CompatHooks.customSlabSurfaceKind(neighborState) == CompatSlabSurfaceKind.BOTTOM_LIKE
                && CompatHooks.shouldSkipSlabSupport(neighborState)) {
            cir.setReturnValue(true);
        }
    }
}
