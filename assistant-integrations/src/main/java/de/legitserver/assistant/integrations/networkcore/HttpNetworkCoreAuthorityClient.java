package de.legitserver.assistant.integrations.networkcore;

import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.security.authn.BoundedReplayProtector;
import de.legitserver.assistant.security.authn.HmacServiceTokenService;
import de.legitserver.assistant.security.authn.ServiceScope;
import de.legitserver.assistant.security.authn.ServiceTokenClaims;
import de.legitserver.assistant.security.authorization.AuthoritativeSecuritySnapshot;
import de.legitserver.assistant.security.authorization.AuthorityLookupStatus;
import de.legitserver.assistant.security.authorization.ResolvedActorIdentity;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Request-bound HMAC HTTP client for NetworkCore's read-only Assistant authority transport. */
public final class HttpNetworkCoreAuthorityClient implements NetworkCoreAuthorityClient {
    private static final String SERVICE_ID = "legit-assistant";
    private static final String AUDIENCE = "networkcore";
    private static final ServiceScope AUTHORITY_READ = new ServiceScope("assistant.authority.read");
    private static final Duration TOKEN_LIFETIME = Duration.ofSeconds(45);
    private static final int MAX_RESPONSE_BYTES = 512 * 1024;

    private final URI actorEndpoint;
    private final URI snapshotEndpoint;
    private final HttpClient http;
    private final HmacServiceTokenService tokens;
    private final byte[] requestHmacKey;
    private final Clock clock;

    public HttpNetworkCoreAuthorityClient(URI baseUri, byte[] hmacKey, Clock clock) {
        if (baseUri == null || hmacKey == null || clock == null) throw new IllegalArgumentException("authority client dependencies required");
        if (hmacKey.length < 32) throw new IllegalArgumentException("authority HMAC key too short");
        if (!secure(baseUri)) throw new IllegalArgumentException("NetworkCore authority URI must use HTTPS or loopback HTTP");
        String base = baseUri.toString();
        if (!base.endsWith("/")) base += "/";
        this.actorEndpoint = URI.create(base + "internal/assistant/v1/authority/actor");
        this.snapshotEndpoint = URI.create(base + "internal/assistant/v1/authority/snapshot");
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        this.tokens = new HmacServiceTokenService(hmacKey, new BoundedReplayProtector(64));
        this.requestHmacKey = hmacKey.clone();
        this.clock = clock;
    }

    public static HttpNetworkCoreAuthorityClient fromEnvironment(Clock clock) {
        String rawUrl = System.getenv("LEGIT_NETWORKCORE_ASSISTANT_AUTHORITY_URL");
        String rawKey = System.getenv("LEGIT_ASSISTANT_NETWORKCORE_AUTHORITY_HMAC_KEY_B64");
        if (rawUrl == null || rawUrl.isBlank() || rawKey == null || rawKey.isBlank()) return null;
        try {
            return new HttpNetworkCoreAuthorityClient(URI.create(rawUrl.strip()), Base64.getDecoder().decode(rawKey.strip()), clock);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    @Override
    public NetworkCoreActorLookup resolveActor(ActorContext presentedActor) {
        if (presentedActor == null) return new NetworkCoreActorLookup(AuthorityLookupStatus.NOT_FOUND, null);
        String body = "{" +
                "\"actorId\":" + quote(presentedActor.actorId().value()) + "," +
                "\"networkIdentityId\":" + quote(presentedActor.networkIdentityId()) + "," +
                "\"discordId\":" + nullableQuote(presentedActor.discordId()) +
                "}";
        AuthorityResponse response = post(actorEndpoint, presentedActor.requestId(), body);
        AuthorityLookupStatus status = status(response);
        if (status != AuthorityLookupStatus.FOUND) return new NetworkCoreActorLookup(status, null);
        String actorId = jsonString(response.body(), "actorId");
        String networkIdentityId = jsonString(response.body(), "networkIdentityId");
        String minecraftUuid = jsonString(response.body(), "minecraftUuid");
        long revision = jsonLong(response.body(), "authorizationRevision", -1L);
        if (actorId == null || networkIdentityId == null || minecraftUuid == null || revision < 0L) {
            return new NetworkCoreActorLookup(AuthorityLookupStatus.UNAVAILABLE, null);
        }
        try {
            return new NetworkCoreActorLookup(AuthorityLookupStatus.FOUND,
                    new ResolvedActorIdentity(new ActorId(actorId), networkIdentityId, UUID.fromString(minecraftUuid), revision));
        } catch (RuntimeException malformed) {
            return new NetworkCoreActorLookup(AuthorityLookupStatus.UNAVAILABLE, null);
        }
    }

    @Override
    public NetworkCoreSecurityLookup securitySnapshot(UUID actorUuid, long minimumRevision) {
        if (actorUuid == null || minimumRevision < 0L) return new NetworkCoreSecurityLookup(AuthorityLookupStatus.UNAVAILABLE, null);
        RequestId requestId = new RequestId("authority-" + UUID.randomUUID());
        String body = "{" +
                "\"actorUuid\":" + quote(actorUuid.toString()) + "," +
                "\"minimumRevision\":" + minimumRevision +
                "}";
        AuthorityResponse response = post(snapshotEndpoint, requestId, body);
        AuthorityLookupStatus status = status(response);
        if (status != AuthorityLookupStatus.FOUND) return new NetworkCoreSecurityLookup(status, null);
        try {
            String json = response.body();
            AuthoritativeSecuritySnapshot snapshot = new AuthoritativeSecuritySnapshot(
                    UUID.fromString(required(jsonString(json, "actorUuid"))),
                    required(jsonString(json, "rankId")),
                    Math.toIntExact(jsonLong(json, "rankWeight", Integer.MIN_VALUE)),
                    jsonNullableString(json, "primaryDomainId"),
                    jsonStringSet(json, "domainIds"),
                    jsonStringSet(json, "bundleIds"),
                    jsonStringSet(json, "effectivePermissions"),
                    jsonStringSet(json, "effectiveCapabilities"),
                    jsonStringSet(json, "panelCapabilities"),
                    jsonBoolean(json, "controlOwner", false),
                    jsonLong(json, "securityRevision", -1L),
                    Instant.parse(required(jsonString(json, "createdAt"))));
            if (!snapshot.actorUuid().equals(actorUuid) || snapshot.securityRevision() < minimumRevision) {
                return new NetworkCoreSecurityLookup(AuthorityLookupStatus.UNAVAILABLE, null);
            }
            return new NetworkCoreSecurityLookup(AuthorityLookupStatus.FOUND, snapshot);
        } catch (RuntimeException malformed) {
            return new NetworkCoreSecurityLookup(AuthorityLookupStatus.UNAVAILABLE, null);
        }
    }

    private AuthorityResponse post(URI endpoint, RequestId requestId, String body) {
        Instant now = clock.instant();
        ServiceTokenClaims claims = new ServiceTokenClaims(
                SERVICE_ID,
                AUDIENCE,
                Set.of(AUTHORITY_READ),
                now,
                now.plus(TOKEN_LIFETIME),
                UUID.randomUUID().toString(),
                requestId);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + tokens.issue(claims))
                .header("X-Request-Id", requestId.value())
                .header("X-Request-Signature", requestSignature(endpoint, requestId.value(), body))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream stream = response.body()) {
                byte[] bytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES) {
                    throw new IllegalStateException("NetworkCore authority response too large");
                }
                return new AuthorityResponse(response.statusCode(), new String(bytes, StandardCharsets.UTF_8));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("NetworkCore authority request interrupted", interrupted);
        } catch (IOException unavailable) {
            throw new IllegalStateException("NetworkCore authority unavailable", unavailable);
        }
    }

    private String requestSignature(URI endpoint, String requestId, String body) {
        String digest = Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(body.getBytes(StandardCharsets.UTF_8)));
        String canonical = "POST\n" + endpoint.getPath() + "\n" + requestId + "\n" + digest;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(requestHmacKey, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("HmacSHA256 unavailable", impossible);
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static AuthorityLookupStatus status(AuthorityResponse response) {
        if (response == null) return AuthorityLookupStatus.UNAVAILABLE;
        if (response.statusCode() == 200 && "FOUND".equals(jsonString(response.body(), "status"))) return AuthorityLookupStatus.FOUND;
        if (response.statusCode() == 404 || "NOT_FOUND".equals(jsonString(response.body(), "status"))) return AuthorityLookupStatus.NOT_FOUND;
        return AuthorityLookupStatus.UNAVAILABLE;
    }

    private static boolean secure(URI uri) {
        if ("https".equalsIgnoreCase(uri.getScheme())) return true;
        if (!"http".equalsIgnoreCase(uri.getScheme())) return false;
        String host = uri.getHost();
        return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host);
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("required JSON field missing");
        return value;
    }

    private static String nullableQuote(String value) {
        return value == null ? "null" : quote(value);
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder().append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }

    private static String jsonString(String json, String key) {
        if (json == null) return null;
        int colon = fieldColon(json, key);
        if (colon < 0) return null;
        int start = skipWhitespace(json, colon + 1);
        if (start >= json.length() || json.charAt(start) != '"') return null;
        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    default -> out.append(c);
                }
                escaped = false;
            } else if (c == '\\') escaped = true;
            else if (c == '"') return out.toString();
            else out.append(c);
        }
        return null;
    }

    private static String jsonNullableString(String json, String key) {
        int colon = fieldColon(json, key);
        if (colon < 0) return null;
        int start = skipWhitespace(json, colon + 1);
        if (json.startsWith("null", start)) return null;
        return jsonString(json, key);
    }

    private static long jsonLong(String json, String key, long fallback) {
        int colon = fieldColon(json, key);
        if (colon < 0) return fallback;
        int start = skipWhitespace(json, colon + 1);
        int end = start;
        while (end < json.length() && (json.charAt(end) == '-' || Character.isDigit(json.charAt(end)))) end++;
        if (start == end) return fallback;
        try { return Long.parseLong(json.substring(start, end)); }
        catch (NumberFormatException invalid) { return fallback; }
    }

    private static boolean jsonBoolean(String json, String key, boolean fallback) {
        int colon = fieldColon(json, key);
        if (colon < 0) return fallback;
        int start = skipWhitespace(json, colon + 1);
        if (json.startsWith("true", start)) return true;
        if (json.startsWith("false", start)) return false;
        return fallback;
    }

    private static Set<String> jsonStringSet(String json, String key) {
        int colon = fieldColon(json, key);
        if (colon < 0) return Set.of();
        int start = skipWhitespace(json, colon + 1);
        if (start >= json.length() || json.charAt(start) != '[') return Set.of();
        int end = json.indexOf(']', start + 1);
        if (end < 0) throw new IllegalArgumentException("unterminated JSON array");
        LinkedHashSet<String> values = new LinkedHashSet<>();
        int cursor = start + 1;
        while (cursor < end) {
            while (cursor < end && (Character.isWhitespace(json.charAt(cursor)) || json.charAt(cursor) == ',')) cursor++;
            if (cursor >= end) break;
            if (json.charAt(cursor) != '"') throw new IllegalArgumentException("non-string array value");
            int valueEnd = cursor + 1;
            boolean escaped = false;
            StringBuilder value = new StringBuilder();
            for (; valueEnd < end; valueEnd++) {
                char c = json.charAt(valueEnd);
                if (escaped) {
                    if (c == 'n') value.append('\n');
                    else if (c == 'r') value.append('\r');
                    else if (c == 't') value.append('\t');
                    else value.append(c);
                    escaped = false;
                } else if (c == '\\') escaped = true;
                else if (c == '"') break;
                else value.append(c);
            }
            if (valueEnd >= end) throw new IllegalArgumentException("unterminated JSON string");
            values.add(value.toString());
            cursor = valueEnd + 1;
        }
        return Set.copyOf(values);
    }

    private static int fieldColon(String json, String key) {
        String marker = "\"" + key + "\"";
        int pos = json.indexOf(marker);
        return pos < 0 ? -1 : json.indexOf(':', pos + marker.length());
    }

    private static int skipWhitespace(String json, int start) {
        int cursor = start;
        while (cursor < json.length() && Character.isWhitespace(json.charAt(cursor))) cursor++;
        return cursor;
    }

    private record AuthorityResponse(int statusCode, String body) {
        private AuthorityResponse {
            if (statusCode < 100 || statusCode > 599 || body == null) {
                throw new IllegalArgumentException("invalid authority response");
            }
        }
    }

}
