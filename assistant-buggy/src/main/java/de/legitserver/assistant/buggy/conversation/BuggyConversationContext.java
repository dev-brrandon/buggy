package de.legitserver.assistant.buggy.conversation;

import de.legitserver.assistant.buggy.channel.BuggyChannelType;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Canonical G1.6 context. Transport metadata and history are descriptive, never authorization inputs. */
public record BuggyConversationContext(
        ActorId actorId,
        BuggyChannelType channel,
        String conversationKey,
        String serverId,
        String currentMessage,
        BuggyConversationLanguage language,
        List<BuggyConversationTurn> recentTurns,
        Map<String, String> metadata) {

    public static final int MAX_RECENT_TURNS = 24;
    public static final int MAX_METADATA_ENTRIES = 16;

    public BuggyConversationContext {
        actorId = CoreValidation.requireNonNull(actorId, "actorId");
        channel = CoreValidation.requireNonNull(channel, "channel");
        conversationKey = CoreValidation.requireSafeToken(conversationKey, "conversationKey", 128);
        serverId = CoreValidation.optionalSafeToken(serverId, "serverId", 128);
        currentMessage = CoreValidation.requireText(currentMessage, "currentMessage", 16_384);
        language = CoreValidation.requireNonNull(language, "language");
        recentTurns = List.copyOf(CoreValidation.requireNonNull(recentTurns, "recentTurns"));
        if (recentTurns.size() > MAX_RECENT_TURNS) {
            throw new IllegalArgumentException("recentTurns exceeds max " + MAX_RECENT_TURNS);
        }
        if (metadata == null || metadata.isEmpty()) {
            metadata = Map.of();
        } else {
            if (metadata.size() > MAX_METADATA_ENTRIES) {
                throw new IllegalArgumentException("metadata exceeds max " + MAX_METADATA_ENTRIES);
            }
            LinkedHashMap<String, String> bounded = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                bounded.put(
                        CoreValidation.requireSafeToken(entry.getKey(), "metadata key", 64),
                        CoreValidation.requireText(entry.getValue(), "metadata value", 256));
            }
            metadata = Map.copyOf(bounded);
        }
    }
}
