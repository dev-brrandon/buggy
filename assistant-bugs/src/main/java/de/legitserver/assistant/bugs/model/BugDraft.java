package de.legitserver.assistant.bugs.model;

import de.legitserver.assistant.bugs.validation.BugValidation;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.classification.DataClassification;
import java.util.Map;
import java.util.Objects;

/** Input required to create the immutable original Bug report and mutable Bug head. */
public record BugDraft(
        PublicBugId publicBugId,
        BugSeverity severity,
        BugPriority priority,
        ActorId reporterActorId,
        BugSource source,
        String originalReport,
        String assistantSummary,
        BugTargetMapping targetMapping,
        DataClassification classification,
        Map<String, String> structuredMetadata) {

    public BugDraft {
        Objects.requireNonNull(publicBugId, "publicBugId");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(reporterActorId, "reporterActorId");
        Objects.requireNonNull(source, "source");
        originalReport = BugValidation.originalReport(originalReport);
        assistantSummary = BugValidation.assistantSummary(assistantSummary);
        targetMapping = targetMapping == null ? BugTargetMapping.empty() : targetMapping;
        Objects.requireNonNull(classification, "classification");
        structuredMetadata = BugValidation.metadata(structuredMetadata);
    }
}
