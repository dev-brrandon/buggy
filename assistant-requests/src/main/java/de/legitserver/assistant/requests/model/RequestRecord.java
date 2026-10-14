package de.legitserver.assistant.requests.model;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.requests.policy.RequestLimits;
import de.legitserver.assistant.requests.validation.RequestValidation;
import java.time.Instant;
import java.util.Objects;

/** Mutable request head. All payloads, approvals, and history entries remain append-only children. */
public record RequestRecord(
        ApprovalRequestId requestId,
        String requestType,
        ActorId requesterActorId,
        RequestState state,
        RequestRiskLevel riskLevel,
        int requiredApprovals,
        long activePayloadRevision,
        String activePayloadSha256,
        Long approvedPayloadRevision,
        String approvedPayloadSha256,
        Instant expiresAt,
        DataClassification classification,
        Instant createdAt,
        Instant updatedAt,
        long revision) {

    public RequestRecord {
        requestId = Objects.requireNonNull(requestId, "requestId");
        requestType = RequestValidation.requestType(requestType);
        requesterActorId = Objects.requireNonNull(requesterActorId, "requesterActorId");
        state = Objects.requireNonNull(state, "state");
        riskLevel = Objects.requireNonNull(riskLevel, "riskLevel");
        if (requiredApprovals < 1 || requiredApprovals > RequestLimits.MAX_REQUIRED_APPROVALS) {
            throw new IllegalArgumentException("requiredApprovals out of range");
        }
        if (activePayloadRevision < 1) throw new IllegalArgumentException("activePayloadRevision must be >= 1");
        activePayloadSha256 = RequestValidation.sha256(activePayloadSha256);
        boolean approvalRevisionPresent = approvedPayloadRevision != null;
        boolean approvalHashPresent = approvedPayloadSha256 != null;
        if (approvalRevisionPresent != approvalHashPresent) {
            throw new IllegalArgumentException("approved payload revision/hash must either both be present or both absent");
        }
        if (approvalHashPresent) {
            if (approvedPayloadRevision.longValue() != activePayloadRevision) {
                throw new IllegalArgumentException("approved payload revision must match active payload revision");
            }
            approvedPayloadSha256 = RequestValidation.sha256(approvedPayloadSha256);
            if (!approvedPayloadSha256.equals(activePayloadSha256)) {
                throw new IllegalArgumentException("approved payload hash must match active payload hash");
            }
        }
        boolean approvalBindingRequired = switch (state) {
            case APPROVED, EXECUTING, EXECUTED, FAILED -> true;
            case PENDING, DENIED, EXPIRED, CANCELLED -> false;
        };
        if (approvalBindingRequired != approvalHashPresent) {
            throw new IllegalArgumentException("state and approved payload binding are inconsistent");
        }
        classification = Objects.requireNonNull(classification, "classification");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt precedes createdAt");
        if (expiresAt != null && !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
        if (riskLevel.isCritical() && expiresAt == null) {
            throw new IllegalArgumentException("CRITICAL request requires expiresAt");
        }
        if (revision < 1) throw new IllegalArgumentException("revision must be >= 1");
    }

    public boolean isExpiredAt(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return expiresAt != null && !instant.isBefore(expiresAt);
    }
}
