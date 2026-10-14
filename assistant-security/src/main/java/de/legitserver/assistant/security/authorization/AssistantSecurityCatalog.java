package de.legitserver.assistant.security.authorization;

import de.legitserver.assistant.core.assistant.AssistantCapabilities;
import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.security.authorization.buggy.BuggySecurityCatalog;
import de.legitserver.assistant.security.control.SecurityFeature;
import de.legitserver.assistant.security.control.SecurityKillSwitch;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Canonical Foundation plus approved product capability-to-NetworkCore security mapping. */
public final class AssistantSecurityCatalog {
    public static final String NETWORKCORE_DIAGNOSTICS = "networkcore.diagnostics";
    public static final String NETWORKCORE_DOCS_TECHNICAL = "networkcore.docs.technical";

    private static final Map<AssistantId, String> USE_PERMISSIONS = Map.of(
            AssistantId.KAESE, "networkcore.assistant.use.kaese",
            AssistantId.BYTE, "networkcore.assistant.use.byte",
            AssistantId.BUGGY, "networkcore.assistant.use.buggy");

    private static final Map<AssistantCapabilityId, CapabilitySecurityRule> FOUNDATION_RULES = Map.ofEntries(
            entry(AssistantCapabilities.KNOWLEDGE_QUERY, "networkcore.assistant.knowledge.query", AuthorizationRiskLevel.LOW, ActionNature.READ_ONLY, null, Set.of()),
            entry(AssistantCapabilities.BUG_CREATE, "networkcore.assistant.bug.create", AuthorizationRiskLevel.LOW, ActionNature.MUTATING, null, Set.of()),
            entry(AssistantCapabilities.BUG_READ, "networkcore.assistant.bug.read", AuthorizationRiskLevel.MEDIUM, ActionNature.READ_ONLY, null, Set.of()),
            entry(AssistantCapabilities.BUG_UPDATE, "networkcore.assistant.bug.update", AuthorizationRiskLevel.HIGH, ActionNature.MUTATING, null, Set.of()),
            entry(AssistantCapabilities.INCIDENT_READ, "networkcore.assistant.incident.read", AuthorizationRiskLevel.HIGH, ActionNature.READ_ONLY, null, Set.of()),
            entry(AssistantCapabilities.SOURCE_READ, "networkcore.assistant.source.read", Set.of(NETWORKCORE_DOCS_TECHNICAL), AuthorizationRiskLevel.HIGH, ActionNature.READ_ONLY, SecurityFeature.SOURCE_READ, Set.of()),
            entry(AssistantCapabilities.DIAGNOSTICS_READ, "networkcore.assistant.diagnostics.read", Set.of(NETWORKCORE_DIAGNOSTICS), AuthorizationRiskLevel.HIGH, ActionNature.READ_ONLY, SecurityFeature.DIAGNOSTICS, Set.of(SecurityKillSwitch.DIAGNOSTICS_OFF)),
            entry(AssistantCapabilities.SUPERVISOR_OBSERVE, "networkcore.assistant.supervisor.observe", AuthorizationRiskLevel.HIGH, ActionNature.READ_ONLY, SecurityFeature.SUPERVISOR, Set.of()),
            entry(AssistantCapabilities.SUPERVISOR_REPORT, "networkcore.assistant.supervisor.report", AuthorizationRiskLevel.MEDIUM, ActionNature.MUTATING, SecurityFeature.SUPERVISOR, Set.of()),
            entry(AssistantCapabilities.SELF_HEAL_LOW, "networkcore.assistant.selfheal.low", AuthorizationRiskLevel.LOW, ActionNature.MUTATING, SecurityFeature.SELF_HEALING, Set.of(SecurityKillSwitch.ALL_TOOLS_OFF, SecurityKillSwitch.SELF_HEALING_OFF)),
            entry(AssistantCapabilities.EXTERNAL_REQUEST, "networkcore.assistant.external.request", AuthorizationRiskLevel.HIGH, ActionNature.MUTATING, SecurityFeature.EXTERNAL_ANALYSIS, Set.of(SecurityKillSwitch.EXTERNAL_ANALYSIS_OFF)),
            entry(AssistantCapabilities.CHANGE_REQUEST, "networkcore.assistant.change.request", AuthorizationRiskLevel.HIGH, ActionNature.MUTATING, null, Set.of()),
            entry(AssistantCapabilities.WRITE_REQUEST, "networkcore.assistant.write.request", AuthorizationRiskLevel.CRITICAL, ActionNature.MUTATING, SecurityFeature.WRITE_ACTIONS, Set.of(SecurityKillSwitch.ALL_TOOLS_OFF, SecurityKillSwitch.WRITE_ACTIONS_OFF)),
            entry(AssistantCapabilities.APPROVAL_REVIEW, "networkcore.assistant.approval.review", AuthorizationRiskLevel.HIGH, ActionNature.MUTATING, null, Set.of()),
            entry(AssistantCapabilities.APPROVAL_CRITICAL, "networkcore.assistant.approval.critical", AuthorizationRiskLevel.CRITICAL, ActionNature.MUTATING, null, Set.of()),
            entry(AssistantCapabilities.ADMIN_VIEW, "networkcore.assistant.admin.view", AuthorizationRiskLevel.HIGH, ActionNature.READ_ONLY, null, Set.of()),
            entry(AssistantCapabilities.ADMIN_MANAGE, "networkcore.assistant.admin.manage", AuthorizationRiskLevel.CRITICAL, ActionNature.MUTATING, null, Set.of()));

    private static final Map<AssistantCapabilityId, CapabilitySecurityRule> RULES = mergeRules();

    private AssistantSecurityCatalog() {}

    public static String assistantUsePermission(AssistantId assistantId) {
        String permission = USE_PERMISSIONS.get(assistantId);
        if (permission == null) throw new IllegalArgumentException("Unknown assistantId: " + assistantId);
        return permission;
    }

    public static Optional<String> channelPermission(AssistantId assistantId, Origin origin) {
        if (assistantId != AssistantId.BUGGY || origin == null) return Optional.empty();
        return switch (origin) {
            case PANEL -> Optional.of("networkcore.assistant.buggy.channel.panel");
            case MINECRAFT -> Optional.of("networkcore.assistant.buggy.channel.minecraft");
            case DISCORD -> Optional.of("networkcore.assistant.buggy.channel.discord");
            case INTERNAL, SYSTEM -> Optional.empty();
        };
    }

    public static Optional<CapabilitySecurityRule> rule(AssistantCapabilityId capabilityId) {
        return Optional.ofNullable(RULES.get(capabilityId));
    }

    public static Map<AssistantCapabilityId, CapabilitySecurityRule> rules() {
        return RULES;
    }

    private static Map<AssistantCapabilityId, CapabilitySecurityRule> mergeRules() {
        LinkedHashMap<AssistantCapabilityId, CapabilitySecurityRule> merged = new LinkedHashMap<>(FOUNDATION_RULES);
        for (var entry : BuggySecurityCatalog.rules().entrySet()) {
            if (merged.put(entry.getKey(), entry.getValue()) != null) {
                throw new IllegalStateException("Duplicate security capability rule: " + entry.getKey());
            }
        }
        return Map.copyOf(merged);
    }

    private static Map.Entry<AssistantCapabilityId, CapabilitySecurityRule> entry(
            AssistantCapabilityId id,
            String permission,
            AuthorizationRiskLevel risk,
            ActionNature nature,
            SecurityFeature feature,
            Set<SecurityKillSwitch> switches) {
        return entry(id, permission, Set.of(), risk, nature, feature, switches);
    }

    private static Map.Entry<AssistantCapabilityId, CapabilitySecurityRule> entry(
            AssistantCapabilityId id,
            String permission,
            Set<String> additionalPermissions,
            AuthorizationRiskLevel risk,
            ActionNature nature,
            SecurityFeature feature,
            Set<SecurityKillSwitch> switches) {
        return Map.entry(id, new CapabilitySecurityRule(id, permission, additionalPermissions, risk, nature, feature, switches));
    }
}
