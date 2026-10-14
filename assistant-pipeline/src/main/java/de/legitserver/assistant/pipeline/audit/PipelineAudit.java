package de.legitserver.assistant.pipeline.audit;

import de.legitserver.assistant.observability.audit.AuditAction;
import de.legitserver.assistant.observability.audit.AuditAppendResult;
import de.legitserver.assistant.observability.audit.AuditOutcome;
import de.legitserver.assistant.observability.audit.AuditRecord;
import de.legitserver.assistant.observability.audit.AuditRecorder;
import de.legitserver.assistant.pipeline.model.PipelineRequest;
import de.legitserver.assistant.pipeline.model.PipelineResponse;
import java.time.Clock;
import java.util.Map;

/** Final request-level audit. Security-critical suboperations keep their own fail-closed audit gates. */
public final class PipelineAudit {
    private final AuditRecorder audit;
    private final Clock clock;

    public PipelineAudit(AuditRecorder audit, Clock clock) {
        if (audit == null || clock == null) throw new IllegalArgumentException("audit dependencies must not be null");
        this.audit = audit;
        this.clock = clock;
    }

    public AuditAppendResult record(PipelineRequest request, PipelineResponse response) {
        return audit.append(new AuditRecord(
                AuditAction.REQUEST_PIPELINE,
                response.successful() ? AuditOutcome.SUCCESS : AuditOutcome.FAILED,
                request.context().requestId(),
                request.context().traceId(),
                request.presentedActor().actorId(),
                request.presentedActor().minecraftUuid(),
                request.assistant().assistantId(),
                request.operation().value(),
                request.context().serverId(),
                "pipeline-operation",
                request.operation().value(),
                clock.instant(),
                Map.of(
                        "status", response.status().name(),
                        "safeCode", response.safeCode(),
                        "idempotentReplay", Boolean.toString(response.idempotentReplay())),
                false));
    }
}
