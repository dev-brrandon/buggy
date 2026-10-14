package de.legitserver.assistant.integrations.buggy;

import de.legitserver.assistant.buggy.personality.BuggyPersonalityContentPolicy;
import de.legitserver.assistant.core.validation.CoreValidation;
import de.legitserver.assistant.security.secret.SecretGuard;
import de.legitserver.assistant.security.secret.SecretScanResult;
import java.util.Objects;

/** B2 adapter that reuses the Foundation SecretGuard before Personality text may be persisted. */
public final class FoundationSecretGuardedPersonalityContentPolicy implements BuggyPersonalityContentPolicy {
    private final SecretGuard secretGuard;

    public FoundationSecretGuardedPersonalityContentPolicy(SecretGuard secretGuard) {
        this.secretGuard = Objects.requireNonNull(secretGuard, "secretGuard");
    }

    @Override
    public String requirePersistableText(String value, String field, int maxLength) {
        String normalized = CoreValidation.requireText(value, field, maxLength);
        SecretScanResult scan = secretGuard.redact(normalized);
        if (scan.secretDetected()) {
            throw new IllegalArgumentException(field + " contains secret material and cannot enter Personality state");
        }
        return scan.redactedText();
    }
}
