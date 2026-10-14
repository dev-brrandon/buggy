package de.legitserver.assistant.buggy.nativecore;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public record BuggyCoreProtocolCatalog(
        int catalogVersion,
        List<BuggyCoreProtocol> protocols,
        String sha256) {

    public static final int CATALOG_VERSION = 1;

    public BuggyCoreProtocolCatalog {
        if (catalogVersion != CATALOG_VERSION) {
            throw new IllegalArgumentException("Unsupported Buggy protocol catalog version");
        }
        protocols = List.copyOf(CoreValidation.requireNonNull(protocols, "protocols"));
        if (protocols.size() != BuggyCoreProtocol.values().length
                || protocols.stream().distinct().count() != BuggyCoreProtocol.values().length) {
            throw new IllegalArgumentException("Protocol catalog must contain each Core Protocol exactly once");
        }
        sha256 = CoreValidation.requireSafeToken(sha256, "sha256", 64);
        if (!canonicalSha256(protocols).equals(sha256)) {
            throw new IllegalArgumentException("Buggy protocol catalog SHA-256 mismatch");
        }
    }

    public static BuggyCoreProtocolCatalog canonical() {
        List<BuggyCoreProtocol> protocols = List.of(BuggyCoreProtocol.values());
        return new BuggyCoreProtocolCatalog(CATALOG_VERSION, protocols, canonicalSha256(protocols));
    }

    private static String canonicalSha256(List<BuggyCoreProtocol> protocols) {
        StringBuilder canonical = new StringBuilder("catalogVersion=").append(CATALOG_VERSION).append('\n');
        protocols.stream().sorted(Comparator.comparing(BuggyCoreProtocol::protocolId)).forEach(protocol -> canonical
                .append(protocol.protocolId()).append(':')
                .append(BuggyCoreProtocol.VERSION).append(':')
                .append(protocol.sha256()).append('\n'));
        return BuggyNativeCoreCanonicalizer.sha256(canonical.toString());
    }
}
