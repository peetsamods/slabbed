package com.slabbed.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * Config plugin for {@code slabbed.mixins.json}: the one place a mixin may be withheld at load
 * time because of another mod (maintainer ruling, 2026-09-02).
 *
 * <p>Two Lithium rules, in opposite directions:
 * <ul>
 *   <li>Lithium's {@code world.explosions.entity_raycast} module redirects the single
 *       {@code Level.clip} call in {@code ServerExplosion.getSeenPercent} — the exact instruction
 *       {@link ServerExplosionOcclusionOffsetClipMixin} redirects. Two redirects cannot share one
 *       call site: whichever applies second scans zero targets, and with {@code require = 1} that
 *       is a hard injection failure before the title screen. While the mod id {@code lithium} is
 *       loaded, that ONE mixin is withheld and explosion occlusion follows vanilla rules.</li>
 *   <li>Lithium replaces vanilla's block-collision iterator with its own sweepers, which never
 *       reach {@link BlockCollisionsLoweredAboveMixin}. {@link LithiumBlockCollisionSweeperShapeLoweredAboveMixin}
 *       and {@link LithiumBlockCollisionSweeperPosLoweredAboveMixin} target those sweepers and are
 *       admitted by {@link #sweeperAsksCollisionContext} ALONE: it reads each sweeper's own bytes
 *       and finds the redirected call. The mod id is not consulted for them — the redirects depend
 *       on that code and nothing else, and a jar shipping the same sweeper under another id
 *       deserves the same fix. Otherwise they are withheld; when the mod id {@code lithium} is
 *       loaded and the probe fails, a warning says so. A Lithium refactor degrades to Lithium's
 *       rules, it never crashes the game.</li>
 * </ul>
 *
 * <p>Invariants this plugin protects — do not trade them away:
 * <ul>
 *   <li>{@code require = 1} stays on every redirect. Tolerating a zero-match would turn a future
 *       rename into a silent loss of a fix behind a green suite; the byte check above is how a
 *       third party's rename is made loud without being fatal.</li>
 *   <li>The explosion skip is keyed on a mod id, legitimate ONLY because it concerns Slabbed's
 *       own patch at load time and bytes-present would not prove Lithium's module is enabled
 *       (its config can disable it), so stepping aside on the id is the conservative choice. The
 *       sweeper admission is keyed on Lithium's CODE, never its id: a behaviour added into another
 *       mod's path rests on that code being there, the same rule that gates every compat
 *       deferral on the other mod's data rather than its presence.</li>
 *   <li>Mixins and target classes are named as strings and the plugin loads no Minecraft, Mixin
 *       or Lithium class: it runs before those classes are transformed, and a class literal here
 *       would load them through the normal classloader. Bytes are read as resources only.</li>
 * </ul>
 */
public final class SlabbedMixinConfigPlugin implements IMixinConfigPlugin {

    static final String LITHIUM_MOD_ID = "lithium";
    static final String EXPLOSION_OCCLUSION_MIXIN =
            "com.slabbed.mixin.ServerExplosionOcclusionOffsetClipMixin";
    static final String LITHIUM_SWEEPER_SHAPE_MIXIN =
            "com.slabbed.mixin.LithiumBlockCollisionSweeperShapeLoweredAboveMixin";
    static final String LITHIUM_SWEEPER_POS_MIXIN =
            "com.slabbed.mixin.LithiumBlockCollisionSweeperPosLoweredAboveMixin";
    /** Lithium's two block-collision sweepers, as resource paths — never class literals. */
    static final String LITHIUM_SWEEPER_SHAPE_CLASS =
            "net/caffeinemc/mods/lithium/common/entity/movement/ChunkAwareBlockCollisionSweeperVoxelShape";
    static final String LITHIUM_SWEEPER_POS_CLASS =
            "net/caffeinemc/mods/lithium/common/entity/movement/ChunkAwareBlockCollisionSweeperBlockPos";
    /** The method the sweeper mixins inject into, and the call they redirect inside it. */
    static final String SWEEPER_ENTRY_METHOD = "computeNext";
    static final String COLLISION_CONTEXT_OWNER = "net/minecraft/world/phys/shapes/CollisionContext";
    static final String COLLISION_SHAPE_CALL = "getCollisionShape";

    private static final Logger LOGGER = LoggerFactory.getLogger("slabbed/mixin");

    private boolean lithiumPresent;
    /** Decided by the byte probe on every startup; without Lithium the resource miss is a no-op. */
    private boolean lithiumSweeperSupported;

    @Override
    public void onLoad(String mixinPackage) {
        this.lithiumPresent = FabricLoader.getInstance().isModLoaded(LITHIUM_MOD_ID);
        this.lithiumSweeperSupported = sweeperAsksCollisionContext(LITHIUM_SWEEPER_SHAPE_CLASS)
                && sweeperAsksCollisionContext(LITHIUM_SWEEPER_POS_CLASS);
        if (this.lithiumSweeperSupported) {
            LOGGER.info("Lithium's block-collision sweepers found: a lowered block's hanging collision is "
                    + "added to them{}", this.lithiumPresent
                    ? "; explosion occlusion follows vanilla rules while Lithium is present "
                            + "(ServerExplosionOcclusionOffsetClipMixin not applied; Lithium's explosion "
                            + "ray cast owns the same call site)"
                    : "");
        } else if (this.lithiumPresent) {
            LOGGER.warn("Lithium is present, but its block-collision sweeper is not the shape this build "
                    + "was verified against, so lowered-block collision follows Lithium's rules (the part "
                    + "of a lowered block hanging below its cell may not be solid). Explosion occlusion "
                    + "follows vanilla rules. Please report your Lithium version to Slabbed.");
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !withheld(mixinClassName, this.lithiumPresent, this.lithiumSweeperSupported);
    }

    /**
     * The whole decision as a pure function: the explosion mixin is withheld while the mod id
     * {@code lithium} is present; the two sweeper mixins are withheld unless the byte probe found
     * the redirected call in Lithium's sweepers (the mod id plays no part); everything else always
     * applies.
     */
    static boolean withheld(String mixinClassName, boolean lithiumPresent, boolean lithiumSweeperSupported) {
        if (EXPLOSION_OCCLUSION_MIXIN.equals(mixinClassName)) {
            return lithiumPresent;
        }
        if (LITHIUM_SWEEPER_SHAPE_MIXIN.equals(mixinClassName)
                || LITHIUM_SWEEPER_POS_MIXIN.equals(mixinClassName)) {
            return !lithiumSweeperSupported;
        }
        return false;
    }

    /**
     * Reads a class's ORIGINAL bytes as a resource (never loading it) and answers whether any
     * method named {@value #SWEEPER_ENTRY_METHOD} still invokes
     * {@code CollisionContext.getCollisionShape} — the exact instruction the sweeper mixins
     * redirect. Absent class, unreadable bytes, or no such call all answer {@code false}, so an
     * unknown Lithium build is withheld from, not crashed on.
     */
    public static boolean sweeperAsksCollisionContext(String internalName) {
        try (InputStream in = SlabbedMixinConfigPlugin.class.getClassLoader()
                .getResourceAsStream(internalName + ".class")) {
            if (in == null) {
                return false;
            }
            ClassNode node = new ClassNode();
            new ClassReader(in.readAllBytes()).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            for (MethodNode method : node.methods) {
                if (!SWEEPER_ENTRY_METHOD.equals(method.name)) {
                    continue;
                }
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn instanceof MethodInsnNode call
                            && COLLISION_CONTEXT_OWNER.equals(call.owner)
                            && COLLISION_SHAPE_CALL.equals(call.name)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Throwable t) {
            LOGGER.warn("Could not inspect {}: {}", internalName, t.toString());
            return false;
        }
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
