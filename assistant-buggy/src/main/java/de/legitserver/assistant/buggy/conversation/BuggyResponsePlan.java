package de.legitserver.assistant.buggy.conversation;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.List;

/** Semantic response plan. It contains no final prose and cannot execute tools or mutate state. */
public record BuggyResponsePlan(
        BuggyConversationIntent intent,
        List<BuggyResponseAct> acts,
        boolean requiresExternalKnowledge,
        boolean requestsAction) {

    public BuggyResponsePlan {
        intent = CoreValidation.requireNonNull(intent, "intent");
        acts = List.copyOf(CoreValidation.requireNonNull(acts, "acts"));
        if (acts.isEmpty() || acts.size() > 4) throw new IllegalArgumentException("acts must contain 1..4 entries");
    }
}
