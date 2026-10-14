package de.legitserver.assistant.requests.policy;

/** Hard bounded Request / Approval limits. */
public final class RequestLimits {
    public static final int MAX_REQUEST_TYPE_CHARS = 96;
    public static final int MAX_CONTENT_TYPE_CHARS = 128;
    public static final int MAX_PAYLOAD_UTF8_BYTES = 65_536;
    public static final int MAX_REASON_CHARS = 2_048;
    public static final int MAX_REQUIRED_APPROVALS = 16;
    public static final int MAX_HISTORY_DETAILS_ENTRIES = 24;
    public static final int MAX_HISTORY_DETAILS_UTF8_BYTES = 16_384;
    public static final int MAX_HISTORY_RESULTS = 500;
    public static final int MAX_APPROVAL_RESULTS = 500;

    private RequestLimits() {}
}
