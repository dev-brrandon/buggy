package de.legitserver.assistant.conversations.model;

/** Storage-visible sanitization state. Raw secrets are never represented as a state. */
public enum MessageRedactionState {
    NONE,
    REDACTED,
    QUARANTINED
}
