package de.legitserver.assistant.buggy.exclusive;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.List;

/** Derived journal row referencing authoritative B2/B7 history. */
public record BuggyDevelopmentJournalEntry(
        String entryId,
        BuggyDevelopmentJournalEntryType type,
        String summary,
        List<String> sourceRefs,
        Instant occurredAt) {
    public BuggyDevelopmentJournalEntry {
        entryId = CoreValidation.requireSafeToken(entryId, "entryId", 200);
        type = CoreValidation.requireNonNull(type, "type");
        summary = CoreValidation.requireText(summary, "summary", 2048);
        if (sourceRefs == null || sourceRefs.isEmpty()) throw new IllegalArgumentException("sourceRefs must not be empty");
        sourceRefs = sourceRefs.stream()
                .map(value -> CoreValidation.requireSafeToken(value, "sourceRef", 300))
                .distinct()
                .toList();
        occurredAt = CoreValidation.requireNonNull(occurredAt, "occurredAt");
    }
}
