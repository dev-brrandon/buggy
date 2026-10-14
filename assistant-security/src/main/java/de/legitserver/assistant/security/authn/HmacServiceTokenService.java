package de.legitserver.assistant.security.authn;

import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Dependency-free HMAC-SHA256 request-bound internal service-token implementation. */
public final class HmacServiceTokenService {
    private static final String VERSION = "v1";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(15);
    private static final Duration MAX_LIFETIME = Duration.ofMinutes(5);

    private final byte[] key;
    private final ReplayProtector replayProtector;

    public HmacServiceTokenService(byte[] key, ReplayProtector replayProtector) {
        if (key == null || key.length < 32) {
            throw new IllegalArgumentException("HMAC key must contain at least 32 bytes");
        }
        this.key = key.clone();
        this.replayProtector = CoreValidation.requireNonNull(replayProtector, "replayProtector");
    }

    public String issue(ServiceTokenClaims claims) {
        CoreValidation.requireNonNull(claims, "claims");
        Duration lifetime = Duration.between(claims.issuedAt(), claims.expiresAt());
        if (lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(MAX_LIFETIME) > 0) {
            throw new IllegalArgumentException("service token lifetime must be > 0 and <= " + MAX_LIFETIME);
        }
        String payload = canonicalPayload(claims);
        String encoded = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return VERSION + "." + encoded + "." + ENCODER.encodeToString(sign(VERSION + "." + encoded));
    }

    public ServiceAuthenticationResult verify(
            String token,
            String expectedAudience,
            RequestId expectedRequestId,
            Set<ServiceScope> requiredScopes,
            Instant now) {
        if (token == null || token.isBlank() || expectedAudience == null || expectedRequestId == null || now == null) {
            return ServiceAuthenticationResult.deny(ServiceAuthenticationStatus.MALFORMED);
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            return ServiceAuthenticationResult.deny(ServiceAuthenticationStatus.MALFORMED);
        }
        byte[] suppliedSignature;
        byte[] payloadBytes;
        try {
            suppliedSignature = DECODER.decode(parts[2]);
            payloadBytes = DECODER.decode(parts[1]);
        } catch (IllegalArgumentException malformed) {
            return ServiceAuthenticationResult.deny(ServiceAuthenticationStatus.MALFORMED);
        }
        byte[] expectedSignature = sign(parts[0] + "." + parts[1]);
        if (!MessageDigest.isEqual(expectedSignature, suppliedSignature)) {
            return ServiceAuthenticationResult.deny(ServiceAuthenticationStatus.SIGNATURE_INVALID);
        }
        ServiceTokenClaims claims;
        try {
            claims = parse(new String(payloadBytes, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException malformed) {
            return ServiceAuthenticationResult.deny(ServiceAuthenticationStatus.MALFORMED);
        }
        if (!claims.audience().equals(expectedAudience)) {
            return ServiceAuthenticationResult.deny(ServiceAuthenticationStatus.AUDIENCE_MISMATCH);
        }
        if (!claims.requestId().equals(expectedRequestId)) {
            return ServiceAuthenticationResult.deny(ServiceAuthenticationStatus.REQUEST_MISMATCH);
        }
        if (claims.issuedAt().isAfter(now.plus(CLOCK_SKEW))) {
            return ServiceAuthenticationResult.deny(ServiceAuthenticationStatus.NOT_YET_VALID);
        }
        if (!claims.expiresAt().isAfter(now.minus(CLOCK_SKEW))) {
            return ServiceAuthenticationResult.deny(ServiceAuthenticationStatus.EXPIRED);
        }
        Duration lifetime = Duration.between(claims.issuedAt(), claims.expiresAt());
        if (lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(MAX_LIFETIME) > 0) {
            return ServiceAuthenticationResult.deny(ServiceAuthenticationStatus.MALFORMED);
        }
        Set<ServiceScope> required = requiredScopes == null ? Set.of() : Set.copyOf(requiredScopes);
        if (!claims.scopes().containsAll(required)) {
            return ServiceAuthenticationResult.deny(ServiceAuthenticationStatus.SCOPE_MISSING);
        }
        if (!replayProtector.claim(claims.jti(), claims.expiresAt(), now)) {
            return ServiceAuthenticationResult.deny(ServiceAuthenticationStatus.REPLAYED);
        }
        return ServiceAuthenticationResult.allow(
                new ServicePrincipal(
                        claims.serviceId(),
                        claims.audience(),
                        claims.scopes(),
                        claims.jti(),
                        claims.issuedAt(),
                        claims.expiresAt()));
    }

    private byte[] sign(String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 unavailable", exception);
        }
    }

    private static String canonicalPayload(ServiceTokenClaims claims) {
        List<String> scopes = claims.scopes().stream().map(ServiceScope::value).sorted().toList();
        return String.join(
                "\n",
                claims.serviceId(),
                claims.audience(),
                String.join(",", scopes),
                Long.toString(claims.issuedAt().getEpochSecond()),
                Long.toString(claims.expiresAt().getEpochSecond()),
                claims.jti(),
                claims.requestId().value());
    }

    private static ServiceTokenClaims parse(String payload) {
        String[] fields = payload.split("\n", -1);
        if (fields.length != 7) {
            throw new IllegalArgumentException("invalid payload field count");
        }
        Set<ServiceScope> scopes = new LinkedHashSet<>();
        if (!fields[2].isEmpty()) {
            for (String scope : fields[2].split(",")) {
                scopes.add(new ServiceScope(scope));
            }
        }
        Instant issuedAt;
        Instant expiresAt;
        try {
            issuedAt = Instant.ofEpochSecond(Long.parseLong(fields[3]));
            expiresAt = Instant.ofEpochSecond(Long.parseLong(fields[4]));
        } catch (NumberFormatException malformed) {
            throw new IllegalArgumentException("invalid token timestamps", malformed);
        }
        return new ServiceTokenClaims(
                fields[0], fields[1], Set.copyOf(scopes), issuedAt, expiresAt, fields[5], new RequestId(fields[6]));
    }
}
