package de.legitserver.assistant.panel;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.core.request.RequestContext;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.observability.audit.AuditAppendResult;
import de.legitserver.assistant.observability.audit.AuditRecorder;
import de.legitserver.assistant.panel.realtime.PanelRealtimeEvent;
import de.legitserver.assistant.panel.realtime.PanelRealtimeEventType;
import de.legitserver.assistant.panel.realtime.PanelRealtimeHub;
import de.legitserver.assistant.panel.security.HmacPanelGatewayAuthenticator;
import de.legitserver.assistant.panel.security.HmacPanelUserContextService;
import de.legitserver.assistant.panel.security.PanelGatewayAuthenticationResult;
import de.legitserver.assistant.panel.security.PanelGatewayRequestSigner;
import de.legitserver.assistant.panel.security.PanelRequestIdentity;
import de.legitserver.assistant.panel.security.PanelSignedHeaders;
import de.legitserver.assistant.panel.state.PanelDegradedStateProjector;
import de.legitserver.assistant.panel.state.PanelIntegrationHealth;
import de.legitserver.assistant.panel.state.PanelIntegrationState;
import de.legitserver.assistant.panel.state.PanelSurface;
import de.legitserver.assistant.panel.state.PanelSurfaceAvailability;
import de.legitserver.assistant.panel.state.PanelSurfaceStatus;
import de.legitserver.assistant.requests.model.ApprovalRequestId;
import de.legitserver.assistant.requests.model.RequestApproval;
import de.legitserver.assistant.requests.model.RequestDraft;
import de.legitserver.assistant.requests.model.RequestHistoryEvent;
import de.legitserver.assistant.requests.model.RequestPayload;
import de.legitserver.assistant.requests.model.RequestRecord;
import de.legitserver.assistant.requests.model.RequestRiskLevel;
import de.legitserver.assistant.requests.model.RequestState;
import de.legitserver.assistant.requests.policy.RequestApprovalPolicy;
import de.legitserver.assistant.requests.repository.RequestRepository;
import de.legitserver.assistant.requests.service.RequestService;
import de.legitserver.assistant.security.authn.BoundedReplayProtector;
import de.legitserver.assistant.security.authn.HmacServiceTokenService;
import de.legitserver.assistant.security.authn.ServiceScopes;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Phase-15 security/degraded/realtime/approval integration verification without Spring. */
public final class Phase15PanelIntegrationSelfTest {
    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");
    private static final byte[] SERVICE_KEY = "phase15-panel-service-key-material-0000000000000000".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CONTEXT_KEY = "phase15-panel-context-key-material-0000000000000000".getBytes(StandardCharsets.UTF_8);

    private Phase15PanelIntegrationSelfTest() {}

    public static void main(String[] args) throws Exception {
        verifyServiceAuthenticationAndUnforgeableUserContext();
        verifyDegradedProjectionIsSurfaceLocal();
        verifyBoundedRealtimeFanout();
        verifyApprovalActorsComeFromVerifiedPanelContext();
        System.out.println("PHASE15_PANEL_INTEGRATION_SELF_TEST: PASS");
    }

    private static void verifyServiceAuthenticationAndUnforgeableUserContext() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        HmacServiceTokenService issuerService = new HmacServiceTokenService(SERVICE_KEY, new BoundedReplayProtector(128));
        HmacServiceTokenService verifierService = new HmacServiceTokenService(SERVICE_KEY, new BoundedReplayProtector(128));
        HmacPanelUserContextService issuerContext = new HmacPanelUserContextService(CONTEXT_KEY, new BoundedReplayProtector(128));
        HmacPanelUserContextService verifierContext = new HmacPanelUserContextService(CONTEXT_KEY, new BoundedReplayProtector(128));
        PanelGatewayRequestSigner signer = new PanelGatewayRequestSigner(
                issuerService, issuerContext, "legit-panel-backend", Set.of(ServiceScopes.INVOKE), clock, Duration.ofSeconds(45));
        HmacPanelGatewayAuthenticator authenticator = new HmacPanelGatewayAuthenticator(verifierService, verifierContext);
        RequestId requestId = new RequestId("req-panel-auth-1");
        TraceId traceId = new TraceId("trace-panel-auth-1");
        PanelSignedHeaders headers = signer.sign(
                new PanelRequestIdentity(new ActorId("actor-user-1"), "network-user-1", "panel-user-1", "panel-01"),
                requestId, traceId);
        PanelGatewayAuthenticationResult result = authenticator.authenticate(
                headers.authorization(), headers.userContext(), requestId, traceId, NOW);
        require(result.authenticated(), "signed Panel request must authenticate");
        require(result.verified().presentedActor().actorId().equals(new ActorId("actor-user-1")), "actor must bind");
        require(result.verified().presentedActor().origin() == Origin.PANEL, "origin must be server-assigned PANEL");
        require(result.verified().presentedActor().securityRevision() == 0L, "Panel must not inject security revision");

        // A single-byte context mutation must fail; no unsigned actor override exists.
        char last = headers.userContext().charAt(headers.userContext().length() - 1);
        char replacement = last == 'A' ? 'B' : 'A';
        String forged = headers.userContext().substring(0, headers.userContext().length() - 1) + replacement;
        RequestId request2 = new RequestId("req-panel-auth-2");
        TraceId trace2 = new TraceId("trace-panel-auth-2");
        PanelSignedHeaders headers2 = signer.sign(
                new PanelRequestIdentity(new ActorId("actor-user-1"), "network-user-1", "panel-user-1", "panel-01"),
                request2, trace2);
        PanelGatewayAuthenticationResult forgedResult = authenticator.authenticate(
                headers2.authorization(), forged, request2, trace2, NOW);
        require(!forgedResult.authenticated(), "forged user context must be denied");
    }

    private static void verifyDegradedProjectionIsSurfaceLocal() {
        EnumMap<PanelSurface, PanelSurfaceStatus> statuses = new EnumMap<>(PanelSurface.class);
        for (PanelSurface surface : PanelSurface.values()) {
            statuses.put(surface, new PanelSurfaceStatus(surface, PanelSurfaceAvailability.AVAILABLE, "OK"));
        }
        statuses.put(PanelSurface.KNOWLEDGE,
                new PanelSurfaceStatus(PanelSurface.KNOWLEDGE, PanelSurfaceAvailability.DEGRADED, "KNOWLEDGE_INDEX_STALE"));
        PanelIntegrationHealth health = new PanelDegradedStateProjector().project(statuses, NOW);
        require(health.state() == PanelIntegrationState.DEGRADED, "one degraded surface must make aggregate degraded");
        require(health.surfaces().get(PanelSurface.BUGS).availability() == PanelSurfaceAvailability.AVAILABLE,
                "unrelated surface must stay available");
        require(health.surfaces().get(PanelSurface.KNOWLEDGE).availability() == PanelSurfaceAvailability.DEGRADED,
                "degraded surface must remain explicit");
    }

    private static void verifyBoundedRealtimeFanout() throws Exception {
        PanelRealtimeHub hub = new PanelRealtimeHub(16, 2, Clock.fixed(NOW, ZoneOffset.UTC));
        AtomicInteger received = new AtomicInteger();
        AutoCloseable subscription = hub.subscribe(event -> received.incrementAndGet());
        PanelRealtimeEvent event = hub.publish(PanelRealtimeEventType.REQUEST_CHANGED, "req-123", Map.of("state", "APPROVED"));
        require(event.sequence() == 1L && received.get() == 1, "subscriber must receive published event");
        require(hub.replayAfter(0, 10).size() == 1, "event replay must work");
        subscription.close();
        require(hub.subscriberCount() == 0, "subscription close must release bounded slot");
    }

    private static void verifyApprovalActorsComeFromVerifiedPanelContext() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        HmacServiceTokenService issuerService = new HmacServiceTokenService(SERVICE_KEY, new BoundedReplayProtector(128));
        HmacServiceTokenService verifierService = new HmacServiceTokenService(SERVICE_KEY, new BoundedReplayProtector(128));
        HmacPanelUserContextService issuerContext = new HmacPanelUserContextService(CONTEXT_KEY, new BoundedReplayProtector(128));
        HmacPanelUserContextService verifierContext = new HmacPanelUserContextService(CONTEXT_KEY, new BoundedReplayProtector(128));
        PanelGatewayRequestSigner signer = new PanelGatewayRequestSigner(
                issuerService, issuerContext, "legit-panel-backend", Set.of(ServiceScopes.INVOKE), clock, Duration.ofSeconds(45));
        HmacPanelGatewayAuthenticator authenticator = new HmacPanelGatewayAuthenticator(verifierService, verifierContext);
        InMemoryRequestRepository repository = new InMemoryRequestRepository();
        RequestService requests = new RequestService(
                repository, new AuditRecorder(record -> AuditAppendResult.recordedResult()), new RequestApprovalPolicy(), clock);

        PanelGatewayAuthenticationResult requester = verify(signer, authenticator,
                new PanelRequestIdentity(new ActorId("actor-requester"), "network-requester", "panel-requester", null),
                "req-create", "trace-create");
        RequestContext requesterContext = context(requester, "req-create", "trace-create");
        RequestRecord created = requests.create(new RequestDraft(
                "panel.write.request", RequestRiskLevel.HIGH, 1, "application/json", "{\"target\":\"safe\"}",
                NOW.plus(Duration.ofMinutes(10)), DataClassification.INTERNAL), requesterContext);
        require(created.state() == RequestState.PENDING, "request must start pending");

        PanelGatewayAuthenticationResult approver = verify(signer, authenticator,
                new PanelRequestIdentity(new ActorId("actor-approver"), "network-approver", "panel-approver", null),
                "req-approve", "trace-approve");
        RequestRecord approved = requests.approve(
                created.requestId(), created.revision(), context(approver, "req-approve", "trace-approve"), "reviewed");
        require(approved.state() == RequestState.APPROVED, "verified second Panel user must approve HIGH request");
        require(repository.approvals(created.requestId(), 10).get(0).approverActorId().equals(new ActorId("actor-approver")),
                "approval actor must originate from verified signed Panel context");
    }

    private static PanelGatewayAuthenticationResult verify(
            PanelGatewayRequestSigner signer,
            HmacPanelGatewayAuthenticator authenticator,
            PanelRequestIdentity identity,
            String request,
            String trace) {
        RequestId requestId = new RequestId(request);
        TraceId traceId = new TraceId(trace);
        PanelSignedHeaders headers = signer.sign(identity, requestId, traceId);
        PanelGatewayAuthenticationResult verified = authenticator.authenticate(
                headers.authorization(), headers.userContext(), requestId, traceId, NOW);
        require(verified.authenticated(), "Panel request verification must succeed");
        return verified;
    }

    private static RequestContext context(PanelGatewayAuthenticationResult verified, String request, String trace) {
        return new RequestContext(
                new RequestId(request), new TraceId(trace), verified.verified().presentedActor().actorId(), Origin.PANEL,
                null, null, verified.verified().presentedActor().serverId(), NOW);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class InMemoryRequestRepository implements RequestRepository {
        private final Map<ApprovalRequestId, RequestRecord> records = new HashMap<>();
        private final Map<ApprovalRequestId, RequestPayload> payloads = new HashMap<>();
        private final Map<ApprovalRequestId, List<RequestApproval>> approvals = new HashMap<>();
        private final Map<ApprovalRequestId, List<RequestHistoryEvent>> history = new HashMap<>();

        @Override
        public synchronized void create(RequestRecord request, RequestPayload payload, RequestHistoryEvent createdEvent) {
            if (records.putIfAbsent(request.requestId(), request) != null) throw new IllegalStateException("duplicate request");
            payloads.put(request.requestId(), payload);
            history.computeIfAbsent(request.requestId(), ignored -> new ArrayList<>()).add(createdEvent);
        }

        @Override public synchronized Optional<RequestRecord> findById(ApprovalRequestId id) { return Optional.ofNullable(records.get(id)); }
        @Override public synchronized Optional<RequestPayload> activePayload(ApprovalRequestId id) { return Optional.ofNullable(payloads.get(id)); }

        @Override
        public synchronized long countApproved(ApprovalRequestId id, long payloadRevision, String payloadSha256) {
            return approvals.getOrDefault(id, List.of()).stream()
                    .filter(value -> value.payloadRevision() == payloadRevision
                            && value.payloadSha256().equals(payloadSha256)
                            && value.decision() == de.legitserver.assistant.requests.model.ApprovalDecision.APPROVE)
                    .count();
        }

        @Override
        public synchronized RequestRecord revisePayload(
                RequestRecord updated, long expectedRevision, RequestPayload payload, RequestHistoryEvent event) {
            replace(updated, expectedRevision);
            payloads.put(updated.requestId(), payload);
            history.computeIfAbsent(updated.requestId(), ignored -> new ArrayList<>()).add(event);
            return updated;
        }

        @Override
        public synchronized RequestRecord recordDecision(
                RequestRecord updated, long expectedRevision, RequestApproval approval, RequestHistoryEvent event) {
            replace(updated, expectedRevision);
            approvals.computeIfAbsent(updated.requestId(), ignored -> new ArrayList<>()).add(approval);
            history.computeIfAbsent(updated.requestId(), ignored -> new ArrayList<>()).add(event);
            return updated;
        }

        @Override
        public synchronized RequestRecord transition(RequestRecord updated, long expectedRevision, RequestHistoryEvent event) {
            replace(updated, expectedRevision);
            history.computeIfAbsent(updated.requestId(), ignored -> new ArrayList<>()).add(event);
            return updated;
        }

        @Override
        public synchronized List<RequestApproval> approvals(ApprovalRequestId id, int limit) {
            return List.copyOf(approvals.getOrDefault(id, List.of()).stream().limit(limit).toList());
        }

        @Override
        public synchronized List<RequestHistoryEvent> history(ApprovalRequestId id, int limit) {
            return List.copyOf(history.getOrDefault(id, List.of()).stream().limit(limit).toList());
        }

        private void replace(RequestRecord updated, long expectedRevision) {
            RequestRecord current = records.get(updated.requestId());
            if (current == null || current.revision() != expectedRevision) throw new IllegalStateException("revision conflict");
            records.put(updated.requestId(), updated);
        }
    }
}
