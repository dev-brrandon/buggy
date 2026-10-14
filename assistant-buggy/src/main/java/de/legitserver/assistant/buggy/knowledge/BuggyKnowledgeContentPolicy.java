package de.legitserver.assistant.buggy.knowledge;

/** Port that enforces the existing Foundation secret detector at every Buggy Knowledge boundary. */
public interface BuggyKnowledgeContentPolicy {
    String requirePersistableText(String value, String field, int maxLength);

    boolean containsSecretMaterial(String value);
}
