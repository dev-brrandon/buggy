package de.legitserver.assistant.buggy.personality;

/** Reference-only evidence types. Raw evidence content is deliberately not persisted in Personality. */
public enum BuggyPersonalityEvidenceType {
    OWNER_STATEMENT_REF,
    OWNER_KNOWLEDGE_REF,
    CONVERSATION_REF,
    TASK_REF,
    INCIDENT_REF,
    ACTIVITY_REF,
    SELF_REVIEW_REF
}
