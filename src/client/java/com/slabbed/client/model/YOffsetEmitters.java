package com.slabbed.client.model;

import com.slabbed.compat.MinecraftVersions;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadTransform;

/**
 * Chooses the Y-offset emitter implementation for the running game version.
 *
 * <p>{@link YOffsetEmitter} implements the Fabric renderer API 17 shape of {@code QuadEmitter}
 * (26.3: {@code shadeDirectionOverride} and the item-glint render types). Fabric API 14 on 26.2 has
 * {@code diffuseShade} instead, so a wrapper compiled against one cannot satisfy the other and the
 * 26.2 variant is compiled separately against 26.2 (the {@code com.slabbed.compat.mc262} shim,
 * merged into this jar). The choice is made once; each wrap is then an exact-typed method-handle call.
 */
final class YOffsetEmitters {

    private static final String LEGACY_EMITTER = "com.slabbed.compat.mc262.YOffsetEmitter262";
    private static final MethodHandle WRAP_WITH_TRANSFORM = resolve();

    private YOffsetEmitters() {
    }

    static QuadEmitter wrapWithTransform(QuadEmitter delegate, float dy, QuadTransform clearCullTransform) {
        try {
            return (QuadEmitter) WRAP_WITH_TRANSFORM.invokeExact(delegate, dy, clearCullTransform);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    private static MethodHandle resolve() {
        MethodType type = MethodType.methodType(QuadEmitter.class, QuadEmitter.class, float.class, QuadTransform.class);
        try {
            Class<?> owner = MinecraftVersions.AT_LEAST_26_3
                    ? YOffsetEmitter.class
                    : Class.forName(LEGACY_EMITTER, true, YOffsetEmitters.class.getClassLoader());
            return MethodHandles.publicLookup().findStatic(owner, "wrapWithTransform", type);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("no Y-offset emitter for this Minecraft version", e);
        }
    }
}
