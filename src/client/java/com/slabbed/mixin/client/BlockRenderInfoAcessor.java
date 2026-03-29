package com.slabbed.mixin.client;

import net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlockRenderInfo.class)
public interface BlockRenderInfoAcessor
{
    @Accessor("cullCompletionFlags")
    void slabbed$setCullCompletionFlags(int flags);

    @Accessor("cullResultFlags")
    void slabbed$setCullResultFlags(int flags);
}
