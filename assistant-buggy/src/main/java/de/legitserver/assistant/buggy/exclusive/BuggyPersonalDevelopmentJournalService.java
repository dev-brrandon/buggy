package de.legitserver.assistant.buggy.exclusive;

import de.legitserver.assistant.buggy.activity.BuggyActivityEntry;
import de.legitserver.assistant.buggy.activity.BuggyActivityType;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityDomain;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityRevision;
import de.legitserver.assistant.buggy.repository.BuggyActivityRepository;
import de.legitserver.assistant.buggy.repository.BuggyPersonalityRepository;
import de.legitserver.assistant.buggy.repository.BuggySelfDevelopmentRepository;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentRevision;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Derived V1 Personal Development Journal. It intentionally creates no second persistence system.
 */
public final class BuggyPersonalDevelopmentJournalService {
    private final BuggyPersonalityRepository personalities;
    private final BuggySelfDevelopmentRepository selfDevelopment;
    private final BuggyActivityRepository activity;

    public BuggyPersonalDevelopmentJournalService(
            BuggyPersonalityRepository personalities,
            BuggySelfDevelopmentRepository selfDevelopment,
            BuggyActivityRepository activity) {
        this.personalities = Objects.requireNonNull(personalities, "personalities");
        this.selfDevelopment = Objects.requireNonNull(selfDevelopment, "selfDevelopment");
        this.activity = Objects.requireNonNull(activity, "activity");
    }

    public List<BuggyDevelopmentJournalEntry> entries(int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be between 1 and 500");
        ArrayList<BuggyDevelopmentJournalEntry> out = new ArrayList<>();
        for (BuggyPersonalityRevision revision : personalities.history()) {
            BuggyDevelopmentJournalEntryType type = personalityType(revision);
            out.add(new BuggyDevelopmentJournalEntry(
                    "personality-" + revision.revisionId(),
                    type,
                    revision.reason(),
                    List.of("personality:" + revision.revisionId()),
                    revision.createdAt()));
        }
        for (BuggySelfDevelopmentRevision item : selfDevelopment.listCurrentItems(500)) {
            BuggyDevelopmentJournalEntryType type = selfDevelopmentType(item.state());
            if (type == null) continue;
            out.add(new BuggyDevelopmentJournalEntry(
                    "selfdev-" + item.itemId() + "-" + item.revision(),
                    type,
                    item.title() + ": " + journalSummary(item),
                    List.of("selfdev:" + item.itemId(), "selfdev-revision:" + item.revision()),
                    item.changedAt()));
        }
        out.addAll(recurringMistakes(activity.recent(500)));
        out.sort(Comparator.comparing(BuggyDevelopmentJournalEntry::occurredAt).reversed()
                .thenComparing(BuggyDevelopmentJournalEntry::entryId));
        return out.stream().limit(limit).toList();
    }

    private static BuggyDevelopmentJournalEntryType personalityType(BuggyPersonalityRevision revision) {
        if (revision.entries().stream().anyMatch(entry -> entry.domain() == BuggyPersonalityDomain.OWNER_PREFERENCE)) {
            return BuggyDevelopmentJournalEntryType.OWNER_PREFERENCE;
        }
        if (revision.entries().stream().anyMatch(entry -> entry.domain() == BuggyPersonalityDomain.WORKFLOW_ORDERING)) {
            return BuggyDevelopmentJournalEntryType.WORKFLOW_IMPROVEMENT;
        }
        return BuggyDevelopmentJournalEntryType.PERSONALITY_CHANGE;
    }

    private static BuggyDevelopmentJournalEntryType selfDevelopmentType(BuggySelfDevelopmentState state) {
        return switch (state) {
            case VERIFIED -> BuggyDevelopmentJournalEntryType.SELF_DEVELOPMENT_SUCCESS;
            case REJECTED_BY_BUGGY -> BuggyDevelopmentJournalEntryType.REJECTED_IDEA;
            case ROLLED_BACK -> BuggyDevelopmentJournalEntryType.LESSON_LEARNED;
            default -> null;
        };
    }

    private static List<BuggyDevelopmentJournalEntry> recurringMistakes(List<BuggyActivityEntry> entries) {
        Map<String, List<BuggyActivityEntry>> failuresBySummary = new LinkedHashMap<>();
        for (BuggyActivityEntry entry : entries) {
            if (entry.type() != BuggyActivityType.TASK_FAILED && entry.type() != BuggyActivityType.AUTOMATION_FAILED) continue;
            failuresBySummary.computeIfAbsent(entry.summary(), ignored -> new ArrayList<>()).add(entry);
        }
        ArrayList<BuggyDevelopmentJournalEntry> out = new ArrayList<>();
        failuresBySummary.forEach((summary, failures) -> {
            if (failures.size() < 2) return;
            List<BuggyActivityEntry> ordered = failures.stream()
                    .sorted(Comparator.comparing(BuggyActivityEntry::occurredAt))
                    .toList();
            BuggyActivityEntry latest = ordered.getLast();
            List<String> refs = ordered.stream().map(item -> "activity:" + item.activityId()).toList();
            out.add(new BuggyDevelopmentJournalEntry(
                    "recurring-mistake-" + latest.activityId(),
                    BuggyDevelopmentJournalEntryType.RECURRING_MISTAKE,
                    "Recurring failure (" + failures.size() + " occurrences): " + summary,
                    refs,
                    latest.occurredAt()));
        });
        return out;
    }

    private static String journalSummary(BuggySelfDevelopmentRevision item) {
        if (item.reason() != null) return item.reason();
        if (item.proposalSummary() != null) return item.proposalSummary();
        return item.problem();
    }
}
