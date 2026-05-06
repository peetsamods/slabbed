package com.slabbed.client.debug;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.util.SlabSupport;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;

/**
 * TEMPORARY client-side companion to {@link BsFbLiveTrace}.
 *
 * <p>Captures the client visual triad (client world state, ClientDy, SlabSupport.getYOffset,
 * client-side anchor view, outline shape Y range) for a BS-FB assembly position so we can
 * compare against the server trace and pinpoint why the system visually lifts after a
 * supporting bottom slab is broken.
 *
 * <p>Default OFF. Activates only when {@code -Dslabbed.bsfb.live.trace=true}. No gameplay
 * or render behavior changes — logging only.
 */
public final class BsFbLiveTraceClient {
    private static final String TRACE_CLASS_NAME = "com.slabbed." + "debug.BsFbLiveTrace";

    private BsFbLiveTraceClient() {}

    /** Wired from {@code SlabbedClient.onInitializeClient()}. Idempotent. */
    public static void init() {
        if (!enabled()) return;
        try {
            Class<?> traceClass = Class.forName(TRACE_CLASS_NAME);
            Field hookField = traceClass.getField("clientCaptureHook");
            if (hookField.get(null) != null) return;
            Class<?> hookType = Class.forName(TRACE_CLASS_NAME + "$ClientCaptureHook");
            Object hook = Proxy.newProxyInstance(
                    hookType.getClassLoader(),
                    new Class<?>[]{hookType},
                    (proxy, method, args) -> {
                        if ("capture".equals(method.getName()) && args != null && args.length == 3) {
                            dispatchCapture((BlockPos) args[0], (BlockPos) args[1], (String) args[2]);
                        }
                        return null;
                    });
            hookField.set(null, hook);
            Slabbed.LOGGER.info("[BSFB-LIVE-TRACE] client capture hook installed");
        } catch (ReflectiveOperationException | IllegalArgumentException | LinkageError ignored) {
            // Debug trace class is dev-only and may be absent from release packaging.
        }
    }

    private static void dispatchCapture(BlockPos supportPos, BlockPos fullPos, String label) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        // Always run capture on the render thread to read ClientWorld state safely.
        mc.execute(() -> doCapture(mc, supportPos, fullPos, label));
    }

    private static void doCapture(MinecraftClient mc, BlockPos supportPos, BlockPos fullPos, String label) {
        ClientWorld world = mc.world;
        if (world == null) {
            Slabbed.LOGGER.info("[BSFB-LIVE-TRACE] CLIENT_VIEW {} tick={} clientWorld=null", label, getTick());
            return;
        }

        BlockState supportState = world.getBlockState(supportPos);
        BlockState fullState = world.getBlockState(fullPos);

        boolean anchored = SlabAnchorAttachment.isAnchored(world, fullPos);
        double clientDy = ClientDy.dyFor(world, fullPos, fullState);
        double slabDy = SlabSupport.getYOffset(world, fullPos, fullState);
        double slabDyAtSupport = SlabSupport.getYOffset(world, supportPos, supportState);

        String slabTypeAtSupport = "none";
        if (supportState.getBlock() instanceof SlabBlock && supportState.contains(SlabBlock.TYPE)) {
            slabTypeAtSupport = supportState.get(SlabBlock.TYPE).toString();
        }
        String slabTypeAtFull = "none";
        if (fullState.getBlock() instanceof SlabBlock && fullState.contains(SlabBlock.TYPE)) {
            slabTypeAtFull = fullState.get(SlabBlock.TYPE).toString();
        }

        // Outline shape Y range at fullPos. Reads only; uses absent ShapeContext to avoid
        // entity-dependent slab-edge differences leaking into the diagnostic.
        String outlineYRange;
        try {
            VoxelShape outline = fullState.getOutlineShape(world, fullPos, ShapeContext.absent());
            if (outline == null || outline.isEmpty()) {
                outlineYRange = "empty";
            } else {
                Box bounds = outline.getBoundingBox();
                outlineYRange = String.format("[%.3f,%.3f]", bounds.minY, bounds.maxY);
            }
        } catch (Throwable t) {
            outlineYRange = "error:" + t.getClass().getSimpleName();
        }

        StringBuilder sb = new StringBuilder(384);
        sb.append("[BSFB-LIVE-TRACE] CLIENT_VIEW ").append(label)
          .append(" tick=").append(getTick())
          .append(" side=CLIENT")
          .append(" | supportPos=").append(supportPos.toShortString())
          .append(" supportState=").append(supportState)
          .append(" | fullPos=").append(fullPos.toShortString())
          .append(" fullState=").append(fullState)
          .append(" | slabTypeAtSupport=").append(slabTypeAtSupport)
          .append(" | slabTypeAtFull=").append(slabTypeAtFull)
          .append(" | clientAnchored=").append(anchored)
          .append(" | clientDy=").append(clientDy)
          .append(" | slabSupportDyFull=").append(slabDy)
          .append(" | slabSupportDySupport=").append(slabDyAtSupport)
          .append(" | outlineYAtFull=").append(outlineYRange)
          .append(" | fullIsAir=").append(fullState.isAir());
        Slabbed.LOGGER.info(sb.toString());
    }

    private static boolean enabled() {
        return Boolean.getBoolean("slabbed.bsfb.live.trace");
    }

    private static long getTick() {
        if (!enabled()) return 0L;
        try {
            Class<?> traceClass = Class.forName(TRACE_CLASS_NAME);
            Method method = traceClass.getMethod("getTick");
            Object value = method.invoke(null);
            return value instanceof Number number ? number.longValue() : 0L;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return 0L;
        }
    }
}
