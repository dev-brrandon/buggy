package de.legitserver.assistant.knowledge.policy;

/** Hard Phase-7 safety and payload limits. */
public final class KnowledgeLimits {
    public static final int MAX_TITLE_CHARS = 256;
    public static final int MAX_CONTENT_UTF8_BYTES = 1_048_576;
    public static final int MAX_TAGS = 64;
    public static final int MAX_TAG_CHARS = 96;
    public static final int MAX_METADATA_ENTRIES = 64;
    public static final int MAX_METADATA_UTF8_BYTES = 16_384;
    public static final int MAX_ACL_PERMISSIONS = 64;
    public static final int MAX_PERMISSION_CHARS = 160;
    public static final int MAX_SEARCH_QUERY_CHARS = 512;
    public static final int MAX_SEARCH_LIMIT = 100;

    private KnowledgeLimits() {}
}
