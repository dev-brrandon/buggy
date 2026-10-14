package de.legitserver.assistant.requests.repository;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.requests.model.ApprovalRequestId;

/** One actor may decide only once for one exact payload revision. */
public final class DuplicateApprovalException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DuplicateApprovalException(ApprovalRequestId requestId, long payloadRevision, ActorId actorId) {
        super("Duplicate approval decision: " + requestId + " payloadRevision=" + payloadRevision + " actor=" + actorId);
    }
}
