package com.slabbed.client;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

public final class TargetDyOverlay {
    private static final double EPS = 1.0e-6;

    private static boolean initialized;
    private static boolean enabled = SlabbedClientFlags.TARGET_DY_OVERLAY;

    private TargetDyOverlay() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        HudRenderCallback.EVENT.register(TargetDyOverlay::render);
    }

    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!enabled) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.textRenderer == null) {
            return;
        }
        HitResult target = client.crosshairTarget;
        if (!(target instanceof BlockHitResult blockHit) || target.getType() != HitResult.Type.BLOCK) {
            drawLine(context, client, "[slabdy] target: none", 8, 8, 0xffd7d7d7);
            return;
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = client.world.getBlockState(pos);
        var id = Registries.BLOCK.getId(state.getBlock());
        String namespace = id == null ? "?" : id.getNamespace();

        double dy = ClientDy.dyFor(client.world, pos, state);
        String status = dy < -EPS ? "LOWERED" : (dy > EPS ? "RAISED" : "flush");
        String source = "minecraft".equals(namespace) ? "VANILLA" : "MOD:" + namespace;
        String half = targetHalf(client, pos, state, blockHit);
        String why = dySource(client, pos, state, dy);

        // Support block directly below the targeted block — diagnoses what the
        // target is resting on (e.g. a Terrain Slab) and whether THAT is offset.
        BlockPos below = pos.down();
        BlockState belowState = client.world.getBlockState(below);
        String belowName = belowState.getBlock().getName().getString();
        String belowSlab = slabType(belowState);
        double belowDy = ClientDy.dyFor(client.world, below, belowState);

        String line1 = "[slabdy] " + pos.toShortString() + "  " + state.getBlock().getName().getString();
        String line2 = "  " + source + " * dy=" + format(dy) + " " + status
                + " * side=" + blockHit.getSide().asString() + " * half=" + half;
        String line3 = "  src=" + why;
        String line4 = "  below=" + belowName + " type=" + belowSlab + " belowDy=" + format(belowDy);

        int color = dy == 0.0d ? 0xffd7d7d7 : (dy < 0.0d ? 0xffffd166 : 0xffff8866);
        drawLine(context, client, line1, 8, 8, color);
        drawLine(context, client, line2, 8, 20, color);
        drawLine(context, client, line3, 8, 32, color);
        drawLine(context, client, line4, 8, 44, color);
    }

    private static void drawLine(
            DrawContext context,
            MinecraftClient client,
            String line,
            int x,
            int y,
            int color
    ) {
        context.fill(x - 3, y - 3, x + client.textRenderer.getWidth(line) + 3, y + 11, 0x99000000);
        context.drawText(client.textRenderer, line, x, y, color, true);
    }

    private static String targetHalf(MinecraftClient client, BlockPos pos, BlockState state, BlockHitResult blockHit) {
        VoxelShape outline = state.getOutlineShape(client.world, pos);
        if (outline.isEmpty()) {
            return "?";
        }
        double middle = pos.getY()
                + (outline.getMin(Direction.Axis.Y) + outline.getMax(Direction.Axis.Y)) / 2.0d;
        return blockHit.getPos().y >= middle ? "UPPER" : "LOWER";
    }

    private static String dySource(MinecraftClient client, BlockPos pos, BlockState state, double dy) {
        if (SlabAnchorAttachment.isFrozenFlat(client.world, pos)) {
            return "FROZEN-FLAT";
        }
        if (SlabAnchorAttachment.isAnchored(client.world, pos)) {
            return "ANCHORED";
        }
        if (SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(client.world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(client.world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(client.world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(client.world, pos, state)) {
            return "compound-side";
        }
        return (dy < -EPS || dy > EPS) ? "geometric" : "-";
    }

    private static String slabType(BlockState state) {
        if (state.getBlock() instanceof SlabBlock && state.contains(SlabBlock.TYPE)) {
            SlabType type = state.get(SlabBlock.TYPE);
            return type.asString();
        }
        return "-";
    }

    private static String format(double value) {
        if (!Double.isFinite(value)) {
            return "NaN";
        }
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}
