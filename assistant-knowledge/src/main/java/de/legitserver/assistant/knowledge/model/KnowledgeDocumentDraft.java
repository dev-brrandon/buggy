package de.legitserver.assistant.knowledge.model;

import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.core.validation.CoreValidation;
import de.legitserver.assistant.knowledge.policy.KnowledgeLimits;
import de.legitserver.assistant.knowledge.validation.KnowledgeValidation;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Validated input for creating a new versioned Knowledge document. */
public record KnowledgeDocumentDraft(
        KnowledgeDomain domain,
        String title,
        DataClassification classification,
        KnowledgeAcl acl,
        String content,
        Set<String> tags,
        Map<String, String> metadata,
        KnowledgeSourceAttribution source) {

    public KnowledgeDocumentDraft {
        Objects.requireNonNull(domain, "domain");
        title = CoreValidation.requireText(title, "title", KnowledgeLimits.MAX_TITLE_CHARS);
        Objects.requireNonNull(classification, "classification");
        Objects.requireNonNull(acl, "acl");
        content = KnowledgeValidation.content(content);
        tags = KnowledgeValidation.tags(tags);
        metadata = KnowledgeValidation.metadata(
                metadata,
                KnowledgeLimits.MAX_METADATA_ENTRIES,
                KnowledgeLimits.MAX_METADATA_UTF8_BYTES);
        Objects.requireNonNull(source, "source");
    }
}
