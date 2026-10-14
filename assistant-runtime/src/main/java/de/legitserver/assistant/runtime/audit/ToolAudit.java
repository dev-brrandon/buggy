package de.legitserver.assistant.runtime.audit;

import de.legitserver.assistant.observability.audit.AuditAction;
import de.legitserver.assistant.observability.audit.AuditOutcome;
import de.legitserver.assistant.observability.audit.AuditRecord;
import de.legitserver.assistant.observability.audit.AuditRecorder;
import de.legitserver.assistant.runtime.model.ToolDescriptor;
import de.legitserver.assistant.runtime.model.ToolInvocation;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Canonical Tool Runtime audit facade. */
public final class ToolAudit {
    private final AuditRecorder recorder;

    public ToolAudit(AuditRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    public void record(ToolDescriptor descriptor, ToolInvocation invocation, AuditOutcome outcome, String stage, Instant at) {
        boolean critical = descriptor.auditPolicy().failClosed();
        recorder.append(new AuditRecord(
                AuditAction.TOOL_INVOCATION,
                outcome,
                invocation.actor().requestId(),
                invocation.actor().traceId(),
                invocation.actor().actorId(),
                invocation.actor().minecraftUuid(),
                invocation.assistant().assistantId(),
                descriptor.toolId().value(),
                invocation.actor().serverId(),
                "TOOL",
                descriptor.toolId().value(),
                at,
                Map.of(
                        "stage", stage,
                        "capability", descriptor.capability().value(),
                        "risk", descriptor.riskLevel().name(),
                        "resourceType", invocation.resource().resourceType(),
                        "resourceId", invocation.resource().resourceId()),
                critical));
    }
}
