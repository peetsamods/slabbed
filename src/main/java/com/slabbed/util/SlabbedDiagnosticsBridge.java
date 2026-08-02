package com.slabbed.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * Release-safe boundary for optional Slabbed diagnostics.
 *
 * <p>The core mod contains this no-op bridge but none of the recorder, overlay, command, or
 * Sentinel implementations. A separately packaged development-tools addon may install the real
 * provider at runtime. Core behavior must remain identical when no provider is installed or when
 * a provider fails: diagnostics observe product actions; they never own them.
 *
 * <p>This is an internal seam, not a public mod API.
 */
public final class SlabbedDiagnosticsBridge {
    public static final String PLAYER_AUTHORED = "PLAYER_AUTHORED";
    public static final String AUTO_USEON_PROXY = "AUTO_USEON_PROXY";
    public static final String GAMETEST = "GAMETEST";

    @FunctionalInterface
    public interface ActionOriginScope extends AutoCloseable {
        @Override
        void close();
    }

    /** Exact action identity that prevents a TEST proxy lease from relabelling other traffic. */
    public record ActionOriginContext(
            String playerUuid,
            String dimensionId,
            BlockPos placementPos) {
        public ActionOriginContext {
            Objects.requireNonNull(playerUuid, "playerUuid");
            Objects.requireNonNull(dimensionId, "dimensionId");
            placementPos = Objects.requireNonNull(placementPos, "placementPos").immutable();
        }
    }

    public interface Provider {
        default boolean available() {
            return false;
        }

        default boolean recorderEnabled() {
            return false;
        }

        default boolean toggleRecorder() {
            return false;
        }

        default boolean setRecorderEnabled(boolean value) {
            return false;
        }

        default String currentRecorderPath() {
            return "unavailable";
        }

        default void log(String tag, String body) {
        }

        default void noteTarget(
                BlockPos targetPos,
                BlockPos expectedPlacePos,
                Direction face,
                String half) {
        }

        default void checkPlacement(BlockPos actualPos, BlockState actualState) {
        }

        default void recordCursor(LinkedHashMap<String, String> fields) {
        }

        default void recordAction(LinkedHashMap<String, String> fields) {
        }

        default void recordRenderedOutline(LinkedHashMap<String, String> fields) {
        }

        default void recordSentinel(LinkedHashMap<String, String> fields) {
        }

        default void armPlacement(BlockGetter world, BlockPos placementPos, long nowTick) {
        }

        default boolean shouldCaptureModelBake() {
            return false;
        }

        default boolean isModelBakeArmed(long posKey) {
            return false;
        }

        default void recordModelBake(BlockPos pos, float bakedDy) {
        }

        default ActionOriginScope enterActionOrigin(
                String origin,
                ActionOriginContext context) {
            return null;
        }
    }

    private enum NoopProvider implements Provider {
        INSTANCE
    }

    private static volatile Provider provider = NoopProvider.INSTANCE;

    private SlabbedDiagnosticsBridge() {
    }

    /** Installs an optional provider and returns the previous one for narrowly scoped tests. */
    public static Provider install(Provider next) {
        Provider previous = provider;
        provider = Objects.requireNonNull(next, "next");
        return previous;
    }

    public static boolean isAvailable() {
        try {
            return provider.available();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isRecorderEnabled() {
        try {
            return provider.recorderEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean toggleRecorder() {
        try {
            return provider.toggleRecorder();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean setRecorderEnabled(boolean value) {
        try {
            return provider.setRecorderEnabled(value);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String currentRecorderPath() {
        try {
            return provider.currentRecorderPath();
        } catch (Throwable ignored) {
            return "unavailable";
        }
    }

    public static void log(String tag, String body) {
        try {
            provider.log(tag, body);
        } catch (Throwable ignored) {
            // Diagnostics are observational and may never change product behavior.
        }
    }

    public static void noteTarget(
            BlockPos targetPos,
            BlockPos expectedPlacePos,
            Direction face,
            String half) {
        try {
            provider.noteTarget(targetPos, expectedPlacePos, face, half);
        } catch (Throwable ignored) {
            // Diagnostics are observational and may never change product behavior.
        }
    }

    public static void checkPlacement(BlockPos actualPos, BlockState actualState) {
        try {
            provider.checkPlacement(actualPos, actualState);
        } catch (Throwable ignored) {
            // Diagnostics are observational and may never change product behavior.
        }
    }

    public static void recordCursor(LinkedHashMap<String, String> fields) {
        try {
            provider.recordCursor(fields);
        } catch (Throwable ignored) {
            // Diagnostics are observational and may never change product behavior.
        }
    }

    public static void recordAction(LinkedHashMap<String, String> fields) {
        try {
            provider.recordAction(fields);
        } catch (Throwable ignored) {
            // Diagnostics are observational and may never change product behavior.
        }
    }

    public static void recordRenderedOutline(LinkedHashMap<String, String> fields) {
        try {
            provider.recordRenderedOutline(fields);
        } catch (Throwable ignored) {
            // Diagnostics are observational and may never change product behavior.
        }
    }

    public static void recordSentinel(LinkedHashMap<String, String> fields) {
        try {
            provider.recordSentinel(fields);
        } catch (Throwable ignored) {
            // Diagnostics are observational and may never change product behavior.
        }
    }

    public static void armPlacement(BlockGetter world, BlockPos placementPos, long nowTick) {
        try {
            provider.armPlacement(world, placementPos, nowTick);
        } catch (Throwable ignored) {
            // Diagnostics are observational and may never change product behavior.
        }
    }

    /** Hot-path gate: callers must invoke this before any diagnostic allocation. */
    public static boolean shouldCaptureModelBake() {
        try {
            return provider.shouldCaptureModelBake();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isModelBakeArmed(long posKey) {
        try {
            return provider.isModelBakeArmed(posKey);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void recordModelBake(BlockPos pos, float bakedDy) {
        try {
            provider.recordModelBake(pos, bakedDy);
        } catch (Throwable ignored) {
            // Diagnostics are observational and may never change product behavior.
        }
    }

    /** Opens a target-bound origin scope that may span an asynchronous TEST proxy action. */
    public static ActionOriginScope openActionOrigin(
            String origin,
            ActionOriginContext context) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(context, "context");
        try {
            ActionOriginScope scope = provider.enterActionOrigin(origin, context);
            return scope == null ? () -> { } : scope;
        } catch (Throwable ignored) {
            return () -> { };
        }
    }

    /**
     * Runs one action under an optional diagnostic-origin scope without allowing diagnostics to
     * suppress, repeat, replace, or reclassify the action's own result.
     */
    public static void withActionOrigin(
            String origin,
            ActionOriginContext context,
            Runnable action) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(action, "action");
        ActionOriginScope scope = openActionOrigin(origin, context);
        try {
            action.run();
        } finally {
            try {
                scope.close();
            } catch (Throwable ignored) {
                // Preserve the product action's success or original exception.
            }
        }
    }
}
