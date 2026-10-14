package de.legitserver.assistant.conversations.policy;

/** Hard upper bounds enforced before or during conversation persistence. */
public record ConversationLimits(
        int maxMessageUtf8Bytes,
        int maxMessages,
        int maxConversationContextUtf8Bytes,
        int maxMetadataEntries,
        int maxMetadataUtf8Bytes,
        int maxAttachments,
        int maxRetrievalContextUtf8Bytes,
        int maxExpiryBatchSize) {

    public static final int ABSOLUTE_MAX_MESSAGE_BYTES = 32 * 1024;
    public static final int ABSOLUTE_MAX_MESSAGES = 2_000;
    public static final int ABSOLUTE_MAX_CONTEXT_BYTES = 8 * 1024 * 1024;
    public static final int ABSOLUTE_MAX_METADATA_BYTES = 16 * 1024;
    public static final int ABSOLUTE_MAX_METADATA_ENTRIES = 64;
    public static final int ABSOLUTE_MAX_ATTACHMENTS = 32;
    public static final int ABSOLUTE_MAX_RETRIEVAL_CONTEXT_BYTES = 1024 * 1024;
    public static final int ABSOLUTE_MAX_EXPIRY_BATCH = 500;

    public ConversationLimits {
        requireRange(maxMessageUtf8Bytes, 1, ABSOLUTE_MAX_MESSAGE_BYTES, "maxMessageUtf8Bytes");
        requireRange(maxMessages, 1, ABSOLUTE_MAX_MESSAGES, "maxMessages");
        requireRange(maxConversationContextUtf8Bytes, 1024, ABSOLUTE_MAX_CONTEXT_BYTES, "maxConversationContextUtf8Bytes");
        requireRange(maxMetadataEntries, 1, ABSOLUTE_MAX_METADATA_ENTRIES, "maxMetadataEntries");
        requireRange(maxMetadataUtf8Bytes, 256, ABSOLUTE_MAX_METADATA_BYTES, "maxMetadataUtf8Bytes");
        requireRange(maxAttachments, 0, ABSOLUTE_MAX_ATTACHMENTS, "maxAttachments");
        requireRange(maxRetrievalContextUtf8Bytes, 1024, ABSOLUTE_MAX_RETRIEVAL_CONTEXT_BYTES, "maxRetrievalContextUtf8Bytes");
        requireRange(maxExpiryBatchSize, 1, ABSOLUTE_MAX_EXPIRY_BATCH, "maxExpiryBatchSize");
    }

    public static ConversationLimits defaults() {
        return new ConversationLimits(
                32 * 1024,
                500,
                2 * 1024 * 1024,
                32,
                16 * 1024,
                8,
                256 * 1024,
                100);
    }

    public void requireAttachmentCount(int count) {
        if (count < 0 || count > maxAttachments) {
            throw new IllegalArgumentException("attachment count exceeds limit: " + maxAttachments);
        }
    }

    public void requireRetrievalContextUtf8Bytes(int bytes) {
        if (bytes < 0 || bytes > maxRetrievalContextUtf8Bytes) {
            throw new IllegalArgumentException("retrieval context exceeds limit: " + maxRetrievalContextUtf8Bytes);
        }
    }

    private static void requireRange(int value, int min, int max, String field) {
        if (value < min || value > max) throw new IllegalArgumentException(field + " out of range");
    }
}
