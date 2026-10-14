package de.legitserver.assistant.integrations.buggy;

import de.legitserver.assistant.buggy.nativecore.BuggyBirthRecord;
import de.legitserver.assistant.buggy.nativecore.BuggyBirthType;
import de.legitserver.assistant.buggy.nativecore.BuggyCoreProtocol;
import de.legitserver.assistant.buggy.nativecore.BuggyCoreProtocolCatalog;
import de.legitserver.assistant.buggy.nativecore.BuggyMaturityDimension;
import de.legitserver.assistant.buggy.nativecore.BuggyMaturityEntry;
import de.legitserver.assistant.buggy.nativecore.BuggyMaturityProfile;
import de.legitserver.assistant.buggy.nativecore.BuggyMaturityStage;
import de.legitserver.assistant.buggy.nativecore.BuggyNativeCoreState;
import de.legitserver.assistant.buggy.nativecore.BuggyNativePersonalityBaseline;
import de.legitserver.assistant.buggy.nativecore.BuggyPersonalityAxis;
import de.legitserver.assistant.buggy.nativecore.BuggyPersonalityAxisEntry;
import de.legitserver.assistant.buggy.nativecore.BuggySelfCapabilityEntry;
import de.legitserver.assistant.buggy.nativecore.BuggySelfCapabilityStatus;
import de.legitserver.assistant.buggy.repository.BuggyNativeCoreRepository;
import de.legitserver.assistant.persistence.transaction.TransactionRunner;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** PostgreSQL adapter for the immutable G1.4 Native-Core birth and baseline state. */
public final class JdbcBuggyNativeCoreRepository implements BuggyNativeCoreRepository {
    private static final long INITIALIZATION_LOCK = 0x4255474759473134L;

    private final TransactionRunner transactions;

    public JdbcBuggyNativeCoreRepository(TransactionRunner transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    @Override
    public Optional<BuggyNativeCoreState> find() {
        return transactions.readOnly(JdbcBuggyNativeCoreRepository::find);
    }

    @Override
    public BuggyNativeCoreState initialize(BuggyNativeCoreState requested) {
        Objects.requireNonNull(requested, "requested");
        return transactions.required(connection -> {
            lock(connection);
            Optional<BuggyNativeCoreState> existing = find(connection);
            if (existing.isPresent()) {
                if (!existing.orElseThrow().sha256().equals(requested.sha256())) {
                    throw new IllegalStateException("Persistent Buggy Native-Core baseline differs from requested state");
                }
                return existing.orElseThrow();
            }
            insertSeed(connection, requested);
            insertProtocols(connection, requested);
            insertPersonality(connection, requested);
            insertMaturity(connection, requested);
            insertCapabilities(connection, requested);
            return find(connection).orElseThrow(() -> new IllegalStateException("Buggy Native-Core insert was not readable"));
        });
    }

    private static void lock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
            statement.setLong(1, INITIALIZATION_LOCK);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalStateException("Buggy Native-Core advisory lock returned no row");
            }
        }
    }

    private static Optional<BuggyNativeCoreState> find(Connection connection) throws SQLException {
        String sql = """
                SELECT schema_version, concept_date, birth_at, birth_type, initial_stage,
                       protocol_catalog_version, protocol_catalog_sha256,
                       personality_schema_version, personality_baseline_sha256, state_sha256
                FROM assistant.buggy_native_core_seed
                WHERE assistant_id='BUGGY'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rs = statement.executeQuery()) {
            if (!rs.next()) return Optional.empty();
            int schemaVersion = rs.getInt("schema_version");
            BuggyBirthRecord birth = new BuggyBirthRecord(
                    rs.getDate("concept_date").toLocalDate(),
                    rs.getTimestamp("birth_at").toInstant(),
                    BuggyBirthType.valueOf(rs.getString("birth_type")),
                    BuggyMaturityStage.valueOf(rs.getString("initial_stage")));
            BuggyCoreProtocolCatalog protocols = loadProtocols(
                    connection,
                    rs.getInt("protocol_catalog_version"),
                    rs.getString("protocol_catalog_sha256"));
            BuggyNativePersonalityBaseline personality = loadPersonality(
                    connection,
                    rs.getInt("personality_schema_version"),
                    rs.getString("personality_baseline_sha256"));
            BuggyMaturityProfile maturity = loadMaturity(connection, birth.initialStage());
            List<BuggySelfCapabilityEntry> capabilities = loadCapabilities(connection);
            String sha = rs.getString("state_sha256");
            if (rs.next()) throw new IllegalStateException("Multiple Buggy Native-Core seed rows found");
            return Optional.of(new BuggyNativeCoreState(
                    schemaVersion, birth, protocols, personality, maturity, capabilities, sha));
        }
    }

    private static BuggyCoreProtocolCatalog loadProtocols(
            Connection connection, int catalogVersion, String catalogSha) throws SQLException {
        List<BuggyCoreProtocol> protocols = new ArrayList<>();
        String sql = """
                SELECT protocol_code, protocol_id, protocol_version, summary, protocol_sha256
                FROM assistant.buggy_native_core_protocols
                WHERE assistant_id='BUGGY'
                ORDER BY protocol_ordinal ASC
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                BuggyCoreProtocol protocol = BuggyCoreProtocol.valueOf(rs.getString("protocol_code"));
                if (!protocol.protocolId().equals(rs.getString("protocol_id"))
                        || BuggyCoreProtocol.VERSION != rs.getInt("protocol_version")
                        || !protocol.summary().equals(rs.getString("summary"))
                        || !protocol.sha256().equals(rs.getString("protocol_sha256"))) {
                    throw new IllegalStateException("Persistent Buggy Core Protocol differs from protected catalog: " + protocol);
                }
                protocols.add(protocol);
            }
        }
        return new BuggyCoreProtocolCatalog(catalogVersion, protocols, catalogSha);
    }

    private static BuggyNativePersonalityBaseline loadPersonality(
            Connection connection, int schemaVersion, String sha) throws SQLException {
        List<BuggyPersonalityAxisEntry> axes = new ArrayList<>();
        String sql = """
                SELECT axis, start_bps, minimum_bps, maximum_bps
                FROM assistant.buggy_native_personality_baseline
                WHERE assistant_id='BUGGY'
                ORDER BY axis ASC
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                axes.add(new BuggyPersonalityAxisEntry(
                        BuggyPersonalityAxis.valueOf(rs.getString("axis")),
                        rs.getInt("start_bps"),
                        rs.getInt("minimum_bps"),
                        rs.getInt("maximum_bps")));
            }
        }
        return new BuggyNativePersonalityBaseline(schemaVersion, axes, sha);
    }

    private static BuggyMaturityProfile loadMaturity(
            Connection connection, BuggyMaturityStage stage) throws SQLException {
        List<BuggyMaturityEntry> entries = new ArrayList<>();
        String sql = """
                SELECT dimension, score_bps
                FROM assistant.buggy_native_maturity_baseline
                WHERE assistant_id='BUGGY'
                ORDER BY dimension ASC
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                entries.add(new BuggyMaturityEntry(
                        BuggyMaturityDimension.valueOf(rs.getString("dimension")),
                        rs.getInt("score_bps")));
            }
        }
        return new BuggyMaturityProfile(stage, entries);
    }

    private static List<BuggySelfCapabilityEntry> loadCapabilities(Connection connection) throws SQLException {
        List<BuggySelfCapabilityEntry> entries = new ArrayList<>();
        String sql = """
                SELECT capability_id, status, confidence_bps, limitation
                FROM assistant.buggy_native_self_capabilities
                WHERE assistant_id='BUGGY'
                ORDER BY capability_id ASC
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                entries.add(new BuggySelfCapabilityEntry(
                        rs.getString("capability_id"),
                        BuggySelfCapabilityStatus.valueOf(rs.getString("status")),
                        rs.getInt("confidence_bps"),
                        rs.getString("limitation")));
            }
        }
        return List.copyOf(entries);
    }

    private static void insertSeed(Connection connection, BuggyNativeCoreState state) throws SQLException {
        String sql = """
                INSERT INTO assistant.buggy_native_core_seed(
                    assistant_id, schema_version, concept_date, birth_at, birth_type, initial_stage,
                    protocol_catalog_version, protocol_catalog_sha256,
                    personality_schema_version, personality_baseline_sha256,
                    state_sha256, created_at)
                VALUES ('BUGGY', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, state.schemaVersion());
            statement.setDate(2, Date.valueOf(state.birth().conceptDate()));
            statement.setTimestamp(3, Timestamp.from(state.birth().bornAt()));
            statement.setString(4, state.birth().birthType().name());
            statement.setString(5, state.birth().initialStage().name());
            statement.setInt(6, state.protocols().catalogVersion());
            statement.setString(7, state.protocols().sha256());
            statement.setInt(8, state.personalityBaseline().schemaVersion());
            statement.setString(9, state.personalityBaseline().sha256());
            statement.setString(10, state.sha256());
            statement.setTimestamp(11, Timestamp.from(state.birth().bornAt()));
            statement.executeUpdate();
        }
    }

    private static void insertProtocols(Connection connection, BuggyNativeCoreState state) throws SQLException {
        String sql = """
                INSERT INTO assistant.buggy_native_core_protocols(
                    assistant_id, protocol_ordinal, protocol_code, protocol_id, protocol_version,
                    summary, protocol_sha256)
                VALUES ('BUGGY', ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int ordinal = 0;
            for (BuggyCoreProtocol protocol : state.protocols().protocols()) {
                statement.setInt(1, ordinal++);
                statement.setString(2, protocol.name());
                statement.setString(3, protocol.protocolId());
                statement.setInt(4, BuggyCoreProtocol.VERSION);
                statement.setString(5, protocol.summary());
                statement.setString(6, protocol.sha256());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertPersonality(Connection connection, BuggyNativeCoreState state) throws SQLException {
        String sql = """
                INSERT INTO assistant.buggy_native_personality_baseline(
                    assistant_id, axis, start_bps, minimum_bps, maximum_bps)
                VALUES ('BUGGY', ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (BuggyPersonalityAxisEntry axis : state.personalityBaseline().axes()) {
                statement.setString(1, axis.axis().name());
                statement.setInt(2, axis.startBps());
                statement.setInt(3, axis.minimumBps());
                statement.setInt(4, axis.maximumBps());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertMaturity(Connection connection, BuggyNativeCoreState state) throws SQLException {
        String sql = """
                INSERT INTO assistant.buggy_native_maturity_baseline(assistant_id, dimension, score_bps)
                VALUES ('BUGGY', ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (BuggyMaturityEntry entry : state.maturityBaseline().entries()) {
                statement.setString(1, entry.dimension().name());
                statement.setInt(2, entry.scoreBps());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertCapabilities(Connection connection, BuggyNativeCoreState state) throws SQLException {
        String sql = """
                INSERT INTO assistant.buggy_native_self_capabilities(
                    assistant_id, capability_id, status, confidence_bps, limitation)
                VALUES ('BUGGY', ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (BuggySelfCapabilityEntry entry : state.capabilities()) {
                statement.setString(1, entry.capabilityId());
                statement.setString(2, entry.status().name());
                statement.setInt(3, entry.confidenceBps());
                statement.setString(4, entry.limitation());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
