package de.legitserver.assistant.integrations.buggy;

import de.legitserver.assistant.security.secret.SecretGuard;

/** Verifies B4 normal Knowledge persistence/retrieval uses the accepted Foundation SecretGuard. */
public final class B4KnowledgeSecretGuardSelfTest {
    private B4KnowledgeSecretGuardSelfTest() {}

    public static void main(String[] args) {
        FoundationSecretGuardedKnowledgeContentPolicy policy =
                new FoundationSecretGuardedKnowledgeContentPolicy(new SecretGuard());
        String multiline = policy.requirePersistableText(
                "Decision: use Vanilla Items\nRationale: preserve Minecraft-native trade", "content", 4096);
        require(multiline.contains("\n"), "multiline Knowledge content must be allowed");
        require(!policy.containsSecretMaterial("secret_ref=legit/prod/postgres"),
                "secret references are metadata, not secret values");
        require(policy.containsSecretMaterial("password=super-secret-value"), "named secret detected");
        expectFailure(() -> policy.requirePersistableText(
                "Authorization: Bearer abcdefghijklmnopqrstuvwxyz", "content", 4096));
        System.out.println("B4_BUGGY_KNOWLEDGE_SECRET_GUARD_SELF_TEST: PASS");
    }

    private static void expectFailure(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected secret-bearing Knowledge to be rejected");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
