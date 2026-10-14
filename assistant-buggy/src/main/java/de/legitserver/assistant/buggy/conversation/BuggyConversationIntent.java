package de.legitserver.assistant.buggy.conversation;

/** G1.6 deterministic conversational intents. Tool-backed intents remain deferred to G1.7+. */
public enum BuggyConversationIntent {
    GREETING,
    FAREWELL,
    THANKS,
    IDENTITY_QUERY,
    CAPABILITY_QUERY,
    SOCIAL_STATUS_QUERY,
    CONTINUATION,
    ACTION_REQUEST,
    INFORMATION_QUERY,
    STATEMENT
}
