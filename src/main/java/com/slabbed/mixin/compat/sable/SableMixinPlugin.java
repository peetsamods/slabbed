package com.slabbed.mixin.compat.sable;

import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Admits the Sable physics-height mixins only when Sable's own bytes still have every member and
 * call they inject into. The decision reads Sable's class files as resources and never loads them;
 * it is keyed on Sable's code, never its mod id. A Sable refactor degrades to Sable's own colliders
 * (lowered blocks collide at their unlowered height, as before), never to a crash.
 */
public final class SableMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();

    static final String PIPELINE = "dev/ryanhcode/sable/physics/impl/rapier/RapierPhysicsPipeline";
    static final String BAKERY = "dev/ryanhcode/sable/physics/impl/rapier/collider/RapierVoxelColliderBakery";
    static final String SYSTEM = "dev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem";
    static final String NEIGHBORHOOD = "dev/ryanhcode/sable/physics/chunk/VoxelNeighborhoodState";
    static final String PIPELINE_API = "dev/ryanhcode/sable/api/physics/PhysicsPipeline";
    static final String RAPIER = "dev/ryanhcode/sable/physics/impl/rapier/Rapier3D";
    static final String COLLIDER_DATA = "dev/ryanhcode/sable/physics/impl/rapier/collider/RapierVoxelColliderData";
    static final String PROPERTIES = "dev/ryanhcode/sable/physics/config/block_properties/PhysicsBlockPropertyHelper";
    static final String CALLBACK_HOLDER = "dev/ryanhcode/sable/api/block/BlockWithSubLevelCollisionCallback";
    static final String SHAPE_API = "dev/ryanhcode/sable/api/block/BlockSubLevelCollisionShape";
    static final String STATE_TO_DOUBLE = "(Lnet/minecraft/world/level/block/state/BlockState;)D";

    static final String SECTION_ADDITION = "handleChunkSectionAddition";
    static final String SECTION_ADDITION_DESC = "(Lnet/minecraft/world/level/chunk/LevelChunkSection;IIIZ)V";
    static final String BLOCK_CHANGE = "handleBlockChange";
    static final String BLOCK_CHANGE_DESC = "(Lnet/minecraft/core/SectionPos;"
            + "Lnet/minecraft/world/level/chunk/LevelChunkSection;III"
            + "Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/world/level/block/state/BlockState;)V";

    static final String GET_STATE = "getState";
    static final String GET_STATE_DESC = "(Ldev/ryanhcode/sable/util/LevelAccelerator;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk;)"
            + "Ldev/ryanhcode/sable/physics/chunk/VoxelNeighborhoodState;";
    static final String GET_DATA = "getPhysicsDataForBlock";
    static final String GET_DATA_DESC = "(Lnet/minecraft/world/level/block/state/BlockState;)"
            + "Ldev/ryanhcode/sable/physics/impl/rapier/collider/RapierVoxelColliderData;";

    /** Injection targets, spelled once so the probe and the injections cannot drift apart. */
    public static final String GET_STATE_CALL = "L" + NEIGHBORHOOD + ";" + GET_STATE + GET_STATE_DESC;
    public static final String GET_DATA_CALL = "L" + BAKERY + ";" + GET_DATA + GET_DATA_DESC;
    public static final String SECTION_ADDITION_METHOD = SECTION_ADDITION + SECTION_ADDITION_DESC;
    public static final String BLOCK_CHANGE_METHOD = BLOCK_CHANGE + BLOCK_CHANGE_DESC;

    private boolean supported;

    @Override
    public void onLoad(String mixinPackage) {
        boolean present = resourceExists(PIPELINE);
        this.supported = present
                && methodCalls(PIPELINE, SECTION_ADDITION, SECTION_ADDITION_DESC)
                && methodCalls(PIPELINE, BLOCK_CHANGE, BLOCK_CHANGE_DESC)
                && hasField(PIPELINE, "level", "Lnet/minecraft/server/level/ServerLevel;")
                && hasField(BAKERY, null, null)
                && hasField(SYSTEM, "pipeline", "Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;")
                && hasField(SYSTEM, "level", "Lnet/minecraft/server/level/ServerLevel;")
                && hasMethod(SYSTEM, "wakeUpObjectsAt", "(III)V")
                && hasMethod(SYSTEM, "<init>", "(Lnet/minecraft/server/level/ServerLevel;)V")
                && hasField(SYSTEM, "IN_PHYSICS_STEP", "Z")
                && hasMethod(PIPELINE_API, BLOCK_CHANGE, BLOCK_CHANGE_DESC)
                && hasMethod(RAPIER, "createVoxelColliderEntry", "(DDDZ"
                        + "Ldev/ryanhcode/sable/api/physics/callback/BlockSubLevelCollisionCallback;)"
                        + "Ldev/ryanhcode/sable/physics/impl/rapier/collider/RapierVoxelColliderData;")
                && hasMethod(COLLIDER_DATA, "addBox", "(Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;)V")
                && hasMethod(PROPERTIES, "getFriction", STATE_TO_DOUBLE)
                && hasMethod(PROPERTIES, "getVolume", STATE_TO_DOUBLE)
                && hasMethod(PROPERTIES, "getRestitution", STATE_TO_DOUBLE)
                && hasMethod(CALLBACK_HOLDER, "sable$getCallback", "(Lnet/minecraft/world/level/block/state/BlockState;)"
                        + "Ldev/ryanhcode/sable/api/physics/callback/BlockSubLevelCollisionCallback;")
                && hasField(NEIGHBORHOOD, "EMPTY", "L" + NEIGHBORHOOD + ";")
                && hasField(NEIGHBORHOOD, "CORNER", "L" + NEIGHBORHOOD + ";")
                && hasField(NEIGHBORHOOD, "FACE", "L" + NEIGHBORHOOD + ";")
                && hasField(NEIGHBORHOOD, "EDGE", "L" + NEIGHBORHOOD + ";")
                && hasField(NEIGHBORHOOD, "INTERIOR", "L" + NEIGHBORHOOD + ";")
                && hasField(SHAPE_API, null, null);
        if (this.supported) {
            LOGGER.info("Sable found: physics objects collide with Slabbed-lowered blocks where they are drawn");
        } else if (present) {
            LOGGER.warn("Sable is present, but its physics collider code is not the shape this build was "
                    + "verified against, so physics objects collide with lowered blocks at their unlowered "
                    + "height. Please report your Sable version to Slabbed.");
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return this.supported;
    }

    private static boolean resourceExists(String internalName) {
        try (InputStream in = open(internalName)) {
            return in != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Whether the exact method still calls both the neighborhood classifier and the collider lookup. */
    private static boolean methodCalls(String owner, String name, String desc) {
        ClassNode node = read(owner);
        if (node == null) {
            return false;
        }
        for (MethodNode method : node.methods) {
            if (!name.equals(method.name) || !desc.equals(method.desc)) {
                continue;
            }
            boolean state = false;
            boolean data = false;
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode call) {
                    state |= NEIGHBORHOOD.equals(call.owner) && GET_STATE.equals(call.name)
                            && GET_STATE_DESC.equals(call.desc);
                    data |= BAKERY.equals(call.owner) && GET_DATA.equals(call.name)
                            && GET_DATA_DESC.equals(call.desc);
                }
            }
            return state && data;
        }
        return false;
    }

    private static boolean hasField(String owner, String name, String desc) {
        ClassNode node = read(owner);
        if (node == null) {
            return false;
        }
        if (name == null) {
            return true;
        }
        for (FieldNode field : node.fields) {
            if (name.equals(field.name) && desc.equals(field.desc)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMethod(String owner, String name, String desc) {
        ClassNode node = read(owner);
        if (node == null) {
            return false;
        }
        for (MethodNode method : node.methods) {
            if (name.equals(method.name) && desc.equals(method.desc)) {
                return true;
            }
        }
        return false;
    }

    private static ClassNode read(String internalName) {
        try (InputStream in = open(internalName)) {
            if (in == null) {
                return null;
            }
            ClassNode node = new ClassNode();
            new ClassReader(in.readAllBytes()).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        } catch (Throwable t) {
            LOGGER.warn("Could not inspect {}: {}", internalName, t.toString());
            return null;
        }
    }

    private static InputStream open(String internalName) {
        return SableMixinPlugin.class.getClassLoader().getResourceAsStream(internalName + ".class");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
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
