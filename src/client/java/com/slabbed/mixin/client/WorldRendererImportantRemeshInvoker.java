package com.slabbed.mixin.client;

import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the section rebuild priority used by vanilla and renderer replacements. */
@Mixin(WorldRenderer.class)
public interface WorldRendererImportantRemeshInvoker {
    @Invoker("scheduleChunkRender")
    void slabbed$scheduleChunkRender(int sectionX, int sectionY, int sectionZ, boolean important);
}
