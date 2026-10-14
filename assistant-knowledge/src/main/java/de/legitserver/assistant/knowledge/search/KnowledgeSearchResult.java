package de.legitserver.assistant.knowledge.search;

import java.util.List;
import java.util.Objects;

/** Explicit Knowledge result that never turns an empty retrieval into invented content. */
public record KnowledgeSearchResult(KnowledgeSearchStatus status, List<KnowledgeSearchHit> hits) {
    public KnowledgeSearchResult {
        Objects.requireNonNull(status, "status");
        hits = hits == null ? List.of() : List.copyOf(hits);
        if (status == KnowledgeSearchStatus.RESULTS && hits.isEmpty()) {
            throw new IllegalArgumentException("RESULTS requires at least one hit");
        }
        if (status == KnowledgeSearchStatus.NO_RELIABLE_RESULT && !hits.isEmpty()) {
            throw new IllegalArgumentException("NO_RELIABLE_RESULT must not contain hits");
        }
    }

    public static KnowledgeSearchResult results(List<KnowledgeSearchHit> hits) {
        return new KnowledgeSearchResult(KnowledgeSearchStatus.RESULTS, hits);
    }

    public static KnowledgeSearchResult noReliableResult() {
        return new KnowledgeSearchResult(KnowledgeSearchStatus.NO_RELIABLE_RESULT, List.of());
    }
}
