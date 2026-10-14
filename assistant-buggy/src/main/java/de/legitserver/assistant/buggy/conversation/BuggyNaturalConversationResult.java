package de.legitserver.assistant.buggy.conversation;

import de.legitserver.assistant.core.validation.CoreValidation;

public record BuggyNaturalConversationResult(
        BuggyConversationContext context,
        BuggyIntentClassification intent,
        BuggyEntityResolution entities,
        BuggyResponsePlan plan,
        String response) {

    public BuggyNaturalConversationResult {
        context = CoreValidation.requireNonNull(context, "context");
        intent = CoreValidation.requireNonNull(intent, "intent");
        entities = CoreValidation.requireNonNull(entities, "entities");
        plan = CoreValidation.requireNonNull(plan, "plan");
        response = CoreValidation.requireText(response, "response", 16_384);
    }
}
