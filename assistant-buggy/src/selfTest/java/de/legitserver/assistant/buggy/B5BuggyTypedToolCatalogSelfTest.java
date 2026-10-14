package de.legitserver.assistant.buggy;

import de.legitserver.assistant.buggy.tools.BuggyToolApprovalPolicy;
import de.legitserver.assistant.buggy.tools.BuggyToolCatalog;
import de.legitserver.assistant.buggy.tools.BuggyToolCategory;
import de.legitserver.assistant.buggy.tools.BuggyToolDefinition;
import de.legitserver.assistant.buggy.tools.BuggyToolHandlerBinding;
import de.legitserver.assistant.buggy.tools.BuggyToolHandlerKind;
import de.legitserver.assistant.buggy.tools.BuggyToolRegistrar;
import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.actor.ActorType;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.runtime.execution.ToolExecutionContext;
import de.legitserver.assistant.runtime.model.ToolAuditPolicy;
import de.legitserver.assistant.runtime.model.ToolId;
import de.legitserver.assistant.runtime.model.ToolOutput;
import de.legitserver.assistant.runtime.model.ToolRiskLevel;
import de.legitserver.assistant.runtime.registry.ToolRegistry;
import de.legitserver.assistant.security.authorization.AssistantSecurityCatalog;
import de.legitserver.assistant.security.authorization.ResourceRef;
import de.legitserver.assistant.security.authorization.buggy.BuggySecurityCapabilities;
import de.legitserver.assistant.security.secret.SecretGuard;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** B5 offline verification for the initial typed Buggy catalog and its Foundation runtime binding. */
public final class B5BuggyTypedToolCatalogSelfTest {
    private static final Instant NOW = Instant.parse("2026-10-04T22:45:00Z");

    private B5BuggyTypedToolCatalogSelfTest() {}

    public static void main(String[] args) throws Exception {
        Path projectRoot = args.length == 0 ? Path.of(".").toAbsolutePath().normalize() : Path.of(args[0]);
        verifyCatalogCoverageAndDescriptors();
        verifyRequestOnlyBoundaries();
        verifyNoUniversalProductionEscapeHatch(projectRoot);
        verifyExplicitRegistrationAndBoundaryGuards();
        verifyWebEgressContract();
        System.out.println("B5_BUGGY_TYPED_TOOL_CATALOG_SELF_TEST: PASS");
    }

    private static void verifyCatalogCoverageAndDescriptors() {
        require(BuggyToolCatalog.CATALOG_ID.equals("buggy-tools-v1"), "canonical B5 catalog id");
        require(BuggyToolCatalog.definitions().size() == 146, "expected 146 initial typed tools");
        require(BuggyToolCatalog.categories().equals(Set.of(BuggyToolCategory.values())),
                "all B5 masterplan categories must be represented");
        require(BuggyToolCatalog.byId().size() == BuggyToolCatalog.definitions().size(), "tool IDs must be unique");

        for (BuggyToolDefinition definition : BuggyToolCatalog.definitions()) {
            var descriptor = definition.descriptor();
            require(BuggySecurityCapabilities.all().contains(descriptor.capability()),
                    "tool capability must be in canonical B3 profile: " + descriptor.toolId());
            var securityRule = AssistantSecurityCatalog.rule(descriptor.capability()).orElseThrow();
            require(descriptor.riskLevel().covers(securityRule.riskLevel()),
                    "tool may not understate B3 security risk: " + descriptor.toolId());
            require(!definition.allowedResourceTypes().isEmpty(), "every tool must be resource-scoped");
            require(!descriptor.inputSchema().fields().containsKey("command"), "generic command input is forbidden");
            require(!descriptor.inputSchema().fields().containsKey("shell"), "generic shell input is forbidden");
            require(!descriptor.inputSchema().fields().containsKey("ssh_command"), "raw SSH input is forbidden");
            require(!definition.ownerOnly(), "B5 initial catalog must not expose direct Owner-only execution");
            if (descriptor.riskLevel() == ToolRiskLevel.HIGH || descriptor.riskLevel() == ToolRiskLevel.CRITICAL) {
                require(descriptor.auditPolicy() == ToolAuditPolicy.CRITICAL_REQUIRED,
                        "HIGH/CRITICAL tools require fail-closed audit");
            }
        }

        require(BuggyToolCatalog.byId().containsKey(new ToolId("tool.database.query.readonly")),
                "typed readonly database query exists");
        require(!BuggyToolCatalog.byId().containsKey(new ToolId("tool.sql.execute.raw")),
                "raw SQL execution is absent");
    }

    private static void verifyRequestOnlyBoundaries() {
        Set<ToolId> requestOnly = BuggyToolCatalog.definitions().stream()
                .filter(definition -> definition.approvalPolicy() == BuggyToolApprovalPolicy.REQUEST_ONLY)
                .map(definition -> definition.descriptor().toolId())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        require(requestOnly.contains(new ToolId("tool.deployment.production")), "production deploy terminates at request boundary");
        require(requestOnly.contains(new ToolId("tool.deployment.rollback")), "rollback terminates at request boundary");
        require(requestOnly.contains(new ToolId("tool.database.backup")), "database backup is request-only in B5");
        require(requestOnly.contains(new ToolId("tool.knowledge.request-publish")), "Knowledge publish remains a request");
        require(requestOnly.contains(new ToolId("tool.mail.send.request")), "mail send remains request-only");
        require(BuggyToolCatalog.definitions().stream().noneMatch(definition ->
                        definition.descriptor().toolId().value().contains("approve-self")),
                "self approval tool must not exist");
        require(BuggyToolCatalog.definitions().stream().filter(definition -> definition.category() == BuggyToolCategory.SECURITY)
                        .allMatch(definition -> definition.descriptor().capability().equals(BuggySecurityCapabilities.SECURITY_READ)),
                "B5 Security category is read-only as required by the implementation masterplan");
    }

    private static void verifyNoUniversalProductionEscapeHatch(Path projectRoot) throws Exception {
        Path sourceRoot = projectRoot.resolve("assistant-buggy/src/main/java");
        String[] forbidden = {
            "Process" + "Builder",
            "Runtime." + "getRuntime",
            "docker." + "exec_raw",
            "ssh." + "exec_raw",
            "sql." + "execute_raw",
            "/bin/" + "sh",
            "bash" + " -c"
        };
        try (var paths = Files.walk(sourceRoot)) {
            for (Path file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (String marker : forbidden) {
                    require(!source.contains(marker), "forbidden production escape hatch in " + file.getFileName());
                }
            }
        }
    }

    private static void verifyExplicitRegistrationAndBoundaryGuards() throws Exception {
        BuggyToolRegistrar registrar = new BuggyToolRegistrar(new SecretGuard());
        expectFailure(IllegalStateException.class, () -> registrar.registerAll(new ToolRegistry(), toolId -> java.util.Optional.empty()));

        ToolRegistry registry = new ToolRegistry();
        AtomicInteger executions = new AtomicInteger();

        ToolRegistry badRequestRegistry = new ToolRegistry();
        expectFailure(IllegalStateException.class, () -> registrar.registerAll(badRequestRegistry, toolId ->
                java.util.Optional.of(new BuggyToolHandlerBinding(
                        context -> new ToolOutput(Map.of("status", "OK"), DataClassification.INTERNAL),
                        BuggyToolHandlerKind.DIRECT_TYPED))));
        registrar.registerAll(registry, toolId -> java.util.Optional.of(new BuggyToolHandlerBinding(context -> {
            executions.incrementAndGet();
            return new ToolOutput(Map.of("status", "OK"), DataClassification.INTERNAL);
        }, expectedKind(toolId))));
        require(registry.size() == BuggyToolCatalog.definitions().size(), "all B5 tools register in Foundation ToolRegistry");

        var whitelist = registry.find(new ToolId("tool.minecraft.whitelist.add")).orElseThrow();
        expectFailure(IllegalArgumentException.class, () -> whitelist.handler().execute(new ToolExecutionContext(
                actor(),
                new ResourceRef("host", "network-01", DataClassification.INTERNAL),
                Map.of("player_id", "HimmelJanik"))));
        require(executions.get() == 0, "wrong resource type must fail before delegate handler");

        expectFailure(IllegalArgumentException.class, () -> whitelist.handler().execute(new ToolExecutionContext(
                actor(),
                new ResourceRef("minecraft-server", "lobby", DataClassification.INTERNAL),
                Map.of("player_id", "password=supersecret"))));
        require(executions.get() == 0, "secret input must fail before delegate handler");

        whitelist.handler().execute(new ToolExecutionContext(
                actor(),
                new ResourceRef("minecraft-server", "lobby", DataClassification.INTERNAL),
                Map.of("player_id", "HimmelJanik")));
        require(executions.get() == 1, "valid resource and non-secret input reaches typed delegate");
    }

    private static void verifyWebEgressContract() {
        var policy = BuggyToolCatalog.WEB_EGRESS_POLICY;
        require(policy.allowedSchemes().equals(Set.of("https", "http")), "Web tools only permit HTTP(S)");
        require(policy.blockLoopback() && policy.blockPrivateNetworks() && policy.blockLinkLocal() && policy.blockMetadataNetworks(),
                "SSRF-sensitive address classes must be blocked");
        require(policy.validateEveryRedirect(), "every redirect must be revalidated");
        require(policy.maxRedirects() <= 5, "redirect count is bounded");
        require(policy.maxResponseBytes() <= 2_000_000L, "response size is bounded");
        require(policy.connectTimeoutMillis() <= 5_000 && policy.readTimeoutMillis() <= 15_000,
                "Web execution time is bounded");
    }


    private static BuggyToolHandlerKind expectedKind(ToolId toolId) {
        BuggyToolDefinition definition = BuggyToolCatalog.byId().get(toolId);
        if (definition == null) throw new IllegalStateException("Unknown tool: " + toolId);
        if (definition.approvalPolicy() == BuggyToolApprovalPolicy.REQUEST_ONLY) {
            return BuggyToolHandlerKind.REQUEST_CREATOR;
        }
        if (definition.category() == BuggyToolCategory.WEB) {
            return BuggyToolHandlerKind.BOUNDED_WEB;
        }
        if (toolId.value().equals("tool.database.query.readonly") || toolId.value().equals("tool.database.explain")) {
            return BuggyToolHandlerKind.READ_ONLY_DATABASE;
        }
        return BuggyToolHandlerKind.DIRECT_TYPED;
    }

    private static ActorContext actor() {
        return new ActorContext(
                new ActorId("actor:buggy-owner"),
                ActorType.HUMAN,
                Origin.PANEL,
                "network:buggy-owner",
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                "owner-panel",
                null,
                new RequestId("req-b5-tools"),
                new TraceId("trace-b5-tools"),
                null,
                1L,
                NOW);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Assertion failed: " + message);
    }

    private static void expectFailure(Class<? extends Throwable> type, ThrowingRunnable action) {
        try {
            action.run();
            throw new IllegalStateException("Expected failure: " + type.getSimpleName());
        } catch (Throwable failure) {
            if (!type.isInstance(failure)) {
                throw new IllegalStateException("Wrong failure type: " + failure, failure);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
