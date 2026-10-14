package de.legitserver.assistant.buggy.knowledge;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.validation.CoreValidation;
import de.legitserver.assistant.knowledge.model.KnowledgeDocumentId;
import java.time.Instant;
import java.util.Objects;

/** Append-only Buggy assessment of a Foundation Knowledge document. No Knowledge content is duplicated here. */
public record BuggyKnowledgeAnnotationRevision(
        KnowledgeDocumentId documentId,
        long revision,
        Long previousRevision,
        BuggyKnowledgeState state,
        String reason,
        String evidenceRef,
        BuggyKnowledgeConfidence confidence,
        ActorId createdBy,
        Instant createdAt,
        String sha256) {

    public BuggyKnowledgeAnnotationRevision {
        Objects.requireNonNull(documentId, "documentId");
        if (revision < 1) throw new IllegalArgumentException("revision must be >= 1");
        if (revision == 1 && previousRevision != null) throw new IllegalArgumentException("first annotation revision has no previous revision");
        if (revision > 1 && !Long.valueOf(revision - 1).equals(previousRevision)) {
            throw new IllegalArgumentException("annotation revision chain is not contiguous");
        }
        Objects.requireNonNull(state, "state");
        reason = CoreValidation.requireText(reason, "reason", 1024);
        evidenceRef = CoreValidation.requireText(evidenceRef, "evidenceRef", 1024);
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt");
        sha256 = CoreValidation.requireSafeToken(sha256, "sha256", 64);
        String expected = BuggyKnowledgeCanonicalizer.annotationSha256(
                documentId, revision, previousRevision, state, reason, evidenceRef, confidence, createdBy, createdAt);
        if (!expected.equals(sha256)) throw new IllegalArgumentException("Buggy Knowledge annotation SHA-256 mismatch");
    }
}
