package de.legitserver.assistant.integrations.conversations;

import de.legitserver.assistant.conversations.model.Conversation;
import de.legitserver.assistant.conversations.model.ConversationContextType;
import de.legitserver.assistant.conversations.model.ConversationId;
import de.legitserver.assistant.conversations.model.ConversationMessage;
import de.legitserver.assistant.conversations.model.ConversationState;
import de.legitserver.assistant.conversations.model.MessageAuthorType;
import de.legitserver.assistant.conversations.model.MessageId;
import de.legitserver.assistant.conversations.model.MessageRedactionState;
import de.legitserver.assistant.conversations.model.NewConversationMessage;
import de.legitserver.assistant.conversations.repository.AppendMessageResult;
import de.legitserver.assistant.conversations.repository.ConversationContextLimitExceededException;
import de.legitserver.assistant.conversations.repository.ConversationLimitExceededException;
import de.legitserver.assistant.conversations.repository.ConversationNotFoundException;
import de.legitserver.assistant.conversations.repository.ConversationRepository;
import de.legitserver.assistant.conversations.repository.ConversationRevisionConflictException;
import de.legitserver.assistant.conversations.validation.ConversationValidation;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.persistence.transaction.TransactionRunner;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL JDBC adapter for the ConversationRepository port. */
public final class JdbcConversationRepository implements ConversationRepository {
    private static final String BASE_COLUMNS = "conversation_id, assistant_id, owner_actor_id, origin, context_type, state, classification, created_at, updated_at, last_activity_at, expires_at, revision, metadata_json::text";
    private final TransactionRunner transactions;

    public JdbcConversationRepository(TransactionRunner transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    @Override
    public void create(Conversation conversation) {
        Objects.requireNonNull(conversation, "conversation");
        transactions.required(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO assistant.conversations(conversation_id, assistant_id, owner_actor_id, origin, context_type, state, classification, created_at, updated_at, last_activity_at, expires_at, revision, metadata_json) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))")) {
                int index = 1;
                statement.setObject(index++, conversation.conversationId().value());
                statement.setString(index++, conversation.assistantId().name());
                statement.setString(index++, conversation.ownerActorId().value());
                statement.setString(index++, conversation.origin().name());
                statement.setString(index++, conversation.contextType().name());
                statement.setString(index++, conversation.state().name());
                statement.setString(index++, conversation.classification().name());
                statement.setTimestamp(index++, Timestamp.from(conversation.createdAt()));
                statement.setTimestamp(index++, Timestamp.from(conversation.updatedAt()));
                statement.setTimestamp(index++, Timestamp.from(conversation.lastActivityAt()));
                setInstant(statement, index++, conversation.expiresAt());
                statement.setLong(index++, conversation.revision());
                statement.setString(index, FlatJsonMapCodec.encode(conversation.metadata()));
                if (statement.executeUpdate() != 1) throw new IllegalStateException("Conversation insert count != 1");
            }
            return null;
        });
    }

    @Override
    public Optional<Conversation> findById(ConversationId conversationId) {
        Objects.requireNonNull(conversationId, "conversationId");
        return transactions.readOnly(connection -> findById(connection, conversationId));
    }

    @Override
    public List<ConversationMessage> listMessages(ConversationId conversationId, long afterSequenceExclusive, int limit) {
        Objects.requireNonNull(conversationId, "conversationId");
        if (afterSequenceExclusive < 0) throw new IllegalArgumentException("afterSequenceExclusive must not be negative");
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be 1..200");
        return transactions.readOnly(connection -> {
            List<ConversationMessage> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT message_id, conversation_id, sequence_no, author_type, actor_id, content, content_sha256, classification, redaction_state, created_at, revision, metadata_json::text "
                            + "FROM assistant.conversation_messages WHERE conversation_id=? AND sequence_no>? ORDER BY sequence_no ASC LIMIT ?")) {
                statement.setObject(1, conversationId.value());
                statement.setLong(2, afterSequenceExclusive);
                statement.setInt(3, limit);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) result.add(mapMessage(rs));
                }
            }
            return List.copyOf(result);
        });
    }

    @Override
    public long countMessages(ConversationId conversationId) {
        Objects.requireNonNull(conversationId, "conversationId");
        return transactions.readOnly(connection -> countMessages(connection, conversationId));
    }

    @Override
    public Conversation transition(
            ConversationId conversationId,
            long expectedRevision,
            ConversationState targetState,
            Instant now,
            Instant expiresAt) {
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(targetState, "targetState");
        Objects.requireNonNull(now, "now");
        if (expectedRevision < 0) throw new IllegalArgumentException("expectedRevision must not be negative");
        return transactions.required(connection -> {
            int updated;
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE assistant.conversations SET state=?, updated_at=?, last_activity_at=?, expires_at=?, revision=revision+1 "
                            + "WHERE conversation_id=? AND revision=?")) {
                statement.setString(1, targetState.name());
                statement.setTimestamp(2, Timestamp.from(now));
                statement.setTimestamp(3, Timestamp.from(now));
                setInstant(statement, 4, expiresAt);
                statement.setObject(5, conversationId.value());
                statement.setLong(6, expectedRevision);
                updated = statement.executeUpdate();
            }
            requireUpdate(connection, conversationId, expectedRevision, updated);
            return findById(connection, conversationId).orElseThrow(() -> new ConversationNotFoundException(conversationId));
        });
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
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(now, "now");
        if (expectedRevision < 0) throw new IllegalArgumentException("expectedRevision must not be negative");
        if (maxMessages < 1) throw new IllegalArgumentException("maxMessages must be positive");
        if (maxConversationContextUtf8Bytes < 1) throw new IllegalArgumentException("maxConversationContextUtf8Bytes must be positive");
        return transactions.required(connection -> {
            Conversation current = findById(connection, conversationId)
                    .orElseThrow(() -> new ConversationNotFoundException(conversationId));
            if (current.state().isTerminal()) {
                throw new IllegalStateException("Cannot append to terminal conversation: " + current.state());
            }
            DataClassification classification = DataClassification.max(current.classification(), message.classification());
            int updated;
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE assistant.conversations SET classification=?, updated_at=?, last_activity_at=?, expires_at=?, revision=revision+1 "
                            + "WHERE conversation_id=? AND revision=?")) {
                statement.setString(1, classification.name());
                statement.setTimestamp(2, Timestamp.from(now));
                statement.setTimestamp(3, Timestamp.from(now));
                setInstant(statement, 4, expiresAt);
                statement.setObject(5, conversationId.value());
                statement.setLong(6, expectedRevision);
                updated = statement.executeUpdate();
            }
            requireUpdate(connection, conversationId, expectedRevision, updated);
            MessageStats stats = messageStats(connection, conversationId);
            if (stats.count() >= maxMessages) throw new ConversationLimitExceededException(maxMessages);
            int newBytes = message.content().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (stats.utf8Bytes() + newBytes > maxConversationContextUtf8Bytes) {
                throw new ConversationContextLimitExceededException(maxConversationContextUtf8Bytes);
            }
            long sequence = stats.count() + 1;
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
            insertMessage(connection, persisted);
            Conversation head = findById(connection, conversationId)
                    .orElseThrow(() -> new ConversationNotFoundException(conversationId));
            return new AppendMessageResult(head, persisted);
        });
    }

    @Override
    public List<Conversation> findExpiredCandidates(Instant now, int limit) {
        Objects.requireNonNull(now, "now");
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be 1..500");
        return transactions.readOnly(connection -> {
            List<Conversation> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + BASE_COLUMNS + " FROM assistant.conversations "
                            + "WHERE expires_at IS NOT NULL AND expires_at<=? AND state NOT IN ('COMPLETED','CANCELLED','EXPIRED','FAILED') "
                            + "ORDER BY expires_at ASC, conversation_id ASC LIMIT ?")) {
                statement.setTimestamp(1, Timestamp.from(now));
                statement.setInt(2, limit);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) result.add(mapConversation(rs));
                }
            }
            return List.copyOf(result);
        });
    }

    private static Optional<Conversation> findById(Connection connection, ConversationId id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + BASE_COLUMNS + " FROM assistant.conversations WHERE conversation_id=?")) {
            statement.setObject(1, id.value());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapConversation(rs)) : Optional.empty();
            }
        }
    }


    private static MessageStats messageStats(Connection connection, ConversationId id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*), COALESCE(sum(octet_length(content)),0) FROM assistant.conversation_messages WHERE conversation_id=?")) {
            statement.setObject(1, id.value());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Message stats row missing");
                return new MessageStats(rs.getLong(1), rs.getLong(2));
            }
        }
    }

    private static long countMessages(Connection connection, ConversationId id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM assistant.conversation_messages WHERE conversation_id=?")) {
            statement.setObject(1, id.value());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Message count row missing");
                return rs.getLong(1);
            }
        }
    }

    private static void insertMessage(Connection connection, ConversationMessage message) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assistant.conversation_messages(message_id, conversation_id, sequence_no, author_type, actor_id, content, content_sha256, classification, redaction_state, created_at, revision, metadata_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))")) {
            int index = 1;
            statement.setObject(index++, message.messageId().value());
            statement.setObject(index++, message.conversationId().value());
            statement.setLong(index++, message.sequenceNo());
            statement.setString(index++, message.authorType().name());
            if (message.actorId() == null) statement.setNull(index++, java.sql.Types.VARCHAR);
            else statement.setString(index++, message.actorId().value());
            statement.setString(index++, message.content());
            statement.setString(index++, message.contentSha256());
            statement.setString(index++, message.classification().name());
            statement.setString(index++, message.redactionState().name());
            statement.setTimestamp(index++, Timestamp.from(message.createdAt()));
            statement.setLong(index++, message.revision());
            statement.setString(index, FlatJsonMapCodec.encode(message.metadata()));
            if (statement.executeUpdate() != 1) throw new IllegalStateException("Message insert count != 1");
        }
    }

    private static void requireUpdate(Connection connection, ConversationId id, long expectedRevision, int updated)
            throws SQLException {
        if (updated == 1) return;
        if (updated > 1) throw new IllegalStateException("Conversation update affected more than one row");
        if (!exists(connection, id)) throw new ConversationNotFoundException(id);
        throw new ConversationRevisionConflictException(id, expectedRevision);
    }

    private static boolean exists(Connection connection, ConversationId id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM assistant.conversations WHERE conversation_id=?")) {
            statement.setObject(1, id.value());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static Conversation mapConversation(ResultSet rs) throws SQLException {
        return new Conversation(
                new ConversationId(rs.getObject("conversation_id", UUID.class)),
                AssistantId.fromWireValue(rs.getString("assistant_id")),
                new ActorId(rs.getString("owner_actor_id")),
                Origin.valueOf(rs.getString("origin")),
                ConversationContextType.valueOf(rs.getString("context_type")),
                ConversationState.valueOf(rs.getString("state")),
                DataClassification.valueOf(rs.getString("classification")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getTimestamp("last_activity_at").toInstant(),
                toInstant(rs.getTimestamp("expires_at")),
                rs.getLong("revision"),
                FlatJsonMapCodec.decode(rs.getString(13)));
    }

    private static ConversationMessage mapMessage(ResultSet rs) throws SQLException {
        String actor = rs.getString("actor_id");
        return new ConversationMessage(
                new MessageId(rs.getObject("message_id", UUID.class)),
                new ConversationId(rs.getObject("conversation_id", UUID.class)),
                rs.getLong("sequence_no"),
                MessageAuthorType.valueOf(rs.getString("author_type")),
                actor == null ? null : new ActorId(actor),
                rs.getString("content"),
                rs.getString("content_sha256"),
                DataClassification.valueOf(rs.getString("classification")),
                MessageRedactionState.valueOf(rs.getString("redaction_state")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getLong("revision"),
                FlatJsonMapCodec.decode(rs.getString(12)));
    }

    private record MessageStats(long count, long utf8Bytes) {}

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static void setInstant(PreparedStatement statement, int index, Instant value) throws SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
        else statement.setTimestamp(index, Timestamp.from(value));
    }
}
