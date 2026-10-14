package de.legitserver.assistant.pipeline.port;

import de.legitserver.assistant.conversations.model.Conversation;

/** Optional conversation resolution. A request without conversation has code NO_CONVERSATION. */
public record PipelineConversationResolution(boolean accepted, String safeCode, Conversation conversation) {
    public PipelineConversationResolution {
        if (safeCode == null || safeCode.isBlank() || safeCode.length() > 96) {
            throw new IllegalArgumentException("safeCode must contain 1..96 characters");
        }
        if (!accepted && conversation != null) throw new IllegalArgumentException("denied result must not carry conversation");
    }

    public static PipelineConversationResolution none() {
        return new PipelineConversationResolution(true, "NO_CONVERSATION", null);
    }

    public static PipelineConversationResolution found(Conversation conversation) {
        if (conversation == null) throw new IllegalArgumentException("conversation must not be null");
        return new PipelineConversationResolution(true, "CONVERSATION_RESOLVED", conversation);
    }

    public static PipelineConversationResolution denied(String safeCode) {
        return new PipelineConversationResolution(false, safeCode, null);
    }
}
