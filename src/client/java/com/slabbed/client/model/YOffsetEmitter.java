package com.slabbed.client.model;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.core.Direction;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Predicate;

/**
 * Dynamic proxy that shifts vertex Y in pos(...) and delegates all other methods.
 */
public final class YOffsetEmitter {
    private YOffsetEmitter() {
    }

    public static QuadEmitter wrap(QuadEmitter delegate, float dy) {
        return wrap(delegate, dy, direction -> false);
    }

    public static QuadEmitter wrap(QuadEmitter delegate, float dy, Predicate<Direction> clearCullFace) {
        return (QuadEmitter) Proxy.newProxyInstance(
                QuadEmitter.class.getClassLoader(),
                new Class<?>[]{QuadEmitter.class},
                (proxy, method, args) -> invoke(delegate, proxy, method, args, dy, clearCullFace)
        );
    }

    private static Object invoke(
            QuadEmitter delegate,
            Object proxy,
            Method method,
            Object[] args,
            float dy,
            Predicate<Direction> clearCullFace
    ) throws Throwable {
        if ("emit".equals(method.getName()) && (args == null || args.length == 0)) {
            Direction cullFace = delegate.cullFace();
            if (cullFace != null && clearCullFace.test(cullFace)) {
                delegate.cullFace(null);
                delegate.nominalFace(cullFace);
            }
            for (int i = 0; i < 4; i++) {
                float x = delegate.x(i);
                float y = delegate.y(i);
                float z = delegate.z(i);
                delegate.pos(i, x, y + dy, z);
            }
            return method.invoke(delegate, args);
        }

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

        if (result == delegate) {
            Class<?> returnType = method.getReturnType();
            if (returnType.isInterface() && returnType.isInstance(proxy)) {
                return proxy;
            }
        }

        return result;
    }
}
