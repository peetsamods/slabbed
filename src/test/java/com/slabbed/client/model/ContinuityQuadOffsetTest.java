package com.slabbed.client.model;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContinuityQuadOffsetTest {
    static final class Fixture {
        final Deque<RenderContext.QuadTransform> transforms = new ArrayDeque<>();
        final List<String> calls = new ArrayList<>();
        final float[][] coordinates = {{0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 1, 0}};
        final QuadEmitter emitter = (QuadEmitter) Proxy.newProxyInstance(QuadEmitter.class.getClassLoader(),
                new Class<?>[]{QuadEmitter.class}, (p, m, a) -> null);
        final RenderContext context = (RenderContext) Proxy.newProxyInstance(RenderContext.class.getClassLoader(),
                new Class<?>[]{RenderContext.class}, (p, m, a) -> {
                    return switch (m.getName()) {
                        case "pushTransform" -> { transforms.push((RenderContext.QuadTransform) a[0]); yield null; }
                        case "popTransform" -> { transforms.pop(); yield null; }
                        case "getEmitter" -> emitter;
                        default -> throw new AssertionError("Unexpected context operation: " + m.getName());
                    };
                });
        final MutableQuadView quad = (MutableQuadView) Proxy.newProxyInstance(MutableQuadView.class.getClassLoader(),
                new Class<?>[]{MutableQuadView.class}, (p, m, a) -> {
                    calls.add(m.getName());
                    return switch (m.getName()) {
                        case "x" -> coordinates[(Integer) a[0]][0];
                        case "y" -> coordinates[(Integer) a[0]][1];
                        case "z" -> coordinates[(Integer) a[0]][2];
                        case "pos" -> { coordinates[(Integer) a[0]] = new float[]{(Float) a[1], (Float) a[2], (Float) a[3]}; yield p; }
                        default -> throw new AssertionError("Material, UV, normal and emitter operations must remain untouched");
                    };
                });
        void emit() {
            assertSame(emitter, context.getEmitter());
            for (var transform : transforms) assertTrue(transform.transform(quad));
        }
    }

    @Test void shiftsExactlyOnceAndPreservesOriginalEmitterAndNonPositionAttributes() {
        var f = new Fixture();
        ContinuityQuadOffset.emit(f.context, -.5f, () ->
                ContinuityQuadOffset.emit(f.context, -.5f, f::emit));
        assertEquals(-.5f, f.coordinates[0][1]);
        assertEquals(.5f, f.coordinates[2][1]);
        assertEquals(1f, f.coordinates[2][0]);
        assertEquals(0f, f.coordinates[2][2]);
        assertEquals(4, f.calls.stream().filter("pos"::equals).count());
        assertTrue(f.transforms.isEmpty());
    }

    @Test void flatEmissionIsUnchangedAndDifferentContextsHaveTheirOwnOwner() {
        var flat = new Fixture();
        ContinuityQuadOffset.emit(flat.context, 0, flat::emit);
        assertTrue(flat.calls.isEmpty());
        var other = new Fixture();
        ContinuityQuadOffset.emit(flat.context, -.5f, () ->
                ContinuityQuadOffset.emit(other.context, -.5f, other::emit));
        assertEquals(-.5f, other.coordinates[0][1]);
        assertTrue(flat.transforms.isEmpty());
        assertTrue(other.transforms.isEmpty());
    }

    @Test void balancedFailurePropagatesAndDoesNotPoisonTheNextEmission() {
        var f = new Fixture();
        var expected = new IllegalStateException("delegate failed");
        assertSame(expected, assertThrows(IllegalStateException.class,
                () -> ContinuityQuadOffset.emit(f.context, -.5f, () -> { throw expected; })));
        assertTrue(f.transforms.isEmpty());
        ContinuityQuadOffset.emit(f.context, -.5f, f::emit);
        assertEquals(-.5f, f.coordinates[0][1]);
        assertTrue(f.transforms.isEmpty());
    }
}
