package de.legitserver.assistant.buggy.tools;

import de.legitserver.assistant.runtime.execution.ToolHandler;
import de.legitserver.assistant.runtime.registry.ToolRegistry;
import de.legitserver.assistant.security.secret.SecretGuard;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Registers B5 definitions into the existing Foundation ToolRegistry with Buggy-specific boundary guards. */
public final class BuggyToolRegistrar {
    private final SecretGuard secretGuard;

    public BuggyToolRegistrar(SecretGuard secretGuard) {
        this.secretGuard = Objects.requireNonNull(secretGuard, "secretGuard");
    }

    public void registerAll(ToolRegistry registry, BuggyToolHandlerResolver resolver) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(resolver, "resolver");
        List<BuggyToolDefinition> definitions = BuggyToolCatalog.definitions();
        for (BuggyToolDefinition definition : definitions) {
            BuggyToolHandlerBinding binding = resolver.resolve(definition.descriptor().toolId())
                    .orElseThrow(() -> new IllegalStateException(
                            "missing typed Buggy tool handler: " + definition.descriptor().toolId()));
            requireCompatibleHandlerKind(definition, binding.kind());
            ToolHandler delegate = binding.handler();
            registry.register(definition.descriptor(), context -> {
                if (!definition.allows(context.resource())) {
                    throw new IllegalArgumentException("resource type outside Buggy tool scope");
                }
                rejectSecretInput(definition, context.input());
                return delegate.execute(context);
            });
        }
    }

    private static void requireCompatibleHandlerKind(BuggyToolDefinition definition, BuggyToolHandlerKind actual) {
        BuggyToolHandlerKind expected;
        if (definition.approvalPolicy() == BuggyToolApprovalPolicy.REQUEST_ONLY) {
            expected = BuggyToolHandlerKind.REQUEST_CREATOR;
        } else if (definition.category() == BuggyToolCategory.WEB) {
            expected = BuggyToolHandlerKind.BOUNDED_WEB;
        } else if (definition.descriptor().toolId().value().equals("tool.database.query.readonly")
                || definition.descriptor().toolId().value().equals("tool.database.explain")) {
            expected = BuggyToolHandlerKind.READ_ONLY_DATABASE;
        } else {
            expected = BuggyToolHandlerKind.DIRECT_TYPED;
        }
        if (actual != expected) {
            throw new IllegalStateException("handler kind mismatch for " + definition.descriptor().toolId()
                    + ": expected " + expected + " but got " + actual);
        }
    }

    private void rejectSecretInput(BuggyToolDefinition definition, Map<String, Object> input) {
        if (definition.secretHandling() == BuggyToolSecretHandling.METADATA_ONLY_AND_REDACT_OUTPUT
                || definition.secretHandling() == BuggyToolSecretHandling.REJECT_SECRET_INPUT_AND_REDACT_OUTPUT) {
            for (Object value : input.values()) {
                if (value instanceof String text && secretGuard.redact(text).secretDetected()) {
                    throw new IllegalArgumentException("secret-bearing tool input rejected");
                }
                if (value instanceof List<?> values) {
                    for (Object item : values) {
                        if (item instanceof String text && secretGuard.redact(text).secretDetected()) {
                            throw new IllegalArgumentException("secret-bearing tool input rejected");
                        }
                    }
                }
            }
        }
    }
}
