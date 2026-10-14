package de.legitserver.assistant.supervisor.selfhealing;

import de.legitserver.assistant.core.assistant.AssistantId;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Thread-safe sliding-window anti-loop state. */
public final class SelfHealingBudgetTracker {
    private final EnumMap<AssistantId, AssistantBudget> budgets = new EnumMap<>(AssistantId.class);

    public SelfHealingBudgetTracker() {
        for (AssistantId assistantId : AssistantId.values()) budgets.put(assistantId, new AssistantBudget());
    }

    public Reservation reserve(
            AssistantId assistantId, String targetFingerprint, Instant now, SelfHealingPolicy policy) {
        if (assistantId == null || targetFingerprint == null || targetFingerprint.isBlank() || now == null || policy == null) {
            throw new IllegalArgumentException("budget reservation fields must not be null");
        }
        AssistantBudget budget = budgets.get(assistantId);
        synchronized (budget) {
            Instant cutoff = now.minus(policy.window());
            prune(budget.allAttempts, cutoff);
            TargetBudget target = budget.targets.computeIfAbsent(targetFingerprint, ignored -> new TargetBudget());
            prune(target.attempts, cutoff);
            if (budget.lastAttempt != null && now.isBefore(budget.lastAttempt.plus(policy.cooldown()))) {
                return Reservation.denied(SelfHealingSuppressionReason.COOLDOWN);
            }
            if (target.lastAttempt != null && now.isBefore(target.lastAttempt.plus(policy.sameTargetSuppression()))) {
                return Reservation.denied(SelfHealingSuppressionReason.SAME_TARGET_SUPPRESSED);
            }
            if (target.attempts.size() >= policy.maxAttemptsPerTarget()) {
                return Reservation.denied(SelfHealingSuppressionReason.MAX_ATTEMPTS_EXCEEDED);
            }
            if (budget.allAttempts.size() >= policy.maxActionsPerWindow()) {
                return Reservation.denied(SelfHealingSuppressionReason.WINDOW_BUDGET_EXCEEDED);
            }
            budget.allAttempts.addLast(now);
            budget.lastAttempt = now;
            target.attempts.addLast(now);
            target.lastAttempt = now;
            return Reservation.permitted();
        }
    }

    public int recordOutcome(AssistantId assistantId, String targetFingerprint, boolean success) {
        AssistantBudget budget = budgets.get(assistantId);
        synchronized (budget) {
            TargetBudget target = budget.targets.computeIfAbsent(targetFingerprint, ignored -> new TargetBudget());
            if (success) target.consecutiveFailures = 0;
            else target.consecutiveFailures++;
            return target.consecutiveFailures;
        }
    }

    private static void prune(ArrayDeque<Instant> attempts, Instant cutoff) {
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) attempts.removeFirst();
    }

    public record Reservation(boolean allowed, SelfHealingSuppressionReason denialReason) {
        public Reservation {
            if (allowed == (denialReason != null)) {
                throw new IllegalArgumentException("reservation state is inconsistent");
            }
        }

        public static Reservation permitted() { return new Reservation(true, null); }

        public static Reservation denied(SelfHealingSuppressionReason reason) { return new Reservation(false, reason); }
    }

    private static final class AssistantBudget {
        private final ArrayDeque<Instant> allAttempts = new ArrayDeque<>();
        private final Map<String, TargetBudget> targets = new HashMap<>();
        private Instant lastAttempt;
    }

    private static final class TargetBudget {
        private final ArrayDeque<Instant> attempts = new ArrayDeque<>();
        private Instant lastAttempt;
        private int consecutiveFailures;
    }
}
