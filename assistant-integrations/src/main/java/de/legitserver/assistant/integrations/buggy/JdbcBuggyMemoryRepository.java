package de.legitserver.assistant.integrations.buggy;

import de.legitserver.assistant.buggy.memory.BuggyMemoryCandidate;
import de.legitserver.assistant.buggy.memory.BuggyMemoryCandidateDecision;
import de.legitserver.assistant.buggy.memory.BuggyMemoryCandidateDecisionType;
import de.legitserver.assistant.buggy.memory.BuggyMemoryContentPolicy;
import de.legitserver.assistant.buggy.memory.BuggyMemoryId;
import de.legitserver.assistant.buggy.memory.BuggyMemoryProvenance;
import de.legitserver.assistant.buggy.memory.BuggyMemoryRetention;
import de.legitserver.assistant.buggy.memory.BuggyMemoryRevision;
import de.legitserver.assistant.buggy.memory.BuggyMemoryState;
import de.legitserver.assistant.buggy.memory.BuggyMemoryType;
import de.legitserver.assistant.buggy.repository.BuggyMemoryRepository;
import de.legitserver.assistant.core.actor.ActorId;
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

/** PostgreSQL G1.5 adapter for memory candidates, decisions, immutable revisions and mutable head pointers. */
public final class JdbcBuggyMemoryRepository implements BuggyMemoryRepository {
    private final TransactionRunner transactions;
    private final BuggyMemoryContentPolicy contentPolicy;

    public JdbcBuggyMemoryRepository(TransactionRunner transactions, BuggyMemoryContentPolicy contentPolicy) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.contentPolicy = Objects.requireNonNull(contentPolicy, "contentPolicy");
    }

    @Override
    public void appendCandidate(BuggyMemoryCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        validateCandidate(candidate);
        transactions.required(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO assistant.buggy_memory_candidates(candidate_id,assistant_id,memory_type,subject_actor_id,"
                            + "memory_key,content,provenance,source_ref,confidence_bps,retention_class,pinned,created_by,created_at) "
                            + "VALUES (?,'BUGGY',?,?,?,?,?,?,?,?,?,?,?)")) {
                int i = 1;
                statement.setObject(i++, candidate.candidateId());
                statement.setString(i++, candidate.type().name());
                statement.setString(i++, candidate.subjectActorId() == null ? null : candidate.subjectActorId().value());
                statement.setString(i++, candidate.key());
                statement.setString(i++, candidate.content());
                statement.setString(i++, candidate.provenance().name());
                statement.setString(i++, candidate.sourceRef());
                statement.setInt(i++, candidate.confidenceBps());
                statement.setString(i++, candidate.retention().name());
                statement.setBoolean(i++, candidate.pinned());
                statement.setString(i++, candidate.createdBy().value());
                statement.setTimestamp(i, Timestamp.from(candidate.createdAt()));
                requireOne(statement.executeUpdate(), "Memory candidate insert");
            }
            return null;
        });
    }

    @Override
    public Optional<BuggyMemoryCandidate> findCandidate(UUID candidateId) {
        Objects.requireNonNull(candidateId, "candidateId");
        return transactions.readOnly(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM assistant.buggy_memory_candidates WHERE assistant_id='BUGGY' AND candidate_id=?")) {
                statement.setObject(1, candidateId);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? Optional.of(mapCandidate(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public Optional<BuggyMemoryCandidateDecision> findCandidateDecision(UUID candidateId) {
        Objects.requireNonNull(candidateId, "candidateId");
        return transactions.readOnly(connection -> findDecision(connection, candidateId));
    }

    @Override
    public BuggyMemoryRevision acceptCandidate(
            BuggyMemoryCandidateDecision decision, BuggyMemoryRevision initialRevision) {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(initialRevision, "initialRevision");
        validateRevision(initialRevision);
        if (decision.decision() != BuggyMemoryCandidateDecisionType.ACCEPTED
                || !Objects.equals(decision.memoryId(), initialRevision.memoryId())
                || initialRevision.revision() != 1L) {
            throw new IllegalArgumentException("Invalid accepted memory candidate binding");
        }
        return transactions.required(connection -> {
            lockCandidate(connection, decision.candidateId());
            BuggyMemoryCandidate candidate = findCandidate(connection, decision.candidateId()).orElseThrow();
            if (findDecision(connection, decision.candidateId()).isPresent()) {
                throw new IllegalStateException("Memory candidate already decided");
            }
            requireMatchesCandidate(candidate, initialRevision);
            insertDecision(connection, decision);
            insertRevision(connection, initialRevision);
            insertHead(connection, initialRevision);
            return findCurrent(connection, initialRevision.memoryId()).orElseThrow();
        });
    }

    @Override
    public void rejectCandidate(BuggyMemoryCandidateDecision decision) {
        Objects.requireNonNull(decision, "decision");
        if (decision.decision() != BuggyMemoryCandidateDecisionType.REJECTED) {
            throw new IllegalArgumentException("rejectCandidate requires REJECTED decision");
        }
        contentPolicy.requirePersistableText(decision.reason(), "reason", 1024);
        transactions.required(connection -> {
            lockCandidate(connection, decision.candidateId());
            if (findCandidate(connection, decision.candidateId()).isEmpty()) {
                throw new IllegalArgumentException("Unknown memory candidate");
            }
            if (findDecision(connection, decision.candidateId()).isPresent()) {
                throw new IllegalStateException("Memory candidate already decided");
            }
            insertDecision(connection, decision);
            return null;
        });
    }

    @Override
    public Optional<BuggyMemoryRevision> findCurrent(BuggyMemoryId memoryId) {
        Objects.requireNonNull(memoryId, "memoryId");
        return transactions.readOnly(connection -> findCurrent(connection, memoryId));
    }

    @Override
    public List<BuggyMemoryRevision> history(BuggyMemoryId memoryId) {
        Objects.requireNonNull(memoryId, "memoryId");
        return transactions.readOnly(connection -> {
            ArrayList<BuggyMemoryRevision> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM assistant.buggy_memory_revisions "
                            + "WHERE assistant_id='BUGGY' AND memory_id=? ORDER BY memory_revision")) {
                statement.setObject(1, memoryId.value());
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) result.add(mapRevision(rs));
                }
            }
            return List.copyOf(result);
        });
    }

    @Override
    public List<BuggyMemoryRevision> listCurrent(BuggyMemoryType type, int limit) {
        Objects.requireNonNull(type, "type");
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be 1..500");
        return transactions.readOnly(connection -> {
            ArrayList<BuggyMemoryRevision> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT r.* FROM assistant.buggy_memory_heads h JOIN assistant.buggy_memory_revisions r "
                            + "ON r.assistant_id=h.assistant_id AND r.memory_id=h.memory_id AND r.memory_revision=h.current_revision "
                            + "WHERE h.assistant_id='BUGGY' AND r.memory_type=? AND r.memory_state='ACTIVE' "
                            + "ORDER BY r.pinned DESC,r.changed_at DESC,r.memory_id LIMIT ?")) {
                statement.setString(1, type.name());
                statement.setInt(2, limit);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) result.add(mapRevision(rs));
                }
            }
            return List.copyOf(result);
        });
    }

    @Override
    public BuggyMemoryRevision appendRevision(long expectedCurrentRevision, BuggyMemoryRevision revision) {
        Objects.requireNonNull(revision, "revision");
        if (expectedCurrentRevision < 1) throw new IllegalArgumentException("expectedCurrentRevision must be >= 1");
        validateRevision(revision);
        return transactions.required(connection -> {
            lockMemory(connection, revision.memoryId());
            BuggyMemoryRevision current = findCurrent(connection, revision.memoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown memory"));
            if (current.revision() != expectedCurrentRevision) throw new IllegalStateException("Stale memory revision");
            if (revision.revision() != expectedCurrentRevision + 1
                    || !Objects.equals(revision.previousRevision(), expectedCurrentRevision)) {
                throw new IllegalArgumentException("Memory revision does not continue active head");
            }
            requireStableIdentity(current, revision);
            insertRevision(connection, revision);
            updateHead(connection, expectedCurrentRevision, revision);
            return findCurrent(connection, revision.memoryId()).orElseThrow();
        });
    }

    private void validateCandidate(BuggyMemoryCandidate candidate) {
        contentPolicy.requirePersistableText(candidate.content(), "content", 16_384);
        contentPolicy.requirePersistableText(candidate.sourceRef(), "sourceRef", 1024);
    }

    private void validateRevision(BuggyMemoryRevision revision) {
        contentPolicy.requirePersistableText(revision.content(), "content", 16_384);
        contentPolicy.requirePersistableText(revision.sourceRef(), "sourceRef", 1024);
    }

    private static Optional<BuggyMemoryCandidate> findCandidate(Connection connection, UUID candidateId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM assistant.buggy_memory_candidates WHERE assistant_id='BUGGY' AND candidate_id=?")) {
            statement.setObject(1, candidateId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapCandidate(rs)) : Optional.empty();
            }
        }
    }

    private static Optional<BuggyMemoryCandidateDecision> findDecision(Connection connection, UUID candidateId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM assistant.buggy_memory_candidate_decisions WHERE assistant_id='BUGGY' AND candidate_id=?")) {
            statement.setObject(1, candidateId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapDecision(rs)) : Optional.empty();
            }
        }
    }

    private static Optional<BuggyMemoryRevision> findCurrent(Connection connection, BuggyMemoryId memoryId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT r.* FROM assistant.buggy_memory_heads h JOIN assistant.buggy_memory_revisions r "
                        + "ON r.assistant_id=h.assistant_id AND r.memory_id=h.memory_id AND r.memory_revision=h.current_revision "
                        + "WHERE h.assistant_id='BUGGY' AND h.memory_id=?")) {
            statement.setObject(1, memoryId.value());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRevision(rs)) : Optional.empty();
            }
        }
    }

    private static void insertDecision(Connection connection, BuggyMemoryCandidateDecision decision) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assistant.buggy_memory_candidate_decisions(candidate_id,assistant_id,decision,memory_id,reason,"
                        + "decided_by,decided_at) VALUES (?,'BUGGY',?,?,?,?,?)")) {
            statement.setObject(1, decision.candidateId());
            statement.setString(2, decision.decision().name());
            statement.setObject(3, decision.memoryId() == null ? null : decision.memoryId().value());
            statement.setString(4, decision.reason());
            statement.setString(5, decision.decidedBy().value());
            statement.setTimestamp(6, Timestamp.from(decision.decidedAt()));
            requireOne(statement.executeUpdate(), "Memory candidate decision insert");
        }
    }

    private static void insertRevision(Connection connection, BuggyMemoryRevision revision) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assistant.buggy_memory_revisions(assistant_id,memory_id,memory_revision,previous_revision,memory_type,"
                        + "subject_actor_id,memory_key,content,provenance,source_ref,confidence_bps,retention_class,pinned,memory_state,"
                        + "changed_by,changed_at) VALUES ('BUGGY',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            int i = 1;
            statement.setObject(i++, revision.memoryId().value());
            statement.setLong(i++, revision.revision());
            if (revision.previousRevision() == null) statement.setObject(i++, null);
            else statement.setLong(i++, revision.previousRevision());
            statement.setString(i++, revision.type().name());
            statement.setString(i++, revision.subjectActorId() == null ? null : revision.subjectActorId().value());
            statement.setString(i++, revision.key());
            statement.setString(i++, revision.content());
            statement.setString(i++, revision.provenance().name());
            statement.setString(i++, revision.sourceRef());
            statement.setInt(i++, revision.confidenceBps());
            statement.setString(i++, revision.retention().name());
            statement.setBoolean(i++, revision.pinned());
            statement.setString(i++, revision.state().name());
            statement.setString(i++, revision.changedBy().value());
            statement.setTimestamp(i, Timestamp.from(revision.changedAt()));
            requireOne(statement.executeUpdate(), "Memory revision insert");
        }
    }

    private static void insertHead(Connection connection, BuggyMemoryRevision revision) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assistant.buggy_memory_heads(assistant_id,memory_id,current_revision,created_at,updated_at) "
                        + "VALUES ('BUGGY',?,?,?,?)")) {
            statement.setObject(1, revision.memoryId().value());
            statement.setLong(2, revision.revision());
            statement.setTimestamp(3, Timestamp.from(revision.changedAt()));
            statement.setTimestamp(4, Timestamp.from(revision.changedAt()));
            requireOne(statement.executeUpdate(), "Memory head insert");
        }
    }

    private static void updateHead(
            Connection connection, long expectedCurrentRevision, BuggyMemoryRevision revision) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE assistant.buggy_memory_heads SET current_revision=?,updated_at=? "
                        + "WHERE assistant_id='BUGGY' AND memory_id=? AND current_revision=?")) {
            statement.setLong(1, revision.revision());
            statement.setTimestamp(2, Timestamp.from(revision.changedAt()));
            statement.setObject(3, revision.memoryId().value());
            statement.setLong(4, expectedCurrentRevision);
            requireOne(statement.executeUpdate(), "Memory head update");
        }
    }

    private static BuggyMemoryCandidate mapCandidate(ResultSet rs) throws SQLException {
        String subject = rs.getString("subject_actor_id");
        return new BuggyMemoryCandidate(
                rs.getObject("candidate_id", UUID.class),
                BuggyMemoryType.valueOf(rs.getString("memory_type")),
                subject == null ? null : new ActorId(subject),
                rs.getString("memory_key"),
                rs.getString("content"),
                BuggyMemoryProvenance.valueOf(rs.getString("provenance")),
                rs.getString("source_ref"),
                rs.getInt("confidence_bps"),
                BuggyMemoryRetention.valueOf(rs.getString("retention_class")),
                rs.getBoolean("pinned"),
                new ActorId(rs.getString("created_by")),
                rs.getTimestamp("created_at").toInstant());
    }

    private static BuggyMemoryCandidateDecision mapDecision(ResultSet rs) throws SQLException {
        UUID memoryId = rs.getObject("memory_id", UUID.class);
        return new BuggyMemoryCandidateDecision(
                rs.getObject("candidate_id", UUID.class),
                BuggyMemoryCandidateDecisionType.valueOf(rs.getString("decision")),
                memoryId == null ? null : new BuggyMemoryId(memoryId),
                rs.getString("reason"),
                new ActorId(rs.getString("decided_by")),
                rs.getTimestamp("decided_at").toInstant());
    }

    private static BuggyMemoryRevision mapRevision(ResultSet rs) throws SQLException {
        long previous = rs.getLong("previous_revision");
        Long previousRevision = rs.wasNull() ? null : previous;
        String subject = rs.getString("subject_actor_id");
        return new BuggyMemoryRevision(
                new BuggyMemoryId(rs.getObject("memory_id", UUID.class)),
                rs.getLong("memory_revision"),
                previousRevision,
                BuggyMemoryType.valueOf(rs.getString("memory_type")),
                subject == null ? null : new ActorId(subject),
                rs.getString("memory_key"),
                rs.getString("content"),
                BuggyMemoryProvenance.valueOf(rs.getString("provenance")),
                rs.getString("source_ref"),
                rs.getInt("confidence_bps"),
                BuggyMemoryRetention.valueOf(rs.getString("retention_class")),
                rs.getBoolean("pinned"),
                BuggyMemoryState.valueOf(rs.getString("memory_state")),
                new ActorId(rs.getString("changed_by")),
                rs.getTimestamp("changed_at").toInstant());
    }

    private static void requireMatchesCandidate(BuggyMemoryCandidate candidate, BuggyMemoryRevision revision) {
        if (candidate.type() != revision.type()
                || !Objects.equals(candidate.subjectActorId(), revision.subjectActorId())
                || !candidate.key().equals(revision.key())
                || !candidate.content().equals(revision.content())
                || candidate.provenance() != revision.provenance()
                || !candidate.sourceRef().equals(revision.sourceRef())
                || candidate.confidenceBps() != revision.confidenceBps()
                || candidate.retention() != revision.retention()
                || candidate.pinned() != revision.pinned()) {
            throw new IllegalArgumentException("Accepted memory revision differs from candidate");
        }
    }

    private static void requireStableIdentity(BuggyMemoryRevision current, BuggyMemoryRevision revision) {
        if (current.type() != revision.type()
                || !Objects.equals(current.subjectActorId(), revision.subjectActorId())
                || !current.key().equals(revision.key())) {
            throw new IllegalArgumentException("Memory type, subject and key are stable across revisions");
        }
    }

    private static void lockCandidate(Connection connection, UUID candidateId) throws SQLException {
        lock(connection, candidateId, 0x425547475943414EL);
    }

    private static void lockMemory(Connection connection, BuggyMemoryId memoryId) throws SQLException {
        lock(connection, memoryId.value(), 0x42554747594D454DL);
    }

    private static void lock(Connection connection, UUID id, long salt) throws SQLException {
        long key = id.getMostSignificantBits() ^ id.getLeastSignificantBits() ^ salt;
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
            statement.setLong(1, key);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Memory advisory lock returned no row");
            }
        }
    }

    private static void requireOne(int count, String operation) {
        if (count != 1) throw new IllegalStateException(operation + " affected " + count + " rows");
    }
}
