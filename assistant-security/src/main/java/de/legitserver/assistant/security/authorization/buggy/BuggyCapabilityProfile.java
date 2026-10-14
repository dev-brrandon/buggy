package de.legitserver.assistant.security.authorization.buggy;

import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.core.assistant.AssistantId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Canonical B3 Buggy capability profile. It defines product scope, not security authority. */
public final class BuggyCapabilityProfile {
    public static final String PROFILE_ID = "buggy-capabilities-v1";

    private static final BuggyCapabilityProfile CANONICAL = new BuggyCapabilityProfile(canonicalBundles());

    private final Map<BuggyCapabilityGroup, BuggyCapabilityBundle> bundles;
    private final Set<AssistantCapabilityId> capabilities;

    private BuggyCapabilityProfile(List<BuggyCapabilityBundle> source) {
        EnumMap<BuggyCapabilityGroup, BuggyCapabilityBundle> byGroup = new EnumMap<>(BuggyCapabilityGroup.class);
        LinkedHashSet<AssistantCapabilityId> allCapabilities = new LinkedHashSet<>();
        for (BuggyCapabilityBundle bundle : source) {
            BuggyCapabilityBundle previous = byGroup.put(bundle.group(), bundle);
            if (previous != null) throw new IllegalArgumentException("Duplicate Buggy capability group: " + bundle.group());
            for (BuggyCapabilityBinding binding : bundle.bindings()) {
                if (!allCapabilities.add(binding.capabilityId())) {
                    throw new IllegalArgumentException("Capability appears in multiple Buggy bundles: " + binding.capabilityId());
                }
            }
        }
        if (!byGroup.keySet().equals(Set.of(BuggyCapabilityGroup.values()))) {
            throw new IllegalArgumentException("Canonical Buggy capability profile must define every V1 group exactly once");
        }
        if (!allCapabilities.equals(BuggySecurityCapabilities.all())) {
            throw new IllegalArgumentException("Buggy capability profile and atomic capability namespace differ");
        }
        this.bundles = Map.copyOf(byGroup);
        this.capabilities = Set.copyOf(allCapabilities);
    }

    public static BuggyCapabilityProfile canonical() {
        return CANONICAL;
    }

    public String profileId() {
        return PROFILE_ID;
    }

    public AssistantId assistantId() {
        return AssistantId.BUGGY;
    }

    public Map<BuggyCapabilityGroup, BuggyCapabilityBundle> bundles() {
        return bundles;
    }

    public Set<AssistantCapabilityId> capabilities() {
        return capabilities;
    }

    public boolean contains(AssistantCapabilityId capabilityId) {
        return capabilityId != null && capabilities.contains(capabilityId);
    }

    public List<BuggyCapabilityBinding> bindings() {
        ArrayList<BuggyCapabilityBinding> result = new ArrayList<>();
        for (BuggyCapabilityGroup group : BuggyCapabilityGroup.values()) {
            result.addAll(bundles.get(group).bindings());
        }
        return List.copyOf(result);
    }

    private static List<BuggyCapabilityBundle> canonicalBundles() {
        return List.of(
                bundle(BuggyCapabilityGroup.OPERATIONS,
                        bind(BuggyCapabilityGroup.OPERATIONS, BuggySecurityCapabilities.OPERATIONS_EXECUTE_LOW, BuggyAuthorityMode.EXECUTE),
                        bind(BuggyCapabilityGroup.OPERATIONS, BuggySecurityCapabilities.OPERATIONS_MUTATE_LOW, BuggyAuthorityMode.MUTATE)),
                bundle(BuggyCapabilityGroup.DIAGNOSTICS,
                        bind(BuggyCapabilityGroup.DIAGNOSTICS, BuggySecurityCapabilities.DIAGNOSTICS_READ, BuggyAuthorityMode.READ),
                        bind(BuggyCapabilityGroup.DIAGNOSTICS, BuggySecurityCapabilities.DIAGNOSTICS_EXECUTE, BuggyAuthorityMode.EXECUTE)),
                bundle(BuggyCapabilityGroup.INFRASTRUCTURE,
                        bind(BuggyCapabilityGroup.INFRASTRUCTURE, BuggySecurityCapabilities.INFRASTRUCTURE_READ, BuggyAuthorityMode.READ),
                        bind(BuggyCapabilityGroup.INFRASTRUCTURE, BuggySecurityCapabilities.INFRASTRUCTURE_RECOVERY_LOW, BuggyAuthorityMode.EXECUTE),
                        bind(BuggyCapabilityGroup.INFRASTRUCTURE, BuggySecurityCapabilities.INFRASTRUCTURE_REQUEST, BuggyAuthorityMode.REQUEST_ONLY)),
                bundle(BuggyCapabilityGroup.SOURCE,
                        bind(BuggyCapabilityGroup.SOURCE, BuggySecurityCapabilities.SOURCE_READ, BuggyAuthorityMode.READ),
                        bind(BuggyCapabilityGroup.SOURCE, BuggySecurityCapabilities.SOURCE_WORKSPACE_MUTATE, BuggyAuthorityMode.MUTATE)),
                bundle(BuggyCapabilityGroup.BUILD,
                        bind(BuggyCapabilityGroup.BUILD, BuggySecurityCapabilities.BUILD_EXECUTE, BuggyAuthorityMode.EXECUTE)),
                bundle(BuggyCapabilityGroup.DEPLOYMENT,
                        bind(BuggyCapabilityGroup.DEPLOYMENT, BuggySecurityCapabilities.DEPLOYMENT_READ, BuggyAuthorityMode.READ),
                        bind(BuggyCapabilityGroup.DEPLOYMENT, BuggySecurityCapabilities.DEPLOYMENT_NONPROD_EXECUTE, BuggyAuthorityMode.EXECUTE),
                        bind(BuggyCapabilityGroup.DEPLOYMENT, BuggySecurityCapabilities.DEPLOYMENT_PRODUCTION_REQUEST, BuggyAuthorityMode.REQUEST_ONLY)),
                bundle(BuggyCapabilityGroup.DATABASE,
                        bind(BuggyCapabilityGroup.DATABASE, BuggySecurityCapabilities.DATABASE_READ, BuggyAuthorityMode.READ),
                        bind(BuggyCapabilityGroup.DATABASE, BuggySecurityCapabilities.DATABASE_WRITE_REQUEST, BuggyAuthorityMode.REQUEST_ONLY)),
                bundle(BuggyCapabilityGroup.NETWORK,
                        bind(BuggyCapabilityGroup.NETWORK, BuggySecurityCapabilities.NETWORK_READ, BuggyAuthorityMode.READ),
                        bind(BuggyCapabilityGroup.NETWORK, BuggySecurityCapabilities.NETWORK_MUTATE_REQUEST, BuggyAuthorityMode.REQUEST_ONLY)),
                bundle(BuggyCapabilityGroup.SECURITY_READ,
                        bind(BuggyCapabilityGroup.SECURITY_READ, BuggySecurityCapabilities.SECURITY_READ, BuggyAuthorityMode.READ)),
                bundle(BuggyCapabilityGroup.SECURITY_REQUEST,
                        bind(BuggyCapabilityGroup.SECURITY_REQUEST, BuggySecurityCapabilities.SECURITY_REQUEST, BuggyAuthorityMode.REQUEST_ONLY)),
                bundle(BuggyCapabilityGroup.KNOWLEDGE,
                        bind(BuggyCapabilityGroup.KNOWLEDGE, BuggySecurityCapabilities.KNOWLEDGE_READ, BuggyAuthorityMode.READ),
                        bind(BuggyCapabilityGroup.KNOWLEDGE, BuggySecurityCapabilities.KNOWLEDGE_DRAFT_MUTATE, BuggyAuthorityMode.MUTATE)),
                bundle(BuggyCapabilityGroup.BUGS,
                        bind(BuggyCapabilityGroup.BUGS, BuggySecurityCapabilities.BUGS_READ, BuggyAuthorityMode.READ),
                        bind(BuggyCapabilityGroup.BUGS, BuggySecurityCapabilities.BUGS_MUTATE, BuggyAuthorityMode.MUTATE)),
                bundle(BuggyCapabilityGroup.REQUESTS,
                        bind(BuggyCapabilityGroup.REQUESTS, BuggySecurityCapabilities.REQUESTS_READ, BuggyAuthorityMode.READ),
                        bind(BuggyCapabilityGroup.REQUESTS, BuggySecurityCapabilities.REQUESTS_CREATE, BuggyAuthorityMode.MUTATE)),
                bundle(BuggyCapabilityGroup.ASSISTANT_SUPERVISOR,
                        bind(BuggyCapabilityGroup.ASSISTANT_SUPERVISOR, BuggySecurityCapabilities.SUPERVISOR_READ, BuggyAuthorityMode.READ),
                        bind(BuggyCapabilityGroup.ASSISTANT_SUPERVISOR, BuggySecurityCapabilities.SUPERVISOR_EXECUTE, BuggyAuthorityMode.EXECUTE)),
                bundle(BuggyCapabilityGroup.ASSISTANT_SELFDEVELOPMENT,
                        bind(BuggyCapabilityGroup.ASSISTANT_SELFDEVELOPMENT, BuggySecurityCapabilities.SELFDEVELOPMENT_WORKSPACE_MUTATE, BuggyAuthorityMode.MUTATE),
                        bind(BuggyCapabilityGroup.ASSISTANT_SELFDEVELOPMENT, BuggySecurityCapabilities.SELFDEVELOPMENT_EXECUTE, BuggyAuthorityMode.EXECUTE)),
                bundle(BuggyCapabilityGroup.PANEL,
                        bind(BuggyCapabilityGroup.PANEL, BuggySecurityCapabilities.PANEL_READ, BuggyAuthorityMode.READ),
                        bind(BuggyCapabilityGroup.PANEL, BuggySecurityCapabilities.PANEL_EXECUTE, BuggyAuthorityMode.EXECUTE)),
                bundle(BuggyCapabilityGroup.DISCORD,
                        bind(BuggyCapabilityGroup.DISCORD, BuggySecurityCapabilities.DISCORD_READ, BuggyAuthorityMode.READ),
                        bind(BuggyCapabilityGroup.DISCORD, BuggySecurityCapabilities.DISCORD_EXECUTE_LOW, BuggyAuthorityMode.EXECUTE)),
                bundle(BuggyCapabilityGroup.MINECRAFT,
                        bind(BuggyCapabilityGroup.MINECRAFT, BuggySecurityCapabilities.MINECRAFT_READ, BuggyAuthorityMode.READ),
                        bind(BuggyCapabilityGroup.MINECRAFT, BuggySecurityCapabilities.MINECRAFT_EXECUTE_LOW, BuggyAuthorityMode.EXECUTE)),
                bundle(BuggyCapabilityGroup.WEB,
                        bind(BuggyCapabilityGroup.WEB, BuggySecurityCapabilities.WEB_READ, BuggyAuthorityMode.READ),
                        bind(BuggyCapabilityGroup.WEB, BuggySecurityCapabilities.WEB_EXECUTE, BuggyAuthorityMode.EXECUTE)),
                bundle(BuggyCapabilityGroup.MAIL,
                        bind(BuggyCapabilityGroup.MAIL, BuggySecurityCapabilities.MAIL_READ, BuggyAuthorityMode.READ),
                        bind(BuggyCapabilityGroup.MAIL, BuggySecurityCapabilities.MAIL_SEND_REQUEST, BuggyAuthorityMode.REQUEST_ONLY)));
    }

    private static BuggyCapabilityBundle bundle(BuggyCapabilityGroup group, BuggyCapabilityBinding... bindings) {
        return new BuggyCapabilityBundle(group, List.of(bindings));
    }

    private static BuggyCapabilityBinding bind(
            BuggyCapabilityGroup group,
            AssistantCapabilityId capabilityId,
            BuggyAuthorityMode mode) {
        return new BuggyCapabilityBinding(group, capabilityId, mode);
    }
}
