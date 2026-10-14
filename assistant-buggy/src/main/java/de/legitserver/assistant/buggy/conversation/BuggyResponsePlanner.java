package de.legitserver.assistant.buggy.conversation;

import java.util.List;

/** Produces a semantic G1.6 response plan without performing I/O, tools, inference or state changes. */
public final class BuggyResponsePlanner {
    public BuggyResponsePlan plan(BuggyIntentClassification intent, BuggyEntityResolution entities) {
        return switch (intent.intent()) {
            case GREETING -> plan(intent, List.of(BuggyResponseAct.GREET), false, false);
            case FAREWELL -> plan(intent, List.of(BuggyResponseAct.SAY_GOODBYE), false, false);
            case THANKS -> plan(intent, List.of(BuggyResponseAct.ACKNOWLEDGE_THANKS), false, false);
            case IDENTITY_QUERY -> plan(intent, List.of(BuggyResponseAct.IDENTIFY_SELF), false, false);
            case CAPABILITY_QUERY -> plan(intent, List.of(BuggyResponseAct.DESCRIBE_CAPABILITIES), false, false);
            case SOCIAL_STATUS_QUERY -> plan(intent, List.of(BuggyResponseAct.ANSWER_SOCIAL_STATUS), false, false);
            case CONTINUATION -> plan(intent, List.of(BuggyResponseAct.ACKNOWLEDGE_CONTINUATION), false, false);
            case ACTION_REQUEST -> plan(intent, List.of(BuggyResponseAct.EXPLAIN_G17_ACTION_LIMIT), true, true);
            case INFORMATION_QUERY -> informationPlan(intent, entities);
            case STATEMENT -> plan(intent, List.of(BuggyResponseAct.ACKNOWLEDGE_STATEMENT), false, false);
        };
    }

    private static BuggyResponsePlan informationPlan(
            BuggyIntentClassification intent, BuggyEntityResolution entities) {
        if (entities.contains(BuggyConversationEntityType.NETWORK)
                || entities.contains(BuggyConversationEntityType.SERVER)
                || entities.contains(BuggyConversationEntityType.SERVICE)) {
            return plan(intent, List.of(BuggyResponseAct.EXPLAIN_G17_KNOWLEDGE_LIMIT), true, false);
        }
        if (entities.contains(BuggyConversationEntityType.BUGGY_SELF)) {
            return plan(intent, List.of(BuggyResponseAct.DESCRIBE_CAPABILITIES), false, false);
        }
        return plan(intent, List.of(BuggyResponseAct.ASK_CLARIFICATION), false, false);
    }

    private static BuggyResponsePlan plan(
            BuggyIntentClassification intent,
            List<BuggyResponseAct> acts,
            boolean requiresExternalKnowledge,
            boolean requestsAction) {
        return new BuggyResponsePlan(intent.intent(), acts, requiresExternalKnowledge, requestsAction);
    }
}
