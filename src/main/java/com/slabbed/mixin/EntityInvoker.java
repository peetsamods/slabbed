package com.slabbed.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntityInvoker
{
    @Invoker("getCameraPosVec")
    Vec3d slabbed$getCameraPosVec(float tickProgress);

    @Invoker("getRotationVec")
    Vec3d slabbed$getRotationVec(float tickProgress);

    @Invoker("getEntityWorld")
    World slabbed$getEntityWorld();
}
