package de.legitserver.assistant.panel.security;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.security.authn.ReplayProtector;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Request-bound HMAC-SHA256 Panel user-context token with one-time replay protection. */
public final class HmacPanelUserContextService {
    private static final String VERSION = "puc1";
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(10);
    private static final Duration MAX_LIFETIME = Duration.ofMinutes(2);
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] key;
    private final ReplayProtector replayProtector;

    public HmacPanelUserContextService(byte[] key, ReplayProtector replayProtector) {
        if (key == null || key.length < 32) throw new IllegalArgumentException("Panel context HMAC key must be at least 32 bytes");
        if (replayProtector == null) throw new IllegalArgumentException("replayProtector must not be null");
        this.key = key.clone();
        this.replayProtector = replayProtector;
    }

    public String issue(PanelUserContextClaims claims) {
        if (claims == null) throw new IllegalArgumentException("claims must not be null");
        Duration lifetime = Duration.between(claims.issuedAt(), claims.expiresAt());
        if (lifetime.isZero() || lifetime.isNegative() || lifetime.compareTo(MAX_LIFETIME) > 0) {
            throw new IllegalArgumentException("Panel user context lifetime must be > 0 and <= " + MAX_LIFETIME);
        }
        String payload = canonical(claims);
        String encoded = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return VERSION + "." + encoded + "." + ENCODER.encodeToString(sign(VERSION + "." + encoded));
    }

    public PanelUserContextVerification verify(
            String token,
            RequestId expectedRequestId,
            TraceId expectedTraceId,
            String expectedServiceCredentialId,
            Instant now) {
        if (token == null || token.isBlank() || expectedRequestId == null || expectedTraceId == null
                || expectedServiceCredentialId == null || now == null) {
            return PanelUserContextVerification.denied(PanelUserContextStatus.MALFORMED);
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            return PanelUserContextVerification.denied(PanelUserContextStatus.MALFORMED);
        }
        byte[] supplied;
        byte[] payloadBytes;
        try {
            supplied = DECODER.decode(parts[2]);
            payloadBytes = DECODER.decode(parts[1]);
        } catch (IllegalArgumentException malformed) {
            return PanelUserContextVerification.denied(PanelUserContextStatus.MALFORMED);
        }
        if (!MessageDigest.isEqual(sign(parts[0] + "." + parts[1]), supplied)) {
            return PanelUserContextVerification.denied(PanelUserContextStatus.SIGNATURE_INVALID);
        }
        PanelUserContextClaims claims;
        try {
            claims = parse(new String(payloadBytes, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException malformed) {
            return PanelUserContextVerification.denied(PanelUserContextStatus.MALFORMED);
        }
        if (!claims.requestId().equals(expectedRequestId)) {
            return PanelUserContextVerification.denied(PanelUserContextStatus.REQUEST_MISMATCH);
        }
        if (!claims.traceId().equals(expectedTraceId)) {
            return PanelUserContextVerification.denied(PanelUserContextStatus.TRACE_MISMATCH);
        }
        if (!claims.serviceCredentialId().equals(expectedServiceCredentialId)) {
            return PanelUserContextVerification.denied(PanelUserContextStatus.SERVICE_BINDING_MISMATCH);
        }
        if (claims.issuedAt().isAfter(now.plus(CLOCK_SKEW))) {
            return PanelUserContextVerification.denied(PanelUserContextStatus.NOT_YET_VALID);
        }
        if (!claims.expiresAt().isAfter(now.minus(CLOCK_SKEW))) {
            return PanelUserContextVerification.denied(PanelUserContextStatus.EXPIRED);
        }
        Duration lifetime = Duration.between(claims.issuedAt(), claims.expiresAt());
        if (lifetime.isZero() || lifetime.isNegative() || lifetime.compareTo(MAX_LIFETIME) > 0) {
            return PanelUserContextVerification.denied(PanelUserContextStatus.MALFORMED);
        }
        if (!replayProtector.claim(claims.jti(), claims.expiresAt(), now)) {
            return PanelUserContextVerification.denied(PanelUserContextStatus.REPLAYED);
        }
        return PanelUserContextVerification.valid(claims);
    }

    private byte[] sign(String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 unavailable", exception);
        }
    }

    private static String canonical(PanelUserContextClaims claims) {
        return String.join("\n",
                claims.actorId().value(),
                claims.networkIdentityId(),
                claims.panelUserId(),
                claims.requestId().value(),
                claims.traceId().value(),
                claims.serviceCredentialId(),
                claims.serverId() == null ? "" : claims.serverId(),
                Long.toString(claims.issuedAt().getEpochSecond()),
                Long.toString(claims.expiresAt().getEpochSecond()),
                claims.jti());
    }

    private static PanelUserContextClaims parse(String payload) {
        String[] fields = payload.split("\n", -1);
        if (fields.length != 10) throw new IllegalArgumentException("invalid Panel user context field count");
        try {
            return new PanelUserContextClaims(
                    new ActorId(fields[0]), fields[1], fields[2], new RequestId(fields[3]), new TraceId(fields[4]),
                    fields[5], fields[6].isEmpty() ? null : fields[6], Instant.ofEpochSecond(Long.parseLong(fields[7])),
                    Instant.ofEpochSecond(Long.parseLong(fields[8])), fields[9]);
        } catch (NumberFormatException malformed) {
            throw new IllegalArgumentException("invalid Panel user context timestamps", malformed);
        }
    }
}
