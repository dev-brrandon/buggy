package de.legitserver.assistant.buggy.channel;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded transport context. It carries location/reference data, never authority. */
public record BuggyChannelContext(
        BuggyChannelType channel,
        String conversationKey,
        String serverId,
        String guildId,
        String channelId,
        Map<String, String> metadata) {

    private static final int MAX_METADATA_ENTRIES = 16;

    public BuggyChannelContext {
        channel = CoreValidation.requireNonNull(channel, "channel");
        conversationKey = CoreValidation.requireSafeToken(conversationKey, "conversationKey", 128);
        serverId = CoreValidation.optionalSafeToken(serverId, "serverId", 128);
        guildId = CoreValidation.optionalSafeToken(guildId, "guildId", 128);
        channelId = CoreValidation.optionalSafeToken(channelId, "channelId", 128);
        if (metadata == null || metadata.isEmpty()) {
            metadata = Map.of();
        } else {
            if (metadata.size() > MAX_METADATA_ENTRIES) {
                throw new IllegalArgumentException("channel metadata exceeds max entries " + MAX_METADATA_ENTRIES);
            }
            LinkedHashMap<String, String> copy = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                String key = CoreValidation.requireSafeToken(entry.getKey(), "metadata key", 64);
                String value = CoreValidation.requireText(entry.getValue(), "metadata value", 256);
                copy.put(key, value);
            }
            metadata = Map.copyOf(copy);
        }
    }
}
