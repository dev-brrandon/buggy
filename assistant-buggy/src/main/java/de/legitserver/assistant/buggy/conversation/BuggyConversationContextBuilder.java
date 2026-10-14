package de.legitserver.assistant.buggy.conversation;

import de.legitserver.assistant.buggy.channel.BuggyChannelType;
import de.legitserver.assistant.core.actor.ActorId;
import java.util.List;
import java.util.Map;

/** Builds a bounded language-aware context from already-authorized transport/conversation data. */
public final class BuggyConversationContextBuilder {
    public BuggyConversationContext build(
            ActorId actorId,
            BuggyChannelType channel,
            String conversationKey,
            String serverId,
            String currentMessage,
            List<BuggyConversationTurn> recentTurns,
            Map<String, String> metadata) {
        List<BuggyConversationTurn> bounded = recentTurns == null
                ? List.of()
                : recentTurns.stream()
                        .skip(Math.max(0, recentTurns.size() - BuggyConversationContext.MAX_RECENT_TURNS))
                        .toList();
        return new BuggyConversationContext(
                actorId,
                channel,
                conversationKey,
                serverId,
                currentMessage,
                detectLanguage(currentMessage),
                bounded,
                metadata);
    }

    private static BuggyConversationLanguage detectLanguage(String message) {
        var words = BuggyConversationText.words(message);
        int german = score(words, "ich", "du", "dir", "dein", "deine", "was", "wer", "wie", "warum", "kannst",
                "hallo", "moin", "danke", "bitte", "weiter", "und", "der", "die", "das", "server");
        int english = score(words, "i", "you", "your", "what", "who", "how", "why", "can", "hello", "thanks",
                "please", "continue", "and", "the", "server");
        return english > german ? BuggyConversationLanguage.ENGLISH : BuggyConversationLanguage.GERMAN;
    }

    private static int score(java.util.Set<String> words, String... markers) {
        int score = 0;
        for (String marker : markers) if (words.contains(marker)) score++;
        return score;
    }
}
