package de.legitserver.assistant.buggy.selfdev;

/** Enforces the existing Foundation secret detector before free-form Self-Development text can be persisted. */
@FunctionalInterface
public interface BuggySelfDevelopmentContentPolicy {
    String requirePersistableText(String value, String field, int maxLength);
}
