package de.legitserver.assistant.runtime.execution;

/** Safe runtime failure taxonomy; handler exception messages never cross this boundary. */
public enum ToolFailureCode {
    TOOL_NOT_FOUND,
    AUTHORIZATION_DENIED,
    INPUT_SCHEMA_INVALID,
    INPUT_CLASSIFICATION_DENIED,
    APPROVAL_REQUIRED,
    APPROVAL_INVALID,
    APPROVAL_EXPIRED,
    CONCURRENCY_LIMIT,
    TIMEOUT,
    EXECUTION_FAILED,
    OUTPUT_SCHEMA_INVALID,
    OUTPUT_CLASSIFICATION_DENIED,
    AUDIT_UNAVAILABLE
}
