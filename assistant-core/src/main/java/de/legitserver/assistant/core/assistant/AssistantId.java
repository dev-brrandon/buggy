package de.legitserver.assistant.core.assistant;

/** Stable technical identities for the initially approved Legit assistants. */
public enum AssistantId {
    KAESE,
    BYTE,
    BUGGY;

    public static AssistantId fromWireValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("assistantId must not be null");
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown assistantId: " + value, exception);
        }
    }
}
