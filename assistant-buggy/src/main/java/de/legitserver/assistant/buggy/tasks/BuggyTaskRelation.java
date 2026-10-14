package de.legitserver.assistant.buggy.tasks;

import de.legitserver.assistant.core.actor.ActorId;
import java.time.Instant;
import java.util.Objects;

public record BuggyTaskRelation(
        BuggyTaskId sourceTaskId,
        BuggyTaskId targetTaskId,
        BuggyTaskRelationType relationType,
        ActorId createdBy,
        Instant createdAt) {
    public BuggyTaskRelation {
        sourceTaskId = Objects.requireNonNull(sourceTaskId, "sourceTaskId");
        targetTaskId = Objects.requireNonNull(targetTaskId, "targetTaskId");
        relationType = Objects.requireNonNull(relationType, "relationType");
        createdBy = Objects.requireNonNull(createdBy, "createdBy");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (sourceTaskId.equals(targetTaskId)) throw new IllegalArgumentException("task cannot relate to itself");
    }
}
