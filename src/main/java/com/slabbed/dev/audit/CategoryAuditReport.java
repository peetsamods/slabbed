package com.slabbed.dev.audit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class CategoryAuditReport {

    private final String categoryId;
    private final Instant auditTime;
    private final List<CategoryAuditResult> results = new ArrayList<>();

    public CategoryAuditReport(String categoryId) {
        this.categoryId = categoryId;
        this.auditTime = Instant.now();
    }

    public void add(CategoryAuditResult result) {
        results.add(result);
    }

    public int placementFailures() {
        return (int) results.stream().filter(r -> !r.placementPass()).count();
    }

    public int neighborFailures() {
        return (int) results.stream().filter(r -> !r.neighborUpdatePass()).count();
    }

    public int reloadFailures() {
        return (int) results.stream().filter(r -> !r.reloadPass()).count();
    }

    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Category: ").append(categoryId).append("\n");
        sb.append("Audit time: ").append(auditTime).append("\n\n");
        sb.append(String.format(
                "%-12s | %-9s | %-8s | %-6s | %-15s | %-32s | %s%n",
                "Lane", "Placement", "Neighbor", "Reload", "Stage", "Reason", "Notes"));
        sb.append("-".repeat(126)).append("\n");
        for (CategoryAuditResult r : results) {
            sb.append(String.format("%-12s | %-9s | %-8s | %-6s | %-15s | %-32s | %s%n",
                    r.lane().name(),
                    r.placementPass() ? "PASS" : "FAIL",
                    r.neighborUpdatePass() ? "PASS" : "FAIL",
                    r.reloadPass() ? "PASS" : "FAIL",
                    r.failureStage().name(),
                    r.failureReason().name(),
                    r.notes()));
        }
        sb.append("\nSummary:\n");
        sb.append("placement_failures=").append(placementFailures()).append("\n");
        sb.append("neighbor_failures=").append(neighborFailures()).append("\n");
        sb.append("reload_failures=").append(reloadFailures()).append("\n");
        sb.append("visual_audit=NOT_RUN\n");
        return sb.toString();
    }

    public void writeTo(Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Path file = outputDir.resolve(categoryId + "-audit.txt");
        Files.writeString(file, toText());
    }
}
