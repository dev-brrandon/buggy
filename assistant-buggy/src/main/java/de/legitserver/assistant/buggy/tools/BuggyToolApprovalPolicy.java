package de.legitserver.assistant.buggy.tools;

/** Product-level execution intent layered on top of the Foundation authorization and risk gates. */
public enum BuggyToolApprovalPolicy {
    AUTONOMOUS_LOW,
    FOUNDATION_POLICY_GATED,
    REQUEST_ONLY,
    OWNER_ONLY
}
