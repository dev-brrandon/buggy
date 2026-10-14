package de.legitserver.assistant.buggy.exclusive;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.List;

/** Owner briefing required by BUGGYBOT-SPEC-V1 section 12.4. */
public record BuggyOwnerBriefing(
        List<String> whatHappened,
        List<String> handledAutomatically,
        List<String> remainsOpen,
        List<String> needsOwnerDecision,
        List<String> learned,
        Instant generatedAt) {
    public BuggyOwnerBriefing {
        whatHappened = checked(whatHappened, "whatHappened");
        handledAutomatically = checked(handledAutomatically, "handledAutomatically");
        remainsOpen = checked(remainsOpen, "remainsOpen");
        needsOwnerDecision = checked(needsOwnerDecision, "needsOwnerDecision");
        learned = checked(learned, "learned");
        generatedAt = CoreValidation.requireNonNull(generatedAt, "generatedAt");
    }

    private static List<String> checked(List<String> values, String field) {
        if (values == null) throw new IllegalArgumentException(field + " must not be null");
        if (values.size() > 100) throw new IllegalArgumentException(field + " exceeds 100 entries");
        return values.stream().map(value -> CoreValidation.requireText(value, field, 1024)).toList();
    }
}
