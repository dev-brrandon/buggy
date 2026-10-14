package de.legitserver.assistant.buggy.exclusive;

import de.legitserver.assistant.buggy.activity.BuggyAttentionLevel;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;

/** Priority-channel projection. The source record remains authoritative. */
public record BuggyPriorityItem(
        String priorityId,
        BuggyPriorityKind kind,
        BuggyAttentionLevel attention,
        String summary,
        String sourceRef,
        BuggyContextRef context,
        Instant createdAt) {
    public BuggyPriorityItem {
        priorityId = CoreValidation.requireSafeToken(priorityId, "priorityId", 160);
        kind = CoreValidation.requireNonNull(kind, "kind");
        attention = CoreValidation.requireNonNull(attention, "attention");
        summary = CoreValidation.requireText(summary, "summary", 512);
        sourceRef = CoreValidation.requireSafeToken(sourceRef, "sourceRef", 256);
        context = CoreValidation.requireNonNull(context, "context");
        createdAt = CoreValidation.requireNonNull(createdAt, "createdAt");
    }
}
