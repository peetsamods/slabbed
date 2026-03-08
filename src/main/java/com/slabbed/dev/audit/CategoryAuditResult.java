package com.slabbed.dev.audit;

public record CategoryAuditResult(
        String categoryId,
        TestLane lane,
        boolean placementPass,
        boolean survivalPass,
        String notes) {
}
