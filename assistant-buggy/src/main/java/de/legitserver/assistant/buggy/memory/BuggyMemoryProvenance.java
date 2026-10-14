package de.legitserver.assistant.buggy.memory;

/** Origin of a memory assertion; INFERRED is never equivalent to an explicit fact. */
public enum BuggyMemoryProvenance {
    EXPLICIT,
    OBSERVED,
    INFERRED,
    DERIVED,
    IMPORTED
}
