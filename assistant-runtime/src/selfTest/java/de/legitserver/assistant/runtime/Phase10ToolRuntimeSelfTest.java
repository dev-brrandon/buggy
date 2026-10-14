package de.legitserver.assistant.runtime;

import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.actor.ActorType;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.core.assistant.AssistantCapabilities;
import de.legitserver.assistant.core.assistant.AssistantDescriptor;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.observability.audit.AuditAppendResult;
import de.legitserver.assistant.observability.audit.AuditPort;
import de.legitserver.assistant.observability.audit.AuditRecord;
import de.legitserver.assistant.observability.audit.AuditRecorder;
import de.legitserver.assistant.observability.metrics.AssistantMetric;
import de.legitserver.assistant.observability.metrics.InMemoryAssistantMetrics;
import de.legitserver.assistant.requests.model.ApprovalRequestId;
import de.legitserver.assistant.requests.model.RequestApproval;
import de.legitserver.assistant.requests.model.RequestHistoryEvent;
import de.legitserver.assistant.requests.model.RequestPayload;
import de.legitserver.assistant.requests.model.RequestRecord;
import de.legitserver.assistant.requests.model.RequestRiskLevel;
import de.legitserver.assistant.requests.model.RequestState;
import de.legitserver.assistant.requests.repository.RequestRepository;
import de.legitserver.assistant.runtime.audit.ToolAudit;
import de.legitserver.assistant.runtime.execution.ToolFailureCode;
import de.legitserver.assistant.runtime.execution.ToolOutputFilter;
import de.legitserver.assistant.runtime.execution.ToolRuntime;
import de.legitserver.assistant.runtime.execution.ToolRuntimeException;
import de.legitserver.assistant.runtime.model.ToolAuditPolicy;
import de.legitserver.assistant.runtime.model.ToolClassificationPolicy;
import de.legitserver.assistant.runtime.model.ToolDescriptor;
import de.legitserver.assistant.runtime.model.ToolId;
import de.legitserver.assistant.runtime.model.ToolInvocation;
import de.legitserver.assistant.runtime.model.ToolInvocationResult;
import de.legitserver.assistant.runtime.model.ToolOutput;
import de.legitserver.assistant.runtime.model.ToolRiskLevel;
import de.legitserver.assistant.runtime.policy.RequestApprovalToolRiskPolicy;
import de.legitserver.assistant.runtime.policy.ToolInvocationCanonicalizer;
import de.legitserver.assistant.runtime.registry.ToolRegistry;
import de.legitserver.assistant.runtime.schema.ToolFieldSchema;
import de.legitserver.assistant.runtime.schema.ToolSchema;
import de.legitserver.assistant.runtime.schema.ToolSchemaValidator;
import de.legitserver.assistant.runtime.schema.ToolValueType;
import de.legitserver.assistant.security.authn.BoundedReplayProtector;
import de.legitserver.assistant.security.authn.HmacServiceTokenService;
import de.legitserver.assistant.security.authn.ServiceAuthenticationResult;
import de.legitserver.assistant.security.authn.ServicePrincipal;
import de.legitserver.assistant.security.authn.ServiceScopes;
import de.legitserver.assistant.security.authn.ServiceTokenClaims;
import de.legitserver.assistant.security.authorization.AuthorizationDecision;
import de.legitserver.assistant.security.authorization.AuthorizationDenialReason;
import de.legitserver.assistant.security.authorization.AuthorizationRiskLevel;
import de.legitserver.assistant.security.authorization.ResourceRef;
import de.legitserver.assistant.security.secret.SecretGuard;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Phase-10 offline gates for the controlled Tool Runtime. */
public final class Phase10ToolRuntimeSelfTest {
    private static final Instant T0 = Instant.parse("2026-10-04T10:30:00Z");
    private static final Clock CLOCK = Clock.fixed(T0, ZoneOffset.UTC);
    private static final ActorId ACTOR_ID = new ActorId("staff.tool-tester");
    private static final ToolSchema TEXT_INPUT = ToolSchema.of(
            new ToolFieldSchema("message", ToolValueType.STRING, true, 256));
    private static final ToolSchema TEXT_OUTPUT = ToolSchema.of(
            new ToolFieldSchema("result", ToolValueType.STRING, true, 4096));

    private Phase10ToolRuntimeSelfTest() {}

    public static void main(String[] args) throws Exception {
        Path projectRoot = args.length == 0 ? Path.of(".").toAbsolutePath().normalize() : Path.of(args[0]);
        verifyNoArbitraryShellAndDescriptorGuards(projectRoot);
        verifyAuthorizationAndInvalidInputRejected();
        verifyTimeout();
        verifyConcurrencyLimit();
        verifyOutputSanitizedAndClassificationBounded();
        verifyExactApprovalBindingAndCriticalAudit();
        System.out.println("PHASE10_TOOL_RUNTIME_SELF_TEST: PASS");
    }

    private static void verifyNoArbitraryShellAndDescriptorGuards(Path projectRoot) throws Exception {
        Path mainSource = projectRoot.resolve("assistant-runtime/src/main/java");
        String forbiddenA = "Process" + "Builder";
        String forbiddenB = "Runtime." + "getRuntime";
        String forbiddenC = "/bin/" + "sh";
        String forbiddenD = "bash" + " -c";
        try (var paths = Files.walk(mainSource)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                require(!source.contains(forbiddenA), "runtime main source must not expose process builder execution");
                require(!source.contains(forbiddenB), "runtime main source must not expose Runtime.exec path");
                require(!source.contains(forbiddenC), "runtime main source must not hard-code shell binary");
                require(!source.contains(forbiddenD), "runtime main source must not hard-code shell command mode");
            }
        }

        ToolRegistry registry = new ToolRegistry();
        expectFailure(IllegalArgumentException.class, () -> registry.register(
                descriptor("tool.bug.update", ToolRiskLevel.MEDIUM, AssistantCapabilities.BUG_UPDATE, Duration.ofSeconds(1), 1),
                context -> new ToolOutput(Map.of("result", "x"), DataClassification.INTERNAL)));
        expectFailure(IllegalArgumentException.class, () -> registry.register(
                new ToolDescriptor(
                        new ToolId("tool.invalid.selfheal"),
                        AssistantCapabilities.KNOWLEDGE_QUERY,
                        ToolRiskLevel.LOW,
                        TEXT_INPUT,
                        TEXT_OUTPUT,
                        Duration.ofSeconds(1),
                        1,
                        ToolAuditPolicy.REQUIRED,
                        classificationPolicy(),
                        true),
                context -> new ToolOutput(Map.of("result", "x"), DataClassification.INTERNAL)));
    }

    private static void verifyAuthorizationAndInvalidInputRejected() {
        AtomicInteger executions = new AtomicInteger();
        RecordingAuditPort audits = new RecordingAuditPort();
        InMemoryAssistantMetrics metrics = new InMemoryAssistantMetrics();
        MemoryRequestRepository requests = new MemoryRequestRepository();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            ToolRegistry registry = new ToolRegistry();
            ToolDescriptor descriptor = descriptor(
                    "tool.knowledge.echo", ToolRiskLevel.LOW, AssistantCapabilities.KNOWLEDGE_QUERY, Duration.ofSeconds(1), 2);
            registry.register(descriptor, context -> {
                executions.incrementAndGet();
                return new ToolOutput(Map.of("result", context.input().get("message")), DataClassification.INTERNAL);
            });

            ToolRuntime denied = runtime(registry, requests, audits, metrics, executor,
                    (tool, invocation) -> AuthorizationDecision.deny(
                            AuthorizationDenialReason.PERMISSION_DENIED, 7, AuthorizationRiskLevel.LOW));
            expectToolFailure(ToolFailureCode.AUTHORIZATION_DENIED,
                    () -> denied.invoke(invocation(descriptor.toolId(), Map.of("message", "hello"), null)));
            require(executions.get() == 0, "authorization denial must happen before handler execution");

            ToolRuntime allowed = runtime(registry, requests, audits, metrics, executor,
                    (tool, invocation) -> AuthorizationDecision.allow(7, AuthorizationRiskLevel.LOW));
            expectToolFailure(ToolFailureCode.INPUT_SCHEMA_INVALID,
                    () -> allowed.invoke(invocation(descriptor.toolId(), Map.of("message", 42), null)));
            require(executions.get() == 0, "invalid input must be rejected before handler execution");
            require(!audits.records.isEmpty(), "denied/invalid attempts must generate audit events");
        } finally {
            executor.shutdownNow();
        }
    }

    private static void verifyTimeout() {
        RecordingAuditPort audits = new RecordingAuditPort();
        InMemoryAssistantMetrics metrics = new InMemoryAssistantMetrics();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicBoolean interrupted = new AtomicBoolean();
        try {
            ToolRegistry registry = new ToolRegistry();
            ToolDescriptor descriptor = descriptor(
                    "tool.knowledge.timeout", ToolRiskLevel.LOW, AssistantCapabilities.KNOWLEDGE_QUERY, Duration.ofMillis(60), 1);
            registry.register(descriptor, context -> {
                try {
                    Thread.sleep(5_000);
                } catch (InterruptedException expected) {
                    interrupted.set(true);
                    Thread.currentThread().interrupt();
                }
                return new ToolOutput(Map.of("result", "late"), DataClassification.INTERNAL);
            });
            ToolRuntime runtime = runtime(registry, new MemoryRequestRepository(), audits, metrics, executor,
                    (tool, invocation) -> AuthorizationDecision.allow(1, AuthorizationRiskLevel.LOW));
            expectToolFailure(ToolFailureCode.TIMEOUT,
                    () -> runtime.invoke(invocation(descriptor.toolId(), Map.of("message", "slow"), null)));
            for (int i = 0; i < 50 && !interrupted.get(); i++) sleep(10);
            require(interrupted.get(), "timeout must cancel/interupt handler future");
            require(metrics.snapshot().scalar(AssistantMetric.TOOL_FAILURES) >= 1, "timeout increments tool failure metric");
        } finally {
            executor.shutdownNow();
        }
    }

    private static void verifyConcurrencyLimit() throws Exception {
        RecordingAuditPort audits = new RecordingAuditPort();
        InMemoryAssistantMetrics metrics = new InMemoryAssistantMetrics();
        ExecutorService handlerExecutor = Executors.newFixedThreadPool(2);
        ExecutorService callerExecutor = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            ToolRegistry registry = new ToolRegistry();
            ToolDescriptor descriptor = descriptor(
                    "tool.knowledge.serial", ToolRiskLevel.LOW, AssistantCapabilities.KNOWLEDGE_QUERY, Duration.ofSeconds(3), 1);
            registry.register(descriptor, context -> {
                entered.countDown();
                if (!release.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("release timeout");
                return new ToolOutput(Map.of("result", "done"), DataClassification.INTERNAL);
            });
            ToolRuntime runtime = runtime(registry, new MemoryRequestRepository(), audits, metrics, handlerExecutor,
                    (tool, invocation) -> AuthorizationDecision.allow(1, AuthorizationRiskLevel.LOW));
            Future<ToolInvocationResult> first = callerExecutor.submit(
                    () -> runtime.invoke(invocation(descriptor.toolId(), Map.of("message", "first"), null)));
            require(entered.await(1, TimeUnit.SECONDS), "first handler entered");
            expectToolFailure(ToolFailureCode.CONCURRENCY_LIMIT,
                    () -> runtime.invoke(invocation(descriptor.toolId(), Map.of("message", "second"), null)));
            release.countDown();
            require("done".equals(first.get(2, TimeUnit.SECONDS).output().get("result")), "first invocation completes");
        } finally {
            release.countDown();
            callerExecutor.shutdownNow();
            handlerExecutor.shutdownNow();
        }
    }

    private static void verifyOutputSanitizedAndClassificationBounded() {
        RecordingAuditPort audits = new RecordingAuditPort();
        InMemoryAssistantMetrics metrics = new InMemoryAssistantMetrics();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            ToolRegistry registry = new ToolRegistry();
            ToolDescriptor descriptor = descriptor(
                    "tool.knowledge.sanitize", ToolRiskLevel.LOW, AssistantCapabilities.KNOWLEDGE_QUERY, Duration.ofSeconds(1), 1);
            registry.register(descriptor, context -> new ToolOutput(
                    Map.of("result", "password=supersecret Authorization: Bearer abcdefghijklmnop"),
                    DataClassification.INTERNAL));
            ToolRuntime runtime = runtime(registry, new MemoryRequestRepository(), audits, metrics, executor,
                    (tool, invocation) -> AuthorizationDecision.allow(1, AuthorizationRiskLevel.LOW));
            ToolInvocationResult result = runtime.invoke(invocation(descriptor.toolId(), Map.of("message", "safe"), null));
            String output = (String) result.output().get("result");
            require(output.contains("[REDACTED]"), "secret material must be redacted");
            require(!output.contains("supersecret") && !output.contains("abcdefghijklmnop"), "raw secrets must not escape");
            require(audits.records.stream().anyMatch(record -> record.outcome().name().equals("SUCCESS")),
                    "successful tool call must be audited");

            ToolRegistry restrictedRegistry = new ToolRegistry();
            ToolDescriptor restricted = new ToolDescriptor(
                    new ToolId("tool.knowledge.classification"),
                    AssistantCapabilities.KNOWLEDGE_QUERY,
                    ToolRiskLevel.LOW,
                    TEXT_INPUT,
                    TEXT_OUTPUT,
                    Duration.ofSeconds(1),
                    1,
                    ToolAuditPolicy.REQUIRED,
                    new ToolClassificationPolicy(DataClassification.INTERNAL, DataClassification.INTERNAL),
                    false);
            restrictedRegistry.register(restricted, context -> new ToolOutput(Map.of("result", "secret data"), DataClassification.SECRET));
            ToolRuntime restrictedRuntime = runtime(restrictedRegistry, new MemoryRequestRepository(), audits, metrics, executor,
                    (tool, invocation) -> AuthorizationDecision.allow(1, AuthorizationRiskLevel.LOW));
            expectToolFailure(ToolFailureCode.OUTPUT_CLASSIFICATION_DENIED,
                    () -> restrictedRuntime.invoke(invocation(restricted.toolId(), Map.of("message", "x"), null)));
        } finally {
            executor.shutdownNow();
        }
    }

    private static void verifyExactApprovalBindingAndCriticalAudit() {
        RecordingAuditPort audits = new RecordingAuditPort();
        InMemoryAssistantMetrics metrics = new InMemoryAssistantMetrics();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            ToolRegistry registry = new ToolRegistry();
            ToolDescriptor descriptor = descriptor(
                    "tool.bug.update", ToolRiskLevel.HIGH, AssistantCapabilities.BUG_UPDATE, Duration.ofSeconds(1), 1);
            registry.register(descriptor, context -> new ToolOutput(Map.of("result", "updated"), DataClassification.INTERNAL));
            ApprovalRequestId approvalId = ApprovalRequestId.random();
            ToolInvocation approvedInvocation = invocation(descriptor.toolId(), Map.of("message", "apply-fix"), approvalId);
            String canonical = ToolInvocationCanonicalizer.canonicalPayload(approvedInvocation);
            MemoryRequestRepository requests = MemoryRequestRepository.approved(
                    approvalId, RequestRiskLevel.HIGH, canonical, approvedInvocation.actor().actorId());
            ToolRuntime runtime = runtime(registry, requests, audits, metrics, executor,
                    (tool, invocation) -> AuthorizationDecision.allow(10, AuthorizationRiskLevel.HIGH));
            ToolInvocationResult result = runtime.invoke(approvedInvocation);
            require("updated".equals(result.output().get("result")), "exact approved invocation executes");

            ToolInvocation changed = invocation(descriptor.toolId(), Map.of("message", "different-fix"), approvalId);
            expectToolFailure(ToolFailureCode.APPROVAL_INVALID, () -> runtime.invoke(changed));

            AuditPort unavailable = record -> AuditAppendResult.unavailable();
            ToolRuntime failClosed = runtime(registry, requests, unavailable, new InMemoryAssistantMetrics(), executor,
                    (tool, invocation) -> AuthorizationDecision.allow(10, AuthorizationRiskLevel.HIGH));
            expectToolFailure(ToolFailureCode.AUDIT_UNAVAILABLE, () -> failClosed.invoke(approvedInvocation));
            long requested = audits.records.stream().filter(record -> record.outcome().name().equals("REQUESTED")).count();
            require(requested >= 1, "critical tool records REQUESTED audit before execution");
        } finally {
            executor.shutdownNow();
        }
    }

    private static ToolRuntime runtime(
            ToolRegistry registry,
            MemoryRequestRepository requests,
            AuditPort auditPort,
            InMemoryAssistantMetrics metrics,
            ExecutorService executor,
            de.legitserver.assistant.runtime.policy.ToolAuthorizationPort authorization) {
        return new ToolRuntime(
                registry,
                authorization,
                new ToolSchemaValidator(),
                new RequestApprovalToolRiskPolicy(requests),
                new ToolOutputFilter(new SecretGuard()),
                new ToolAudit(new AuditRecorder(auditPort)),
                metrics,
                executor,
                CLOCK);
    }

    private static ToolDescriptor descriptor(
            String id,
            ToolRiskLevel risk,
            de.legitserver.assistant.core.assistant.AssistantCapabilityId capability,
            Duration timeout,
            int maxConcurrency) {
        return new ToolDescriptor(
                new ToolId(id),
                capability,
                risk,
                TEXT_INPUT,
                TEXT_OUTPUT,
                timeout,
                maxConcurrency,
                risk.isHighRisk() ? ToolAuditPolicy.CRITICAL_REQUIRED : ToolAuditPolicy.REQUIRED,
                classificationPolicy(),
                false);
    }

    private static ToolClassificationPolicy classificationPolicy() {
        return new ToolClassificationPolicy(DataClassification.CONFIDENTIAL, DataClassification.CONFIDENTIAL);
    }

    private static ToolInvocation invocation(ToolId toolId, Map<String, Object> input, ApprovalRequestId approvalId) {
        String requestToken = "tool-" + Math.abs(toolId.value().hashCode()) + "-" + Math.abs(input.hashCode());
        RequestId requestId = new RequestId(requestToken);
        ServicePrincipal principal = principal(requestId, requestToken + "-jti");
        ActorContext actor = new ActorContext(
                ACTOR_ID,
                ActorType.HUMAN,
                Origin.PANEL,
                "identity.tool.tester",
                null,
                "panel.tool.tester",
                null,
                requestId,
                new TraceId("trace-" + Math.abs(requestToken.hashCode())),
                "survival-01",
                10,
                T0.minusSeconds(1));
        AssistantDescriptor assistant = new AssistantDescriptor(
                AssistantId.BYTE,
                "Byte",
                true,
                "default",
                "default",
                "default",
                "default",
                null,
                1,
                T0.minusSeconds(10));
        return new ToolInvocation(
                toolId,
                principal,
                actor,
                assistant,
                new ResourceRef("server", "survival-01", DataClassification.INTERNAL),
                input,
                DataClassification.INTERNAL,
                approvalId);
    }

    private static ServicePrincipal principal(RequestId requestId, String jti) {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) key[i] = (byte) (i + 1);
        HmacServiceTokenService tokens = new HmacServiceTokenService(key, new BoundedReplayProtector(32));
        ServiceTokenClaims claims = new ServiceTokenClaims(
                "assistant-api",
                "legit-assistant",
                Set.of(ServiceScopes.INVOKE),
                T0.minusSeconds(5),
                T0.plusSeconds(60),
                jti,
                requestId);
        String token = tokens.issue(claims);
        ServiceAuthenticationResult result = tokens.verify(
                token, "legit-assistant", requestId, Set.of(ServiceScopes.INVOKE), T0);
        require(result.authenticated(), "self-test service principal minting");
        return result.principal();
    }

    private static void expectToolFailure(ToolFailureCode code, ThrowingRunnable runnable) {
        try {
            runnable.run();
            throw new IllegalStateException("Expected ToolRuntimeException " + code);
        } catch (ToolRuntimeException expected) {
            require(expected.code() == code, "expected failure " + code + " but got " + expected.code());
        } catch (Exception other) {
            throw new IllegalStateException("Unexpected exception type", other);
        }
    }

    private static void expectFailure(Class<? extends Throwable> type, ThrowingRunnable runnable) {
        try {
            runnable.run();
            throw new IllegalStateException("Expected failure " + type.getSimpleName());
        } catch (Throwable expected) {
            if (!type.isInstance(expected)) throw new IllegalStateException("Unexpected failure", expected);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", interrupted);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class RecordingAuditPort implements AuditPort {
        private final List<AuditRecord> records = new ArrayList<>();

        @Override
        public synchronized AuditAppendResult append(AuditRecord record) {
            records.add(record);
            return AuditAppendResult.recordedResult();
        }
    }

    private static final class MemoryRequestRepository implements RequestRepository {
        private RequestRecord request;
        private RequestPayload payload;

        static MemoryRequestRepository approved(
                ApprovalRequestId id,
                RequestRiskLevel risk,
                String payloadBody,
                ActorId actor) {
            MemoryRequestRepository repository = new MemoryRequestRepository();
            RequestPayload payload = RequestPayload.create(
                    id,
                    1,
                    RequestApprovalToolRiskPolicy.CONTENT_TYPE,
                    payloadBody,
                    actor,
                    T0.minusSeconds(20));
            repository.payload = payload;
            repository.request = new RequestRecord(
                    id,
                    RequestApprovalToolRiskPolicy.REQUEST_TYPE,
                    actor,
                    RequestState.APPROVED,
                    risk,
                    1,
                    1,
                    payload.sha256(),
                    1L,
                    payload.sha256(),
                    risk == RequestRiskLevel.CRITICAL ? T0.plusSeconds(60) : null,
                    DataClassification.INTERNAL,
                    T0.minusSeconds(20),
                    T0.minusSeconds(10),
                    2);
            return repository;
        }

        @Override
        public void create(RequestRecord request, RequestPayload payload, RequestHistoryEvent createdEvent) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<RequestRecord> findById(ApprovalRequestId requestId) {
            return request != null && request.requestId().equals(requestId) ? Optional.of(request) : Optional.empty();
        }

        @Override
        public Optional<RequestPayload> activePayload(ApprovalRequestId requestId) {
            return payload != null && payload.requestId().equals(requestId) ? Optional.of(payload) : Optional.empty();
        }

        @Override
        public long countApproved(ApprovalRequestId requestId, long payloadRevision, String payloadSha256) {
            return 0;
        }

        @Override
        public RequestRecord revisePayload(
                RequestRecord updated,
                long expectedRevision,
                RequestPayload payload,
                RequestHistoryEvent event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RequestRecord recordDecision(
                RequestRecord updated,
                long expectedRevision,
                RequestApproval approval,
                RequestHistoryEvent event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RequestRecord transition(RequestRecord updated, long expectedRevision, RequestHistoryEvent event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RequestApproval> approvals(ApprovalRequestId requestId, int limit) {
            return List.of();
        }

        @Override
        public List<RequestHistoryEvent> history(ApprovalRequestId requestId, int limit) {
            return List.of();
        }
    }
}
