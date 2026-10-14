package de.legitserver.assistant.buggy.personality;

/** Typed source classification for personality observations. */
public enum BuggyPersonalityObservationOrigin {
    EXPLICIT_OWNER,
    REPEATED_OWNER_PATTERN,
    OWNER_KNOWLEDGE_REFERENCE,
    WORKFLOW_OUTCOME,
    INCIDENT_OUTCOME,
    SELF_REVIEW
}
