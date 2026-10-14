package de.legitserver.assistant.api.v1.pagination;

import java.util.List;

/** Standard collection envelope. nextCursor is opaque and absent on the final page. */
public record ApiPage<T>(List<T> items, int pageSize, boolean hasMore, String nextCursor) {
    public ApiPage {
        items = items == null ? List.of() : List.copyOf(items);
        if (pageSize < 1) throw new IllegalArgumentException("pageSize must be >= 1");
        if (items.size() > pageSize) throw new IllegalArgumentException("items exceed pageSize");
        if (hasMore && (nextCursor == null || nextCursor.isBlank())) {
            throw new IllegalArgumentException("nextCursor required when hasMore=true");
        }
        if (!hasMore) nextCursor = null;
    }

    public static <T> ApiPage<T> slice(List<T> source, ApiPageRequest request) {
        List<T> safe = source == null ? List.of() : List.copyOf(source);
        int start = Math.min(request.offset(), safe.size());
        int end = Math.min(start + request.pageSize(), safe.size());
        boolean more = end < safe.size();
        return new ApiPage<>(safe.subList(start, end), request.pageSize(), more,
                more ? ApiPageRequest.encodeOffset(end) : null);
    }
}
