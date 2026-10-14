package de.legitserver.assistant.buggy.supervision;

import de.legitserver.assistant.core.validation.CoreValidation;

/** Canonical B11 operation surface. Tool ids refer to the already-existing B5 catalog. */
public enum BuggySupervisorOperation {
    STATUS(BuggySupervisorAuthorityMode.READ, "tool.assistant.status"),
    HEALTH(BuggySupervisorAuthorityMode.READ, "tool.assistant.health"),
    METRICS(BuggySupervisorAuthorityMode.READ, "tool.assistant.metrics"),
    FAILURES(BuggySupervisorAuthorityMode.READ, "tool.assistant.failures"),
    INSPECT_CONFIGURATION(BuggySupervisorAuthorityMode.READ, "tool.assistant.inspect-configuration"),
    INSPECT_TOOLS(BuggySupervisorAuthorityMode.READ, "tool.assistant.inspect-tools"),
    INSPECT_KNOWLEDGE(BuggySupervisorAuthorityMode.READ, "tool.assistant.inspect-knowledge"),
    COORDINATE_TASK(BuggySupervisorAuthorityMode.COORDINATE, null),
    DELEGATE_WORK(BuggySupervisorAuthorityMode.COORDINATE, null),
    REVIEW_RESULT(BuggySupervisorAuthorityMode.COORDINATE, null),
    TAKE_INCIDENT_COORDINATION(BuggySupervisorAuthorityMode.COORDINATE, null),
    ESCALATE_PROBLEM(BuggySupervisorAuthorityMode.REQUEST_ONLY, null),
    DEGRADE(BuggySupervisorAuthorityMode.EXECUTE_BOUNDED, "tool.assistant.degrade"),
    RESTART(BuggySupervisorAuthorityMode.EXECUTE_BOUNDED, "tool.assistant.restart"),
    PAUSE(BuggySupervisorAuthorityMode.EXECUTE_BOUNDED, "tool.assistant.pause"),
    RESUME(BuggySupervisorAuthorityMode.EXECUTE_BOUNDED, "tool.assistant.resume"),
    OVERWRITE_IDENTITY(BuggySupervisorAuthorityMode.FORBIDDEN, null),
    REWRITE_PERSONALITY(BuggySupervisorAuthorityMode.FORBIDDEN, null),
    INCREASE_PERMISSIONS(BuggySupervisorAuthorityMode.FORBIDDEN, null),
    REPLACE_SECURITY_AUTHORITY(BuggySupervisorAuthorityMode.FORBIDDEN, null),
    PERFORM_OWNER_ONLY_DECISION(BuggySupervisorAuthorityMode.FORBIDDEN, null);

    private final BuggySupervisorAuthorityMode authorityMode;
    private final String toolId;

    BuggySupervisorOperation(BuggySupervisorAuthorityMode authorityMode, String toolId) {
        this.authorityMode = authorityMode;
        this.toolId = toolId == null ? null : CoreValidation.requireSafeToken(toolId, "toolId", 128);
    }

    public BuggySupervisorAuthorityMode authorityMode() {
        return authorityMode;
    }

    public String toolId() {
        return toolId;
    }

    public boolean isForbidden() {
        return authorityMode == BuggySupervisorAuthorityMode.FORBIDDEN;
    }
}
