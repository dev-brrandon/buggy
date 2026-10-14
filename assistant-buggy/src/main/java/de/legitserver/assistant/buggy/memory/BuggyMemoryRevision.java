package de.legitserver.assistant.buggy.memory;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;

/** Append-only native memory revision. Older revisions are superseded by the head pointer, never rewritten. */
public record BuggyMemoryRevision(
        BuggyMemoryId memoryId,
        long revision,
        Long previousRevision,
        BuggyMemoryType type,
        ActorId subjectActorId,
        String key,
        String content,
        BuggyMemoryProvenance provenance,
        String sourceRef,
        int confidenceBps,
        BuggyMemoryRetention retention,
        boolean pinned,
        BuggyMemoryState state,
        ActorId changedBy,
        Instant changedAt) {

    public BuggyMemoryRevision {
        memoryId = CoreValidation.requireNonNull(memoryId, "memoryId");
        if (revision < 1) throw new IllegalArgumentException("revision must be >= 1");
        if ((revision == 1 && previousRevision != null)
                || (revision > 1 && (previousRevision == null || previousRevision != revision - 1))) {
            throw new IllegalArgumentException("Invalid memory revision chain");
        }
        type = CoreValidation.requireNonNull(type, "type");
        key = CoreValidation.requireLowerDottedId(key, "key", 128);
        content = CoreValidation.requireText(content, "content", 16_384);
        provenance = CoreValidation.requireNonNull(provenance, "provenance");
        sourceRef = CoreValidation.requireText(sourceRef, "sourceRef", 1024);
        if (confidenceBps < 0 || confidenceBps > 10_000) {
            throw new IllegalArgumentException("confidenceBps must be between 0 and 10000");
        }
        retention = CoreValidation.requireNonNull(retention, "retention");
        state = CoreValidation.requireNonNull(state, "state");
        changedBy = CoreValidation.requireNonNull(changedBy, "changedBy");
        changedAt = CoreValidation.requireNonNull(changedAt, "changedAt");
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
        if (type == BuggyMemoryType.PRIMARY_OWNER && retention == BuggyMemoryRetention.SESSION) {
            throw new IllegalArgumentException("PRIMARY_OWNER memory cannot use SESSION retention");
        }
        if (pinned && retention == BuggyMemoryRetention.SESSION) {
            throw new IllegalArgumentException("SESSION retention cannot be pinned");
        }
    }
}
