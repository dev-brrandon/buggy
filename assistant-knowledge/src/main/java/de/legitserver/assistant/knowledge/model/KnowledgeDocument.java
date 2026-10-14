package de.legitserver.assistant.knowledge.model;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.core.validation.CoreValidation;
import de.legitserver.assistant.knowledge.policy.KnowledgeLimits;
import java.time.Instant;
import java.util.Objects;

/** Immutable projection of the mutable Knowledge document head. */
public record KnowledgeDocument(
        KnowledgeDocumentId documentId,
        KnowledgeDomain domain,
        String title,
        DataClassification classification,
        KnowledgeDocumentStatus status,
        long currentRevision,
        Instant validFrom,
        ActorId createdBy,
        Instant createdAt,
        ActorId updatedBy,
        Instant updatedAt,
        KnowledgeAcl acl) {

    public KnowledgeDocument {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(domain, "domain");
        title = CoreValidation.requireText(title, "title", KnowledgeLimits.MAX_TITLE_CHARS);
        Objects.requireNonNull(classification, "classification");
        Objects.requireNonNull(status, "status");
        if (currentRevision < 1) throw new IllegalArgumentException("currentRevision must be >= 1");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedBy, "updatedBy");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(acl, "acl");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt before createdAt");
        if (status == KnowledgeDocumentStatus.PUBLISHED && validFrom == null) {
            throw new IllegalArgumentException("PUBLISHED document requires validFrom");
        }
        if ((status == KnowledgeDocumentStatus.DRAFT || status == KnowledgeDocumentStatus.REVIEW)
                && validFrom != null) {
            throw new IllegalArgumentException("Unpublished document must not have validFrom");
        }
        if (validFrom != null && validFrom.isAfter(updatedAt)) {
            throw new IllegalArgumentException("validFrom after updatedAt");
        }
    }
}
