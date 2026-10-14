package de.legitserver.assistant.buggy.memory;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.UUID;

/** Immutable intake candidate. Accepting it creates revision 1; rejection never creates durable memory. */
public record BuggyMemoryCandidate(
        UUID candidateId,
        BuggyMemoryType type,
        ActorId subjectActorId,
        String key,
        String content,
        BuggyMemoryProvenance provenance,
        String sourceRef,
        int confidenceBps,
        BuggyMemoryRetention retention,
        boolean pinned,
        ActorId createdBy,
        Instant createdAt) {

    public BuggyMemoryCandidate {
        candidateId = CoreValidation.requireNonNull(candidateId, "candidateId");
        type = CoreValidation.requireNonNull(type, "type");
        key = CoreValidation.requireLowerDottedId(key, "key", 128);
        content = CoreValidation.requireText(content, "content", 16_384);
        provenance = CoreValidation.requireNonNull(provenance, "provenance");
        sourceRef = CoreValidation.requireText(sourceRef, "sourceRef", 1024);
        if (confidenceBps < 0 || confidenceBps > 10_000) {
            throw new IllegalArgumentException("confidenceBps must be between 0 and 10000");
        }
        retention = CoreValidation.requireNonNull(retention, "retention");
        createdBy = CoreValidation.requireNonNull(createdBy, "createdBy");
        createdAt = CoreValidation.requireNonNull(createdAt, "createdAt");
        requireShape(type, subjectActorId, sourceRef, retention, pinned);
    }

    private static void requireShape(
            BuggyMemoryType type,
            ActorId subjectActorId,
            String sourceRef,
            BuggyMemoryRetention retention,
            boolean pinned) {
        if ((type == BuggyMemoryType.RELATIONSHIP || type == BuggyMemoryType.PRIMARY_OWNER)
                && subjectActorId == null) {
            throw new IllegalArgumentException(type + " memory requires subjectActorId");
        }
        if (type == BuggyMemoryType.SESSION && !sourceRef.startsWith("conversation:")) {
            throw new IllegalArgumentException("SESSION memory must reference conversation:<id>");
        }
        if (type == BuggyMemoryType.KNOWLEDGE && !sourceRef.startsWith("knowledge:")) {
            throw new IllegalArgumentException("KNOWLEDGE memory must reference knowledge:<id>");
        }
        if (pinned && retention == BuggyMemoryRetention.SESSION) {
            throw new IllegalArgumentException("SESSION retention cannot be pinned");
        }
        if (type == BuggyMemoryType.PRIMARY_OWNER && retention == BuggyMemoryRetention.SESSION) {
            throw new IllegalArgumentException("PRIMARY_OWNER memory must outlive a session");
        }
    }
}
