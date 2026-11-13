package com.slabbed.mixin.client;

import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reads the animation's immutable block position without loading an optional implementation. */
@Pseudo
@Mixin(targets = "fr.madu59.fwa.anims.Animation", remap = false)
public interface FancyWorldAnimationsAccessor {
    @Invoker("getPos") BlockPos slabbed$getPos();
}
