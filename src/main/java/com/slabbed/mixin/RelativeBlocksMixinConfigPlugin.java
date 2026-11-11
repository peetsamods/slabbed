package com.slabbed.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** The compositor adapter is limited to the platform and RPB layouts it was verified against. */
public final class RelativeBlocksMixinConfigPlugin implements IMixinConfigPlugin {
    private boolean supported;

    @Override
    public void onLoad(String mixinPackage) {
        var loader = FabricLoader.getInstance();
        var mod = loader.getModContainer("relativeblocks");
        if (mod.isEmpty()) return;
        String version = mod.get().getMetadata().getVersion().getFriendlyString();
        String minecraft = loader.getModContainer("minecraft").orElseThrow()
                .getMetadata().getVersion().getFriendlyString();
        supported = supports(minecraft, version);
        if (!supported) {
            LoggerFactory.getLogger("slabbed/relativeblocks").warn(
                    "Relatively Placed Blocks compositor compatibility is inactive for Minecraft {} / RPB {}; "
                            + "this adapter supports Minecraft 26.3 with RPB 2.0.0 or 2.0.2", minecraft, version);
        }
    }

    public static boolean supports(String minecraft, String version) {
        return "26.3".equals(minecraft) && ("2.0.0".equals(version) || "2.0.2".equals(version));
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return supported; }
    @Override
    public String getRefMapperConfig() { return null; }
    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override
    public List<String> getMixins() { return null; }
    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
