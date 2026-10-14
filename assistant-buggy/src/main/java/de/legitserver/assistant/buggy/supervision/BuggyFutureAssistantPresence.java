package de.legitserver.assistant.buggy.supervision;

import de.legitserver.assistant.core.assistant.AssistantId;
import java.time.Instant;
import java.util.Objects;

/** Read-only availability projection supplied by a future assistant registry/runtime adapter. */
public record BuggyFutureAssistantPresence(
        AssistantId assistantId,
        BuggyFutureAssistantState state,
        long runtimeRevision,
        Instant observedAt) {
    public BuggyFutureAssistantPresence {
        assistantId = Objects.requireNonNull(assistantId, "assistantId");
        state = Objects.requireNonNull(state, "state");
        if (assistantId == AssistantId.BUGGY) {
            throw new IllegalArgumentException("future assistant presence cannot target BUGGY");
        }
        if (runtimeRevision < 0) throw new IllegalArgumentException("runtimeRevision must be >= 0");
        observedAt = Objects.requireNonNull(observedAt, "observedAt");
        if (state == BuggyFutureAssistantState.NOT_STARTED && runtimeRevision != 0) {
            throw new IllegalArgumentException("NOT_STARTED assistant must have runtimeRevision 0");
        }
    }

    public boolean supervisorReadable() {
        return state != BuggyFutureAssistantState.NOT_STARTED && state != BuggyFutureAssistantState.UNAVAILABLE;
    }

    public boolean delegationAvailable() {
        return state == BuggyFutureAssistantState.AVAILABLE || state == BuggyFutureAssistantState.DEGRADED;
    }
}
