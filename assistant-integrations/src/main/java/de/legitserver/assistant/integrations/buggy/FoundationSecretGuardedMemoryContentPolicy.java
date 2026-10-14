package de.legitserver.assistant.integrations.buggy;

import de.legitserver.assistant.buggy.memory.BuggyMemoryContentPolicy;
import de.legitserver.assistant.security.secret.SecretGuard;
import java.util.Objects;

/** Reuses the Foundation SecretGuard for G1.5 memory, reflection and learning persistence. */
public final class FoundationSecretGuardedMemoryContentPolicy implements BuggyMemoryContentPolicy {
    private final SecretGuard secretGuard;

    public FoundationSecretGuardedMemoryContentPolicy(SecretGuard secretGuard) {
        this.secretGuard = Objects.requireNonNull(secretGuard, "secretGuard");
    }

    @Override
    public String requirePersistableText(String value, String field, int maxLength) {
        if (value == null) throw new IllegalArgumentException(field + " must not be null");
        String normalized = value.strip();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " exceeds max length " + maxLength);
        boolean illegalControl = normalized.codePoints().anyMatch(codePoint ->
                Character.isISOControl(codePoint) && codePoint != '\n' && codePoint != '\t');
        if (illegalControl) throw new IllegalArgumentException(field + " contains illegal control characters");
        if (secretGuard.redact(normalized).secretDetected()) {
            throw new IllegalArgumentException(field + " contains secret material and cannot enter Native Memory");
        }
        return normalized;
    }

    @Override
    public boolean containsSecretMaterial(String value) {
        return value != null && secretGuard.redact(value).secretDetected();
    }
}
