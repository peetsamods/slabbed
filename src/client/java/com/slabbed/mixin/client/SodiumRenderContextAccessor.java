package com.slabbed.mixin.client;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Supplies the source cell and its snapshot to optional chunk renderer integrations. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext", remap = false)
public interface SodiumRenderContextAccessor {
    @Accessor("level") BlockAndTintGetter slabbed$getLevel();
    @Accessor("pos") BlockPos slabbed$getPos();
    @Accessor("state") BlockState slabbed$getState();
}
