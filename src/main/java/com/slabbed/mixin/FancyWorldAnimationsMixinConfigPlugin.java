package com.slabbed.mixin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Optional scene adapters are admitted only for their verified third-party API version. */
public final class FancyWorldAnimationsMixinConfigPlugin implements IMixinConfigPlugin {
    private boolean supported;
    private boolean sectionCulling;

    @Override
    public void onLoad(String mixinPackage) {
        var loader = FabricLoader.getInstance();
        var fwa = loader.getModContainer("fwa");
        sectionCulling = fwa.map(mod -> mod.getMetadata().getVersion().getFriendlyString().equals("1.3.7")).orElse(false);
        supported = fwa.map(mod -> Set.of("1.3.4", "1.3.7").contains(mod.getMetadata().getVersion().getFriendlyString()))
                .orElse(false)
                && loader.getModContainer("minecraft").map(mod ->
                    mod.getMetadata().getVersion().getFriendlyString().equals("26.2")).orElse(false);
        if (fwa.isPresent() && !supported) {
            LoggerFactory.getLogger("Slabbed").warn("Fancy World Animations placement adapter is disabled "
                    + "for this unverified version. Its animations may not follow placed geometry.");
        }
    }

    @Override public boolean shouldApplyMixin(String target, String mixin) {
        return supported && (!mixin.endsWith("FancyWorldAnimationsSectionMixin") || sectionCulling);
    }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> other) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
