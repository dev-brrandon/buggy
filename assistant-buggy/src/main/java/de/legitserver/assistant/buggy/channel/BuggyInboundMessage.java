package de.legitserver.assistant.buggy.channel;

import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Canonical G1 inbound envelope shared by Web, Minecraft and Discord adapters. */
public record BuggyInboundMessage(
        AssistantId assistantId,
        BuggyChannelPrincipal principal,
        BuggyChannelContext context,
        String messageId,
        String content,
        Instant receivedAt) {

    public static final int MAX_MESSAGE_UTF8_BYTES = 16_384;

    public BuggyInboundMessage {
        assistantId = CoreValidation.requireNonNull(assistantId, "assistantId");
        if (!AssistantId.BUGGY.equals(assistantId)) {
            throw new IllegalArgumentException("G1 Buggy ingress accepts assistant:buggy only");
        }
        principal = CoreValidation.requireNonNull(principal, "principal");
        context = CoreValidation.requireNonNull(context, "context");
        messageId = CoreValidation.requireSafeToken(messageId, "messageId", 128);
        content = CoreValidation.requireText(content, "content", MAX_MESSAGE_UTF8_BYTES);
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_MESSAGE_UTF8_BYTES) {
            throw new IllegalArgumentException("content exceeds max UTF-8 bytes " + MAX_MESSAGE_UTF8_BYTES);
        }
        receivedAt = CoreValidation.requireNonNull(receivedAt, "receivedAt");
    }
}
