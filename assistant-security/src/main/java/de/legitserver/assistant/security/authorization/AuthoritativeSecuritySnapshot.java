package de.legitserver.assistant.security.authorization;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Framework-free projection of NetworkCore's ActorSecuritySnapshot. */
public record AuthoritativeSecuritySnapshot(
        UUID actorUuid,
        String rankId,
        int rankWeight,
        String primaryDomainId,
        Set<String> domainIds,
        Set<String> bundleIds,
        Set<String> effectivePermissions,
        Set<String> effectiveCapabilities,
        Set<String> panelCapabilities,
        boolean controlOwner,
        long securityRevision,
        Instant createdAt) {

    public AuthoritativeSecuritySnapshot {
        actorUuid = CoreValidation.requireNonNull(actorUuid, "actorUuid");
        rankId = CoreValidation.requireSafeToken(rankId, "rankId", 96);
        primaryDomainId = CoreValidation.optionalSafeToken(primaryDomainId, "primaryDomainId", 96);
        domainIds = immutableTokens(domainIds, "domainIds");
        bundleIds = immutableTokens(bundleIds, "bundleIds");
        effectivePermissions = immutableTokens(effectivePermissions, "effectivePermissions");
        effectiveCapabilities = immutableTokens(effectiveCapabilities, "effectiveCapabilities");
        panelCapabilities = immutableTokens(panelCapabilities, "panelCapabilities");
        if (securityRevision < 0) {
            throw new IllegalArgumentException("securityRevision must be >= 0");
        }
        createdAt = CoreValidation.requireNonNull(createdAt, "createdAt");
    }

    public boolean hasPermission(String permission) {
        return effectivePermissions.contains(CoreValidation.requireSafeToken(permission, "permission", 160));
    }

    public boolean hasCapability(String capability) {
        return effectiveCapabilities.contains(CoreValidation.requireSafeToken(capability, "capability", 160));
    }

    private static Set<String> immutableTokens(Set<String> values, String field) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream()
                .map(value -> CoreValidation.requireSafeToken(value, field + " entry", 160))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
