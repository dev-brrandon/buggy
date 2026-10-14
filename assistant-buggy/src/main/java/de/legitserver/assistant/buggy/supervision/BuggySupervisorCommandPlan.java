package de.legitserver.assistant.buggy.supervision;

import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.Objects;

/** Non-executing plan. Execution remains in the existing B5 Tool Runtime/Foundation pipeline. */
public record BuggySupervisorCommandPlan(
        AssistantId targetAssistant,
        BuggySupervisorOperation operation,
        BuggySupervisorAuthorityMode authorityMode,
        String toolId,
        String reason,
        boolean requiresFoundationAuthorization,
        boolean changesTargetAuthority) {
    public BuggySupervisorCommandPlan {
        BuggySupervisorContractCatalog.requireFutureTarget(targetAssistant);
        operation = Objects.requireNonNull(operation, "operation");
        authorityMode = Objects.requireNonNull(authorityMode, "authorityMode");
        if (operation.isForbidden()) throw new IllegalArgumentException("forbidden supervisor operation");
        if (authorityMode != operation.authorityMode()) throw new IllegalArgumentException("authority mode mismatch");
        toolId = toolId == null ? null : CoreValidation.requireSafeToken(toolId, "toolId", 128);
        reason = CoreValidation.requireText(reason, "reason", 2048);
        if (!requiresFoundationAuthorization) throw new IllegalArgumentException("supervisor action must remain Foundation-authorized");
        if (changesTargetAuthority) throw new IllegalArgumentException("Buggy may not change target authority");
        if (!Objects.equals(toolId, operation.toolId())) throw new IllegalArgumentException("tool binding mismatch");
    }
}
