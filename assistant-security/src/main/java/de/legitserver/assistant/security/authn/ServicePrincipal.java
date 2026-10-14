package de.legitserver.assistant.security.authn;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.Set;

/**
 * Authenticated internal service identity, separate from the human/system actor being authorized.
 * The constructor is package-private so only credential-verification code in this package can mint principals.
 */
public final class ServicePrincipal {
    private final String serviceId;
    private final String audience;
    private final Set<ServiceScope> scopes;
    private final String credentialId;
    private final Instant issuedAt;
    private final Instant expiresAt;

    ServicePrincipal(
            String serviceId,
            String audience,
            Set<ServiceScope> scopes,
            String credentialId,
            Instant issuedAt,
            Instant expiresAt) {
        this.serviceId = CoreValidation.requireSafeToken(serviceId, "serviceId", 96);
        this.audience = CoreValidation.requireSafeToken(audience, "audience", 96);
        this.scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
        this.credentialId = CoreValidation.requireSafeToken(credentialId, "credentialId", 128);
        this.issuedAt = CoreValidation.requireNonNull(issuedAt, "issuedAt");
        this.expiresAt = CoreValidation.requireNonNull(expiresAt, "expiresAt");
        if (!this.expiresAt.isAfter(this.issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
    }

    public String serviceId() { return serviceId; }
    public String audience() { return audience; }
    public Set<ServiceScope> scopes() { return scopes; }
    public String credentialId() { return credentialId; }
    public Instant issuedAt() { return issuedAt; }
    public Instant expiresAt() { return expiresAt; }

    public boolean hasScope(ServiceScope scope) {
        return scopes.contains(CoreValidation.requireNonNull(scope, "scope"));
    }
}
