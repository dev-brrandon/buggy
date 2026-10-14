package de.legitserver.assistant.integrations.buggy;

import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeAnnotationRevision;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeConfidence;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeContentPolicy;
import de.legitserver.assistant.buggy.knowledge.BuggyKnowledgeState;
import de.legitserver.assistant.buggy.repository.BuggyKnowledgeAnnotationRepository;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.knowledge.model.KnowledgeDocumentId;
import de.legitserver.assistant.persistence.transaction.TransactionRunner;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL adapter for append-only Buggy Knowledge stale/dispute assessments. */
public final class JdbcBuggyKnowledgeAnnotationRepository implements BuggyKnowledgeAnnotationRepository {
    private final TransactionRunner transactions;
    private final BuggyKnowledgeContentPolicy contentPolicy;

    public JdbcBuggyKnowledgeAnnotationRepository(
            TransactionRunner transactions,
            BuggyKnowledgeContentPolicy contentPolicy) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.contentPolicy = Objects.requireNonNull(contentPolicy, "contentPolicy");
    }

    @Override
    public Optional<BuggyKnowledgeAnnotationRevision> findCurrent(KnowledgeDocumentId documentId) {
        Objects.requireNonNull(documentId, "documentId");
        return transactions.readOnly(connection -> findCurrent(connection, documentId));
    }

    @Override
    public List<BuggyKnowledgeAnnotationRevision> history(KnowledgeDocumentId documentId) {
        Objects.requireNonNull(documentId, "documentId");
        return transactions.readOnly(connection -> {
            List<BuggyKnowledgeAnnotationRevision> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT document_id, annotation_revision, previous_revision, state, reason, evidence_ref, confidence, "
                            + "created_by, created_at, annotation_sha256 "
                            + "FROM assistant.buggy_knowledge_annotation_revisions "
                            + "WHERE assistant_id='BUGGY' AND document_id=? ORDER BY annotation_revision ASC")) {
                statement.setObject(1, documentId.value());
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) result.add(map(rs));
                }
            }
            return List.copyOf(result);
        });
    }

    @Override
    public BuggyKnowledgeAnnotationRevision append(
            long expectedCurrentRevision,
            BuggyKnowledgeAnnotationRevision revision) {
        Objects.requireNonNull(revision, "revision");
        if (expectedCurrentRevision < 0) throw new IllegalArgumentException("expectedCurrentRevision must be >= 0");
        contentPolicy.requirePersistableText(revision.reason(), "reason", 1024);
        contentPolicy.requirePersistableText(revision.evidenceRef(), "evidenceRef", 1024);
        return transactions.required(connection -> {
            lockDocument(connection, revision.documentId());
            Optional<BuggyKnowledgeAnnotationRevision> current = findCurrent(connection, revision.documentId());
            long actual = current.map(BuggyKnowledgeAnnotationRevision::revision).orElse(0L);
            if (actual != expectedCurrentRevision) {
                throw new IllegalStateException("Stale Buggy Knowledge annotation revision");
            }
            if (revision.revision() != expectedCurrentRevision + 1) {
                throw new IllegalArgumentException("Buggy Knowledge annotation does not continue the active head");
            }
            insertRevision(connection, revision);
            if (expectedCurrentRevision == 0) {
                insertHead(connection, revision);
            } else {
                updateHead(connection, expectedCurrentRevision, revision);
            }
            return findCurrent(connection, revision.documentId())
                    .orElseThrow(() -> new IllegalStateException("Buggy Knowledge annotation insert was not readable"));
        });
    }

    private static void lockDocument(Connection connection, KnowledgeDocumentId documentId) throws SQLException {
        UUID id = documentId.value();
        long lockKey = id.getMostSignificantBits() ^ id.getLeastSignificantBits() ^ 0x4255474759423034L;
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
            statement.setLong(1, lockKey);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Buggy Knowledge advisory lock returned no row");
            }
        }
    }

    private static Optional<BuggyKnowledgeAnnotationRevision> findCurrent(
            Connection connection,
            KnowledgeDocumentId documentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT r.document_id, r.annotation_revision, r.previous_revision, r.state, r.reason, r.evidence_ref, "
                        + "r.confidence, r.created_by, r.created_at, r.annotation_sha256 "
                        + "FROM assistant.buggy_knowledge_annotation_head h "
                        + "JOIN assistant.buggy_knowledge_annotation_revisions r "
                        + "ON r.assistant_id=h.assistant_id AND r.document_id=h.document_id "
                        + "AND r.annotation_revision=h.current_revision "
                        + "WHERE h.assistant_id='BUGGY' AND h.document_id=?")) {
            statement.setObject(1, documentId.value());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private static void insertRevision(Connection connection, BuggyKnowledgeAnnotationRevision revision) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assistant.buggy_knowledge_annotation_revisions(assistant_id, document_id, annotation_revision, "
                        + "previous_revision, state, reason, evidence_ref, confidence, created_by, created_at, annotation_sha256) "
                        + "VALUES ('BUGGY', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, revision.documentId().value());
            statement.setLong(2, revision.revision());
            if (revision.previousRevision() == null) statement.setObject(3, null);
            else statement.setLong(3, revision.previousRevision());
            statement.setString(4, revision.state().name());
            statement.setString(5, revision.reason());
            statement.setString(6, revision.evidenceRef());
            statement.setString(7, revision.confidence().name());
            statement.setString(8, revision.createdBy().value());
            statement.setTimestamp(9, Timestamp.from(revision.createdAt()));
            statement.setString(10, revision.sha256());
            requireOne(statement.executeUpdate(), "Buggy Knowledge annotation revision");
        }
    }

    private static void insertHead(Connection connection, BuggyKnowledgeAnnotationRevision revision) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assistant.buggy_knowledge_annotation_head(assistant_id, document_id, current_revision, updated_at) "
                        + "VALUES ('BUGGY', ?, ?, ?)")) {
            statement.setObject(1, revision.documentId().value());
            statement.setLong(2, revision.revision());
            statement.setTimestamp(3, Timestamp.from(revision.createdAt()));
            requireOne(statement.executeUpdate(), "Buggy Knowledge annotation head");
        }
    }

    private static void updateHead(
            Connection connection,
            long expectedCurrentRevision,
            BuggyKnowledgeAnnotationRevision revision) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE assistant.buggy_knowledge_annotation_head SET current_revision=?, updated_at=? "
                        + "WHERE assistant_id='BUGGY' AND document_id=? AND current_revision=?")) {
            statement.setLong(1, revision.revision());
            statement.setTimestamp(2, Timestamp.from(revision.createdAt()));
            statement.setObject(3, revision.documentId().value());
            statement.setLong(4, expectedCurrentRevision);
            requireOne(statement.executeUpdate(), "Buggy Knowledge annotation head update");
        }
    }

    private static BuggyKnowledgeAnnotationRevision map(ResultSet rs) throws SQLException {
        long previous = rs.getLong("previous_revision");
        Long previousRevision = rs.wasNull() ? null : previous;
        return new BuggyKnowledgeAnnotationRevision(
                new KnowledgeDocumentId(rs.getObject("document_id", UUID.class)),
                rs.getLong("annotation_revision"),
                previousRevision,
                BuggyKnowledgeState.valueOf(rs.getString("state")),
                rs.getString("reason"),
                rs.getString("evidence_ref"),
                BuggyKnowledgeConfidence.valueOf(rs.getString("confidence")),
                new ActorId(rs.getString("created_by")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("annotation_sha256"));
    }

    private static void requireOne(int count, String operation) {
        if (count != 1) throw new IllegalStateException(operation + " affected " + count + " rows");
    }
}
