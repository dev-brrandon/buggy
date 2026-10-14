package de.legitserver.assistant.knowledge.model;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.knowledge.policy.KnowledgeLimits;
import de.legitserver.assistant.knowledge.validation.KnowledgeValidation;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable content revision. Knowledge content is always treated as untrusted input. */
public record KnowledgeRevision(
        KnowledgeDocumentId documentId,
        long revision,
        String content,
        String contentSha256,
        Set<String> tags,
        Map<String, String> metadata,
        KnowledgeSourceAttribution source,
        ActorId createdBy,
        Instant createdAt) {

    public KnowledgeRevision {
        Objects.requireNonNull(documentId, "documentId");
        if (revision < 1) throw new IllegalArgumentException("revision must be >= 1");
        content = KnowledgeValidation.content(content);
        contentSha256 = KnowledgeValidation.sha256(contentSha256);
        if (!contentSha256.equals(KnowledgeValidation.sha256Of(content))) {
            throw new IllegalArgumentException("contentSha256 does not match content");
        }
        tags = KnowledgeValidation.tags(tags);
        metadata = KnowledgeValidation.metadata(
                metadata,
                KnowledgeLimits.MAX_METADATA_ENTRIES,
                KnowledgeLimits.MAX_METADATA_UTF8_BYTES);
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
