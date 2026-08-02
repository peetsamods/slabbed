package com.slabbed.devtools.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.client.model.OffsetBlockStateModel;
import com.slabbed.devtools.SlabbedRuntimeIdentity;
import com.slabbed.devtools.recording.SlabModelStaleSentinel;
import com.slabbed.devtools.recording.SlabbedRecorder;
import com.slabbed.util.SlabbedDiagnosticsBridge;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public final class TargetDyOverlay {
    private static boolean initialized;
    private static boolean enabled;
    private static boolean debugDesired = SlabbedClientFlags.TARGET_DY_OVERLAY;
    private static boolean recordDesired = SlabbedClientFlags.RECORDER;
    private static BlockHitResult pendingUseTarget;
    private static BlockPos pendingUseExpectedPlace;
    private static String pendingUseBeforeBlock;
    private static int pendingUseObserveTicks;
    private static SlabbedDiagnosticsBridge.ActionOriginScope pendingUseOriginScope;
    private static String lastRenderedOutlineSignature;

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
        eventBus.addListener(TargetDyOverlay::onLoggingIn);
        eventBus.addListener(TargetDyOverlay::onLoggingOut);
        eventBus.addListener(TargetDyOverlay::onChunkUnload);
    }

    public static boolean toggle() {
        return setEnabled(!enabled);
    }

    public static boolean setEnabled(boolean value) {
        debugDesired = value;
        enabled = value;
        return enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
            return;
        }
        enabled = debugDesired;
        String worldIdentity = client.level.dimension().location().toString();
        LinkedHashMap<String, String> identity = SlabbedRuntimeIdentity.capture(worldIdentity);
        SlabbedRecorder.configureRuntimeIdentity(identity);
        if (recordDesired) {
            SlabbedDiagnosticsBridge.setRecorderEnabled(true);
        }
        SlabModelStaleSentinel.onWorldJoin(client.level.getGameTime());
        Slabbed.LOGGER.info(
                "[SLABBED_DEVTOOLS_RUNTIME] schema=6 debug={} record={} "
                        + "core={} coreSha256={} addon={} addonSha256={} recorderDir={}",
                enabled ? "on" : "off",
                SlabbedDiagnosticsBridge.isRecorderEnabled() ? "on" : "off",
                identity.getOrDefault("coreFile", "unknown"),
                identity.getOrDefault("coreSha256", "unknown"),
                identity.getOrDefault("addonFile", "unknown"),
                identity.getOrDefault("addonSha256", "unknown"),
                SlabbedDiagnosticsBridge.currentRecorderPath());
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        closePendingUseOriginScope();
        SlabbedRecorder.stopForWorldLeave();
        SlabModelStaleSentinel.onWorldLeave();
        enabled = false;
        pendingUseTarget = null;
        pendingUseExpectedPlace = null;
        pendingUseBeforeBlock = null;
        pendingUseObserveTicks = 0;
        lastLoggedSignature = null;
        lastRenderedOutlineSignature = null;
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        var chunkPos = event.getChunk().getPos();
        SlabModelStaleSentinel.onChunkUnload(chunkPos.x, chunkPos.z);
    }

    /**
     * Canon command root is {@code /slabdev} (matching the 26.2 dev-tooling convention),
     * with {@code debug [on|off|toggle]} owning the crosshair overlay and
     * {@code record [on|off|toggle]} owning {@link SlabbedDiagnosticsBridge}. {@code row} and
     * {@code use} are Forge-only diagnostic extras, not part of the 26.2 contract.
     */
    private static void registerCommand(RegisterClientCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> slabdev = Commands.literal("slabdev");

        slabdev.then(Commands.literal("debug")
                .executes(context -> reportDebugState(context.getSource(), toggle()))
                .then(Commands.literal("on").executes(context -> reportDebugState(context.getSource(), setEnabled(true))))
                .then(Commands.literal("off").executes(context -> reportDebugState(context.getSource(), setEnabled(false))))
                .then(Commands.literal("toggle").executes(context -> reportDebugState(context.getSource(), toggle()))));

        slabdev.then(Commands.literal("row")
                .executes(context -> reportRow(context.getSource())));
        slabdev.then(Commands.literal("use")
                .executes(context -> useTarget(context.getSource())));

        // This command class is packaged only in the slabbed_devtools addon. The core jar has no
        // /slabdev literal; a loaded addon installs the provider before registering this tree.
        if (SlabbedDiagnosticsBridge.isAvailable()) {
            slabdev.then(Commands.literal("record")
                    .executes(context -> reportRecordState(context.getSource(), toggleRecord()))
                    .then(Commands.literal("on").executes(context -> reportRecordState(context.getSource(), setRecordEnabled(true))))
                    .then(Commands.literal("off").executes(context -> reportRecordState(context.getSource(), setRecordEnabled(false))))
                    .then(Commands.literal("toggle").executes(context -> reportRecordState(context.getSource(), toggleRecord()))));
        }
        event.getDispatcher().register(slabdev);
    }

    private static int reportDebugState(CommandSourceStack source, boolean overlayEnabled) {
        source.sendSuccess(
                () -> Component.literal("Slabbed target dy overlay: "
                        + (overlayEnabled ? "on" : "off")),
                false);
        return 1;
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

    private static boolean toggleRecord() {
        return setRecordEnabled(!recordDesired);
    }

    private static boolean setRecordEnabled(boolean value) {
        recordDesired = value;
        boolean nowOn = SlabbedDiagnosticsBridge.setRecorderEnabled(value);
        if (!nowOn) {
            SlabModelStaleSentinel.resetCold();
        } else {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.level != null) {
                SlabModelStaleSentinel.onWorldJoin(client.level.getGameTime());
            }
        }
        return nowOn;
    }

    private static int reportRecordState(CommandSourceStack source, boolean nowOn) {
        source.sendSuccess(
                () -> Component.literal("Slabbed recorder: " + (nowOn ? "on -> " + SlabbedDiagnosticsBridge.currentRecorderPath() : "off")),
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
        BlockPos expectedPlacePos = resolveProxyPlacementPos(client, blockHit);
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
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.level != null) {
            SlabModelStaleSentinel.maybeSample(
                    client.level,
                    client.level.getGameTime(),
                    client.level::hasChunkAt);
        }
        if (pendingUseTarget == null) {
            return;
        }
        if (client == null || client.level == null || client.player == null || client.gameMode == null) {
            closePendingUseOriginScope();
            return;
        }
        if (pendingUseObserveTicks < 0) {
            closePendingUseOriginScope();
            pendingUseOriginScope = SlabbedDiagnosticsBridge.openActionOrigin(
                    SlabbedDiagnosticsBridge.AUTO_USEON_PROXY,
                    new SlabbedDiagnosticsBridge.ActionOriginContext(
                            client.player.getUUID().toString(),
                            client.level.dimension().location().toString(),
                            pendingUseExpectedPlace));
            try {
                KeyMapping.click(client.options.keyUse.getKey());
            } catch (RuntimeException error) {
                closePendingUseOriginScope();
                throw error;
            }
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
        closePendingUseOriginScope();

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

    private static void closePendingUseOriginScope() {
        SlabbedDiagnosticsBridge.ActionOriginScope scope = pendingUseOriginScope;
        pendingUseOriginScope = null;
        if (scope != null) {
            try {
                scope.close();
            } catch (RuntimeException ignored) {
                // Diagnostics cannot make a completed or failed proxy action affect play.
            }
        }
    }

    /** Uses the same vanilla placement-context resolver that the eventual BlockItem action uses. */
    private static BlockPos resolveProxyPlacementPos(
            Minecraft client,
            BlockHitResult blockHit) {
        InteractionHand hand = client.player.getMainHandItem().getItem() instanceof BlockItem
                ? InteractionHand.MAIN_HAND
                : client.player.getOffhandItem().getItem() instanceof BlockItem
                        ? InteractionHand.OFF_HAND
                        : InteractionHand.MAIN_HAND;
        ItemStack stack = client.player.getItemInHand(hand);
        if (stack.getItem() instanceof BlockItem) {
            return new BlockPlaceContext(
                    client.level, client.player, hand, stack, blockHit)
                    .getClickedPos().immutable();
        }
        return blockHit.getBlockPos().relative(blockHit.getDirection()).immutable();
    }

    /**
     * Logs a target row whenever the crosshair's target block/dy/half/face changes,
     * regardless of whether the on-screen /slabdev debug overlay is toggled. Dedupes on a
     * compact signature (not raw hit coordinates) so continuous look-direction drift
     * does not spam the log; only meaningful target changes are written.
     */
    private static void maybeLogTargetChange() {
        if (!SlabbedDiagnosticsBridge.isRecorderEnabled()) {
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
                LinkedHashMap<String, String> row = new LinkedHashMap<>();
                row.put("finalHitPos", "none");
                row.put("finalHitState", "none");
                row.put("hitFace", "none");
                row.put("heldItem", itemId(client.player.getMainHandItem()));
                row.put("playerUuid", client.player.getUUID().toString());
                row.put("dimensionId", client.level.dimension().location().toString());
                row.put("mismatchMarker", "none");
                SlabbedDiagnosticsBridge.recordCursor(row);
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
        BlockPos placePos = resolveProxyPlacementPos(client, blockHit);
        Vec3 hit = blockHit.getLocation();
        VoxelShape outline = state.getShape(client.level, pos);
        if (dy != 0.0d) {
            outline = outline.move(0.0d, dy, 0.0d);
        }
        AABB outlineBox = outline.isEmpty() ? null : outline.bounds();
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("finalHitPos", pos.toShortString());
        row.put("finalHitState", state.toString());
        row.put("finalHitDy", format(dy));
        row.put("hitFace", blockHit.getDirection().getName());
        row.put("targetHalf", half);
        row.put("hitVec", formatVec(hit));
        row.put("cursorOutlineBounds", formatBox(outlineBox));
        row.put("expectedPlacementPos", placePos.toShortString());
        row.put("heldItem", itemId(client.player.getMainHandItem()));
        row.put("playerUuid", client.player.getUUID().toString());
        row.put("dimensionId", client.level.dimension().location().toString());
        row.put("mismatchMarker", "none");
        SlabbedDiagnosticsBridge.recordCursor(row);
        SlabbedDiagnosticsBridge.noteTarget(pos, placePos, blockHit.getDirection(), half);
    }

    private static void render(RenderGuiEvent.Post event) {
        boolean recorderOn = SlabbedDiagnosticsBridge.isRecorderEnabled();
        if (!enabled && !recorderOn) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.font == null) {
            return;
        }
        GuiGraphics context = event.getGuiGraphics();
        HitResult target = client.hitResult;
        if (!(target instanceof BlockHitResult blockHit) || target.getType() != HitResult.Type.BLOCK) {
            if (enabled) {
                drawLine(context, client, "[slabdev] target: none", 8, 8, 0xffd7d7d7);
            }
            return;
        }
        if (recorderOn) {
            recordRenderedOutline(client, blockHit);
        }
        if (!enabled) {
            return;
        }
        List<String> lines = targetLines(client, blockHit, false);
        double dy = ClientDy.dyFor(client.level, blockHit.getBlockPos(), client.level.getBlockState(blockHit.getBlockPos()));
        int color = dy == 0.0d ? 0xffd7d7d7 : (dy < 0.0d ? 0xffffd166 : 0xffff8866);
        for (int i = 0; i < lines.size(); i++) {
            drawLine(context, client, lines.get(i), 8, 8 + (i * 12), color);
        }
    }

    private static void recordRenderedOutline(Minecraft client, BlockHitResult blockHit) {
        if (!SlabbedDiagnosticsBridge.isRecorderEnabled()) {
            return;
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = client.level.getBlockState(pos);
        double dy = ClientDy.dyFor(client.level, pos, state);
        VoxelShape outline = state.getShape(client.level, pos);
        if (dy != 0.0d) {
            outline = outline.move(0.0d, dy, 0.0d);
        }
        AABB localBounds = outline.isEmpty() ? null : outline.bounds();
        AABB worldBounds = localBounds == null
                ? null : localBounds.move(pos.getX(), pos.getY(), pos.getZ());
        Vec3 camera = client.gameRenderer.getMainCamera().getPosition();
        AABB cameraBounds = worldBounds == null
                ? null : worldBounds.move(-camera.x, -camera.y, -camera.z);
        String signature = pos.toShortString() + "|" + state + "|" + format(dy)
                + "|" + formatBox(localBounds) + "|" + blockHit.getDirection().getName();
        if (signature.equals(lastRenderedOutlineSignature)) {
            return;
        }
        lastRenderedOutlineSignature = signature;
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("renderedOutlinePos", pos.toShortString());
        row.put("cursorFinalHitPos", pos.toShortString());
        row.put("renderedOutlineState", state.toString());
        row.put("renderedOutlineBounds", formatBox(localBounds));
        row.put("cursorOutlineBounds", formatBox(localBounds));
        row.put("renderedOutlineWorldBounds", formatBox(worldBounds));
        row.put("renderedOutlineCameraRelativeBounds", formatBox(cameraBounds));
        row.put("renderedOutlineHitVec", formatVec(blockHit.getLocation()));
        row.put("modelDy", format(dy));
        row.put("marker", "none");
        SlabbedDiagnosticsBridge.recordRenderedOutline(row);
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
        lines.add("[slabdev] target=" + pos.toShortString() + " " + id);
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
