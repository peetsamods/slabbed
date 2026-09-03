package com.slabbed.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Config plugin for {@code slabbed.mixins.json}: the one place a mixin may be withheld at load
 * time because another mod owns the same call site.
 *
 * <p>Lithium's {@code world.explosions.entity_raycast} module redirects the single
 * {@code Level.clip} call in {@code ServerExplosion.getSeenPercent} — the exact instruction
 * {@link ServerExplosionOcclusionOffsetClipMixin} redirects. Two redirects cannot share one call
 * site: whichever applies second scans zero targets, and with {@code require = 1} that is a hard
 * injection failure before the title screen. While the mod id {@code lithium} is loaded, that ONE
 * mixin is not applied and explosion occlusion follows vanilla rules; every other Slabbed mixin
 * applies unchanged (maintainer ruling, 2026-09-02).
 *
 * <p>Invariants this plugin protects — do not trade them away:
 * <ul>
 *   <li>{@code require = 1} stays on every clip redirect. Tolerating a zero-match would turn a
 *       future vanilla rename into a silent loss of the fix behind a green suite.</li>
 *   <li>The skip is keyed on a mod id, and that is legitimate ONLY because this is a load-time
 *       conflict over Slabbed's own patch — the plugin claims nothing about Lithium's behaviour or
 *       data. Deferring a behaviour to another mod is gated on that mod's data, never on its id.</li>
 *   <li>The mixin is named as a string and the plugin references no Minecraft or mixin class: it
 *       runs before those classes are transformed, and a class literal here would load the mixin
 *       through the normal classloader.</li>
 * </ul>
 */
public final class SlabbedMixinConfigPlugin implements IMixinConfigPlugin {

    static final String LITHIUM_MOD_ID = "lithium";
    static final String EXPLOSION_OCCLUSION_MIXIN =
            "com.slabbed.mixin.ServerExplosionOcclusionOffsetClipMixin";

    private static final Logger LOGGER = LoggerFactory.getLogger("slabbed/mixin");

    private boolean lithiumPresent;
    private boolean announced;

    @Override
    public void onLoad(String mixinPackage) {
        this.lithiumPresent = FabricLoader.getInstance().isModLoaded(LITHIUM_MOD_ID);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!withheldWhileLithiumPresent(mixinClassName, this.lithiumPresent)) {
            return true;
        }
        if (!this.announced) {
            this.announced = true;
            LOGGER.info("Lithium is present: explosion occlusion follows vanilla rules while Lithium is "
                    + "present (ServerExplosionOcclusionOffsetClipMixin not applied; Lithium's explosion "
                    + "ray cast owns the same call site)");
        }
        return false;
    }

    /** The whole decision as a pure function: only the explosion-occlusion mixin, only with Lithium. */
    static boolean withheldWhileLithiumPresent(String mixinClassName, boolean lithiumPresent) {
        return lithiumPresent && EXPLOSION_OCCLUSION_MIXIN.equals(mixinClassName);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                         IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                          IMixinInfo mixinInfo) {
    }
}
