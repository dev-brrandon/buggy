package de.legitserver.assistant.requests.repository;

import de.legitserver.assistant.requests.model.ApprovalRequestId;
import de.legitserver.assistant.requests.model.RequestApproval;
import de.legitserver.assistant.requests.model.RequestHistoryEvent;
import de.legitserver.assistant.requests.model.RequestPayload;
import de.legitserver.assistant.requests.model.RequestRecord;
import java.util.List;
import java.util.Optional;

/** Persistence port with transactional aggregate operations and no delete contract. */
public interface RequestRepository {
    void create(RequestRecord request, RequestPayload payload, RequestHistoryEvent createdEvent);

    Optional<RequestRecord> findById(ApprovalRequestId requestId);

    Optional<RequestPayload> activePayload(ApprovalRequestId requestId);

    long countApproved(ApprovalRequestId requestId, long payloadRevision, String payloadSha256);

    RequestRecord revisePayload(
            RequestRecord updated,
            long expectedRevision,
            RequestPayload payload,
            RequestHistoryEvent event);

    RequestRecord recordDecision(
            RequestRecord updated,
            long expectedRevision,
            RequestApproval approval,
            RequestHistoryEvent event);

    RequestRecord transition(RequestRecord updated, long expectedRevision, RequestHistoryEvent event);

    List<RequestApproval> approvals(ApprovalRequestId requestId, int limit);

    List<RequestHistoryEvent> history(ApprovalRequestId requestId, int limit);
}
