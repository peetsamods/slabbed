package com.slabbed.devtools;

import com.slabbed.devtools.recording.SlabbedRecorder;
import com.slabbed.devtools.recording.SlabModelStaleSentinel;
import com.slabbed.util.SlabbedDiagnosticsBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Path;
import java.util.LinkedHashMap;

/** Installs the schema-6 recorder behind the release-safe diagnostics boundary. */
final class SlabbedRecorderProvider implements SlabbedDiagnosticsBridge.Provider {
    @Override
    public boolean available() {
        return true;
    }

    @Override
    public boolean recorderEnabled() {
        return SlabbedRecorder.isEnabled();
    }

    @Override
    public boolean toggleRecorder() {
        return SlabbedRecorder.toggle();
    }

    @Override
    public boolean setRecorderEnabled(boolean value) {
        return SlabbedRecorder.setEnabled(value);
    }

    @Override
    public String currentRecorderPath() {
        Path path = SlabbedRecorder.currentLogPath();
        return path == null ? "not-started" : path.toAbsolutePath().normalize().toString();
    }

    @Override
    public void log(String tag, String body) {
        SlabbedRecorder.log(tag, body);
    }

    @Override
    public void noteTarget(
            BlockPos targetPos,
            BlockPos expectedPlacePos,
            Direction face,
            String half) {
        SlabbedRecorder.noteTarget(targetPos, expectedPlacePos, face, half);
    }

    @Override
    public void checkPlacement(BlockPos actualPos, BlockState actualState) {
        SlabbedRecorder.checkPlacement(actualPos, actualState);
    }

    @Override
    public void recordCursor(LinkedHashMap<String, String> fields) {
        SlabbedRecorder.recordCursor(fields);
    }

    @Override
    public void recordAction(LinkedHashMap<String, String> fields) {
        SlabbedRecorder.recordAction(fields);
    }

    @Override
    public void recordRenderedOutline(LinkedHashMap<String, String> fields) {
        SlabbedRecorder.recordRenderedOutline(fields);
    }

    @Override
    public void recordSentinel(LinkedHashMap<String, String> fields) {
        SlabbedRecorder.recordSentinel(fields);
    }

    @Override
    public void armPlacement(BlockGetter world, BlockPos placementPos, long nowTick) {
        SlabModelStaleSentinel.armPlacement(world, placementPos, nowTick);
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
    public void recordModelBake(BlockPos pos, float bakedDy) {
        SlabModelStaleSentinel.recordBake(pos, bakedDy);
    }

    @Override
    public SlabbedDiagnosticsBridge.ActionOriginScope enterActionOrigin(
            String origin,
            SlabbedDiagnosticsBridge.ActionOriginContext context) {
        return SlabbedRecorder.enterActionOrigin(origin, context);
    }
}
