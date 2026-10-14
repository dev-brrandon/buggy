package de.legitserver.assistant.knowledge.repository;

import de.legitserver.assistant.knowledge.model.KnowledgeDocumentId;

/** Optimistic conflict on the current immutable Knowledge revision. */
public final class KnowledgeRevisionConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public KnowledgeRevisionConflictException(KnowledgeDocumentId id, long expectedRevision) {
        super("Knowledge revision conflict for " + id + " at revision " + expectedRevision);
    }
}
