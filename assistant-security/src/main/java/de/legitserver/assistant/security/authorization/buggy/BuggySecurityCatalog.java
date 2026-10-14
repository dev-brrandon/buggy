package de.legitserver.assistant.security.authorization.buggy;

import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.security.authorization.ActionNature;
import de.legitserver.assistant.security.authorization.AuthorizationRiskLevel;
import de.legitserver.assistant.security.authorization.CapabilitySecurityRule;
import de.legitserver.assistant.security.control.SecurityFeature;
import de.legitserver.assistant.security.control.SecurityKillSwitch;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** B3 extension of the existing Foundation capability/security catalog for Buggy V1. */
public final class BuggySecurityCatalog {
    private static final Set<SecurityKillSwitch> ALL_TOOLS = Set.of(SecurityKillSwitch.ALL_TOOLS_OFF);
    private static final Set<SecurityKillSwitch> WRITE_SWITCHES = Set.of(
            SecurityKillSwitch.ALL_TOOLS_OFF, SecurityKillSwitch.WRITE_ACTIONS_OFF);
    private static final Set<SecurityKillSwitch> DIAGNOSTIC_SWITCHES = Set.of(
            SecurityKillSwitch.ALL_TOOLS_OFF, SecurityKillSwitch.DIAGNOSTICS_OFF);
    private static final Set<SecurityKillSwitch> RECOVERY_SWITCHES = Set.of(
            SecurityKillSwitch.ALL_TOOLS_OFF, SecurityKillSwitch.SELF_HEALING_OFF);
    private static final Map<AssistantCapabilityId, CapabilitySecurityRule> RULES = createRules();

    private BuggySecurityCatalog() {}

    public static Map<AssistantCapabilityId, CapabilitySecurityRule> rules() {
        return RULES;
    }

    public static Set<String> requiredNetworkCorePermissions() {
        java.util.LinkedHashSet<String> permissions = new java.util.LinkedHashSet<>();
        permissions.add("networkcore.assistant.use.buggy");
        for (CapabilitySecurityRule rule : RULES.values()) {
            permissions.addAll(rule.allCapabilityPermissions());
        }
        return Set.copyOf(permissions);
    }

    private static Map<AssistantCapabilityId, CapabilitySecurityRule> createRules() {
        LinkedHashMap<AssistantCapabilityId, CapabilitySecurityRule> rules = new LinkedHashMap<>();

        add(rules, BuggySecurityCapabilities.OPERATIONS_EXECUTE_LOW, "operations.execute.low", AuthorizationRiskLevel.LOW,
                ActionNature.MUTATING, SecurityFeature.SELF_HEALING, RECOVERY_SWITCHES);
        add(rules, BuggySecurityCapabilities.OPERATIONS_MUTATE_LOW, "operations.mutate.low", AuthorizationRiskLevel.LOW,
                ActionNature.MUTATING, SecurityFeature.WRITE_ACTIONS, WRITE_SWITCHES);

        add(rules, BuggySecurityCapabilities.DIAGNOSTICS_READ, "diagnostics.read", AuthorizationRiskLevel.LOW,
                ActionNature.READ_ONLY, SecurityFeature.DIAGNOSTICS, DIAGNOSTIC_SWITCHES);
        add(rules, BuggySecurityCapabilities.DIAGNOSTICS_EXECUTE, "diagnostics.execute", AuthorizationRiskLevel.MEDIUM,
                ActionNature.READ_ONLY, SecurityFeature.DIAGNOSTICS, DIAGNOSTIC_SWITCHES);

        add(rules, BuggySecurityCapabilities.INFRASTRUCTURE_READ, "infrastructure.read", AuthorizationRiskLevel.MEDIUM,
                ActionNature.READ_ONLY, null, ALL_TOOLS);
        add(rules, BuggySecurityCapabilities.INFRASTRUCTURE_RECOVERY_LOW, "infrastructure.recovery.low", AuthorizationRiskLevel.LOW,
                ActionNature.MUTATING, SecurityFeature.SELF_HEALING, RECOVERY_SWITCHES);
        add(rules, BuggySecurityCapabilities.INFRASTRUCTURE_REQUEST, "infrastructure.request", AuthorizationRiskLevel.MEDIUM,
                ActionNature.MUTATING, null, ALL_TOOLS);

        add(rules, BuggySecurityCapabilities.SOURCE_READ, "source.read", AuthorizationRiskLevel.HIGH,
                ActionNature.READ_ONLY, SecurityFeature.SOURCE_READ, ALL_TOOLS);
        add(rules, BuggySecurityCapabilities.SOURCE_WORKSPACE_MUTATE, "source.workspace.mutate", AuthorizationRiskLevel.MEDIUM,
                ActionNature.MUTATING, SecurityFeature.WRITE_ACTIONS, WRITE_SWITCHES);

        add(rules, BuggySecurityCapabilities.BUILD_EXECUTE, "build.execute", AuthorizationRiskLevel.LOW,
                ActionNature.READ_ONLY, null, ALL_TOOLS);

        add(rules, BuggySecurityCapabilities.DEPLOYMENT_READ, "deployment.read", AuthorizationRiskLevel.MEDIUM,
                ActionNature.READ_ONLY, null, ALL_TOOLS);
        add(rules, BuggySecurityCapabilities.DEPLOYMENT_NONPROD_EXECUTE, "deployment.nonprod.execute", AuthorizationRiskLevel.MEDIUM,
                ActionNature.MUTATING, SecurityFeature.WRITE_ACTIONS, WRITE_SWITCHES);
        add(rules, BuggySecurityCapabilities.DEPLOYMENT_PRODUCTION_REQUEST, "deployment.production.request", AuthorizationRiskLevel.MEDIUM,
                ActionNature.MUTATING, null, ALL_TOOLS);

        add(rules, BuggySecurityCapabilities.DATABASE_READ, "database.read", AuthorizationRiskLevel.HIGH,
                ActionNature.READ_ONLY, null, ALL_TOOLS);
        add(rules, BuggySecurityCapabilities.DATABASE_WRITE_REQUEST, "database.write.request", AuthorizationRiskLevel.MEDIUM,
                ActionNature.MUTATING, null, ALL_TOOLS);

        add(rules, BuggySecurityCapabilities.NETWORK_READ, "network.read", AuthorizationRiskLevel.HIGH,
                ActionNature.READ_ONLY, null, ALL_TOOLS);
        add(rules, BuggySecurityCapabilities.NETWORK_MUTATE_REQUEST, "network.mutate.request", AuthorizationRiskLevel.MEDIUM,
                ActionNature.MUTATING, null, ALL_TOOLS);

        add(rules, BuggySecurityCapabilities.SECURITY_READ, "security.read", AuthorizationRiskLevel.HIGH,
                ActionNature.READ_ONLY, null, ALL_TOOLS);
        add(rules, BuggySecurityCapabilities.SECURITY_REQUEST, "security.request", AuthorizationRiskLevel.MEDIUM,
                ActionNature.MUTATING, null, ALL_TOOLS);

        add(rules, BuggySecurityCapabilities.KNOWLEDGE_READ, "knowledge.read", AuthorizationRiskLevel.LOW,
                ActionNature.READ_ONLY, null, ALL_TOOLS);
        add(rules, BuggySecurityCapabilities.KNOWLEDGE_DRAFT_MUTATE, "knowledge.draft.mutate", AuthorizationRiskLevel.LOW,
                ActionNature.MUTATING, SecurityFeature.WRITE_ACTIONS, WRITE_SWITCHES);

        add(rules, BuggySecurityCapabilities.BUGS_READ, "bugs.read", AuthorizationRiskLevel.LOW,
                ActionNature.READ_ONLY, null, ALL_TOOLS);
        add(rules, BuggySecurityCapabilities.BUGS_MUTATE, "bugs.mutate", AuthorizationRiskLevel.MEDIUM,
                ActionNature.MUTATING, SecurityFeature.WRITE_ACTIONS, WRITE_SWITCHES);

        add(rules, BuggySecurityCapabilities.REQUESTS_READ, "requests.read", AuthorizationRiskLevel.LOW,
                ActionNature.READ_ONLY, null, ALL_TOOLS);
        add(rules, BuggySecurityCapabilities.REQUESTS_CREATE, "requests.create", AuthorizationRiskLevel.LOW,
                ActionNature.MUTATING, null, ALL_TOOLS);

        add(rules, BuggySecurityCapabilities.SUPERVISOR_READ, "supervisor.read", AuthorizationRiskLevel.MEDIUM,
                ActionNature.READ_ONLY, SecurityFeature.SUPERVISOR, ALL_TOOLS);
        add(rules, BuggySecurityCapabilities.SUPERVISOR_EXECUTE, "supervisor.execute", AuthorizationRiskLevel.MEDIUM,
                ActionNature.MUTATING, SecurityFeature.SUPERVISOR, ALL_TOOLS);

        add(rules, BuggySecurityCapabilities.SELFDEVELOPMENT_WORKSPACE_MUTATE, "selfdevelopment.workspace.mutate", AuthorizationRiskLevel.LOW,
                ActionNature.MUTATING, SecurityFeature.WRITE_ACTIONS, WRITE_SWITCHES);
        add(rules, BuggySecurityCapabilities.SELFDEVELOPMENT_EXECUTE, "selfdevelopment.execute", AuthorizationRiskLevel.MEDIUM,
                ActionNature.READ_ONLY, null, ALL_TOOLS);

        add(rules, BuggySecurityCapabilities.PANEL_READ, "panel.read", AuthorizationRiskLevel.MEDIUM,
                ActionNature.READ_ONLY, null, ALL_TOOLS);
        add(rules, BuggySecurityCapabilities.PANEL_EXECUTE, "panel.execute", AuthorizationRiskLevel.MEDIUM,
                ActionNature.MUTATING, SecurityFeature.WRITE_ACTIONS, WRITE_SWITCHES);

        add(rules, BuggySecurityCapabilities.DISCORD_READ, "discord.read", AuthorizationRiskLevel.LOW,
                ActionNature.READ_ONLY, null, ALL_TOOLS);
        add(rules, BuggySecurityCapabilities.DISCORD_EXECUTE_LOW, "discord.execute.low", AuthorizationRiskLevel.LOW,
                ActionNature.MUTATING, SecurityFeature.WRITE_ACTIONS, WRITE_SWITCHES);

        add(rules, BuggySecurityCapabilities.MINECRAFT_READ, "minecraft.read", AuthorizationRiskLevel.LOW,
                ActionNature.READ_ONLY, null, ALL_TOOLS);
        add(rules, BuggySecurityCapabilities.MINECRAFT_EXECUTE_LOW, "minecraft.execute.low", AuthorizationRiskLevel.LOW,
                ActionNature.MUTATING, SecurityFeature.WRITE_ACTIONS, WRITE_SWITCHES);

        add(rules, BuggySecurityCapabilities.WEB_READ, "web.read", AuthorizationRiskLevel.LOW,
                ActionNature.READ_ONLY, null, ALL_TOOLS);
        add(rules, BuggySecurityCapabilities.WEB_EXECUTE, "web.execute", AuthorizationRiskLevel.MEDIUM,
                ActionNature.READ_ONLY, null, ALL_TOOLS);

        add(rules, BuggySecurityCapabilities.MAIL_READ, "mail.read", AuthorizationRiskLevel.MEDIUM,
                ActionNature.READ_ONLY, null, ALL_TOOLS);
        add(rules, BuggySecurityCapabilities.MAIL_SEND_REQUEST, "mail.send.request", AuthorizationRiskLevel.MEDIUM,
                ActionNature.MUTATING, null, ALL_TOOLS);

        if (!rules.keySet().equals(BuggySecurityCapabilities.all())) {
            throw new IllegalStateException("Buggy security rules must cover the complete B3 capability namespace");
        }
        return Map.copyOf(rules);
    }

    private static void add(
            Map<AssistantCapabilityId, CapabilitySecurityRule> target,
            AssistantCapabilityId capability,
            String permissionSuffix,
            AuthorizationRiskLevel risk,
            ActionNature nature,
            SecurityFeature feature,
            Set<SecurityKillSwitch> killSwitches) {
        String permission = "networkcore.assistant.buggy." + permissionSuffix;
        CapabilitySecurityRule rule = new CapabilitySecurityRule(
                capability, permission, Set.of(), risk, nature, feature, killSwitches);
        if (target.put(capability, rule) != null) {
            throw new IllegalStateException("Duplicate Buggy security rule: " + capability);
        }
    }
}
