package com.slabbed.test.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Read-only view of an arrow's resting state for the lifecycle rows; the test never writes through it. */
@Mixin(AbstractArrow.class)
public interface AbstractArrowLifecycleAccessor {
    @Invoker("isInGround")
    boolean slabbed$isInGround();

    @Accessor("life")
    int slabbed$life();
}
