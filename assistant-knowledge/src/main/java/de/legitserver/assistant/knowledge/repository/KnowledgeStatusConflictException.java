package de.legitserver.assistant.knowledge.repository;

import de.legitserver.assistant.knowledge.model.KnowledgeDocumentId;
import de.legitserver.assistant.knowledge.model.KnowledgeDocumentStatus;

/** Optimistic conflict on a Knowledge publication state transition. */
public final class KnowledgeStatusConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public KnowledgeStatusConflictException(KnowledgeDocumentId id, KnowledgeDocumentStatus expectedStatus) {
        super("Knowledge status conflict for " + id + "; expected " + expectedStatus);
    }
}
