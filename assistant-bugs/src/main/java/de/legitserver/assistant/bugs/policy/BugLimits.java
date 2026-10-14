package de.legitserver.assistant.bugs.policy;

/** Hard Phase-8 payload bounds derived from the Phase-0 request/metadata budgets. */
public final class BugLimits {
    public static final int MAX_ORIGINAL_REPORT_UTF8_BYTES = 262_144;
    public static final int MAX_ASSISTANT_SUMMARY_UTF8_BYTES = 32_768;
    public static final int MAX_METADATA_ENTRIES = 64;
    public static final int MAX_METADATA_UTF8_BYTES = 16_384;
    public static final int MAX_HISTORY_MAP_ENTRIES = 64;
    public static final int MAX_HISTORY_MAP_UTF8_BYTES = 16_384;
    public static final int MAX_REASON_CHARS = 1_024;
    public static final int MAX_LIST_LIMIT = 200;

    private BugLimits() {}
}
