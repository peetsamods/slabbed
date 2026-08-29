package com.slabbed.client;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.Locale;

public final class TargetDyOverlay {
    private static boolean initialized;
    private static boolean enabled = SlabbedClientFlags.TARGET_DY_OVERLAY;

    private TargetDyOverlay() {
    }

    public static void init(IEventBus eventBus) {
        if (initialized) {
            return;
        }
        initialized = true;
        eventBus.addListener(TargetDyOverlay::registerCommand);
        eventBus.addListener(TargetDyOverlay::render);
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
                }));
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
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = client.level.getBlockState(pos);
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String namespace = id == null ? "?" : id.getNamespace();

        double dy = ClientDy.dyFor(client.level, pos, state);
        String status = dy < -1.0e-6 ? "LOWERED" : (dy > 1.0e-6 ? "RAISED" : "flush");
        String source = "minecraft".equals(namespace) ? "VANILLA" : "MOD:" + namespace;
        String half = targetHalf(client, pos, state, blockHit);
        String why = dySource(client, pos, state, dy);

        String line1 = "[slabdy] " + pos.toShortString() + "  " + state.getBlock().getName().getString();
        String line2 = "  " + source + " * dy=" + format(dy) + " " + status
                + " * side=" + blockHit.getDirection().getName() + " * half=" + half;
        String line3 = "  src=" + why;
        int color = dy == 0.0d ? 0xffd7d7d7 : (dy < 0.0d ? 0xffffd166 : 0xffff8866);
        drawLine(context, client, line1, 8, 8, color);
        drawLine(context, client, line2, 8, 20, color);
        drawLine(context, client, line3, 8, 32, color);
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

    private static String targetHalf(Minecraft client, BlockPos pos, BlockState state, BlockHitResult blockHit) {
        VoxelShape outline = state.getShape(client.level, pos);
        if (outline.isEmpty()) {
            return "?";
        }
        double middle = pos.getY()
                + (outline.min(Direction.Axis.Y) + outline.max(Direction.Axis.Y)) / 2.0d;
        return blockHit.getLocation().y >= middle ? "UPPER" : "LOWER";
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

    private static String format(double value) {
        if (!Double.isFinite(value)) {
            return "NaN";
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
