package com.slabbed.client.model;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;

/** Applies one translation to an admitted emission without replacing its context or emitter. */
public final class ContinuityQuadOffset {
    private static final ThreadLocal<Set<RenderContext>> OWNERS =
            ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));

    private ContinuityQuadOffset() {
    }

    public static void emit(RenderContext context, float dy, Runnable emission) {
        if (dy == 0.0f) {
            emission.run();
            return;
        }
        Set<RenderContext> owners = OWNERS.get();
        if (!owners.add(context)) {
            emission.run();
            return;
        }
        boolean pushed = false;
        try {
            context.pushTransform(quad -> {
                for (int vertex = 0; vertex < 4; vertex++) {
                    quad.pos(vertex, quad.x(vertex), quad.y(vertex) + dy, quad.z(vertex));
                }
                return true;
            });
            pushed = true;
            emission.run();
        } finally {
            try {
                if (pushed) {
                    context.popTransform();
                }
            } finally {
                owners.remove(context);
            }
        }
    }
}
