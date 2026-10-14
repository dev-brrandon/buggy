package de.legitserver.assistant.buggy.knowledge;

import de.legitserver.assistant.knowledge.model.KnowledgeDomain;

/** Broad Buggy topic taxonomy mapped onto the frozen Foundation Knowledge domains. */
public enum BuggyKnowledgeTopic {
    ARCHITECTURE_DECISIONS(KnowledgeDomain.MINECRAFT_ARCHITECTURE),
    NETWORKCORE_PLATFORM_SYSTEM(KnowledgeDomain.MINECRAFT_ARCHITECTURE),
    MINECRAFT_PLUGINS_DEPENDENCIES(KnowledgeDomain.MINECRAFT_ARCHITECTURE),
    SERVER_NETWORK_ARCHITECTURE(KnowledgeDomain.SERVER_OPERATIONS),
    SOURCE_BUILD_HISTORY(KnowledgeDomain.DEPLOYMENTS),
    DEPLOYMENTS_RELEASES(KnowledgeDomain.DEPLOYMENTS),
    BUGS_INCIDENTS_POSTMORTEMS(KnowledgeDomain.BUG_REPORTING),
    DATABASES_MIGRATIONS(KnowledgeDomain.SERVER_OPERATIONS),
    PANEL_INTERNAL_APIS(KnowledgeDomain.PANEL),
    DISCORD_WEB_MAIL(KnowledgeDomain.SERVER_OPERATIONS),
    SECURITY_ARCHITECTURE_POLICIES(KnowledgeDomain.SECURITY),
    OPERATIONS_RECOVERY_RUNBOOKS(KnowledgeDomain.SERVER_OPERATIONS),
    MONITORING_OBSERVABILITY(KnowledgeDomain.TROUBLESHOOTING),
    HISTORICAL_DECISIONS_RATIONALE(KnowledgeDomain.MINECRAFT_ARCHITECTURE),
    SELF_DEVELOPMENT_HISTORY(KnowledgeDomain.TROUBLESHOOTING),
    FUTURE_ASSISTANT_OPERATIONS(KnowledgeDomain.SERVER_OPERATIONS),
    OWNER_CONTEXT(KnowledgeDomain.STAFF_PROCEDURES);

    private final KnowledgeDomain foundationDomain;

    BuggyKnowledgeTopic(KnowledgeDomain foundationDomain) {
        this.foundationDomain = foundationDomain;
    }

    public KnowledgeDomain foundationDomain() {
        return foundationDomain;
    }
}
