package de.legitserver.assistant.bugs.model;

import de.legitserver.assistant.core.actor.ActorId;
import java.time.Instant;
import java.util.Objects;

/** Directed relation between two Bug aggregates. */
public record BugRelation(
        InternalBugId sourceBugId,
        BugRelationType relationType,
        InternalBugId targetBugId,
        ActorId createdBy,
        Instant createdAt) {

    public BugRelation {
        Objects.requireNonNull(sourceBugId, "sourceBugId");
        Objects.requireNonNull(relationType, "relationType");
        Objects.requireNonNull(targetBugId, "targetBugId");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt");
        if (sourceBugId.equals(targetBugId)) throw new IllegalArgumentException("Bug relation cannot target itself");
    }
}
