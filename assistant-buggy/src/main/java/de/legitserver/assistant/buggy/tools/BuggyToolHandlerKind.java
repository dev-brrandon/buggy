package de.legitserver.assistant.buggy.tools;

/** Declares the integration boundary a concrete B5 handler implements. */
public enum BuggyToolHandlerKind {
    DIRECT_TYPED,
    REQUEST_CREATOR,
    BOUNDED_WEB,
    READ_ONLY_DATABASE
}
