package de.legitserver.assistant.persistence.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates Flyway-style versioned SQL files without binding domain code to Flyway. */
public final class SqlMigrationCatalog {
    private static final Pattern NAME = Pattern.compile("V(\\d+)_(\\d+)__([a-z0-9_]+)\\.sql");

    public List<SqlMigration> inspect(Path migrationDirectory) {
        try {
            List<SqlMigration> migrations = new ArrayList<>();
            try (var paths = Files.list(migrationDirectory)) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    Matcher matcher = NAME.matcher(path.getFileName().toString());
                    if (!matcher.matches()) {
                        throw new IllegalStateException("Invalid migration name: " + path.getFileName());
                    }
                    String sql = Files.readString(path, StandardCharsets.UTF_8);
                    requireSafeMigration(sql, path);
                    migrations.add(
                            new SqlMigration(
                                    matcher.group(1) + "." + matcher.group(2),
                                    matcher.group(3),
                                    path.getFileName().toString(),
                                    sha256(sql)));
                }
            }
            migrations.sort(Comparator.comparing(SqlMigrationCatalog::numericVersion));
            ensureUniqueVersions(migrations);
            return List.copyOf(migrations);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot inspect migration directory", failure);
        }
    }

    private static long numericVersion(SqlMigration migration) {
        String[] parts = migration.version().split("\\.");
        return Long.parseLong(parts[0]) * 1_000_000L + Long.parseLong(parts[1]);
    }

    private static void ensureUniqueVersions(List<SqlMigration> migrations) {
        for (int i = 1; i < migrations.size(); i++) {
            if (migrations.get(i - 1).version().equals(migrations.get(i).version())) {
                throw new IllegalStateException("Duplicate migration version: " + migrations.get(i).version());
            }
        }
    }

    private static void requireSafeMigration(String sql, Path path) {
        String normalized = sql.toLowerCase();
        if (normalized.contains("drop schema") || normalized.contains("drop database")) {
            throw new IllegalStateException("Destructive migration is forbidden: " + path.getFileName());
        }
        if (normalized.contains(" password ") || normalized.contains("password=")) {
            throw new IllegalStateException("Credentials are forbidden in migrations: " + path.getFileName());
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }
}
