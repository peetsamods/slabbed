package com.slabbed.client.model;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Dynamic proxy that shifts vertex Y in pos(...) and delegates all other methods.
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
        // Intercept emit() to translate vertices right before emission (covers models that don't call pos())
        if ("emit".equals(method.getName()) && (args == null || args.length == 0)) {
            for (int i = 0; i < 4; i++) {
                float x = delegate.x(i);
                float y = delegate.y(i);
                float z = delegate.z(i);
                delegate.pos(i, x, y + dy, z);
            }
            return method.invoke(delegate, args);
        }

        // Intercept pos(int, float, float, float)
        if ("pos".equals(method.getName())
                && args != null
                && args.length == 4
                && args[0] instanceof Integer
                && args[1] instanceof Float
                && args[2] instanceof Float
                && args[3] instanceof Float) {
            float y = (Float) args[2];
            args[2] = y + dy;
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
