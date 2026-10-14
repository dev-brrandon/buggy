package de.legitserver.assistant.runtime.registry;

import de.legitserver.assistant.core.assistant.AssistantCapabilities;
import de.legitserver.assistant.runtime.execution.ToolHandler;
import de.legitserver.assistant.runtime.model.ToolAuditPolicy;
import de.legitserver.assistant.runtime.model.ToolDescriptor;
import de.legitserver.assistant.runtime.model.ToolRiskLevel;
import de.legitserver.assistant.runtime.model.ToolId;
import de.legitserver.assistant.security.authorization.AssistantSecurityCatalog;
import de.legitserver.assistant.security.authorization.CapabilitySecurityRule;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Explicit in-process registry. No tool is synthesized from user-provided command text. */
public final class ToolRegistry {
    private final Map<ToolId, RegisteredTool> tools = new ConcurrentHashMap<>();

    public void register(ToolDescriptor descriptor, ToolHandler handler) {
        if (descriptor == null || handler == null) throw new IllegalArgumentException("descriptor and handler are required");
        CapabilitySecurityRule securityRule = AssistantSecurityCatalog.rule(descriptor.capability())
                .orElseThrow(() -> new IllegalArgumentException("tool capability is not in canonical security catalog"));
        if (!descriptor.riskLevel().covers(securityRule.riskLevel())) {
            throw new IllegalArgumentException("tool risk may not understate capability security risk");
        }
        if (descriptor.selfHealingEligible()) {
            if (!descriptor.capability().equals(AssistantCapabilities.SELF_HEAL_LOW)) {
                throw new IllegalArgumentException("selfHealingEligible is restricted to assistant.selfheal.low");
            }
            if (descriptor.riskLevel() != ToolRiskLevel.LOW) {
                throw new IllegalArgumentException("selfHealingEligible tools must be LOW risk");
            }
            if (descriptor.auditPolicy() != ToolAuditPolicy.CRITICAL_REQUIRED) {
                throw new IllegalArgumentException("selfHealingEligible tools require fail-closed audit");
            }
        }
        RegisteredTool previous = tools.putIfAbsent(descriptor.toolId(), new RegisteredTool(descriptor, handler));
        if (previous != null) throw new IllegalArgumentException("toolId already registered");
    }

    public Optional<RegisteredTool> find(ToolId toolId) {
        return Optional.ofNullable(tools.get(toolId));
    }

    public int size() {
        return tools.size();
    }
}
