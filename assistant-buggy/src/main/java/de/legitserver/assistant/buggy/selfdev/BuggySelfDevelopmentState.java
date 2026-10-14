package de.legitserver.assistant.buggy.selfdev;

/** Canonical BUGGYBOT-SPEC-V1 B7 lifecycle states. */
public enum BuggySelfDevelopmentState {
    OBSERVED,
    PROPOSAL,
    IMPLEMENTING,
    TESTING,
    REJECTED_BY_BUGGY,
    WAITING_FOR_OWNER_APPROVAL,
    APPROVED,
    DEPLOYED,
    VERIFIED,
    ROLLED_BACK
}
