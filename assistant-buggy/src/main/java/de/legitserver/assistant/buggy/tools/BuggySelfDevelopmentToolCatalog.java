package de.legitserver.assistant.buggy.tools;

import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.runtime.model.ToolAuditPolicy;
import de.legitserver.assistant.runtime.model.ToolClassificationPolicy;
import de.legitserver.assistant.runtime.model.ToolDescriptor;
import de.legitserver.assistant.runtime.model.ToolId;
import de.legitserver.assistant.runtime.model.ToolRiskLevel;
import de.legitserver.assistant.runtime.schema.ToolSchema;
import de.legitserver.assistant.security.authorization.buggy.BuggySecurityCapabilities;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Canonical B7 typed Self-Development additions. They are inert until explicit handlers are bound. */
public final class BuggySelfDevelopmentToolCatalog {
    public static final String CATALOG_ID = "buggy-selfdev-tools-v1";
    private static final List<BuggySelfDevelopmentToolDefinition> DEFINITIONS = List.of(
            tool("observe", BuggySecurityCapabilities.SELFDEVELOPMENT_EXECUTE, BuggyToolSchemas.SELFDEV_OBSERVE,
                    BuggyToolSchemas.ACTION_OUTPUT, Set.of("source-component"), "Record a bounded improvement observation."),
            tool("create-proposal", BuggySecurityCapabilities.SELFDEVELOPMENT_EXECUTE, BuggyToolSchemas.SELFDEV_PROPOSAL,
                    BuggyToolSchemas.ACTION_OUTPUT, Set.of("selfdev-item"), "Create a versioned Self-Development proposal."),
            tool("create-workspace", BuggySecurityCapabilities.SELFDEVELOPMENT_WORKSPACE_MUTATE, BuggyToolSchemas.SELFDEV_WORKSPACE,
                    BuggyToolSchemas.ARTIFACT_OUTPUT, Set.of("source-snapshot"), "Create an isolated disposable workspace from an immutable source snapshot."),
            tool("modify-source", BuggySecurityCapabilities.SELFDEVELOPMENT_WORKSPACE_MUTATE, BuggyToolSchemas.SELFDEV_MODIFY,
                    BuggyToolSchemas.ARTIFACT_OUTPUT, Set.of("build-workspace"), "Modify source only inside the controlled Self-Development workspace."),
            tool("build", BuggySecurityCapabilities.SELFDEVELOPMENT_EXECUTE, BuggyToolSchemas.SELFDEV_RUN,
                    BuggyToolSchemas.ARTIFACT_OUTPUT, Set.of("build-workspace"), "Run a bounded build in the isolated workspace."),
            tool("test", BuggySecurityCapabilities.SELFDEVELOPMENT_EXECUTE, BuggyToolSchemas.SELFDEV_RUN,
                    BuggyToolSchemas.STATUS_OUTPUT, Set.of("build-workspace", "build-artifact"), "Run bounded tests against the candidate change."),
            tool("benchmark", BuggySecurityCapabilities.SELFDEVELOPMENT_EXECUTE, BuggyToolSchemas.SELFDEV_BENCHMARK,
                    BuggyToolSchemas.STATUS_OUTPUT, Set.of("build-workspace", "build-artifact"), "Run bounded performance/impact benchmarks."),
            tool("security-scan", BuggySecurityCapabilities.SELFDEVELOPMENT_EXECUTE, BuggyToolSchemas.SELFDEV_RUN,
                    BuggyToolSchemas.STATUS_OUTPUT, Set.of("build-workspace", "build-artifact"), "Run bounded security checks for the candidate change."),
            tool("compare", BuggySecurityCapabilities.SELFDEVELOPMENT_EXECUTE, BuggyToolSchemas.SELFDEV_COMPARE,
                    BuggyToolSchemas.READ_OUTPUT, Set.of("selfdev-item"), "Compare baseline and candidate evidence without deployment."),
            tool("reject-own-proposal", BuggySecurityCapabilities.SELFDEVELOPMENT_EXECUTE, BuggyToolSchemas.SELFDEV_REJECT,
                    BuggyToolSchemas.ACTION_OUTPUT, Set.of("selfdev-item"), "Reject Buggy's own proposal based on recorded evidence."),
            requestTool("submit-for-approval", BuggySecurityCapabilities.REQUESTS_CREATE, BuggyToolSchemas.SELFDEV_SUBMIT,
                    BuggyToolSchemas.REQUEST_OUTPUT, Set.of("selfdev-item"), "Submit the exact tested promotion package into Foundation Requests/Approvals."));
    private static final Map<ToolId, BuggySelfDevelopmentToolDefinition> BY_ID = index();

    private BuggySelfDevelopmentToolCatalog() {}

    public static List<BuggySelfDevelopmentToolDefinition> definitions() {
        return DEFINITIONS;
    }

    public static Map<ToolId, BuggySelfDevelopmentToolDefinition> byId() {
        return BY_ID;
    }

    private static BuggySelfDevelopmentToolDefinition tool(
            String id,
            AssistantCapabilityId capability,
            ToolSchema input,
            ToolSchema output,
            Set<String> resources,
            String description) {
        return definition(id, capability, input, output, resources, description, BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED);
    }

    private static BuggySelfDevelopmentToolDefinition requestTool(
            String id,
            AssistantCapabilityId capability,
            ToolSchema input,
            ToolSchema output,
            Set<String> resources,
            String description) {
        return definition(id, capability, input, output, resources, description, BuggyToolApprovalPolicy.REQUEST_ONLY);
    }

    private static BuggySelfDevelopmentToolDefinition definition(
            String id,
            AssistantCapabilityId capability,
            ToolSchema input,
            ToolSchema output,
            Set<String> resources,
            String description,
            BuggyToolApprovalPolicy approvalPolicy) {
        ToolDescriptor descriptor = new ToolDescriptor(
                new ToolId("tool.selfdev." + id),
                capability,
                ToolRiskLevel.MEDIUM,
                input,
                output,
                Duration.ofSeconds(120),
                4,
                ToolAuditPolicy.REQUIRED,
                new ToolClassificationPolicy(DataClassification.CONFIDENTIAL, DataClassification.CONFIDENTIAL),
                false);
        return new BuggySelfDevelopmentToolDefinition(descriptor, description, resources, approvalPolicy);
    }

    private static Map<ToolId, BuggySelfDevelopmentToolDefinition> index() {
        LinkedHashMap<ToolId, BuggySelfDevelopmentToolDefinition> result = new LinkedHashMap<>();
        for (BuggySelfDevelopmentToolDefinition definition : DEFINITIONS) {
            if (result.put(definition.descriptor().toolId(), definition) != null) {
                throw new IllegalStateException("duplicate Self-Development tool ID");
            }
        }
        return Map.copyOf(result);
    }
}
