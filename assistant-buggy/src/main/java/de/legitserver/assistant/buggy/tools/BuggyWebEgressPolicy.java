package de.legitserver.assistant.buggy.tools;

import java.util.Set;

/** Mandatory constraints that concrete B5 Web handlers must enforce for every request and redirect. */
public record BuggyWebEgressPolicy(
        Set<String> allowedSchemes,
        boolean blockLoopback,
        boolean blockPrivateNetworks,
        boolean blockLinkLocal,
        boolean blockMetadataNetworks,
        boolean validateEveryRedirect,
        int maxRedirects,
        long maxResponseBytes,
        int connectTimeoutMillis,
        int readTimeoutMillis,
        Set<String> allowedContentTypePrefixes) {
    public BuggyWebEgressPolicy {
        allowedSchemes = allowedSchemes == null ? Set.of() : Set.copyOf(allowedSchemes);
        allowedContentTypePrefixes = allowedContentTypePrefixes == null ? Set.of() : Set.copyOf(allowedContentTypePrefixes);
        if (allowedSchemes.isEmpty() || allowedContentTypePrefixes.isEmpty()) {
            throw new IllegalArgumentException("web egress allow-lists must not be empty");
        }
        if (maxRedirects < 0 || maxRedirects > 10) throw new IllegalArgumentException("maxRedirects out of range");
        if (maxResponseBytes < 1 || maxResponseBytes > 10_000_000L) {
            throw new IllegalArgumentException("maxResponseBytes out of range");
        }
        if (connectTimeoutMillis < 100 || connectTimeoutMillis > 30_000
                || readTimeoutMillis < 100 || readTimeoutMillis > 60_000) {
            throw new IllegalArgumentException("web timeouts out of range");
        }
    }

    public static BuggyWebEgressPolicy canonical() {
        return new BuggyWebEgressPolicy(
                Set.of("https", "http"),
                true,
                true,
                true,
                true,
                true,
                5,
                2_000_000L,
                5_000,
                15_000,
                Set.of("text/", "application/json", "application/xml", "application/xhtml+xml"));
    }
}
