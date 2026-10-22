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
        return findNear(screenWidth, screenHeight, buttonWidth, screenHeight - 24, occupied);
    }

    static Optional<Box> findNear(int screenWidth, int screenHeight, int buttonWidth,
                                  int preferredY, List<Box> occupied) {
        int width = Math.min(buttonWidth, screenWidth - 16);
        int bottom = screenHeight - 24;
        if (width < 40 || bottom < 8) return Optional.empty();
        int anchor = Math.max(8, Math.min(preferredY, bottom));
        int center = (screenWidth - width) / 2;
        // Prefer the row beside the menu cluster, then nearby rows, rather than a screen edge.
        for (int distance = 0; distance <= screenHeight; distance += 2) {
            for (int y : new int[] {anchor + distance, anchor - distance}) {
                if (y < 8 || y > bottom) continue;
                for (int offset = 0; offset <= screenWidth; offset += 2) {
                    for (int x : new int[] {center - offset, center + offset}) {
                        if (x < 8 || x + width > screenWidth - 8) continue;
                        Box candidate = new Box(x, y, width, 20);
                        if (occupied.stream().noneMatch(candidate::overlaps)) return Optional.of(candidate);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private IssueReportPlacement() { }
}
