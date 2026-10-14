package de.legitserver.assistant.conversations.policy;

import de.legitserver.assistant.conversations.model.ConversationState;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Canonical lifecycle transition policy. Terminal states cannot be reopened. */
public final class ConversationStateMachine {
    private static final Map<ConversationState, Set<ConversationState>> ALLOWED = buildAllowed();

    public boolean canTransition(ConversationState from, ConversationState to) {
        if (from == null || to == null || from == to) return false;
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public void requireTransition(ConversationState from, ConversationState to) {
        if (!canTransition(from, to)) {
            throw new IllegalConversationStateTransitionException(from, to);
        }
    }

    private static Map<ConversationState, Set<ConversationState>> buildAllowed() {
        EnumMap<ConversationState, Set<ConversationState>> result = new EnumMap<>(ConversationState.class);
        result.put(ConversationState.CREATED, EnumSet.of(
                ConversationState.ACTIVE,
                ConversationState.CANCELLED,
                ConversationState.EXPIRED,
                ConversationState.FAILED));
        result.put(ConversationState.ACTIVE, EnumSet.of(
                ConversationState.WAITING_FOR_INPUT,
                ConversationState.WAITING_FOR_APPROVAL,
                ConversationState.PROCESSING,
                ConversationState.COMPLETED,
                ConversationState.CANCELLED,
                ConversationState.EXPIRED,
                ConversationState.FAILED));
        result.put(ConversationState.WAITING_FOR_INPUT, EnumSet.of(
                ConversationState.ACTIVE,
                ConversationState.PROCESSING,
                ConversationState.CANCELLED,
                ConversationState.EXPIRED,
                ConversationState.FAILED));
        result.put(ConversationState.WAITING_FOR_APPROVAL, EnumSet.of(
                ConversationState.ACTIVE,
                ConversationState.PROCESSING,
                ConversationState.CANCELLED,
                ConversationState.EXPIRED,
                ConversationState.FAILED));
        result.put(ConversationState.PROCESSING, EnumSet.of(
                ConversationState.ACTIVE,
                ConversationState.WAITING_FOR_INPUT,
                ConversationState.WAITING_FOR_APPROVAL,
                ConversationState.COMPLETED,
                ConversationState.CANCELLED,
                ConversationState.EXPIRED,
                ConversationState.FAILED));
        result.put(ConversationState.COMPLETED, EnumSet.noneOf(ConversationState.class));
        result.put(ConversationState.CANCELLED, EnumSet.noneOf(ConversationState.class));
        result.put(ConversationState.EXPIRED, EnumSet.noneOf(ConversationState.class));
        result.put(ConversationState.FAILED, EnumSet.noneOf(ConversationState.class));
        return Map.copyOf(result);
    }
}
