package de.legitserver.assistant.buggy.conversation;

import java.util.Objects;

/** G1.6 orchestration: context -> intent -> entities -> response plan -> deterministic natural response. */
public final class BuggyNaturalConversationEngine {
    private final BuggyIntentEngine intents;
    private final BuggyEntityResolver entities;
    private final BuggyResponsePlanner planner;
    private final BuggyNaturalResponseComposer composer;

    public BuggyNaturalConversationEngine(
            BuggyIntentEngine intents,
            BuggyEntityResolver entities,
            BuggyResponsePlanner planner,
            BuggyNaturalResponseComposer composer) {
        this.intents = Objects.requireNonNull(intents, "intents");
        this.entities = Objects.requireNonNull(entities, "entities");
        this.planner = Objects.requireNonNull(planner, "planner");
        this.composer = Objects.requireNonNull(composer, "composer");
    }

    public static BuggyNaturalConversationEngine canonical() {
        return new BuggyNaturalConversationEngine(
                new BuggyIntentEngine(),
                new BuggyEntityResolver(),
                new BuggyResponsePlanner(),
                BuggyNaturalResponseComposer.canonical());
    }

    public BuggyNaturalConversationResult respond(BuggyConversationContext context) {
        Objects.requireNonNull(context, "context");
        BuggyIntentClassification intent = intents.classify(context);
        BuggyEntityResolution resolved = entities.resolve(context);
        BuggyResponsePlan plan = planner.plan(intent, resolved);
        String response = composer.compose(context, plan);
        return new BuggyNaturalConversationResult(context, intent, resolved, plan, response);
    }
}
