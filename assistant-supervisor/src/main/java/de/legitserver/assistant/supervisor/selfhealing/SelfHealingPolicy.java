package de.legitserver.assistant.supervisor.selfhealing;

import java.time.Duration;

/** Bounded anti-loop policy for automatic low-risk Self-Healing. */
public record SelfHealingPolicy(
        int maxAttemptsPerTarget,
        Duration cooldown,
        Duration window,
        int maxActionsPerWindow,
        Duration sameTargetSuppression,
        int failureEscalationThreshold) {
    public SelfHealingPolicy {
        if (maxAttemptsPerTarget < 1 || maxAttemptsPerTarget > 20) {
            throw new IllegalArgumentException("maxAttemptsPerTarget out of range");
        }
        if (maxActionsPerWindow < 1 || maxActionsPerWindow > 100) {
            throw new IllegalArgumentException("maxActionsPerWindow out of range");
        }
        if (failureEscalationThreshold < 1 || failureEscalationThreshold > maxAttemptsPerTarget) {
            throw new IllegalArgumentException("failureEscalationThreshold out of range");
        }
        requirePositive(cooldown, "cooldown");
        requirePositive(window, "window");
        requirePositive(sameTargetSuppression, "sameTargetSuppression");
        if (cooldown.compareTo(window) > 0 || sameTargetSuppression.compareTo(window) > 0) {
            throw new IllegalArgumentException("cooldown/suppression must not exceed window");
        }
    }

    public static SelfHealingPolicy conservativeDefault() {
        return new SelfHealingPolicy(3, Duration.ofSeconds(30), Duration.ofMinutes(10), 10, Duration.ofMinutes(2), 3);
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
