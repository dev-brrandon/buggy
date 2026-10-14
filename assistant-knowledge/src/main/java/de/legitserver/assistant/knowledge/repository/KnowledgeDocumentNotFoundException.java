package de.legitserver.assistant.knowledge.repository;

import de.legitserver.assistant.knowledge.model.KnowledgeDocumentId;

/** Raised when a Knowledge document head does not exist. */
public final class KnowledgeDocumentNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public KnowledgeDocumentNotFoundException(KnowledgeDocumentId id) {
        super("Knowledge document not found: " + id);
    }
}
