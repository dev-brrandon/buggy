package de.legitserver.assistant.integrations.buggy;

import de.legitserver.assistant.buggy.config.BuggyManifest;
import de.legitserver.assistant.buggy.identity.BuggyIdentityCore;
import de.legitserver.assistant.core.assistant.AssistantDescriptor;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.security.authorization.AssistantSecurityCatalog;
import de.legitserver.assistant.security.authorization.buggy.BuggyCapabilityPolicy;
import de.legitserver.assistant.security.authorization.buggy.BuggyCapabilityProfile;
import de.legitserver.assistant.security.authorization.buggy.BuggySecurityCapabilities;
import java.time.Instant;

/** Cross-module B3 contract: the B1 manifest points at the exact canonical B3 security profile. */
public final class B3BuggyCapabilityBindingSelfTest {
    private B3BuggyCapabilityBindingSelfTest() {}

    public static void main(String[] args) {
        BuggyManifest manifest = BuggyManifest.initial();
        require(manifest.assistantId() == AssistantId.BUGGY, "B1 manifest assistant id");
        require(manifest.securityAuthority().equals("NETWORKCORE"), "B1 identity keeps NetworkCore authoritative");
        require(BuggyIdentityCore.SECURITY_AUTHORITY.equals("NETWORKCORE"), "protected identity authority remains NetworkCore");
        require(manifest.capabilityProfileId().equals(BuggyCapabilityProfile.PROFILE_ID),
                "B1 manifest must bind the canonical B3 capability profile");

        AssistantDescriptor descriptor = new AssistantDescriptor(
                manifest.assistantId(),
                manifest.displayName(),
                true,
                manifest.capabilityProfileId(),
                manifest.policyProfileId(),
                manifest.knowledgeProfileId(),
                manifest.runtimeProfileId(),
                manifest.personalityProfileId(),
                1L,
                Instant.parse("2026-10-04T21:00:00Z"));
        BuggyCapabilityPolicy policy = new BuggyCapabilityPolicy();
        require(policy.hasCapability(descriptor, BuggySecurityCapabilities.KNOWLEDGE_READ),
                "canonical manifest profile grants a declared B3 capability");
        require(AssistantSecurityCatalog.rule(BuggySecurityCapabilities.KNOWLEDGE_READ).isPresent(),
                "declared B3 capability must resolve through Foundation Security");
        require(AssistantSecurityCatalog.assistantUsePermission(AssistantId.BUGGY).equals("networkcore.assistant.use.buggy"),
                "Buggy remains gated by NetworkCore assistant-use permission");

        System.out.println("B3_BUGGY_CAPABILITY_BINDING_SELF_TEST: PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Assertion failed: " + message);
    }
}
