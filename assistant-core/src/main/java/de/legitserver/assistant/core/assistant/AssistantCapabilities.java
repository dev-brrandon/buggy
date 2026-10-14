package de.legitserver.assistant.core.assistant;

import java.util.Set;

/** Phase-0 approved initial capability namespace. */
public final class AssistantCapabilities {

    public static final AssistantCapabilityId KNOWLEDGE_QUERY = id("assistant.knowledge.query");
    public static final AssistantCapabilityId BUG_CREATE = id("assistant.bug.create");
    public static final AssistantCapabilityId BUG_READ = id("assistant.bug.read");
    public static final AssistantCapabilityId BUG_UPDATE = id("assistant.bug.update");
    public static final AssistantCapabilityId INCIDENT_READ = id("assistant.incident.read");
    public static final AssistantCapabilityId SOURCE_READ = id("assistant.source.read");
    public static final AssistantCapabilityId DIAGNOSTICS_READ = id("assistant.diagnostics.read");
    public static final AssistantCapabilityId SUPERVISOR_OBSERVE = id("assistant.supervisor.observe");
    public static final AssistantCapabilityId SUPERVISOR_REPORT = id("assistant.supervisor.report");
    public static final AssistantCapabilityId SELF_HEAL_LOW = id("assistant.selfheal.low");
    public static final AssistantCapabilityId EXTERNAL_REQUEST = id("assistant.external.request");
    public static final AssistantCapabilityId CHANGE_REQUEST = id("assistant.change.request");
    public static final AssistantCapabilityId WRITE_REQUEST = id("assistant.write.request");
    public static final AssistantCapabilityId APPROVAL_REVIEW = id("assistant.approval.review");
    public static final AssistantCapabilityId APPROVAL_CRITICAL = id("assistant.approval.critical");
    public static final AssistantCapabilityId ADMIN_VIEW = id("assistant.admin.view");
    public static final AssistantCapabilityId ADMIN_MANAGE = id("assistant.admin.manage");

    private static final Set<AssistantCapabilityId> INITIAL =
            Set.of(
                    KNOWLEDGE_QUERY,
                    BUG_CREATE,
                    BUG_READ,
                    BUG_UPDATE,
                    INCIDENT_READ,
                    SOURCE_READ,
                    DIAGNOSTICS_READ,
                    SUPERVISOR_OBSERVE,
                    SUPERVISOR_REPORT,
                    SELF_HEAL_LOW,
                    EXTERNAL_REQUEST,
                    CHANGE_REQUEST,
                    WRITE_REQUEST,
                    APPROVAL_REVIEW,
                    APPROVAL_CRITICAL,
                    ADMIN_VIEW,
                    ADMIN_MANAGE);

    private AssistantCapabilities() {}

    public static Set<AssistantCapabilityId> initialNamespace() {
        return INITIAL;
    }

    private static AssistantCapabilityId id(String value) {
        return new AssistantCapabilityId(value);
    }
}
