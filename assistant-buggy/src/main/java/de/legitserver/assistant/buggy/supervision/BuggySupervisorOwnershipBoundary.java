package de.legitserver.assistant.buggy.supervision;

/** Explicit independence boundary for future assistants supervised by Buggy. */
public record BuggySupervisorOwnershipBoundary(
        String identityOwner,
        String personalityOwner,
        String permissionAuthority,
        String securityAuthority,
        String ownerOnlyDecisionAuthority,
        boolean targetMayDisagree,
        boolean targetMustReauthorizeDelegatedWork) {
    public static BuggySupervisorOwnershipBoundary canonical() {
        return new BuggySupervisorOwnershipBoundary(
                "TARGET_ASSISTANT",
                "TARGET_ASSISTANT",
                "NETWORKCORE",
                "NETWORKCORE",
                "OWNER",
                true,
                true);
    }
}
