package de.legitserver.assistant.buggy.controlcenter;

import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.security.authorization.buggy.BuggySecurityCapabilities;

/** Server-side authorization mapping for every B8 surface. Browser-provided permissions are never accepted. */
public enum BuggyControlCenterSurface {
    OVERVIEW(BuggySecurityCapabilities.PANEL_READ),
    IDENTITY(BuggySecurityCapabilities.PANEL_READ),
    PERSONALITY(BuggySecurityCapabilities.PANEL_READ),
    TASKS(BuggySecurityCapabilities.PANEL_READ),
    ACTIVITY(BuggySecurityCapabilities.PANEL_READ),
    APPROVALS(BuggySecurityCapabilities.REQUESTS_READ),
    INCIDENTS(BuggySecurityCapabilities.BUGS_READ),
    KNOWLEDGE(BuggySecurityCapabilities.KNOWLEDGE_READ),
    SELF_DEVELOPMENT(BuggySecurityCapabilities.PANEL_READ),
    AUTOMATIONS(BuggySecurityCapabilities.PANEL_READ),
    TOOLS(BuggySecurityCapabilities.PANEL_READ),
    SECURITY(BuggySecurityCapabilities.SECURITY_READ),
    AUDIT(BuggySecurityCapabilities.PANEL_READ);

    private final AssistantCapabilityId capabilityId;
    BuggyControlCenterSurface(AssistantCapabilityId capabilityId) { this.capabilityId = capabilityId; }
    public AssistantCapabilityId capabilityId() { return capabilityId; }
}
