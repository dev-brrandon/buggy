package de.legitserver.assistant.buggy.knowledge;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.Map;
import java.util.Set;

/** Typed input for a Buggy-created Foundation Knowledge DRAFT. */
public record BuggyKnowledgeDraft(
        BuggyKnowledgeArea area,
        BuggyKnowledgeTopic topic,
        String title,
        BuggyKnowledgeVisibility visibility,
        String content,
        Set<String> tags,
        Map<String, String> metadata,
        BuggyKnowledgeProvenance provenance,
        String sourceRef,
        String sourceLabel,
        BuggyKnowledgeConfidence confidence,
        String conflictKey,
        String conflictValue) {

    public BuggyKnowledgeDraft {
        area = CoreValidation.requireNonNull(area, "area");
        topic = CoreValidation.requireNonNull(topic, "topic");
        title = CoreValidation.requireText(title, "title", 256);
        visibility = CoreValidation.requireNonNull(visibility, "visibility");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content must not be blank");
        content = content.strip();
        if (content.length() > 1_000_000) throw new IllegalArgumentException("content exceeds max length 1000000");
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        provenance = CoreValidation.requireNonNull(provenance, "provenance");
        sourceRef = CoreValidation.requireText(sourceRef, "sourceRef", 1024);
        sourceLabel = CoreValidation.optionalText(sourceLabel, "sourceLabel", 256);
        confidence = CoreValidation.requireNonNull(confidence, "confidence");
        conflictKey = CoreValidation.optionalSafeToken(conflictKey, "conflictKey", 128);
        conflictValue = CoreValidation.optionalText(conflictValue, "conflictValue", 1024);
        if ((conflictKey == null) != (conflictValue == null)) {
            throw new IllegalArgumentException("conflictKey and conflictValue must either both be set or both be null");
        }
    }
}
