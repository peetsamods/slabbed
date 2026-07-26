package com.slabbed.dev;

import com.slabbed.util.LiveCursorIntentRecorder;
import com.slabbed.util.SlabModelStaleSentinel;
import com.slabbed.util.SlabbedDiagnosticsBridge;
import net.fabricmc.api.ModInitializer;

public final class SlabbedDiagnostics implements ModInitializer {
    @Override
    public void onInitialize() {
        SlabbedDiagnosticsBridge.install(new SlabbedDiagnosticsBridge.Provider() {
            @Override
            public boolean enabled() {
                return LiveCursorIntentRecorder.enabled();
            }

            @Override
            public SlabbedDiagnosticsBridge.PacketScope openUsePacketScope(
                    String side,
                    int sequence,
                    String playerId,
                    String dimensionId) {
                LiveCursorIntentRecorder.UsePacketScope scope =
                        LiveCursorIntentRecorder.openUsePacketScope(
                                side, sequence, playerId, dimensionId);
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
            }

            @Override
            public SlabbedDiagnosticsBridge.ActionOriginScope enterActionOrigin(String origin) {
                LiveCursorIntentRecorder.ActionOriginScope scope =
                        LiveCursorIntentRecorder.enterActionOrigin(
                                LiveCursorIntentRecorder.ActionOrigin.valueOf(origin));
                return scope::close;
            }
        });
    }
}
