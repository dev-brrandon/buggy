package de.legitserver.assistant.buggy.exclusive;

import java.util.List;

public record BuggyHistoricalComparison(
        String baselineSnapshotId,
        String currentSnapshotId,
        List<BuggyHistoricalDelta> deltas) {
    public BuggyHistoricalComparison {
        if (baselineSnapshotId == null || baselineSnapshotId.isBlank()) throw new IllegalArgumentException("baselineSnapshotId required");
        if (currentSnapshotId == null || currentSnapshotId.isBlank()) throw new IllegalArgumentException("currentSnapshotId required");
        deltas = List.copyOf(deltas);
    }

    public long changedCount() {
        return deltas.stream().filter(delta -> delta.type() != BuggyHistoricalDeltaType.UNCHANGED).count();
    }
}
