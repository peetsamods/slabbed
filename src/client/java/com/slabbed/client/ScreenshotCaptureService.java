package com.slabbed.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.KeyBinding.Category;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Client-only dev helper that captures a screenshot and copies it to the OS clipboard.
 */
public final class ScreenshotCaptureService {
    private static final DateTimeFormatter NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneId.systemDefault());
    private static final Category KEY_CATEGORY = Category.create(Identifier.of("slabbed", "dev"));
    private static final KeyBinding SCREENSHOT_KEY = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.slabbed.screenshot", GLFW.GLFW_KEY_KP_0, KEY_CATEGORY));

    private ScreenshotCaptureService() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (SCREENSHOT_KEY.wasPressed()) {
                capture(client);
            }
        });
    }

    private static void capture(MinecraftClient client) {
        Framebuffer framebuffer = client.getFramebuffer();
        if (framebuffer == null) {
            showMessage(client, Text.literal("[slabdev] No framebuffer available for screenshot"));
            return;
        }

        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path screenshotDir = gameDir.resolve("run").resolve("slabbed-screenshots");
        try {
            Files.createDirectories(screenshotDir);
        } catch (IOException e) {
            showMessage(client, Text.literal("[slabdev] Failed to create screenshot dir: " + e.getMessage()));
            return;
        }

        String fileName = "slabbed-" + NAME_FORMATTER.format(Instant.now()) + ".png";
        Path outputPath = screenshotDir.resolve(fileName);

        ScreenshotRecorder.takeScreenshot(framebuffer, 1, image -> {
            try (image) {
                image.writeTo(outputPath);
                boolean copied = copyToClipboard(image);
                String shownPath = gameDir.relativize(outputPath).toString();
                String status = copied ? " (copied to clipboard)" : " (clipboard unavailable)";
                showMessage(client, Text.literal("[slabdev] Screenshot saved → " + shownPath + status));
            } catch (IOException | RuntimeException e) {
                showMessage(client, Text.literal("[slabdev] Screenshot failed: " + e.getMessage()));
            }
        });
    }

    private static boolean copyToClipboard(NativeImage nativeImage) {
        BufferedImage buffered = toBufferedImage(nativeImage);
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new ImageSelection(buffered), null);
            return true;
        } catch (IllegalStateException | HeadlessException e) {
            return false;
        }
    }

    private static BufferedImage toBufferedImage(NativeImage nativeImage) {
        int width = nativeImage.getWidth();
        int height = nativeImage.getHeight();
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                bufferedImage.setRGB(x, y, nativeImage.getColorArgb(x, y));
            }
        }
        return bufferedImage;
    }

    private static void showMessage(MinecraftClient client, Text text) {
        client.execute(() -> client.inGameHud.getChatHud().addMessage(text));
    }

    private record ImageSelection(BufferedImage image) implements Transferable {
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) {
            if (!isDataFlavorSupported(flavor)) {
                throw new IllegalStateException("Unsupported flavor: " + flavor);
            }
            return image;
        }
    }
}
