package de.legitserver.assistant.requests.policy;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.requests.model.RequestDraft;
import de.legitserver.assistant.requests.model.RequestRecord;
import java.time.Instant;
import java.util.Objects;

/** Central Phase-9 approval policy, independent of transports and persistence. */
public final class RequestApprovalPolicy {
    public void validateCreation(RequestDraft draft, Instant now) {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(now, "now");
        if (draft.riskLevel().isCritical() && draft.expiresAt() == null) {
            throw new IllegalArgumentException("CRITICAL request requires expiry");
        }
        if (draft.expiresAt() != null && !draft.expiresAt().isAfter(now)) {
            throw new IllegalArgumentException("expiresAt must be in the future");
        }
    }

    public boolean selfApprovalAllowed(RequestRecord request, ActorId approver) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(approver, "approver");
        return !request.riskLevel().isHighRisk() || !request.requesterActorId().equals(approver);
    }
}
