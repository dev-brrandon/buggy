package de.legitserver.assistant.hardening;

import de.legitserver.assistant.contracts.source.SourcePath;
import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.actor.ActorType;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.core.assistant.AssistantCapabilities;
import de.legitserver.assistant.core.assistant.AssistantDescriptor;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.core.request.RequestContext;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.observability.audit.AuditAppendResult;
import de.legitserver.assistant.observability.audit.AuditPort;
import de.legitserver.assistant.observability.audit.AuditRecorder;
import de.legitserver.assistant.panel.client.PanelAssistantHttpClient;
import de.legitserver.assistant.panel.client.PanelHttpRequest;
import de.legitserver.assistant.panel.security.HmacPanelUserContextService;
import de.legitserver.assistant.panel.security.PanelGatewayRequestSigner;
import de.legitserver.assistant.pipeline.policy.RateLimitKey;
import de.legitserver.assistant.pipeline.policy.SlidingWindowRateLimiter;
import de.legitserver.assistant.requests.model.ApprovalDecision;
import de.legitserver.assistant.requests.model.ApprovalRequestId;
import de.legitserver.assistant.requests.model.RequestApproval;
import de.legitserver.assistant.requests.model.RequestDraft;
import de.legitserver.assistant.requests.model.RequestHistoryEvent;
import de.legitserver.assistant.requests.model.RequestPayload;
import de.legitserver.assistant.requests.model.RequestRecord;
import de.legitserver.assistant.requests.model.RequestRiskLevel;
import de.legitserver.assistant.requests.model.RequestState;
import de.legitserver.assistant.requests.policy.RequestApprovalPolicy;
import de.legitserver.assistant.requests.policy.RequestLimits;
import de.legitserver.assistant.requests.repository.DuplicateApprovalException;
import de.legitserver.assistant.requests.repository.RequestRepository;
import de.legitserver.assistant.requests.repository.RequestRevisionConflictException;
import de.legitserver.assistant.requests.service.RequestExpiredException;
import de.legitserver.assistant.requests.service.RequestService;
import de.legitserver.assistant.requests.service.SelfApprovalDeniedException;
import de.legitserver.assistant.requests.validation.RequestValidation;
import de.legitserver.assistant.runtime.schema.ToolFieldSchema;
import de.legitserver.assistant.runtime.schema.ToolSchema;
import de.legitserver.assistant.runtime.schema.ToolSchemaValidator;
import de.legitserver.assistant.runtime.schema.ToolSchemaViolationException;
import de.legitserver.assistant.runtime.schema.ToolValueType;
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
import de.legitserver.assistant.security.authorization.AuthorizationDenialReason;
import de.legitserver.assistant.security.authorization.AuthorizationRequest;
import de.legitserver.assistant.security.authorization.ResourceAuthorizationStatus;
import de.legitserver.assistant.security.authorization.ResourceRef;
import de.legitserver.assistant.security.authorization.ResolvedActorIdentity;
import de.legitserver.assistant.security.authorization.SecuritySnapshotLookup;
import de.legitserver.assistant.security.authorization.StaticAssistantCapabilityPolicy;
import de.legitserver.assistant.security.cache.CachingSecuritySnapshotPort;
import de.legitserver.assistant.security.control.InMemorySecurityControlPlane;
import de.legitserver.assistant.security.control.SecurityFeature;
import de.legitserver.assistant.security.secret.SecretGuard;
import de.legitserver.assistant.supervisor.selfhealing.SelfHealingBudgetTracker;
import de.legitserver.assistant.supervisor.selfhealing.SelfHealingPolicy;
import de.legitserver.assistant.supervisor.selfhealing.SelfHealingSuppressionReason;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Explicit Phase-16 security attack matrix. */
final class SecurityAttackSuite {
    private static final Instant NOW = Instant.parse("2026-10-04T15:00:00Z");
    private static final UUID ACTOR_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final ActorId ACTOR_ID = new ActorId("actor:11111111-2222-3333-4444-555555555555");
    private static final String NETWORK_ID = "network:11111111-2222-3333-4444-555555555555";

    private SecurityAttackSuite() {}

    static void run(Path projectRoot) throws Exception {
        forgedActor();
        revokedPermission();
        networkCoreDown();
        tokenReplay();
        selfApproval();
        expiredApproval();
        secretLeakage();
        ssrf();
        pathTraversal();
        toolInjection(projectRoot);
        payloadAbuse();
        rateLimitBypass();
        selfHealingLoop();
        System.out.println("PHASE16_SECURITY_SUITE: PASS attacks=13");
    }

    private static void forgedActor() {
        SecurityFixture fixture = new SecurityFixture();
        ActorContext forged = fixture.actor(new ActorId("actor:forged"), 1);
        var decision = fixture.authorization.authorize(new AuthorizationRequest(
                fixture.principal, forged, fixture.assistant, AssistantCapabilities.DIAGNOSTICS_READ, fixture.resource));
        require(decision.denialReason() == AuthorizationDenialReason.ACTOR_BINDING_MISMATCH,
                "forged actor must be denied");
        pass("forged actor");
    }

    private static void revokedPermission() {
        SecurityFixture fixture = new SecurityFixture();
        fixture.snapshot.set(SecuritySnapshotLookup.found(fixture.snapshot(2, Set.of(
                "networkcore.assistant.use.buggy",
                "networkcore.assistant.diagnostics.read"))));
        fixture.cache.invalidate(ACTOR_UUID);
        var decision = fixture.authorization.authorize(fixture.request(fixture.actor));
        require(decision.denialReason() == AuthorizationDenialReason.PERMISSION_DENIED,
                "revoked permission must fail closed");
        pass("revoked permission");
    }

    private static void networkCoreDown() {
        SecurityFixture fixture = new SecurityFixture();
        fixture.actorResolution.set(ActorResolution.unavailable());
        var decision = fixture.authorization.authorize(fixture.request(fixture.actor));
        require(decision.denialReason() == AuthorizationDenialReason.AUTHORITY_UNAVAILABLE,
                "NetworkCore outage must fail closed");
        pass("NetworkCore down");
    }

    private static void tokenReplay() {
        byte[] key = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        HmacServiceTokenService service = new HmacServiceTokenService(key, new BoundedReplayProtector(64));
        RequestId requestId = new RequestId("req-phase16-replay");
        String token = service.issue(new ServiceTokenClaims(
                "panel-backend", "legit-assistant", Set.of(ServiceScopes.INVOKE), NOW.minusSeconds(1),
                NOW.plusSeconds(30), "jti-phase16-replay", requestId));
        require(service.verify(token, "legit-assistant", requestId, Set.of(ServiceScopes.INVOKE), NOW).authenticated(),
                "first token use must authenticate");
        require(service.verify(token, "legit-assistant", requestId, Set.of(ServiceScopes.INVOKE), NOW).status()
                        == ServiceAuthenticationStatus.REPLAYED,
                "replayed token must be denied");
        pass("token replay");
    }

    private static void selfApproval() {
        MemoryRequestRepository repository = new MemoryRequestRepository();
        RequestService service = requestService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        RequestRecord request = service.create(
                draft(RequestRiskLevel.HIGH, NOW.plusSeconds(120), "{\"action\":\"restart\"}"),
                requestContext(ACTOR_ID, "self-create", NOW));
        expectFailure(SelfApprovalDeniedException.class, () -> service.approve(
                request.requestId(), request.revision(), requestContext(ACTOR_ID, "self-approve", NOW), "self"));
        require(service.findById(request.requestId()).orElseThrow().revision() == 1,
                "self approval denial must not mutate request");
        pass("self approval");
    }

    private static void expiredApproval() {
        MemoryRequestRepository repository = new MemoryRequestRepository();
        RequestService creator = requestService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        RequestRecord request = creator.create(
                draft(RequestRiskLevel.CRITICAL, NOW.plusSeconds(30), "{\"action\":\"critical\"}"),
                requestContext(ACTOR_ID, "expiry-create", NOW));
        RequestService expired = requestService(repository, Clock.fixed(NOW.plusSeconds(31), ZoneOffset.UTC));
        ActorId approver = new ActorId("actor:approver");
        expectFailure(RequestExpiredException.class, () -> expired.approve(
                request.requestId(), request.revision(), requestContext(approver, "expiry-approve", NOW.plusSeconds(31)), "late"));
        require(expired.findById(request.requestId()).orElseThrow().state() == RequestState.EXPIRED,
                "expired approval must transition request to EXPIRED");
        pass("expired approval");
    }

    private static void secretLeakage() {
        String secret = "password=hunter2 Authorization: Bearer abcdefghijklmnopqrstuvwxyz jdbc=postgres://admin:supersecret@db/app";
        var result = new SecretGuard().redact(secret);
        require(result.secretDetected(), "secret guard must detect secrets");
        require(!result.redactedText().contains("hunter2") && !result.redactedText().contains("supersecret"),
                "secret values must not leak");
        pass("secret leakage");
    }

    private static void ssrf() {
        byte[] serviceKey = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        byte[] userKey = "abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8);
        HmacServiceTokenService serviceTokens = new HmacServiceTokenService(serviceKey, new BoundedReplayProtector(64));
        HmacPanelUserContextService userContexts = new HmacPanelUserContextService(userKey, new BoundedReplayProtector(64));
        PanelGatewayRequestSigner signer = new PanelGatewayRequestSigner(
                serviceTokens, userContexts, "panel-backend", Set.of(ServiceScopes.INVOKE),
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(30));
        expectFailure(IllegalArgumentException.class, () -> new PanelAssistantHttpClient(
                URI.create("http://169.254.169.254/latest/meta-data/"), HttpClient.newHttpClient(), signer, Duration.ofSeconds(2)));
        expectFailure(IllegalArgumentException.class,
                () -> new PanelHttpRequest("GET", "//169.254.169.254/latest/meta-data", "", null));
        pass("SSRF");
    }

    private static void pathTraversal() {
        expectFailure(IllegalArgumentException.class, () -> new SourcePath("../../etc/passwd"));
        expectFailure(IllegalArgumentException.class, () -> new SourcePath("safe/../secret"));
        expectFailure(IllegalArgumentException.class,
                () -> new PanelHttpRequest("GET", "/api/assistant/v1/../../actuator", "", null));
        pass("path traversal");
    }

    private static void toolInjection(Path projectRoot) throws Exception {
        ToolSchema schema = ToolSchema.of(new ToolFieldSchema("action", ToolValueType.STRING, true, 64));
        ToolSchemaValidator validator = new ToolSchemaValidator();
        expectFailure(ToolSchemaViolationException.class,
                () -> validator.validate(schema, Map.of("action", "status", "command", "rm -rf /")));
        List<String> forbidden = List.of("ProcessBuilder(", "Runtime.getRuntime().exec", ".exec(\"");
        try (var paths = Files.walk(projectRoot)) {
            List<Path> production = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> !path.toString().contains("/assistant-hardening/"))
                    .toList();
            for (Path path : production) {
                String text = Files.readString(path);
                for (String needle : forbidden) {
                    require(!text.contains(needle), "forbidden executable primitive in production source: " + path + " -> " + needle);
                }
            }
        }
        pass("tool injection");
    }

    private static void payloadAbuse() {
        String oversized = "x".repeat(RequestLimits.MAX_PAYLOAD_UTF8_BYTES + 1);
        expectFailure(IllegalArgumentException.class, () -> RequestValidation.payloadBody(oversized));
        String httpOversized = "x".repeat(1_048_577);
        expectFailure(IllegalArgumentException.class,
                () -> new PanelHttpRequest("POST", "/api/assistant/v1/bugs", httpOversized, "idem-phase16"));
        pass("payload abuse");
    }

    private static void rateLimitBypass() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(
                3, Duration.ofMinutes(1), 16, Clock.fixed(NOW, ZoneOffset.UTC));
        RateLimitKey key = new RateLimitKey(ACTOR_ID, AssistantId.BUGGY, AssistantCapabilities.DIAGNOSTICS_READ);
        require(limiter.tryAcquire(key) && limiter.tryAcquire(key) && limiter.tryAcquire(key),
                "first requests must fit rate budget");
        for (int i = 0; i < 50; i++) {
            RateLimitKey equivalent = new RateLimitKey(new ActorId(ACTOR_ID.value()), AssistantId.BUGGY, AssistantCapabilities.DIAGNOSTICS_READ);
            require(!limiter.tryAcquire(equivalent), "equivalent key must not bypass rate limit");
        }
        pass("rate limit bypass");
    }

    private static void selfHealingLoop() {
        SelfHealingBudgetTracker tracker = new SelfHealingBudgetTracker();
        SelfHealingPolicy policy = new SelfHealingPolicy(
                2, Duration.ofSeconds(1), Duration.ofMinutes(1), 10, Duration.ofSeconds(1), 2);
        String fingerprint = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        require(tracker.reserve(AssistantId.BUGGY, fingerprint, NOW, policy).allowed(), "first self-heal attempt");
        require(tracker.reserve(AssistantId.BUGGY, fingerprint, NOW.plusSeconds(2), policy).allowed(), "second self-heal attempt");
        var third = tracker.reserve(AssistantId.BUGGY, fingerprint, NOW.plusSeconds(4), policy);
        require(!third.allowed() && third.denialReason() == SelfHealingSuppressionReason.MAX_ATTEMPTS_EXCEEDED,
                "self-healing loop must stop at max attempts");
        pass("self-healing loop");
    }

    private static RequestService requestService(MemoryRequestRepository repository, Clock clock) {
        AuditPort audit = record -> AuditAppendResult.recordedResult();
        return new RequestService(repository, new AuditRecorder(audit), new RequestApprovalPolicy(), clock);
    }

    private static RequestDraft draft(RequestRiskLevel risk, Instant expiresAt, String payload) {
        return new RequestDraft(
                "CONTROLLED_WRITE_REQUEST", risk, 1, "application/json", payload, expiresAt, DataClassification.INTERNAL);
    }

    private static RequestContext requestContext(ActorId actorId, String suffix, Instant now) {
        return new RequestContext(
                new RequestId("req:phase16:" + suffix), new TraceId("trace:phase16:" + suffix), actorId,
                Origin.INTERNAL, AssistantId.BYTE, null, "hardening", now);
    }

    private static void pass(String attack) {
        System.out.println("PHASE16_SECURITY_ATTACK: PASS " + attack);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Assertion failed: " + message);
    }

    private static <T extends Throwable> void expectFailure(Class<T> type, ThrowingRunnable operation) {
        try {
            operation.run();
            throw new IllegalStateException("Expected failure: " + type.getSimpleName());
        } catch (Throwable failure) {
            if (type.isInstance(failure)) return;
            if (failure instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Unexpected checked failure", failure);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    static final class SecurityFixture {
        private final AtomicReference<ActorResolution> actorResolution;
        private final AtomicReference<SecuritySnapshotLookup> snapshot;
        private final CachingSecuritySnapshotPort cache;
        private final AssistantAuthorizationService authorization;
        private final ActorContext actor;
        private final AssistantDescriptor assistant;
        private final ServicePrincipal principal;
        private final ResourceRef resource;

        SecurityFixture() {
            ResolvedActorIdentity identity = new ResolvedActorIdentity(ACTOR_ID, NETWORK_ID, ACTOR_UUID, 1);
            actorResolution = new AtomicReference<>(ActorResolution.found(identity));
            AuthoritativeActorResolverPort resolver = ignored -> actorResolution.get();
            snapshot = new AtomicReference<>(SecuritySnapshotLookup.found(snapshot(1, Set.of(
                    "networkcore.assistant.use.buggy",
                    "networkcore.assistant.diagnostics.read",
                    AssistantSecurityCatalog.NETWORKCORE_DIAGNOSTICS))));
            AuthoritativeSecuritySnapshotPort source = (uuid, minimumRevision) -> {
                SecuritySnapshotLookup current = snapshot.get();
                if (current.status() != de.legitserver.assistant.security.authorization.AuthorityLookupStatus.FOUND) return current;
                return current.snapshot().securityRevision() >= minimumRevision ? current : SecuritySnapshotLookup.unavailable();
            };
            cache = new CachingSecuritySnapshotPort(source, Duration.ofSeconds(5), 64, Clock.fixed(NOW, ZoneOffset.UTC));
            StaticAssistantCapabilityPolicy capabilities = new StaticAssistantCapabilityPolicy(Map.of(
                    AssistantId.BUGGY, Set.of(AssistantCapabilities.DIAGNOSTICS_READ)));
            InMemorySecurityControlPlane controls = new InMemorySecurityControlPlane(Set.of(SecurityFeature.DIAGNOSTICS), NOW);
            authorization = new AssistantAuthorizationService(
                    resolver, cache, capabilities,
                    (presented, security, capability, ref) -> ResourceAuthorizationStatus.ALLOW,
                    controls);
            actor = actor(ACTOR_ID, 1);
            assistant = new AssistantDescriptor(
                    AssistantId.BUGGY, "BuggyBot", true, "buggy-phase16", "default-security",
                    "buggy-knowledge", "default-runtime", null, 1, NOW);
            principal = verifiedPrincipal();
            resource = new ResourceRef("server", "survival", DataClassification.INTERNAL);
        }

        boolean authorizeOnce() {
            return authorization.authorize(request(actor)).allowed();
        }

        private AuthorizationRequest request(ActorContext currentActor) {
            return new AuthorizationRequest(principal, currentActor, assistant, AssistantCapabilities.DIAGNOSTICS_READ, resource);
        }

        private ActorContext actor(ActorId actorId, long revision) {
            return new ActorContext(
                    actorId, ActorType.HUMAN, Origin.PANEL, NETWORK_ID, ACTOR_UUID, "panel-user-1", null,
                    new RequestId("req-phase16-authz"), new TraceId("trace-phase16-authz"), "survival", revision, NOW);
        }

        private AuthoritativeSecuritySnapshot snapshot(long revision, Set<String> permissions) {
            return new AuthoritativeSecuritySnapshot(
                    ACTOR_UUID, "developer", 900, "DEVELOPMENT", Set.of("DEVELOPMENT"), Set.of("usr"),
                    permissions, Set.of(), Set.of(), false, revision, NOW);
        }

        private ServicePrincipal verifiedPrincipal() {
            byte[] key = "abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8);
            HmacServiceTokenService tokens = new HmacServiceTokenService(key, new BoundedReplayProtector(64));
            RequestId requestId = new RequestId("req-phase16-service");
            String token = tokens.issue(new ServiceTokenClaims(
                    "panel-backend", "legit-assistant", Set.of(ServiceScopes.INVOKE), NOW.minusSeconds(1),
                    NOW.plusSeconds(30), "jti-phase16-service", requestId));
            var verified = tokens.verify(token, "legit-assistant", requestId, Set.of(ServiceScopes.INVOKE), NOW);
            if (!verified.authenticated()) throw new IllegalStateException("test service credential did not verify");
            return verified.principal();
        }
    }

    private static final class MemoryRequestRepository implements RequestRepository {
        private final Map<ApprovalRequestId, RequestRecord> requests = new LinkedHashMap<>();
        private final List<RequestPayload> payloads = new ArrayList<>();
        private final List<RequestApproval> approvals = new ArrayList<>();
        private final List<RequestHistoryEvent> history = new ArrayList<>();

        @Override
        public synchronized void create(RequestRecord request, RequestPayload payload, RequestHistoryEvent createdEvent) {
            if (requests.putIfAbsent(request.requestId(), request) != null) throw new IllegalStateException("duplicate request");
            payloads.add(payload);
            history.add(createdEvent);
        }

        @Override
        public synchronized Optional<RequestRecord> findById(ApprovalRequestId requestId) {
            return Optional.ofNullable(requests.get(requestId));
        }

        @Override
        public synchronized Optional<RequestPayload> activePayload(ApprovalRequestId requestId) {
            RequestRecord head = requests.get(requestId);
            if (head == null) return Optional.empty();
            return payloads.stream().filter(value -> value.requestId().equals(requestId)
                    && value.payloadRevision() == head.activePayloadRevision()).findFirst();
        }

        @Override
        public synchronized long countApproved(ApprovalRequestId requestId, long payloadRevision, String payloadSha256) {
            return approvals.stream().filter(value -> value.requestId().equals(requestId)
                    && value.payloadRevision() == payloadRevision
                    && value.payloadSha256().equals(payloadSha256)
                    && value.decision() == ApprovalDecision.APPROVE).count();
        }

        @Override
        public synchronized RequestRecord revisePayload(
                RequestRecord updated, long expectedRevision, RequestPayload payload, RequestHistoryEvent event) {
            requireRevision(updated.requestId(), expectedRevision);
            requests.put(updated.requestId(), updated);
            payloads.add(payload);
            history.add(event);
            return updated;
        }

        @Override
        public synchronized RequestRecord recordDecision(
                RequestRecord updated, long expectedRevision, RequestApproval approval, RequestHistoryEvent event) {
            requireRevision(updated.requestId(), expectedRevision);
            boolean duplicate = approvals.stream().anyMatch(existing -> existing.requestId().equals(approval.requestId())
                    && existing.payloadRevision() == approval.payloadRevision()
                    && existing.approverActorId().equals(approval.approverActorId()));
            if (duplicate) throw new DuplicateApprovalException(
                    approval.requestId(), approval.payloadRevision(), approval.approverActorId());
            requests.put(updated.requestId(), updated);
            approvals.add(approval);
            history.add(event);
            return updated;
        }

        @Override
        public synchronized RequestRecord transition(
                RequestRecord updated, long expectedRevision, RequestHistoryEvent event) {
            requireRevision(updated.requestId(), expectedRevision);
            requests.put(updated.requestId(), updated);
            history.add(event);
            return updated;
        }

        @Override
        public synchronized List<RequestApproval> approvals(ApprovalRequestId requestId, int limit) {
            return approvals.stream().filter(value -> value.requestId().equals(requestId)).limit(limit).toList();
        }

        @Override
        public synchronized List<RequestHistoryEvent> history(ApprovalRequestId requestId, int limit) {
            return history.stream().filter(value -> value.requestId().equals(requestId)).limit(limit).toList();
        }

        private void requireRevision(ApprovalRequestId requestId, long expectedRevision) {
            RequestRecord current = requests.get(requestId);
            if (current == null || current.revision() != expectedRevision) {
                throw new RequestRevisionConflictException(requestId, expectedRevision);
            }
        }
    }
}
