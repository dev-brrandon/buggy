package de.legitserver.assistant.buggy.channel;

import de.legitserver.assistant.core.assistant.AssistantId;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;

/**
 * G1 normalization boundary shared by concrete transports. Authorization remains Foundation/NetworkCore-owned.
 */
public final class BuggyChannelIngress {
    private final Clock clock;

    public BuggyChannelIngress(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public BuggyInboundMessage web(
            BuggyChannelPrincipal principal,
            String conversationKey,
            String messageId,
            String content,
            Map<String, String> metadata) {
        return message(principal, new BuggyChannelContext(
                BuggyChannelType.WEB, conversationKey, null, null, null, metadata), messageId, content);
    }

    public BuggyInboundMessage minecraft(
            BuggyChannelPrincipal principal,
            String conversationKey,
            String serverId,
            String messageId,
            String content,
            Map<String, String> metadata) {
        return message(principal, new BuggyChannelContext(
                BuggyChannelType.MINECRAFT, conversationKey, serverId, null, null, metadata), messageId, content);
    }

    public BuggyInboundMessage discord(
            BuggyChannelPrincipal principal,
            String conversationKey,
            String guildId,
            String channelId,
            String messageId,
            String content,
            Map<String, String> metadata) {
        return message(principal, new BuggyChannelContext(
                BuggyChannelType.DISCORD, conversationKey, null, guildId, channelId, metadata), messageId, content);
    }

    private BuggyInboundMessage message(
            BuggyChannelPrincipal principal,
            BuggyChannelContext context,
            String messageId,
            String content) {
        return new BuggyInboundMessage(
                AssistantId.BUGGY, principal, context, messageId, content, clock.instant());
    }
}
