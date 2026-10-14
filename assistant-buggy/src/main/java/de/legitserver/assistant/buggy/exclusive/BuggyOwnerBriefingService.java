package de.legitserver.assistant.buggy.exclusive;

import de.legitserver.assistant.buggy.activity.BuggyActivityEntry;
import de.legitserver.assistant.buggy.activity.BuggyAttentionLevel;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentRevision;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentState;
import de.legitserver.assistant.buggy.tasks.BuggyTaskRevision;
import de.legitserver.assistant.buggy.tasks.BuggyTaskStatus;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Builds concise Owner briefings from authoritative B6/B7 projections without persisting a second copy. */
public final class BuggyOwnerBriefingService {
    private final Clock clock;

    public BuggyOwnerBriefingService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public BuggyOwnerBriefing create(
            List<BuggyActivityEntry> activity,
            List<BuggyTaskRevision> tasks,
            List<BuggyPriorityItem> priorities,
            List<BuggySelfDevelopmentRevision> selfDevelopment,
            List<String> learned) {
        List<BuggyActivityEntry> activityCopy = List.copyOf(Objects.requireNonNull(activity, "activity"));
        List<BuggyTaskRevision> taskCopy = List.copyOf(Objects.requireNonNull(tasks, "tasks"));
        List<BuggyPriorityItem> priorityCopy = List.copyOf(Objects.requireNonNull(priorities, "priorities"));
        List<BuggySelfDevelopmentRevision> selfDevCopy = List.copyOf(Objects.requireNonNull(selfDevelopment, "selfDevelopment"));

        List<String> happened = activityCopy.stream()
                .sorted(Comparator.comparing(BuggyActivityEntry::occurredAt).reversed())
                .limit(20)
                .map(BuggyActivityEntry::summary)
                .toList();
        List<String> automatic = activityCopy.stream()
                .filter(entry -> entry.automationId() != null)
                .sorted(Comparator.comparing(BuggyActivityEntry::occurredAt).reversed())
                .limit(20)
                .map(BuggyActivityEntry::summary)
                .toList();
        List<String> open = taskCopy.stream()
                .filter(task -> task.status() != BuggyTaskStatus.COMPLETED && task.status() != BuggyTaskStatus.CANCELLED)
                .sorted(Comparator.comparingInt((BuggyTaskRevision task) -> task.attention().ordinal()).reversed())
                .limit(20)
                .map(task -> task.title() + " [" + task.status().name() + "]")
                .toList();
        List<String> owner = priorityCopy.stream()
                .filter(item -> item.kind() == BuggyPriorityKind.OWNER_DECISION
                        || item.attention().ordinal() >= BuggyAttentionLevel.ACTION_REQUIRED.ordinal())
                .limit(20)
                .map(BuggyPriorityItem::summary)
                .toList();
        List<String> selfDevLessons = selfDevCopy.stream()
                .filter(item -> item.state() == BuggySelfDevelopmentState.VERIFIED
                        || item.state() == BuggySelfDevelopmentState.REJECTED_BY_BUGGY
                        || item.state() == BuggySelfDevelopmentState.ROLLED_BACK)
                .sorted(Comparator.comparing(BuggySelfDevelopmentRevision::changedAt).reversed())
                .limit(10)
                .map(item -> item.title() + ": " + item.reason())
                .toList();
        java.util.ArrayList<String> combinedLearned = new java.util.ArrayList<>(selfDevLessons);
        combinedLearned.addAll(List.copyOf(Objects.requireNonNull(learned, "learned")));
        return new BuggyOwnerBriefing(happened, automatic, open, owner, combinedLearned.stream().limit(20).toList(), clock.instant());
    }
}
