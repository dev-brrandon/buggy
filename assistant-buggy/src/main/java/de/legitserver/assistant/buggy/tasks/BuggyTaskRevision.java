package de.legitserver.assistant.buggy.tasks;

import de.legitserver.assistant.buggy.activity.BuggyAttentionLevel;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.Objects;

/** Immutable task state revision. Repository implementations must append, never rewrite. */
public record BuggyTaskRevision(
        BuggyTaskId taskId,
        long revision,
        Long previousRevision,
        String title,
        String description,
        BuggyTaskStatus status,
        BuggyAttentionLevel attention,
        int progressPercent,
        String waitingReason,
        ActorId changedBy,
        Instant changedAt) {
    public BuggyTaskRevision {
        taskId = Objects.requireNonNull(taskId, "taskId");
        if (revision < 1) throw new IllegalArgumentException("revision must be >= 1");
        if ((revision == 1) != (previousRevision == null)) throw new IllegalArgumentException("previousRevision inconsistent");
        if (previousRevision != null && previousRevision.longValue() != revision - 1) {
            throw new IllegalArgumentException("previousRevision must be immediate predecessor");
        }
        title = CoreValidation.requireText(title, "title", 256);
        description = CoreValidation.requireText(description, "description", 8192);
        status = Objects.requireNonNull(status, "status");
        attention = Objects.requireNonNull(attention, "attention");
        if (progressPercent < 0 || progressPercent > 100) throw new IllegalArgumentException("progressPercent out of range");
        waitingReason = CoreValidation.optionalText(waitingReason, "waitingReason", 1024);
        boolean waiting = status == BuggyTaskStatus.WAITING_DEPENDENCY || status == BuggyTaskStatus.WAITING_APPROVAL;
        if (waiting != (waitingReason != null)) throw new IllegalArgumentException("waiting state/reason inconsistent");
        if (status == BuggyTaskStatus.COMPLETED && progressPercent != 100) {
            throw new IllegalArgumentException("completed task must be 100 percent");
        }
        changedBy = Objects.requireNonNull(changedBy, "changedBy");
        changedAt = Objects.requireNonNull(changedAt, "changedAt");
    }
}
