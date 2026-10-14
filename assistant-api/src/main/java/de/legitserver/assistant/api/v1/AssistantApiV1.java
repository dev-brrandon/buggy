package de.legitserver.assistant.api.v1;

/** Stable Phase-14 HTTP namespace. Breaking changes require a new API version. */
public final class AssistantApiV1 {
    public static final String BASE_PATH = "/api/assistant/v1";
    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    public static final String PANEL_CONTEXT_HEADER = "X-Legit-Panel-Context";
    public static final int DEFAULT_PAGE_SIZE = 25;
    public static final int MAX_PAGE_SIZE = 100;

    private AssistantApiV1() {}
}
