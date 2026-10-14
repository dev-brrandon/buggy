package de.legitserver.assistant.knowledge.policy;

import de.legitserver.assistant.knowledge.model.KnowledgeDocumentStatus;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Explicit publication state machine for Knowledge document heads. */
public final class KnowledgePublicationWorkflow {
    private static final Map<KnowledgeDocumentStatus, Set<KnowledgeDocumentStatus>> ALLOWED = Map.of(
            KnowledgeDocumentStatus.DRAFT,
                    EnumSet.of(KnowledgeDocumentStatus.REVIEW, KnowledgeDocumentStatus.ARCHIVED),
            KnowledgeDocumentStatus.REVIEW,
                    EnumSet.of(
                            KnowledgeDocumentStatus.DRAFT,
                            KnowledgeDocumentStatus.PUBLISHED,
                            KnowledgeDocumentStatus.ARCHIVED),
            KnowledgeDocumentStatus.PUBLISHED,
                    EnumSet.of(KnowledgeDocumentStatus.ARCHIVED),
            KnowledgeDocumentStatus.ARCHIVED,
                    EnumSet.noneOf(KnowledgeDocumentStatus.class));

    public void requireTransition(KnowledgeDocumentStatus from, KnowledgeDocumentStatus to) {
        if (from == null || to == null) throw new IllegalArgumentException("statuses must not be null");
        if (!ALLOWED.get(from).contains(to)) {
            throw new IllegalKnowledgeStatusTransitionException(from, to);
        }
    }
}
