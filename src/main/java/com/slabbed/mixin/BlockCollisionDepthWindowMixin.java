package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabbedOffsetRaycast;
import net.minecraft.world.BlockCollisionSpliterator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Stored collision owners must be visited even when their bodies lie below their cells (LAW.md). */
@Mixin(BlockCollisionSpliterator.class)
public abstract class BlockCollisionDepthWindowMixin {
    @ModifyArgs(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/CuboidBlockIterator;<init>(IIIIII)V"))
    private void slabbed$includeDeepCollisionOwners(Args args) {
        if (SlabAnchorAttachment.FROZEN_DY_ENABLED) {
            int maxY = args.get(4);
            int radius = (int) Math.ceil(-SlabbedOffsetRaycast.DEEPEST_TARGETABLE_DY);
            args.set(4, maxY + radius);
        }
    }
}
