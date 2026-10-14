package de.legitserver.assistant.security;

import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.actor.ActorType;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.core.assistant.AssistantCapabilities;
import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.core.assistant.AssistantDescriptor;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.security.authn.BoundedReplayProtector;
import de.legitserver.assistant.security.authn.HmacServiceTokenService;
import de.legitserver.assistant.security.authn.ServiceAuthenticationStatus;
import de.legitserver.assistant.security.authn.ServicePrincipal;
import de.legitserver.assistant.security.authn.ServiceScopes;
import de.legitserver.assistant.security.authn.ServiceTokenClaims;
import de.legitserver.assistant.security.authorization.ActorResolution;
import de.legitserver.assistant.security.authorization.AssistantAuthorizationService;
import de.legitserver.assistant.security.authorization.AssistantSecurityCatalog;
import de.legitserver.assistant.security.authorization.AuthoritativeActorResolverPort;
import de.legitserver.assistant.security.authorization.AuthoritativeSecuritySnapshot;
import de.legitserver.assistant.security.authorization.AuthoritativeSecuritySnapshotPort;
import de.legitserver.assistant.security.authorization.AuthorizationDecision;
import de.legitserver.assistant.security.authorization.AuthorizationDenialReason;
import de.legitserver.assistant.security.authorization.AuthorizationRequest;
import de.legitserver.assistant.security.authorization.ResourceAuthorizationStatus;
import de.legitserver.assistant.security.authorization.ResourceRef;
import de.legitserver.assistant.security.authorization.ResolvedActorIdentity;
import de.legitserver.assistant.security.authorization.SecuritySnapshotLookup;
import de.legitserver.assistant.security.authorization.StaticAssistantCapabilityPolicy;
import de.legitserver.assistant.security.cache.CachingSecuritySnapshotPort;
import de.legitserver.assistant.security.control.InMemorySecurityControlPlane;
import de.legitserver.assistant.security.control.OperationalMode;
import de.legitserver.assistant.security.control.SecurityFeature;
import de.legitserver.assistant.security.control.SecurityKillSwitch;
import de.legitserver.assistant.security.secret.SecretGuard;
import de.legitserver.assistant.security.secret.SecretKind;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Framework-free Phase-3 security foundation verification. */
public final class Phase3SecuritySelfTest {
    private static final Instant NOW = Instant.parse("2026-10-03T19:00:00Z");
    private static final UUID UUID_ACTOR = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final ActorId ACTOR_ID = new ActorId("actor:11111111-2222-3333-4444-555555555555");
    private static final String NETWORK_ID = "network:11111111-2222-3333-4444-555555555555";

    private Phase3SecuritySelfTest() {}

    public static void main(String[] args) {
        verifyServiceAuthentication();
        verifyAuthorizationPipeline();
        verifySecurityCache();
        verifySecretGuard();
        verifyCatalogCompleteness();
        System.out.println("PHASE3_SECURITY_SELF_TEST: PASS");
    }

    private static void verifyServiceAuthentication() {
        byte[] key = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        RequestId requestId = new RequestId("req-phase3-auth");
        ServiceTokenClaims claims = new ServiceTokenClaims(
                "panel-backend",
                "legit-assistant",
                Set.of(ServiceScopes.INVOKE),
                NOW.minusSeconds(1),
                NOW.plusSeconds(30),
                "jti-phase3-1",
                requestId);
        HmacServiceTokenService service = new HmacServiceTokenService(key, new BoundedReplayProtector(64));
        String token = service.issue(claims);
        var first = service.verify(token, "legit-assistant", requestId, Set.of(ServiceScopes.INVOKE), NOW);
        require(first.authenticated(), "valid service token must authenticate");
        require(first.principal().serviceId().equals("panel-backend"), "service identity must survive verification");
        var replay = service.verify(token, "legit-assistant", requestId, Set.of(ServiceScopes.INVOKE), NOW);
        require(replay.status() == ServiceAuthenticationStatus.REPLAYED, "replayed token must be denied");

        HmacServiceTokenService freshVerifier = new HmacServiceTokenService(key, new BoundedReplayProtector(64));
        require(
                freshVerifier.verify(token, "wrong-audience", requestId, Set.of(ServiceScopes.INVOKE), NOW).status()
                        == ServiceAuthenticationStatus.AUDIENCE_MISMATCH,
                "wrong audience must fail");
        require(
                freshVerifier.verify(token, "legit-assistant", new RequestId("req-other"), Set.of(ServiceScopes.INVOKE), NOW).status()
                        == ServiceAuthenticationStatus.REQUEST_MISMATCH,
                "request binding must fail closed");

        ServiceTokenClaims expired = new ServiceTokenClaims(
                "panel-backend",
                "legit-assistant",
                Set.of(ServiceScopes.INVOKE),
                NOW.minusSeconds(60),
                NOW.minusSeconds(30),
                "jti-phase3-expired",
                requestId);
        String expiredToken = freshVerifier.issue(expired);
        require(
                freshVerifier.verify(expiredToken, "legit-assistant", requestId, Set.of(ServiceScopes.INVOKE), NOW).status()
                        == ServiceAuthenticationStatus.EXPIRED,
                "expired token must fail");

        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");
        require(
                freshVerifier.verify(tampered, "legit-assistant", requestId, Set.of(ServiceScopes.INVOKE), NOW).status()
                        == ServiceAuthenticationStatus.SIGNATURE_INVALID,
                "tampered token must fail signature validation");
    }

    private static void verifyAuthorizationPipeline() {
        ActorContext actor = actorContext(ACTOR_ID, 1);
        ResolvedActorIdentity identity = new ResolvedActorIdentity(ACTOR_ID, NETWORK_ID, UUID_ACTOR, 1);
        AtomicReference<ActorResolution> actorResolution = new AtomicReference<>(ActorResolution.found(identity));
        AuthoritativeActorResolverPort resolver = ignored -> actorResolution.get();

        AtomicReference<SecuritySnapshotLookup> snapshot = new AtomicReference<>(SecuritySnapshotLookup.found(snapshot(
                1,
                Set.of(
                        "networkcore.assistant.use.buggy",
                        "networkcore.assistant.diagnostics.read",
                        AssistantSecurityCatalog.NETWORKCORE_DIAGNOSTICS,
                        "networkcore.assistant.bug.create",
                        "networkcore.assistant.knowledge.query"))));
        AuthoritativeSecuritySnapshotPort source = (uuid, minimumRevision) -> {
            SecuritySnapshotLookup current = snapshot.get();
            if (current.status() != de.legitserver.assistant.security.authorization.AuthorityLookupStatus.FOUND) return current;
            return current.snapshot().securityRevision() >= minimumRevision ? current : SecuritySnapshotLookup.unavailable();
        };
        CachingSecuritySnapshotPort cache = new CachingSecuritySnapshotPort(
                source, Duration.ofSeconds(5), 64, Clock.fixed(NOW, ZoneOffset.UTC));

        StaticAssistantCapabilityPolicy capabilities = new StaticAssistantCapabilityPolicy(Map.of(
                AssistantId.BUGGY,
                Set.of(AssistantCapabilities.DIAGNOSTICS_READ, AssistantCapabilities.BUG_CREATE, AssistantCapabilities.KNOWLEDGE_QUERY)));
        AtomicReference<ResourceAuthorizationStatus> resourceStatus = new AtomicReference<>(ResourceAuthorizationStatus.ALLOW);
        var resources = (de.legitserver.assistant.security.authorization.ResourceAuthorizationPort)
                (presented, security, capability, resource) -> resource == null
                        ? ResourceAuthorizationStatus.ALLOW
                        : resourceStatus.get();
        InMemorySecurityControlPlane controls = new InMemorySecurityControlPlane(
                Set.of(SecurityFeature.DIAGNOSTICS, SecurityFeature.SUPERVISOR), NOW);
        AssistantAuthorizationService authorization = new AssistantAuthorizationService(
                resolver, cache, capabilities, resources, controls);

        AssistantDescriptor buggy = descriptor(true);
        ServicePrincipal service = verifiedServicePrincipal();
        ResourceRef server = new ResourceRef("server", "survival", DataClassification.INTERNAL);
        AuthorizationRequest diagnostics = new AuthorizationRequest(
                service, actor, buggy, AssistantCapabilities.DIAGNOSTICS_READ, server);

        require(authorization.authorize(diagnostics).allowed(), "fully authorized diagnostics must pass");

        ActorContext forged = actorContext(new ActorId("actor:forged"), 1);
        AuthorizationDecision forgedDecision = authorization.authorize(new AuthorizationRequest(
                service, forged, buggy, AssistantCapabilities.DIAGNOSTICS_READ, server));
        require(
                forgedDecision.denialReason() == AuthorizationDenialReason.ACTOR_BINDING_MISMATCH,
                "forged actor context must be denied");

        actorResolution.set(ActorResolution.unavailable());
        require(
                authorization.authorize(diagnostics).denialReason() == AuthorizationDenialReason.AUTHORITY_UNAVAILABLE,
                "authority outage must fail closed");
        actorResolution.set(ActorResolution.found(identity));

        snapshot.set(SecuritySnapshotLookup.found(snapshot(
                2,
                Set.of("networkcore.assistant.use.buggy", "networkcore.assistant.diagnostics.read", "networkcore.assistant.bug.create", "networkcore.assistant.knowledge.query"))));
        cache.invalidate(UUID_ACTOR);
        require(
                authorization.authorize(diagnostics).denialReason() == AuthorizationDenialReason.PERMISSION_DENIED,
                "revoked underlying NetworkCore permission must be respected after invalidation");

        snapshot.set(SecuritySnapshotLookup.found(snapshot(
                3,
                Set.of(
                        "networkcore.assistant.use.buggy",
                        "networkcore.assistant.diagnostics.read",
                        AssistantSecurityCatalog.NETWORKCORE_DIAGNOSTICS,
                        "networkcore.assistant.bug.create",
                        "networkcore.assistant.knowledge.query"))));
        cache.invalidate(UUID_ACTOR);
        controls.setKillSwitch(SecurityKillSwitch.DIAGNOSTICS_OFF, true, NOW.plusSeconds(1));
        require(
                authorization.authorize(diagnostics).denialReason() == AuthorizationDenialReason.KILL_SWITCH_ACTIVE,
                "diagnostics kill switch must stop access");
        controls.setKillSwitch(SecurityKillSwitch.DIAGNOSTICS_OFF, false, NOW.plusSeconds(2));

        controls.setOperationalMode(OperationalMode.READ_ONLY, NOW.plusSeconds(3));
        AuthorizationRequest bugCreate = new AuthorizationRequest(
                service, actor, buggy, AssistantCapabilities.BUG_CREATE, null);
        require(
                authorization.authorize(bugCreate).denialReason() == AuthorizationDenialReason.READ_ONLY_MODE,
                "read-only emergency mode must block mutating capabilities");
        require(authorization.authorize(diagnostics).allowed(), "read-only emergency mode may keep permitted reads available");

        controls.setOperationalMode(OperationalMode.MAINTENANCE, NOW.plusSeconds(4));
        AuthorizationRequest knowledge = new AuthorizationRequest(
                service, actor, buggy, AssistantCapabilities.KNOWLEDGE_QUERY, null);
        require(
                authorization.authorize(knowledge).denialReason() == AuthorizationDenialReason.MAINTENANCE_MODE,
                "maintenance mode must deny assistant actions");
        controls.setOperationalMode(OperationalMode.NORMAL, NOW.plusSeconds(5));

        resourceStatus.set(ResourceAuthorizationStatus.DENY);
        require(
                authorization.authorize(diagnostics).denialReason() == AuthorizationDenialReason.RESOURCE_DENIED,
                "resource denial must fail closed");
        resourceStatus.set(ResourceAuthorizationStatus.UNAVAILABLE);
        require(
                authorization.authorize(diagnostics).denialReason() == AuthorizationDenialReason.RESOURCE_AUTHORIZATION_UNAVAILABLE,
                "resource authority outage must fail closed");
    }

    private static void verifySecurityCache() {
        AtomicReference<SecuritySnapshotLookup> delegateValue = new AtomicReference<>(SecuritySnapshotLookup.found(snapshot(5, Set.of())));
        AuthoritativeSecuritySnapshotPort delegate = (uuid, revision) -> delegateValue.get();
        CachingSecuritySnapshotPort cache = new CachingSecuritySnapshotPort(
                delegate, Duration.ofSeconds(5), 16, Clock.fixed(NOW, ZoneOffset.UTC));
        require(cache.lookup(UUID_ACTOR, 5).status() == de.legitserver.assistant.security.authorization.AuthorityLookupStatus.FOUND, "first lookup");
        require(cache.lookup(UUID_ACTOR, 5).status() == de.legitserver.assistant.security.authorization.AuthorityLookupStatus.FOUND, "cached lookup");
        require(cache.metrics().hits() == 1 && cache.metrics().misses() == 1, "cache counters must track hit/miss");
        delegateValue.set(SecuritySnapshotLookup.found(snapshot(6, Set.of("networkcore.changed"))));
        cache.invalidate(UUID_ACTOR);
        require(cache.lookup(UUID_ACTOR, 6).snapshot().securityRevision() == 6, "invalidation must expose fresh revision");
        require(cache.metrics().invalidations() == 1, "invalidation metric");
    }

    private static void verifySecretGuard() {
        String jwt = "eyJabcdefghijk.abcdefghijklmnop.abcdefghijklmnop";
        String privateKey = "-----BEGIN PRIVATE KEY-----\nABCDEF123456\n-----END PRIVATE KEY-----";
        String input = "password=hunter2\nAuthorization: Bearer abcdefghijklmnopqrstuvwxyz\n"
                + "jdbc=postgres://admin:supersecret@db.internal/app\n"
                + jwt + "\n" + privateKey;
        var result = new SecretGuard().redact(input);
        require(result.secretDetected(), "secret guard must detect secret material");
        require(result.secretKinds().contains(SecretKind.NAMED_SECRET), "named secret detected");
        require(result.secretKinds().contains(SecretKind.BEARER_TOKEN), "bearer token detected");
        require(result.secretKinds().contains(SecretKind.URL_CREDENTIAL), "URL credential detected");
        require(result.secretKinds().contains(SecretKind.JWT), "JWT detected");
        require(result.secretKinds().contains(SecretKind.PRIVATE_KEY), "private key detected");
        require(!result.redactedText().contains("hunter2"), "password must be redacted");
        require(!result.redactedText().contains("supersecret"), "URL password must be redacted");
        require(!result.redactedText().contains(jwt), "JWT must be redacted");
        require(!result.redactedText().contains("ABCDEF123456"), "private key body must be redacted");
    }

    private static void verifyCatalogCompleteness() {
        require(
                AssistantSecurityCatalog.rules().keySet().containsAll(AssistantCapabilities.initialNamespace()),
                "every initial Foundation capability must retain a security rule");
        AssistantCapabilityId future = new AssistantCapabilityId("assistant.future.capability");
        require(AssistantSecurityCatalog.rule(future).isEmpty(), "unknown future capability must deny by default");
    }

    private static ActorContext actorContext(ActorId actorId, long revision) {
        return new ActorContext(
                actorId,
                ActorType.HUMAN,
                Origin.PANEL,
                NETWORK_ID,
                UUID_ACTOR,
                "panel-user-1",
                null,
                new RequestId("req-phase3-authz"),
                new TraceId("trace-phase3-authz"),
                "survival",
                revision,
                NOW);
    }

    private static AssistantDescriptor descriptor(boolean enabled) {
        return new AssistantDescriptor(
                AssistantId.BUGGY,
                "BuggyBot",
                enabled,
                "buggy-phase3",
                "default-security",
                "buggy-knowledge",
                "default-runtime",
                null,
                1,
                NOW);
    }

    private static ServicePrincipal verifiedServicePrincipal() {
        byte[] key = "abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8);
        HmacServiceTokenService tokens = new HmacServiceTokenService(key, new BoundedReplayProtector(64));
        RequestId requestId = new RequestId("req-phase3-service-principal");
        String token = tokens.issue(new ServiceTokenClaims(
                "panel-backend",
                "legit-assistant",
                Set.of(ServiceScopes.INVOKE),
                NOW.minusSeconds(1),
                NOW.plusSeconds(30),
                "jti-authorized-service",
                requestId));
        var verified = tokens.verify(token, "legit-assistant", requestId, Set.of(ServiceScopes.INVOKE), NOW);
        if (!verified.authenticated()) throw new IllegalStateException("test service credential did not verify");
        return verified.principal();
    }

    private static AuthoritativeSecuritySnapshot snapshot(long revision, Set<String> permissions) {
        return new AuthoritativeSecuritySnapshot(
                UUID_ACTOR,
                "developer",
                900,
                "DEVELOPMENT",
                Set.of("DEVELOPMENT"),
                Set.of("usr"),
                permissions,
                Set.of(),
                Set.of(),
                false,
                revision,
                NOW);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
