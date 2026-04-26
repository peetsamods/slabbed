package com.slabbed.dev.audit;

public record CategoryAuditCase(
        String categoryId,
        TestLane lane,
        String targetBlockId,
        String expectedSupportKind) {
}
