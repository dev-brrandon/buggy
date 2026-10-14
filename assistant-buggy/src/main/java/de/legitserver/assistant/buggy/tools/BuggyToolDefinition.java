package de.legitserver.assistant.buggy.tools;

import de.legitserver.assistant.runtime.model.ToolDescriptor;
import de.legitserver.assistant.security.authorization.ResourceRef;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** B5 product metadata that augments, but never replaces, the Foundation ToolDescriptor. */
public record BuggyToolDefinition(
        ToolDescriptor descriptor,
        BuggyToolCategory category,
        String description,
        Set<String> allowedResourceTypes,
        BuggyToolApprovalPolicy approvalPolicy,
        BuggyToolSecretHandling secretHandling,
        boolean ownerOnly) {
    private static final Set<String> FORBIDDEN_UNIVERSAL_IDS = Set.of(
            "tool.shell." + "execute",
            "tool.terminal." + "exec",
            "tool.os." + "command",
            "tool.docker." + "exec_raw",
            "tool.ssh." + "exec_raw",
            "tool.sql." + "execute_raw");

    public BuggyToolDefinition {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        category = Objects.requireNonNull(category, "category");
        description = requireText(description, "description", 320);
        approvalPolicy = Objects.requireNonNull(approvalPolicy, "approvalPolicy");
        secretHandling = Objects.requireNonNull(secretHandling, "secretHandling");
        if (allowedResourceTypes == null || allowedResourceTypes.isEmpty()) {
            throw new IllegalArgumentException("allowedResourceTypes must not be empty");
        }
        LinkedHashSet<String> resources = new LinkedHashSet<>();
        for (String resourceType : allowedResourceTypes) {
            resources.add(requireToken(resourceType, "resourceType", 96));
        }
        allowedResourceTypes = Set.copyOf(resources);

        String id = descriptor.toolId().value();
        if (FORBIDDEN_UNIVERSAL_IDS.contains(id)
                || id.contains("exec_raw")
                || id.contains("execute_raw")
                || id.contains("shell")
                || id.contains("terminal")) {
            throw new IllegalArgumentException("unrestricted production tool IDs are forbidden");
        }
        if (ownerOnly != (approvalPolicy == BuggyToolApprovalPolicy.OWNER_ONLY)) {
            throw new IllegalArgumentException("ownerOnly must exactly match OWNER_ONLY approval policy");
        }
        if (approvalPolicy == BuggyToolApprovalPolicy.REQUEST_ONLY && descriptor.selfHealingEligible()) {
            throw new IllegalArgumentException("REQUEST_ONLY tools cannot be self-healing eligible");
        }
    }

    public boolean allows(ResourceRef resource) {
        return resource != null && allowedResourceTypes.contains(resource.resourceType());
    }

    private static String requireText(String value, String label, int maxLength) {
        if (value == null) throw new IllegalArgumentException(label + " must not be null");
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + " length out of range");
        }
        return normalized;
    }

    private static String requireToken(String value, String label, int maxLength) {
        String normalized = requireText(value, label, maxLength);
        if (!normalized.matches("[a-z0-9_.:-]+")) {
            throw new IllegalArgumentException(label + " contains unsafe characters");
        }
        return normalized;
    }
}
