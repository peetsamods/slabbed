package com.slabbed.util;

import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.lang.reflect.Method;

/**
 * Reflection bridge for optional debug-only tracing classes that are excluded
 * from release artifacts.
 */
public final class SlabbedDebugBridge {
    private static final String INSPECT_CLASS = "com.slabbed.debug.SlabbedInspect";
    private static final String BSFB_CLASS = "com.slabbed.debug.BsFbLiveTrace";
    private static final String BSFB_CLIENT_CLASS = "com.slabbed.client.debug.BsFbLiveTraceClient";

    private static final boolean INSPECT_ENABLED = Boolean.getBoolean("slabbed.inspect")
            || Boolean.getBoolean("slabbed.b2.live.trace");
    private static final boolean BSFB_ENABLED = Boolean.getBoolean("slabbed.bsfb.live.trace");

    private SlabbedDebugBridge() {
    }

    public static boolean inspectEnabled() {
        return INSPECT_ENABLED;
    }

    public static boolean bsfbEnabled() {
        return BSFB_ENABLED;
    }

    public static void logSessionStart() {
        if (!INSPECT_ENABLED) {
            return;
        }
        invokeStatic(INSPECT_CLASS, "logSessionStart", new Class<?>[0]);
    }

    public static void logClientTarget(
            World world,
            Vec3d eye,
            Vec3d rayEnd,
            float yaw,
            float pitch,
            ItemStack held,
            HitResult initialTarget,
            HitResult finalTarget,
            String decision,
            boolean sideSlabRetargetFired
    ) {
        if (!INSPECT_ENABLED) {
            return;
        }
        invokeStatic(
                INSPECT_CLASS,
                "logClientTarget",
                new Class<?>[]{
                        World.class, Vec3d.class, Vec3d.class,
                        float.class, float.class,
                        ItemStack.class, HitResult.class, HitResult.class,
                        String.class, boolean.class
                },
                world, eye, rayEnd, yaw, pitch, held, initialTarget, finalTarget, decision, sideSlabRetargetFired
        );
    }

    public static void logIntent(ItemUsageContext incoming, ItemUsageContext outgoing, String reason) {
        if (!INSPECT_ENABLED) {
            return;
        }
        invokeStatic(
                INSPECT_CLASS,
                "logIntent",
                new Class<?>[]{ItemUsageContext.class, ItemUsageContext.class, String.class},
                incoming, outgoing, reason
        );
    }

    public static void logPlacement(
            String stage,
            World world,
            Identifier itemId,
            ItemPlacementContext ctx,
            BlockPos hitPos,
            BlockPos placePos,
            ActionResult result
    ) {
        if (!INSPECT_ENABLED) {
            return;
        }
        invokeStatic(
                INSPECT_CLASS,
                "logPlacement",
                new Class<?>[]{
                        String.class, World.class, Identifier.class,
                        ItemPlacementContext.class, BlockPos.class, BlockPos.class,
                        ActionResult.class
                },
                stage, world, itemId, ctx, hitPos, placePos, result
        );
    }

    public static void logPlacementNoReturn(
            World world,
            Identifier itemId,
            Direction face,
            BlockPos hitPos,
            BlockPos placePos
    ) {
        if (!INSPECT_ENABLED) {
            return;
        }
        invokeStatic(
                INSPECT_CLASS,
                "logPlacementNoReturn",
                new Class<?>[]{World.class, Identifier.class, Direction.class, BlockPos.class, BlockPos.class},
                world, itemId, face, hitPos, placePos
        );
    }

    public static void captureBsFb(World world, BlockPos supportPos, BlockPos fullPos, String label) {
        if (!BSFB_ENABLED) {
            return;
        }
        invokeStatic(
                BSFB_CLASS,
                "capture",
                new Class<?>[]{World.class, BlockPos.class, BlockPos.class, String.class},
                world, supportPos, fullPos, label
        );
    }

    public static void initBsFbLiveTraceClient() {
        if (!BSFB_ENABLED) {
            return;
        }
        invokeStatic(BSFB_CLIENT_CLASS, "init", new Class<?>[0]);
    }

    private static void invokeStatic(String className, String methodName, Class<?>[] paramTypes, Object... args) {
        try {
            Class<?> cls = Class.forName(className);
            Method method = cls.getMethod(methodName, paramTypes);
            method.invoke(null, args);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Optional debug class may be excluded from release artifacts.
        }
    }
}
