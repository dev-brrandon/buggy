package de.legitserver.assistant.bugs.model;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.Objects;

/** Reference-only link to the canonical incident domain; no sensitive incident payload is copied. */
public record BugIncidentLink(
        InternalBugId internalBugId,
        String internalIncidentId,
        String publicErrorId,
        ActorId createdBy,
        Instant createdAt) {

    public BugIncidentLink {
        Objects.requireNonNull(internalBugId, "internalBugId");
        internalIncidentId = CoreValidation.requireSafeToken(internalIncidentId, "internalIncidentId", 128);
        publicErrorId = CoreValidation.optionalSafeToken(publicErrorId, "publicErrorId", 128);
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
