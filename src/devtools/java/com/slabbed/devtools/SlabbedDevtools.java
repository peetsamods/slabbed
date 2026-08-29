package com.slabbed.devtools;

import com.slabbed.devtools.client.SlabbedDevtoolsClient;
import com.slabbed.devtools.command.SlabRigCommand;
import com.slabbed.devtools.util.LiveCursorIntentRecorder;
import com.slabbed.devtools.util.SlabModelStaleSentinel;
import com.slabbed.util.SlabbedDiagnosticsBridge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Dev-only diagnostics mod: installs the real bridge provider so the shipping-side seams feed
 * the live recorder and stale-model sentinel, and registers the dev commands. This mod loads
 * only in dev run configurations; it is never part of a shipped archive, and the release
 * hygiene gate proves the shipping jar cannot even name these classes.
 */
@Mod(SlabbedDevtools.MOD_ID)
public final class SlabbedDevtools {
    public static final String MOD_ID = "slabbed_devtools";

    public SlabbedDevtools(IEventBus modBus) {
        SlabbedDiagnosticsBridge.install(new BridgeProvider());
        NeoForge.EVENT_BUS.addListener(SlabbedDevtools::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(SlabbedDevtools::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(SlabbedDevtools::onServerStopped);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            SlabbedDevtoolsClient.init(modBus);
        }
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        SlabRigCommand.register(event.getDispatcher());
    }

    /** Observation only: the recorder never cancels or alters the break. */
    private static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!LiveCursorIntentRecorder.enabled()) {
            return;
        }
        if (event.getLevel() instanceof net.minecraft.world.level.Level level) {
            SlabModelStaleSentinel.armBreakNeighborhood(
                    level,
                    event.getPos(),
                    level.getGameTime());
            LiveCursorIntentRecorder.recordBreakEvent(
                    level,
                    event.getPos(),
                    event.getState(),
                    event.getPlayer() == null ? "none" : event.getPlayer().getGameProfile().getName());
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        SlabRigCommand.onServerStopped(event.getServer());
    }

    private static final class BridgeProvider implements SlabbedDiagnosticsBridge.Provider {
        @Override
        public boolean enabled() {
            return LiveCursorIntentRecorder.enabled();
        }

        @Override
        public SlabbedDiagnosticsBridge.PacketScope openUsePacketScope(
                String side, int sequence, String playerId, String dimensionId) {
            LiveCursorIntentRecorder.UsePacketScope scope =
                    LiveCursorIntentRecorder.openUsePacketScope(side, sequence, playerId, dimensionId);
            return new SlabbedDiagnosticsBridge.PacketScope() {
                @Override
                public boolean claimed() {
                    return scope.claimed();
                }

                @Override
                public void close() {
                    scope.close();
                }
            };
        }

        @Override
        public void recordAction(java.util.LinkedHashMap<String, String> fields) {
            LiveCursorIntentRecorder.recordAction(fields);
        }

        @Override
        public void recordCursor(java.util.LinkedHashMap<String, String> fields) {
            LiveCursorIntentRecorder.recordCursor(fields);
        }

        @Override
        public void recordRenderedOutline(java.util.LinkedHashMap<String, String> fields) {
            LiveCursorIntentRecorder.recordRenderedOutline(fields);
        }

        @Override
        public void recordBreakEvent(
                net.minecraft.world.level.Level world,
                net.minecraft.core.BlockPos pos,
                net.minecraft.world.level.block.state.BlockState state,
                String playerName) {
            LiveCursorIntentRecorder.recordBreakEvent(world, pos, state, playerName);
        }

        @Override
        public void armBreakNeighborhood(
                net.minecraft.world.level.BlockGetter world,
                net.minecraft.core.BlockPos pos,
                long nowTick) {
            SlabModelStaleSentinel.armBreakNeighborhood(world, pos, nowTick);
        }

        @Override
        public void armPlacement(
                net.minecraft.world.level.BlockGetter world,
                net.minecraft.core.BlockPos pos,
                long nowTick) {
            SlabModelStaleSentinel.armPlacement(world, pos, nowTick);
        }

        @Override
        public boolean shouldCaptureModelBake() {
            return SlabModelStaleSentinel.shouldCapture();
        }

        @Override
        public boolean isModelBakeArmed(long posKey) {
            return SlabModelStaleSentinel.isArmed(posKey);
        }

        @Override
        public void recordModelBake(net.minecraft.core.BlockPos pos, float bakedDy) {
            SlabModelStaleSentinel.recordBake(pos, bakedDy);
            LiveCursorIntentRecorder.recordModelObservation(pos, bakedDy);
        }

        @Override
        public SlabbedDiagnosticsBridge.ActionOriginScope enterActionOrigin(String origin) {
            LiveCursorIntentRecorder.ActionOriginScope scope =
                    LiveCursorIntentRecorder.enterActionOrigin(
                            LiveCursorIntentRecorder.ActionOrigin.valueOf(origin));
            return scope::close;
        }
    }
}
