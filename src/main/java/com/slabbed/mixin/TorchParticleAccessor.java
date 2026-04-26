package com.slabbed.mixin;

import net.minecraft.block.TorchBlock;
import net.minecraft.particle.SimpleParticleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link TorchBlock}'s {@code particle} field so subclasses (notably
 * {@link net.minecraft.block.WallTorchBlock}) can read the inherited flame
 * particle type from sibling mixins. Required because Mixin's {@code @Shadow}
 * does not traverse class hierarchy: shadowing {@code particle} directly on
 * {@code WallTorchBlock} fails because the field is declared on {@code TorchBlock}.
 */
@Mixin(TorchBlock.class)
public interface TorchParticleAccessor {
    @Accessor("particle")
    SimpleParticleType slabbed$getParticle();
}
