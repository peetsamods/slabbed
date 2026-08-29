package com.slabbed.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * Release-safe boundary for optional Slabbed diagnostics.
 *
 * <p>The public mod contains this no-op bridge but none of the recorder, overlay, or Sentinel
 * implementations. Development and GameTest runtimes install the real provider from a
 * development-only auxiliary component.
 */
public final class SlabbedDiagnosticsBridge {
    public static final String AUTO_USEON_PROXY = "AUTO_USEON_PROXY";

    @FunctionalInterface
    public interface ActionOriginScope extends AutoCloseable {
        @Override
        void close();
    }

    public interface PacketScope extends AutoCloseable {
        boolean claimed();

        @Override
        void close();
    }

    public interface Provider {
        default boolean enabled() {
            return false;
        }

        default PacketScope openUsePacketScope(
                String side,
                int sequence,
                String playerId,
                String dimensionId) {
            return null;
        }

        default void recordAction(LinkedHashMap<String, String> fields) {
        }

        default void recordCursor(LinkedHashMap<String, String> fields) {
        }

        default void recordRenderedOutline(LinkedHashMap<String, String> fields) {
        }

        default void recordBreakEvent(Level world, BlockPos pos, BlockState state, String playerName) {
        }

        default void armBreakNeighborhood(BlockGetter world, BlockPos pos, long nowTick) {
        }

        default void armPlacement(BlockGetter world, BlockPos pos, long nowTick) {
        }

        default boolean shouldCaptureModelBake() {
            return false;
        }

        default boolean isModelBakeArmed(long posKey) {
            return false;
        }

        default void recordModelBake(BlockPos pos, float bakedDy) {
        }

        default ActionOriginScope enterActionOrigin(String origin) {
            return null;
        }
    }

    private enum NoopProvider implements Provider {
        INSTANCE
    }

    private static volatile Provider provider = NoopProvider.INSTANCE;
    private static volatile boolean installed;

    private SlabbedDiagnosticsBridge() {
    }

    public static void install(Provider next) {
        provider = Objects.requireNonNull(next, "next");
        installed = true;
    }

    /** Cheap release-path gate before any optional provider dispatch. */
    public static boolean installed() {
        return installed;
    }

    public static boolean enabled() {
        try {
            return provider.enabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static PacketScope openUsePacketScope(
            String side,
            int sequence,
            String playerId,
            String dimensionId) {
        try {
            return provider.openUsePacketScope(side, sequence, playerId, dimensionId);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void recordAction(LinkedHashMap<String, String> fields) {
        try {
            provider.recordAction(fields);
        } catch (Throwable ignored) {
        }
    }

    public static void recordCursor(LinkedHashMap<String, String> fields) {
        try {
            provider.recordCursor(fields);
        } catch (Throwable ignored) {
        }
    }

    public static void recordRenderedOutline(LinkedHashMap<String, String> fields) {
        try {
            provider.recordRenderedOutline(fields);
        } catch (Throwable ignored) {
        }
    }

    public static void recordBreakEvent(Level world, BlockPos pos, BlockState state, String playerName) {
        try {
            provider.recordBreakEvent(world, pos, state, playerName);
        } catch (Throwable ignored) {
        }
    }

    public static void armBreakNeighborhood(BlockGetter world, BlockPos pos, long nowTick) {
        try {
            provider.armBreakNeighborhood(world, pos, nowTick);
        } catch (Throwable ignored) {
        }
    }

    public static void armPlacement(BlockGetter world, BlockPos pos, long nowTick) {
        try {
            provider.armPlacement(world, pos, nowTick);
        } catch (Throwable ignored) {
        }
    }

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
        }
    }

    /**
     * Runs the action exactly once under an optional best-effort diagnostic origin scope.
     * Diagnostic entry/close failures cannot suppress, repeat, or replace the action or its
     * exception.
     */
    public static void withActionOrigin(String origin, Runnable action) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(action, "action");
        ActionOriginScope scope = null;
        try {
            scope = provider.enterActionOrigin(origin);
        } catch (Throwable ignored) {
            // Diagnostics are observational. Their failure must not change the product action.
        }
        try {
            action.run();
        } finally {
            if (scope != null) {
                try {
                    scope.close();
                } catch (Throwable ignored) {
                    // Preserve the action's success or original exception exactly.
                }
            }
        }
    }
}
