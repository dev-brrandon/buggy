package de.legitserver.assistant.integrations.buggy;

import de.legitserver.assistant.buggy.personality.BuggyPersonalityContentPolicy;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityDomain;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityEvidenceRef;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityEvidenceType;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityObservation;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityObservationOrigin;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityRevision;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityRevisionOrigin;
import de.legitserver.assistant.buggy.personality.BuggyPersonalitySeed;
import de.legitserver.assistant.buggy.personality.BuggyPersonalitySnapshot;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityStateEntry;
import de.legitserver.assistant.buggy.repository.BuggyPersonalityRepository;
import de.legitserver.assistant.core.actor.ActorId;
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

/** PostgreSQL adapter for B2 Buggy Personality Seed, observations, revisions and append-only rollback history. */
public final class JdbcBuggyPersonalityRepository implements BuggyPersonalityRepository {
    private static final long PERSONALITY_ADVISORY_LOCK = 0x4255474759423032L;
    private final TransactionRunner transactions;
    private final BuggyPersonalityContentPolicy contentPolicy;

    public JdbcBuggyPersonalityRepository(
            TransactionRunner transactions, BuggyPersonalityContentPolicy contentPolicy) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.contentPolicy = Objects.requireNonNull(contentPolicy, "contentPolicy");
    }

    @Override
    public Optional<BuggyPersonalitySnapshot> find() {
        return transactions.readOnly(JdbcBuggyPersonalityRepository::find);
    }

    @Override
    public BuggyPersonalitySnapshot initialize(
            BuggyPersonalitySeed seed, BuggyPersonalityRevision initialRevision) {
        Objects.requireNonNull(seed, "seed");
        Objects.requireNonNull(initialRevision, "initialRevision");
        if (initialRevision.revision() != 1 || initialRevision.origin() != BuggyPersonalityRevisionOrigin.SEED) {
            throw new IllegalArgumentException("Personality initialization requires the canonical seed revision");
        }
        validateRevisionContent(initialRevision);
        return transactions.required(connection -> {
            lockPersonality(connection);
            requireActivePersonalityProfile(connection, seed.profileId());
            Optional<BuggyPersonalitySnapshot> existing = find(connection);
            if (existing.isPresent()) {
                BuggyPersonalitySnapshot snapshot = existing.orElseThrow();
                if (!snapshot.seed().sha256().equals(seed.sha256())) {
                    throw new IllegalStateException("Buggy Personality already initialized with a different seed");
                }
                return snapshot;
            }
            insertSeed(connection, seed, initialRevision.createdAt());
            insertSeedEntries(connection, seed.entries());
            insertRevision(connection, initialRevision);
            insertRevisionChanges(connection, initialRevision);
            insertRevisionEvidence(connection, initialRevision);
            insertStateEntries(connection, initialRevision);
            insertHead(connection, initialRevision);
            return find(connection).orElseThrow(() -> new IllegalStateException("Buggy Personality insert was not readable"));
        });
    }

    @Override
    public Optional<BuggyPersonalityRevision> findRevision(long revision) {
        if (revision < 1) throw new IllegalArgumentException("revision must be >= 1");
        return transactions.readOnly(connection -> findRevision(connection, revision));
    }

    @Override
    public Optional<BuggyPersonalityObservation> findObservation(String observationId) {
        Objects.requireNonNull(observationId, "observationId");
        return transactions.readOnly(connection -> findObservation(connection, observationId));
    }

    @Override
    public List<BuggyPersonalityRevision> history() {
        return transactions.readOnly(connection -> {
            List<Long> revisions = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT personality_revision FROM assistant.buggy_personality_revisions "
                            + "WHERE assistant_id='BUGGY' ORDER BY personality_revision ASC");
                    ResultSet rs = statement.executeQuery()) {
                while (rs.next()) revisions.add(rs.getLong(1));
            }
            List<BuggyPersonalityRevision> result = new ArrayList<>();
            for (Long revision : revisions) {
                result.add(findRevision(connection, revision)
                        .orElseThrow(() -> new IllegalStateException("Personality revision disappeared during history load")));
            }
            return List.copyOf(result);
        });
    }

    @Override
    public List<BuggyPersonalityObservation> observations() {
        return transactions.readOnly(connection -> {
            List<String> ids = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT observation_id FROM assistant.buggy_personality_observations "
                            + "WHERE assistant_id='BUGGY' ORDER BY created_at ASC, observation_id ASC");
                    ResultSet rs = statement.executeQuery()) {
                while (rs.next()) ids.add(rs.getString(1));
            }
            List<BuggyPersonalityObservation> result = new ArrayList<>();
            for (String id : ids) {
                result.add(findObservation(connection, id)
                        .orElseThrow(() -> new IllegalStateException("Personality observation disappeared during history load")));
            }
            return List.copyOf(result);
        });
    }

    @Override
    public BuggyPersonalityObservation appendObservation(BuggyPersonalityObservation observation) {
        Objects.requireNonNull(observation, "observation");
        validateObservationContent(observation);
        return transactions.required(connection -> {
            lockPersonality(connection);
            requirePersonalityInitialized(connection);
            Optional<BuggyPersonalityObservation> existing = findObservation(connection, observation.observationId());
            if (existing.isPresent()) {
                if (!existing.orElseThrow().sha256().equals(observation.sha256())) {
                    throw new IllegalStateException("Personality observation id already exists with different content");
                }
                return existing.orElseThrow();
            }
            insertObservation(connection, observation);
            insertEvidence(connection, observation);
            return findObservation(connection, observation.observationId())
                    .orElseThrow(() -> new IllegalStateException("Personality observation insert was not readable"));
        });
    }

    @Override
    public BuggyPersonalitySnapshot appendRevision(
            long expectedCurrentRevision, BuggyPersonalityRevision revision) {
        Objects.requireNonNull(revision, "revision");
        validateRevisionContent(revision);
        return transactions.required(connection -> {
            lockPersonality(connection);
            BuggyPersonalitySnapshot current = find(connection)
                    .orElseThrow(() -> new IllegalStateException("Buggy Personality is not initialized"));
            if (current.current().revision() != expectedCurrentRevision) {
                throw new IllegalStateException("Stale Buggy Personality revision");
            }
            if (revision.previousRevision() == null || revision.previousRevision() != expectedCurrentRevision) {
                throw new IllegalArgumentException("Personality revision does not continue the active head");
            }
            insertRevision(connection, revision);
            insertRevisionChanges(connection, revision);
            insertRevisionEvidence(connection, revision);
            insertStateEntries(connection, revision);
            updateHead(connection, expectedCurrentRevision, revision);
            return find(connection).orElseThrow(() -> new IllegalStateException("Personality revision was not readable"));
        });
    }

    private void validateObservationContent(BuggyPersonalityObservation observation) {
        contentPolicy.requirePersistableText(observation.observedValue(), "observedValue", 512);
        contentPolicy.requirePersistableText(observation.note(), "note", 512);
    }

    private void validateRevisionContent(BuggyPersonalityRevision revision) {
        contentPolicy.requirePersistableText(revision.reason(), "reason", 512);
        for (BuggyPersonalityStateEntry entry : revision.entries()) {
            contentPolicy.requirePersistableText(entry.value(), "personalityStateValue", 512);
        }
    }

    private static void lockPersonality(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
            statement.setLong(1, PERSONALITY_ADVISORY_LOCK);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Buggy Personality advisory lock returned no row");
            }
        }
    }

    private static void requireActivePersonalityProfile(Connection connection, String profileId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT cfg.personality_profile_id "
                        + "FROM assistant.buggy_identity_head h "
                        + "JOIN assistant.buggy_config_revisions cfg "
                        + "ON cfg.assistant_id=h.assistant_id AND cfg.config_revision=h.active_config_revision "
                        + "WHERE h.assistant_id='BUGGY'");
                ResultSet rs = statement.executeQuery()) {
            if (!rs.next()) throw new IllegalStateException("Buggy identity is not provisioned");
            if (!profileId.equals(rs.getString(1))) {
                throw new IllegalStateException("Buggy personality profile is not active in the identity manifest");
            }
        }
    }

    private static void requirePersonalityInitialized(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM assistant.buggy_personality_head WHERE assistant_id='BUGGY'");
                ResultSet rs = statement.executeQuery()) {
            if (!rs.next()) throw new IllegalStateException("Buggy Personality is not initialized");
        }
    }

    private static Optional<BuggyPersonalitySnapshot> find(Connection connection) throws SQLException {
        Long currentRevision = null;
        Instant updatedAt = null;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT current_revision, updated_at FROM assistant.buggy_personality_head WHERE assistant_id='BUGGY'");
                ResultSet rs = statement.executeQuery()) {
            if (!rs.next()) return Optional.empty();
            currentRevision = rs.getLong("current_revision");
            updatedAt = rs.getTimestamp("updated_at").toInstant();
            if (rs.next()) throw new IllegalStateException("Multiple Buggy Personality heads found");
        }
        BuggyPersonalitySeed seed = loadSeed(connection);
        BuggyPersonalityRevision current = findRevision(connection, currentRevision)
                .orElseThrow(() -> new IllegalStateException("Active Buggy Personality revision is missing"));
        return Optional.of(new BuggyPersonalitySnapshot(seed, current, updatedAt));
    }

    private static BuggyPersonalitySeed loadSeed(Connection connection) throws SQLException {
        String profileId;
        int seedVersion;
        String sha;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT profile_id, seed_version, seed_sha256 FROM assistant.buggy_personality_seed WHERE assistant_id='BUGGY'");
                ResultSet rs = statement.executeQuery()) {
            if (!rs.next()) throw new IllegalStateException("Buggy Personality seed is missing");
            profileId = rs.getString("profile_id");
            seedVersion = rs.getInt("seed_version");
            sha = rs.getString("seed_sha256");
        }
        List<BuggyPersonalityStateEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT domain, attribute_key, attribute_value, confidence_bps "
                        + "FROM assistant.buggy_personality_seed_entries WHERE assistant_id='BUGGY' ORDER BY entry_ordinal ASC");
                ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                entries.add(new BuggyPersonalityStateEntry(
                        BuggyPersonalityDomain.valueOf(rs.getString("domain")),
                        rs.getString("attribute_key"),
                        rs.getString("attribute_value"),
                        rs.getInt("confidence_bps"),
                        null));
            }
        }
        return new BuggyPersonalitySeed(profileId, seedVersion, entries, sha);
    }

    private static Optional<BuggyPersonalityRevision> findRevision(
            Connection connection, long revision) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT previous_revision, origin, reason, confidence_bps, rollback_target_revision, created_by, created_at, state_sha256 "
                        + "FROM assistant.buggy_personality_revisions "
                        + "WHERE assistant_id='BUGGY' AND personality_revision=?")) {
            statement.setLong(1, revision);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Long previous = nullableLong(rs, "previous_revision");
                Long rollback = nullableLong(rs, "rollback_target_revision");
                BuggyPersonalityRevisionOrigin origin = BuggyPersonalityRevisionOrigin.valueOf(rs.getString("origin"));
                String reason = rs.getString("reason");
                int confidence = rs.getInt("confidence_bps");
                ActorId createdBy = new ActorId(rs.getString("created_by"));
                Instant createdAt = rs.getTimestamp("created_at").toInstant();
                String sha = rs.getString("state_sha256");
                List<String> changedAttributes = loadRevisionChanges(connection, revision);
                List<String> evidenceObservationIds = loadRevisionEvidence(connection, revision);
                List<BuggyPersonalityStateEntry> entries = loadStateEntries(connection, revision);
                return Optional.of(new BuggyPersonalityRevision(
                        revision,
                        previous,
                        origin,
                        reason,
                        confidence,
                        rollback,
                        changedAttributes,
                        evidenceObservationIds,
                        entries,
                        createdBy,
                        createdAt,
                        sha));
            }
        }
    }

    private static List<String> loadRevisionChanges(Connection connection, long revision) throws SQLException {
        List<String> changes = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT attribute_identity_key FROM assistant.buggy_personality_revision_changes "
                        + "WHERE assistant_id='BUGGY' AND personality_revision=? ORDER BY change_ordinal ASC")) {
            statement.setLong(1, revision);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) changes.add(rs.getString(1));
            }
        }
        return List.copyOf(changes);
    }

    private static List<String> loadRevisionEvidence(Connection connection, long revision) throws SQLException {
        List<String> evidence = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT observation_id FROM assistant.buggy_personality_revision_evidence "
                        + "WHERE assistant_id='BUGGY' AND personality_revision=? ORDER BY evidence_ordinal ASC")) {
            statement.setLong(1, revision);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) evidence.add(rs.getString(1));
            }
        }
        return List.copyOf(evidence);
    }

    private static List<BuggyPersonalityStateEntry> loadStateEntries(
            Connection connection, long revision) throws SQLException {
        List<BuggyPersonalityStateEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT domain, attribute_key, attribute_value, confidence_bps, source_observation_id "
                        + "FROM assistant.buggy_personality_state_entries "
                        + "WHERE assistant_id='BUGGY' AND personality_revision=? ORDER BY domain, attribute_key")) {
            statement.setLong(1, revision);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    entries.add(new BuggyPersonalityStateEntry(
                            BuggyPersonalityDomain.valueOf(rs.getString("domain")),
                            rs.getString("attribute_key"),
                            rs.getString("attribute_value"),
                            rs.getInt("confidence_bps"),
                            rs.getString("source_observation_id")));
                }
            }
        }
        return List.copyOf(entries);
    }

    private static Optional<BuggyPersonalityObservation> findObservation(
            Connection connection, String observationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT domain, attribute_key, observed_value, origin, confidence_bps, note, created_by, created_at, observation_sha256 "
                        + "FROM assistant.buggy_personality_observations "
                        + "WHERE assistant_id='BUGGY' AND observation_id=?")) {
            statement.setString(1, observationId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                BuggyPersonalityDomain domain = BuggyPersonalityDomain.valueOf(rs.getString("domain"));
                String key = rs.getString("attribute_key");
                String value = rs.getString("observed_value");
                BuggyPersonalityObservationOrigin origin = BuggyPersonalityObservationOrigin.valueOf(rs.getString("origin"));
                int confidence = rs.getInt("confidence_bps");
                String note = rs.getString("note");
                ActorId createdBy = new ActorId(rs.getString("created_by"));
                Instant createdAt = rs.getTimestamp("created_at").toInstant();
                String sha = rs.getString("observation_sha256");
                List<BuggyPersonalityEvidenceRef> evidence = loadEvidence(connection, observationId);
                return Optional.of(new BuggyPersonalityObservation(
                        observationId,
                        domain,
                        key,
                        value,
                        origin,
                        confidence,
                        evidence,
                        note,
                        createdBy,
                        createdAt,
                        sha));
            }
        }
    }

    private static List<BuggyPersonalityEvidenceRef> loadEvidence(
            Connection connection, String observationId) throws SQLException {
        List<BuggyPersonalityEvidenceRef> evidence = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT evidence_type, reference_id, observed_at "
                        + "FROM assistant.buggy_personality_observation_evidence "
                        + "WHERE assistant_id='BUGGY' AND observation_id=? ORDER BY evidence_ordinal ASC")) {
            statement.setString(1, observationId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    evidence.add(new BuggyPersonalityEvidenceRef(
                            BuggyPersonalityEvidenceType.valueOf(rs.getString("evidence_type")),
                            rs.getString("reference_id"),
                            rs.getTimestamp("observed_at").toInstant()));
                }
            }
        }
        return List.copyOf(evidence);
    }

    private static void insertSeed(
            Connection connection, BuggyPersonalitySeed seed, Instant createdAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assistant.buggy_personality_seed(assistant_id, profile_id, seed_version, seed_sha256, created_at) "
                        + "VALUES ('BUGGY', ?, ?, ?, ?)")) {
            statement.setString(1, seed.profileId());
            statement.setInt(2, seed.seedVersion());
            statement.setString(3, seed.sha256());
            statement.setTimestamp(4, Timestamp.from(createdAt));
            requireOne(statement.executeUpdate(), "Buggy Personality seed");
        }
    }

    private static void insertSeedEntries(
            Connection connection, List<BuggyPersonalityStateEntry> entries) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assistant.buggy_personality_seed_entries(assistant_id, entry_ordinal, domain, attribute_key, attribute_value, confidence_bps) "
                        + "VALUES ('BUGGY', ?, ?, ?, ?, ?)")) {
            for (int i = 0; i < entries.size(); i++) {
                BuggyPersonalityStateEntry entry = entries.get(i);
                statement.setInt(1, i);
                statement.setString(2, entry.domain().name());
                statement.setString(3, entry.attributeKey());
                statement.setString(4, entry.value());
                statement.setInt(5, entry.confidenceBps());
                statement.addBatch();
            }
            requireBatch(entries.size(), statement.executeBatch(), "Buggy Personality seed entries");
        }
    }

    private static void insertObservation(
            Connection connection, BuggyPersonalityObservation observation) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assistant.buggy_personality_observations(assistant_id, observation_id, domain, attribute_key, observed_value, origin, confidence_bps, note, created_by, created_at, observation_sha256) "
                        + "VALUES ('BUGGY', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, observation.observationId());
            statement.setString(2, observation.domain().name());
            statement.setString(3, observation.attributeKey());
            statement.setString(4, observation.observedValue());
            statement.setString(5, observation.origin().name());
            statement.setInt(6, observation.confidenceBps());
            statement.setString(7, observation.note());
            statement.setString(8, observation.createdBy().value());
            statement.setTimestamp(9, Timestamp.from(observation.createdAt()));
            statement.setString(10, observation.sha256());
            requireOne(statement.executeUpdate(), "Buggy Personality observation");
        }
    }

    private static void insertEvidence(
            Connection connection, BuggyPersonalityObservation observation) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assistant.buggy_personality_observation_evidence(assistant_id, observation_id, evidence_ordinal, evidence_type, reference_id, observed_at) "
                        + "VALUES ('BUGGY', ?, ?, ?, ?, ?)")) {
            for (int i = 0; i < observation.evidence().size(); i++) {
                BuggyPersonalityEvidenceRef evidence = observation.evidence().get(i);
                statement.setString(1, observation.observationId());
                statement.setInt(2, i);
                statement.setString(3, evidence.type().name());
                statement.setString(4, evidence.referenceId());
                statement.setTimestamp(5, Timestamp.from(evidence.observedAt()));
                statement.addBatch();
            }
            requireBatch(observation.evidence().size(), statement.executeBatch(), "Buggy Personality evidence");
        }
    }

    private static void insertRevision(
            Connection connection, BuggyPersonalityRevision revision) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assistant.buggy_personality_revisions(assistant_id, personality_revision, previous_revision, origin, reason, confidence_bps, rollback_target_revision, created_by, created_at, state_sha256) "
                        + "VALUES ('BUGGY', ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setLong(1, revision.revision());
            setNullableLong(statement, 2, revision.previousRevision());
            statement.setString(3, revision.origin().name());
            statement.setString(4, revision.reason());
            statement.setInt(5, revision.confidenceBps());
            setNullableLong(statement, 6, revision.rollbackTargetRevision());
            statement.setString(7, revision.createdBy().value());
            statement.setTimestamp(8, Timestamp.from(revision.createdAt()));
            statement.setString(9, revision.sha256());
            requireOne(statement.executeUpdate(), "Buggy Personality revision");
        }
    }

    private static void insertRevisionChanges(
            Connection connection, BuggyPersonalityRevision revision) throws SQLException {
        if (revision.changedAttributes().isEmpty()) return;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assistant.buggy_personality_revision_changes(assistant_id, personality_revision, change_ordinal, attribute_identity_key) "
                        + "VALUES ('BUGGY', ?, ?, ?)")) {
            for (int i = 0; i < revision.changedAttributes().size(); i++) {
                statement.setLong(1, revision.revision());
                statement.setInt(2, i);
                statement.setString(3, revision.changedAttributes().get(i));
                statement.addBatch();
            }
            requireBatch(
                    revision.changedAttributes().size(),
                    statement.executeBatch(),
                    "Buggy Personality revision changes");
        }
    }

    private static void insertRevisionEvidence(
            Connection connection, BuggyPersonalityRevision revision) throws SQLException {
        if (revision.evidenceObservationIds().isEmpty()) return;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assistant.buggy_personality_revision_evidence(assistant_id, personality_revision, evidence_ordinal, observation_id) "
                        + "VALUES ('BUGGY', ?, ?, ?)")) {
            for (int i = 0; i < revision.evidenceObservationIds().size(); i++) {
                statement.setLong(1, revision.revision());
                statement.setInt(2, i);
                statement.setString(3, revision.evidenceObservationIds().get(i));
                statement.addBatch();
            }
            requireBatch(
                    revision.evidenceObservationIds().size(),
                    statement.executeBatch(),
                    "Buggy Personality revision evidence");
        }
    }

    private static void insertStateEntries(
            Connection connection, BuggyPersonalityRevision revision) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assistant.buggy_personality_state_entries(assistant_id, personality_revision, domain, attribute_key, attribute_value, confidence_bps, source_observation_id) "
                        + "VALUES ('BUGGY', ?, ?, ?, ?, ?, ?)")) {
            for (BuggyPersonalityStateEntry entry : revision.entries()) {
                statement.setLong(1, revision.revision());
                statement.setString(2, entry.domain().name());
                statement.setString(3, entry.attributeKey());
                statement.setString(4, entry.value());
                statement.setInt(5, entry.confidenceBps());
                statement.setString(6, entry.sourceObservationId());
                statement.addBatch();
            }
            requireBatch(revision.entries().size(), statement.executeBatch(), "Buggy Personality state entries");
        }
    }

    private static void insertHead(
            Connection connection, BuggyPersonalityRevision initialRevision) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO assistant.buggy_personality_head(assistant_id, current_revision, updated_at) VALUES ('BUGGY', ?, ?)")) {
            statement.setLong(1, initialRevision.revision());
            statement.setTimestamp(2, Timestamp.from(initialRevision.createdAt()));
            requireOne(statement.executeUpdate(), "Buggy Personality head");
        }
    }

    private static void updateHead(
            Connection connection, long expectedRevision, BuggyPersonalityRevision next) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE assistant.buggy_personality_head SET current_revision=?, updated_at=? "
                        + "WHERE assistant_id='BUGGY' AND current_revision=?")) {
            statement.setLong(1, next.revision());
            statement.setTimestamp(2, Timestamp.from(next.createdAt()));
            statement.setLong(3, expectedRevision);
            requireOne(statement.executeUpdate(), "Buggy Personality head");
        }
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.BIGINT);
        else statement.setLong(index, value);
    }

    private static void requireOne(int count, String operation) {
        if (count != 1) throw new IllegalStateException(operation + " affected row count != 1");
    }

    private static void requireBatch(int expected, int[] counts, String operation) {
        if (counts.length != expected) {
            throw new IllegalStateException(operation + " batch count mismatch");
        }
        for (int count : counts) {
            if (count != 1 && count != java.sql.Statement.SUCCESS_NO_INFO) {
                throw new IllegalStateException(operation + " batch row failed");
            }
        }
    }
}
