package de.legitserver.assistant.integrations.buggy;

import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentApprovalBinding;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentApprovalStatus;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentId;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentPromotionPackage;
import de.legitserver.assistant.contracts.source.SourceSnapshot;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.request.RequestContext;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.observability.audit.AuditAppendResult;
import de.legitserver.assistant.observability.audit.AuditPort;
import de.legitserver.assistant.observability.audit.AuditRecord;
import de.legitserver.assistant.observability.audit.AuditRecorder;
import de.legitserver.assistant.requests.model.ApprovalDecision;
import de.legitserver.assistant.requests.model.ApprovalRequestId;
import de.legitserver.assistant.requests.model.RequestApproval;
import de.legitserver.assistant.requests.model.RequestHistoryEvent;
import de.legitserver.assistant.requests.model.RequestPayload;
import de.legitserver.assistant.requests.model.RequestRecord;
import de.legitserver.assistant.requests.policy.RequestApprovalPolicy;
import de.legitserver.assistant.requests.repository.DuplicateApprovalException;
import de.legitserver.assistant.requests.repository.RequestRepository;
import de.legitserver.assistant.requests.repository.RequestRevisionConflictException;
import de.legitserver.assistant.requests.service.RequestService;
import de.legitserver.assistant.runtime.model.ToolRiskLevel;
import de.legitserver.assistant.security.secret.SecretGuard;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** B7 cross-module verification against the real Foundation Request/Approval and SecretGuard contracts. */
public final class B7SelfDevelopmentFoundationBindingSelfTest {
    private static final Instant T0 = Instant.parse("2026-10-05T00:15:00Z");
    private static final Clock CLOCK = Clock.fixed(T0, ZoneOffset.UTC);
    private static final ActorId OWNER = new ActorId("owner.himmeljanik");

    private B7SelfDevelopmentFoundationBindingSelfTest() {}

    public static void main(String[] args) {
        verifyFoundationRequestBinding();
        verifyPayloadRevisionInvalidatesBinding();
        verifySecretGuardBoundary();
        System.out.println("B7_SELF_DEVELOPMENT_FOUNDATION_BINDING_SELF_TEST: PASS");
    }

    private static void verifyFoundationRequestBinding() {
        MemoryRepository repository = new MemoryRepository();
        RequestService requests = service(repository);
        FoundationSelfDevelopmentApprovalGateway gateway = new FoundationSelfDevelopmentApprovalGateway(requests, CLOCK);
        BuggySelfDevelopmentApprovalBinding binding = gateway.submitPromotion(promotion(), buggyContext("promotion"));

        RequestRecord created = requests.findById(binding.requestId()).orElseThrow();
        require(created.requestType().equals(FoundationSelfDevelopmentApprovalGateway.PROMOTION_REQUEST_TYPE),
                "promotion uses canonical request type");
        require(created.requesterActorId().value().equals("assistant:buggy"),
                "Buggy service identity is the requester, not the owner");
        require(created.activePayloadSha256().equals(binding.payloadSha256()), "binding uses exact active payload hash");
        require(gateway.status(binding) == BuggySelfDevelopmentApprovalStatus.PENDING, "new promotion is pending");

        RequestRecord approved = requests.approve(
                binding.requestId(), created.revision(), ownerContext("approve"), "owner approved exact promotion payload");
        require(gateway.status(binding) == BuggySelfDevelopmentApprovalStatus.APPROVED, "owner approval becomes visible");
        RequestRecord executing = requests.beginExecution(
                binding.requestId(), approved.revision(), ownerContext("execute"));
        require(gateway.status(binding) == BuggySelfDevelopmentApprovalStatus.APPROVED,
                "executing exact approved payload remains approved for promotion gate");
        requests.markExecuted(binding.requestId(), executing.revision(), ownerContext("executed"));
        require(gateway.status(binding) == BuggySelfDevelopmentApprovalStatus.EXECUTED,
                "executed exact payload unlocks deployment completion");
    }

    private static void verifyPayloadRevisionInvalidatesBinding() {
        MemoryRepository repository = new MemoryRepository();
        RequestService requests = service(repository);
        FoundationSelfDevelopmentApprovalGateway gateway = new FoundationSelfDevelopmentApprovalGateway(requests, CLOCK);
        BuggySelfDevelopmentApprovalBinding binding = gateway.submitPromotion(promotion(), buggyContext("tamper"));
        RequestRecord created = requests.findById(binding.requestId()).orElseThrow();
        requests.revisePayload(
                binding.requestId(), created.revision(),
                "application/vnd.legit.buggy-selfdev.v1+text",
                promotion().canonicalBody() + "\nmutated=true",
                ownerContext("revise"), "test binding invalidation");
        require(gateway.status(binding) == BuggySelfDevelopmentApprovalStatus.TERMINAL_FAILURE,
                "payload revision invalidates original promotion binding");
    }

    private static void verifySecretGuardBoundary() {
        FoundationSecretGuardedSelfDevelopmentContentPolicy policy =
                new FoundationSecretGuardedSelfDevelopmentContentPolicy(new SecretGuard());
        require(policy.requirePersistableText("safe proposal", "proposal", 100).equals("safe proposal"),
                "safe text is retained");
        expectFailure(IllegalArgumentException.class,
                () -> policy.requirePersistableText("password=supersecret", "proposal", 100));
    }

    private static BuggySelfDevelopmentPromotionPackage promotion() {
        return new BuggySelfDevelopmentPromotionPackage(
                BuggySelfDevelopmentId.random(),
                7,
                new SourceSnapshot(
                        "assistant-buggy", "0.1.0-dev.18", "build-b7", "rev-b7", T0,
                        "1111111111111111111111111111111111111111111111111111111111111111"),
                "artifact:patch/b7.patch",
                "artifact:build/b7.jar",
                ToolRiskLevel.MEDIUM,
                List.of("2222222222222222222222222222222222222222222222222222222222222222"));
    }

    private static RequestService service(MemoryRepository repository) {
        AuditPort audit = record -> AuditAppendResult.recordedResult();
        return new RequestService(repository, new AuditRecorder(audit), new RequestApprovalPolicy(), CLOCK);
    }

    private static RequestContext buggyContext(String suffix) {
        return new RequestContext(
                new RequestId("req:b7:" + suffix), new TraceId("trace:b7:" + suffix),
                new ActorId("owner.himmeljanik"), Origin.INTERNAL, AssistantId.BUGGY,
                null, "assistant-test", T0);
    }

    private static RequestContext ownerContext(String suffix) {
        return new RequestContext(
                new RequestId("req:b7:owner:" + suffix), new TraceId("trace:b7:owner:" + suffix),
                OWNER, Origin.INTERNAL, AssistantId.BUGGY, null, "assistant-test", T0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Assertion failed: " + message);
    }

    private static <T extends Throwable> void expectFailure(Class<T> type, Runnable operation) {
        try {
            operation.run();
            throw new IllegalStateException("Expected failure: " + type.getSimpleName());
        } catch (Throwable failure) {
            if (type.isInstance(failure)) return;
            throw failure;
        }
    }

    private static final class MemoryRepository implements RequestRepository {
        private final Map<ApprovalRequestId, RequestRecord> requests = new LinkedHashMap<>();
        private final List<RequestPayload> payloads = new ArrayList<>();
        private final List<RequestApproval> approvals = new ArrayList<>();
        private final List<RequestHistoryEvent> history = new ArrayList<>();

        @Override
        public synchronized void create(RequestRecord request, RequestPayload payload, RequestHistoryEvent event) {
            if (requests.putIfAbsent(request.requestId(), request) != null) throw new IllegalStateException("duplicate request");
            payloads.add(payload);
            history.add(event);
        }

        @Override
        public synchronized Optional<RequestRecord> findById(ApprovalRequestId requestId) {
            return Optional.ofNullable(requests.get(requestId));
        }

        @Override
        public synchronized Optional<RequestPayload> activePayload(ApprovalRequestId requestId) {
            RequestRecord head = requests.get(requestId);
            if (head == null) return Optional.empty();
            return payloads.stream().filter(p -> p.requestId().equals(requestId)
                    && p.payloadRevision() == head.activePayloadRevision()).findFirst();
        }

        @Override
        public synchronized long countApproved(ApprovalRequestId requestId, long payloadRevision, String payloadSha256) {
            return approvals.stream().filter(a -> a.requestId().equals(requestId)
                    && a.payloadRevision() == payloadRevision
                    && a.payloadSha256().equals(payloadSha256)
                    && a.decision() == ApprovalDecision.APPROVE).count();
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
            return approvals.stream().filter(a -> a.requestId().equals(requestId)).limit(limit).toList();
        }

        @Override
        public synchronized List<RequestHistoryEvent> history(ApprovalRequestId requestId, int limit) {
            return history.stream().filter(e -> e.requestId().equals(requestId)).limit(limit).toList();
        }

        private void requireRevision(ApprovalRequestId requestId, long expectedRevision) {
            RequestRecord current = requests.get(requestId);
            if (current == null || current.revision() != expectedRevision) {
                throw new RequestRevisionConflictException(requestId, expectedRevision);
            }
        }
    }
}
