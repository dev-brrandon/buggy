package de.legitserver.assistant.buggy.repository;

import de.legitserver.assistant.buggy.tasks.BuggyTaskId;
import de.legitserver.assistant.buggy.tasks.BuggyTaskRelation;
import de.legitserver.assistant.buggy.tasks.BuggyTaskRevision;
import java.util.List;
import java.util.Optional;

public interface BuggyTaskRepository {
    Optional<BuggyTaskRevision> findCurrent(BuggyTaskId taskId);
    List<BuggyTaskRevision> history(BuggyTaskId taskId);
    default List<BuggyTaskRevision> listCurrentTasks(int limit) { return List.of(); }
    BuggyTaskRevision append(long expectedCurrentRevision, BuggyTaskRevision revision);
    void addRelation(BuggyTaskRelation relation);
    List<BuggyTaskRelation> relations(BuggyTaskId taskId);
}
