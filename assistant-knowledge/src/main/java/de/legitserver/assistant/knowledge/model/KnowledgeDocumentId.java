package de.legitserver.assistant.knowledge.model;

import java.util.Objects;
import java.util.UUID;

/** Stable internal identifier of a Knowledge document. */
public record KnowledgeDocumentId(UUID value) {
    public KnowledgeDocumentId {
        Objects.requireNonNull(value, "value");
    }

    public static KnowledgeDocumentId random() {
        return new KnowledgeDocumentId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
