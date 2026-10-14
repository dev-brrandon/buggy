package de.legitserver.assistant.knowledge.search;

import de.legitserver.assistant.knowledge.access.KnowledgeAccessContext;

/** Infrastructure-neutral Knowledge retrieval boundary. */
public interface KnowledgeSearchPort {
    KnowledgeSearchResult search(KnowledgeSearchQuery query, KnowledgeAccessContext accessContext);
}
