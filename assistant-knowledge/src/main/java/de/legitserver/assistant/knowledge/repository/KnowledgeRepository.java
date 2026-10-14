package de.legitserver.assistant.knowledge.repository;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.knowledge.model.KnowledgeDocument;
import de.legitserver.assistant.knowledge.model.KnowledgeDocumentId;
import de.legitserver.assistant.knowledge.model.KnowledgeDocumentStatus;
import de.legitserver.assistant.knowledge.model.KnowledgeRevision;
import java.time.Instant;
import java.util.Optional;

/** Persistence boundary for Knowledge document heads and immutable revisions. */
public interface KnowledgeRepository {
    void create(KnowledgeDocument document, KnowledgeRevision initialRevision);

    Optional<KnowledgeDocument> findById(KnowledgeDocumentId documentId);

    Optional<KnowledgeRevision> findRevision(KnowledgeDocumentId documentId, long revision);

    KnowledgeDocument appendRevision(
            KnowledgeRevision revision,
            long expectedCurrentRevision,
            ActorId updatedBy,
            Instant updatedAt);

    KnowledgeDocument transitionStatus(
            KnowledgeDocumentId documentId,
            long expectedCurrentRevision,
            KnowledgeDocumentStatus expectedStatus,
            KnowledgeDocumentStatus newStatus,
            Instant validFrom,
            ActorId updatedBy,
            Instant updatedAt);
}
