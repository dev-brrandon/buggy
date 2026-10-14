package de.legitserver.assistant.buggy.tools;

import de.legitserver.assistant.runtime.execution.ToolHandler;
import de.legitserver.assistant.runtime.registry.ToolRegistry;
import de.legitserver.assistant.security.secret.SecretGuard;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Registers B7 Self-Development tools into the existing Foundation ToolRegistry. */
public final class BuggySelfDevelopmentToolRegistrar {
    private final SecretGuard secretGuard;

    public BuggySelfDevelopmentToolRegistrar(SecretGuard secretGuard) {
        this.secretGuard = Objects.requireNonNull(secretGuard, "secretGuard");
    }

    public void registerAll(ToolRegistry registry, BuggyToolHandlerResolver resolver) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(resolver, "resolver");
        for (BuggySelfDevelopmentToolDefinition definition : BuggySelfDevelopmentToolCatalog.definitions()) {
            BuggyToolHandlerBinding binding = resolver.resolve(definition.descriptor().toolId())
                    .orElseThrow(() -> new IllegalStateException("missing typed Self-Development handler: "
                            + definition.descriptor().toolId()));
            BuggyToolHandlerKind expected = definition.approvalPolicy() == BuggyToolApprovalPolicy.REQUEST_ONLY
                    ? BuggyToolHandlerKind.REQUEST_CREATOR : BuggyToolHandlerKind.DIRECT_TYPED;
            if (binding.kind() != expected) {
                throw new IllegalStateException("handler kind mismatch for " + definition.descriptor().toolId());
            }
            ToolHandler delegate = binding.handler();
            registry.register(definition.descriptor(), context -> {
                if (!definition.allows(context.resource())) {
                    throw new IllegalArgumentException("resource type outside Self-Development tool scope");
                }
                rejectSecrets(context.input());
                return delegate.execute(context);
            });
        }
    }

    private void rejectSecrets(Map<String, Object> input) {
        for (Object value : input.values()) scan(value);
    }

    private void scan(Object value) {
        if (value instanceof String text && secretGuard.redact(text).secretDetected()) {
            throw new IllegalArgumentException("secret-bearing Self-Development tool input rejected");
        }
        if (value instanceof List<?> values) {
            for (Object item : values) scan(item);
        }
        if (value instanceof Map<?, ?> values) {
            for (Object item : values.values()) scan(item);
        }
    }
}
