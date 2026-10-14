package de.legitserver.assistant.bugs.model;

import de.legitserver.assistant.bugs.validation.BugValidation;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.classification.DataClassification;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Immutable projection of the mutable Bug head; original report fields never change. */
public record BugRecord(
        InternalBugId internalBugId,
        PublicBugId publicBugId,
        BugStatus status,
        BugSeverity severity,
        BugPriority priority,
        ActorId reporterActorId,
        BugSource source,
        String originalReport,
        String originalReportSha256,
        String assistantSummary,
        BugTargetMapping targetMapping,
        DataClassification classification,
        Instant createdAt,
        Instant updatedAt,
        long revision,
        Map<String, String> structuredMetadata) {

    public BugRecord {
        Objects.requireNonNull(internalBugId, "internalBugId");
        Objects.requireNonNull(publicBugId, "publicBugId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(reporterActorId, "reporterActorId");
        Objects.requireNonNull(source, "source");
        originalReport = BugValidation.originalReport(originalReport);
        originalReportSha256 = BugValidation.sha256(originalReportSha256);
        if (!BugValidation.sha256Of(originalReport).equals(originalReportSha256)) {
            throw new IllegalArgumentException("originalReportSha256 does not match originalReport");
        }
        assistantSummary = BugValidation.assistantSummary(assistantSummary);
        targetMapping = targetMapping == null ? BugTargetMapping.empty() : targetMapping;
        Objects.requireNonNull(classification, "classification");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt before createdAt");
        if (revision < 1) throw new IllegalArgumentException("revision must be >= 1");
        structuredMetadata = BugValidation.metadata(structuredMetadata);
    }
}
