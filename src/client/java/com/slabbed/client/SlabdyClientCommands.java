package com.slabbed.client;

import com.mojang.brigadier.context.CommandContext;
import com.slabbed.client.model.OffsetBlockStateModel;
import com.slabbed.dev.SlabdyRowFormatter;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * {@code /slabdy} — re-derived from Forge 1.20.1's {@code TargetDyOverlay} (the branch
 * Maintainer pointed at as "the slabdy debug info that is helpful"), not ported: Forge's
 * event bus / GuiGraphics / KeyMapping.click() have no Fabric equivalent, so this is a
 * fresh implementation of the same behavior using Fabric's client-command and
 * HudRenderCallback APIs.
 *
 * <p>Always present in every build (see build.gradle — com/slabbed/dev/** and this class
 * are never excluded). No ambient cost when unused: the HUD overlay only draws when
 * toggled on by typing the bare {@code /slabdy}, and {@code row}/{@code use} only run
 * when explicitly invoked.
 *
 * <ul>
 *   <li>{@code /slabdy} — toggle the on-screen target-dy overlay.</li>
 *   <li>{@code /slabdy row} — print the current target's full diagnostic dump to chat.</li>
 *   <li>{@code /slabdy use} — perform a real use/place against the current target and
 *       report the block before/after at the expected placement position.</li>
 * </ul>
 */
public final class SlabdyClientCommands {

    private static boolean overlayEnabled = false;

    private SlabdyClientCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(
                        literal("slabdy")
                                .executes(SlabdyClientCommands::toggleOverlay)
                                .then(literal("row").executes(SlabdyClientCommands::runRow))
                                .then(literal("use").executes(SlabdyClientCommands::runUse))));
        HudRenderCallback.EVENT.register(SlabdyClientCommands::renderOverlay);
    }

    private static int toggleOverlay(CommandContext<FabricClientCommandSource> ctx) {
        overlayEnabled = !overlayEnabled;
        ctx.getSource().sendFeedback(Text.literal("Slabbed target dy overlay: " + (overlayEnabled ? "on" : "off")));
        return 1;
    }

    private static int runRow(CommandContext<FabricClientCommandSource> ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) {
            ctx.getSource().sendError(Text.literal("[slabdy row] no client world"));
            return 0;
        }
        BlockHitResult blockHit = currentBlockTarget(client);
        if (blockHit == null) {
            ctx.getSource().sendError(Text.literal("[slabdy row] target is not a block"));
            return 0;
        }
        List<String> lines = buildRow(client, blockHit, true);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append('\n');
        }
        ctx.getSource().sendFeedback(Text.literal(sb.toString().stripTrailing()));
        return 1;
    }

    private static int runUse(CommandContext<FabricClientCommandSource> ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null || client.interactionManager == null) {
            ctx.getSource().sendError(Text.literal("[slabdy use] no client world"));
            return 0;
        }
        BlockHitResult blockHit = currentBlockTarget(client);
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

    private static void renderOverlay(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
        if (!overlayEnabled) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }
        BlockHitResult blockHit = currentBlockTarget(client);
        if (blockHit == null) {
            drawLine(context, client, "[slabdy] target: none", 8, 8, 0xffd7d7d7);
            return;
        }
        List<String> lines = buildRow(client, blockHit, false);
        double dy = com.slabbed.util.SlabSupport.getVisualYOffset(
                client.world, blockHit.getBlockPos(), client.world.getBlockState(blockHit.getBlockPos()));
        int color = dy == 0.0d ? 0xffd7d7d7 : (dy < 0.0d ? 0xffffd166 : 0xffff8866);
        for (int i = 0; i < lines.size(); i++) {
            drawLine(context, client, lines.get(i), 8, 8 + (i * 12), color);
        }
    }

    private static List<String> buildRow(MinecraftClient client, BlockHitResult blockHit, boolean armModelTrace) {
        BlockPos pos = blockHit.getBlockPos();
        var state = client.world.getBlockState(pos);
        ItemStack held = client.player == null ? ItemStack.EMPTY : client.player.getMainHandStack();

        // Snapshot whatever trace was captured on a PRIOR render frame (if any) before
        // (re-)arming it — the trace this call arms will only be fresh on the NEXT frame,
        // exactly mirroring the Forge original's two-call arm/read cycle.
        OffsetBlockStateModel.RenderOffsetTrace trace = OffsetBlockStateModel.snapshotRenderOffsetTrace();
        String modelTraceLine = formatModelTrace(pos, trace);
        BlockPos armedPos = null;
        if (armModelTrace) {
            OffsetBlockStateModel.resetRenderOffsetTrace(pos);
            client.worldRenderer.scheduleBlockRenders(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX(), pos.getY(), pos.getZ());
            armedPos = pos;
        }

        return SlabdyRowFormatter.formatRow(
                client.world, pos, state, blockHit.getSide(), blockHit.getPos(), held,
                modelTraceLine, armedPos);
    }

    private static String formatModelTrace(BlockPos pos, OffsetBlockStateModel.RenderOffsetTrace trace) {
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

    private static BlockHitResult currentBlockTarget(MinecraftClient client) {
        HitResult target = client.crosshairTarget;
        if (!(target instanceof BlockHitResult blockHit) || target.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return blockHit;
    }

    private static void drawLine(DrawContext context, MinecraftClient client, String line, int x, int y, int color) {
        context.fill(x - 3, y - 3, x + client.textRenderer.getWidth(line) + 3, y + 11, 0x99000000);
        context.drawText(client.textRenderer, line, x, y, color, true);
    }

    private static String blockId(net.minecraft.block.BlockState state) {
        var id = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock());
        return id == null ? "?" : id.toString();
    }
}
