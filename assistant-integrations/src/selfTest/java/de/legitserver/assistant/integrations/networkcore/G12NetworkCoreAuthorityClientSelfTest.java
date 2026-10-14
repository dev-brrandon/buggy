package de.legitserver.assistant.integrations.networkcore;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.actor.ActorType;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.security.authn.BoundedReplayProtector;
import de.legitserver.assistant.security.authn.HmacServiceTokenService;
import de.legitserver.assistant.security.authn.ServiceAuthenticationStatus;
import de.legitserver.assistant.security.authn.ServiceScope;
import de.legitserver.assistant.security.authorization.AuthorityLookupStatus;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Set;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** G1.2 HTTP contract acceptance for request-bound Assistant -> NetworkCore authority lookups. */
public final class G12NetworkCoreAuthorityClientSelfTest {
    private static final Instant NOW = Instant.parse("2026-10-05T21:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID ACTOR_UUID = UUID.fromString("c1120ac8-9dde-4ae3-a17c-0fe073e6b013");
    private static final byte[] KEY = key();

    private G12NetworkCoreAuthorityClientSelfTest() {}

    public static void main(String[] args) throws Exception {
        verifyRoundTripAndRequestBoundHmac();
        verifyNonLoopbackPlainHttpIsRejected();
        System.out.println("G1_2_NETWORKCORE_AUTHORITY_CLIENT_SELF_TEST: PASS");
    }

    private static void verifyRoundTripAndRequestBoundHmac() throws Exception {
        AtomicInteger authenticated = new AtomicInteger();
        HmacServiceTokenService verifier = new HmacServiceTokenService(KEY, new BoundedReplayProtector(32));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 8);
        server.createContext("/internal/assistant/v1/authority/actor", exchange -> {
            try {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                authenticate(exchange, verifier, authenticated, body);
                require(body.contains(ACTOR_UUID.toString()), "actor request contains canonical network identity");
                send(exchange, 200, "{\"status\":\"FOUND\",\"actorId\":\"" + ACTOR_UUID
                        + "\",\"networkIdentityId\":\"" + ACTOR_UUID
                        + "\",\"minecraftUuid\":\"" + ACTOR_UUID
                        + "\",\"authorizationRevision\":44}");
            } finally {
                exchange.close();
            }
        });
        server.createContext("/internal/assistant/v1/authority/snapshot", exchange -> {
            try {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                authenticate(exchange, verifier, authenticated, body);
                require(body.contains("\"minimumRevision\":44"), "snapshot request carries minimum authority revision");
                send(exchange, 200, "{\"status\":\"FOUND\",\"snapshot\":{" +
                        "\"actorUuid\":\"" + ACTOR_UUID + "\"," +
                        "\"rankId\":\"owner\"," +
                        "\"rankWeight\":1000," +
                        "\"primaryDomainId\":\"owner\"," +
                        "\"domainIds\":[\"owner\"]," +
                        "\"bundleIds\":[\"buggy-delegate-development\",\"buggy-channel-panel\"]," +
                        "\"effectivePermissions\":[\"networkcore.assistant.use.buggy\",\"networkcore.assistant.buggy.channel.panel\",\"networkcore.assistant.buggy.knowledge.read\"]," +
                        "\"effectiveCapabilities\":[]," +
                        "\"panelCapabilities\":[]," +
                        "\"controlOwner\":true," +
                        "\"securityRevision\":44," +
                        "\"createdAt\":\"2026-10-05T21:00:00Z\"}}");
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            HttpNetworkCoreAuthorityClient client = new HttpNetworkCoreAuthorityClient(base, KEY, CLOCK);
            ActorContext presented = new ActorContext(
                    new ActorId(ACTOR_UUID.toString()),
                    ActorType.HUMAN,
                    Origin.PANEL,
                    ACTOR_UUID.toString(),
                    ACTOR_UUID,
                    "panel-owner",
                    null,
                    new RequestId("g12-authority-actor"),
                    new TraceId("g12-authority-trace"),
                    "network-01",
                    44,
                    NOW);
            NetworkCoreActorLookup actor = client.resolveActor(presented);
            require(actor.status() == AuthorityLookupStatus.FOUND, "actor authority lookup succeeds");
            require(actor.identity().actorId().equals(presented.actorId()), "actor binding is preserved");
            require(actor.identity().authorizationRevision() == 44, "actor authority revision is preserved");

            NetworkCoreSecurityLookup snapshot = client.securitySnapshot(ACTOR_UUID, 44);
            require(snapshot.status() == AuthorityLookupStatus.FOUND, "snapshot authority lookup succeeds");
            require(snapshot.snapshot().securityRevision() == 44, "snapshot revision is preserved");
            require(snapshot.snapshot().hasPermission("networkcore.assistant.buggy.channel.panel"),
                    "channel permission survives authority projection");
            require(authenticated.get() == 2, "both authority calls use valid request-bound HMAC tokens");
        } finally {
            server.stop(0);
        }
    }

    private static void verifyNonLoopbackPlainHttpIsRejected() {
        try {
            new HttpNetworkCoreAuthorityClient(URI.create("http://example.com:8767/"), KEY, CLOCK);
            throw new IllegalStateException("G1.2 authority self-test failed: non-loopback HTTP accepted");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void authenticate(
            HttpExchange exchange,
            HmacServiceTokenService verifier,
            AtomicInteger authenticated,
            String body) throws IOException {
        require("POST".equals(exchange.getRequestMethod()), "authority transport is POST only");
        String requestId = exchange.getRequestHeaders().getFirst("X-Request-Id");
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        require(requestId != null && !requestId.isBlank(), "request-bound id header is present");
        require(authorization != null && authorization.startsWith("Bearer "), "bearer HMAC token is present");
        String requestSignature = exchange.getRequestHeaders().getFirst("X-Request-Signature");
        require(requestSignature != null && !requestSignature.isBlank(), "body-bound request signature is present");
        require(verifyRequestSignature(exchange, requestId, body, requestSignature),
                "request signature binds method, path, request id and body digest");
        var result = verifier.verify(
                authorization.substring("Bearer ".length()),
                "networkcore",
                new RequestId(requestId),
                Set.of(new ServiceScope("assistant.authority.read")),
                NOW);
        require(result.status() == ServiceAuthenticationStatus.AUTHENTICATED,
                "authority HMAC token verifies with NetworkCore contract");
        require("legit-assistant".equals(result.principal().serviceId()), "authority token binds assistant service id");
        authenticated.incrementAndGet();
    }

    private static boolean verifyRequestSignature(
            HttpExchange exchange, String requestId, String body, String suppliedHeader) {
        try {
            String digest = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
            String canonical = exchange.getRequestMethod() + "\n" + exchange.getRequestURI().getPath()
                    + "\n" + requestId + "\n" + digest;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(KEY, "HmacSHA256"));
            byte[] expected = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            byte[] supplied = Base64.getUrlDecoder().decode(suppliedHeader);
            return MessageDigest.isEqual(expected, supplied);
        } catch (GeneralSecurityException | IllegalArgumentException failure) {
            return false;
        }
    }

    private static void send(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static byte[] key() {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 0x6d);
        return key;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("G1.2 authority self-test failed: " + message);
    }
}
