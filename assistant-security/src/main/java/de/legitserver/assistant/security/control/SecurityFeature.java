package de.legitserver.assistant.security.control;

/** High-risk or optional capability groups controlled independently at runtime. */
public enum SecurityFeature {
    SOURCE_READ,
    DIAGNOSTICS,
    EXTERNAL_ANALYSIS,
    SELF_HEALING,
    WRITE_ACTIONS,
    SUPERVISOR
}
