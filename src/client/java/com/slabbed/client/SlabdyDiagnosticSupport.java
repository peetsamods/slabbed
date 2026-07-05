package com.slabbed.client;

import com.slabbed.client.model.OffsetBlockStateModel;
import com.slabbed.util.RuntimeDiagnostics;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Shared, always-shipped client-side glue behind {@code /slabdy}, {@code /slabdy row}, the passive
 * overlay, and the {@code /slabdy record} per-tick diagnostic capture. Holds the model-trace
 * arm/read cycle and the release-safe bridge into the dev-only {@code SlabbedDiagnostics} analysis
 * layer (via {@link RuntimeDiagnostics}, which resolves it reflectively — a release build where the
 * layer is stripped simply gets no flag line and records nothing).
 */
public final class SlabdyDiagnosticSupport {

    private SlabdyDiagnosticSupport() {
    }

    /**
     * Build the full {@link SlabdyRowFormatter} dump for the current block target. When
     * {@code armModelTrace} is true (the {@code /slabdy row} path), it also (re)arms the render-thread
     * model-dy trace for the next frame — mirroring the sibling's two-call arm/read cycle. The
     * passive overlay passes {@code false} (it reads whatever a prior frame captured, never arming).
     */
    public static List<String> buildRow(MinecraftClient client, BlockHitResult blockHit, boolean armModelTrace) {
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = client.world.getBlockState(pos);
        ItemStack held = client.player == null ? ItemStack.EMPTY : client.player.getMainHandStack();

        OffsetBlockStateModel.RenderOffsetSample trace = OffsetBlockStateModel.snapshotRenderOffsetSample();
        String modelTraceLine = formatModelTrace(pos, trace);
        BlockPos armedPos = null;
        if (armModelTrace && client.worldRenderer != null) {
            OffsetBlockStateModel.resetRenderOffsetSample(pos);
            client.worldRenderer.scheduleBlockRenders(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX(), pos.getY(), pos.getZ());
            armedPos = pos;
        }

        return SlabdyRowFormatter.formatRow(
                client.world, pos, state, blockHit.getSide(), blockHit.getPos(), held,
                modelTraceLine, armedPos);
    }

    /**
     * The render-thread model dy for {@code pos} if the trace happens to hold it, else NaN. This
     * branch only records the {@code RenderOffsetSample} for chains under
     * {@code -Dslabbed.render.offset.trace=true}, so this is NaN for most blocks — matching what the
     * formatter and the diagnostic sample honestly report.
     */
    public static double modelDyFor(BlockPos pos) {
        OffsetBlockStateModel.RenderOffsetSample trace = OffsetBlockStateModel.snapshotRenderOffsetSample();
        if (trace != null && trace.seen() && pos.toShortString().equals(trace.pos())) {
            return trace.modelDy();
        }
        return Double.NaN;
    }

    /**
     * A "[slabdy] FLAGS: ..." line if the dev-only {@code SlabbedDiagnostics} layer is present and the
     * current target trips any red flag; {@code null} otherwise (including in a release build, where
     * the layer is stripped). Reached reflectively through {@link RuntimeDiagnostics} so this
     * always-shipped class never links the stripped record type.
     */
    public static String suspectFlagLine(MinecraftClient client, BlockPos pos, BlockState state) {
        if (!RuntimeDiagnostics.isVisualDiagnosticsAvailable()) {
            return null;
        }
        Object sample = RuntimeDiagnostics.analyzeVisualDiagnostic(client.world, pos, state, modelDyFor(pos));
        if (sample == null) {
            return null;
        }
        String summary = RuntimeDiagnostics.visualDiagnosticFlagSummary(sample);
        return summary == null || summary.isEmpty() ? null : "[slabdy] FLAGS: " + summary;
    }

    /** The current block target, or null if the crosshair is not on a block. */
    public static BlockHitResult currentBlockTarget(MinecraftClient client) {
        HitResult target = client.crosshairTarget;
        if (!(target instanceof BlockHitResult blockHit) || target.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return blockHit;
    }

    private static String formatModelTrace(BlockPos pos, OffsetBlockStateModel.RenderOffsetSample trace) {
        if (trace == null || !trace.seen() || !pos.toShortString().equals(trace.pos())) {
            return "missing";
        }
        return "seen"
                + " view=" + trace.viewClass()
                + " modelDy=" + String.format(java.util.Locale.ROOT, "%.3f", trace.modelDy())
                + " clientDy=" + String.format(java.util.Locale.ROOT, "%.3f", trace.clientDy())
                + " slabSupportDy=" + String.format(java.util.Locale.ROOT, "%.3f", trace.slabSupportDy())
                + " excludedByWrapper=" + trace.excludedByWrapper();
    }
}
