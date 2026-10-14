package de.legitserver.assistant.integrations.buggy;

import de.legitserver.assistant.security.secret.SecretGuard;

/** Verifies B2 Personality free-form persistence is guarded by the existing Foundation SecretGuard. */
public final class B2PersonalitySecretGuardSelfTest {
    private B2PersonalitySecretGuardSelfTest() {}

    public static void main(String[] args) {
        FoundationSecretGuardedPersonalityContentPolicy policy =
                new FoundationSecretGuardedPersonalityContentPolicy(new SecretGuard());
        require(
                policy.requirePersistableText("prefer concise incident replies", "value", 512)
                        .equals("prefer concise incident replies"),
                "normal personality text passes unchanged");
        expectFailure(() -> policy.requirePersistableText("password=super-secret-value", "value", 512));
        expectFailure(() -> policy.requirePersistableText(
                "Authorization: Bearer abcdefghijklmnopqrstuvwxyz123456", "value", 512));
        System.out.println("B2_PERSONALITY_SECRET_GUARD_SELF_TEST: PASS");
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new IllegalStateException("Expected secret rejection");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Assertion failed: " + message);
    }
}
