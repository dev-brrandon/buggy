package de.legitserver.assistant.integrations.buggy;

import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentContentPolicy;
import de.legitserver.assistant.core.validation.CoreValidation;
import de.legitserver.assistant.security.secret.SecretGuard;
import de.legitserver.assistant.security.secret.SecretScanResult;
import java.util.Objects;

/** B7 adapter that reuses the Foundation SecretGuard before Self-Development free-form text is persisted. */
public final class FoundationSecretGuardedSelfDevelopmentContentPolicy implements BuggySelfDevelopmentContentPolicy {
    private final SecretGuard secretGuard;

    public FoundationSecretGuardedSelfDevelopmentContentPolicy(SecretGuard secretGuard) {
        this.secretGuard = Objects.requireNonNull(secretGuard, "secretGuard");
    }

    @Override
    public String requirePersistableText(String value, String field, int maxLength) {
        String normalized = CoreValidation.requireText(value, field, maxLength);
        SecretScanResult scan = secretGuard.redact(normalized);
        if (scan.secretDetected()) {
            throw new IllegalArgumentException(field + " contains secret material and cannot enter Self-Development state");
        }
        return scan.redactedText();
    }
}
