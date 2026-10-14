package de.legitserver.assistant.buggy.conversation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded symbolic entity resolver with a small contextual-reference fallback. */
public final class BuggyEntityResolver {
    public BuggyEntityResolution resolve(BuggyConversationContext context) {
        Map<BuggyConversationEntityType, BuggyConversationEntity> entities = detect(context.currentMessage(), context.serverId(), false);
        if (entities.isEmpty() && refersToPriorContext(context.currentMessage())) {
            List<BuggyConversationTurn> turns = context.recentTurns();
            int inspected = 0;
            for (int index = turns.size() - 1; index >= 0 && inspected < 4; index--, inspected++) {
                Map<BuggyConversationEntityType, BuggyConversationEntity> prior =
                        detect(turns.get(index).content(), context.serverId(), true);
                prior.forEach(entities::putIfAbsent);
            }
        }
        return new BuggyEntityResolution(new ArrayList<>(entities.values()));
    }

    private static Map<BuggyConversationEntityType, BuggyConversationEntity> detect(
            String source, String serverId, boolean contextual) {
        String text = BuggyConversationText.normalized(source);
        Set<String> words = BuggyConversationText.words(text);
        EnumMap<BuggyConversationEntityType, BuggyConversationEntity> result =
                new EnumMap<>(BuggyConversationEntityType.class);

        if (BuggyConversationText.hasAnyWord(words, "buggy", "du", "dir", "dich", "dein", "deine", "you", "your", "yourself")) {
            put(result, BuggyConversationEntityType.BUGGY_SELF, "buggy", contextual ? 7_000 : 9_000, contextual);
        }
        if (BuggyConversationText.containsAny(text, "primary owner", "primary-owner")
                || BuggyConversationText.hasAnyWord(words, "owner", "besitzer")) {
            put(result, BuggyConversationEntityType.PRIMARY_OWNER, "primary-owner", contextual ? 6_500 : 9_000, contextual);
        }
        if (BuggyConversationText.containsAny(text, "legit-server", "legit server")
                || BuggyConversationText.hasAnyWord(words, "netzwerk", "network")) {
            put(result, BuggyConversationEntityType.NETWORK, "legit-server", contextual ? 7_000 : 9_500, contextual);
        }
        if (BuggyConversationText.hasAnyWord(words, "server", "proxy", "lobby", "survival", "build")) {
            String value = serverId == null ? "server" : serverId;
            put(result, BuggyConversationEntityType.SERVER, value, contextual ? 6_500 : 8_800, contextual);
        }
        if (BuggyConversationText.hasAnyWord(words,
                "service", "services", "dienst", "dienste", "panel", "discord", "minecraft", "bot")) {
            put(result, BuggyConversationEntityType.SERVICE, "service", contextual ? 6_500 : 8_500, contextual);
        }
        if (BuggyConversationText.hasAnyWord(words,
                "memory", "gedächtnis", "gedaechtnis", "erinnerung", "erinnerungen")) {
            put(result, BuggyConversationEntityType.MEMORY, "native-memory", contextual ? 7_000 : 9_000, contextual);
        }
        if (text.contains("g1.") || BuggyConversationText.hasAnyWord(words,
                "roadmap", "phase", "phasen", "block", "blöcke", "bloecke")) {
            put(result, BuggyConversationEntityType.ROADMAP, "g1-roadmap", contextual ? 7_000 : 9_000, contextual);
        }
        return result;
    }

    private static boolean refersToPriorContext(String source) {
        String text = BuggyConversationText.normalized(source);
        Set<String> words = BuggyConversationText.words(text);
        return BuggyConversationText.hasAnyWord(words,
                "das", "dazu", "davon", "damit", "dort", "weiter", "es", "dies", "dieses", "that", "it", "there", "this", "continue");
    }

    private static void put(
            Map<BuggyConversationEntityType, BuggyConversationEntity> target,
            BuggyConversationEntityType type,
            String value,
            int confidence,
            boolean contextual) {
        target.putIfAbsent(type, new BuggyConversationEntity(type, value, confidence, contextual));
    }
}
