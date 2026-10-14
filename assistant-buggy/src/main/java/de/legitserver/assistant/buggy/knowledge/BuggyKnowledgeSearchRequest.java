package de.legitserver.assistant.buggy.knowledge;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.Map;
import java.util.Set;

/** Bounded Buggy search request over the existing Foundation Knowledge search port. */
public record BuggyKnowledgeSearchRequest(
        String text,
        Set<BuggyKnowledgeTopic> topics,
        Set<String> tags,
        Map<String, String> metadata,
        boolean includeStale,
        boolean includeDisputed,
        int limit) {

    public BuggyKnowledgeSearchRequest {
        text = text == null || text.isBlank() ? null : CoreValidation.requireText(text, "text", 512);
        topics = topics == null ? Set.of() : Set.copyOf(topics);
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be 1..100");
    }
}
