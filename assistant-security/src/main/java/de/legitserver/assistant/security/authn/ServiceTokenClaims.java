package de.legitserver.assistant.security.authn;

import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.Set;

/** Signed request-bound service token claims. */
public record ServiceTokenClaims(
        String serviceId,
        String audience,
        Set<ServiceScope> scopes,
        Instant issuedAt,
        Instant expiresAt,
        String jti,
        RequestId requestId) {

    public ServiceTokenClaims {
        serviceId = CoreValidation.requireSafeToken(serviceId, "serviceId", 96);
        audience = CoreValidation.requireSafeToken(audience, "audience", 96);
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
        issuedAt = CoreValidation.requireNonNull(issuedAt, "issuedAt");
        expiresAt = CoreValidation.requireNonNull(expiresAt, "expiresAt");
        jti = CoreValidation.requireSafeToken(jti, "jti", 128);
        requestId = CoreValidation.requireNonNull(requestId, "requestId");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
    }
}
