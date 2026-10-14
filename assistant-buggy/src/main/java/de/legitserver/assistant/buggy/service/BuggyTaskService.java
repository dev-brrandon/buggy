package de.legitserver.assistant.buggy.service;

import de.legitserver.assistant.buggy.activity.BuggyActivityEntry;
import de.legitserver.assistant.buggy.activity.BuggyActivityType;
import de.legitserver.assistant.buggy.activity.BuggyAttentionLevel;
import de.legitserver.assistant.buggy.repository.BuggyActivityRepository;
import de.legitserver.assistant.buggy.repository.BuggyTaskRepository;
import de.legitserver.assistant.buggy.tasks.BuggyTaskId;
import de.legitserver.assistant.buggy.tasks.BuggyTaskRelation;
import de.legitserver.assistant.buggy.tasks.BuggyTaskRelationType;
import de.legitserver.assistant.buggy.tasks.BuggyTaskRevision;
import de.legitserver.assistant.buggy.tasks.BuggyTaskStatus;
import de.legitserver.assistant.core.actor.ActorId;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Durable Buggy task aggregate independent from chat/session lifetime. */
public final class BuggyTaskService {
    private static final Map<BuggyTaskStatus, Set<BuggyTaskStatus>> TRANSITIONS = Map.of(
            BuggyTaskStatus.OPEN, Set.of(BuggyTaskStatus.ACTIVE, BuggyTaskStatus.PAUSED, BuggyTaskStatus.CANCELLED),
            BuggyTaskStatus.ACTIVE, Set.of(BuggyTaskStatus.WAITING_DEPENDENCY, BuggyTaskStatus.WAITING_APPROVAL,
                    BuggyTaskStatus.PAUSED, BuggyTaskStatus.COMPLETED, BuggyTaskStatus.FAILED, BuggyTaskStatus.CANCELLED),
            BuggyTaskStatus.WAITING_DEPENDENCY, Set.of(BuggyTaskStatus.ACTIVE, BuggyTaskStatus.PAUSED, BuggyTaskStatus.CANCELLED),
            BuggyTaskStatus.WAITING_APPROVAL, Set.of(BuggyTaskStatus.ACTIVE, BuggyTaskStatus.PAUSED, BuggyTaskStatus.CANCELLED),
            BuggyTaskStatus.PAUSED, Set.of(BuggyTaskStatus.ACTIVE, BuggyTaskStatus.CANCELLED),
            BuggyTaskStatus.COMPLETED, Set.of(),
            BuggyTaskStatus.FAILED, Set.of(),
            BuggyTaskStatus.CANCELLED, Set.of());

    private final BuggyTaskRepository tasks;
    private final BuggyActivityRepository activity;
    private final Clock clock;

    public BuggyTaskService(BuggyTaskRepository tasks, BuggyActivityRepository activity, Clock clock) {
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.activity = Objects.requireNonNull(activity, "activity");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public BuggyTaskRevision create(String title, String description, BuggyAttentionLevel attention, ActorId actorId) {
        BuggyTaskRevision revision = new BuggyTaskRevision(BuggyTaskId.random(), 1, null, title, description,
                BuggyTaskStatus.OPEN, attention, 0, null, actorId, clock.instant());
        BuggyTaskRevision persisted = tasks.append(0, revision);
        record(persisted, BuggyActivityType.TASK_CREATED, "Task created", Map.of("status", persisted.status().name()));
        return persisted;
    }

    public BuggyTaskRevision transition(BuggyTaskId taskId, BuggyTaskStatus status, int progressPercent,
            String waitingReason, BuggyAttentionLevel attention, ActorId actorId) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(status, "status");
        BuggyTaskRevision current = tasks.findCurrent(taskId).orElseThrow(() -> new IllegalArgumentException("Unknown task"));
        if (!TRANSITIONS.get(current.status()).contains(status)) throw new IllegalStateException("Illegal task transition");
        if (progressPercent < current.progressPercent() && status != BuggyTaskStatus.CANCELLED && status != BuggyTaskStatus.FAILED) {
            throw new IllegalArgumentException("task progress may not move backwards");
        }
        BuggyTaskRevision next = new BuggyTaskRevision(taskId, current.revision() + 1, current.revision(),
                current.title(), current.description(), status, attention, progressPercent, waitingReason, actorId, clock.instant());
        BuggyTaskRevision persisted = tasks.append(current.revision(), next);
        BuggyActivityType type = switch (status) {
            case WAITING_DEPENDENCY, WAITING_APPROVAL -> BuggyActivityType.TASK_WAITING;
            case COMPLETED -> BuggyActivityType.TASK_COMPLETED;
            case FAILED -> BuggyActivityType.TASK_FAILED;
            default -> BuggyActivityType.TASK_UPDATED;
        };
        record(persisted, type, "Task state changed", Map.of("status", status.name(), "progress", Integer.toString(progressPercent)));
        return persisted;
    }

    public void relate(BuggyTaskId source, BuggyTaskId target, BuggyTaskRelationType type, ActorId actorId) {
        if (tasks.findCurrent(source).isEmpty() || tasks.findCurrent(target).isEmpty()) {
            throw new IllegalArgumentException("Both related tasks must exist");
        }
        if (type == BuggyTaskRelationType.PARENT_CHILD) ensureNoParentCycle(source, target);
        tasks.addRelation(new BuggyTaskRelation(source, target, type, actorId, clock.instant()));
    }

    private void ensureNoParentCycle(BuggyTaskId parent, BuggyTaskId child) {
        ArrayDeque<BuggyTaskId> queue = new ArrayDeque<>();
        HashSet<BuggyTaskId> seen = new HashSet<>();
        queue.add(child);
        while (!queue.isEmpty()) {
            BuggyTaskId current = queue.removeFirst();
            if (!seen.add(current)) continue;
            if (current.equals(parent)) throw new IllegalArgumentException("PARENT_CHILD relation would create a cycle");
            if (seen.size() > 256) throw new IllegalStateException("task relation graph exceeds safe traversal bound");
            for (BuggyTaskRelation relation : tasks.relations(current)) {
                if (relation.relationType() == BuggyTaskRelationType.PARENT_CHILD
                        && relation.sourceTaskId().equals(current)) {
                    queue.addLast(relation.targetTaskId());
                }
            }
        }
    }

    private void record(BuggyTaskRevision task, BuggyActivityType type, String summary, Map<String, String> details) {
        activity.append(new BuggyActivityEntry(UUID.randomUUID(), type, task.attention(), task.taskId(), null,
                summary, details, task.changedBy(), clock.instant()));
    }
}
