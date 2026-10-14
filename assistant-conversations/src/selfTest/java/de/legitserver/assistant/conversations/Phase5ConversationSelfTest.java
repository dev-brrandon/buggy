package de.legitserver.assistant.conversations;

import de.legitserver.assistant.conversations.model.Conversation;
import de.legitserver.assistant.conversations.model.ConversationContextType;
import de.legitserver.assistant.conversations.model.ConversationId;
import de.legitserver.assistant.conversations.model.ConversationMessage;
import de.legitserver.assistant.conversations.model.ConversationState;
import de.legitserver.assistant.conversations.model.MessageAuthorType;
import de.legitserver.assistant.conversations.model.MessageId;
import de.legitserver.assistant.conversations.model.MessageRedactionState;
import de.legitserver.assistant.conversations.model.NewConversationMessage;
import de.legitserver.assistant.conversations.policy.ConversationExpiryPolicy;
import de.legitserver.assistant.conversations.policy.ConversationLimits;
import de.legitserver.assistant.conversations.policy.ConversationStateMachine;
import de.legitserver.assistant.conversations.policy.IllegalConversationStateTransitionException;
import de.legitserver.assistant.conversations.repository.AppendMessageResult;
import de.legitserver.assistant.conversations.repository.ConversationContextLimitExceededException;
import de.legitserver.assistant.conversations.repository.ConversationLimitExceededException;
import de.legitserver.assistant.conversations.repository.ConversationNotFoundException;
import de.legitserver.assistant.conversations.repository.ConversationRepository;
import de.legitserver.assistant.conversations.repository.ConversationRevisionConflictException;
import de.legitserver.assistant.conversations.service.ConversationExpiryService;
import de.legitserver.assistant.conversations.service.ConversationService;
import de.legitserver.assistant.conversations.validation.ConversationValidation;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.classification.DataClassification;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Framework-free Phase-5 domain and lifecycle verification. */
public final class Phase5ConversationSelfTest {
    private static final Instant T0 = Instant.parse("2026-10-03T20:00:00Z");
    private static final ActorId OWNER = new ActorId("actor:phase5-owner");

    private Phase5ConversationSelfTest() {}

    public static void main(String[] args) {
        verifyStateMachine();
        verifyLimitsAndModels();
        verifyServiceAndOptimisticRevision();
        verifyExpiryHook();
        System.out.println("PHASE5_CONVERSATION_SELF_TEST: PASS");
    }

    private static void verifyStateMachine() {
        ConversationStateMachine machine = new ConversationStateMachine();
        require(machine.canTransition(ConversationState.CREATED, ConversationState.ACTIVE), "CREATED -> ACTIVE");
        require(machine.canTransition(ConversationState.ACTIVE, ConversationState.PROCESSING), "ACTIVE -> PROCESSING");
        require(machine.canTransition(ConversationState.PROCESSING, ConversationState.WAITING_FOR_INPUT), "PROCESSING -> WAITING_FOR_INPUT");
        require(machine.canTransition(ConversationState.ACTIVE, ConversationState.COMPLETED), "ACTIVE -> COMPLETED");
        require(!machine.canTransition(ConversationState.COMPLETED, ConversationState.ACTIVE), "terminal state must not reopen");
        expect(IllegalConversationStateTransitionException.class,
                () -> machine.requireTransition(ConversationState.CANCELLED, ConversationState.ACTIVE));
    }

    private static void verifyLimitsAndModels() {
        ConversationLimits limits = ConversationLimits.defaults();
        require(limits.maxMessageUtf8Bytes() == 32768, "message limit");
        require(limits.maxMessages() == 500, "message count limit");
        require(limits.maxConversationContextUtf8Bytes() == 2 * 1024 * 1024, "conversation context limit");
        limits.requireAttachmentCount(8);
        limits.requireRetrievalContextUtf8Bytes(256 * 1024);
        expect(IllegalArgumentException.class, () -> limits.requireAttachmentCount(9));
        expect(IllegalArgumentException.class, () -> limits.requireRetrievalContextUtf8Bytes(256 * 1024 + 1));
        expect(IllegalArgumentException.class,
                () -> new ConversationLimits(32769, 1, 1024, 1, 256, 0, 1024, 1));

        String content = "Hallo Käse / Byte / Buggy";
        String hash = ConversationValidation.sha256(content);
        ConversationMessage message = new ConversationMessage(
                MessageId.random(),
                ConversationId.random(),
                1,
                MessageAuthorType.HUMAN,
                OWNER,
                content,
                hash,
                DataClassification.INTERNAL,
                MessageRedactionState.NONE,
                T0,
                0,
                Map.of("channel", "panel"));
        require(hash.equals(message.contentSha256()), "content hash");
        expect(IllegalArgumentException.class, () -> new ConversationMessage(
                MessageId.random(),
                ConversationId.random(),
                1,
                MessageAuthorType.HUMAN,
                OWNER,
                content,
                "0".repeat(64),
                DataClassification.INTERNAL,
                MessageRedactionState.NONE,
                T0,
                0,
                Map.of()));
        expect(IllegalArgumentException.class, () -> new NewConversationMessage(
                MessageAuthorType.HUMAN,
                null,
                "missing actor",
                DataClassification.INTERNAL,
                MessageRedactionState.NONE,
                Map.of()));
    }

    private static void verifyServiceAndOptimisticRevision() {
        FakeRepository repository = new FakeRepository();
        MutableClock clock = new MutableClock(T0);
        ConversationService service = new ConversationService(
                repository,
                new ConversationStateMachine(),
                new ConversationExpiryPolicy(),
                new ConversationLimits(32768, 2, 2 * 1024 * 1024, 32, 16384, 8, 256 * 1024, 100),
                clock);

        Conversation created = service.create(
                AssistantId.BUGGY,
                OWNER,
                Origin.PANEL,
                ConversationContextType.DIAGNOSTICS,
                DataClassification.INTERNAL,
                Map.of("purpose", "phase5"));
        require(created.state() == ConversationState.CREATED, "created state");
        require(created.expiresAt().equals(T0.plus(Duration.ofHours(1))), "diagnostics expiry");

        clock.advance(Duration.ofMinutes(1));
        Conversation active = service.transition(created.conversationId(), 0, ConversationState.ACTIVE);
        require(active.revision() == 1, "transition increments revision");

        clock.advance(Duration.ofMinutes(1));
        AppendMessageResult first = service.append(
                created.conversationId(),
                1,
                new NewConversationMessage(
                        MessageAuthorType.HUMAN,
                        OWNER,
                        "erste Nachricht",
                        DataClassification.CONFIDENTIAL,
                        MessageRedactionState.NONE,
                        Map.of()));
        require(first.message().sequenceNo() == 1, "first sequence");
        require(first.conversation().revision() == 2, "append increments revision");
        require(first.conversation().classification() == DataClassification.CONFIDENTIAL, "classification escalates");

        expect(ConversationRevisionConflictException.class, () -> service.append(
                created.conversationId(),
                1,
                new NewConversationMessage(
                        MessageAuthorType.ASSISTANT,
                        null,
                        "stale",
                        DataClassification.INTERNAL,
                        MessageRedactionState.NONE,
                        Map.of())));

        AppendMessageResult second = service.append(
                created.conversationId(),
                2,
                new NewConversationMessage(
                        MessageAuthorType.ASSISTANT,
                        null,
                        "zweite Nachricht",
                        DataClassification.INTERNAL,
                        MessageRedactionState.NONE,
                        Map.of()));
        require(second.message().sequenceNo() == 2, "second sequence");
        expect(ConversationLimitExceededException.class, () -> service.append(
                created.conversationId(),
                3,
                new NewConversationMessage(
                        MessageAuthorType.ASSISTANT,
                        null,
                        "zu viel",
                        DataClassification.INTERNAL,
                        MessageRedactionState.NONE,
                        Map.of())));
        require(repository.findById(created.conversationId()).orElseThrow().revision() == 3,
                "limit failure must not advance revision");

        FakeRepository contextRepository = new FakeRepository();
        ConversationService contextService = new ConversationService(
                contextRepository,
                new ConversationStateMachine(),
                new ConversationExpiryPolicy(),
                new ConversationLimits(32768, 10, 1024, 32, 16384, 8, 256 * 1024, 100),
                new MutableClock(T0));
        Conversation contextConversation = contextService.create(
                AssistantId.KAESE, OWNER, Origin.PANEL, ConversationContextType.GENERAL, DataClassification.INTERNAL, Map.of());
        contextConversation = contextService.transition(contextConversation.conversationId(), 0, ConversationState.ACTIVE);
        ConversationId contextConversationId = contextConversation.conversationId();
        var contextFirst = contextService.append(
                contextConversationId,
                1,
                new NewConversationMessage(
                        MessageAuthorType.HUMAN, OWNER, "a".repeat(800), DataClassification.INTERNAL, MessageRedactionState.NONE, Map.of()));
        expect(ConversationContextLimitExceededException.class, () -> contextService.append(
                contextConversationId,
                contextFirst.conversation().revision(),
                new NewConversationMessage(
                        MessageAuthorType.HUMAN, OWNER, "b".repeat(300), DataClassification.INTERNAL, MessageRedactionState.NONE, Map.of())));
    }

    private static void verifyExpiryHook() {
        FakeRepository repository = new FakeRepository();
        MutableClock clock = new MutableClock(T0);
        ConversationService service = new ConversationService(
                repository,
                new ConversationStateMachine(),
                new ConversationExpiryPolicy(),
                ConversationLimits.defaults(),
                clock);
        Conversation conversation = service.create(
                AssistantId.BYTE,
                OWNER,
                Origin.PANEL,
                ConversationContextType.KNOWLEDGE,
                DataClassification.INTERNAL,
                Map.of());
        conversation = service.transition(conversation.conversationId(), 0, ConversationState.ACTIVE);
        clock.advance(Duration.ofHours(5));
        var result = new ConversationExpiryService(
                        repository,
                        new ConversationStateMachine(),
                        ConversationLimits.defaults(),
                        clock)
                .expireDue();
        require(result.candidates() == 1 && result.expired() == 1 && result.conflicts() == 0, "expiry sweep");
        require(repository.findById(conversation.conversationId()).orElseThrow().state() == ConversationState.EXPIRED,
                "conversation expired");
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) return;
            throw new AssertionError("Expected " + type.getName() + " but got " + failure, failure);
        }
        throw new AssertionError("Expected " + type.getName());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (ZoneOffset.UTC.equals(zone)) return this;
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static final class FakeRepository implements ConversationRepository {
        private final Map<ConversationId, Conversation> conversations = new HashMap<>();
        private final Map<ConversationId, List<ConversationMessage>> messages = new HashMap<>();

        @Override
        public void create(Conversation conversation) {
            if (conversations.putIfAbsent(conversation.conversationId(), conversation) != null) {
                throw new IllegalStateException("duplicate conversation");
            }
            messages.put(conversation.conversationId(), new ArrayList<>());
        }

        @Override
        public Optional<Conversation> findById(ConversationId conversationId) {
            return Optional.ofNullable(conversations.get(conversationId));
        }

        @Override
        public List<ConversationMessage> listMessages(ConversationId conversationId, long afterSequenceExclusive, int limit) {
            return messages.getOrDefault(conversationId, List.of()).stream()
                    .filter(message -> message.sequenceNo() > afterSequenceExclusive)
                    .limit(limit)
                    .toList();
        }

        @Override
        public long countMessages(ConversationId conversationId) {
            return messages.getOrDefault(conversationId, List.of()).size();
        }

        @Override
        public Conversation transition(
                ConversationId conversationId,
                long expectedRevision,
                ConversationState targetState,
                Instant now,
                Instant expiresAt) {
            Conversation current = requireConversation(conversationId);
            requireRevision(current, expectedRevision);
            Conversation updated = new Conversation(
                    current.conversationId(),
                    current.assistantId(),
                    current.ownerActorId(),
                    current.origin(),
                    current.contextType(),
                    targetState,
                    current.classification(),
                    current.createdAt(),
                    now,
                    now,
                    expiresAt,
                    current.revision() + 1,
                    current.metadata());
            conversations.put(conversationId, updated);
            return updated;
        }

        @Override
        public AppendMessageResult appendMessage(
                ConversationId conversationId,
                long expectedRevision,
                NewConversationMessage message,
                Instant now,
                Instant expiresAt,
                int maxMessages,
                int maxConversationContextUtf8Bytes) {
            Conversation current = requireConversation(conversationId);
            requireRevision(current, expectedRevision);
            List<ConversationMessage> currentMessages = messages.get(conversationId);
            if (currentMessages.size() >= maxMessages) throw new ConversationLimitExceededException(maxMessages);
            long contextBytes = currentMessages.stream()
                    .mapToLong(existing -> existing.content().getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                    .sum();
            int newBytes = message.content().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (contextBytes + newBytes > maxConversationContextUtf8Bytes) {
                throw new ConversationContextLimitExceededException(maxConversationContextUtf8Bytes);
            }
            long sequence = currentMessages.size() + 1L;
            ConversationMessage persisted = new ConversationMessage(
                    MessageId.random(),
                    conversationId,
                    sequence,
                    message.authorType(),
                    message.actorId(),
                    message.content(),
                    ConversationValidation.sha256(message.content()),
                    message.classification(),
                    message.redactionState(),
                    now,
                    0,
                    message.metadata());
            currentMessages.add(persisted);
            Conversation updated = new Conversation(
                    current.conversationId(),
                    current.assistantId(),
                    current.ownerActorId(),
                    current.origin(),
                    current.contextType(),
                    current.state(),
                    DataClassification.max(current.classification(), message.classification()),
                    current.createdAt(),
                    now,
                    now,
                    expiresAt,
                    current.revision() + 1,
                    current.metadata());
            conversations.put(conversationId, updated);
            return new AppendMessageResult(updated, persisted);
        }

        @Override
        public List<Conversation> findExpiredCandidates(Instant now, int limit) {
            return conversations.values().stream()
                    .filter(conversation -> conversation.expiresAt() != null)
                    .filter(conversation -> !conversation.expiresAt().isAfter(now))
                    .filter(conversation -> !conversation.state().isTerminal())
                    .sorted((left, right) -> left.expiresAt().compareTo(right.expiresAt()))
                    .limit(limit)
                    .toList();
        }

        private Conversation requireConversation(ConversationId id) {
            Conversation value = conversations.get(id);
            if (value == null) throw new ConversationNotFoundException(id);
            return value;
        }

        private static void requireRevision(Conversation current, long expectedRevision) {
            if (current.revision() != expectedRevision) {
                throw new ConversationRevisionConflictException(current.conversationId(), expectedRevision);
            }
        }
    }
}
