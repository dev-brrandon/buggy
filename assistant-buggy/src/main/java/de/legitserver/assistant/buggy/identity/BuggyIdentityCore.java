package de.legitserver.assistant.buggy.identity;

import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Protected, persistent Buggy identity core. Any change to these constants is an explicit product/security
 * change and cannot be produced by personality learning or runtime state.
 */
public record BuggyIdentityCore(
        AssistantId assistantId,
        String canonicalId,
        String displayName,
        String serviceIdentity,
        String assistantType,
        String primaryRole,
        String authorityTier,
        String securityAuthority,
        Set<String> scopes,
        List<String> principles,
        long coreVersion,
        String sha256,
        Instant createdAt) {

    public static final AssistantId ASSISTANT_ID = AssistantId.BUGGY;
    public static final String CANONICAL_ID = "buggy";
    public static final String DISPLAY_NAME = "Buggy";
    public static final String SERVICE_IDENTITY = "assistant:buggy";
    public static final String ASSISTANT_TYPE = "OWNER_ASSISTANT";
    public static final String PRIMARY_ROLE = "PERSONAL_OWNER_AND_NETWORK_OPERATIONS_ASSISTANT";
    public static final String AUTHORITY_TIER = "HIGHEST_ASSISTANT_TIER";
    public static final String SECURITY_AUTHORITY = "NETWORKCORE";
    public static final long CORE_VERSION = 1L;
    public static final Set<String> SCOPES = Set.of("NETWORK_WIDE", "OWNER_DIRECTED");
    public static final List<String> PRINCIPLES = List.of(
            "protect Legit",
            "protect the Owner",
            "do not fabricate certainty",
            "distinguish facts, inference and preference",
            "challenge unsafe or weak technical ideas",
            "prefer safe and reversible actions",
            "learn from prior work",
            "maintain a coherent persistent identity",
            "respect Foundation security boundaries");

    public BuggyIdentityCore {
        assistantId = CoreValidation.requireNonNull(assistantId, "assistantId");
        canonicalId = CoreValidation.requireSafeToken(canonicalId, "canonicalId", 32);
        displayName = CoreValidation.requireText(displayName, "displayName", 80);
        serviceIdentity = CoreValidation.requireSafeToken(serviceIdentity, "serviceIdentity", 96);
        assistantType = CoreValidation.requireSafeToken(assistantType, "assistantType", 64);
        primaryRole = CoreValidation.requireSafeToken(primaryRole, "primaryRole", 96);
        authorityTier = CoreValidation.requireSafeToken(authorityTier, "authorityTier", 64);
        securityAuthority = CoreValidation.requireSafeToken(securityAuthority, "securityAuthority", 64);
        scopes = Set.copyOf(CoreValidation.requireNonNull(scopes, "scopes"));
        principles = List.copyOf(CoreValidation.requireNonNull(principles, "principles"));
        sha256 = CoreValidation.requireSafeToken(sha256, "sha256", 64);
        createdAt = CoreValidation.requireNonNull(createdAt, "createdAt");
        if (coreVersion != CORE_VERSION) throw new IllegalArgumentException("Unsupported Buggy Identity Core version");
        requireCanonical(assistantId, ASSISTANT_ID, "assistantId");
        requireCanonical(canonicalId, CANONICAL_ID, "canonicalId");
        requireCanonical(displayName, DISPLAY_NAME, "displayName");
        requireCanonical(serviceIdentity, SERVICE_IDENTITY, "serviceIdentity");
        requireCanonical(assistantType, ASSISTANT_TYPE, "assistantType");
        requireCanonical(primaryRole, PRIMARY_ROLE, "primaryRole");
        requireCanonical(authorityTier, AUTHORITY_TIER, "authorityTier");
        requireCanonical(securityAuthority, SECURITY_AUTHORITY, "securityAuthority");
        requireCanonical(scopes, SCOPES, "scopes");
        requireCanonical(principles, PRINCIPLES, "principles");
        String expectedHash = canonicalSha256();
        if (!expectedHash.equals(sha256)) {
            throw new IllegalArgumentException("Buggy Identity Core SHA-256 mismatch");
        }
    }

    public static BuggyIdentityCore canonical(Instant createdAt) {
        return new BuggyIdentityCore(
                ASSISTANT_ID,
                CANONICAL_ID,
                DISPLAY_NAME,
                SERVICE_IDENTITY,
                ASSISTANT_TYPE,
                PRIMARY_ROLE,
                AUTHORITY_TIER,
                SECURITY_AUTHORITY,
                SCOPES,
                PRINCIPLES,
                CORE_VERSION,
                canonicalSha256(),
                createdAt);
    }

    public static String canonicalSha256() {
        String canonical = String.join("\n",
                "assistantId=" + ASSISTANT_ID.name(),
                "canonicalId=" + CANONICAL_ID,
                "displayName=" + DISPLAY_NAME,
                "serviceIdentity=" + SERVICE_IDENTITY,
                "assistantType=" + ASSISTANT_TYPE,
                "primaryRole=" + PRIMARY_ROLE,
                "authorityTier=" + AUTHORITY_TIER,
                "securityAuthority=" + SECURITY_AUTHORITY,
                "scopes=NETWORK_WIDE,OWNER_DIRECTED",
                "principles=" + String.join("|", PRINCIPLES));
        return sha256(canonical);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private static void requireCanonical(Object actual, Object expected, String field) {
        if (!expected.equals(actual)) throw new IllegalArgumentException("Buggy Identity Core " + field + " is protected");
    }
}
