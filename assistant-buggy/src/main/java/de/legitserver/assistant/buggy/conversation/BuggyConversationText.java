package de.legitserver.assistant.buggy.conversation;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class BuggyConversationText {
    private BuggyConversationText() {}

    static String normalized(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT);
    }

    static Set<String> words(String value) {
        String normalized = normalized(value).replaceAll("[^\\p{L}\\p{N}._:+-]+", " ").strip();
        if (normalized.isEmpty()) return Set.of();
        return Arrays.stream(normalized.split("\\s+"))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    static boolean containsAny(String normalized, String... phrases) {
        return Arrays.stream(phrases).anyMatch(normalized::contains);
    }

    static boolean hasAnyWord(Set<String> words, String... candidates) {
        return Arrays.stream(candidates).anyMatch(words::contains);
    }
}
