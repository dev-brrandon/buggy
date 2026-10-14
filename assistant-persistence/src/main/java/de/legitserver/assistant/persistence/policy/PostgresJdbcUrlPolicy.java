package de.legitserver.assistant.persistence.policy;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Production JDBC URL policy: PostgreSQL only and TLS verification required. */
public final class PostgresJdbcUrlPolicy {
    private PostgresJdbcUrlPolicy() {}

    public static String requireProductionSafe(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException("Production persistence requires jdbc:postgresql://");
        }
        String lower = jdbcUrl.toLowerCase(Locale.ROOT);
        if (!(lower.contains("sslmode=verify-full") || lower.contains("sslmode=verify-ca"))) {
            throw new IllegalArgumentException("PostgreSQL TLS verification is required");
        }
        validateHost(jdbcUrl.substring("jdbc:".length()));
        return jdbcUrl;
    }

    private static void validateHost(String uriText) {
        try {
            URI uri = new URI(uriText);
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("PostgreSQL host is required");
            }
        } catch (URISyntaxException failure) {
            throw new IllegalArgumentException("Invalid PostgreSQL JDBC URL", failure);
        }
    }
}
