package de.legitserver.assistant.requests.service;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.request.RequestContext;
import de.legitserver.assistant.observability.audit.AuditAction;
import de.legitserver.assistant.observability.audit.AuditOutcome;
import de.legitserver.assistant.observability.audit.AuditRecord;
import de.legitserver.assistant.observability.audit.AuditRecorder;
import de.legitserver.assistant.requests.model.ApprovalDecision;
import de.legitserver.assistant.requests.model.ApprovalRequestId;
import de.legitserver.assistant.requests.model.RequestApproval;
import de.legitserver.assistant.requests.model.RequestDraft;
import de.legitserver.assistant.requests.model.RequestHistoryEvent;
import de.legitserver.assistant.requests.model.RequestHistoryEventType;
import de.legitserver.assistant.requests.model.RequestPayload;
import de.legitserver.assistant.requests.model.RequestRecord;
import de.legitserver.assistant.requests.model.RequestState;
import de.legitserver.assistant.requests.policy.RequestApprovalPolicy;
import de.legitserver.assistant.requests.policy.RequestLimits;
import de.legitserver.assistant.requests.repository.RequestNotFoundException;
import de.legitserver.assistant.requests.repository.RequestRepository;
import de.legitserver.assistant.requests.validation.RequestValidation;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Application service implementing the Phase-9 Request / Approval lifecycle. */
public final class RequestService {
    private final RequestRepository repository;
    private final AuditRecorder auditRecorder;
    private final RequestApprovalPolicy policy;
    private final Clock clock;

    public RequestService(
            RequestRepository repository,
            AuditRecorder auditRecorder,
            RequestApprovalPolicy policy,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public RequestRecord create(RequestDraft draft, RequestContext context) {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(context, "context");
        Instant now = clock.instant();
        policy.validateCreation(draft, now);
        ApprovalRequestId id = ApprovalRequestId.random();
        RequestPayload payload = RequestPayload.create(
                id, 1, draft.payloadContentType(), draft.payloadBody(), context.actorId(), now);
        RequestRecord request = new RequestRecord(
                id,
                draft.requestType(),
                context.actorId(),
                RequestState.PENDING,
                draft.riskLevel(),
                draft.requiredApprovals(),
                1,
                payload.sha256(),
                null,
                null,
                draft.expiresAt(),
                draft.classification(),
                now,
                now,
                1);
        RequestHistoryEvent event = RequestHistoryEvent.create(
                request,
                RequestHistoryEventType.CREATED,
                context.actorId(),
                null,
                RequestState.PENDING,
                Map.of("requiredApprovals", Integer.toString(request.requiredApprovals())),
                now);
        auditBefore(context, request, AuditAction.WRITE_REQUEST, "CREATE", null);
        repository.create(request, payload, event);
        auditSuccess(context, request, AuditAction.WRITE_REQUEST, "CREATE");
        return request;
    }

    public RequestRecord revisePayload(
            ApprovalRequestId requestId,
            long expectedRevision,
            String contentType,
            String payloadBody,
            RequestContext context,
            String reason) {
        RequestRecord current = requireRequest(requestId);
        requireExpectedRevision(current, expectedRevision);
        requireState("REVISE_PAYLOAD", current.state(), RequestState.PENDING, RequestState.APPROVED);
        current = requireLive(current, expectedRevision, context);
        String normalizedBody = RequestValidation.payloadBody(payloadBody);
        String newHash = RequestValidation.sha256Of(normalizedBody);
        if (newHash.equals(current.activePayloadSha256())) {
            throw new IllegalArgumentException("Payload hash is unchanged");
        }
        Instant now = clock.instant();
        RequestPayload payload = RequestPayload.create(
                current.requestId(),
                current.activePayloadRevision() + 1,
                contentType,
                normalizedBody,
                context.actorId(),
                now);
        RequestRecord updated = copy(
                current,
                RequestState.PENDING,
                payload.payloadRevision(),
                payload.sha256(),
                null,
                null,
                now,
                expectedRevision + 1);
        RequestHistoryEvent event = RequestHistoryEvent.create(
                updated,
                RequestHistoryEventType.PAYLOAD_REVISED,
                context.actorId(),
                current.state(),
                RequestState.PENDING,
                details("previousPayloadSha256", current.activePayloadSha256(), "reason", reason),
                now);
        auditBefore(context, updated, AuditAction.WRITE_REQUEST, "REVISE_PAYLOAD", reason);
        RequestRecord persisted = repository.revisePayload(updated, expectedRevision, payload, event);
        auditSuccess(context, persisted, AuditAction.WRITE_REQUEST, "REVISE_PAYLOAD");
        return persisted;
    }

    public RequestRecord approve(
            ApprovalRequestId requestId,
            long expectedRevision,
            RequestContext context,
            String reason) {
        return decide(requestId, expectedRevision, ApprovalDecision.APPROVE, context, reason);
    }

    public RequestRecord deny(
            ApprovalRequestId requestId,
            long expectedRevision,
            RequestContext context,
            String reason) {
        return decide(requestId, expectedRevision, ApprovalDecision.DENY, context, reason);
    }

    public RequestRecord cancel(
            ApprovalRequestId requestId,
            long expectedRevision,
            RequestContext context,
            String reason) {
        RequestRecord current = requireRequest(requestId);
        requireExpectedRevision(current, expectedRevision);
        requireState("CANCEL", current.state(), RequestState.PENDING, RequestState.APPROVED);
        current = requireLive(current, expectedRevision, context);
        Instant now = clock.instant();
        RequestRecord updated = copy(
                current,
                RequestState.CANCELLED,
                current.activePayloadRevision(),
                current.activePayloadSha256(),
                null,
                null,
                now,
                expectedRevision + 1);
        RequestHistoryEvent event = RequestHistoryEvent.create(
                updated,
                RequestHistoryEventType.CANCELLED,
                context.actorId(),
                current.state(),
                RequestState.CANCELLED,
                details("reason", reason),
                now);
        return auditedTransition(current, updated, expectedRevision, event, context, "CANCEL");
    }

    public RequestRecord expireIfDue(
            ApprovalRequestId requestId, long expectedRevision, RequestContext context) {
        RequestRecord current = requireRequest(requestId);
        requireExpectedRevision(current, expectedRevision);
        requireState("EXPIRE", current.state(), RequestState.PENDING, RequestState.APPROVED);
        if (!current.isExpiredAt(clock.instant())) return current;
        return expire(current, expectedRevision, context);
    }

    public RequestRecord beginExecution(
            ApprovalRequestId requestId,
            long expectedRevision,
            RequestContext context) {
        RequestRecord current = requireRequest(requestId);
        requireExpectedRevision(current, expectedRevision);
        requireState("BEGIN_EXECUTION", current.state(), RequestState.APPROVED);
        current = requireLive(current, expectedRevision, context);
        if (current.approvedPayloadRevision() == null
                || current.approvedPayloadRevision().longValue() != current.activePayloadRevision()
                || !current.activePayloadSha256().equals(current.approvedPayloadSha256())) {
            throw new IllegalRequestStateException("BEGIN_EXECUTION_PAYLOAD_BINDING", current.state());
        }
        Instant now = clock.instant();
        RequestRecord updated = copy(
                current,
                RequestState.EXECUTING,
                current.activePayloadRevision(),
                current.activePayloadSha256(),
                current.approvedPayloadRevision(),
                current.approvedPayloadSha256(),
                now,
                expectedRevision + 1);
        RequestHistoryEvent event = RequestHistoryEvent.create(
                updated,
                RequestHistoryEventType.EXECUTION_STARTED,
                context.actorId(),
                RequestState.APPROVED,
                RequestState.EXECUTING,
                Map.of(),
                now);
        return auditedTransition(current, updated, expectedRevision, event, context, "BEGIN_EXECUTION");
    }

    public RequestRecord markExecuted(
            ApprovalRequestId requestId,
            long expectedRevision,
            RequestContext context) {
        return finishExecution(requestId, expectedRevision, context, true, null);
    }

    public RequestRecord markFailed(
            ApprovalRequestId requestId,
            long expectedRevision,
            RequestContext context,
            String reason) {
        return finishExecution(requestId, expectedRevision, context, false, reason);
    }

    public Optional<RequestRecord> findById(ApprovalRequestId requestId) {
        return repository.findById(Objects.requireNonNull(requestId, "requestId"));
    }

    public Optional<RequestPayload> activePayload(ApprovalRequestId requestId) {
        return repository.activePayload(Objects.requireNonNull(requestId, "requestId"));
    }

    public List<RequestApproval> approvals(ApprovalRequestId requestId, int limit) {
        return repository.approvals(requestId, boundedLimit(limit, RequestLimits.MAX_APPROVAL_RESULTS));
    }

    public List<RequestHistoryEvent> history(ApprovalRequestId requestId, int limit) {
        return repository.history(requestId, boundedLimit(limit, RequestLimits.MAX_HISTORY_RESULTS));
    }

    private RequestRecord decide(
            ApprovalRequestId requestId,
            long expectedRevision,
            ApprovalDecision decision,
            RequestContext context,
            String reason) {
        RequestRecord current = requireRequest(requestId);
        requireExpectedRevision(current, expectedRevision);
        requireState(decision.name(), current.state(), RequestState.PENDING);
        current = requireLive(current, expectedRevision, context);
        ActorId actor = context.actorId();
        if (!policy.selfApprovalAllowed(current, actor)) {
            auditDenied(context, current, "SELF_APPROVAL");
            throw new SelfApprovalDeniedException();
        }
        Instant now = clock.instant();
        RequestApproval approval = RequestApproval.create(current, actor, decision, reason, now);
        long approvedCount = repository.countApproved(
                current.requestId(), current.activePayloadRevision(), current.activePayloadSha256());
        RequestState nextState;
        Long approvedRevision = null;
        String approvedHash = null;
        RequestHistoryEventType eventType;
        if (decision == ApprovalDecision.DENY) {
            nextState = RequestState.DENIED;
            eventType = RequestHistoryEventType.DENIAL_RECORDED;
        } else {
            boolean thresholdReached = approvedCount + 1 >= current.requiredApprovals();
            nextState = thresholdReached ? RequestState.APPROVED : RequestState.PENDING;
            if (thresholdReached) {
                approvedRevision = current.activePayloadRevision();
                approvedHash = current.activePayloadSha256();
            }
            eventType = RequestHistoryEventType.APPROVAL_RECORDED;
        }
        RequestRecord updated = copy(
                current,
                nextState,
                current.activePayloadRevision(),
                current.activePayloadSha256(),
                approvedRevision,
                approvedHash,
                now,
                expectedRevision + 1);
        RequestHistoryEvent event = RequestHistoryEvent.create(
                updated,
                eventType,
                actor,
                current.state(),
                nextState,
                details(
                        "decision", decision.name(),
                        "activeApprovalCount", Long.toString(decision == ApprovalDecision.APPROVE ? approvedCount + 1 : approvedCount),
                        "reason", reason),
                now);
        auditBefore(context, current, AuditAction.APPROVAL, decision.name(), reason);
        RequestRecord persisted = repository.recordDecision(updated, expectedRevision, approval, event);
        auditSuccess(context, persisted, AuditAction.APPROVAL, decision.name());
        return persisted;
    }

    private RequestRecord finishExecution(
            ApprovalRequestId requestId,
            long expectedRevision,
            RequestContext context,
            boolean success,
            String reason) {
        RequestRecord current = requireRequest(requestId);
        requireExpectedRevision(current, expectedRevision);
        requireState(success ? "MARK_EXECUTED" : "MARK_FAILED", current.state(), RequestState.EXECUTING);
        Instant now = clock.instant();
        RequestState next = success ? RequestState.EXECUTED : RequestState.FAILED;
        RequestRecord updated = copy(
                current,
                next,
                current.activePayloadRevision(),
                current.activePayloadSha256(),
                current.approvedPayloadRevision(),
                current.approvedPayloadSha256(),
                now,
                expectedRevision + 1);
        RequestHistoryEvent event = RequestHistoryEvent.create(
                updated,
                success ? RequestHistoryEventType.EXECUTED : RequestHistoryEventType.FAILED,
                context.actorId(),
                RequestState.EXECUTING,
                next,
                details("reason", reason),
                now);
        return auditedTransition(current, updated, expectedRevision, event, context, success ? "EXECUTED" : "FAILED");
    }

    private RequestRecord requireLive(RequestRecord current, long expectedRevision, RequestContext context) {
        if (!current.isExpiredAt(clock.instant())) return current;
        expire(current, expectedRevision, context);
        throw new RequestExpiredException();
    }

    private RequestRecord expire(RequestRecord current, long expectedRevision, RequestContext context) {
        Instant now = clock.instant();
        RequestRecord updated = copy(
                current,
                RequestState.EXPIRED,
                current.activePayloadRevision(),
                current.activePayloadSha256(),
                null,
                null,
                now,
                expectedRevision + 1);
        RequestHistoryEvent event = RequestHistoryEvent.create(
                updated,
                RequestHistoryEventType.EXPIRED,
                context.actorId(),
                current.state(),
                RequestState.EXPIRED,
                Map.of(),
                now);
        return auditedTransition(current, updated, expectedRevision, event, context, "EXPIRE");
    }

    private RequestRecord auditedTransition(
            RequestRecord before,
            RequestRecord updated,
            long expectedRevision,
            RequestHistoryEvent event,
            RequestContext context,
            String operation) {
        auditBefore(context, before, AuditAction.WRITE_REQUEST, operation, event.details().get("reason"));
        RequestRecord persisted = repository.transition(updated, expectedRevision, event);
        auditSuccess(context, persisted, AuditAction.WRITE_REQUEST, operation);
        return persisted;
    }

    private RequestRecord requireRequest(ApprovalRequestId requestId) {
        Objects.requireNonNull(requestId, "requestId");
        return repository.findById(requestId).orElseThrow(() -> new RequestNotFoundException(requestId));
    }

    private static void requireExpectedRevision(RequestRecord request, long expectedRevision) {
        if (expectedRevision < 1) throw new IllegalArgumentException("expectedRevision must be >= 1");
        if (request.revision() != expectedRevision) {
            throw new de.legitserver.assistant.requests.repository.RequestRevisionConflictException(
                    request.requestId(), expectedRevision);
        }
    }

    private static void requireState(String operation, RequestState state, RequestState... allowed) {
        for (RequestState candidate : allowed) {
            if (state == candidate) return;
        }
        throw new IllegalRequestStateException(operation, state);
    }

    private static RequestRecord copy(
            RequestRecord current,
            RequestState state,
            long activePayloadRevision,
            String activePayloadSha256,
            Long approvedPayloadRevision,
            String approvedPayloadSha256,
            Instant updatedAt,
            long revision) {
        return new RequestRecord(
                current.requestId(),
                current.requestType(),
                current.requesterActorId(),
                state,
                current.riskLevel(),
                current.requiredApprovals(),
                activePayloadRevision,
                activePayloadSha256,
                approvedPayloadRevision,
                approvedPayloadSha256,
                current.expiresAt(),
                current.classification(),
                current.createdAt(),
                updatedAt,
                revision);
    }

    private void auditBefore(
            RequestContext context,
            RequestRecord request,
            AuditAction action,
            String operation,
            String reason) {
        auditRecorder.append(auditRecord(
                context,
                request,
                action,
                AuditOutcome.REQUESTED,
                operation,
                reason,
                request.riskLevel().isHighRisk(),
                clock.instant()));
    }

    private void auditSuccess(
            RequestContext context,
            RequestRecord request,
            AuditAction action,
            String operation) {
        auditRecorder.append(auditRecord(
                context,
                request,
                action,
                AuditOutcome.SUCCESS,
                operation,
                null,
                false,
                clock.instant()));
    }

    private void auditDenied(RequestContext context, RequestRecord request, String operation) {
        auditRecorder.append(auditRecord(
                context,
                request,
                AuditAction.APPROVAL,
                AuditOutcome.DENIED,
                operation,
                null,
                false,
                clock.instant()));
    }

    private static AuditRecord auditRecord(
            RequestContext context,
            RequestRecord request,
            AuditAction action,
            AuditOutcome outcome,
            String operation,
            String reason,
            boolean critical,
            Instant occurredAt) {
        return new AuditRecord(
                action,
                outcome,
                context.requestId(),
                context.traceId(),
                context.actorId(),
                null,
                context.assistantId(),
                "request:" + request.requestId(),
                context.serverId(),
                "request",
                request.requestId().toString(),
                occurredAt,
                details(
                        "operation", operation,
                        "riskLevel", request.riskLevel().name(),
                        "state", request.state().name(),
                        "payloadRevision", Long.toString(request.activePayloadRevision()),
                        "payloadSha256", request.activePayloadSha256(),
                        "reason", reason),
                critical);
    }

    private static Map<String, String> details(String... pairs) {
        java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (pairs[i + 1] != null) result.put(pairs[i], pairs[i + 1]);
        }
        return Map.copyOf(result);
    }

    private static int boundedLimit(int limit, int max) {
        if (limit < 1 || limit > max) throw new IllegalArgumentException("limit must be between 1 and " + max);
        return limit;
    }
}
