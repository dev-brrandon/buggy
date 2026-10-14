package de.legitserver.assistant.observability;

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
import de.legitserver.assistant.observability.audit.AuditAction;
import de.legitserver.assistant.observability.audit.AuditAppendResult;
import de.legitserver.assistant.observability.audit.AuditOutcome;
import de.legitserver.assistant.observability.audit.AuditRecord;
import de.legitserver.assistant.observability.audit.AuditRecorder;
import de.legitserver.assistant.observability.audit.AuditUnavailableException;
import de.legitserver.assistant.observability.authorization.ObservedAuthorizationService;
import de.legitserver.assistant.observability.health.AssistantHealthService;
import de.legitserver.assistant.observability.health.AssistantHealthStatus;
import de.legitserver.assistant.observability.health.DependencyHealthRegistry;
import de.legitserver.assistant.observability.health.DependencyId;
import de.legitserver.assistant.observability.health.DependencyState;
import de.legitserver.assistant.observability.logging.LogLevel;
import de.legitserver.assistant.observability.logging.SanitizedStructuredLogger;
import de.legitserver.assistant.observability.logging.StructuredLogEvent;
import de.legitserver.assistant.observability.logging.TelemetrySanitizer;
import de.legitserver.assistant.observability.metrics.AssistantMetric;
import de.legitserver.assistant.observability.metrics.InMemoryAssistantMetrics;
import de.legitserver.assistant.observability.metrics.MetricKind;
import de.legitserver.assistant.observability.tracing.CorrelationContextHolder;
import de.legitserver.assistant.observability.tracing.CorrelationIds;
import de.legitserver.assistant.security.authn.BoundedReplayProtector;
import de.legitserver.assistant.security.authn.HmacServiceTokenService;
import de.legitserver.assistant.security.authn.ServicePrincipal;
import de.legitserver.assistant.security.authn.ServiceScopes;
import de.legitserver.assistant.security.authn.ServiceTokenClaims;
import de.legitserver.assistant.security.authorization.ActorResolution;
import de.legitserver.assistant.security.authorization.AssistantAuthorizationService;
import de.legitserver.assistant.security.authorization.AuthoritativeSecuritySnapshot;
import de.legitserver.assistant.security.authorization.AuthorizationDenialReason;
import de.legitserver.assistant.security.authorization.AuthorizationRequest;
import de.legitserver.assistant.security.authorization.ResolvedActorIdentity;
import de.legitserver.assistant.security.authorization.ResourceAuthorizationStatus;
import de.legitserver.assistant.security.authorization.ResourceRef;
import de.legitserver.assistant.security.authorization.SecuritySnapshotLookup;
import de.legitserver.assistant.security.authorization.StaticAssistantCapabilityPolicy;
import de.legitserver.assistant.security.control.InMemorySecurityControlPlane;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Framework-free Phase-6 audit and observability verification. */
public final class Phase6ObservabilitySelfTest {
    private static final Instant NOW = Instant.parse("2026-10-04T00:00:00Z");
    private static final UUID ACTOR_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final ActorId ACTOR_ID = new ActorId("actor:11111111-2222-3333-4444-555555555555");

    private Phase6ObservabilitySelfTest() {}

    public static void main(String[] args) {
        verifySanitizedLogging();
        verifyAuditFailClosed();
        verifyMetricCatalogAndBounds();
        verifyDependencyHealth();
        verifyTraceCorrelation();
        verifySensitiveAuthorizationDenialAudit();
        System.out.println("PHASE6_OBSERVABILITY_SELF_TEST: PASS");
    }

    private static void verifySanitizedLogging() {
        TelemetrySanitizer sanitizer = TelemetrySanitizer.standard();
        String raw = "Bearer super-secret-token password=hunter2 postgres://admin:dbsecret@db/app";
        String sanitized = sanitizer.sanitizeValue(raw);
        require(!sanitized.contains("super-secret-token"), "bearer token must be redacted");
        require(!sanitized.contains("hunter2"), "password must be redacted");
        require(!sanitized.contains("dbsecret"), "URI credential must be redacted");

        ArrayList<StructuredLogEvent> captured = new ArrayList<>();
        SanitizedStructuredLogger logger = new SanitizedStructuredLogger(sanitizer, captured::add);
        logger.log(new StructuredLogEvent(
                NOW,
                LogLevel.INFO,
                new RequestId("req-phase6-log"),
                new TraceId("11111111111111111111111111111111"),
                "selftest",
                "log",
                "SUCCESS",
                Duration.ofMillis(3),
                AssistantId.BUGGY,
                ACTOR_ID,
                Map.of("authorization", "Bearer secret-token-value", "safe", "ok")));
        require(captured.size() == 1, "sanitized logger must emit exactly one event");
        require("[REDACTED]".equals(captured.getFirst().fields().get("authorization")), "sensitive key must be redacted");
        require("ok".equals(captured.getFirst().fields().get("safe")), "safe field must survive sanitization");
    }

    private static void verifyAuditFailClosed() {
        ArrayList<AuditRecord> records = new ArrayList<>();
        AuditRecorder recorder = new AuditRecorder(record -> {
            records.add(record);
            return AuditAppendResult.recordedResult();
        });
        AuditRecord critical = auditRecord(true);
        require(recorder.append(critical).recorded(), "recorded critical audit must pass");
        require(records.size() == 1, "audit port must append record");

        AuditRecorder unavailable = new AuditRecorder(record -> AuditAppendResult.unavailable());
        boolean blocked = false;
        try {
            unavailable.append(critical);
        } catch (AuditUnavailableException expected) {
            blocked = true;
        }
        require(blocked, "critical action must fail closed when audit is unavailable");
        require(!unavailable.append(auditRecord(false)).recorded(), "non-critical telemetry may degrade without becoming success");
    }

    private static AuditRecord auditRecord(boolean critical) {
        return new AuditRecord(
                AuditAction.WRITE_REQUEST,
                AuditOutcome.REQUESTED,
                new RequestId("req-phase6-audit"),
                new TraceId("22222222222222222222222222222222"),
                ACTOR_ID,
                ACTOR_UUID,
                AssistantId.BUGGY,
                "bug:123",
                "survival",
                "bug",
                "123",
                NOW,
                Map.of("password", "must-not-leak", "safe", "value"),
                critical);
    }

    private static void verifyMetricCatalogAndBounds() {
        InMemoryAssistantMetrics metrics = new InMemoryAssistantMetrics();
        metrics.increment(AssistantMetric.REQUEST_COUNT);
        metrics.increment(AssistantMetric.REQUEST_ERRORS);
        metrics.increment(AssistantMetric.AUTHORIZATION_DENIALS);
        metrics.increment(AssistantMetric.RATE_LIMIT_HITS);
        metrics.increment(AssistantMetric.TOOL_CALLS);
        metrics.increment(AssistantMetric.TOOL_FAILURES);
        metrics.increment(AssistantMetric.SELF_HEALING_ATTEMPTS);
        metrics.increment(AssistantMetric.SELF_HEALING_FAILURES);
        metrics.setGauge(AssistantMetric.ACTIVE_CONVERSATIONS, 7);
        metrics.setGauge(AssistantMetric.QUEUE_DEPTH, 3);
        metrics.setGauge(AssistantMetric.ASSISTANT_HEALTH, 1);
        metrics.recordLatency(AssistantMetric.REQUEST_LATENCY, Duration.ofMillis(10));
        metrics.recordLatency(AssistantMetric.REQUEST_LATENCY, Duration.ofMillis(20));
        metrics.recordLatency(AssistantMetric.DATABASE_LATENCY, Duration.ofMillis(30));
        metrics.recordLatency(AssistantMetric.NETWORKCORE_LATENCY, Duration.ofMillis(40));
        var snapshot = metrics.snapshot();
        for (AssistantMetric metric : AssistantMetric.values()) {
            if (metric.kind() == MetricKind.LATENCY) {
                require(snapshot.latencyValues().containsKey(metric), "latency metric missing: " + metric);
            } else {
                require(snapshot.scalarValues().containsKey(metric), "scalar metric missing: " + metric);
            }
        }
        require(snapshot.scalar(AssistantMetric.REQUEST_COUNT) == 1, "request counter must increment");
        require(snapshot.scalar(AssistantMetric.ACTIVE_CONVERSATIONS) == 7, "active conversation gauge must update");
        require(snapshot.latency(AssistantMetric.REQUEST_LATENCY).p95Ms() == 20, "latency p95 must be available");
    }

    private static void verifyDependencyHealth() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        DependencyHealthRegistry registry = new DependencyHealthRegistry(clock);
        AssistantHealthService health = new AssistantHealthService(registry, clock);
        require(!health.snapshot().ready(), "unconfigured required dependencies must not report ready");
        for (DependencyId dependency : DependencyId.values()) {
            require(registry.get(dependency).state() == DependencyState.NOT_CONFIGURED, "dependency must start NOT_CONFIGURED");
        }
        registry.update(DependencyId.NETWORKCORE, DependencyState.UP, "UP", 4);
        registry.update(DependencyId.DATABASE, DependencyState.UP, "UP", 7);
        require(health.snapshot().ready(), "required dependencies UP should make service ready");
        require(health.snapshot().status() == AssistantHealthStatus.DEGRADED, "unconfigured optional dependencies must remain visible");
        for (DependencyId dependency : DependencyId.values()) {
            registry.update(dependency, DependencyState.UP, "UP", 1);
        }
        require(health.snapshot().status() == AssistantHealthStatus.UP, "all dependencies UP should be healthy");
    }

    private static void verifyTraceCorrelation() {
        String traceId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        var correlation = CorrelationIds.resolve("req-client-1", traceId, null);
        require("req-client-1".equals(correlation.requestId().value()), "valid request id must propagate");
        require(traceId.equals(correlation.traceId().value()), "valid trace id must propagate");
        require(correlation.traceParent().startsWith("00-" + traceId + "-"), "traceparent must carry trace id");
        require(correlation.spanId().length() == 16, "span id must be W3C sized");

        var fallback = CorrelationIds.resolve("bad request id", null, "00-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb-cccccccccccccccc-01");
        require(!"bad request id".equals(fallback.requestId().value()), "invalid request id must be replaced");
        require("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".equals(fallback.traceId().value()), "traceparent trace id must be accepted");

        CorrelationContextHolder.Scope scope = CorrelationContextHolder.open(correlation);
        try {
            require(CorrelationContextHolder.current().orElseThrow().equals(correlation), "correlation scope must be visible");
        } finally {
            scope.close();
        }
        require(CorrelationContextHolder.current().isEmpty(), "correlation scope must be cleared");
    }

    private static void verifySensitiveAuthorizationDenialAudit() {
        ActorContext actor = actorContext();
        ResolvedActorIdentity identity = new ResolvedActorIdentity(ACTOR_ID, "network:actor-1", ACTOR_UUID, 1);
        AuthoritativeSecuritySnapshot snapshot = new AuthoritativeSecuritySnapshot(
                ACTOR_UUID,
                "usr",
                0,
                null,
                Set.of(),
                Set.of("usr"),
                Set.of("networkcore.assistant.use.buggy"),
                Set.of(),
                Set.of(),
                false,
                1,
                NOW);
        AssistantAuthorizationService delegate = new AssistantAuthorizationService(
                ignored -> ActorResolution.found(identity),
                (uuid, revision) -> SecuritySnapshotLookup.found(snapshot),
                new StaticAssistantCapabilityPolicy(Map.of(AssistantId.BUGGY, Set.of(AssistantCapabilities.DIAGNOSTICS_READ))),
                (presented, security, capability, resource) -> ResourceAuthorizationStatus.ALLOW,
                new InMemorySecurityControlPlane(Set.of(), NOW));
        ArrayList<AuditRecord> audits = new ArrayList<>();
        AuditRecorder audit = new AuditRecorder(record -> {
            audits.add(record);
            return AuditAppendResult.recordedResult();
        });
        InMemoryAssistantMetrics metrics = new InMemoryAssistantMetrics();
        SanitizedStructuredLogger logger = new SanitizedStructuredLogger(TelemetrySanitizer.standard(), event -> {});
        ObservedAuthorizationService observed = new ObservedAuthorizationService(delegate, audit, metrics, logger);
        RequestContext context = new RequestContext(
                actor.requestId(), actor.traceId(), actor.actorId(), actor.origin(), AssistantId.BUGGY, null, "survival", NOW);
        AuthorizationRequest request = new AuthorizationRequest(
                verifiedServicePrincipal(),
                actor,
                descriptor(),
                AssistantCapabilities.DIAGNOSTICS_READ,
                new ResourceRef("server", "survival", DataClassification.INTERNAL));
        var decision = observed.authorize(context, request);
        require(decision.denialReason() == AuthorizationDenialReason.PERMISSION_DENIED, "missing permission must deny");
        require(audits.size() == 1, "sensitive authorization denial must be audited");
        require(audits.getFirst().action() == AuditAction.SENSITIVE_AUTHORIZATION_DENIAL, "denial audit action mismatch");
        require(metrics.snapshot().scalar(AssistantMetric.AUTHORIZATION_DENIALS) == 1, "authorization denial metric must increment");
    }

    private static ActorContext actorContext() {
        return new ActorContext(
                ACTOR_ID,
                ActorType.HUMAN,
                Origin.PANEL,
                "network:actor-1",
                ACTOR_UUID,
                "panel-user-1",
                null,
                new RequestId("req-phase6-authz"),
                new TraceId("33333333333333333333333333333333"),
                "survival",
                1,
                NOW);
    }


    private static ServicePrincipal verifiedServicePrincipal() {
        byte[] key = "abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8);
        HmacServiceTokenService tokens = new HmacServiceTokenService(key, new BoundedReplayProtector(16));
        RequestId requestId = new RequestId("req-phase6-service-principal");
        String token = tokens.issue(new ServiceTokenClaims(
                "panel-backend",
                "legit-assistant",
                Set.of(ServiceScopes.INVOKE),
                NOW.minusSeconds(1),
                NOW.plusSeconds(30),
                "jti-phase6-service",
                requestId));
        var result = tokens.verify(token, "legit-assistant", requestId, Set.of(ServiceScopes.INVOKE), NOW);
        if (!result.authenticated()) throw new IllegalStateException("test service credential did not verify");
        return result.principal();
    }

    private static AssistantDescriptor descriptor() {
        return new AssistantDescriptor(
                AssistantId.BUGGY,
                "BuggyBot",
                true,
                "buggy-phase6",
                "default-security",
                "buggy-knowledge",
                "default-runtime",
                null,
                1,
                NOW);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
