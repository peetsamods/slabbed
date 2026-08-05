package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Redstone wire: allow slab top faces to count as valid ground for placement/survival.
 *
 * <p>GH #37 parity (mirrors 1.21.11 commit 37928aca): Slabbed provides slab SUPPORT only —
 * vanilla owns every horizontal connection decision. The former {@code getRenderConnectionType}
 * overrides widened vanilla's {@code NONE} verdict into {@code SIDE} toward any solid-topped
 * block (the "can dust stand on this?" predicate misread as "should dust connect to this?"),
 * creating phantom visual arms and real directional power toward inert blocks, and bypassing
 * component-facing and occlusion rules. Slab-height connectivity still works without them:
 * {@code SlabSupportStateMixin}'s {@code isSideSolid}/{@code isSideSolidFullSquare} UP
 * overrides let vanilla's own rise/drop discovery find dust above a slab
 * (proven by {@code RedstoneWireConnectionTest}).
 */
@Mixin(RedstoneWireBlock.class)
public abstract class RedstoneWireBlockMixin {

    @Inject(method = "canPlaceAt(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/WorldView;Lnet/minecraft/util/math/BlockPos;)Z",
            at = @At("HEAD"), cancellable = true)
    private void slabbed$canPlaceAt(BlockState state, WorldView world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (SlabSupport.isRedstoneSupportTopSurface(world, pos.down())) {
            cir.setReturnValue(true);
        }
    }
}
