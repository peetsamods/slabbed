package com.slabbed.client.model;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Dynamic proxy that shifts vertex Y once at emit() time and delegates all other methods.
 * Avoids brittle hand-written delegation against evolving FRAPI APIs.
 */
public final class YOffsetEmitter {
    private YOffsetEmitter() {
    }

    public static QuadEmitter wrap(QuadEmitter delegate, float dy) {
        return (QuadEmitter) Proxy.newProxyInstance(
                QuadEmitter.class.getClassLoader(),
                new Class<?>[]{QuadEmitter.class},
                (proxy, method, args) -> invoke(delegate, proxy, method, args, dy)
        );
    }

    private static Object invoke(QuadEmitter delegate, Object proxy, Method method, Object[] args, float dy) throws Throwable {
        // Translate all 4 vertices by dy exactly ONCE, right before emission — emit() reads the
        // final vertex positions, so this covers EVERY model regardless of how it set them
        // (fromVanilla / copyFrom / per-vertex pos()).
        //
        // We intentionally do NOT also shift inside pos(): doing BOTH double-shifted any model that
        // sets vertices via per-vertex pos() — cross / tinted plant models (short grass, fern, tall
        // grass) — pushing them to dy*2 (= -1.0 instead of -0.5) so the whole tuft sank into the
        // block below and rendered INVISIBLE on Terrain Slabs. Full-cube models emit via fromVanilla
        // and never hit the old pos() path, so they were single-shifted and correct — which is why
        // the compound stack rendered right while vegetation vanished.
        if ("emit".equals(method.getName()) && (args == null || args.length == 0)) {
            for (int i = 0; i < 4; i++) {
                float x = delegate.x(i);
                float y = delegate.y(i);
                float z = delegate.z(i);
                delegate.pos(i, x, y + dy, z);
            }
            return method.invoke(delegate, args);
        }

        Object result = method.invoke(delegate, args);

        // Preserve fluent chaining: if delegate returns itself, return proxy instead
        if (result == delegate) {
            Class<?> rt = method.getReturnType();
            if (rt.isInterface() && rt.isInstance(proxy)) {
                return proxy;
            }
        }

        return result;
    }
}
