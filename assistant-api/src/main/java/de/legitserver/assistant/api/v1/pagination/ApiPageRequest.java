package de.legitserver.assistant.api.v1.pagination;

import de.legitserver.assistant.api.v1.AssistantApiV1;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Opaque cursor pagination contract shared by every collection endpoint. */
public record ApiPageRequest(int pageSize, String cursor, int offset) {
    private static final String PREFIX = "v1:";

    public ApiPageRequest {
        if (pageSize < 1 || pageSize > AssistantApiV1.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be within 1.." + AssistantApiV1.MAX_PAGE_SIZE);
        }
        cursor = cursor == null || cursor.isBlank() ? null : cursor;
        if (offset < 0) throw new IllegalArgumentException("offset must be >= 0");
    }

    public static ApiPageRequest from(Integer requestedPageSize, String cursor) {
        int pageSize = requestedPageSize == null ? AssistantApiV1.DEFAULT_PAGE_SIZE : requestedPageSize;
        return new ApiPageRequest(pageSize, normalize(cursor), decodeOffset(cursor));
    }

    public static String encodeOffset(int offset) {
        if (offset < 0) throw new IllegalArgumentException("offset must be >= 0");
        String raw = PREFIX + offset;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static int decodeOffset(String cursor) {
        if (cursor == null || cursor.isBlank()) return 0;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (!raw.startsWith(PREFIX)) throw new IllegalArgumentException("invalid cursor");
            int offset = Integer.parseInt(raw.substring(PREFIX.length()));
            if (offset < 0) throw new IllegalArgumentException("invalid cursor");
            return offset;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("cursor is invalid", exception);
        }
    }

    private static String normalize(String cursor) {
        return cursor == null || cursor.isBlank() ? null : cursor;
    }
}
