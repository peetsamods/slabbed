package com.slabbed.client.runtime;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.World;

public final class ModelDyTranslateTraceBridge {
    private static volatile BlockPos slabbed$tracePos = null;
    private static volatile Trace slabbed$lastTrace = Trace.missing();
    private static volatile ClientWorld slabbed$beta4RecorderWorld = null;
    private static volatile boolean slabbed$beta4RecorderStartLogged = false;
    private static volatile long slabbed$beta4RecorderLastTick = Long.MIN_VALUE;
    private static volatile int slabbed$beta4RecorderTicksRemaining = 0;
    private static volatile String slabbed$beta4RecorderWatchRaw = null;
    private static volatile BlockPos[] slabbed$beta4RecorderWatch = new BlockPos[0];

    private ModelDyTranslateTraceBridge() {
    }

    public record Trace(
            boolean seen,
            String viewClass,
            String pos,
            String state,
            String lastMethod,
            int observedCalls,
            int appliedCalls,
            double totalAppliedDy,
            double lastDy
    ) {
        static Trace missing() {
            return new Trace(false, "none", "none", "none", "none", 0, 0, 0.0, 0.0);
        }
    }

    public static void reset(BlockPos pos) {
        slabbed$tracePos = pos == null ? null : pos.toImmutable();
        slabbed$lastTrace = Trace.missing();
    }

    public static Trace snapshot() {
        return slabbed$lastTrace;
    }

    public static void record(String method, BlockRenderView world, BlockPos pos, BlockState state, double dy) {
        recordBeta4ModelDy(method, world, pos, state, dy);
        BlockPos target = slabbed$tracePos;
        if (target == null || !target.equals(pos)) {
            return;
        }
        Trace prev = slabbed$lastTrace;
        boolean applied = dy != 0.0d;
        slabbed$lastTrace = new Trace(
                true,
                world.getClass().getName(),
                pos.toShortString(),
                state.toString(),
                method,
                prev.observedCalls() + 1,
                prev.appliedCalls() + (applied ? 1 : 0),
                prev.totalAppliedDy() + (applied ? dy : 0.0d),
                dy);
    }

    public static void recordBeta4ModelDy(
            String method,
            BlockRenderView view,
            BlockPos pos,
            BlockState state,
            double modelDy
    ) {
        if (!Boolean.getBoolean("slabbed.beta4ModelDyRecorder")) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld clientWorld = mc == null ? null : mc.world;
        if (clientWorld == null || !slabbed$beta4RecorderTick(clientWorld)) {
            return;
        }
        int watchIndex = slabbed$watchIndex(pos);
        if (watchIndex < 0) {
            return;
        }

        double viewClientDy = ClientDy.dyFor(view, pos, state);
        double viewSlabSupportDy = SlabSupport.getYOffset(view, pos, state);
        boolean viewIsWorld = view instanceof World;
        boolean viewAnchored = SlabAnchorAttachment.isAnchored(view, pos);
        boolean viewPersistentCarrier = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(view, pos, state);
        boolean viewBottomCarrier = SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive(view, pos, state);

        BlockState clientState = clientWorld.getBlockState(pos);
        double clientDy = ClientDy.dyFor(clientWorld, pos, clientState);
        double clientSlabSupportDy = SlabSupport.getYOffset(clientWorld, pos, clientState);
        boolean anchored = SlabAnchorAttachment.isAnchored(clientWorld, pos);
        boolean persistentCarrier = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(clientWorld, pos, clientState);
        boolean bottomCarrier = SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive(clientWorld, pos, clientState);
        boolean lowered = slabbed$isLowered(clientDy);
        boolean modelDyLowered = slabbed$isLowered(modelDy);
        boolean modelDyZeroClientTruthLowered = modelDy == 0.0d && lowered;
        boolean slab = clientState.getBlock() instanceof SlabBlock;
        String slabType = clientState.contains(SlabBlock.TYPE) ? clientState.get(SlabBlock.TYPE).asString() : "none";
        String sourceMode = persistentCarrier
                ? "persistentLoweredSlabCarrier"
                : (lowered ? "dynamicLoweredOrAnchored" : "normal");

        Slabbed.LOGGER.info(
                "[BETA4_MODEL_DY_RECORDER] tick={} ticksRemaining={} watch={} method={} pos={} state={} viewClass={} viewIsWorld={} worldKey={} modelDy={} viewClientDy={} viewSlabSupportDy={} viewAnchored={} viewPersistentLoweredSlabCarrier={} viewPersistentLoweredBottomSlabCarrier={} clientState={} clientDy={} clientSlabSupportDy={} anchored={} persistentFullBlockAnchor={} persistentLoweredSlabCarrier={} persistentLoweredBottomSlabCarrier={} lowered={} slabType={} sourceMode={} nonWorldBridgeAnchor={} nonWorldBridgeCarrier={} nonWorldBridgeBottomCarrier={} modelDyZeroClientTruthLowered={} modelDyLowered={}",
                clientWorld.getTime(),
                slabbed$beta4RecorderTicksRemaining,
                watchIndex,
                method,
                pos.toShortString(),
                state,
                view.getClass().getName(),
                viewIsWorld,
                slabbed$worldKey(view, clientWorld),
                slabbed$formatDouble(modelDy),
                slabbed$formatDouble(viewClientDy),
                slabbed$formatDouble(viewSlabSupportDy),
                viewAnchored,
                viewPersistentCarrier,
                viewBottomCarrier,
                clientState,
                slabbed$formatDouble(clientDy),
                slabbed$formatDouble(clientSlabSupportDy),
                anchored,
                anchored && !slab,
                persistentCarrier,
                bottomCarrier,
                lowered,
                slabType,
                sourceMode,
                !viewIsWorld && anchored,
                !viewIsWorld && persistentCarrier,
                !viewIsWorld && bottomCarrier,
                modelDyZeroClientTruthLowered,
                modelDyLowered);
    }

    private static boolean slabbed$beta4RecorderTick(ClientWorld world) {
        if (world != slabbed$beta4RecorderWorld) {
            slabbed$beta4RecorderWorld = world;
            slabbed$beta4RecorderStartLogged = false;
            slabbed$beta4RecorderLastTick = Long.MIN_VALUE;
            slabbed$beta4RecorderTicksRemaining = slabbed$intProperty("slabbed.beta4ModelDyRecorderTicks", 700);
        }
        if (!slabbed$beta4RecorderStartLogged) {
            slabbed$beta4RecorderStartLogged = true;
            Slabbed.LOGGER.info(
                    "[BETA4_MODEL_DY_RECORDER_START] enabled=true ticks={} world={} watch={}",
                    slabbed$beta4RecorderTicksRemaining,
                    world.getRegistryKey().getValue(),
                    System.getProperty("slabbed.beta4ModelDyRecorderWatch", ""));
        }
        long tick = world.getTime();
        if (tick != slabbed$beta4RecorderLastTick) {
            slabbed$beta4RecorderLastTick = tick;
            slabbed$beta4RecorderTicksRemaining--;
        }
        return slabbed$beta4RecorderTicksRemaining >= 0;
    }

    private static int slabbed$watchIndex(BlockPos pos) {
        BlockPos[] watch = slabbed$watchPositions();
        for (int i = 0; i < watch.length; i++) {
            if (watch[i].equals(pos)) {
                return i;
            }
        }
        return -1;
    }

    private static BlockPos[] slabbed$watchPositions() {
        String raw = System.getProperty("slabbed.beta4ModelDyRecorderWatch", "");
        if (raw.equals(slabbed$beta4RecorderWatchRaw)) {
            return slabbed$beta4RecorderWatch;
        }
        String[] entries = raw.split(";");
        BlockPos[] parsed = new BlockPos[entries.length];
        int count = 0;
        for (String entry : entries) {
            BlockPos pos = slabbed$parseBlockPos(entry);
            if (pos != null) {
                parsed[count++] = pos;
            }
        }
        BlockPos[] compact = new BlockPos[count];
        System.arraycopy(parsed, 0, compact, 0, count);
        slabbed$beta4RecorderWatchRaw = raw;
        slabbed$beta4RecorderWatch = compact;
        return compact;
    }

    private static BlockPos slabbed$parseBlockPos(String raw) {
        String[] parts = raw.trim().split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new BlockPos(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int slabbed$intProperty(String name, int fallback) {
        String raw = System.getProperty(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean slabbed$isLowered(double dy) {
        return Math.abs(dy + 0.5d) <= 1.0e-6;
    }

    private static String slabbed$formatDouble(double value) {
        return String.format("%.6f", value);
    }

    private static String slabbed$worldKey(BlockRenderView view, ClientWorld clientWorld) {
        if (view instanceof World world) {
            return world.getRegistryKey().getValue().toString();
        }
        return clientWorld.getRegistryKey().getValue().toString();
    }
}
