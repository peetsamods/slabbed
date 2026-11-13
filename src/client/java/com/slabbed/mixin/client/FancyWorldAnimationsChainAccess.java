package com.slabbed.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Identifies the optional renderer that owns an entire hanging column. */
@Pseudo
@Mixin(targets = "fr.madu59.fwa.anims.AbstractChainAnimation", remap = false)
public interface FancyWorldAnimationsChainAccess {
    @Accessor("chainCount") int slabbed$getChainCount();
}
