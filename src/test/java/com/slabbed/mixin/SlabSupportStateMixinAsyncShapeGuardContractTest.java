package com.slabbed.mixin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class SlabSupportStateMixinAsyncShapeGuardContractTest {
    private static final Path MIXIN_SOURCE = Path.of(
            "src/main/java/com/slabbed/mixin/SlabSupportStateMixin.java");

    @Test
    void serverBackgroundShapeQueriesReturnBeforeOffsetResolution() throws IOException {
        String source = Files.readString(MIXIN_SOURCE);
        int outlineMethod = source.indexOf("private void slabbed$offsetOutline");
        int guard = source.indexOf("if (slabbed$isUnsafeAsyncShapeContext(world))", outlineMethod);
        int guardBodyEnd = source.indexOf('}', guard);
        int offsetResolution = source.indexOf("SlabSupport.getYOffset(world, pos, self)", outlineMethod);

        assertTrue(outlineMethod >= 0, "outline injection must remain present");
        assertTrue(guard > outlineMethod, "outline injection must check the async-shape guard");
        assertTrue(guardBodyEnd > guard
                        && source.substring(guard, guardBodyEnd).contains("return;"),
                "async-shape guard must return immediately when the context is unsafe");
        assertTrue(offsetResolution > guard,
                "async-shape guard must return before outline offset resolution can read chunks");
        assertTrue(source.contains("world instanceof ServerLevel serverLevel"),
                "server worlds must use thread ownership rather than worker names");
        assertTrue(source.contains("!serverLevel.getServer().isSameThread()"),
                "off-server-thread shape queries must be classified as unsafe");
        assertTrue(source.contains("name.startsWith(\"Worker-Main\")")
                        && source.contains("name.contains(\"ForkJoinPool\")"),
                "non-server worlds must retain the established named-worker fallback");
    }
}
