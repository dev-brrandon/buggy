package de.legitserver.assistant.api.v1;

import de.legitserver.assistant.api.v1.backend.PanelApiReadPort;
import de.legitserver.assistant.api.v1.backend.PanelAssistantResolutionPort;
import de.legitserver.assistant.api.v1.backend.PanelPipelineOperationPolicy;
import de.legitserver.assistant.api.v1.backend.PipelineAssistantApiBackend;
import de.legitserver.assistant.api.v1.model.ApiCallContext;
import de.legitserver.assistant.api.v1.model.ApiCommandRequest;
import de.legitserver.assistant.api.v1.model.ApiMutationResult;
import de.legitserver.assistant.api.v1.model.ApiResourceView;
import de.legitserver.assistant.api.v1.pagination.ApiPage;
import de.legitserver.assistant.api.v1.pagination.ApiPageRequest;
import de.legitserver.assistant.conversations.model.Conversation;
import de.legitserver.assistant.conversations.repository.ConversationRepository;
import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.assistant.AssistantCapabilities;
import de.legitserver.assistant.core.assistant.AssistantDescriptor;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.core.request.RequestContext;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.observability.audit.AuditAppendResult;
import de.legitserver.assistant.observability.audit.AuditRecorder;
import de.legitserver.assistant.observability.health.DependencyHealthRegistry;
import de.legitserver.assistant.observability.metrics.InMemoryAssistantMetrics;
import de.legitserver.assistant.panel.security.HmacPanelGatewayAuthenticator;
import de.legitserver.assistant.panel.security.HmacPanelPipelineCredentialIssuer;
import de.legitserver.assistant.panel.security.HmacPanelUserContextService;
import de.legitserver.assistant.panel.security.PanelGatewayAuthenticationResult;
import de.legitserver.assistant.panel.security.PanelGatewayRequestSigner;
import de.legitserver.assistant.panel.security.PanelRequestIdentity;
import de.legitserver.assistant.panel.security.PanelSignedHeaders;
import de.legitserver.assistant.pipeline.RuntimeRequestPipeline;
import de.legitserver.assistant.pipeline.audit.PipelineAudit;
import de.legitserver.assistant.pipeline.control.BoundedAdmissionController;
import de.legitserver.assistant.pipeline.control.IdempotencyCoordinator;
import de.legitserver.assistant.pipeline.control.PipelineCancellationRegistry;
import de.legitserver.assistant.pipeline.conversation.RepositoryConversationResolver;
import de.legitserver.assistant.pipeline.model.PipelineExecutionOutput;
import de.legitserver.assistant.pipeline.model.PipelineOperation;
import de.legitserver.assistant.pipeline.model.PipelineRoute;
import de.legitserver.assistant.pipeline.model.PipelineRouteType;
import de.legitserver.assistant.pipeline.policy.RuntimePipelinePolicy;
import de.legitserver.assistant.pipeline.policy.SlidingWindowRateLimiter;
import de.legitserver.assistant.pipeline.response.DefaultPipelineFormatter;
import de.legitserver.assistant.pipeline.response.DefaultPipelineResponseSecurity;
import de.legitserver.assistant.pipeline.routing.RegisteredPipelineRouter;
import de.legitserver.assistant.pipeline.routing.StaticPipelineCapabilityResolver;
import de.legitserver.assistant.pipeline.security.AuthoritativePipelineActorResolver;
import de.legitserver.assistant.pipeline.security.HmacPipelineAuthentication;
import de.legitserver.assistant.pipeline.security.Phase3IngressAuthorization;
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
import de.legitserver.assistant.security.authorization.ActorResolution;
import de.legitserver.assistant.security.authorization.AssistantSecurityCatalog;
import de.legitserver.assistant.security.authorization.AuthoritativeSecuritySnapshot;
import de.legitserver.assistant.security.authorization.AuthorizationDecision;
import de.legitserver.assistant.security.authorization.AuthorizationRiskLevel;
import de.legitserver.assistant.security.authorization.ResolvedActorIdentity;
import de.legitserver.assistant.security.authorization.SecuritySnapshotLookup;
import de.legitserver.assistant.security.control.InMemorySecurityControlPlane;
import de.legitserver.assistant.security.control.SecurityFeature;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Proves Panel signed identity -> API backend -> Phase-13 pipeline -> Phase-9 approval lifecycle. */
public final class Phase15PanelPipelineSelfTest {
    private static final Instant NOW = Instant.parse("2026-10-04T12:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final byte[] PANEL_SERVICE_KEY = "phase15-http-panel-service-key-material-000000000000".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PANEL_CONTEXT_KEY = "phase15-http-panel-context-key-material-000000000000".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PIPELINE_KEY = "phase15-internal-pipeline-key-material-00000000000000".getBytes(StandardCharsets.UTF_8);
    private static final PipelineOperation CREATE = new PipelineOperation("assistant.request.create");
    private static final PipelineOperation APPROVE = new PipelineOperation("assistant.request.approve");
    private static final UUID REQUESTER_UUID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID APPROVER_UUID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private Phase15PanelPipelineSelfTest() {}

    public static void main(String[] args) throws Exception {
        require(args.length == 1, "project dir required");
        verifyPanelApiContract(Path.of(args[0]));
        InMemoryRequestRepository repository = new InMemoryRequestRepository();
        RequestService requestService = new RequestService(
                repository, new AuditRecorder(record -> AuditAppendResult.recordedResult()), new RequestApprovalPolicy(), CLOCK);
        HmacServiceTokenService pipelineIssuerTokens = new HmacServiceTokenService(PIPELINE_KEY, new BoundedReplayProtector(128));
        HmacServiceTokenService pipelineVerifierTokens = new HmacServiceTokenService(PIPELINE_KEY, new BoundedReplayProtector(128));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            RuntimeRequestPipeline pipeline = pipeline(requestService, pipelineVerifierTokens, executor);
            AssistantDescriptor assistant = new AssistantDescriptor(
                    AssistantId.BUGGY, "Buggy", true, "cap-phase15", "policy-phase15", "knowledge-phase15",
                    "runtime-phase15", "personality-phase15", 15, NOW);
            PanelAssistantResolutionPort resolver = (context, operation, resourceId, command) -> assistant;
            PipelineAssistantApiBackend backend = new PipelineAssistantApiBackend(
                    pipeline,
                    new HmacPanelPipelineCredentialIssuer(pipelineIssuerTokens, CLOCK, Duration.ofSeconds(60)),
                    resolver,
                    operation -> PanelPipelineOperationPolicy.internalDefault(),
                    new NoReads(),
                    CLOCK);

            ApiCallContext requester = panelContext("requester", "network-requester", "panel-requester", "panel-create", "trace-create");
            ApiMutationResult created = backend.createRequest(
                    requester,
                    new ApiCommandRequest(Map.of("kind", "write")),
                    "idem-create-1");
            ApprovalRequestId requestId = new ApprovalRequestId(UUID.fromString(created.resource().id()));
            RequestRecord pending = repository.findById(requestId).orElseThrow();
            require(pending.state() == RequestState.PENDING, "created request must be pending");

            ApiCallContext approver = panelContext("approver", "network-approver", "panel-approver", "panel-approve", "trace-approve");
            ApiMutationResult approvedResult = backend.approveRequest(
                    approver,
                    requestId.toString(),
                    new ApiCommandRequest(Map.of("expectedRevision", pending.revision(), "reason", "reviewed")),
                    "idem-approve-1");
            RequestRecord approved = repository.findById(requestId).orElseThrow();
            require(approved.state() == RequestState.APPROVED, "request must reach APPROVED through pipeline");
            require(repository.approvals(requestId, 10).size() == 1, "exactly one approval must be persisted");
            require(repository.approvals(requestId, 10).get(0).approverActorId().equals(new ActorId("actor-approver")),
                    "approval actor must be verified Panel actor");
            require("APPROVED".equals(approvedResult.resource().attributes().get("state")), "API mutation returns approved state");
            System.out.println("PHASE15_PANEL_PIPELINE_SELF_TEST: PASS");
        } finally {
            executor.shutdownNow();
        }
    }


    private static void verifyPanelApiContract(Path project) throws Exception {
        String spec = Files.readString(project.resolve("assistant-api/src/main/resources/openapi/assistant-v1.yaml"));
        require(spec.contains("PanelServiceAuth:"), "OpenAPI must document Panel service authentication");
        require(spec.contains("PanelUserContext:"), "OpenAPI must document signed Panel user context");
        require(spec.contains("  /panel/status:"), "Panel status surface documented");
        require(spec.contains("  /panel/audit:"), "Panel audit surface documented");
        require(spec.contains("  /panel/rate-limits:"), "Panel rate-limit surface documented");
        require(spec.contains("  /panel/feature-flags:"), "Panel feature-flag surface documented");
        require(spec.contains("  /panel/events:"), "Panel SSE surface documented");
        require(count(spec, "operationId:") == 26, "Phase-15 OpenAPI must expose 26 operations");

        StringBuilder panelSources = new StringBuilder();
        try (var paths = Files.walk(project.resolve("assistant-panel/src/main/java"))) {
            paths.filter(path -> path.toString().endsWith(".java")).sorted().forEach(path -> {
                try {
                    panelSources.append(Files.readString(path)).append('\n');
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
        String source = panelSources.toString();
        require(!source.contains("java.sql"), "Panel module must not use java.sql");
        require(!source.contains("javax.sql"), "Panel module must not use javax.sql");
        require(!source.contains("DataSource"), "Panel module must not access databases directly");
        require(!source.contains("Jdbc"), "Panel module must not contain JDBC adapters");
    }

    private static int count(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static RuntimeRequestPipeline pipeline(
            RequestService requests,
            HmacServiceTokenService verifierTokens,
            ExecutorService executor) {
        AtomicInteger calls = new AtomicInteger();
        var actorAuthority = (de.legitserver.assistant.security.authorization.AuthoritativeActorResolverPort) presented -> {
            if (presented.actorId().equals(new ActorId("actor-requester"))) {
                return ActorResolution.found(new ResolvedActorIdentity(
                        presented.actorId(), "network-requester", REQUESTER_UUID, 15));
            }
            if (presented.actorId().equals(new ActorId("actor-approver"))) {
                return ActorResolution.found(new ResolvedActorIdentity(
                        presented.actorId(), "network-approver", APPROVER_UUID, 15));
            }
            return ActorResolution.notFound();
        };
        Set<String> permissions = Set.of(
                AssistantSecurityCatalog.assistantUsePermission(AssistantId.BUGGY),
                "networkcore.assistant.buggy.channel.panel",
                "networkcore.assistant.write.request",
                "networkcore.assistant.approval.review");
        AuthoritativeSecuritySnapshot requesterSnapshot = snapshot(REQUESTER_UUID, permissions);
        AuthoritativeSecuritySnapshot approverSnapshot = snapshot(APPROVER_UUID, permissions);
        var snapshotAuthority = (de.legitserver.assistant.security.authorization.AuthoritativeSecuritySnapshotPort) (uuid, minimumRevision) -> {
            if (minimumRevision > 15) return SecuritySnapshotLookup.notFound();
            if (REQUESTER_UUID.equals(uuid)) return SecuritySnapshotLookup.found(requesterSnapshot);
            if (APPROVER_UUID.equals(uuid)) return SecuritySnapshotLookup.found(approverSnapshot);
            return SecuritySnapshotLookup.notFound();
        };
        InMemorySecurityControlPlane controls = new InMemorySecurityControlPlane(EnumSet.allOf(SecurityFeature.class), NOW);
        InMemoryAssistantMetrics metrics = new InMemoryAssistantMetrics();
        DependencyHealthRegistry dependencies = new DependencyHealthRegistry(CLOCK);
        RuntimePipelinePolicy policy = new RuntimePipelinePolicy(
                context -> AuthorizationDecision.allow(
                        context.actor().securityRevision(),
                        context.capabilityId().equals(AssistantCapabilities.WRITE_REQUEST)
                                ? AuthorizationRiskLevel.CRITICAL : AuthorizationRiskLevel.HIGH),
                dependencies,
                new SlidingWindowRateLimiter(100, Duration.ofMinutes(1), 128, CLOCK),
                metrics);
        RegisteredPipelineRouter routes = new RegisteredPipelineRouter();
        routes.register(CREATE, new PipelineRoute(
                "route.request.create", PipelineRouteType.DOMAIN, AssistantCapabilities.WRITE_REQUEST, Set.of(), Duration.ofSeconds(5)),
                context -> {
                    calls.incrementAndGet();
                    RequestRecord created = requests.create(new RequestDraft(
                            "panel.write.request", RequestRiskLevel.HIGH, 1, "application/json", "{\"operation\":\"write\"}",
                            NOW.plus(Duration.ofMinutes(5)), DataClassification.INTERNAL), context.request().context());
                    return new PipelineExecutionOutput(
                            Map.of("id", created.requestId().toString(), "state", created.state().name(), "revision", created.revision()),
                            DataClassification.INTERNAL);
                });
        routes.register(APPROVE, new PipelineRoute(
                "route.request.approve", PipelineRouteType.DOMAIN, AssistantCapabilities.APPROVAL_REVIEW, Set.of(), Duration.ofSeconds(5)),
                context -> {
                    calls.incrementAndGet();
                    String rawId = String.valueOf(context.request().input().get("resourceId"));
                    long expectedRevision = ((Number) context.request().input().get("expectedRevision")).longValue();
                    String reason = String.valueOf(context.request().input().get("reason"));
                    RequestRecord approved = requests.approve(
                            new ApprovalRequestId(UUID.fromString(rawId)), expectedRevision, context.request().context(), reason);
                    return new PipelineExecutionOutput(
                            Map.of("id", approved.requestId().toString(), "state", approved.state().name(), "revision", approved.revision()),
                            DataClassification.INTERNAL);
                });
        return new RuntimeRequestPipeline(
                new HmacPipelineAuthentication(verifierTokens),
                new AuthoritativePipelineActorResolver(actorAuthority),
                new Phase3IngressAuthorization(snapshotAuthority, controls),
                new RepositoryConversationResolver(new NoConversationRepository()),
                new StaticPipelineCapabilityResolver(Map.of(
                        CREATE, AssistantCapabilities.WRITE_REQUEST,
                        APPROVE, AssistantCapabilities.APPROVAL_REVIEW)),
                routes,
                policy,
                routes,
                new DefaultPipelineResponseSecurity(),
                new DefaultPipelineFormatter(),
                new PipelineAudit(new AuditRecorder(record -> AuditAppendResult.recordedResult()), CLOCK),
                new BoundedAdmissionController(2, 4, Duration.ofMillis(10), metrics),
                new IdempotencyCoordinator(Duration.ofMinutes(10), 128, CLOCK),
                new PipelineCancellationRegistry(),
                executor,
                metrics,
                (requestId, stage) -> { },
                CLOCK,
                Duration.ofMillis(250));
    }

    private static AuthoritativeSecuritySnapshot snapshot(UUID uuid, Set<String> permissions) {
        return new AuthoritativeSecuritySnapshot(
                uuid, "usr", 0, "usr", Set.of("usr"), Set.of("usr"), permissions, Set.of(), Set.of(), false, 15, NOW);
    }

    private static ApiCallContext panelContext(
            String actorSuffix, String networkIdentity, String panelUser, String requestId, String traceId) {
        HmacServiceTokenService issueService = new HmacServiceTokenService(PANEL_SERVICE_KEY, new BoundedReplayProtector(64));
        HmacServiceTokenService verifyService = new HmacServiceTokenService(PANEL_SERVICE_KEY, new BoundedReplayProtector(64));
        HmacPanelUserContextService issueContext = new HmacPanelUserContextService(PANEL_CONTEXT_KEY, new BoundedReplayProtector(64));
        HmacPanelUserContextService verifyContext = new HmacPanelUserContextService(PANEL_CONTEXT_KEY, new BoundedReplayProtector(64));
        PanelGatewayRequestSigner signer = new PanelGatewayRequestSigner(
                issueService, issueContext, "legit-panel-backend", Set.of(ServiceScopes.INVOKE), CLOCK, Duration.ofSeconds(30));
        HmacPanelGatewayAuthenticator authenticator = new HmacPanelGatewayAuthenticator(verifyService, verifyContext);
        RequestId req = new RequestId(requestId);
        TraceId trace = new TraceId(traceId);
        PanelSignedHeaders signed = signer.sign(
                new PanelRequestIdentity(new ActorId("actor-" + actorSuffix), networkIdentity, panelUser, "panel-01"), req, trace);
        PanelGatewayAuthenticationResult result = authenticator.authenticate(
                signed.authorization(), signed.userContext(), req, trace, NOW);
        require(result.authenticated(), "signed Panel context must authenticate");
        return new ApiCallContext(req, trace, signed.authorization(), result.verified());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Phase-15 pipeline self-test failed: " + message);
    }

    private static final class NoReads implements PanelApiReadPort {
        private static UnsupportedOperationException unsupported() { return new UnsupportedOperationException("read not used"); }
        @Override public ApiPage<ApiResourceView> listAssistants(ApiCallContext c, ApiPageRequest p) { throw unsupported(); }
        @Override public ApiResourceView getAssistant(ApiCallContext c, String id) { throw unsupported(); }
        @Override public ApiResourceView getConversation(ApiCallContext c, String id) { throw unsupported(); }
        @Override public ApiPage<ApiResourceView> listBugs(ApiCallContext c, ApiPageRequest p) { throw unsupported(); }
        @Override public ApiResourceView getBug(ApiCallContext c, String id) { throw unsupported(); }
        @Override public ApiPage<ApiResourceView> searchKnowledge(ApiCallContext c, String q, ApiPageRequest p) { throw unsupported(); }
        @Override public ApiResourceView getKnowledgeDocument(ApiCallContext c, String id) { throw unsupported(); }
        @Override public ApiResourceView getRequest(ApiCallContext c, String id) { throw unsupported(); }
        @Override public ApiPage<ApiResourceView> listSupervisorAssistants(ApiCallContext c, ApiPageRequest p) { throw unsupported(); }
        @Override public ApiResourceView getAssistantHealth(ApiCallContext c, String id) { throw unsupported(); }
        @Override public ApiPage<ApiResourceView> getAssistantFailures(ApiCallContext c, String id, ApiPageRequest p) { throw unsupported(); }
    }

    private static final class InMemoryRequestRepository implements RequestRepository {
        private final Map<ApprovalRequestId, RequestRecord> records = new HashMap<>();
        private final Map<ApprovalRequestId, RequestPayload> payloads = new HashMap<>();
        private final Map<ApprovalRequestId, List<RequestApproval>> approvals = new HashMap<>();
        private final Map<ApprovalRequestId, List<RequestHistoryEvent>> history = new HashMap<>();

        @Override
        public synchronized void create(RequestRecord request, RequestPayload payload, RequestHistoryEvent createdEvent) {
            records.put(request.requestId(), request);
            payloads.put(request.requestId(), payload);
            history.computeIfAbsent(request.requestId(), ignored -> new ArrayList<>()).add(createdEvent);
        }
        @Override public synchronized Optional<RequestRecord> findById(ApprovalRequestId id) { return Optional.ofNullable(records.get(id)); }
        @Override public synchronized Optional<RequestPayload> activePayload(ApprovalRequestId id) { return Optional.ofNullable(payloads.get(id)); }
        @Override public synchronized long countApproved(ApprovalRequestId id, long rev, String sha) {
            return approvals.getOrDefault(id, List.of()).stream()
                    .filter(a -> a.payloadRevision() == rev && a.payloadSha256().equals(sha)
                            && a.decision() == de.legitserver.assistant.requests.model.ApprovalDecision.APPROVE).count();
        }
        @Override public synchronized RequestRecord revisePayload(RequestRecord u, long e, RequestPayload p, RequestHistoryEvent h) {
            replace(u, e); payloads.put(u.requestId(), p); history.computeIfAbsent(u.requestId(), x -> new ArrayList<>()).add(h); return u;
        }
        @Override public synchronized RequestRecord recordDecision(RequestRecord u, long e, RequestApproval a, RequestHistoryEvent h) {
            replace(u, e); approvals.computeIfAbsent(u.requestId(), x -> new ArrayList<>()).add(a);
            history.computeIfAbsent(u.requestId(), x -> new ArrayList<>()).add(h); return u;
        }
        @Override public synchronized RequestRecord transition(RequestRecord u, long e, RequestHistoryEvent h) {
            replace(u, e); history.computeIfAbsent(u.requestId(), x -> new ArrayList<>()).add(h); return u;
        }
        @Override public synchronized List<RequestApproval> approvals(ApprovalRequestId id, int limit) {
            return List.copyOf(approvals.getOrDefault(id, List.of()).stream().limit(limit).toList());
        }
        @Override public synchronized List<RequestHistoryEvent> history(ApprovalRequestId id, int limit) {
            return List.copyOf(history.getOrDefault(id, List.of()).stream().limit(limit).toList());
        }
        private void replace(RequestRecord u, long expected) {
            RequestRecord current = records.get(u.requestId());
            if (current == null || current.revision() != expected) throw new IllegalStateException("revision conflict");
            records.put(u.requestId(), u);
        }
    }

    private static final class NoConversationRepository implements ConversationRepository {
        @Override public void create(Conversation conversation) { throw new UnsupportedOperationException(); }
        @Override public Optional<Conversation> findById(de.legitserver.assistant.conversations.model.ConversationId id) { return Optional.empty(); }
        @Override public List<de.legitserver.assistant.conversations.model.ConversationMessage> listMessages(
                de.legitserver.assistant.conversations.model.ConversationId id, long after, int limit) { return List.of(); }
        @Override public long countMessages(de.legitserver.assistant.conversations.model.ConversationId id) { return 0; }
        @Override public Conversation transition(
                de.legitserver.assistant.conversations.model.ConversationId id, long expected,
                de.legitserver.assistant.conversations.model.ConversationState state, Instant now, Instant expiresAt) {
            throw new UnsupportedOperationException();
        }
        @Override public de.legitserver.assistant.conversations.repository.AppendMessageResult appendMessage(
                de.legitserver.assistant.conversations.model.ConversationId id, long expected,
                de.legitserver.assistant.conversations.model.NewConversationMessage message, Instant now, Instant expiresAt,
                int maxMessages, int maxBytes) { throw new UnsupportedOperationException(); }
        @Override public List<Conversation> findExpiredCandidates(Instant now, int limit) { return List.of(); }
    }
}
