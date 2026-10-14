package de.legitserver.assistant.buggy.development;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Append-only G1.5 native development journal; B10's derived journal remains intact for compatibility. */
public record BuggyNativeDevelopmentJournalEntry(
        UUID entryId,
        BuggyDevelopmentJournalType type,
        String summary,
        List<String> sourceRefs,
        ActorId createdBy,
        Instant createdAt) {

    public BuggyNativeDevelopmentJournalEntry {
        entryId = CoreValidation.requireNonNull(entryId, "entryId");
        type = CoreValidation.requireNonNull(type, "type");
        summary = CoreValidation.requireText(summary, "summary", 2048);
        sourceRefs = List.copyOf(CoreValidation.requireNonNull(sourceRefs, "sourceRefs"));
        if (sourceRefs.isEmpty() || sourceRefs.size() > 64) {
            throw new IllegalArgumentException("sourceRefs must contain between 1 and 64 entries");
        }
        for (String sourceRef : sourceRefs) {
            CoreValidation.requireText(sourceRef, "sourceRef", 512);
        }
        createdBy = CoreValidation.requireNonNull(createdBy, "createdBy");
        createdAt = CoreValidation.requireNonNull(createdAt, "createdAt");
    }
}
