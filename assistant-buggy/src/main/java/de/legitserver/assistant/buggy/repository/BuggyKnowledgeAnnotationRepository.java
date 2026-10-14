package de.legitserver.assistant.buggy.repository;

import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeAnnotationRevision;
import de.legitserver.assistant.knowledge.model.KnowledgeDocumentId;
import java.util.List;
import java.util.Optional;

/** Persistence port for B4 Buggy-only Knowledge state annotations. */
public interface BuggyKnowledgeAnnotationRepository {
    Optional<BuggyKnowledgeAnnotationRevision> findCurrent(KnowledgeDocumentId documentId);

    List<BuggyKnowledgeAnnotationRevision> history(KnowledgeDocumentId documentId);

    BuggyKnowledgeAnnotationRevision append(long expectedCurrentRevision, BuggyKnowledgeAnnotationRevision revision);
}
