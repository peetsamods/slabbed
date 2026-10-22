package com.slabbed.client;

import java.util.List;
import java.util.Optional;

/** Finds unused screen space without moving vanilla or other mods' controls. */
final class IssueReportPlacement {
    record Box(int x, int y, int width, int height) {
        boolean overlaps(Box other) {
            return x < other.x + other.width + 2 && x + width + 2 > other.x
                    && y < other.y + other.height + 2 && y + height + 2 > other.y;
        }
    }

    static Optional<Box> find(int screenWidth, int screenHeight, int buttonWidth, List<Box> occupied) {
        int width = Math.min(buttonWidth, screenWidth - 16);
        if (width < 40) return Optional.empty();
        for (int y = screenHeight - 24; y >= 8; y -= 2) {
            int center = (screenWidth - width) / 2;
            for (int offset = 0; offset <= screenWidth; offset += 2) {
                for (int x : new int[] {center - offset, center + offset}) {
                    if (x < 8 || x + width > screenWidth - 8) continue;
                    Box candidate = new Box(x, y, width, 20);
                    if (occupied.stream().noneMatch(candidate::overlaps)) return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    private IssueReportPlacement() { }
}
