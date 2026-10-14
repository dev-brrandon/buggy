package de.legitserver.assistant.knowledge.search;

import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.core.validation.CoreValidation;
import de.legitserver.assistant.knowledge.model.KnowledgeDocumentId;
import de.legitserver.assistant.knowledge.model.KnowledgeDomain;
import de.legitserver.assistant.knowledge.model.KnowledgeSourceAttribution;
import de.legitserver.assistant.knowledge.policy.KnowledgeLimits;
import de.legitserver.assistant.knowledge.validation.KnowledgeValidation;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Published, ACL-filtered Knowledge retrieval unit including provenance. */
public record KnowledgeSearchHit(
        KnowledgeDocumentId documentId,
        long revision,
        KnowledgeDomain domain,
        String title,
        DataClassification classification,
        String content,
        String contentSha256,
        Set<String> tags,
        Map<String, String> metadata,
        KnowledgeSourceAttribution source,
        double relevanceScore) {

    public KnowledgeSearchHit {
        Objects.requireNonNull(documentId, "documentId");
        if (revision < 1) throw new IllegalArgumentException("revision must be >= 1");
        Objects.requireNonNull(domain, "domain");
        title = CoreValidation.requireText(title, "title", KnowledgeLimits.MAX_TITLE_CHARS);
        Objects.requireNonNull(classification, "classification");
        content = KnowledgeValidation.content(content);
        contentSha256 = KnowledgeValidation.sha256(contentSha256);
        tags = KnowledgeValidation.tags(tags);
        metadata = KnowledgeValidation.metadata(
                metadata,
                KnowledgeLimits.MAX_METADATA_ENTRIES,
                KnowledgeLimits.MAX_METADATA_UTF8_BYTES);
        Objects.requireNonNull(source, "source");
        if (!Double.isFinite(relevanceScore) || relevanceScore < 0.0d) {
            throw new IllegalArgumentException("relevanceScore must be finite and >= 0");
        }
    }
}
