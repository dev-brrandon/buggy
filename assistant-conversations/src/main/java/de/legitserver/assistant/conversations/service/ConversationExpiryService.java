package de.legitserver.assistant.conversations.service;

import de.legitserver.assistant.conversations.model.Conversation;
import de.legitserver.assistant.conversations.model.ConversationState;
import de.legitserver.assistant.conversations.policy.ConversationLimits;
import de.legitserver.assistant.conversations.policy.ConversationStateMachine;
import de.legitserver.assistant.conversations.repository.ConversationRepository;
import de.legitserver.assistant.conversations.repository.ConversationRevisionConflictException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Bounded expiry hook. Scheduling belongs to a later runtime/job phase. */
public final class ConversationExpiryService {
    private final ConversationRepository repository;
    private final ConversationStateMachine stateMachine;
    private final ConversationLimits limits;
    private final Clock clock;

    public ConversationExpiryService(
            ConversationRepository repository,
            ConversationStateMachine stateMachine,
            ConversationLimits limits,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ExpirySweepResult expireDue() {
        Instant now = clock.instant();
        List<Conversation> candidates = repository.findExpiredCandidates(now, limits.maxExpiryBatchSize());
        int expired = 0;
        int conflicts = 0;
        for (Conversation candidate : candidates) {
            if (!stateMachine.canTransition(candidate.state(), ConversationState.EXPIRED)) continue;
            try {
                repository.transition(
                        candidate.conversationId(), candidate.revision(), ConversationState.EXPIRED, now, null);
                expired++;
            } catch (ConversationRevisionConflictException concurrentChange) {
                conflicts++;
            }
        }
        return new ExpirySweepResult(candidates.size(), expired, conflicts);
    }
}
