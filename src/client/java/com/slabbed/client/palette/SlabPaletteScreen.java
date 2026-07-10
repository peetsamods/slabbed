package com.slabbed.client.palette;

import com.slabbed.util.SlabTestKit;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * The 5x9 palette overlay, docked to a screen edge. Being a {@link Screen} gives frozen player
 * movement and a free cursor for free. Clicking a cell drops that item into the player's current
 * hotbar slot (creative only); shift-click stays open. A gear button flips to an options view for
 * dock side / scale / rows and, in edit mode, right-click sets a cell to the item in the main hand.
 *
 * <p>Rendering is deliberately vanilla-simple: flat {@code fill} rects for slots plus
 * {@code gfx.item(...)} icons, so it cannot break on resource packs. All coordinate math is in
 * scaled pixels; only the intrinsically-16px item glyph is drawn through a scaled pose.
 */
public final class SlabPaletteScreen extends Screen {

    private static final int BASE_CELL = 18;
    private static final int EDGE_MARGIN = 8;
    private static final int HEADER_H = 14;

    private static final int PANEL_BG = 0xC0101010;
    private static final int SLOT_BG = 0x80000000;
    private static final int SLOT_BORDER = 0xFF3A3A3A;
    private static final int SLOT_HOVER = 0x80FFFFFF;
    private static final int GEAR_BG = 0xFF2A2A2A;
    private static final int GEAR_BG_HOVER = 0xFF4A6A9A;

    private final SlabPaletteConfig config = SlabPaletteConfig.get();
    private boolean optionsOpen;
    private boolean editMode;

    /**
     * The 45 resolved icons, built once per screen-init and after any config change — so rendering
     * never re-parses ids or allocates stacks per frame (and can never crash on a bad id: resolution
     * goes through {@link SlabPaletteConfig#resolveStackAt(int)}). {@code null} until {@link #init()}.
     */
    private ItemStack[] stackCache;

    // Header titles hoisted out of the per-frame render path.
    private Component whiteTitle;
    private Component editTitle;

    public SlabPaletteScreen() {
        super(Component.literal("Slabbed Palette"));
    }

    @Override
    public boolean isPauseScreen() {
        // Never pause: this is a docked overlay the maintainer flips open mid-test, not a menu.
        return false;
    }

    @Override
    protected void init() {
        super.init();
        if (whiteTitle == null) {
            whiteTitle = Component.literal("Slabbed Palette").withStyle(ChatFormatting.WHITE);
            editTitle = Component.literal("Palette — edit (right-click a cell = main hand)")
                    .withStyle(ChatFormatting.YELLOW);
        }
        rebuildCache();
    }

    /** Re-resolves every cell icon from config. Call after any config change (options / edit writes). */
    private void rebuildCache() {
        ItemStack[] cache = new ItemStack[SlabTestKit.SIZE];
        for (int i = 0; i < cache.length; i++) {
            cache[i] = config.resolveStackAt(i);
        }
        stackCache = cache;
    }

    // --- geometry ------------------------------------------------------------------------------

    private int cell() {
        return Math.max(1, Math.round(BASE_CELL * config.scale()));
    }

    private int rows() {
        return config.rows();
    }

    private int gridWidth() {
        return SlabTestKit.COLUMNS * cell();
    }

    private int gridHeight() {
        return rows() * cell();
    }

    private int originX() {
        int margin = Math.round(EDGE_MARGIN * config.scale());
        return config.side() == SlabPaletteConfig.DockSide.LEFT
                ? margin
                : this.width - gridWidth() - margin;
    }

    private int originY() {
        int header = Math.round(HEADER_H * config.scale());
        return Math.max(header + 4, (this.height - gridHeight()) / 2);
    }

    /** Screen-space rect of the gear button (top-right corner of the panel). */
    private int gearX() {
        return originX() + gridWidth() - cell();
    }

    private int gearY() {
        return originY() - Math.round(HEADER_H * config.scale()) - 2;
    }

    private int gearSize() {
        return Math.round(HEADER_H * config.scale());
    }

    /** Grid cell index under (mx,my), or -1. */
    private int cellAt(double mx, double my) {
        int c = cell();
        int x0 = originX();
        int y0 = originY();
        if (mx < x0 || my < y0 || mx >= x0 + gridWidth() || my >= y0 + gridHeight()) {
            return -1;
        }
        int col = (int) ((mx - x0) / c);
        int row = (int) ((my - y0) / c);
        if (col < 0 || col >= SlabTestKit.COLUMNS || row < 0 || row >= rows()) {
            return -1;
        }
        return row * SlabTestKit.COLUMNS + col;
    }

    private boolean overGear(double mx, double my) {
        int gx = gearX();
        int gy = gearY();
        int gs = gearSize();
        return mx >= gx && mx < gx + gs && my >= gy && my < gy + gs;
    }

    /** Reads a cell icon from the cache built in {@link #init()} (never re-parses or allocates here). */
    private ItemStack stackAt(int index) {
        if (stackCache == null || index < 0 || index >= stackCache.length) {
            return ItemStack.EMPTY;
        }
        return stackCache[index];
    }

    // --- rendering -----------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        // Dim the world a touch so the grid reads clearly, without a full menu blur.
        gfx.fill(0, 0, this.width, this.height, 0x40000000);

        int c = cell();
        int x0 = originX();
        int y0 = originY();
        int header = Math.round(HEADER_H * config.scale());

        // Panel backing.
        gfx.fill(x0 - 3, y0 - header - 3, x0 + gridWidth() + 3, y0 + gridHeight() + 3, PANEL_BG);

        // Header label (titles hoisted to fields in init() — no per-frame allocation).
        gfx.text(this.font, editMode ? editTitle : whiteTitle, x0, y0 - header + 1, 0xFFFFFFFF);

        // Gear button.
        int gx = gearX();
        int gy = gearY();
        int gs = gearSize();
        boolean gearHover = overGear(mouseX, mouseY);
        gfx.fill(gx, gy, gx + gs, gy + gs, gearHover ? GEAR_BG_HOVER : GEAR_BG);
        gfx.centeredText(this.font, Component.literal(optionsOpen ? "x" : "⚙"),
                gx + gs / 2, gy + (gs - 8) / 2, 0xFFFFFFFF);

        int hovered = cellAt(mouseX, mouseY);

        // Slots + icons.
        for (int i = 0; i < SlabTestKit.COLUMNS * rows(); i++) {
            int col = i % SlabTestKit.COLUMNS;
            int row = i / SlabTestKit.COLUMNS;
            int cx = x0 + col * c;
            int cy = y0 + row * c;

            gfx.fill(cx, cy, cx + c, cy + c, SLOT_BG);
            // 1px border.
            gfx.fill(cx, cy, cx + c, cy + 1, SLOT_BORDER);
            gfx.fill(cx, cy + c - 1, cx + c, cy + c, SLOT_BORDER);
            gfx.fill(cx, cy, cx + 1, cy + c, SLOT_BORDER);
            gfx.fill(cx + c - 1, cy, cx + c, cy + c, SLOT_BORDER);

            ItemStack stack = stackAt(i);
            if (!stack.isEmpty()) {
                // Center a 16px icon in the (scaled) cell, scaling the glyph itself via the pose.
                float iconScale = config.scale();
                int inset = Math.round((c - 16 * iconScale) / 2f);
                gfx.pose().pushMatrix();
                gfx.pose().translate(cx + inset, cy + inset);
                gfx.pose().scale(iconScale, iconScale);
                gfx.item(stack, 0, 0);
                gfx.pose().popMatrix();
            }

            if (i == hovered) {
                gfx.fill(cx + 1, cy + 1, cx + c - 1, cy + c - 1, SLOT_HOVER);
            }
        }

        // Hover tooltip (drawn last so it sits above the grid).
        if (hovered >= 0) {
            ItemStack stack = stackAt(hovered);
            if (!stack.isEmpty()) {
                gfx.setTooltipForNextFrame(this.font, stack, mouseX, mouseY);
            }
        }

        if (optionsOpen) {
            renderOptions(gfx, mouseX, mouseY);
        }
    }

    private void renderOptions(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        int panelW = 150;
        int panelH = 74;
        int px = (this.width - panelW) / 2;
        int py = (this.height - panelH) / 2;
        gfx.fill(px, py, px + panelW, py + panelH, 0xF0101010);
        gfx.fill(px, py, px + panelW, py + 1, 0xFF5A5A5A);

        gfx.text(this.font, Component.literal("Palette options").withStyle(ChatFormatting.WHITE),
                px + 6, py + 5, 0xFFFFFFFF);
        drawOptionRow(gfx, px, py + 18, "Dock side", config.side().name());
        drawOptionRow(gfx, px, py + 32, "Scale", String.format("%.2fx", config.scale()));
        drawOptionRow(gfx, px, py + 46, "Rows", Integer.toString(config.rows()));
        gfx.text(this.font,
                Component.literal(editMode ? "Edit: ON (right-click cells)" : "Edit: off (click to toggle)")
                        .withStyle(editMode ? ChatFormatting.YELLOW : ChatFormatting.GRAY),
                px + 6, py + 60, 0xFFFFFFFF);
    }

    private void drawOptionRow(GuiGraphicsExtractor gfx, int px, int y, String label, String value) {
        gfx.text(this.font, Component.literal(label).withStyle(ChatFormatting.GRAY), px + 6, y, 0xFFAAAAAA);
        gfx.text(this.font, Component.literal("[ " + value + " ]").withStyle(ChatFormatting.AQUA),
                px + 84, y, 0xFF66DDFF);
    }

    // --- options hit regions (must match renderOptions) ----------------------------------------

    private int optX() {
        return (this.width - 150) / 2;
    }

    private int optY() {
        return (this.height - 74) / 2;
    }

    /** Which options row (0 side, 1 scale, 2 rows, 3 edit-toggle) is under the cursor, or -1. */
    private int optionRowAt(double mx, double my) {
        int px = optX();
        int py = optY();
        if (mx < px || mx >= px + 150) {
            return -1;
        }
        if (my >= py + 16 && my < py + 30) {
            return 0;
        }
        if (my >= py + 30 && my < py + 44) {
            return 1;
        }
        if (my >= py + 44 && my < py + 58) {
            return 2;
        }
        if (my >= py + 58 && my < py + 72) {
            return 3;
        }
        return -1;
    }

    // --- input ---------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();

        // A mouse-rebound palette key toggles the overlay closed (default V is a keyboard key, so this
        // is inert unless the maintainer binds the open key to a mouse button).
        if (SlabPalette.OPEN_KEY.matchesMouse(event)) {
            onClose();
            return true;
        }

        if (overGear(mx, my)) {
            optionsOpen = !optionsOpen;
            return true;
        }

        if (optionsOpen) {
            // Consume ANY click within the panel's full visual rect (title band + bottom sliver
            // included) so it can never fall through to a grid cell drawn behind the panel.
            if (overOptionsPanel(mx, my)) {
                switch (optionRowAt(mx, my)) {
                    case 0 -> { config.cycleSide(); rebuildCache(); }
                    case 1 -> { config.cycleScale(); rebuildCache(); }
                    case 2 -> { config.cycleRows(); rebuildCache(); }
                    case 3 -> editMode = !editMode;
                    default -> { /* click inside the panel but not on a row: swallow it */ }
                }
                return true;
            }
            // Clicks outside the panel fall through to grid handling below.
        }

        int index = cellAt(mx, my);
        if (index >= 0) {
            if (editMode && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                setCellToMainHand(index);
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                grabToHotbar(index, hasShiftDown());
                return true;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    /** True when (mx,my) is anywhere inside the options panel's full visual rect (see renderOptions). */
    private boolean overOptionsPanel(double mx, double my) {
        int px = optX();
        int py = optY();
        return mx >= px && mx < px + 150 && my >= py && my < py + 74;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // ESC or the (possibly rebound) palette key closes.
        if (event.key() == GLFW.GLFW_KEY_ESCAPE || SlabPalette.OPEN_KEY.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(null);
    }

    private static boolean hasShiftDown() {
        long window = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    // --- actions -------------------------------------------------------------------------------

    private void grabToHotbar(int index, boolean stayOpen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (!mc.player.isCreative()) {
            mc.player.sendSystemMessage(
                    Component.literal("The Slabbed palette grabs items in Creative only.")
                            .withStyle(ChatFormatting.RED));
            return;
        }
        ItemStack stack = stackAt(index);
        if (stack.isEmpty()) {
            return;
        }
        int hotbar = mc.player.getInventory().getSelectedSlot();
        // Mirror vanilla CreativeModeInventoryScreen: set the LOCAL inventory slot first (so the change
        // is felt this frame, no server round-trip lag), THEN send the creative add. Creative container
        // slots put the hotbar at 36..44 (36 + hotbar index), like the creative inventory screen.
        mc.player.getInventory().setItem(hotbar, stack.copy());
        mc.gameMode.handleCreativeModeItemAdd(stack.copy(), 36 + hotbar);
        if (!stayOpen) {
            onClose();
        }
    }

    private void setCellToMainHand(int index) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        ItemStack held = mc.player.getMainHandItem();
        Identifier id = held.isEmpty()
                ? Identifier.fromNamespaceAndPath("minecraft", "air")
                : BuiltInRegistries.ITEM.getKey(held.getItem());
        config.setItemAt(index, id);
        rebuildCache();
    }
}
