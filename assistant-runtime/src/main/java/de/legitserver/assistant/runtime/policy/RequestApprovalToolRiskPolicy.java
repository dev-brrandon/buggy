package de.legitserver.assistant.runtime.policy;

import de.legitserver.assistant.requests.model.RequestPayload;
import de.legitserver.assistant.requests.model.RequestRecord;
import de.legitserver.assistant.requests.model.RequestState;
import de.legitserver.assistant.requests.repository.RequestRepository;
import de.legitserver.assistant.runtime.execution.ToolFailureCode;
import de.legitserver.assistant.runtime.execution.ToolRuntimeException;
import de.legitserver.assistant.runtime.model.ToolDescriptor;
import de.legitserver.assistant.runtime.model.ToolInvocation;
import java.time.Instant;
import java.util.Objects;

/** HIGH/CRITICAL tools require an APPROVED Phase-9 request bound to this exact canonical invocation. */
public final class RequestApprovalToolRiskPolicy implements ToolRiskPolicy {
    public static final String REQUEST_TYPE = "TOOL_EXECUTION";
    public static final String CONTENT_TYPE = "application/vnd.legit.tool-invocation+json;v=1";

    private final RequestRepository repository;

    public RequestApprovalToolRiskPolicy(RequestRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public void authorize(ToolDescriptor descriptor, ToolInvocation invocation, Instant now) {
        if (!descriptor.riskLevel().isHighRisk()) return;
        if (invocation.approvalRequestId() == null) throw new ToolRuntimeException(ToolFailureCode.APPROVAL_REQUIRED);
        RequestRecord request = repository.findById(invocation.approvalRequestId())
                .orElseThrow(() -> new ToolRuntimeException(ToolFailureCode.APPROVAL_INVALID));
        if (!REQUEST_TYPE.equals(request.requestType()) || request.state() != RequestState.APPROVED) {
            throw new ToolRuntimeException(ToolFailureCode.APPROVAL_INVALID);
        }
        if (request.isExpiredAt(now)) throw new ToolRuntimeException(ToolFailureCode.APPROVAL_EXPIRED);
        if (!descriptor.riskLevel().coveredBy(request.riskLevel())) {
            throw new ToolRuntimeException(ToolFailureCode.APPROVAL_INVALID);
        }
        if (request.approvedPayloadRevision() == null
                || request.approvedPayloadRevision().longValue() != request.activePayloadRevision()
                || request.approvedPayloadSha256() == null
                || !request.approvedPayloadSha256().equals(request.activePayloadSha256())) {
            throw new ToolRuntimeException(ToolFailureCode.APPROVAL_INVALID);
        }
        RequestPayload payload = repository.activePayload(request.requestId())
                .orElseThrow(() -> new ToolRuntimeException(ToolFailureCode.APPROVAL_INVALID));
        if (payload.payloadRevision() != request.activePayloadRevision()
                || !payload.sha256().equals(request.activePayloadSha256())
                || !CONTENT_TYPE.equals(payload.contentType())
                || !payload.body().equals(ToolInvocationCanonicalizer.canonicalPayload(invocation))) {
            throw new ToolRuntimeException(ToolFailureCode.APPROVAL_INVALID);
        }
    }
}
