package de.legitserver.assistant.bugs.model;

/** Canonical Phase-8 Bug lifecycle states. */
public enum BugStatus {
    OPEN,
    TRIAGED,
    INVESTIGATING,
    FIX_PLANNED,
    FIX_IN_PROGRESS,
    FIXED,
    VERIFIED,
    CLOSED,
    REJECTED,
    DUPLICATE
}
