package de.legitserver.assistant.buggy.config;

import de.legitserver.assistant.buggy.identity.BuggyIdentityCore;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.Set;

/** Typed Buggy bot manifest payload. Security authority fields are fixed, not configurable escape hatches. */
public record BuggyManifest(
        int schemaVersion,
        AssistantId assistantId,
        String canonicalId,
        String displayName,
        String serviceIdentity,
        String assistantType,
        Set<String> scopes,
        String primaryRole,
        String authorityTier,
        String securityAuthority,
        String capabilityProfileId,
        String policyProfileId,
        String knowledgeProfileId,
        String runtimeProfileId,
        String personalityProfileId,
        boolean persistentIdentity,
        boolean ownerDirected,
        boolean networkWide,
        boolean selfApprovalAllowed,
        boolean authoritySelfEscalationAllowed,
        boolean riskDowngradeAllowed) {

    public static final int SCHEMA_VERSION = 1;

    public BuggyManifest {
        if (schemaVersion != SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported Buggy manifest schema");
        assistantId = CoreValidation.requireNonNull(assistantId, "assistantId");
        canonicalId = CoreValidation.requireSafeToken(canonicalId, "canonicalId", 32);
        displayName = CoreValidation.requireText(displayName, "displayName", 80);
        serviceIdentity = CoreValidation.requireSafeToken(serviceIdentity, "serviceIdentity", 96);
        assistantType = CoreValidation.requireSafeToken(assistantType, "assistantType", 64);
        scopes = Set.copyOf(CoreValidation.requireNonNull(scopes, "scopes"));
        primaryRole = CoreValidation.requireSafeToken(primaryRole, "primaryRole", 96);
        authorityTier = CoreValidation.requireSafeToken(authorityTier, "authorityTier", 64);
        securityAuthority = CoreValidation.requireSafeToken(securityAuthority, "securityAuthority", 64);
        capabilityProfileId = CoreValidation.requireSafeToken(capabilityProfileId, "capabilityProfileId", 96);
        policyProfileId = CoreValidation.requireSafeToken(policyProfileId, "policyProfileId", 96);
        knowledgeProfileId = CoreValidation.requireSafeToken(knowledgeProfileId, "knowledgeProfileId", 96);
        runtimeProfileId = CoreValidation.requireSafeToken(runtimeProfileId, "runtimeProfileId", 96);
        personalityProfileId = CoreValidation.optionalSafeToken(personalityProfileId, "personalityProfileId", 96);

        if (assistantId != AssistantId.BUGGY
                || !BuggyIdentityCore.CANONICAL_ID.equals(canonicalId)
                || !BuggyIdentityCore.SERVICE_IDENTITY.equals(serviceIdentity)
                || !BuggyIdentityCore.ASSISTANT_TYPE.equals(assistantType)
                || !BuggyIdentityCore.PRIMARY_ROLE.equals(primaryRole)
                || !BuggyIdentityCore.AUTHORITY_TIER.equals(authorityTier)
                || !BuggyIdentityCore.SECURITY_AUTHORITY.equals(securityAuthority)
                || !BuggyIdentityCore.SCOPES.equals(scopes)) {
            throw new IllegalArgumentException("Buggy manifest attempted to mutate protected identity/security fields");
        }
        if (!persistentIdentity || !ownerDirected || !networkWide) {
            throw new IllegalArgumentException("Buggy manifest must preserve persistent NETWORK_WIDE + OWNER_DIRECTED identity");
        }
        if (selfApprovalAllowed || authoritySelfEscalationAllowed || riskDowngradeAllowed) {
            throw new IllegalArgumentException("Buggy manifest cannot enable security bypass flags");
        }
    }

    public static BuggyManifest initial() {
        return new BuggyManifest(
                SCHEMA_VERSION,
                AssistantId.BUGGY,
                BuggyIdentityCore.CANONICAL_ID,
                BuggyIdentityCore.DISPLAY_NAME,
                BuggyIdentityCore.SERVICE_IDENTITY,
                BuggyIdentityCore.ASSISTANT_TYPE,
                BuggyIdentityCore.SCOPES,
                BuggyIdentityCore.PRIMARY_ROLE,
                BuggyIdentityCore.AUTHORITY_TIER,
                BuggyIdentityCore.SECURITY_AUTHORITY,
                "buggy-capabilities-v1",
                "buggy-policy-v1",
                "buggy-knowledge-v1",
                "buggy-runtime-v1",
                null,
                true,
                true,
                true,
                false,
                false,
                false);
    }

    public BuggyManifest withPersonalityProfileId(String profileId) {
        return new BuggyManifest(
                schemaVersion,
                assistantId,
                canonicalId,
                displayName,
                serviceIdentity,
                assistantType,
                scopes,
                primaryRole,
                authorityTier,
                securityAuthority,
                capabilityProfileId,
                policyProfileId,
                knowledgeProfileId,
                runtimeProfileId,
                profileId,
                persistentIdentity,
                ownerDirected,
                networkWide,
                selfApprovalAllowed,
                authoritySelfEscalationAllowed,
                riskDowngradeAllowed);
    }
}
