package com.slabbed.client;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.model.OffsetBlockStateModel;
import com.slabbed.util.SlabbedRecorder;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TargetDyOverlay {
    private static boolean initialized;
    private static boolean enabled = SlabbedClientFlags.TARGET_DY_OVERLAY;
    private static BlockHitResult pendingUseTarget;
    private static BlockPos pendingUseExpectedPlace;
    private static String pendingUseBeforeBlock;
    private static int pendingUseObserveTicks;

    private TargetDyOverlay() {
    }

    public static void init(IEventBus eventBus) {
        if (initialized) {
            return;
        }
        initialized = true;
        eventBus.addListener(TargetDyOverlay::registerCommand);
        eventBus.addListener(TargetDyOverlay::render);
        eventBus.addListener(TargetDyOverlay::clientTick);
    }

    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static void registerCommand(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("slabdy")
                .executes(context -> {
                    boolean overlayEnabled = toggle();
                    context.getSource().sendSuccess(
                            () -> Component.literal("Slabbed target dy overlay: "
                                    + (overlayEnabled ? "on" : "off")),
                            false);
                    return 1;
                })
                .then(Commands.literal("row")
                        .executes(context -> reportRow(context.getSource())))
                .then(Commands.literal("use")
                        .executes(context -> useTarget(context.getSource())))
                .then(Commands.literal("record")
                        .executes(context -> toggleRecord(context.getSource()))));
    }

    private static int reportRow(CommandSourceStack source) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.player == null) {
            source.sendFailure(Component.literal("Slabbed target dy row: no client world"));
            return 0;
        }
        HitResult target = client.hitResult;
        if (!(target instanceof BlockHitResult blockHit) || target.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal("Slabbed target dy row: target is not a block"));
            return 0;
        }
        source.sendSuccess(
                () -> Component.literal("Slabbed target dy row: "
                        + String.join(" | ", targetLines(client, blockHit, true))),
                false);
        return 1;
    }

    private static int toggleRecord(CommandSourceStack source) {
        boolean nowOn = SlabbedRecorder.toggle();
        source.sendSuccess(
                () -> Component.literal("Slabbed recorder: " + (nowOn ? "on -> " + SlabbedRecorder.currentLogPath() : "off")),
                false);
        return 1;
    }

    private static int useTarget(CommandSourceStack source) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.player == null || client.gameMode == null) {
            source.sendFailure(Component.literal("Slabbed target dy use: no client world"));
            return 0;
        }
        HitResult target = client.hitResult;
        if (!(target instanceof BlockHitResult blockHit) || target.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal("Slabbed target dy use: target is not a block"));
            return 0;
        }
        BlockPos targetPos = blockHit.getBlockPos();
        if (!client.level.getWorldBorder().isWithinBounds(targetPos)) {
            source.sendFailure(Component.literal("Slabbed target dy use: target outside world border"));
            return 0;
        }
        BlockPos expectedPlacePos = targetPos.relative(blockHit.getDirection());
        String beforePlaceBlock = blockId(client.level.getBlockState(expectedPlacePos));
        pendingUseTarget = blockHit;
        pendingUseExpectedPlace = expectedPlacePos;
        pendingUseBeforeBlock = beforePlaceBlock;
        pendingUseObserveTicks = -1;
        source.sendSuccess(
                () -> Component.literal("Slabbed target dy use: queued keyUse"
                        + " target=" + targetPos.toShortString()
                        + " face=" + blockHit.getDirection().getName()
                        + " expectedPlace=" + expectedPlacePos.toShortString()
                        + " before=" + beforePlaceBlock),
                false);
        return 1;
    }

    private static String lastLoggedSignature;

    private static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        maybeLogTargetChange();
        if (pendingUseTarget == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.player == null || client.gameMode == null) {
            return;
        }
        if (pendingUseObserveTicks < 0) {
            KeyMapping.click(client.options.keyUse.getKey());
            pendingUseObserveTicks = 2;
            return;
        }
        if (pendingUseObserveTicks-- > 0) {
            return;
        }
        BlockHitResult blockHit = pendingUseTarget;
        BlockPos expectedPlacePos = pendingUseExpectedPlace;
        String beforePlaceBlock = pendingUseBeforeBlock;
        pendingUseTarget = null;
        pendingUseExpectedPlace = null;
        pendingUseBeforeBlock = null;

        BlockPos targetPos = blockHit.getBlockPos();
        String afterPlaceBlock = expectedPlacePos == null
                ? "?"
                : blockId(client.level.getBlockState(expectedPlacePos));
        client.player.displayClientMessage(
                Component.literal("Slabbed target dy use: keyUse observed"
                        + " target=" + targetPos.toShortString()
                        + " face=" + blockHit.getDirection().getName()
                        + " expectedPlace=" + (expectedPlacePos == null ? "?" : expectedPlacePos.toShortString())
                        + " before=" + beforePlaceBlock
                        + " after=" + afterPlaceBlock),
                false);
    }

    /**
     * Logs a target row whenever the crosshair's target block/dy/half/face changes,
     * regardless of whether the on-screen /slabdy overlay is toggled. Dedupes on a
     * compact signature (not raw hit coordinates) so continuous look-direction drift
     * does not spam the log; only meaningful target changes are written.
     */
    private static void maybeLogTargetChange() {
        if (!SlabbedRecorder.isEnabled()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.player == null) {
            return;
        }
        HitResult target = client.hitResult;
        if (!(target instanceof BlockHitResult blockHit) || target.getType() != HitResult.Type.BLOCK) {
            if (!"none".equals(lastLoggedSignature)) {
                lastLoggedSignature = "none";
                SlabbedRecorder.log("target", "none");
            }
            return;
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = client.level.getBlockState(pos);
        double dy = ClientDy.dyFor(client.level, pos, state);
        String half = targetHalf(client, pos, state, dy, blockHit);
        String signature = pos.toShortString() + "|" + state + "|" + format(dy) + "|" + half
                + "|" + blockHit.getDirection().getName();
        if (signature.equals(lastLoggedSignature)) {
            return;
        }
        lastLoggedSignature = signature;
        BlockPos placePos = pos.relative(blockHit.getDirection());
        SlabbedRecorder.log("target", String.join(" ", targetLines(client, blockHit, false))
                + " | fullState=" + state);
        SlabbedRecorder.noteTarget(pos, placePos, blockHit.getDirection(), half);
    }

    private static void render(RenderGuiEvent.Post event) {
        if (!enabled) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.font == null) {
            return;
        }
        GuiGraphics context = event.getGuiGraphics();
        HitResult target = client.hitResult;
        if (!(target instanceof BlockHitResult blockHit) || target.getType() != HitResult.Type.BLOCK) {
            drawLine(context, client, "[slabdy] target: none", 8, 8, 0xffd7d7d7);
            return;
        }
        List<String> lines = targetLines(client, blockHit, false);
        double dy = ClientDy.dyFor(client.level, blockHit.getBlockPos(), client.level.getBlockState(blockHit.getBlockPos()));
        int color = dy == 0.0d ? 0xffd7d7d7 : (dy < 0.0d ? 0xffffd166 : 0xffff8866);
        for (int i = 0; i < lines.size(); i++) {
            drawLine(context, client, lines.get(i), 8, 8 + (i * 12), color);
        }
    }

    private static void drawLine(
            GuiGraphics context,
            Minecraft client,
            String line,
            int x,
            int y,
            int color
    ) {
        context.fill(x - 3, y - 3, x + client.font.width(line) + 3, y + 11, 0x99000000);
        context.drawString(client.font, line, x, y, color, true);
    }

    private static String targetHalf(Minecraft client, BlockPos pos, BlockState state, double dy, BlockHitResult blockHit) {
        VoxelShape outline = state.getShape(client.level, pos);
        if (dy != 0.0d) {
            outline = outline.move(0.0d, dy, 0.0d);
        }
        if (outline.isEmpty()) {
            return "?";
        }
        double middle = pos.getY()
                + (outline.min(Direction.Axis.Y) + outline.max(Direction.Axis.Y)) / 2.0d;
        return blockHit.getLocation().y >= middle ? "UPPER" : "LOWER";
    }

    private static List<String> targetLines(Minecraft client, BlockHitResult blockHit, boolean armModelTrace) {
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = client.level.getBlockState(pos);
        String id = blockId(state);
        double dy = ClientDy.dyFor(client.level, pos, state);
        String status = dy < -1.0e-6 ? "LOWERED" : (dy > 1.0e-6 ? "RAISED" : "flush");
        String half = targetHalf(client, pos, state, dy, blockHit);
        String why = dySource(client, pos, state, dy);
        Vec3 hit = blockHit.getLocation();
        Vec3 localHit = hit.subtract(pos.getX(), pos.getY(), pos.getZ());
        VoxelShape outline = state.getShape(client.level, pos);
        if (dy != 0.0d) {
            outline = outline.move(0.0d, dy, 0.0d);
        }
        AABB outlineBox = outline.isEmpty() ? null : outline.bounds();
        BlockPos belowPos = pos.below();
        BlockState belowState = client.level.getBlockState(belowPos);
        double belowDy = ClientDy.dyFor(client.level, belowPos, belowState);
        ItemStack held = client.player == null ? ItemStack.EMPTY : client.player.getMainHandItem();
        BlockPos placePos = pos.relative(blockHit.getDirection());
        BlockState placeState = client.level.getBlockState(placePos);
        OffsetBlockStateModel.ModelDyOwnerSample modelSample = OffsetBlockStateModel.snapshotModelDyOwnerSample();

        List<String> lines = new ArrayList<>();
        lines.add("[slabdy] target=" + pos.toShortString() + " " + id);
        lines.add("  owner=" + pos.toShortString() + " * " + sourceLabel(id)
                + " * dy=" + format(dy) + " " + status
                + " * src=" + why);
        lines.add("  face=" + blockHit.getDirection().getName()
                + " * half=" + half
                + " * hit=" + formatVec(hit)
                + " * local=" + formatVec(localHit));
        lines.add("  outline=" + formatBox(outlineBox)
                + " * outlineMinY=" + (outlineBox == null ? "NaN" : format(outlineBox.minY))
                + " * outlineMaxY=" + (outlineBox == null ? "NaN" : format(outlineBox.maxY)));
        lines.add("  modelTrace=" + formatModelSample(pos, modelSample)
                + " * modelTraceArmed=" + (armModelTrace ? pos.toShortString() : "-"));
        lines.add("  held=" + itemId(held)
                + " * expectedPlace=" + placePos.toShortString()
                + " " + blockId(placeState));
        lines.add("  below=" + belowPos.toShortString() + " " + blockId(belowState)
                + " * dy=" + format(belowDy)
                + " * src=" + dySource(client, belowPos, belowState, belowDy));
        if (armModelTrace) {
            OffsetBlockStateModel.resetModelDyOwnerSample(pos);
            client.levelRenderer.setBlocksDirty(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
        }
        return lines;
    }

    private static String formatModelSample(BlockPos pos, OffsetBlockStateModel.ModelDyOwnerSample sample) {
        if (sample == null || !sample.seen() || !pos.toShortString().equals(sample.pos())) {
            return "missing";
        }
        return "seen"
                + " view=" + sample.viewClass()
                + " emitCalls=" + sample.emitCalls()
                + " appliedCalls=" + sample.appliedCalls()
                + " totalAppliedDy=" + format(sample.totalAppliedDy())
                + " lastDy=" + format(sample.lastDy());
    }

    private static String dySource(Minecraft client, BlockPos pos, BlockState state, double dy) {
        if (SlabAnchorAttachment.isFrozenFlat(client.level, pos)) {
            return "FROZEN-FLAT";
        }
        if (SlabAnchorAttachment.isAnchored(client.level, pos)) {
            return "ANCHORED";
        }
        if (SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(client.level, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(client.level, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(client.level, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(client.level, pos, state)) {
            return "compound-side";
        }
        return (dy < -1.0e-6 || dy > 1.0e-6) ? "geometric" : "-";
    }

    private static String blockId(BlockState state) {
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id == null ? "?" : id.toString();
    }

    private static String sourceLabel(String blockId) {
        int separator = blockId.indexOf(':');
        String namespace = separator >= 0 ? blockId.substring(0, separator) : blockId;
        return "minecraft".equals(namespace) ? "VANILLA" : "MOD:" + namespace;
    }

    private static String itemId(ItemStack stack) {
        if (stack.isEmpty()) {
            return "empty";
        }
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "?" : id.toString();
    }

    private static String formatVec(Vec3 vec) {
        return String.format(Locale.ROOT, "%.3f,%.3f,%.3f", vec.x, vec.y, vec.z);
    }

    private static String formatBox(AABB box) {
        if (box == null) {
            return "empty";
        }
        return String.format(
                Locale.ROOT,
                "[%.3f,%.3f,%.3f -> %.3f,%.3f,%.3f]",
                box.minX,
                box.minY,
                box.minZ,
                box.maxX,
                box.maxY,
                box.maxZ);
    }

    private static String format(double value) {
        if (!Double.isFinite(value)) {
            return "NaN";
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
