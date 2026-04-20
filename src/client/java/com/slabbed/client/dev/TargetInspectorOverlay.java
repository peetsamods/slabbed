package com.slabbed.client.dev;

import com.slabbed.util.SlabSupport;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.KeyBinding.Category;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

/**
 * Dev-only HUD overlay that reports the current crosshair-target block:
 * target id, block pos, hit face letter (N/E/S/W/T/B), 3x3 face cell
 * (TL..BR), and the slab-support classification of the block directly
 * under and above the target (F/BS/TS/DS).
 *
 * <p>Gameplay-neutral: reads {@link MinecraftClient#crosshairTarget} and
 * world state only; never modifies world, never interacts, never touches
 * slab placement/survival logic. Slab classification routes through
 * {@link SlabSupport}.
 *
 * <p>Registered only in the development environment
 * ({@code FabricLoader#isDevelopmentEnvironment}). Default-off. Bound to
 * F6 by default; the user can rebind it in the Controls screen under the
 * "Miscellaneous" category.
 */
@Environment(EnvType.CLIENT)
public final class TargetInspectorOverlay {
    private static final Category CATEGORY = Category.MISC;
    private static final String KEY_ID = "key.slabbed.target_inspector";

    private static boolean enabled = false;
    private static KeyBinding toggleKey;

    private TargetInspectorOverlay() {
    }

    public static void register() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                KEY_ID,
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                CATEGORY
        ));
        ClientTickEvents.END_CLIENT_TICK.register(TargetInspectorOverlay::onTick);
        HudRenderCallback.EVENT.register(TargetInspectorOverlay::onHud);
    }

    private static void onTick(MinecraftClient client) {
        if (toggleKey == null) return;
        while (toggleKey.wasPressed()) {
            enabled = !enabled;
        }
    }

    private static void onHud(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!enabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) return;
        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof BlockHitResult br) || br.getType() != HitResult.Type.BLOCK) return;

        String line = formatPacket(mc.world, br);
        TextRenderer tr = mc.textRenderer;
        int pad = 4;
        int w = tr.getWidth(line) + pad * 2;
        int h = tr.fontHeight + pad * 2;
        int x = 4;
        int y = 4;
        ctx.fill(x, y, x + w, y + h, 0x80000000);
        ctx.drawText(tr, Text.literal(line), x + pad, y + pad, 0xFFFFFFFF, false);
    }

    private static String formatPacket(World world, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        BlockState target = world.getBlockState(pos);
        Identifier id = Registries.BLOCK.getId(target.getBlock());
        String face = FaceCell.faceLetter(hit.getSide());
        String cell = FaceCell.classify(hit);
        String under = supportLetter(world.getBlockState(pos.down()));
        String above = supportLetter(world.getBlockState(pos.up()));
        return String.format(
                "t=%s pos=%d,%d,%d face=%s cell=%s under=%s above=%s",
                id, pos.getX(), pos.getY(), pos.getZ(), face, cell, under, above
        );
    }

    /**
     * Support classification letter for a neighbor state.
     * F = full/other (non-slab), BS = bottom slab, TS = top slab, DS = double slab.
     */
    private static String supportLetter(BlockState s) {
        if (!SlabSupport.isSupportingSlab(s)) return "F";
        SlabType t = s.get(SlabBlock.TYPE);
        return switch (t) {
            case BOTTOM -> "BS";
            case TOP -> "TS";
            case DOUBLE -> "DS";
        };
    }
}
