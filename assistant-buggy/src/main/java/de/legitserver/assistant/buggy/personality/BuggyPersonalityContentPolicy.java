package de.legitserver.assistant.buggy.personality;

/**
 * Boundary used by Buggy Personality before persistence of free-form text.
 * The production adapter delegates to the existing Foundation SecretGuard.
 */
@FunctionalInterface
public interface BuggyPersonalityContentPolicy {
    String requirePersistableText(String value, String field, int maxLength);
}
