package com.slabbed.dev.audit;

public record CategoryAuditResult(
        String categoryId,
        TestLane lane,
        boolean placementPass,
        boolean neighborUpdatePass,
        boolean reloadPass,
        AuditFailureStage failureStage,
        AuditFailureReason failureReason,
        String notes) {
}
