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

    private SlabbedDiagnosticsBridge() {
    }

    public static void install(Provider next) {
        provider = Objects.requireNonNull(next, "next");
    }

    public static boolean enabled() {
        return provider.enabled();
    }

    public static PacketScope openUsePacketScope(
            String side,
            int sequence,
            String playerId,
            String dimensionId) {
        return provider.openUsePacketScope(side, sequence, playerId, dimensionId);
    }

    public static void recordAction(LinkedHashMap<String, String> fields) {
        provider.recordAction(fields);
    }

    public static void recordCursor(LinkedHashMap<String, String> fields) {
        provider.recordCursor(fields);
    }

    public static void recordRenderedOutline(LinkedHashMap<String, String> fields) {
        provider.recordRenderedOutline(fields);
    }

    public static void recordBreakEvent(Level world, BlockPos pos, BlockState state, String playerName) {
        provider.recordBreakEvent(world, pos, state, playerName);
    }

    public static void armBreakNeighborhood(BlockGetter world, BlockPos pos, long nowTick) {
        provider.armBreakNeighborhood(world, pos, nowTick);
    }

    public static void armPlacement(BlockGetter world, BlockPos pos, long nowTick) {
        provider.armPlacement(world, pos, nowTick);
    }

    public static boolean shouldCaptureModelBake() {
        return provider.shouldCaptureModelBake();
    }

    public static boolean isModelBakeArmed(long posKey) {
        return provider.isModelBakeArmed(posKey);
    }

    public static void recordModelBake(BlockPos pos, float bakedDy) {
        provider.recordModelBake(pos, bakedDy);
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
