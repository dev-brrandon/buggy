package de.legitserver.assistant.integrations.conversations;

import de.legitserver.assistant.conversations.model.Conversation;
import de.legitserver.assistant.conversations.model.ConversationContextType;
import de.legitserver.assistant.conversations.model.ConversationState;
import de.legitserver.assistant.conversations.model.MessageAuthorType;
import de.legitserver.assistant.conversations.model.MessageRedactionState;
import de.legitserver.assistant.conversations.model.NewConversationMessage;
import de.legitserver.assistant.conversations.policy.ConversationExpiryPolicy;
import de.legitserver.assistant.conversations.policy.ConversationLimits;
import de.legitserver.assistant.conversations.policy.ConversationStateMachine;
import de.legitserver.assistant.conversations.repository.ConversationLimitExceededException;
import de.legitserver.assistant.conversations.repository.ConversationRevisionConflictException;
import de.legitserver.assistant.conversations.service.ConversationExpiryService;
import de.legitserver.assistant.conversations.service.ConversationService;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.persistence.transaction.JdbcTransactionRunner;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** Live PostgreSQL 18 verification for the Phase-5 conversation repository. */
public final class Phase5PostgresConversationSelfTest {
    private static final ActorId OWNER = new ActorId("actor:phase5-postgres");
    private static final Instant T0 = Instant.parse("2026-10-03T21:00:00Z");

    private Phase5PostgresConversationSelfTest() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Expected JDBC URL, user and password");
        JdbcTransactionRunner transactions = new JdbcTransactionRunner(new DriverManagerDataSource(args[0], args[1], args[2]));
        JdbcConversationRepository repository = new JdbcConversationRepository(transactions);
        cleanup(transactions);
        verifySeed(transactions);
        verifyLifecyclePersistence(repository);
        verifyConcurrentOptimisticLocking(repository);
        verifyConcurrentAppend(repository);
        verifyExpiry(repository);
        cleanup(transactions);
        System.out.println("PHASE5_POSTGRES_CONVERSATION_SELF_TEST: PASS");
    }

    private static void verifySeed(JdbcTransactionRunner transactions) {
        String value = transactions.readOnly(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT metadata_value FROM assistant.platform_metadata WHERE metadata_key='foundation.conversation.version'")) {
                try (ResultSet rs = statement.executeQuery()) {
                    require(rs.next(), "conversation foundation seed missing");
                    return rs.getString(1);
                }
            }
        });
        require("phase5".equals(value), "conversation foundation seed invalid");
    }

    private static void verifyLifecyclePersistence(JdbcConversationRepository repository) {
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
                ConversationContextType.BUG_ANALYSIS,
                DataClassification.INTERNAL,
                Map.of("probe", "postgres", "unicode", "Käse"));
        Conversation reloaded = repository.findById(created.conversationId()).orElseThrow();
        require(reloaded.metadata().get("unicode").equals("Käse"), "metadata JSON round-trip");

        clock.advance(Duration.ofSeconds(1));
        Conversation active = service.transition(created.conversationId(), 0, ConversationState.ACTIVE);
        require(active.revision() == 1, "transition revision");

        clock.advance(Duration.ofSeconds(1));
        var first = service.append(
                created.conversationId(),
                1,
                new NewConversationMessage(
                        MessageAuthorType.HUMAN,
                        OWNER,
                        "Problem tritt reproduzierbar auf.",
                        DataClassification.CONFIDENTIAL,
                        MessageRedactionState.NONE,
                        Map.of("source", "panel")));
        require(first.conversation().revision() == 2, "first append revision");
        require(first.conversation().classification() == DataClassification.CONFIDENTIAL, "classification escalation");

        clock.advance(Duration.ofSeconds(1));
        var second = service.append(
                created.conversationId(),
                2,
                new NewConversationMessage(
                        MessageAuthorType.ASSISTANT,
                        null,
                        "Analyse läuft.",
                        DataClassification.INTERNAL,
                        MessageRedactionState.NONE,
                        Map.of()));
        require(second.message().sequenceNo() == 2, "second sequence");
        require(repository.listMessages(created.conversationId(), 0, 10).size() == 2, "message listing");
        require(repository.countMessages(created.conversationId()) == 2, "message count");

        boolean staleRejected = false;
        try {
            service.transition(created.conversationId(), 2, ConversationState.PROCESSING);
        } catch (ConversationRevisionConflictException expected) {
            staleRejected = true;
        }
        require(staleRejected, "stale revision rejected");

        boolean limitRejected = false;
        try {
            service.append(
                    created.conversationId(),
                    3,
                    new NewConversationMessage(
                            MessageAuthorType.ASSISTANT,
                            null,
                            "third",
                            DataClassification.INTERNAL,
                            MessageRedactionState.NONE,
                            Map.of()));
        } catch (ConversationLimitExceededException expected) {
            limitRejected = true;
        }
        require(limitRejected, "message limit rejected");
        require(repository.findById(created.conversationId()).orElseThrow().revision() == 3,
                "limit rollback preserved revision");
    }

    private static void verifyConcurrentOptimisticLocking(JdbcConversationRepository repository) throws Exception {
        Instant now = T0.plus(Duration.ofHours(1));
        Conversation conversation = new Conversation(
                de.legitserver.assistant.conversations.model.ConversationId.random(),
                AssistantId.BYTE,
                OWNER,
                Origin.INTERNAL,
                ConversationContextType.GENERAL,
                ConversationState.ACTIVE,
                DataClassification.INTERNAL,
                now,
                now,
                now,
                now.plus(Duration.ofHours(24)),
                0,
                Map.of("probe", "concurrency"));
        repository.create(conversation);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        Runnable worker = () -> {
            ready.countDown();
            try {
                fire.await();
                repository.transition(
                        conversation.conversationId(),
                        0,
                        ConversationState.PROCESSING,
                        now.plusSeconds(1),
                        now.plus(Duration.ofHours(24)));
                success.incrementAndGet();
            } catch (ConversationRevisionConflictException expected) {
                conflict.incrementAndGet();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        };
        Thread left = new Thread(worker, "phase5-left");
        Thread right = new Thread(worker, "phase5-right");
        left.start();
        right.start();
        ready.await();
        fire.countDown();
        left.join();
        right.join();
        require(success.get() == 1, "exactly one concurrent update succeeds");
        require(conflict.get() == 1, "exactly one concurrent update conflicts");
        require(repository.findById(conversation.conversationId()).orElseThrow().revision() == 1,
                "concurrent revision exactly one increment");
    }

    private static void verifyConcurrentAppend(JdbcConversationRepository repository) throws Exception {
        Instant now = T0.plus(Duration.ofHours(1).plusMinutes(30));
        Conversation conversation = new Conversation(
                de.legitserver.assistant.conversations.model.ConversationId.random(),
                AssistantId.BUGGY,
                OWNER,
                Origin.INTERNAL,
                ConversationContextType.BUG_ANALYSIS,
                ConversationState.ACTIVE,
                DataClassification.INTERNAL,
                now,
                now,
                now,
                now.plus(Duration.ofHours(24)),
                0,
                Map.of("probe", "concurrent-append"));
        repository.create(conversation);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        Runnable worker = () -> {
            ready.countDown();
            try {
                fire.await();
                repository.appendMessage(
                        conversation.conversationId(),
                        0,
                        new NewConversationMessage(
                                MessageAuthorType.ASSISTANT,
                                null,
                                "parallel append",
                                DataClassification.INTERNAL,
                                MessageRedactionState.NONE,
                                Map.of()),
                        now.plusSeconds(1),
                        now.plus(Duration.ofHours(24)),
                        10,
                        2 * 1024 * 1024);
                success.incrementAndGet();
            } catch (ConversationRevisionConflictException expected) {
                conflict.incrementAndGet();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        };
        Thread left = new Thread(worker, "phase5-append-left");
        Thread right = new Thread(worker, "phase5-append-right");
        left.start();
        right.start();
        ready.await();
        fire.countDown();
        left.join();
        right.join();
        require(success.get() == 1, "exactly one concurrent append succeeds");
        require(conflict.get() == 1, "exactly one concurrent append conflicts");
        require(repository.countMessages(conversation.conversationId()) == 1, "concurrent append stores one message");
        require(repository.listMessages(conversation.conversationId(), 0, 10).getFirst().sequenceNo() == 1,
                "concurrent append sequence remains one");
        require(repository.findById(conversation.conversationId()).orElseThrow().revision() == 1,
                "concurrent append increments revision once");
    }

    private static void verifyExpiry(JdbcConversationRepository repository) {
        MutableClock clock = new MutableClock(T0.plus(Duration.ofHours(2)));
        ConversationService service = new ConversationService(
                repository,
                new ConversationStateMachine(),
                new ConversationExpiryPolicy(),
                ConversationLimits.defaults(),
                clock);
        Conversation created = service.create(
                AssistantId.KAESE,
                OWNER,
                Origin.MINECRAFT,
                ConversationContextType.DIAGNOSTICS,
                DataClassification.INTERNAL,
                Map.of("probe", "expiry"));
        service.transition(created.conversationId(), 0, ConversationState.ACTIVE);
        clock.advance(Duration.ofHours(2));
        var result = new ConversationExpiryService(
                        repository,
                        new ConversationStateMachine(),
                        ConversationLimits.defaults(),
                        clock)
                .expireDue();
        require(result.expired() >= 1, "expiry sweep expired due conversation");
        require(repository.findById(created.conversationId()).orElseThrow().state() == ConversationState.EXPIRED,
                "expired state persisted");
    }

    private static void cleanup(JdbcTransactionRunner transactions) {
        transactions.required(connection -> {
            try (PreparedStatement messages = connection.prepareStatement(
                            "DELETE FROM assistant.conversation_messages WHERE conversation_id IN (SELECT conversation_id FROM assistant.conversations WHERE owner_actor_id=?)");
                    PreparedStatement conversations = connection.prepareStatement(
                            "DELETE FROM assistant.conversations WHERE owner_actor_id=?")) {
                messages.setString(1, OWNER.value());
                messages.executeUpdate();
                conversations.setString(1, OWNER.value());
                conversations.executeUpdate();
            }
            return null;
        });
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
            return ZoneOffset.UTC.equals(zone) ? this : Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static final class DriverManagerDataSource implements DataSource {
        private final String jdbcUrl;
        private final String user;
        private final String password;

        private DriverManagerDataSource(String jdbcUrl, String user, String password) {
            this.jdbcUrl = jdbcUrl;
            this.user = user;
            this.password = password;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(jdbcUrl, user, password);
        }

        @Override
        public Connection getConnection(String username, String suppliedPassword) throws SQLException {
            return DriverManager.getConnection(jdbcUrl, username, suppliedPassword);
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return DriverManager.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            DriverManager.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            DriverManager.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return DriverManager.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return Logger.getLogger("de.legitserver.assistant.conversations");
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) return iface.cast(this);
            throw new SQLException("Not a wrapper for " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}
