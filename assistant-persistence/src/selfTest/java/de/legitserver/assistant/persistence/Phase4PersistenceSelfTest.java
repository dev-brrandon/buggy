package de.legitserver.assistant.persistence;

import de.legitserver.assistant.persistence.migration.SqlMigration;
import de.legitserver.assistant.persistence.migration.SqlMigrationCatalog;
import de.legitserver.assistant.persistence.optimistic.OptimisticLockConflictException;
import de.legitserver.assistant.persistence.optimistic.OptimisticLocking;
import de.legitserver.assistant.persistence.policy.PostgresJdbcUrlPolicy;
import de.legitserver.assistant.persistence.transaction.JdbcTransactionRunner;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import org.hsqldb.jdbc.jdbcDataSource;

/** Phase-4 offline verification. JDBC semantics run against an embedded compatibility database. */
public final class Phase4PersistenceSelfTest {
    private Phase4PersistenceSelfTest() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected assistant-persistence project directory");
        }
        verifySchemaOwnership();
        Path projectDirectory = Path.of(args[0]);
        verifyMigrationCatalog(projectDirectory.resolve("src/main/resources/db/migration"));
        verifyDatabaseRolePolicy(projectDirectory.resolve("src/main/resources/db/admin"));
        verifyPostgresUrlPolicy();
        verifyJdbcTransactionsAndOptimisticLocking();
        System.out.println("PHASE4_PERSISTENCE_SELF_TEST: PASS");
    }

    private static void verifySchemaOwnership() {
        require(PersistenceSchemas.ALL.size() == 5, "expected five schemas");
        require(RepositoryDomain.AUDIT.schema().equals("audit"), "audit ownership");
        require(RepositoryDomain.BUGS.schema().equals("bugs"), "bugs ownership");
    }

    private static void verifyMigrationCatalog(Path directory) {
        List<SqlMigration> migrations = new SqlMigrationCatalog().inspect(directory);
        require(migrations.size() >= 3, "expected at least the three Phase-4 migrations");
        require(migrations.get(0).version().equals("4.001"), "first migration version");
        require(migrations.stream().anyMatch(m -> m.version().equals("4.003")), "Phase-4 final migration version");
        for (SqlMigration migration : migrations) {
            require(migration.sha256().length() == 64, "migration checksum");
        }
    }


    private static void verifyDatabaseRolePolicy(Path directory) throws Exception {
        String roles = java.nio.file.Files.readString(directory.resolve("001_roles.sql.example")).toLowerCase();
        String grants = java.nio.file.Files.readString(directory.resolve("002_schema_grants.sql.example")).toLowerCase();
        require(roles.contains("legit_assistant_owner nologin"), "owner must be NOLOGIN");
        require(grants.contains("grant select, insert on all tables in schema audit"), "audit append grant");
        require(!grants.contains("update, delete on all tables in schema audit"), "audit must not grant update/delete");
        require(!grants.contains("grant create"), "runtime role must not receive CREATE");
        String executableRoles = roles.lines().filter(line -> !line.stripLeading().startsWith("--")).reduce("", (a, b) -> a + "\n" + b);
        String executableGrants = grants.lines().filter(line -> !line.stripLeading().startsWith("--")).reduce("", (a, b) -> a + "\n" + b);
        require(!executableRoles.contains("password"), "role template must not contain password clauses");
        require(!executableGrants.contains("password"), "grant template must not contain password clauses");
    }

    private static void verifyPostgresUrlPolicy() {
        String valid = "jdbc:postgresql://db.internal:5432/legit_assistant?sslmode=verify-full";
        require(PostgresJdbcUrlPolicy.requireProductionSafe(valid).equals(valid), "valid PostgreSQL URL");
        expectFailure(() -> PostgresJdbcUrlPolicy.requireProductionSafe("jdbc:postgresql://db/legit"));
        expectFailure(() -> PostgresJdbcUrlPolicy.requireProductionSafe("jdbc:mysql://db/legit?sslmode=verify-full"));
    }

    private static void verifyJdbcTransactionsAndOptimisticLocking() throws Exception {
        jdbcDataSource dataSource = new jdbcDataSource();
        dataSource.setDatabase("jdbc:hsqldb:mem:phase4");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        JdbcTransactionRunner tx = new JdbcTransactionRunner(dataSource);

        tx.required(
                connection -> {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("CREATE TABLE parent_record (id VARCHAR(64) PRIMARY KEY)");
                        statement.execute(
                                "CREATE TABLE versioned_probe (id VARCHAR(64) PRIMARY KEY, parent_id VARCHAR(64), payload VARCHAR(128) NOT NULL, revision BIGINT NOT NULL, CONSTRAINT fk_probe_parent FOREIGN KEY (parent_id) REFERENCES parent_record(id))");
                        statement.execute("INSERT INTO parent_record(id) VALUES ('p1')");
                        statement.execute("INSERT INTO versioned_probe(id,parent_id,payload,revision) VALUES ('x','p1','before',0)");
                    }
                    return null;
                });

        try {
            tx.required(
                    connection -> {
                        try (Statement statement = connection.createStatement()) {
                            statement.execute("INSERT INTO parent_record(id) VALUES ('rollback-me')");
                        }
                        throw new IllegalStateException("force rollback");
                    });
            throw new IllegalStateException("rollback test did not fail");
        } catch (IllegalStateException expected) {
            require(expected.getMessage().equals("force rollback"), "rollback propagated original failure");
        }
        require(count(dataSource.getConnection(), "SELECT COUNT(*) FROM parent_record WHERE id='rollback-me'") == 0, "rollback removed uncommitted row");

        tx.required(
                connection -> {
                    try (PreparedStatement statement =
                            connection.prepareStatement(
                                    "UPDATE versioned_probe SET payload=?, revision=revision+1 WHERE id=? AND revision=?")) {
                        statement.setString(1, "after");
                        statement.setString(2, "x");
                        statement.setLong(3, 0);
                        OptimisticLocking.requireExactlyOne(statement.executeUpdate(), "probe", "x", 0);
                    }
                    return null;
                });

        try {
            tx.required(
                    connection -> {
                        try (PreparedStatement statement =
                                connection.prepareStatement(
                                        "UPDATE versioned_probe SET payload=?, revision=revision+1 WHERE id=? AND revision=?")) {
                            statement.setString(1, "stale");
                            statement.setString(2, "x");
                            statement.setLong(3, 0);
                            OptimisticLocking.requireExactlyOne(statement.executeUpdate(), "probe", "x", 0);
                        }
                        return null;
                    });
            throw new IllegalStateException("stale optimistic update was accepted");
        } catch (OptimisticLockConflictException expected) {
            require(expected.getMessage().contains("probe x"), "optimistic conflict metadata");
        }

        try {
            tx.required(
                    connection -> {
                        try (Statement statement = connection.createStatement()) {
                            statement.execute(
                                    "INSERT INTO versioned_probe(id,parent_id,payload,revision) VALUES ('bad','missing','x',0)");
                        }
                        return null;
                    });
            throw new IllegalStateException("foreign key violation was accepted");
        } catch (RuntimeException expected) {
            require(expected.getMessage() != null, "foreign key failure propagated");
        }
    }

    private static int count(Connection connection, String sql) throws Exception {
        try (connection; Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            require(result.next(), "count query returned row");
            return result.getInt(1);
        }
    }

    private static void expectFailure(Runnable operation) {
        try {
            operation.run();
            throw new IllegalStateException("expected failure");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Assertion failed: " + message);
        }
    }
}
