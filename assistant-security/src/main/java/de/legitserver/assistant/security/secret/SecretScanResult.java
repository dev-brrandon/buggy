package de.legitserver.assistant.security.secret;

import java.util.Set;

/** Redacted output and non-sensitive classification of discovered secret material. */
public record SecretScanResult(String redactedText, Set<SecretKind> secretKinds) {
    public SecretScanResult {
        if (redactedText == null) throw new IllegalArgumentException("redactedText must not be null");
        secretKinds = secretKinds == null ? Set.of() : Set.copyOf(secretKinds);
    }

    public boolean secretDetected() {
        return !secretKinds.isEmpty();
    }
}
