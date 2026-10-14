package de.legitserver.assistant.buggy.tools;

import de.legitserver.assistant.runtime.model.ToolDescriptor;
import de.legitserver.assistant.security.authorization.ResourceRef;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** B7 Self-Development tool metadata layered on the existing Foundation ToolDescriptor. */
public record BuggySelfDevelopmentToolDefinition(
        ToolDescriptor descriptor,
        String description,
        Set<String> allowedResourceTypes,
        BuggyToolApprovalPolicy approvalPolicy) {
    public BuggySelfDevelopmentToolDefinition {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        description = requireText(description, "description", 320);
        approvalPolicy = Objects.requireNonNull(approvalPolicy, "approvalPolicy");
        if (allowedResourceTypes == null || allowedResourceTypes.isEmpty()) {
            throw new IllegalArgumentException("allowedResourceTypes must not be empty");
        }
        LinkedHashSet<String> safe = new LinkedHashSet<>();
        for (String resource : allowedResourceTypes) safe.add(requireToken(resource));
        allowedResourceTypes = Set.copyOf(safe);
        String id = descriptor.toolId().value();
        if (!id.startsWith("tool.selfdev.")) throw new IllegalArgumentException("B7 tool must use tool.selfdev.* namespace");
        if (id.contains("shell") || id.contains("terminal") || id.contains("exec_raw") || id.contains("execute_raw")) {
            throw new IllegalArgumentException("unrestricted execution IDs are forbidden");
        }
    }

    public boolean allows(ResourceRef resource) {
        return resource != null && allowedResourceTypes.contains(resource.resourceType());
    }

    private static String requireToken(String value) {
        String normalized = requireText(value, "resourceType", 96);
        if (!normalized.matches("[a-z0-9_.:-]+")) throw new IllegalArgumentException("unsafe resourceType");
        return normalized;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null) throw new IllegalArgumentException(field + " must not be null");
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > maxLength) throw new IllegalArgumentException(field + " length out of range");
        return normalized;
    }
}
