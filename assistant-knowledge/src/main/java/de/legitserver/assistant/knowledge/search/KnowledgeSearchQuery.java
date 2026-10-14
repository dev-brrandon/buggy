package de.legitserver.assistant.knowledge.search;

import de.legitserver.assistant.core.validation.CoreValidation;
import de.legitserver.assistant.knowledge.model.KnowledgeDomain;
import de.legitserver.assistant.knowledge.policy.KnowledgeLimits;
import de.legitserver.assistant.knowledge.validation.KnowledgeValidation;
import java.util.Map;
import java.util.Set;

/** Bounded V1 Full Text + Tags + Metadata search request. */
public record KnowledgeSearchQuery(
        String text,
        Set<KnowledgeDomain> domains,
        Set<String> tags,
        Map<String, String> metadata,
        int limit) {

    public KnowledgeSearchQuery {
        text = text == null || text.isBlank()
                ? null
                : CoreValidation.requireText(text, "text", KnowledgeLimits.MAX_SEARCH_QUERY_CHARS);
        domains = domains == null ? Set.of() : Set.copyOf(domains);
        tags = KnowledgeValidation.tags(tags);
        metadata = KnowledgeValidation.metadata(
                metadata,
                KnowledgeLimits.MAX_METADATA_ENTRIES,
                KnowledgeLimits.MAX_METADATA_UTF8_BYTES);
        if (limit < 1 || limit > KnowledgeLimits.MAX_SEARCH_LIMIT) {
            throw new IllegalArgumentException("limit must be 1.." + KnowledgeLimits.MAX_SEARCH_LIMIT);
        }
    }
}
