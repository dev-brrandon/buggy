package de.legitserver.assistant.buggy.activity;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bundles INFO/NOTICE activity within a bounded window while preserving higher attention individually. */
public final class BuggyActivityBundler {
    private final Duration window;

    public BuggyActivityBundler(Duration window) {
        if (window == null || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be positive");
        }
        this.window = window;
    }

    public List<BuggyActivityBundle> bundle(List<BuggyActivityEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        if (entries.isEmpty()) return List.of();
        ArrayList<BuggyActivityBundle> output = new ArrayList<>();
        ArrayList<BuggyActivityEntry> current = new ArrayList<>();
        List<BuggyActivityEntry> ordered = entries.stream()
                .sorted(java.util.Comparator.comparing(BuggyActivityEntry::occurredAt))
                .toList();
        for (BuggyActivityEntry entry : ordered) {
            if (current.isEmpty()) {
                current.add(entry);
                continue;
            }
            BuggyActivityEntry first = current.getFirst();
            BuggyActivityEntry last = current.getLast();
            boolean lowValue = lowValue(first.attention()) && lowValue(entry.attention());
            boolean sameKind = first.type() == entry.type()
                    && Objects.equals(first.automationId(), entry.automationId())
                    && Objects.equals(first.taskId(), entry.taskId());
            boolean close = !entry.occurredAt().isAfter(last.occurredAt().plus(window));
            if (lowValue && sameKind && close) current.add(entry);
            else {
                output.add(toBundle(current));
                current.clear();
                current.add(entry);
            }
        }
        output.add(toBundle(current));
        return List.copyOf(output);
    }

    private static boolean lowValue(BuggyAttentionLevel level) {
        return level == BuggyAttentionLevel.INFO || level == BuggyAttentionLevel.NOTICE;
    }

    private static BuggyActivityBundle toBundle(List<BuggyActivityEntry> entries) {
        BuggyActivityEntry first = entries.getFirst();
        BuggyActivityEntry last = entries.getLast();
        BuggyAttentionLevel attention = first.attention().ordinal() >= last.attention().ordinal()
                ? first.attention() : last.attention();
        return new BuggyActivityBundle(attention, entries.size(), first.occurredAt(), last.occurredAt(), entries);
    }
}
