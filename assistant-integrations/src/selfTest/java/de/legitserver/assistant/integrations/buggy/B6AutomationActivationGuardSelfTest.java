package de.legitserver.assistant.integrations.buggy;

import de.legitserver.assistant.buggy.activity.BuggyAttentionLevel;
import de.legitserver.assistant.buggy.automation.BuggyAutomationLevel;
import de.legitserver.assistant.buggy.automation.BuggyAutomationRevision;
import de.legitserver.assistant.buggy.automation.BuggyAutomationState;
import de.legitserver.assistant.buggy.automation.BuggyAutomationTriggerType;
import de.legitserver.assistant.buggy.automation.BuggySignalType;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.requests.model.ApprovalRequestId;
import de.legitserver.assistant.requests.model.RequestApproval;
import de.legitserver.assistant.requests.model.RequestHistoryEvent;
import de.legitserver.assistant.requests.model.RequestPayload;
import de.legitserver.assistant.requests.model.RequestRecord;
import de.legitserver.assistant.requests.model.RequestRiskLevel;
import de.legitserver.assistant.requests.model.RequestState;
import de.legitserver.assistant.requests.repository.RequestRepository;
import de.legitserver.assistant.runtime.model.ToolId;
import de.legitserver.assistant.runtime.model.ToolRiskLevel;
import de.legitserver.assistant.security.authorization.buggy.BuggySecurityCapabilities;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

public final class B6AutomationActivationGuardSelfTest {
    private static final Instant NOW = Instant.parse("2026-10-04T23:00:00Z");
    private static final ActorId OWNER = new ActorId("actor:buggy-b6-owner");
    private B6AutomationActivationGuardSelfTest() {}

    public static void main(String[] args) {
        ApprovalRequestId id = ApprovalRequestId.random();
        String hash = "a".repeat(64);
        RequestRecord approved = new RequestRecord(
                id,
                FoundationRequestAutomationActivationGuard.REQUEST_TYPE,
                OWNER,
                RequestState.APPROVED,
                RequestRiskLevel.HIGH,
                1,
                1,
                hash,
                1L,
                hash,
                NOW.plus(Duration.ofHours(1)),
                DataClassification.INTERNAL,
                NOW.minusSeconds(10),
                NOW,
                2);
        FoundationRequestAutomationActivationGuard guard = new FoundationRequestAutomationActivationGuard(
                new SingleRequestRepository(approved), Clock.fixed(NOW, ZoneOffset.UTC));
        require(guard.approved(highAutomation(id)), "approved exact activation request must pass");

        RequestRecord wrongType = new RequestRecord(
                id, "OTHER_REQUEST", OWNER, RequestState.APPROVED, RequestRiskLevel.HIGH, 1, 1, hash, 1L, hash,
                NOW.plusSeconds(60), DataClassification.INTERNAL, NOW.minusSeconds(10), NOW, 2);
        require(!new FoundationRequestAutomationActivationGuard(new SingleRequestRepository(wrongType), Clock.fixed(NOW, ZoneOffset.UTC))
                .approved(highAutomation(id)), "wrong request type must fail closed");
        System.out.println("B6_AUTOMATION_ACTIVATION_GUARD_SELF_TEST: PASS");
    }

    private static BuggyAutomationRevision highAutomation(ApprovalRequestId requestId) {
        return new BuggyAutomationRevision(
                "auto:source-analysis", 1, null, "Source analysis", BuggyAutomationState.ENABLED,
                BuggyAutomationTriggerType.SIGNAL, BuggySignalType.RECURRING_BUG, null,
                BuggyAutomationLevel.PREPARE, BuggyAttentionLevel.NOTICE,
                new ToolId("tool.source.read"), BuggySecurityCapabilities.SOURCE_READ, ToolRiskLevel.HIGH,
                3, Duration.ofSeconds(30), Duration.ofMinutes(10), 10, Duration.ofMinutes(2), 3,
                requestId, OWNER, NOW);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static final class SingleRequestRepository implements RequestRepository {
        private final RequestRecord record;
        private SingleRequestRepository(RequestRecord record) { this.record = record; }
        @Override public void create(RequestRecord request, RequestPayload payload, RequestHistoryEvent event) { throw unsupported(); }
        @Override public Optional<RequestRecord> findById(ApprovalRequestId requestId) { return record.requestId().equals(requestId) ? Optional.of(record) : Optional.empty(); }
        @Override public Optional<RequestPayload> activePayload(ApprovalRequestId requestId) { return Optional.empty(); }
        @Override public long countApproved(ApprovalRequestId requestId, long payloadRevision, String payloadSha256) { return 0; }
        @Override public RequestRecord revisePayload(RequestRecord updated, long expectedRevision, RequestPayload payload, RequestHistoryEvent event) { throw unsupported(); }
        @Override public RequestRecord recordDecision(RequestRecord updated, long expectedRevision, RequestApproval approval, RequestHistoryEvent event) { throw unsupported(); }
        @Override public RequestRecord transition(RequestRecord updated, long expectedRevision, RequestHistoryEvent event) { throw unsupported(); }
        @Override public List<RequestApproval> approvals(ApprovalRequestId requestId, int limit) { return List.of(); }
        @Override public List<RequestHistoryEvent> history(ApprovalRequestId requestId, int limit) { return List.of(); }
        private static UnsupportedOperationException unsupported() { return new UnsupportedOperationException("not needed by test"); }
    }
}
