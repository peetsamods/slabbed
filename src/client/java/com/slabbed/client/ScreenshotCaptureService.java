package com.slabbed.client;

import com.slabbed.Slabbed;
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
    private static final int CLIPBOARD_RETRY_ATTEMPTS = 10;
    private static final long CLIPBOARD_RETRY_BACKOFF_MS = 150L;
    private static final int MAX_CHAT_FAILURE_LENGTH = 120;
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
                ClipboardCopyResult clipboardResult = copyToClipboard(image, outputPath);
                String shownPath = gameDir.relativize(outputPath).toString();
                String status;
                if (clipboardResult.imageCopied()) {
                    status = " (image copied to clipboard)";
                } else if (clipboardResult.pathCopied()) {
                    status = " (image clipboard failed; path copied)";
                } else {
                    status = " (clipboard failed: " + trimForChat(clipboardResult.failureDetail()) + ")";
                }
                showMessage(client, Text.literal("[slabdev] Screenshot saved → " + shownPath + status));
            } catch (IOException | RuntimeException e) {
                showMessage(client, Text.literal("[slabdev] Screenshot failed: " + e.getMessage()));
            }
        });
    }

    private static ClipboardCopyResult copyToClipboard(NativeImage nativeImage, Path outputPath) {
        ImageCopyResult imageResult = copyImageToClipboard(nativeImage);
        if (imageResult.copied()) {
            return ClipboardCopyResult.imageSuccess();
        }

        String absolutePath = outputPath.toAbsolutePath().toString();
        boolean pathCopied = copyPathToClipboard(absolutePath);
        if (pathCopied) {
            return ClipboardCopyResult.pathSuccess(imageResult.failureDetail());
        }
        return ClipboardCopyResult.failure(imageResult.failureDetail());
    }

    private static ImageCopyResult copyImageToClipboard(NativeImage nativeImage) {
        BufferedImage buffered = toBufferedImage(nativeImage);
        Clipboard clipboard;
        try {
            clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        } catch (RuntimeException e) {
            String detail = formatThrowable(e);
            Slabbed.LOGGER.warn("[slabdev] Clipboard unavailable before retries: {}", detail, e);
            return ImageCopyResult.failure(detail);
        }

        String firstFailureDetail = null;
        for (int attempt = 1; attempt <= CLIPBOARD_RETRY_ATTEMPTS; attempt++) {
            try {
                clipboard.setContents(new ImageSelection(buffered), null);
                return ImageCopyResult.success();
            } catch (IllegalStateException e) {
                String detail = formatThrowable(e);
                if (firstFailureDetail == null) {
                    firstFailureDetail = detail;
                }
                Slabbed.LOGGER.info("[slabdev] Clipboard attempt {}/{} failed: {}",
                        attempt, CLIPBOARD_RETRY_ATTEMPTS, detail);

                if (attempt == CLIPBOARD_RETRY_ATTEMPTS) {
                    break;
                }

                try {
                    Thread.sleep(CLIPBOARD_RETRY_BACKOFF_MS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    Slabbed.LOGGER.warn("[slabdev] Clipboard retry interrupted after attempt {}", attempt, interrupted);
                    break;
                }
            } catch (RuntimeException e) {
                String detail = formatThrowable(e);
                if (firstFailureDetail == null) {
                    firstFailureDetail = detail;
                }
                Slabbed.LOGGER.warn("[slabdev] Clipboard copy failed with non-retryable error: {}", detail, e);
                return ImageCopyResult.failure(firstFailureDetail);
            }
        }

        if (firstFailureDetail == null) {
            firstFailureDetail = "ClipboardError: unknown";
        }
        Slabbed.LOGGER.warn("[slabdev] Clipboard copy failed after {} attempts; first failure: {}",
                CLIPBOARD_RETRY_ATTEMPTS, firstFailureDetail);
        return ImageCopyResult.failure(firstFailureDetail);
    }

    private static boolean copyPathToClipboard(String absolutePath) {
        try {
            MinecraftClient.getInstance().keyboard.setClipboard(absolutePath);
            return true;
        } catch (RuntimeException e) {
            Slabbed.LOGGER.warn("[slabdev] Path clipboard fallback failed: {}", formatThrowable(e), e);
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

    private static String formatThrowable(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            message = "(no message)";
        }
        return throwable.getClass().getSimpleName() + ": " + message;
    }

    private static String trimForChat(String text) {
        String singleLine = text.replace('\n', ' ').replace('\r', ' ');
        if (singleLine.length() <= MAX_CHAT_FAILURE_LENGTH) {
            return singleLine;
        }
        return singleLine.substring(0, MAX_CHAT_FAILURE_LENGTH - 3) + "...";
    }

    private record ClipboardCopyResult(boolean imageCopied, boolean pathCopied, String failureDetail) {
        private static ClipboardCopyResult imageSuccess() {
            return new ClipboardCopyResult(true, false, "");
        }

        private static ClipboardCopyResult pathSuccess(String failureDetail) {
            return new ClipboardCopyResult(false, true, failureDetail);
        }

        private static ClipboardCopyResult failure(String detail) {
            return new ClipboardCopyResult(false, false, detail);
        }
    }

    private record ImageCopyResult(boolean copied, String failureDetail) {
        private static ImageCopyResult success() {
            return new ImageCopyResult(true, "");
        }

        private static ImageCopyResult failure(String detail) {
            return new ImageCopyResult(false, detail);
        }
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
