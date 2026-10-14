package de.legitserver.assistant.core.error;

/** Core error codes; domains add their own codes without changing the envelope. */
public final class AssistantErrorCodes {

    public static final AssistantErrorCode INVALID_ARGUMENT = code("CORE.INVALID_ARGUMENT");
    public static final AssistantErrorCode INVALID_STATE = code("CORE.INVALID_STATE");
    public static final AssistantErrorCode NOT_FOUND = code("CORE.NOT_FOUND");
    public static final AssistantErrorCode CONFLICT = code("CORE.CONFLICT");
    public static final AssistantErrorCode UNAVAILABLE = code("CORE.UNAVAILABLE");
    public static final AssistantErrorCode INTERNAL = code("CORE.INTERNAL");

    private AssistantErrorCodes() {}

    private static AssistantErrorCode code(String value) {
        return new AssistantErrorCode(value);
    }
}
