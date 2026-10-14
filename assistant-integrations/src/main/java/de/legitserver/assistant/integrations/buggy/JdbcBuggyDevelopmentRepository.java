package de.legitserver.assistant.integrations.buggy;

import de.legitserver.assistant.buggy.development.BuggyDevelopmentJournalType;
import de.legitserver.assistant.buggy.development.BuggyLearningCandidate;
import de.legitserver.assistant.buggy.development.BuggyNativeDevelopmentJournalEntry;
import de.legitserver.assistant.buggy.development.BuggyReflectionRecord;
import de.legitserver.assistant.buggy.development.BuggySelfCapabilityRevision;
import de.legitserver.assistant.buggy.memory.BuggyMemoryContentPolicy;
import de.legitserver.assistant.buggy.memory.BuggyMemoryId;
import de.legitserver.assistant.buggy.nativecore.BuggySelfCapabilityStatus;
import de.legitserver.assistant.buggy.repository.BuggyDevelopmentRepository;
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

/** PostgreSQL G1.5 adapter for reflections, learning candidates, capability revisions and native journal. */
public final class JdbcBuggyDevelopmentRepository implements BuggyDevelopmentRepository {
    private final TransactionRunner transactions;
    private final BuggyMemoryContentPolicy contentPolicy;

    public JdbcBuggyDevelopmentRepository(TransactionRunner transactions, BuggyMemoryContentPolicy contentPolicy) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.contentPolicy = Objects.requireNonNull(contentPolicy, "contentPolicy");
    }

    @Override
    public BuggyReflectionRecord appendReflection(
            BuggyReflectionRecord reflection, BuggyNativeDevelopmentJournalEntry journalEntry) {
        Objects.requireNonNull(reflection, "reflection");
        Objects.requireNonNull(journalEntry, "journalEntry");
        validateReflection(reflection);
        validateJournal(journalEntry);
        if (journalEntry.type() != BuggyDevelopmentJournalType.REFLECTION_RECORDED) {
            throw new IllegalArgumentException("Reflection requires REFLECTION_RECORDED journal entry");
        }
        return transactions.required(connection -> {
            insertReflection(connection, reflection);
            insertReflectionRefs(connection, reflection);
            insertJournal(connection, journalEntry);
            return findReflection(connection, reflection.reflectionId()).orElseThrow();
        });
    }

    @Override
    public Optional<BuggyReflectionRecord> findReflection(UUID reflectionId) {
        Objects.requireNonNull(reflectionId, "reflectionId");
        return transactions.readOnly(connection -> findReflection(connection, reflectionId));
    }

    @Override
    public BuggyLearningCandidate appendLearningCandidate(
            BuggyLearningCandidate candidate, BuggyNativeDevelopmentJournalEntry journalEntry) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(journalEntry, "journalEntry");
        contentPolicy.requirePersistableText(candidate.proposal(), "proposal", 4096);
        validateJournal(journalEntry);
        if (journalEntry.type() != BuggyDevelopmentJournalType.LEARNING_CANDIDATE_CREATED) {
            throw new IllegalArgumentException("Learning candidate requires LEARNING_CANDIDATE_CREATED journal entry");
        }
        return transactions.required(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO assistant.buggy_learning_candidates(candidate_id,assistant_id,reflection_id,rule_key,proposal,"
                            + "confidence_bps,created_by,created_at) VALUES (?,'BUGGY',?,?,?,?,?,?)")) {
                statement.setObject(1, candidate.candidateId());
                statement.setObject(2, candidate.reflectionId());
                statement.setString(3, candidate.ruleKey());
                statement.setString(4, candidate.proposal());
                statement.setInt(5, candidate.confidenceBps());
                statement.setString(6, candidate.createdBy().value());
                statement.setTimestamp(7, Timestamp.from(candidate.createdAt()));
                requireOne(statement.executeUpdate(), "Learning candidate insert");
            }
            insertJournal(connection, journalEntry);
            return findLearningCandidate(connection, candidate.candidateId()).orElseThrow();
        });
    }

    @Override
    public Optional<BuggyLearningCandidate> findLearningCandidate(UUID candidateId) {
        Objects.requireNonNull(candidateId, "candidateId");
        return transactions.readOnly(connection -> findLearningCandidate(connection, candidateId));
    }

    @Override
    public List<BuggyLearningCandidate> recentLearningCandidates(int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be 1..500");
        return transactions.readOnly(connection -> {
            ArrayList<BuggyLearningCandidate> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM assistant.buggy_learning_candidates WHERE assistant_id='BUGGY' "
                            + "ORDER BY created_at DESC,candidate_id LIMIT ?")) {
                statement.setInt(1, limit);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) result.add(mapLearningCandidate(rs));
                }
            }
            return List.copyOf(result);
        });
    }

    @Override
    public Optional<BuggySelfCapabilityRevision> findCurrentCapabilityRevision(String capabilityId) {
        Objects.requireNonNull(capabilityId, "capabilityId");
        return transactions.readOnly(connection -> findCurrentCapabilityRevision(connection, capabilityId));
    }

    @Override
    public List<BuggySelfCapabilityRevision> capabilityHistory(String capabilityId) {
        Objects.requireNonNull(capabilityId, "capabilityId");
        return transactions.readOnly(connection -> {
            ArrayList<BuggySelfCapabilityRevision> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM assistant.buggy_self_capability_revisions "
                            + "WHERE assistant_id='BUGGY' AND capability_id=? ORDER BY capability_revision")) {
                statement.setString(1, capabilityId);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) result.add(mapCapabilityRevision(rs));
                }
            }
            return List.copyOf(result);
        });
    }

    @Override
    public BuggySelfCapabilityRevision appendCapabilityRevision(
            long expectedCurrentRevision,
            BuggySelfCapabilityRevision revision,
            BuggyNativeDevelopmentJournalEntry journalEntry) {
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(journalEntry, "journalEntry");
        if (expectedCurrentRevision < 0) throw new IllegalArgumentException("expectedCurrentRevision must be >= 0");
        contentPolicy.requirePersistableText(revision.evidenceRef(), "evidenceRef", 512);
        if (revision.limitation() != null) {
            contentPolicy.requirePersistableText(revision.limitation(), "limitation", 320);
        }
        validateJournal(journalEntry);
        if (journalEntry.type() != BuggyDevelopmentJournalType.SELF_CAPABILITY_REVISED) {
            throw new IllegalArgumentException("Capability revision requires SELF_CAPABILITY_REVISED journal entry");
        }
        return transactions.required(connection -> {
            lockCapability(connection, revision.capabilityId());
            long actual = findCurrentCapabilityRevision(connection, revision.capabilityId())
                    .map(BuggySelfCapabilityRevision::revision)
                    .orElse(0L);
            if (actual != expectedCurrentRevision) throw new IllegalStateException("Stale self capability revision");
            if (revision.revision() != expectedCurrentRevision + 1) {
                throw new IllegalArgumentException("Capability revision does not continue active head");
            }
            insertCapabilityRevision(connection, revision);
            if (expectedCurrentRevision == 0) insertCapabilityHead(connection, revision);
            else updateCapabilityHead(connection, expectedCurrentRevision, revision);
            insertJournal(connection, journalEntry);
            return findCurrentCapabilityRevision(connection, revision.capabilityId()).orElseThrow();
        });
    }

    @Override
    public List<BuggyNativeDevelopmentJournalEntry> journal(int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be 1..500");
        return transactions.readOnly(connection -> {
            ArrayList<BuggyNativeDevelopmentJournalEntry> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM assistant.buggy_native_development_journal WHERE assistant_id='BUGGY' "
                            + "ORDER BY created_at DESC,entry_id LIMIT ?")) {
                statement.setInt(1, limit);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) result.add(mapJournal(connection, rs));
                }
            }
            return List.copyOf(result);
        });
    }

    private void validateReflection(BuggyReflectionRecord reflection) {
        contentPolicy.requirePersistableText(reflection.summary(), "summary", 4096);
        contentPolicy.requirePersistableText(reflection.lesson(), "lesson", 4096);
    }

    private void validateJournal(BuggyNativeDevelopmentJournalEntry journalEntry) {
        contentPolicy.requirePersistableText(journalEntry.summary(), "summary", 2048);
        for (String ref : journalEntry.sourceRefs()) {
            contentPolicy.requirePersistableText(ref, "sourceRef", 512);
        }
    }

    private static void insertReflection(Connection connection, BuggyReflectionRecord reflection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assistant.buggy_reflections(reflection_id,assistant_id,summary,lesson,confidence_bps,created_by,created_at) "
                        + "VALUES (?,'BUGGY',?,?,?,?,?)")) {
            statement.setObject(1, reflection.reflectionId());
            statement.setString(2, reflection.summary());
            statement.setString(3, reflection.lesson());
            statement.setInt(4, reflection.confidenceBps());
            statement.setString(5, reflection.createdBy().value());
            statement.setTimestamp(6, Timestamp.from(reflection.createdAt()));
            requireOne(statement.executeUpdate(), "Reflection insert");
        }
    }

    private static void insertReflectionRefs(Connection connection, BuggyReflectionRecord reflection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assistant.buggy_reflection_memory_refs(reflection_id,assistant_id,memory_ordinal,memory_id) "
                        + "VALUES (?,'BUGGY',?,?)")) {
            int ordinal = 0;
            for (BuggyMemoryId memoryId : reflection.memoryRefs()) {
                statement.setObject(1, reflection.reflectionId());
                statement.setInt(2, ordinal++);
                statement.setObject(3, memoryId.value());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static Optional<BuggyReflectionRecord> findReflection(Connection connection, UUID reflectionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM assistant.buggy_reflections WHERE assistant_id='BUGGY' AND reflection_id=?")) {
            statement.setObject(1, reflectionId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                ArrayList<BuggyMemoryId> refs = new ArrayList<>();
                try (PreparedStatement refStatement = connection.prepareStatement(
                        "SELECT memory_id FROM assistant.buggy_reflection_memory_refs "
                                + "WHERE assistant_id='BUGGY' AND reflection_id=? ORDER BY memory_ordinal")) {
                    refStatement.setObject(1, reflectionId);
                    try (ResultSet refRs = refStatement.executeQuery()) {
                        while (refRs.next()) refs.add(new BuggyMemoryId(refRs.getObject("memory_id", UUID.class)));
                    }
                }
                return Optional.of(new BuggyReflectionRecord(
                        rs.getObject("reflection_id", UUID.class),
                        refs,
                        rs.getString("summary"),
                        rs.getString("lesson"),
                        rs.getInt("confidence_bps"),
                        new ActorId(rs.getString("created_by")),
                        rs.getTimestamp("created_at").toInstant()));
            }
        }
    }

    private static Optional<BuggyLearningCandidate> findLearningCandidate(Connection connection, UUID candidateId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM assistant.buggy_learning_candidates WHERE assistant_id='BUGGY' AND candidate_id=?")) {
            statement.setObject(1, candidateId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapLearningCandidate(rs)) : Optional.empty();
            }
        }
    }

    private static BuggyLearningCandidate mapLearningCandidate(ResultSet rs) throws SQLException {
        return new BuggyLearningCandidate(
                rs.getObject("candidate_id", UUID.class),
                rs.getObject("reflection_id", UUID.class),
                rs.getString("rule_key"),
                rs.getString("proposal"),
                rs.getInt("confidence_bps"),
                new ActorId(rs.getString("created_by")),
                rs.getTimestamp("created_at").toInstant());
    }

    private static Optional<BuggySelfCapabilityRevision> findCurrentCapabilityRevision(
            Connection connection, String capabilityId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT r.* FROM assistant.buggy_self_capability_heads h JOIN assistant.buggy_self_capability_revisions r "
                        + "ON r.assistant_id=h.assistant_id AND r.capability_id=h.capability_id "
                        + "AND r.capability_revision=h.current_revision "
                        + "WHERE h.assistant_id='BUGGY' AND h.capability_id=?")) {
            statement.setString(1, capabilityId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapCapabilityRevision(rs)) : Optional.empty();
            }
        }
    }

    private static void insertCapabilityRevision(Connection connection, BuggySelfCapabilityRevision revision)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assistant.buggy_self_capability_revisions(assistant_id,capability_id,capability_revision,"
                        + "previous_revision,status,confidence_bps,limitation,evidence_ref,changed_by,changed_at) "
                        + "VALUES ('BUGGY',?,?,?,?,?,?,?,?,?)")) {
            int i = 1;
            statement.setString(i++, revision.capabilityId());
            statement.setLong(i++, revision.revision());
            if (revision.previousRevision() == null) statement.setObject(i++, null);
            else statement.setLong(i++, revision.previousRevision());
            statement.setString(i++, revision.status().name());
            statement.setInt(i++, revision.confidenceBps());
            statement.setString(i++, revision.limitation());
            statement.setString(i++, revision.evidenceRef());
            statement.setString(i++, revision.changedBy().value());
            statement.setTimestamp(i, Timestamp.from(revision.changedAt()));
            requireOne(statement.executeUpdate(), "Self capability revision insert");
        }
    }

    private static void insertCapabilityHead(Connection connection, BuggySelfCapabilityRevision revision)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assistant.buggy_self_capability_heads(assistant_id,capability_id,current_revision,created_at,updated_at) "
                        + "VALUES ('BUGGY',?,?,?,?)")) {
            statement.setString(1, revision.capabilityId());
            statement.setLong(2, revision.revision());
            statement.setTimestamp(3, Timestamp.from(revision.changedAt()));
            statement.setTimestamp(4, Timestamp.from(revision.changedAt()));
            requireOne(statement.executeUpdate(), "Self capability head insert");
        }
    }

    private static void updateCapabilityHead(
            Connection connection, long expectedCurrentRevision, BuggySelfCapabilityRevision revision) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE assistant.buggy_self_capability_heads SET current_revision=?,updated_at=? "
                        + "WHERE assistant_id='BUGGY' AND capability_id=? AND current_revision=?")) {
            statement.setLong(1, revision.revision());
            statement.setTimestamp(2, Timestamp.from(revision.changedAt()));
            statement.setString(3, revision.capabilityId());
            statement.setLong(4, expectedCurrentRevision);
            requireOne(statement.executeUpdate(), "Self capability head update");
        }
    }

    private static BuggySelfCapabilityRevision mapCapabilityRevision(ResultSet rs) throws SQLException {
        long previous = rs.getLong("previous_revision");
        Long previousRevision = rs.wasNull() ? null : previous;
        return new BuggySelfCapabilityRevision(
                rs.getString("capability_id"),
                rs.getLong("capability_revision"),
                previousRevision,
                BuggySelfCapabilityStatus.valueOf(rs.getString("status")),
                rs.getInt("confidence_bps"),
                rs.getString("limitation"),
                rs.getString("evidence_ref"),
                new ActorId(rs.getString("changed_by")),
                rs.getTimestamp("changed_at").toInstant());
    }

    private static void insertJournal(Connection connection, BuggyNativeDevelopmentJournalEntry journal) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assistant.buggy_native_development_journal(entry_id,assistant_id,entry_type,summary,created_by,created_at) "
                        + "VALUES (?,'BUGGY',?,?,?,?)")) {
            statement.setObject(1, journal.entryId());
            statement.setString(2, journal.type().name());
            statement.setString(3, journal.summary());
            statement.setString(4, journal.createdBy().value());
            statement.setTimestamp(5, Timestamp.from(journal.createdAt()));
            requireOne(statement.executeUpdate(), "Development journal insert");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assistant.buggy_native_development_journal_refs(entry_id,ref_ordinal,source_ref) VALUES (?,?,?)")) {
            int ordinal = 0;
            for (String ref : journal.sourceRefs()) {
                statement.setObject(1, journal.entryId());
                statement.setInt(2, ordinal++);
                statement.setString(3, ref);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static BuggyNativeDevelopmentJournalEntry mapJournal(Connection connection, ResultSet rs) throws SQLException {
        UUID entryId = rs.getObject("entry_id", UUID.class);
        ArrayList<String> refs = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT source_ref FROM assistant.buggy_native_development_journal_refs "
                        + "WHERE entry_id=? ORDER BY ref_ordinal")) {
            statement.setObject(1, entryId);
            try (ResultSet refRs = statement.executeQuery()) {
                while (refRs.next()) refs.add(refRs.getString("source_ref"));
            }
        }
        return new BuggyNativeDevelopmentJournalEntry(
                entryId,
                BuggyDevelopmentJournalType.valueOf(rs.getString("entry_type")),
                rs.getString("summary"),
                refs,
                new ActorId(rs.getString("created_by")),
                rs.getTimestamp("created_at").toInstant());
    }

    private static void lockCapability(Connection connection, String capabilityId) throws SQLException {
        long key = ((long) capabilityId.hashCode() << 32) ^ 0x4255474759434150L;
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
            statement.setLong(1, key);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Capability advisory lock returned no row");
            }
        }
    }

    private static void requireOne(int count, String operation) {
        if (count != 1) throw new IllegalStateException(operation + " affected " + count + " rows");
    }
}
