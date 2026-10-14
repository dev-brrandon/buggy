package de.legitserver.assistant.buggy.automation;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record BuggyAutomationRun(
        UUID runId,
        String automationId,
        long automationRevision,
        BuggyAutomationLevel finalLevel,
        String targetFingerprint,
        boolean successful,
        String safeCode,
        Instant startedAt,
        Instant completedAt) {
    public BuggyAutomationRun {
        runId = Objects.requireNonNull(runId, "runId");
        automationId = CoreValidation.requireSafeToken(automationId, "automationId", 128);
        if (automationRevision < 1) throw new IllegalArgumentException("automationRevision must be >= 1");
        finalLevel = Objects.requireNonNull(finalLevel, "finalLevel");
        targetFingerprint = CoreValidation.requireSafeToken(targetFingerprint, "targetFingerprint", 192);
        safeCode = CoreValidation.requireUpperCode(safeCode, "safeCode", 96);
        startedAt = Objects.requireNonNull(startedAt, "startedAt");
        completedAt = Objects.requireNonNull(completedAt, "completedAt");
        if (completedAt.isBefore(startedAt)) throw new IllegalArgumentException("completedAt precedes startedAt");
    }
}
