package de.legitserver.assistant.supervisor.selfhealing;

/** Explicit anti-loop / eligibility suppression reason. */
public enum SelfHealingSuppressionReason {
    TOOL_NOT_REGISTERED,
    RISK_NOT_LOW,
    NOT_SELF_HEALING_ELIGIBLE,
    CAPABILITY_NOT_SELF_HEAL,
    AUDIT_NOT_FAIL_CLOSED,
    COOLDOWN,
    WINDOW_BUDGET_EXCEEDED,
    MAX_ATTEMPTS_EXCEEDED,
    SAME_TARGET_SUPPRESSED
}
