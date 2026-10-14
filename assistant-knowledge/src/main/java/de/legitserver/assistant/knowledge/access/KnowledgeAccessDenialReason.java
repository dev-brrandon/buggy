package de.legitserver.assistant.knowledge.access;

/** Fail-closed resource-level denial reasons for Knowledge access. */
public enum KnowledgeAccessDenialReason {
    NONE,
    QUERY_NOT_AUTHORIZED,
    DOMAIN_DENIED,
    CLASSIFICATION_DENIED,
    DOCUMENT_PERMISSION_DENIED
}
