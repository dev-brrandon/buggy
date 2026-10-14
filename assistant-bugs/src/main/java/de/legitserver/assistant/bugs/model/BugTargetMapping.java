package de.legitserver.assistant.bugs.model;

import de.legitserver.assistant.core.validation.CoreValidation;

/** Safe references linking a Bug to component, version, build, server and environment identities. */
public record BugTargetMapping(
        String componentId,
        String pluginVersion,
        String buildVersion,
        String serverId,
        String environment) {

    public BugTargetMapping {
        componentId = CoreValidation.optionalSafeToken(componentId, "componentId", 128);
        pluginVersion = CoreValidation.optionalSafeToken(pluginVersion, "pluginVersion", 128);
        buildVersion = CoreValidation.optionalSafeToken(buildVersion, "buildVersion", 128);
        serverId = CoreValidation.optionalSafeToken(serverId, "serverId", 128);
        environment = CoreValidation.optionalSafeToken(environment, "environment", 64);
    }

    public static BugTargetMapping empty() {
        return new BugTargetMapping(null, null, null, null, null);
    }

    public boolean isEmpty() {
        return componentId == null
                && pluginVersion == null
                && buildVersion == null
                && serverId == null
                && environment == null;
    }
}
