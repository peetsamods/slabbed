package com.slabbed.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;

import com.slabbed.Slabbed;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Reflective probe for the dev-only {@code SlabbedRecorder} (Model A: absent-in-release).
 *
 * <p>The recorder class is EXCLUDED from the shipped jar (see the {@code jar} block in
 * build.gradle) and its {@code /slabdy record} command literal must not exist there —
 * not merely be inert. Shipping call sites therefore never reference
 * {@code SlabbedRecorder} directly; they call this bridge, which resolves the class
 * reflectively ONCE at class-init and only in a development environment
 * ({@code !FMLEnvironment.production}). When the class is absent or the environment is
 * production, {@link #isAvailable()} is a constant {@code false} and every other method
 * is a hardcoded no-op, so the disabled path costs nothing and can never crash play.
 *
 * <p>Probe failure in a dev environment logs ONE warn and degrades to no-op — a dev
 * tool must never take the game down (same law as the recorder's own I/O handling).
 */
public final class SlabbedRecorderBridge {
    private static final boolean AVAILABLE;
    private static final MethodHandle IS_ENABLED;
    private static final MethodHandle TOGGLE;
    private static final MethodHandle CURRENT_LOG_PATH;
    private static final MethodHandle LOG;
    private static final MethodHandle NOTE_TARGET;
    private static final MethodHandle CHECK_PLACEMENT;

    static {
        boolean available = false;
        MethodHandle isEnabled = null;
        MethodHandle toggle = null;
        MethodHandle currentLogPath = null;
        MethodHandle log = null;
        MethodHandle noteTarget = null;
        MethodHandle checkPlacement = null;
        if (!FMLEnvironment.production) {
            try {
                Class<?> recorder = Class.forName("com.slabbed.util.SlabbedRecorder");
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                isEnabled = lookup.findStatic(recorder, "isEnabled",
                        MethodType.methodType(boolean.class));
                toggle = lookup.findStatic(recorder, "toggle",
                        MethodType.methodType(boolean.class));
                currentLogPath = lookup.findStatic(recorder, "currentLogPath",
                        MethodType.methodType(Path.class));
                log = lookup.findStatic(recorder, "log",
                        MethodType.methodType(void.class, String.class, String.class));
                noteTarget = lookup.findStatic(recorder, "noteTarget",
                        MethodType.methodType(void.class, BlockPos.class, BlockPos.class,
                                Direction.class, String.class));
                checkPlacement = lookup.findStatic(recorder, "checkPlacement",
                        MethodType.methodType(void.class, BlockPos.class, BlockState.class));
                available = true;
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
                Slabbed.LOGGER.warn("Slabbed recorder unavailable in this environment: {}", e.toString());
            }
        }
        AVAILABLE = available;
        IS_ENABLED = isEnabled;
        TOGGLE = toggle;
        CURRENT_LOG_PATH = currentLogPath;
        LOG = log;
        NOTE_TARGET = noteTarget;
        CHECK_PLACEMENT = checkPlacement;
    }

    private SlabbedRecorderBridge() {
    }

    /** True only when the recorder class resolved in a development environment. */
    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static boolean isEnabled() {
        if (!AVAILABLE) {
            return false;
        }
        try {
            return (boolean) IS_ENABLED.invokeExact();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean toggle() {
        if (!AVAILABLE) {
            return false;
        }
        try {
            return (boolean) TOGGLE.invokeExact();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Display string of the recorder's current log path ("unavailable" when absent). */
    public static String currentLogPath() {
        if (!AVAILABLE) {
            return "unavailable";
        }
        try {
            Path path = (Path) CURRENT_LOG_PATH.invokeExact();
            return String.valueOf(path);
        } catch (Throwable t) {
            return "unavailable";
        }
    }

    public static void log(String tag, String body) {
        if (!AVAILABLE) {
            return;
        }
        try {
            LOG.invokeExact(tag, body);
        } catch (Throwable t) {
            // Dev tool only; never let logging failures affect gameplay.
        }
    }

    public static void noteTarget(BlockPos targetPos, BlockPos expectedPlacePos, Direction face, String half) {
        if (!AVAILABLE) {
            return;
        }
        try {
            NOTE_TARGET.invokeExact(targetPos, expectedPlacePos, face, half);
        } catch (Throwable t) {
            // Dev tool only; never let logging failures affect gameplay.
        }
    }

    public static void checkPlacement(BlockPos actualPos, BlockState actualState) {
        if (!AVAILABLE) {
            return;
        }
        try {
            CHECK_PLACEMENT.invokeExact(actualPos, actualState);
        } catch (Throwable t) {
            // Dev tool only; never let logging failures affect gameplay.
        }
    }
}
