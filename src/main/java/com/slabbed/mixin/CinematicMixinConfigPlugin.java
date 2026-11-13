package com.slabbed.mixin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Optional scene adapters are admitted only for their verified third-party API version. */
public final class CinematicMixinConfigPlugin implements IMixinConfigPlugin {
    private boolean supported;
    private boolean modern;

    @Override
    public void onLoad(String mixinPackage) {
        var loader = FabricLoader.getInstance();
        var cinematic = loader.getModContainer("cinematic");
        modern = loader.getModContainer("minecraft").map(mod ->
                !mod.getMetadata().getVersion().getFriendlyString().startsWith("26.2")).orElse(false);
        supported = cinematic.map(mod -> mod.getMetadata().getVersion().getFriendlyString().equals("0.18.28"))
                .orElse(false)
                && loader.getModContainer("minecraft").map(mod ->
                    mod.getMetadata().getVersion().getFriendlyString().equals("26.3")).orElse(false);
        if (cinematic.isPresent() && !supported) {
            LoggerFactory.getLogger("Slabbed").warn("Cinematic Interactions table offset adapter is disabled "
                    + "for this unverified version. Its table scenes may not follow lowered blocks.");
        }
    }

    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (mixin.endsWith(".ModernBlockOverlayMixin")) return modern;
        if (mixin.endsWith(".LegacyBlockOverlayMixin")) return !modern;
        return !mixin.startsWith("com.slabbed.mixin.client.CinematicTable") || supported;
    }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> other) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
