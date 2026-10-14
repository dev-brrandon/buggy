package de.legitserver.assistant.buggy.supervision;

import de.legitserver.assistant.core.assistant.AssistantId;
import java.util.Objects;

/** Handoff contract that explicitly preserves target authority and the right to reject/disagree. */
public record BuggyDelegationEnvelope(
        AssistantId sourceAssistant,
        BuggyDelegationIntent intent,
        boolean targetMustReauthorize,
        boolean targetMayReject,
        boolean targetMayDisagree,
        boolean sourceMayOverrideTargetPolicy) {
    public BuggyDelegationEnvelope {
        if (sourceAssistant != AssistantId.BUGGY) throw new IllegalArgumentException("delegation source must be BUGGY");
        intent = Objects.requireNonNull(intent, "intent");
        if (!targetMustReauthorize || !targetMayReject || !targetMayDisagree || sourceMayOverrideTargetPolicy) {
            throw new IllegalArgumentException("delegation independence boundary cannot be weakened");
        }
    }
}
