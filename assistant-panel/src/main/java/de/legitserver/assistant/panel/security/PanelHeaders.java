package de.legitserver.assistant.panel.security;

/** Stable internal Panel-to-Assistant transport headers. */
public final class PanelHeaders {
    public static final String USER_CONTEXT = "X-Legit-Panel-Context";
    public static final String REQUEST_ID = "X-Request-Id";
    public static final String TRACE_ID = "X-Trace-Id";
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String SERVICE_AUDIENCE = "legit-assistant-panel-api";

    private PanelHeaders() {}
}
