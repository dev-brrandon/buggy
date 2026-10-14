package de.legitserver.assistant.buggy.development;

import de.legitserver.assistant.buggy.memory.BuggyMemoryId;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** Persisted reflection summary. It stores conclusions/evidence references, never private chain-of-thought. */
public record BuggyReflectionRecord(
        UUID reflectionId,
        List<BuggyMemoryId> memoryRefs,
        String summary,
        String lesson,
        int confidenceBps,
        ActorId createdBy,
        Instant createdAt) {

    public BuggyReflectionRecord {
        reflectionId = CoreValidation.requireNonNull(reflectionId, "reflectionId");
        memoryRefs = List.copyOf(CoreValidation.requireNonNull(memoryRefs, "memoryRefs"));
        if (memoryRefs.isEmpty() || memoryRefs.size() > 64) {
            throw new IllegalArgumentException("memoryRefs must contain between 1 and 64 entries");
        }
        if (new LinkedHashSet<>(memoryRefs).size() != memoryRefs.size()) {
            throw new IllegalArgumentException("memoryRefs must be unique");
        }
        summary = CoreValidation.requireText(summary, "summary", 4096);
        lesson = CoreValidation.requireText(lesson, "lesson", 4096);
        if (confidenceBps < 0 || confidenceBps > 10_000) {
            throw new IllegalArgumentException("confidenceBps must be between 0 and 10000");
        }
        createdBy = CoreValidation.requireNonNull(createdBy, "createdBy");
        createdAt = CoreValidation.requireNonNull(createdAt, "createdAt");
    }
}
