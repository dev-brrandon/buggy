package de.legitserver.assistant.buggy.automation;

import de.legitserver.assistant.buggy.activity.BuggyAttentionLevel;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.core.validation.CoreValidation;
import de.legitserver.assistant.requests.model.ApprovalRequestId;
import de.legitserver.assistant.runtime.model.ToolId;
import de.legitserver.assistant.runtime.model.ToolRiskLevel;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Append-only durable automation policy revision. */
public record BuggyAutomationRevision(
        String automationId,
        long revision,
        Long previousRevision,
        String name,
        BuggyAutomationState state,
        BuggyAutomationTriggerType triggerType,
        BuggySignalType signalType,
        Duration scheduleInterval,
        BuggyAutomationLevel maxLevel,
        BuggyAttentionLevel attention,
        ToolId actionToolId,
        AssistantCapabilityId capabilityId,
        ToolRiskLevel riskLevel,
        int maxAttemptsPerTarget,
        Duration cooldown,
        Duration window,
        int maxActionsPerWindow,
        Duration sameTargetSuppression,
        int failureEscalationThreshold,
        ApprovalRequestId activationRequestId,
        ActorId changedBy,
        Instant changedAt) {
    public BuggyAutomationRevision {
        automationId = CoreValidation.requireSafeToken(automationId, "automationId", 128);
        if (revision < 1) throw new IllegalArgumentException("revision must be >= 1");
        if ((revision == 1) != (previousRevision == null)) throw new IllegalArgumentException("previousRevision inconsistent");
        if (previousRevision != null && previousRevision.longValue() != revision - 1) throw new IllegalArgumentException("previousRevision must be predecessor");
        name = CoreValidation.requireText(name, "name", 256);
        state = Objects.requireNonNull(state, "state");
        triggerType = Objects.requireNonNull(triggerType, "triggerType");
        maxLevel = Objects.requireNonNull(maxLevel, "maxLevel");
        attention = Objects.requireNonNull(attention, "attention");
        actionToolId = Objects.requireNonNull(actionToolId, "actionToolId");
        capabilityId = Objects.requireNonNull(capabilityId, "capabilityId");
        riskLevel = Objects.requireNonNull(riskLevel, "riskLevel");
        if (triggerType == BuggyAutomationTriggerType.SIGNAL && signalType == null) throw new IllegalArgumentException("SIGNAL trigger requires signalType");
        if (triggerType == BuggyAutomationTriggerType.SCHEDULE && signalType != null) throw new IllegalArgumentException("SCHEDULE trigger cannot declare signalType");
        if (triggerType == BuggyAutomationTriggerType.SCHEDULE) requirePositive(scheduleInterval, "scheduleInterval");
        if (triggerType == BuggyAutomationTriggerType.SIGNAL && scheduleInterval != null) throw new IllegalArgumentException("SIGNAL trigger cannot declare scheduleInterval");
        if (maxAttemptsPerTarget < 1 || maxAttemptsPerTarget > 3) throw new IllegalArgumentException("maxAttemptsPerTarget must be 1..3");
        if (maxActionsPerWindow < 1 || maxActionsPerWindow > 100) throw new IllegalArgumentException("maxActionsPerWindow out of range");
        if (failureEscalationThreshold < 1 || failureEscalationThreshold > maxAttemptsPerTarget) throw new IllegalArgumentException("failureEscalationThreshold out of range");
        requirePositive(cooldown, "cooldown"); requirePositive(window, "window"); requirePositive(sameTargetSuppression, "sameTargetSuppression");
        if (cooldown.compareTo(window) > 0 || sameTargetSuppression.compareTo(window) > 0) throw new IllegalArgumentException("cooldown/suppression exceeds window");
        if (maxLevel == BuggyAutomationLevel.ACT && riskLevel != ToolRiskLevel.LOW) throw new IllegalArgumentException("ACT automation must be LOW risk");
        if (state == BuggyAutomationState.ENABLED && riskLevel != ToolRiskLevel.LOW && activationRequestId == null) {
            throw new IllegalArgumentException("privileged automation activation requires approval request binding");
        }
        changedBy = Objects.requireNonNull(changedBy, "changedBy");
        changedAt = Objects.requireNonNull(changedAt, "changedAt");
    }
    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
    }
}
