package de.legitserver.assistant.security.authorization;

/** Whether a capability mutates state and therefore must be blocked in read-only emergency mode. */
public enum ActionNature {
    READ_ONLY,
    MUTATING
}
