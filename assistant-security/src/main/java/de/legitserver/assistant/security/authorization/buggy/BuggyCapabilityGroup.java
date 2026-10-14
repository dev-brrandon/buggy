package de.legitserver.assistant.security.authorization.buggy;

/** Canonical Buggy V1 capability groups from BUGGYBOT-SPEC-V1. */
public enum BuggyCapabilityGroup {
    OPERATIONS("operations.*"),
    DIAGNOSTICS("diagnostics.*"),
    INFRASTRUCTURE("infrastructure.*"),
    SOURCE("source.*"),
    BUILD("build.*"),
    DEPLOYMENT("deployment.*"),
    DATABASE("database.*"),
    NETWORK("network.*"),
    SECURITY_READ("security.read.*"),
    SECURITY_REQUEST("security.request.*"),
    KNOWLEDGE("knowledge.*"),
    BUGS("bugs.*"),
    REQUESTS("requests.*"),
    ASSISTANT_SUPERVISOR("assistant.supervisor.*"),
    ASSISTANT_SELFDEVELOPMENT("assistant.selfdevelopment.*"),
    PANEL("panel.*"),
    DISCORD("discord.*"),
    MINECRAFT("minecraft.*"),
    WEB("web.*"),
    MAIL("mail.*");

    private final String externalName;

    BuggyCapabilityGroup(String externalName) {
        this.externalName = externalName;
    }

    public String externalName() {
        return externalName;
    }
}
