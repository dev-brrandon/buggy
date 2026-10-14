package de.legitserver.assistant.observability.audit;

/** Canonical Phase-6 audit action categories. */
public enum AuditAction {
    REQUEST_PIPELINE,
    SENSITIVE_AUTHORIZATION_DENIAL,
    BUG_MUTATION,
    INCIDENT_ACCESS,
    SOURCE_ACCESS,
    DIAGNOSTICS_ACCESS,
    SUPERVISOR_READ,
    SELF_HEALING,
    APPROVAL,
    EXTERNAL_ANALYSIS,
    WRITE_REQUEST,
    SECURITY_CHANGE,
    TOOL_INVOCATION,
    BUGGY_AUTOMATION
}
