package com.slabbed.mixin.client;

import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Accesses the output origin without changing lighting or the block's stored position. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public interface SodiumBlockOriginAccessor {
    @Accessor("posOffset") Vector3f slabbed$getPosOffset();
}
