package de.legitserver.assistant.buggy.knowledge;

import de.legitserver.assistant.knowledge.model.KnowledgeDocumentId;
import java.util.Objects;

/** One published Knowledge document whose conflict value disagrees with the candidate value. */
public record BuggyKnowledgeConflict(
        KnowledgeDocumentId documentId,
        long revision,
        String title,
        String existingValue,
        BuggyKnowledgeConfidence confidence,
        BuggyKnowledgeProvenance provenance) {

    public BuggyKnowledgeConflict {
        Objects.requireNonNull(documentId, "documentId");
        if (revision < 1) throw new IllegalArgumentException("revision must be >= 1");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(existingValue, "existingValue");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(provenance, "provenance");
    }
}
