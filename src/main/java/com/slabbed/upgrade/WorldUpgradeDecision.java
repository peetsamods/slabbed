package com.slabbed.upgrade;

import java.util.Objects;

/** One explicit, versioned upgrade choice for one saved world. */
public record WorldUpgradeDecision(
        int schemaVersion,
        int engineEpoch,
        Mode mode,
        String reviewedModVersion,
        BackupDisposition backupDisposition,
        String backupFileName,
        long backupSize
) {
    public static final int CURRENT_SCHEMA = 1;
    public static final int CURRENT_ENGINE_EPOCH = 1;

    public enum Mode {
        KEEP_EXISTING,
        MIGRATE_ALL
    }

    public enum BackupDisposition {
        REQUESTED_AND_VERIFIED,
        EXPLICITLY_SKIPPED,
        NOT_REQUIRED_NEW_WORLD
    }

    public static WorldUpgradeDecision forNewWorld(String modVersion) {
        return new WorldUpgradeDecision(CURRENT_SCHEMA, CURRENT_ENGINE_EPOCH, Mode.MIGRATE_ALL,
                modVersion, BackupDisposition.NOT_REQUIRED_NEW_WORLD, "", 0);
    }

    public WorldUpgradeDecision {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(backupDisposition, "backupDisposition");
        reviewedModVersion = requireText(reviewedModVersion, "reviewedModVersion");
        backupFileName = Objects.requireNonNull(backupFileName, "backupFileName");
        if (schemaVersion != CURRENT_SCHEMA || engineEpoch != CURRENT_ENGINE_EPOCH) {
            throw new IllegalArgumentException("unsupported world-upgrade schema or engine epoch");
        }
        if (backupDisposition == BackupDisposition.REQUESTED_AND_VERIFIED) {
            backupFileName = requireText(backupFileName, "backupFileName");
            if (backupSize <= 0) {
                throw new IllegalArgumentException("verified backup size must be positive");
            }
        } else if (!backupFileName.isEmpty() || backupSize != 0) {
            throw new IllegalArgumentException("unrequested backup must not carry an artifact");
        }
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank() || value.length() > 160 || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(label + " is invalid");
        }
        return value;
    }
}
