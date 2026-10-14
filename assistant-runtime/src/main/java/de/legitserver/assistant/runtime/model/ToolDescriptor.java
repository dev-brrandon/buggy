package de.legitserver.assistant.runtime.model;

import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.runtime.schema.ToolSchema;
import java.time.Duration;
import java.util.Objects;

/** Canonical Phase-10 controlled tool descriptor. */
public record ToolDescriptor(
        ToolId toolId,
        AssistantCapabilityId capability,
        ToolRiskLevel riskLevel,
        ToolSchema inputSchema,
        ToolSchema outputSchema,
        Duration timeout,
        int maxConcurrency,
        ToolAuditPolicy auditPolicy,
        ToolClassificationPolicy classificationPolicy,
        boolean selfHealingEligible) {
    public ToolDescriptor {
        toolId = Objects.requireNonNull(toolId, "toolId");
        capability = Objects.requireNonNull(capability, "capability");
        riskLevel = Objects.requireNonNull(riskLevel, "riskLevel");
        inputSchema = Objects.requireNonNull(inputSchema, "inputSchema");
        outputSchema = Objects.requireNonNull(outputSchema, "outputSchema");
        timeout = Objects.requireNonNull(timeout, "timeout");
        auditPolicy = Objects.requireNonNull(auditPolicy, "auditPolicy");
        classificationPolicy = Objects.requireNonNull(classificationPolicy, "classificationPolicy");
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofMinutes(2)) > 0) {
            throw new IllegalArgumentException("timeout must be > 0 and <= 2 minutes");
        }
        if (maxConcurrency < 1 || maxConcurrency > 64) throw new IllegalArgumentException("maxConcurrency out of range");
        if (riskLevel.isHighRisk() && auditPolicy != ToolAuditPolicy.CRITICAL_REQUIRED) {
            throw new IllegalArgumentException("HIGH/CRITICAL tools require fail-closed audit policy");
        }
    }
}
