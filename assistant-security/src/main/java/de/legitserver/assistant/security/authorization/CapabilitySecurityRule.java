package de.legitserver.assistant.security.authorization;

import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.core.validation.CoreValidation;
import de.legitserver.assistant.security.control.SecurityFeature;
import de.legitserver.assistant.security.control.SecurityKillSwitch;
import java.util.Set;

/** Security metadata required to execute a capability. */
public record CapabilitySecurityRule(
        AssistantCapabilityId capabilityId,
        String assistantPermission,
        Set<String> additionalPermissions,
        AuthorizationRiskLevel riskLevel,
        ActionNature actionNature,
        SecurityFeature feature,
        Set<SecurityKillSwitch> killSwitches) {

    public CapabilitySecurityRule {
        capabilityId = CoreValidation.requireNonNull(capabilityId, "capabilityId");
        assistantPermission = CoreValidation.requireSafeToken(assistantPermission, "assistantPermission", 160);
        additionalPermissions = immutablePermissions(additionalPermissions);
        riskLevel = CoreValidation.requireNonNull(riskLevel, "riskLevel");
        actionNature = CoreValidation.requireNonNull(actionNature, "actionNature");
        killSwitches = killSwitches == null ? Set.of() : Set.copyOf(killSwitches);
    }

    public Set<String> allCapabilityPermissions() {
        java.util.LinkedHashSet<String> permissions = new java.util.LinkedHashSet<>();
        permissions.add(assistantPermission);
        permissions.addAll(additionalPermissions);
        return Set.copyOf(permissions);
    }

    private static Set<String> immutablePermissions(Set<String> source) {
        if (source == null || source.isEmpty()) return Set.of();
        return source.stream()
                .map(value -> CoreValidation.requireSafeToken(value, "permission", 160))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
