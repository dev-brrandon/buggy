package de.legitserver.assistant.buggy.repository;

import de.legitserver.assistant.buggy.activity.BuggyActivityEntry;
import java.util.List;

public interface BuggyActivityRepository {
    void append(BuggyActivityEntry entry);
    List<BuggyActivityEntry> recent(int limit);
}
