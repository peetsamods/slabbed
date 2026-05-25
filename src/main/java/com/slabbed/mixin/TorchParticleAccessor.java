package com.slabbed.mixin;

import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.level.block.TorchBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
     * Exposes {@link TorchBlock}'s {@code flameParticle} field so subclasses (notably
 * {@link net.minecraft.world.level.block.WallTorchBlock}) can read the inherited flame
 * particle type from sibling mixins. Required because Mixin's {@code @Shadow}
     * does not traverse class hierarchy: shadowing {@code flameParticle} directly on
 * {@code WallTorchBlock} fails because the field is declared on {@code TorchBlock}.
 */
@Mixin(TorchBlock.class)
public interface TorchParticleAccessor {
    @Accessor("flameParticle")
    SimpleParticleType slabbed$getParticle();
}
