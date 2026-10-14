package de.legitserver.assistant.pipeline;

import de.legitserver.assistant.conversations.model.Conversation;
import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.actor.ActorType;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.core.assistant.AssistantCapabilities;
import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.core.assistant.AssistantDescriptor;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.core.request.RequestContext;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.observability.audit.AuditAction;
import de.legitserver.assistant.observability.audit.AuditAppendResult;
import de.legitserver.assistant.observability.audit.AuditPort;
import de.legitserver.assistant.observability.audit.AuditRecord;
import de.legitserver.assistant.observability.audit.AuditRecorder;
import de.legitserver.assistant.observability.health.DependencyHealthRegistry;
import de.legitserver.assistant.observability.health.DependencyId;
import de.legitserver.assistant.observability.health.DependencyState;
import de.legitserver.assistant.observability.metrics.AssistantMetric;
import de.legitserver.assistant.observability.metrics.InMemoryAssistantMetrics;
import de.legitserver.assistant.pipeline.audit.PipelineAudit;
import de.legitserver.assistant.pipeline.control.BoundedAdmissionController;
import de.legitserver.assistant.pipeline.control.IdempotencyCoordinator;
import de.legitserver.assistant.pipeline.control.PipelineCancellationRegistry;
import de.legitserver.assistant.pipeline.conversation.RepositoryConversationResolver;
import de.legitserver.assistant.pipeline.model.PipelineExecutionOutput;
import de.legitserver.assistant.pipeline.model.PipelineOperation;
import de.legitserver.assistant.pipeline.model.PipelineRequest;
import de.legitserver.assistant.pipeline.model.PipelineResponse;
import de.legitserver.assistant.pipeline.model.PipelineRoute;
import de.legitserver.assistant.pipeline.model.PipelineRouteType;
import de.legitserver.assistant.pipeline.model.PipelineStage;
import de.legitserver.assistant.pipeline.model.PipelineStatus;
import de.legitserver.assistant.pipeline.policy.RuntimePipelinePolicy;
import de.legitserver.assistant.pipeline.policy.SlidingWindowRateLimiter;
import de.legitserver.assistant.pipeline.response.DefaultPipelineFormatter;
import de.legitserver.assistant.pipeline.response.DefaultPipelineResponseSecurity;
import de.legitserver.assistant.pipeline.routing.RegisteredPipelineRouter;
import de.legitserver.assistant.pipeline.routing.StaticPipelineCapabilityResolver;
import de.legitserver.assistant.pipeline.security.AuthoritativePipelineActorResolver;
import de.legitserver.assistant.pipeline.security.HmacPipelineAuthentication;
import de.legitserver.assistant.pipeline.security.Phase3IngressAuthorization;
import de.legitserver.assistant.security.authn.BoundedReplayProtector;
import de.legitserver.assistant.security.authn.HmacServiceTokenService;
import de.legitserver.assistant.security.authn.ServiceScopes;
import de.legitserver.assistant.security.authn.ServiceTokenClaims;
import de.legitserver.assistant.security.authorization.ActorResolution;
import de.legitserver.assistant.security.authorization.AssistantAuthorizationService;
import de.legitserver.assistant.security.authorization.AssistantSecurityCatalog;
import de.legitserver.assistant.security.authorization.AuthoritativeSecuritySnapshot;
import de.legitserver.assistant.security.authorization.AuthorizationRequest;
import de.legitserver.assistant.security.authorization.ResourceAuthorizationStatus;
import de.legitserver.assistant.security.authorization.ResourceRef;
import de.legitserver.assistant.security.authorization.ResolvedActorIdentity;
import de.legitserver.assistant.security.authorization.SecuritySnapshotLookup;
import de.legitserver.assistant.security.authorization.StaticAssistantCapabilityPolicy;
import de.legitserver.assistant.security.control.InMemorySecurityControlPlane;
import de.legitserver.assistant.security.control.SecurityFeature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Phase-13 end-to-end runtime pipeline acceptance self-test. */
public final class Phase13RuntimePipelineSelfTest {
    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final ActorId ACTOR_ID = new ActorId("actor-phase13");
    private static final UUID ACTOR_UUID = UUID.fromString("c1120ac8-9dde-4ae3-a17c-0fe073e6b013");
    private static final PipelineOperation KNOWLEDGE = new PipelineOperation("assistant.knowledge.answer");
    private static final PipelineOperation SOURCE = new PipelineOperation("assistant.source.inspect");

    private Phase13RuntimePipelineSelfTest() {}

    public static void main(String[] args) throws Exception {
        verifyFullPipelineOrderAndResponseSecurity();
        verifyPartialDependencyDegradation();
        verifyCancellationPropagatesToExecution();
        verifyTimeoutCancelsExecution();
        verifyRateLimiting();
        verifyIdempotencyReplayAndConflict();
        verifyBoundedConcurrencyAndBackpressure();
        verifyAuthenticationFailureStopsBeforeExecution();
        verifyMissingChannelPermissionStopsBeforeExecution();
        System.out.println("PHASE13_RUNTIME_PIPELINE_SELF_TEST: PASS");
    }

    private static void verifyFullPipelineOrderAndResponseSecurity() throws Exception {
        List<PipelineStage> observed = new CopyOnWriteArrayList<>();
        AtomicInteger executions = new AtomicInteger();
        try (Fixture fixture = new Fixture(
                20,
                4,
                8,
                DependencyState.UP,
                Duration.ofSeconds(3),
                context -> {
                    executions.incrementAndGet();
                    return new PipelineExecutionOutput(
                            Map.of("answer", "healthy password=phase13-secret"), DataClassification.INTERNAL);
                },
                context -> new PipelineExecutionOutput(Map.of("source", "ok"), DataClassification.INTERNAL),
                (requestId, stage) -> observed.add(stage))) {
            PipelineResponse response = fixture.pipeline.execute(fixture.request(KNOWLEDGE, "full-order", Map.of("question", "status"), Duration.ofSeconds(2)));
            require(response.status() == PipelineStatus.SUCCESS, "full pipeline request succeeds");
            require(executions.get() == 1, "full pipeline executes exactly once");
            require("healthy password=[REDACTED]".equals(response.output().get("answer")), "response security redacts secrets");
            require(observed.equals(List.of(PipelineStage.values())), "canonical pipeline stage order is exact");
            require(fixture.audits.records.stream().anyMatch(record -> record.action() == AuditAction.REQUEST_PIPELINE),
                    "request-level audit emitted");
            require(fixture.metrics.snapshot().scalar(AssistantMetric.REQUEST_COUNT) == 1L, "request metric increments");
        }
    }

    private static void verifyPartialDependencyDegradation() throws Exception {
        AtomicInteger sourceExecutions = new AtomicInteger();
        AtomicInteger knowledgeExecutions = new AtomicInteger();
        try (Fixture fixture = new Fixture(
                20,
                4,
                8,
                DependencyState.DOWN,
                Duration.ofSeconds(2),
                context -> {
                    knowledgeExecutions.incrementAndGet();
                    return new PipelineExecutionOutput(Map.of("answer", "knowledge-ok"), DataClassification.INTERNAL);
                },
                context -> {
                    sourceExecutions.incrementAndGet();
                    return new PipelineExecutionOutput(Map.of("source", "must-not-run"), DataClassification.INTERNAL);
                },
                (requestId, stage) -> { })) {
            PipelineResponse source = fixture.pipeline.execute(fixture.request(SOURCE, "degraded-source", Map.of(), Duration.ofSeconds(2)));
            require(source.status() == PipelineStatus.DEPENDENCY_DEGRADED, "source route degrades when its dependency is down");
            require(sourceExecutions.get() == 0, "degraded dependency blocks only affected execution");
            PipelineResponse knowledge = fixture.pipeline.execute(fixture.request(KNOWLEDGE, "healthy-knowledge", Map.of(), Duration.ofSeconds(2)));
            require(knowledge.status() == PipelineStatus.SUCCESS, "unrelated route remains available during partial degradation");
            require(knowledgeExecutions.get() == 1, "healthy route still executes");
        }
    }

    private static void verifyCancellationPropagatesToExecution() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        try (Fixture fixture = new Fixture(
                20,
                2,
                2,
                DependencyState.UP,
                Duration.ofSeconds(10),
                context -> {
                    started.countDown();
                    try {
                        Thread.sleep(10_000L);
                    } catch (InterruptedException cancellation) {
                        interrupted.set(true);
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("cancelled");
                    }
                    return new PipelineExecutionOutput(Map.of("unexpected", true), DataClassification.INTERNAL);
                },
                context -> new PipelineExecutionOutput(Map.of(), DataClassification.INTERNAL),
                (requestId, stage) -> { })) {
            PipelineRequest request = fixture.request(KNOWLEDGE, "cancel", Map.of(), Duration.ofSeconds(10));
            ExecutorService caller = Executors.newSingleThreadExecutor();
            try {
                Future<PipelineResponse> future = caller.submit(() -> fixture.pipeline.execute(request));
                require(started.await(2, TimeUnit.SECONDS), "cancellation test handler started");
                require(fixture.pipeline.cancel(request.context().requestId()), "active request cancellation accepted");
                PipelineResponse response = future.get(3, TimeUnit.SECONDS);
                require(response.status() == PipelineStatus.CANCELLED, "cancelled request returns CANCELLED");
                awaitTrue(interrupted, Duration.ofSeconds(1));
                require(interrupted.get(), "cancellation interrupts running execution");
            } finally {
                caller.shutdownNow();
            }
        }
    }

    private static void verifyTimeoutCancelsExecution() throws Exception {
        AtomicBoolean interrupted = new AtomicBoolean();
        try (Fixture fixture = new Fixture(
                20,
                2,
                2,
                DependencyState.UP,
                Duration.ofMillis(120),
                context -> {
                    try {
                        Thread.sleep(10_000L);
                    } catch (InterruptedException timeout) {
                        interrupted.set(true);
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("timed out");
                    }
                    return new PipelineExecutionOutput(Map.of(), DataClassification.INTERNAL);
                },
                context -> new PipelineExecutionOutput(Map.of(), DataClassification.INTERNAL),
                (requestId, stage) -> { })) {
            PipelineResponse response = fixture.pipeline.execute(fixture.request(KNOWLEDGE, "timeout", Map.of(), Duration.ofSeconds(2)));
            require(response.status() == PipelineStatus.TIMEOUT, "route timeout returns TIMEOUT");
            awaitTrue(interrupted, Duration.ofSeconds(1));
            require(interrupted.get(), "timeout interrupts running execution");
        }
    }

    private static void verifyRateLimiting() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        try (Fixture fixture = new Fixture(
                1,
                4,
                4,
                DependencyState.UP,
                Duration.ofSeconds(2),
                context -> {
                    executions.incrementAndGet();
                    return new PipelineExecutionOutput(Map.of("ok", true), DataClassification.INTERNAL);
                },
                context -> new PipelineExecutionOutput(Map.of(), DataClassification.INTERNAL),
                (requestId, stage) -> { })) {
            require(fixture.pipeline.execute(fixture.request(KNOWLEDGE, "rate-a", Map.of("n", 1), Duration.ofSeconds(2))).successful(),
                    "first request within rate window succeeds");
            PipelineResponse denied = fixture.pipeline.execute(fixture.request(KNOWLEDGE, "rate-b", Map.of("n", 2), Duration.ofSeconds(2)));
            require(denied.status() == PipelineStatus.RATE_LIMITED, "second request exceeds configured rate limit");
            require(executions.get() == 1, "rate-limited request never executes");
            require(fixture.metrics.snapshot().scalar(AssistantMetric.RATE_LIMIT_HITS) == 1L, "rate-limit metric increments");
        }
    }

    private static void verifyIdempotencyReplayAndConflict() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        try (Fixture fixture = new Fixture(
                20,
                4,
                4,
                DependencyState.UP,
                Duration.ofSeconds(2),
                context -> {
                    executions.incrementAndGet();
                    return new PipelineExecutionOutput(Map.of("value", "stable"), DataClassification.INTERNAL);
                },
                context -> new PipelineExecutionOutput(Map.of(), DataClassification.INTERNAL),
                (requestId, stage) -> { })) {
            PipelineRequest firstRequest = fixture.request(KNOWLEDGE, "idem-key", Map.of("payload", "A"), Duration.ofSeconds(2));
            PipelineResponse first = fixture.pipeline.execute(firstRequest);
            require(first.successful() && !first.idempotentReplay(), "first idempotent request executes normally");
            PipelineRequest replayRequest = fixture.request(KNOWLEDGE, "idem-key", Map.of("payload", "A"), Duration.ofSeconds(2));
            PipelineResponse replay = fixture.pipeline.execute(replayRequest);
            require(replay.successful() && replay.idempotentReplay(), "same key and payload replays cached secured response");
            require(replay.requestId().equals(replayRequest.context().requestId()), "idempotent replay uses current correlation identifiers");
            require(executions.get() == 1, "idempotent replay does not re-execute handler");
            PipelineResponse conflict = fixture.pipeline.execute(fixture.request(
                    KNOWLEDGE, "idem-key", Map.of("payload", "B"), Duration.ofSeconds(2)));
            require(conflict.status() == PipelineStatus.IDEMPOTENCY_CONFLICT, "same key with different payload is rejected");
            require(executions.get() == 1, "idempotency conflict never executes handler");
        }
    }

    private static void verifyBoundedConcurrencyAndBackpressure() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        try (Fixture fixture = new Fixture(
                20,
                1,
                0,
                DependencyState.UP,
                Duration.ofSeconds(5),
                context -> {
                    int active = concurrent.incrementAndGet();
                    maxConcurrent.accumulateAndGet(active, Math::max);
                    try {
                        if (Boolean.TRUE.equals(context.request().input().get("hold"))) {
                            firstStarted.countDown();
                            if (!releaseFirst.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("test release timeout");
                        }
                        return new PipelineExecutionOutput(Map.of("ok", true), DataClassification.INTERNAL);
                    } finally {
                        concurrent.decrementAndGet();
                    }
                },
                context -> new PipelineExecutionOutput(Map.of(), DataClassification.INTERNAL),
                (requestId, stage) -> { })) {
            ExecutorService caller = Executors.newSingleThreadExecutor();
            try {
                Future<PipelineResponse> first = caller.submit(() -> fixture.pipeline.execute(
                        fixture.request(KNOWLEDGE, "bp-first", Map.of("hold", true), Duration.ofSeconds(5))));
                require(firstStarted.await(2, TimeUnit.SECONDS), "first backpressure request acquired execution permit");
                PipelineResponse second = fixture.pipeline.execute(
                        fixture.request(KNOWLEDGE, "bp-second", Map.of("hold", false), Duration.ofSeconds(2)));
                require(second.status() == PipelineStatus.BACKPRESSURE, "request beyond active+queue bounds is rejected");
                releaseFirst.countDown();
                require(first.get(3, TimeUnit.SECONDS).successful(), "first admitted request completes");
                require(maxConcurrent.get() == 1, "bounded concurrency is never exceeded");
            } finally {
                releaseFirst.countDown();
                caller.shutdownNow();
            }
        }
    }

    private static void verifyAuthenticationFailureStopsBeforeExecution() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        try (Fixture fixture = new Fixture(
                20,
                2,
                2,
                DependencyState.UP,
                Duration.ofSeconds(2),
                context -> {
                    executions.incrementAndGet();
                    return new PipelineExecutionOutput(Map.of(), DataClassification.INTERNAL);
                },
                context -> new PipelineExecutionOutput(Map.of(), DataClassification.INTERNAL),
                (requestId, stage) -> { })) {
            PipelineRequest valid = fixture.request(KNOWLEDGE, "bad-auth", Map.of(), Duration.ofSeconds(2));
            PipelineRequest invalid = new PipelineRequest(
                    valid.context(),
                    "v1.invalid.invalid",
                    valid.presentedActor(),
                    valid.assistant(),
                    valid.operation(),
                    valid.resource(),
                    valid.input(),
                    valid.inputClassification(),
                    valid.responseClassificationCeiling(),
                    valid.approvalRequestId(),
                    valid.idempotencyKey(),
                    valid.timeout());
            PipelineResponse response = fixture.pipeline.execute(invalid);
            require(response.status() == PipelineStatus.AUTHENTICATION_FAILED, "invalid service credential fails at authentication");
            require(executions.get() == 0, "authentication failure never reaches execution");
        }
    }

    private static void verifyMissingChannelPermissionStopsBeforeExecution() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        try (Fixture fixture = new Fixture(
                20,
                2,
                2,
                DependencyState.UP,
                Duration.ofSeconds(2),
                context -> {
                    executions.incrementAndGet();
                    return new PipelineExecutionOutput(Map.of(), DataClassification.INTERNAL);
                },
                context -> new PipelineExecutionOutput(Map.of(), DataClassification.INTERNAL),
                (requestId, stage) -> { },
                false)) {
            PipelineResponse response = fixture.pipeline.execute(
                    fixture.request(KNOWLEDGE, "channel-denied", Map.of(), Duration.ofSeconds(2)));
            require(response.status() == PipelineStatus.AUTHORIZATION_DENIED,
                    "missing Buggy Panel channel permission fails at ingress authorization");
            require("ASSISTANT_CHANNEL_DENIED".equals(response.safeCode()),
                    "missing channel permission returns bounded channel denial code");
            require(executions.get() == 0, "missing channel permission never reaches execution");
        }
    }

    private static void awaitTrue(AtomicBoolean value, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!value.get() && System.nanoTime() < deadline) Thread.sleep(10L);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Phase-13 self-test failed: " + message);
    }

    private static final class Fixture implements AutoCloseable {
        private final AtomicInteger sequence = new AtomicInteger();
        private final HmacServiceTokenService tokens = new HmacServiceTokenService(key(), new BoundedReplayProtector(256));
        private final AssistantDescriptor assistant = new AssistantDescriptor(
                AssistantId.BUGGY,
                "Buggy",
                true,
                "cap-phase13",
                "policy-phase13",
                "knowledge-phase13",
                "runtime-phase13",
                "personality-phase13",
                13,
                NOW);
        private final AuthoritativeSecuritySnapshot snapshot;
        private final ExecutorService executionExecutor = Executors.newFixedThreadPool(4);
        private final RecordingAuditPort audits = new RecordingAuditPort();
        private final InMemoryAssistantMetrics metrics = new InMemoryAssistantMetrics();
        private final RuntimeRequestPipeline pipeline;

        private Fixture(
                int maxRequests,
                int maxConcurrency,
                int maxQueued,
                DependencyState sourceState,
                Duration knowledgeTimeout,
                de.legitserver.assistant.pipeline.port.PipelineExecutionPort knowledgeHandler,
                de.legitserver.assistant.pipeline.port.PipelineExecutionPort sourceHandler,
                de.legitserver.assistant.pipeline.port.PipelineStageObserver observer) {
            this(maxRequests, maxConcurrency, maxQueued, sourceState, knowledgeTimeout,
                    knowledgeHandler, sourceHandler, observer, true);
        }

        private Fixture(
                int maxRequests,
                int maxConcurrency,
                int maxQueued,
                DependencyState sourceState,
                Duration knowledgeTimeout,
                de.legitserver.assistant.pipeline.port.PipelineExecutionPort knowledgeHandler,
                de.legitserver.assistant.pipeline.port.PipelineExecutionPort sourceHandler,
                de.legitserver.assistant.pipeline.port.PipelineStageObserver observer,
                boolean includePanelChannelPermission) {
            Set<String> permissions = permissions(includePanelChannelPermission);
            snapshot = new AuthoritativeSecuritySnapshot(
                    ACTOR_UUID,
                    "usr",
                    0,
                    "usr",
                    Set.of("usr"),
                    Set.of("usr"),
                    permissions,
                    Set.of(),
                    Set.of(),
                    false,
                    13,
                    NOW);
            var actorAuthority = (de.legitserver.assistant.security.authorization.AuthoritativeActorResolverPort) presented ->
                    ActorResolution.found(new ResolvedActorIdentity(ACTOR_ID, "network-phase13", ACTOR_UUID, 13));
            var snapshotAuthority = (de.legitserver.assistant.security.authorization.AuthoritativeSecuritySnapshotPort) (uuid, minimumRevision) ->
                    ACTOR_UUID.equals(uuid) && minimumRevision <= 13 ? SecuritySnapshotLookup.found(snapshot) : SecuritySnapshotLookup.notFound();
            InMemorySecurityControlPlane controls = new InMemorySecurityControlPlane(EnumSet.allOf(SecurityFeature.class), NOW);
            StaticAssistantCapabilityPolicy capabilityPolicy = new StaticAssistantCapabilityPolicy(Map.of(
                    AssistantId.BUGGY, Set.of(AssistantCapabilities.KNOWLEDGE_QUERY, AssistantCapabilities.SOURCE_READ)));
            AssistantAuthorizationService capabilityAuthorization = new AssistantAuthorizationService(
                    actorAuthority,
                    snapshotAuthority,
                    capabilityPolicy,
                    (actor, security, capability, resource) -> ResourceAuthorizationStatus.ALLOW,
                    controls);

            DependencyHealthRegistry health = new DependencyHealthRegistry(CLOCK);
            health.update(DependencyId.SOURCE_REGISTRY, sourceState, sourceState == DependencyState.UP ? "UP" : "TEST_DOWN", 0L);
            SlidingWindowRateLimiter rateLimiter = new SlidingWindowRateLimiter(
                    maxRequests, Duration.ofMinutes(1), 1024, CLOCK);
            RuntimePipelinePolicy runtimePolicy = new RuntimePipelinePolicy(
                    context -> capabilityAuthorization.authorize(new AuthorizationRequest(
                            context.servicePrincipal(),
                            context.actor(),
                            context.request().assistant(),
                            context.capabilityId(),
                            context.request().resource())),
                    health,
                    rateLimiter,
                    metrics);

            StaticPipelineCapabilityResolver capabilityResolver = new StaticPipelineCapabilityResolver(Map.of(
                    KNOWLEDGE, AssistantCapabilities.KNOWLEDGE_QUERY,
                    SOURCE, AssistantCapabilities.SOURCE_READ));
            RegisteredPipelineRouter routes = new RegisteredPipelineRouter();
            routes.register(
                    KNOWLEDGE,
                    new PipelineRoute(
                            "route.knowledge.answer",
                            PipelineRouteType.DOMAIN,
                            AssistantCapabilities.KNOWLEDGE_QUERY,
                            Set.of(),
                            knowledgeTimeout),
                    knowledgeHandler);
            routes.register(
                    SOURCE,
                    new PipelineRoute(
                            "route.source.inspect",
                            PipelineRouteType.DOMAIN,
                            AssistantCapabilities.SOURCE_READ,
                            Set.of(DependencyId.SOURCE_REGISTRY),
                            Duration.ofSeconds(2)),
                    sourceHandler);

            de.legitserver.assistant.conversations.repository.ConversationRepository noConversationRepository = new NoConversationRepository();
            PipelineCancellationRegistry cancellations = new PipelineCancellationRegistry();
            pipeline = new RuntimeRequestPipeline(
                    new HmacPipelineAuthentication(tokens),
                    new AuthoritativePipelineActorResolver(actorAuthority),
                    new Phase3IngressAuthorization(snapshotAuthority, controls),
                    new RepositoryConversationResolver(noConversationRepository),
                    capabilityResolver,
                    routes,
                    runtimePolicy,
                    routes,
                    new DefaultPipelineResponseSecurity(),
                    new DefaultPipelineFormatter(),
                    new PipelineAudit(new AuditRecorder(audits), CLOCK),
                    new BoundedAdmissionController(maxConcurrency, maxQueued, Duration.ofMillis(20), metrics),
                    new IdempotencyCoordinator(Duration.ofMinutes(10), 1024, CLOCK),
                    cancellations,
                    executionExecutor,
                    metrics,
                    observer,
                    CLOCK,
                    Duration.ofMillis(250));
        }

        private PipelineRequest request(
                PipelineOperation operation,
                String idempotencyKey,
                Map<String, Object> input,
                Duration timeout) {
            int number = sequence.incrementAndGet();
            RequestId requestId = new RequestId("phase13-request-" + number);
            TraceId traceId = new TraceId("phase13-trace-" + number);
            ActorContext actor = new ActorContext(
                    ACTOR_ID,
                    ActorType.HUMAN,
                    Origin.PANEL,
                    "network-phase13",
                    ACTOR_UUID,
                    "panel-phase13",
                    null,
                    requestId,
                    traceId,
                    "network-01",
                    13,
                    NOW);
            RequestContext context = new RequestContext(
                    requestId,
                    traceId,
                    ACTOR_ID,
                    Origin.PANEL,
                    AssistantId.BUGGY,
                    null,
                    "network-01",
                    NOW);
            String token = tokens.issue(new ServiceTokenClaims(
                    "panel-backend",
                    "legit-assistant",
                    Set.of(ServiceScopes.INVOKE),
                    NOW.minusSeconds(1),
                    NOW.plusSeconds(120),
                    "phase13-jti-" + number,
                    requestId));
            ResourceRef resource = operation.equals(SOURCE)
                    ? new ResourceRef("source", "component-networkcore", DataClassification.INTERNAL)
                    : new ResourceRef("knowledge", "catalog-general", DataClassification.INTERNAL);
            return new PipelineRequest(
                    context,
                    token,
                    actor,
                    assistant,
                    operation,
                    resource,
                    input,
                    DataClassification.INTERNAL,
                    DataClassification.INTERNAL,
                    null,
                    idempotencyKey,
                    timeout);
        }

        @Override
        public void close() {
            executionExecutor.shutdownNow();
            try {
                if (!executionExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("execution executor did not terminate");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while terminating execution executor", interrupted);
            }
        }

        private static byte[] key() {
            byte[] bytes = new byte[32];
            java.util.Arrays.fill(bytes, (byte) 0x5a);
            return bytes;
        }

        private static Set<String> permissions(boolean includePanelChannelPermission) {
            HashSet<String> permissions = new HashSet<>();
            permissions.add(AssistantSecurityCatalog.assistantUsePermission(AssistantId.BUGGY));
            if (includePanelChannelPermission) permissions.add("networkcore.assistant.buggy.channel.panel");
            for (AssistantCapabilityId capability : List.of(AssistantCapabilities.KNOWLEDGE_QUERY, AssistantCapabilities.SOURCE_READ)) {
                permissions.addAll(AssistantSecurityCatalog.rule(capability).orElseThrow().allCapabilityPermissions());
            }
            return Set.copyOf(permissions);
        }
    }

    private static final class RecordingAuditPort implements AuditPort {
        private final List<AuditRecord> records = new CopyOnWriteArrayList<>();

        @Override
        public AuditAppendResult append(AuditRecord record) {
            records.add(record);
            return AuditAppendResult.recordedResult();
        }
    }

    private static final class NoConversationRepository implements de.legitserver.assistant.conversations.repository.ConversationRepository {
        @Override
        public void create(Conversation conversation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Optional<Conversation> findById(de.legitserver.assistant.conversations.model.ConversationId conversationId) {
            return java.util.Optional.empty();
        }

        @Override
        public List<de.legitserver.assistant.conversations.model.ConversationMessage> listMessages(
                de.legitserver.assistant.conversations.model.ConversationId conversationId,
                long afterSequenceExclusive,
                int limit) {
            return List.of();
        }

        @Override
        public long countMessages(de.legitserver.assistant.conversations.model.ConversationId conversationId) {
            return 0L;
        }

        @Override
        public Conversation transition(
                de.legitserver.assistant.conversations.model.ConversationId conversationId,
                long expectedRevision,
                de.legitserver.assistant.conversations.model.ConversationState targetState,
                Instant now,
                Instant expiresAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public de.legitserver.assistant.conversations.repository.AppendMessageResult appendMessage(
                de.legitserver.assistant.conversations.model.ConversationId conversationId,
                long expectedRevision,
                de.legitserver.assistant.conversations.model.NewConversationMessage message,
                Instant now,
                Instant expiresAt,
                int maxMessages,
                int maxConversationContextUtf8Bytes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Conversation> findExpiredCandidates(Instant now, int limit) {
            return List.of();
        }
    }
}
