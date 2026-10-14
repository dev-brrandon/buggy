package de.legitserver.assistant.integrations.buggy;

import de.legitserver.assistant.buggy.identity.BuggyIdentityCore;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentApprovalBinding;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentApprovalPort;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentApprovalStatus;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentId;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentPromotionPackage;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.core.request.RequestContext;
import de.legitserver.assistant.requests.model.RequestDraft;
import de.legitserver.assistant.requests.model.RequestRecord;
import de.legitserver.assistant.requests.model.RequestRiskLevel;
import de.legitserver.assistant.requests.model.RequestState;
import de.legitserver.assistant.requests.service.RequestService;
import de.legitserver.assistant.runtime.model.ToolRiskLevel;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** B7 adapter that binds Self-Development promotion/rollback to the existing Foundation Request lifecycle. */
public final class FoundationSelfDevelopmentApprovalGateway implements BuggySelfDevelopmentApprovalPort {
    private static final ActorId BUGGY_SERVICE_ACTOR = new ActorId(BuggyIdentityCore.SERVICE_IDENTITY);
    private static final String CONTENT_TYPE = "application/vnd.legit.buggy-selfdev.v1+text";
    private final RequestService requests;
    private final Clock clock;

    public FoundationSelfDevelopmentApprovalGateway(RequestService requests, Clock clock) {
        this.requests = Objects.requireNonNull(requests, "requests");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public BuggySelfDevelopmentApprovalBinding submitPromotion(
            BuggySelfDevelopmentPromotionPackage promotionPackage,
            RequestContext context) {
        Objects.requireNonNull(promotionPackage, "promotionPackage");
        RequestRecord request = requests.create(new RequestDraft(
                PROMOTION_REQUEST_TYPE,
                toRequestRisk(promotionPackage.riskLevel()),
                1,
                CONTENT_TYPE,
                promotionPackage.canonicalBody(),
                expiryFor(promotionPackage.riskLevel()),
                DataClassification.CONFIDENTIAL), serviceContext(context));
        return new BuggySelfDevelopmentApprovalBinding(
                request.requestId(), promotionPackage.submittedRevision(), request.requestType(),
                request.activePayloadSha256(), promotionPackage.riskLevel());
    }

    @Override
    public BuggySelfDevelopmentApprovalBinding submitRollback(
            BuggySelfDevelopmentId itemId,
            long submittedRevision,
            String deploymentReference,
            ToolRiskLevel riskLevel,
            RequestContext context) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(riskLevel, "riskLevel");
        String body = String.join("\n",
                "itemId=" + itemId,
                "submittedRevision=" + submittedRevision,
                "deploymentReference=" + deploymentReference,
                "risk=" + riskLevel.name());
        RequestRecord request = requests.create(new RequestDraft(
                ROLLBACK_REQUEST_TYPE,
                toRequestRisk(riskLevel),
                1,
                CONTENT_TYPE,
                body,
                expiryFor(riskLevel),
                DataClassification.CONFIDENTIAL), serviceContext(context));
        return new BuggySelfDevelopmentApprovalBinding(
                request.requestId(), submittedRevision, request.requestType(), request.activePayloadSha256(), riskLevel);
    }

    @Override
    public BuggySelfDevelopmentApprovalStatus status(BuggySelfDevelopmentApprovalBinding binding) {
        Objects.requireNonNull(binding, "binding");
        RequestRecord request = requests.findById(binding.requestId()).orElse(null);
        if (request == null) return BuggySelfDevelopmentApprovalStatus.TERMINAL_FAILURE;
        if (!request.requestType().equals(binding.requestType())
                || !request.activePayloadSha256().equals(binding.payloadSha256())
                || request.riskLevel() != toRequestRisk(binding.riskLevel())
                || !request.requesterActorId().equals(BUGGY_SERVICE_ACTOR)) {
            return BuggySelfDevelopmentApprovalStatus.TERMINAL_FAILURE;
        }
        if (request.state() == RequestState.PENDING) return BuggySelfDevelopmentApprovalStatus.PENDING;
        if (request.state() == RequestState.APPROVED || request.state() == RequestState.EXECUTING) {
            return exactApprovedPayload(request, binding)
                    ? BuggySelfDevelopmentApprovalStatus.APPROVED
                    : BuggySelfDevelopmentApprovalStatus.TERMINAL_FAILURE;
        }
        if (request.state() == RequestState.EXECUTED) {
            return exactApprovedPayload(request, binding)
                    ? BuggySelfDevelopmentApprovalStatus.EXECUTED
                    : BuggySelfDevelopmentApprovalStatus.TERMINAL_FAILURE;
        }
        return BuggySelfDevelopmentApprovalStatus.TERMINAL_FAILURE;
    }

    private static boolean exactApprovedPayload(RequestRecord request, BuggySelfDevelopmentApprovalBinding binding) {
        return request.approvedPayloadRevision() != null
                && request.approvedPayloadRevision().longValue() == request.activePayloadRevision()
                && binding.payloadSha256().equals(request.approvedPayloadSha256());
    }

    private RequestContext serviceContext(RequestContext original) {
        Objects.requireNonNull(original, "context");
        if (original.assistantId() != AssistantId.BUGGY) {
            throw new IllegalArgumentException("Self-Development Request context must target BUGGY");
        }
        return new RequestContext(
                original.requestId(),
                original.traceId(),
                BUGGY_SERVICE_ACTOR,
                original.origin(),
                AssistantId.BUGGY,
                original.conversationId(),
                original.serverId(),
                original.receivedAt());
    }

    private Instant expiryFor(ToolRiskLevel riskLevel) {
        return riskLevel == ToolRiskLevel.CRITICAL ? clock.instant().plus(Duration.ofHours(24)) : null;
    }

    private static RequestRiskLevel toRequestRisk(ToolRiskLevel riskLevel) {
        return RequestRiskLevel.valueOf(Objects.requireNonNull(riskLevel, "riskLevel").name());
    }
}
