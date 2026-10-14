package de.legitserver.assistant.knowledge.policy;

import de.legitserver.assistant.knowledge.model.KnowledgeDocumentStatus;

/** Raised when publication workflow state transitions are violated. */
public final class IllegalKnowledgeStatusTransitionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public IllegalKnowledgeStatusTransitionException(
            KnowledgeDocumentStatus from, KnowledgeDocumentStatus to) {
        super("Illegal Knowledge status transition: " + from + " -> " + to);
    }
}
