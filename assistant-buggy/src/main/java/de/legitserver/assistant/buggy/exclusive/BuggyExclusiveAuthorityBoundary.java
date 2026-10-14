package de.legitserver.assistant.buggy.exclusive;

import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.security.authorization.buggy.BuggySecurityCapabilities;
import java.util.EnumMap;
import java.util.Map;

/**
 * Maps B10 evidence to existing B3 read/diagnostic capabilities. B10 intentionally defines no new authority.
 */
public final class BuggyExclusiveAuthorityBoundary {
    private static final Map<BuggyEvidenceSource, AssistantCapabilityId> READ_CAPABILITIES = create();

    private BuggyExclusiveAuthorityBoundary() {}

    public static AssistantCapabilityId requiredReadCapability(BuggyEvidenceSource source) {
        AssistantCapabilityId capability = READ_CAPABILITIES.get(source);
        if (capability == null) throw new IllegalArgumentException("Unsupported evidence source");
        return capability;
    }

    public static Map<BuggyEvidenceSource, AssistantCapabilityId> readCapabilities() {
        return READ_CAPABILITIES;
    }

    private static Map<BuggyEvidenceSource, AssistantCapabilityId> create() {
        EnumMap<BuggyEvidenceSource, AssistantCapabilityId> map = new EnumMap<>(BuggyEvidenceSource.class);
        map.put(BuggyEvidenceSource.DEPLOYMENT, BuggySecurityCapabilities.DEPLOYMENT_READ);
        map.put(BuggyEvidenceSource.LOG, BuggySecurityCapabilities.DIAGNOSTICS_READ);
        map.put(BuggyEvidenceSource.METRIC, BuggySecurityCapabilities.DIAGNOSTICS_READ);
        map.put(BuggyEvidenceSource.DATABASE, BuggySecurityCapabilities.DATABASE_READ);
        map.put(BuggyEvidenceSource.BUG_HISTORY, BuggySecurityCapabilities.BUGS_READ);
        map.put(BuggyEvidenceSource.SOURCE_DIFF, BuggySecurityCapabilities.SOURCE_READ);
        map.put(BuggyEvidenceSource.KNOWLEDGE, BuggySecurityCapabilities.KNOWLEDGE_READ);
        map.put(BuggyEvidenceSource.INCIDENT_TIMELINE, BuggySecurityCapabilities.DIAGNOSTICS_READ);
        if (map.size() != BuggyEvidenceSource.values().length) {
            throw new IllegalStateException("Every B10 evidence source requires an existing B3 read capability");
        }
        return Map.copyOf(map);
    }
}
