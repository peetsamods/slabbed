package com.slabbed.client;

import com.slabbed.Slabbed;
import com.slabbed.util.RuntimeDiagnostics;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.InvocationTargetException;

public final class SlabbedClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RuntimeDiagnostics.logInspectSessionStart();
        SlabbedModelLoadingPlugin.init();
        SlabAnchorClientSync.init();
        RuntimeDiagnostics.initBsFbLiveTraceClient();
        initGapFillerOverlay();
        initTargetDyOverlay();
        initScreenshotCaptureService();
    }

    private static void initGapFillerOverlay() {
        if (!SlabbedClientFlags.GAP_FILL) {
            return;
        }
        invokeStaticInit(
                "com.slabbed.client.GapFillerOverlay",
                "gap filler overlay");
    }

    private static void initTargetDyOverlay() {
        TargetDyOverlay.init();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("slabdy")
                        .executes(SlabbedClient::slabdyToggle)
                        .then(ClientCommandManager.literal("row").executes(SlabbedClient::slabdyRow))
                        .then(ClientCommandManager.literal("use").executes(SlabbedClient::slabdyUse))
                        .then(ClientCommandManager.literal("record").executes(SlabbedClient::slabdyRecord))));
        // While the recorder is enabled, capture a full SlabbedDiagnostics sample every time the
        // crosshair target changes — deduped so look-drift doesn't spam the log. Reaches the dev-only
        // analysis layer release-safely through RuntimeDiagnostics; a release build (layer stripped)
        // no-ops. This closes the loop Phase 7 left open (recordVisualDiagnostic was dangling).
        ClientTickEvents.END_CLIENT_TICK.register(SlabbedClient::maybeRecordTargetDiagnostic);
    }

    private static int slabdyToggle(com.mojang.brigadier.context.CommandContext<
            net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> ctx) {
        boolean enabled = TargetDyOverlay.toggle();
        ctx.getSource().sendFeedback(Text.literal("[slabbed] target dy overlay " + (enabled ? "ON" : "OFF")));
        return 1;
    }

    private static int slabdyRow(com.mojang.brigadier.context.CommandContext<
            net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) {
            ctx.getSource().sendError(Text.literal("[slabdy row] no client world"));
            return 0;
        }
        BlockHitResult blockHit = SlabdyDiagnosticSupport.currentBlockTarget(client);
        if (blockHit == null) {
            ctx.getSource().sendError(Text.literal("[slabdy row] target is not a block"));
            return 0;
        }
        java.util.List<String> lines = SlabdyDiagnosticSupport.buildRow(client, blockHit, true);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append('\n');
        }
        ctx.getSource().sendFeedback(Text.literal(sb.toString().stripTrailing()));
        return 1;
    }

    private static int slabdyUse(com.mojang.brigadier.context.CommandContext<
            net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null || client.interactionManager == null) {
            ctx.getSource().sendError(Text.literal("[slabdy use] no client world"));
            return 0;
        }
        BlockHitResult blockHit = SlabdyDiagnosticSupport.currentBlockTarget(client);
        if (blockHit == null) {
            ctx.getSource().sendError(Text.literal("[slabdy use] target is not a block"));
            return 0;
        }
        BlockPos targetPos = blockHit.getBlockPos();
        BlockPos expectedPlacePos = targetPos.offset(blockHit.getSide());
        String before = blockId(client.world.getBlockState(expectedPlacePos));

        ActionResult result = client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, blockHit);

        String after = blockId(client.world.getBlockState(expectedPlacePos));
        ctx.getSource().sendFeedback(Text.literal("[slabdy use] target=" + targetPos.toShortString()
                + " face=" + blockHit.getSide()
                + " expectedPlace=" + expectedPlacePos.toShortString()
                + " before=" + before
                + " after=" + after
                + " result=" + result));
        return 1;
    }

    private static int slabdyRecord(com.mojang.brigadier.context.CommandContext<
            net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> ctx) {
        // Route through RuntimeDiagnostics (this branch's recorder bridge) rather than the sibling's
        // SlabbedAuditBridge recorder surface — Phase 7 wired the LiveCursorIntentRecorder there.
        boolean nowOn = RuntimeDiagnostics.toggleRecorder();
        ctx.getSource().sendFeedback(Text.literal("[slabbed] recorder: "
                + (nowOn ? "on -> " + RuntimeDiagnostics.recorderLogPathDisplay() : "off")));
        return 1;
    }

    private static String lastRecordedSignature = "";

    private static void maybeRecordTargetDiagnostic(MinecraftClient client) {
        if (!RuntimeDiagnostics.isRecorderEnabled()) {
            return;
        }
        if (client == null || client.world == null) {
            return;
        }
        BlockHitResult blockHit = SlabdyDiagnosticSupport.currentBlockTarget(client);
        if (blockHit == null) {
            if (!"none".equals(lastRecordedSignature)) {
                lastRecordedSignature = "none";
            }
            return;
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = client.world.getBlockState(pos);
        double modelDy = SlabdyDiagnosticSupport.modelDyFor(pos);
        String sig = pos.toShortString() + "|" + state + "|"
                + String.format(java.util.Locale.ROOT, "%.3f", modelDy);
        if (sig.equals(lastRecordedSignature)) {
            return;
        }
        lastRecordedSignature = sig;
        Object sample = RuntimeDiagnostics.analyzeVisualDiagnostic(client.world, pos, state, modelDy);
        RuntimeDiagnostics.recordVisualDiagnostic(pos, sample);
    }

    private static String blockId(BlockState state) {
        var id = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock());
        return id == null ? "?" : id.toString();
    }

    private static void initScreenshotCaptureService() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }
        invokeStaticInit(
                "com.slabbed.client.ScreenshotCaptureService",
                "screenshot capture service");
    }

    private static void invokeStaticInit(String className, String label) {
        try {
            Class<?> hookClass = Class.forName(className);
            hookClass.getMethod("init").invoke(null);
        } catch (ClassNotFoundException e) {
            Slabbed.LOGGER.warn("{} is unavailable in this environment", label);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | LinkageError e) {
            Slabbed.LOGGER.warn("Failed to initialize {}", label, e);
        }
    }
}
