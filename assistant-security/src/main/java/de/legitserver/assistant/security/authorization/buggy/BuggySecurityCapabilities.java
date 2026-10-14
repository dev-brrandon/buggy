package de.legitserver.assistant.security.authorization.buggy;

import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import java.util.Set;

/** Atomic Buggy V1 capabilities. Every ID is authorized through the existing Foundation security pipeline. */
public final class BuggySecurityCapabilities {
    public static final AssistantCapabilityId OPERATIONS_EXECUTE_LOW = id("assistant.buggy.operations.execute.low");
    public static final AssistantCapabilityId OPERATIONS_MUTATE_LOW = id("assistant.buggy.operations.mutate.low");

    public static final AssistantCapabilityId DIAGNOSTICS_READ = id("assistant.buggy.diagnostics.read");
    public static final AssistantCapabilityId DIAGNOSTICS_EXECUTE = id("assistant.buggy.diagnostics.execute");

    public static final AssistantCapabilityId INFRASTRUCTURE_READ = id("assistant.buggy.infrastructure.read");
    public static final AssistantCapabilityId INFRASTRUCTURE_RECOVERY_LOW = id("assistant.buggy.infrastructure.recovery.low");
    public static final AssistantCapabilityId INFRASTRUCTURE_REQUEST = id("assistant.buggy.infrastructure.request");

    public static final AssistantCapabilityId SOURCE_READ = id("assistant.buggy.source.read");
    public static final AssistantCapabilityId SOURCE_WORKSPACE_MUTATE = id("assistant.buggy.source.workspace.mutate");

    public static final AssistantCapabilityId BUILD_EXECUTE = id("assistant.buggy.build.execute");

    public static final AssistantCapabilityId DEPLOYMENT_READ = id("assistant.buggy.deployment.read");
    public static final AssistantCapabilityId DEPLOYMENT_NONPROD_EXECUTE = id("assistant.buggy.deployment.nonprod.execute");
    public static final AssistantCapabilityId DEPLOYMENT_PRODUCTION_REQUEST = id("assistant.buggy.deployment.production.request");

    public static final AssistantCapabilityId DATABASE_READ = id("assistant.buggy.database.read");
    public static final AssistantCapabilityId DATABASE_WRITE_REQUEST = id("assistant.buggy.database.write.request");

    public static final AssistantCapabilityId NETWORK_READ = id("assistant.buggy.network.read");
    public static final AssistantCapabilityId NETWORK_MUTATE_REQUEST = id("assistant.buggy.network.mutate.request");

    public static final AssistantCapabilityId SECURITY_READ = id("assistant.buggy.security.read");
    public static final AssistantCapabilityId SECURITY_REQUEST = id("assistant.buggy.security.request");

    public static final AssistantCapabilityId KNOWLEDGE_READ = id("assistant.buggy.knowledge.read");
    public static final AssistantCapabilityId KNOWLEDGE_DRAFT_MUTATE = id("assistant.buggy.knowledge.draft.mutate");

    public static final AssistantCapabilityId BUGS_READ = id("assistant.buggy.bugs.read");
    public static final AssistantCapabilityId BUGS_MUTATE = id("assistant.buggy.bugs.mutate");

    public static final AssistantCapabilityId REQUESTS_READ = id("assistant.buggy.requests.read");
    public static final AssistantCapabilityId REQUESTS_CREATE = id("assistant.buggy.requests.create");

    public static final AssistantCapabilityId SUPERVISOR_READ = id("assistant.buggy.supervisor.read");
    public static final AssistantCapabilityId SUPERVISOR_EXECUTE = id("assistant.buggy.supervisor.execute");

    public static final AssistantCapabilityId SELFDEVELOPMENT_WORKSPACE_MUTATE = id("assistant.buggy.selfdevelopment.workspace.mutate");
    public static final AssistantCapabilityId SELFDEVELOPMENT_EXECUTE = id("assistant.buggy.selfdevelopment.execute");

    public static final AssistantCapabilityId PANEL_READ = id("assistant.buggy.panel.read");
    public static final AssistantCapabilityId PANEL_EXECUTE = id("assistant.buggy.panel.execute");

    public static final AssistantCapabilityId DISCORD_READ = id("assistant.buggy.discord.read");
    public static final AssistantCapabilityId DISCORD_EXECUTE_LOW = id("assistant.buggy.discord.execute.low");

    public static final AssistantCapabilityId MINECRAFT_READ = id("assistant.buggy.minecraft.read");
    public static final AssistantCapabilityId MINECRAFT_EXECUTE_LOW = id("assistant.buggy.minecraft.execute.low");

    public static final AssistantCapabilityId WEB_READ = id("assistant.buggy.web.read");
    public static final AssistantCapabilityId WEB_EXECUTE = id("assistant.buggy.web.execute");

    public static final AssistantCapabilityId MAIL_READ = id("assistant.buggy.mail.read");
    public static final AssistantCapabilityId MAIL_SEND_REQUEST = id("assistant.buggy.mail.send.request");

    private static final Set<AssistantCapabilityId> ALL = Set.of(
            OPERATIONS_EXECUTE_LOW,
            OPERATIONS_MUTATE_LOW,
            DIAGNOSTICS_READ,
            DIAGNOSTICS_EXECUTE,
            INFRASTRUCTURE_READ,
            INFRASTRUCTURE_RECOVERY_LOW,
            INFRASTRUCTURE_REQUEST,
            SOURCE_READ,
            SOURCE_WORKSPACE_MUTATE,
            BUILD_EXECUTE,
            DEPLOYMENT_READ,
            DEPLOYMENT_NONPROD_EXECUTE,
            DEPLOYMENT_PRODUCTION_REQUEST,
            DATABASE_READ,
            DATABASE_WRITE_REQUEST,
            NETWORK_READ,
            NETWORK_MUTATE_REQUEST,
            SECURITY_READ,
            SECURITY_REQUEST,
            KNOWLEDGE_READ,
            KNOWLEDGE_DRAFT_MUTATE,
            BUGS_READ,
            BUGS_MUTATE,
            REQUESTS_READ,
            REQUESTS_CREATE,
            SUPERVISOR_READ,
            SUPERVISOR_EXECUTE,
            SELFDEVELOPMENT_WORKSPACE_MUTATE,
            SELFDEVELOPMENT_EXECUTE,
            PANEL_READ,
            PANEL_EXECUTE,
            DISCORD_READ,
            DISCORD_EXECUTE_LOW,
            MINECRAFT_READ,
            MINECRAFT_EXECUTE_LOW,
            WEB_READ,
            WEB_EXECUTE,
            MAIL_READ,
            MAIL_SEND_REQUEST);

    private BuggySecurityCapabilities() {}

    public static Set<AssistantCapabilityId> all() {
        return ALL;
    }

    private static AssistantCapabilityId id(String value) {
        return new AssistantCapabilityId(value);
    }
}
