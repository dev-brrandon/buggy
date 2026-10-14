package de.legitserver.assistant.buggy.exclusive;

import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

public final class BuggyHistoricalComparisonService {
    public BuggyHistoricalComparison compare(BuggyHistoricalSnapshot baseline, BuggyHistoricalSnapshot current) {
        if (baseline == null || current == null) throw new IllegalArgumentException("snapshots required");
        if (!baseline.subject().equals(current.subject())) throw new IllegalArgumentException("snapshot subjects differ");
        Set<String> keys = new TreeSet<>();
        keys.addAll(baseline.attributes().keySet());
        keys.addAll(current.attributes().keySet());
        ArrayList<BuggyHistoricalDelta> deltas = new ArrayList<>();
        for (String key : keys) {
            String before = baseline.attributes().get(key);
            String after = current.attributes().get(key);
            BuggyHistoricalDeltaType type;
            if (before == null) type = BuggyHistoricalDeltaType.ADDED;
            else if (after == null) type = BuggyHistoricalDeltaType.REMOVED;
            else if (before.equals(after)) type = BuggyHistoricalDeltaType.UNCHANGED;
            else type = BuggyHistoricalDeltaType.CHANGED;
            deltas.add(new BuggyHistoricalDelta(key, type, before, after));
        }
        return new BuggyHistoricalComparison(baseline.snapshotId(), current.snapshotId(), deltas);
    }
}
