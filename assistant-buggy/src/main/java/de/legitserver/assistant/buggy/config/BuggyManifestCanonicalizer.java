package de.legitserver.assistant.buggy.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Deterministic manifest canonicalization for persistence integrity and future approval binding. */
public final class BuggyManifestCanonicalizer {
    private BuggyManifestCanonicalizer() {}

    public static String canonicalPayload(BuggyManifest manifest) {
        if (manifest == null) throw new IllegalArgumentException("manifest must not be null");
        return String.join("\n",
                "schemaVersion=" + manifest.schemaVersion(),
                "assistantId=" + manifest.assistantId().name(),
                "canonicalId=" + manifest.canonicalId(),
                "displayName=" + manifest.displayName(),
                "serviceIdentity=" + manifest.serviceIdentity(),
                "assistantType=" + manifest.assistantType(),
                "scopes=NETWORK_WIDE,OWNER_DIRECTED",
                "primaryRole=" + manifest.primaryRole(),
                "authorityTier=" + manifest.authorityTier(),
                "securityAuthority=" + manifest.securityAuthority(),
                "capabilityProfileId=" + manifest.capabilityProfileId(),
                "policyProfileId=" + manifest.policyProfileId(),
                "knowledgeProfileId=" + manifest.knowledgeProfileId(),
                "runtimeProfileId=" + manifest.runtimeProfileId(),
                "personalityProfileId=" + (manifest.personalityProfileId() == null ? "" : manifest.personalityProfileId()),
                "persistentIdentity=" + manifest.persistentIdentity(),
                "ownerDirected=" + manifest.ownerDirected(),
                "networkWide=" + manifest.networkWide(),
                "selfApprovalAllowed=" + manifest.selfApprovalAllowed(),
                "authoritySelfEscalationAllowed=" + manifest.authoritySelfEscalationAllowed(),
                "riskDowngradeAllowed=" + manifest.riskDowngradeAllowed());
    }

    public static String sha256(BuggyManifest manifest) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonicalPayload(manifest).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }
}
