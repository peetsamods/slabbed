package com.slabbed.mixin.client;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Supplies the source block for optional renderer integrations. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext", remap = false)
public interface SodiumRenderContextAccessor {
    @Accessor("level") BlockRenderView slabbed$getLevel();
    @Accessor("pos") BlockPos slabbed$getPos();
    @Accessor("state") BlockState slabbed$getState();
}
