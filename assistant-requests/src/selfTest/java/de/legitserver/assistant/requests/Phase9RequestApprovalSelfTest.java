package de.legitserver.assistant.requests;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.core.request.RequestContext;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.observability.audit.AuditAppendResult;
import de.legitserver.assistant.observability.audit.AuditPort;
import de.legitserver.assistant.observability.audit.AuditRecord;
import de.legitserver.assistant.observability.audit.AuditRecorder;
import de.legitserver.assistant.observability.audit.AuditUnavailableException;
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
import de.legitserver.assistant.requests.repository.DuplicateApprovalException;
import de.legitserver.assistant.requests.repository.RequestRepository;
import de.legitserver.assistant.requests.repository.RequestRevisionConflictException;
import de.legitserver.assistant.requests.service.IllegalRequestStateException;
import de.legitserver.assistant.requests.service.RequestExpiredException;
import de.legitserver.assistant.requests.service.RequestService;
import de.legitserver.assistant.requests.service.SelfApprovalDeniedException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/** Phase-9 offline verification for payload binding, expiry, self-approval, concurrency and audit gates. */
public final class Phase9RequestApprovalSelfTest {
    private static final Instant T0 = Instant.parse("2026-10-04T09:30:00Z");
    private static final Clock CLOCK = Clock.fixed(T0, ZoneOffset.UTC);
    private static final ActorId REQUESTER = new ActorId("staff.requester");
    private static final ActorId APPROVER_A = new ActorId("staff.approver-a");
    private static final ActorId APPROVER_B = new ActorId("staff.approver-b");

    private Phase9RequestApprovalSelfTest() {}

    public static void main(String[] args) throws Exception {
        verifyPayloadHashAndRevisionBinding();
        verifyExpiry();
        verifySelfApprovalPolicy();
        verifyConcurrentApprovalSafety();
        verifyCriticalAuditFailClosed();
        System.out.println("PHASE9_REQUEST_APPROVAL_SELF_TEST: PASS");
    }

    private static void verifyPayloadHashAndRevisionBinding() {
        MemoryRepository repository = new MemoryRepository();
        RecordingAuditPort audit = new RecordingAuditPort();
        RequestService service = service(repository, audit, CLOCK);
        RequestRecord request = service.create(
                draft(RequestRiskLevel.HIGH, 2, "{\"target\":\"server-a\",\"enabled\":true}", null),
                context(REQUESTER, "create-a"));
        String originalHash = request.activePayloadSha256();
        require(repository.payloads.size() == 1, "initial immutable payload revision");

        request = service.approve(request.requestId(), 1, context(APPROVER_A, "approve-a"), "first approval");
        require(request.state() == RequestState.PENDING, "first of two approvals remains pending");
        require(request.revision() == 2, "first approval advances head revision");

        request = service.approve(request.requestId(), 2, context(APPROVER_B, "approve-b"), "second approval");
        require(request.state() == RequestState.APPROVED, "second approval reaches threshold");
        require(request.approvedPayloadRevision() != null && request.approvedPayloadRevision() == 1L,
                "approved revision bound to payload revision 1");
        require(originalHash.equals(request.approvedPayloadSha256()), "approved hash bound exactly");

        request = service.revisePayload(
                request.requestId(),
                3,
                "application/json",
                "{\"target\":\"server-b\",\"enabled\":true}",
                context(REQUESTER, "revise-b"),
                "target changed");
        require(request.state() == RequestState.PENDING, "payload change resets approval state");
        require(request.activePayloadRevision() == 2, "payload revision incremented");
        require(request.approvedPayloadRevision() == null && request.approvedPayloadSha256() == null,
                "payload change clears approval binding");
        ApprovalRequestId replayProbeId = request.requestId();
        expectFailure(
                IllegalRequestStateException.class,
                () -> service.beginExecution(replayProbeId, 4, context(REQUESTER, "replay-blocked")));

        request = service.revisePayload(
                request.requestId(),
                4,
                "application/json",
                "{\"target\":\"server-a\",\"enabled\":true}",
                context(REQUESTER, "revise-back"),
                "revert content");
        require(request.activePayloadRevision() == 3, "reverted content still creates a new payload revision");
        require(request.activePayloadSha256().equals(originalHash), "exact reverted payload has original SHA-256");
        require(repository.countApproved(request.requestId(), 3, originalHash) == 0,
                "old same-hash approvals cannot replay onto a new payload revision");

        request = service.approve(request.requestId(), 5, context(APPROVER_A, "approve-new-a"), "new revision approval");
        require(request.state() == RequestState.PENDING,
                "old approvals did not satisfy threshold for new same-hash revision");
        request = service.approve(request.requestId(), 6, context(APPROVER_B, "approve-new-b"), "new revision approval 2");
        require(request.state() == RequestState.APPROVED, "new revision independently reaches approval threshold");
        require(request.approvedPayloadRevision() == 3L, "new approval binds current revision");

        request = service.beginExecution(request.requestId(), 7, context(REQUESTER, "execute"));
        require(request.state() == RequestState.EXECUTING, "approved bound payload may execute");
        request = service.markExecuted(request.requestId(), 8, context(REQUESTER, "executed"));
        require(request.state() == RequestState.EXECUTED, "execution can complete");
        require(service.history(request.requestId(), 50).size() == 9, "complete append-only request history");
        require(service.approvals(request.requestId(), 50).size() == 4, "all decisions retained append-only");
        require(audit.records.size() >= 18, "requested/success audit records captured for mutations");
    }

    private static void verifyExpiry() {
        MemoryRepository repository = new MemoryRepository();
        RecordingAuditPort audit = new RecordingAuditPort();
        RequestService beforeExpiry = service(repository, audit, CLOCK);
        RequestRecord request = beforeExpiry.create(
                draft(RequestRiskLevel.CRITICAL, 1, "shutdown=false", T0.plusSeconds(60)),
                context(REQUESTER, "critical-create"));
        require(request.expiresAt() != null, "critical request has mandatory expiry");

        Clock expiredClock = Clock.fixed(T0.plusSeconds(61), ZoneOffset.UTC);
        RequestService afterExpiry = service(repository, audit, expiredClock);
        ApprovalRequestId id = request.requestId();
        expectFailure(
                RequestExpiredException.class,
                () -> afterExpiry.approve(id, 1, context(APPROVER_A, "expired-approve"), "too late"));
        RequestRecord expired = afterExpiry.findById(id).orElseThrow();
        require(expired.state() == RequestState.EXPIRED, "expired request transitioned atomically to EXPIRED");
        require(expired.revision() == 2, "expiry advances revision exactly once");
        expectFailure(
                IllegalArgumentException.class,
                () -> beforeExpiry.create(
                        draft(RequestRiskLevel.CRITICAL, 1, "x", null),
                        context(REQUESTER, "critical-no-expiry")));
    }

    private static void verifySelfApprovalPolicy() {
        MemoryRepository repository = new MemoryRepository();
        RecordingAuditPort audit = new RecordingAuditPort();
        RequestService service = service(repository, audit, CLOCK);

        RequestRecord high = service.create(
                draft(RequestRiskLevel.HIGH, 1, "restart=proxy", null),
                context(REQUESTER, "high-create"));
        expectFailure(
                SelfApprovalDeniedException.class,
                () -> service.approve(high.requestId(), 1, context(REQUESTER, "high-self"), "self"));
        require(service.findById(high.requestId()).orElseThrow().revision() == 1,
                "denied high-risk self approval did not mutate request");

        RequestRecord low = service.create(
                draft(RequestRiskLevel.LOW, 1, "note=ack", null),
                context(REQUESTER, "low-create"));
        low = service.approve(low.requestId(), 1, context(REQUESTER, "low-self"), "allowed low-risk self approval");
        require(low.state() == RequestState.APPROVED, "LOW self approval allowed by default");
        require(audit.records.stream().anyMatch(record -> record.outcome().name().equals("DENIED")),
                "self-approval denial audited");
    }

    private static void verifyConcurrentApprovalSafety() throws Exception {
        MemoryRepository repository = new MemoryRepository();
        RecordingAuditPort audit = new RecordingAuditPort();
        RequestService service = service(repository, audit, CLOCK);
        RequestRecord request = service.create(
                draft(RequestRiskLevel.MEDIUM, 1, "deploy=build-9", null),
                context(REQUESTER, "race-create"));
        ApprovalRequestId id = request.requestId();
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = List.of(
                    executor.submit(() -> runConcurrentApproval(service, id, APPROVER_A, start, successes, conflicts)),
                    executor.submit(() -> runConcurrentApproval(service, id, APPROVER_B, start, successes, conflicts)));
            start.countDown();
            for (Future<?> future : futures) future.get();
        } finally {
            executor.shutdownNow();
        }
        require(successes.get() == 1, "exactly one concurrent decision committed");
        require(conflicts.get() == 1, "exactly one concurrent decision rejected by revision conflict");
        RequestRecord current = service.findById(id).orElseThrow();
        require(current.revision() == 2 && current.state() == RequestState.APPROVED,
                "concurrent approval advanced request exactly once");
        require(service.approvals(id, 20).size() == 1, "only committed concurrent approval appended");
    }

    private static void verifyCriticalAuditFailClosed() {
        MemoryRepository repository = new MemoryRepository();
        RecordingAuditPort working = new RecordingAuditPort();
        RequestService creator = service(repository, working, CLOCK);
        RequestRecord request = creator.create(
                draft(RequestRiskLevel.HIGH, 1, "permission=grant", null),
                context(REQUESTER, "audit-create"));

        AuditPort unavailable = record -> AuditAppendResult.unavailable();
        RequestService blocked = service(repository, unavailable, CLOCK);
        expectFailure(
                AuditUnavailableException.class,
                () -> blocked.approve(request.requestId(), 1, context(APPROVER_A, "audit-unavailable"), "approval"));
        RequestRecord unchanged = creator.findById(request.requestId()).orElseThrow();
        require(unchanged.revision() == 1 && unchanged.state() == RequestState.PENDING,
                "critical approval failed closed before state mutation when audit unavailable");
        require(creator.approvals(request.requestId(), 20).isEmpty(), "no approval appended after audit failure");
    }

    private static void runConcurrentApproval(
            RequestService service,
            ApprovalRequestId id,
            ActorId actor,
            CountDownLatch start,
            AtomicInteger successes,
            AtomicInteger conflicts) {
        try {
            start.await();
            service.approve(id, 1, context(actor, "race-" + actor.value()), "race");
            successes.incrementAndGet();
        } catch (RequestRevisionConflictException expected) {
            conflicts.incrementAndGet();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent approval interrupted", interrupted);
        }
    }

    private static RequestService service(MemoryRepository repository, AuditPort auditPort, Clock clock) {
        return new RequestService(repository, new AuditRecorder(auditPort), new RequestApprovalPolicy(), clock);
    }

    private static RequestDraft draft(
            RequestRiskLevel risk,
            int requiredApprovals,
            String payload,
            Instant expiresAt) {
        return new RequestDraft(
                "CONTROLLED_WRITE_REQUEST",
                risk,
                requiredApprovals,
                "application/json",
                payload,
                expiresAt,
                DataClassification.INTERNAL);
    }

    private static RequestContext context(ActorId actorId, String suffix) {
        return new RequestContext(
                new RequestId("req:" + suffix),
                new TraceId("trace:" + suffix),
                actorId,
                Origin.INTERNAL,
                AssistantId.BYTE,
                null,
                "assistant-test",
                T0);
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

    private static final class RecordingAuditPort implements AuditPort {
        private final List<AuditRecord> records = java.util.Collections.synchronizedList(new ArrayList<>());

        @Override
        public AuditAppendResult append(AuditRecord record) {
            records.add(record);
            return AuditAppendResult.recordedResult();
        }
    }

    private static final class MemoryRepository implements RequestRepository {
        private final Map<ApprovalRequestId, RequestRecord> requests = new LinkedHashMap<>();
        private final List<RequestPayload> payloads = new ArrayList<>();
        private final List<RequestApproval> approvals = new ArrayList<>();
        private final List<RequestHistoryEvent> history = new ArrayList<>();

        @Override
        public synchronized void create(RequestRecord request, RequestPayload payload, RequestHistoryEvent createdEvent) {
            if (requests.putIfAbsent(request.requestId(), request) != null) {
                throw new IllegalStateException("duplicate requestId");
            }
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
            return payloads.stream()
                    .filter(payload -> payload.requestId().equals(requestId)
                            && payload.payloadRevision() == head.activePayloadRevision())
                    .findFirst();
        }

        @Override
        public synchronized long countApproved(
                ApprovalRequestId requestId, long payloadRevision, String payloadSha256) {
            return approvals.stream()
                    .filter(approval -> approval.requestId().equals(requestId)
                            && approval.payloadRevision() == payloadRevision
                            && approval.payloadSha256().equals(payloadSha256)
                            && approval.decision() == ApprovalDecision.APPROVE)
                    .count();
        }

        @Override
        public synchronized RequestRecord revisePayload(
                RequestRecord updated,
                long expectedRevision,
                RequestPayload payload,
                RequestHistoryEvent event) {
            requireRevision(updated.requestId(), expectedRevision);
            requests.put(updated.requestId(), updated);
            payloads.add(payload);
            history.add(event);
            return updated;
        }

        @Override
        public synchronized RequestRecord recordDecision(
                RequestRecord updated,
                long expectedRevision,
                RequestApproval approval,
                RequestHistoryEvent event) {
            requireRevision(updated.requestId(), expectedRevision);
            boolean duplicate = approvals.stream().anyMatch(existing ->
                    existing.requestId().equals(approval.requestId())
                            && existing.payloadRevision() == approval.payloadRevision()
                            && existing.approverActorId().equals(approval.approverActorId()));
            if (duplicate) {
                throw new DuplicateApprovalException(
                        approval.requestId(), approval.payloadRevision(), approval.approverActorId());
            }
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
            return approvals.stream()
                    .filter(approval -> approval.requestId().equals(requestId))
                    .limit(limit)
                    .toList();
        }

        @Override
        public synchronized List<RequestHistoryEvent> history(ApprovalRequestId requestId, int limit) {
            return history.stream()
                    .filter(event -> event.requestId().equals(requestId))
                    .limit(limit)
                    .toList();
        }

        private void requireRevision(ApprovalRequestId requestId, long expectedRevision) {
            RequestRecord current = requests.get(requestId);
            if (current == null || current.revision() != expectedRevision) {
                throw new RequestRevisionConflictException(requestId, expectedRevision);
            }
        }
    }
}
