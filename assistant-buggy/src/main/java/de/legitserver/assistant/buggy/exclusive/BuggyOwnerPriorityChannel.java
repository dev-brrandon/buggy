package de.legitserver.assistant.buggy.exclusive;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Stateless priority projection; it never replaces Requests, Incidents or Self-Development persistence. */
public final class BuggyOwnerPriorityChannel {
    public List<BuggyPriorityItem> prioritize(List<BuggyPriorityItem> candidates, int limit) {
        Objects.requireNonNull(candidates, "candidates");
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
        return candidates.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingInt((BuggyPriorityItem item) -> item.attention().ordinal()).reversed()
                        .thenComparing(BuggyPriorityItem::createdAt)
                        .thenComparing(BuggyPriorityItem::priorityId))
                .limit(limit)
                .toList();
    }
}
