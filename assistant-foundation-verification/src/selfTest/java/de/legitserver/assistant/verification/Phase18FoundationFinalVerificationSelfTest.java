package de.legitserver.assistant.verification;

import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.actor.ActorType;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.core.assistant.AssistantCapabilities;
import de.legitserver.assistant.core.assistant.AssistantDescriptor;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.security.authn.BoundedReplayProtector;
import de.legitserver.assistant.security.authn.HmacServiceTokenService;
import de.legitserver.assistant.security.authn.ServicePrincipal;
import de.legitserver.assistant.security.authn.ServiceScopes;
import de.legitserver.assistant.security.authn.ServiceTokenClaims;
import de.legitserver.assistant.security.authorization.ActorResolution;
import de.legitserver.assistant.security.authorization.AssistantAuthorizationService;
import de.legitserver.assistant.security.authorization.AssistantSecurityCatalog;
import de.legitserver.assistant.security.authorization.AuthoritativeSecuritySnapshot;
import de.legitserver.assistant.security.authorization.AuthorizationDenialReason;
import de.legitserver.assistant.security.authorization.AuthorizationRequest;
import de.legitserver.assistant.security.authorization.ResourceAuthorizationStatus;
import de.legitserver.assistant.security.authorization.SecuritySnapshotLookup;
import de.legitserver.assistant.security.authorization.StaticAssistantCapabilityPolicy;
import de.legitserver.assistant.security.cache.CachingSecuritySnapshotPort;
import de.legitserver.assistant.security.control.InMemorySecurityControlPlane;
import de.legitserver.assistant.security.control.OperationalMode;
import de.legitserver.assistant.security.control.SecurityFeature;
import de.legitserver.assistant.security.control.SecurityKillSwitch;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Cross-module Phase-18 acceptance verification. */
public final class Phase18FoundationFinalVerificationSelfTest {
    private static final Instant NOW = Instant.parse("2026-10-04T16:00:00Z");
    private static final UUID ACTOR_UUID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final ActorId ACTOR_ID = new ActorId("actor:aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final String NETWORK_ID = "network:aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    private Phase18FoundationFinalVerificationSelfTest() {}

    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Path.of(".").toAbsolutePath().normalize() : Path.of(args[0]).toAbsolutePath().normalize();

        verifyDeployableFoundation(root);
        verifySecurityFoundation(root);
        verifyCapabilitiesAndResourceAuthorization(root);
        verifyConversationFoundation(root);
        verifyKnowledgeFoundation(root);
        verifyBugFoundation(root);
        verifyRequestApprovalFoundation(root);
        verifyToolRuntime(root);
        verifySupervisorAndSelfHealing(root);
        verifyIntegrationPorts(root);
        verifyDataSecurity(root);
        verifyRuntimeControlsAndPagination(root);
        verifyObservability(root);
        verifyPanelIntegration(root);
        verifyTestAndOperationsCoverage(root);
        verifyEmergencyControls();

        System.out.println("PHASE18_FOUNDATION_FINAL_VERIFICATION_SELF_TEST: PASS");
    }

    private static void verifyDeployableFoundation(Path root) throws IOException {
        requireFile(root, "Dockerfile");
        requireFile(root, "deployment/kubernetes/base/deployment.yaml");
        requireFile(root, "deployment/kubernetes/base/networkpolicy-default-deny.yaml");
        requireFile(root, "deployment/postgres/backup-postgres.sh");
        requireFile(root, "deployment/postgres/restore-postgres.sh");
        requireContains(root.resolve("Dockerfile"), "USER 10001:10001");
        requireContains(root.resolve("deployment/kubernetes/base/deployment.yaml"), "runAsNonRoot: true");
        requireContains(root.resolve("deployment/kubernetes/base/deployment.yaml"), "readOnlyRootFilesystem: true");
        requireContains(root.resolve("deployment/kubernetes/base/deployment.yaml"), "allowPrivilegeEscalation: false");
        pass("deployable");
    }

    private static void verifySecurityFoundation(Path root) throws IOException {
        requireDir(root, "assistant-security");
        requireFile(root, "assistant-security/src/main/java/de/legitserver/assistant/security/authn/HmacServiceTokenService.java");
        requireFile(root, "assistant-integrations/src/main/java/de/legitserver/assistant/integrations/networkcore/NetworkCoreAuthorizationAdapter.java");
        requireContains(root.resolve("assistant-security/src/main/java/de/legitserver/assistant/security/authorization/AssistantAuthorizationService.java"), "AUTHORITY_UNAVAILABLE");
        require(AssistantSecurityCatalog.rules().keySet().containsAll(AssistantCapabilities.initialNamespace()), "security catalog must retain every accepted Foundation capability");
        pass("security-foundation");
    }

    private static void verifyCapabilitiesAndResourceAuthorization(Path root) throws IOException {
        requireFile(root, "assistant-security/src/main/java/de/legitserver/assistant/security/authorization/ResourceAuthorizationPort.java");
        requireFile(root, "assistant-security/src/main/java/de/legitserver/assistant/security/cache/CachingSecuritySnapshotPort.java");
        requireContains(root.resolve("assistant-security/src/main/java/de/legitserver/assistant/security/authorization/AssistantAuthorizationService.java"), "RESOURCE_AUTHORIZATION_UNAVAILABLE");
        pass("capability-resource-authorization-cache");
    }

    private static void verifyConversationFoundation(Path root) throws IOException {
        requireFile(root, "assistant-conversations/src/main/java/de/legitserver/assistant/conversations/model/Conversation.java");
        requireFile(root, "assistant-conversations/src/main/java/de/legitserver/assistant/conversations/model/ConversationState.java");
        requireSourceContainsAny(root.resolve("assistant-conversations/src/main/java"), "expiresAt", "ConversationExpiryService");
        pass("conversations");
    }

    private static void verifyKnowledgeFoundation(Path root) throws IOException {
        requireFile(root, "assistant-knowledge/src/main/java/de/legitserver/assistant/knowledge/model/KnowledgeDocument.java");
        requireSourceContainsAny(root.resolve("assistant-knowledge/src/main/java"), "PUBLISHED", "NO_RELIABLE_RESULT");
        requireSourceContainsAny(root.resolve("assistant-persistence/src/main/resources"), "knowledge", "immutable");
        pass("knowledge");
    }

    private static void verifyBugFoundation(Path root) throws IOException {
        requireFile(root, "assistant-bugs/src/main/java/de/legitserver/assistant/bugs/model/BugRecord.java");
        requireSourceContainsAny(root.resolve("assistant-bugs/src/main/java"), "originalReport", "history", "incident");
        requireSourceContainsAny(root.resolve("assistant-persistence/src/main/resources"), "bug", "immutable");
        pass("bugs");
    }

    private static void verifyRequestApprovalFoundation(Path root) throws IOException {
        requireDir(root, "assistant-requests");
        requireSourceContainsAny(root.resolve("assistant-requests/src/main/java"), "payloadSha256", "expiresAt", "APPROVED");
        requireSourceContainsAny(root.resolve("assistant-persistence/src/main/resources"), "approval", "payload_sha256");
        pass("requests-approvals");
    }

    private static void verifyToolRuntime(Path root) throws IOException {
        requireFile(root, "assistant-runtime/src/main/java/de/legitserver/assistant/runtime/model/ToolDescriptor.java");
        requireSourceContainsAny(root.resolve("assistant-runtime/src/main/java"), "maxConcurrency", "timeout");
        requireNoForbiddenExecutionContract(root.resolve("assistant-runtime/src/main/java"));
        pass("tool-runtime");
    }

    private static void verifySupervisorAndSelfHealing(Path root) throws IOException {
        requireFile(root, "assistant-supervisor/src/main/java/de/legitserver/assistant/supervisor/read/AssistantSupervisorReadPort.java");
        requireSourceContainsAny(root.resolve("assistant-supervisor/src/main/java"), "cooldown", "maxAttempts", "selfHealing");
        var selfHealRule = AssistantSecurityCatalog.rule(AssistantCapabilities.SELF_HEAL_LOW).orElseThrow();
        require(selfHealRule.killSwitches().contains(SecurityKillSwitch.ALL_TOOLS_OFF), "self-healing must obey ALL_TOOLS_OFF");
        require(selfHealRule.killSwitches().contains(SecurityKillSwitch.SELF_HEALING_OFF), "self-healing must obey SELF_HEALING_OFF");
        pass("supervisor-self-healing");
    }

    private static void verifyIntegrationPorts(Path root) throws IOException {
        requireFile(root, "assistant-integration-contracts/src/main/java/de/legitserver/assistant/contracts/source/SourceRegistryPort.java");
        requireFile(root, "assistant-integration-contracts/src/main/java/de/legitserver/assistant/contracts/diagnostics/DiagnosticReadPort.java");
        requireFile(root, "assistant-integration-contracts/src/main/java/de/legitserver/assistant/contracts/write/ControlledWritePort.java");
        requireFile(root, "assistant-integration-contracts/src/main/java/de/legitserver/assistant/contracts/inference/InferencePort.java");
        requireFile(root, "assistant-integration-contracts/src/main/java/de/legitserver/assistant/contracts/external/ExternalAnalysisPort.java");
        requireNoForbiddenExecutionContract(root.resolve("assistant-integration-contracts/src/main/java"));
        pass("integration-ports");
    }

    private static void verifyDataSecurity(Path root) throws IOException {
        requireSourceContainsAny(root.resolve("assistant-security/src/main/java"), "SecretGuard", "DataClassification");
        requireSourceContainsAny(root.resolve("assistant-runtime/src/main/java"), "redact", "classification");
        requireSourceContainsAny(root.resolve("assistant-pipeline/src/main/java"), "ResponseSecurity", "classification");
        pass("secret-output-classification");
    }

    private static void verifyRuntimeControlsAndPagination(Path root) throws IOException {
        requireFile(root, "assistant-pipeline/src/main/java/de/legitserver/assistant/pipeline/policy/SlidingWindowRateLimiter.java");
        requireFile(root, "assistant-pipeline/src/main/java/de/legitserver/assistant/pipeline/control/BoundedAdmissionController.java");
        Path openApi = root.resolve("assistant-api/src/main/resources/openapi/assistant-v1.yaml");
        String yaml = Files.readString(openApi, StandardCharsets.UTF_8);
        require(countLines(yaml, "  /") == 25, "OpenAPI must expose 25 paths at Phase 18 baseline");
        require(countOperationLines(yaml) == 26, "OpenAPI must expose 26 operations at Phase 18 baseline");
        require(yaml.contains("name: pageSize"), "OpenAPI must document pageSize pagination");
        require(yaml.contains("maximum: 100"), "OpenAPI pageSize must remain bounded to 100");
        require(yaml.contains("name: Idempotency-Key"), "OpenAPI must document Idempotency-Key");
        pass("rate-limit-backpressure-pagination");
    }

    private static void verifyObservability(Path root) throws IOException {
        requireDir(root, "assistant-observability");
        requireSourceContainsAny(root.resolve("assistant-observability/src/main/java"), "Audit", "Metric", "Health", "Trace");
        requireContains(root.resolve("assistant-bootstrap/src/main/resources/application.yml"), "livenessState");
        requireContains(root.resolve("assistant-bootstrap/src/main/resources/application.yml"), "readinessState");
        pass("audit-logging-metrics-health-tracing");
    }

    private static void verifyPanelIntegration(Path root) throws IOException {
        requireDir(root, "assistant-panel");
        requireFile(root, "assistant-panel/src/main/java/de/legitserver/assistant/panel/security/HmacPanelUserContextService.java");
        requireFile(root, "assistant-panel/src/main/java/de/legitserver/assistant/panel/realtime/PanelRealtimeHub.java");
        requireSourceContainsAny(root.resolve("assistant-panel/src/main/java"), "replay", "requestId", "traceId");
        pass("panel-integration");
    }

    private static void verifyTestAndOperationsCoverage(Path root) throws IOException {
        requireDir(root, "assistant-hardening/src/selfTest/java");
        requireDir(root, "assistant-operations/src/selfTest/java");
        requireFile(root, "assistant-hardening/src/selfTest/java/de/legitserver/assistant/hardening/SecurityAttackSuite.java");
        requireFile(root, "assistant-hardening/src/selfTest/java/de/legitserver/assistant/hardening/PerformanceSuite.java");
        requireFile(root, "assistant-hardening/src/selfTest/java/de/legitserver/assistant/hardening/MemorySoakSuite.java");
        requireFile(root, "assistant-operations/src/selfTest/java/de/legitserver/assistant/operations/Phase17DeploymentOperationsSelfTest.java");
        pass("test-failure-load-restore-coverage");
    }

    private static void verifyEmergencyControls() {
        ActorContext actor = new ActorContext(
                ACTOR_ID,
                ActorType.HUMAN,
                Origin.PANEL,
                NETWORK_ID,
                ACTOR_UUID,
                "panel-user-final",
                null,
                new RequestId("req-phase18-emergency"),
                new TraceId("trace-phase18-emergency"),
                "survival",
                42,
                NOW);

        var identity = new de.legitserver.assistant.security.authorization.ResolvedActorIdentity(
                ACTOR_ID, NETWORK_ID, ACTOR_UUID, 42);
        var resolver = (de.legitserver.assistant.security.authorization.AuthoritativeActorResolverPort)
                ignored -> ActorResolution.found(identity);

        Set<String> permissions = Set.of(
                "networkcore.assistant.use.buggy",
                "networkcore.assistant.knowledge.query",
                "networkcore.assistant.bug.create",
                "networkcore.assistant.write.request",
                "networkcore.assistant.selfheal.low");
        AuthoritativeSecuritySnapshot snapshot = new AuthoritativeSecuritySnapshot(
                ACTOR_UUID,
                "developer",
                900,
                "DEVELOPMENT",
                Set.of("DEVELOPMENT"),
                Set.of("usr"),
                permissions,
                Set.of(),
                Set.of(),
                false,
                42,
                NOW);
        var snapshotSource = (de.legitserver.assistant.security.authorization.AuthoritativeSecuritySnapshotPort)
                (uuid, minimumRevision) -> minimumRevision <= 42
                        ? SecuritySnapshotLookup.found(snapshot)
                        : SecuritySnapshotLookup.unavailable();
        var cache = new CachingSecuritySnapshotPort(
                snapshotSource, Duration.ofSeconds(5), 32, Clock.fixed(NOW, ZoneOffset.UTC));
        var capabilities = new StaticAssistantCapabilityPolicy(Map.of(
                AssistantId.BUGGY,
                Set.of(
                        AssistantCapabilities.KNOWLEDGE_QUERY,
                        AssistantCapabilities.BUG_CREATE,
                        AssistantCapabilities.WRITE_REQUEST,
                        AssistantCapabilities.SELF_HEAL_LOW)));
        var resourceAuth = (de.legitserver.assistant.security.authorization.ResourceAuthorizationPort)
                (presented, security, capability, resource) -> ResourceAuthorizationStatus.ALLOW;
        InMemorySecurityControlPlane controls = new InMemorySecurityControlPlane(EnumSet.allOf(SecurityFeature.class), NOW);
        AssistantAuthorizationService authorization = new AssistantAuthorizationService(
                resolver, cache, capabilities, resourceAuth, controls);
        AssistantDescriptor assistant = new AssistantDescriptor(
                AssistantId.BUGGY,
                "BuggyBot",
                true,
                "buggy-final",
                "default-security",
                "buggy-knowledge",
                "default-runtime",
                null,
                18,
                NOW);
        ServicePrincipal service = servicePrincipal();

        AuthorizationRequest knowledge = new AuthorizationRequest(
                service, actor, assistant, AssistantCapabilities.KNOWLEDGE_QUERY, null);
        AuthorizationRequest bugCreate = new AuthorizationRequest(
                service, actor, assistant, AssistantCapabilities.BUG_CREATE, null);
        AuthorizationRequest write = new AuthorizationRequest(
                service, actor, assistant, AssistantCapabilities.WRITE_REQUEST, null);
        AuthorizationRequest selfHeal = new AuthorizationRequest(
                service, actor, assistant, AssistantCapabilities.SELF_HEAL_LOW, null);

        require(authorization.authorize(knowledge).allowed(), "normal-mode read must be allowed");
        require(authorization.authorize(bugCreate).allowed(), "normal-mode mutation must be allowed");

        controls.setOperationalMode(OperationalMode.READ_ONLY, NOW.plusSeconds(1));
        require(authorization.authorize(knowledge).allowed(), "READ_ONLY must preserve authorized reads");
        require(
                authorization.authorize(bugCreate).denialReason() == AuthorizationDenialReason.READ_ONLY_MODE,
                "READ_ONLY must block mutations");

        controls.setOperationalMode(OperationalMode.MAINTENANCE, NOW.plusSeconds(2));
        require(
                authorization.authorize(knowledge).denialReason() == AuthorizationDenialReason.MAINTENANCE_MODE,
                "MAINTENANCE must block assistant actions");

        controls.setOperationalMode(OperationalMode.NORMAL, NOW.plusSeconds(3));
        controls.setKillSwitch(SecurityKillSwitch.ALL_TOOLS_OFF, true, NOW.plusSeconds(4));
        require(
                authorization.authorize(write).denialReason() == AuthorizationDenialReason.KILL_SWITCH_ACTIVE,
                "ALL_TOOLS_OFF must block WRITE_REQUEST");
        require(
                authorization.authorize(selfHeal).denialReason() == AuthorizationDenialReason.KILL_SWITCH_ACTIVE,
                "ALL_TOOLS_OFF must block SELF_HEAL_LOW");

        controls.setKillSwitch(SecurityKillSwitch.ALL_TOOLS_OFF, false, NOW.plusSeconds(5));
        controls.setKillSwitch(SecurityKillSwitch.WRITE_ACTIONS_OFF, true, NOW.plusSeconds(6));
        require(
                authorization.authorize(write).denialReason() == AuthorizationDenialReason.KILL_SWITCH_ACTIVE,
                "WRITE_ACTIONS_OFF must block WRITE_REQUEST");
        controls.setKillSwitch(SecurityKillSwitch.WRITE_ACTIONS_OFF, false, NOW.plusSeconds(7));
        controls.setKillSwitch(SecurityKillSwitch.SELF_HEALING_OFF, true, NOW.plusSeconds(8));
        require(
                authorization.authorize(selfHeal).denialReason() == AuthorizationDenialReason.KILL_SWITCH_ACTIVE,
                "SELF_HEALING_OFF must block SELF_HEAL_LOW");

        pass("emergency-read-only-maintenance-global-kill-switch");
    }

    private static ServicePrincipal servicePrincipal() {
        byte[] key = "phase18-final-verification-key-32b".getBytes(StandardCharsets.UTF_8);
        HmacServiceTokenService tokens = new HmacServiceTokenService(key, new BoundedReplayProtector(32));
        RequestId requestId = new RequestId("req-phase18-service");
        String token = tokens.issue(new ServiceTokenClaims(
                "panel-backend",
                "legit-assistant",
                Set.of(ServiceScopes.INVOKE),
                NOW.minusSeconds(1),
                NOW.plusSeconds(30),
                "jti-phase18-service",
                requestId));
        var verified = tokens.verify(token, "legit-assistant", requestId, Set.of(ServiceScopes.INVOKE), NOW);
        require(verified.authenticated(), "phase18 service credential must verify");
        return verified.principal();
    }

    private static void requireFile(Path root, String relative) {
        require(Files.isRegularFile(root.resolve(relative)), "missing file: " + relative);
    }

    private static void requireDir(Path root, String relative) {
        require(Files.isDirectory(root.resolve(relative)), "missing directory: " + relative);
    }

    private static void requireContains(Path path, String expected) throws IOException {
        require(Files.readString(path, StandardCharsets.UTF_8).contains(expected), path + " must contain: " + expected);
    }

    private static void requireSourceContainsAny(Path root, String... needles) throws IOException {
        require(Files.exists(root), "missing source path: " + root);
        boolean[] found = new boolean[needles.length];
        try (var stream = Files.walk(root)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String text;
                try {
                    text = Files.readString(file, StandardCharsets.UTF_8);
                } catch (IOException ignored) {
                    continue;
                }
                for (int i = 0; i < needles.length; i++) {
                    if (text.contains(needles[i])) found[i] = true;
                }
            }
        }
        for (int i = 0; i < needles.length; i++) {
            require(found[i], root + " must contain token: " + needles[i]);
        }
    }

    private static void requireNoForbiddenExecutionContract(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            for (Path file : stream.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                require(!text.contains("ProcessBuilder"), "forbidden ProcessBuilder contract: " + file);
                require(!text.contains("Runtime.getRuntime().exec"), "forbidden Runtime.exec contract: " + file);
            }
        }
    }

    private static long countLines(String text, String prefix) {
        return text.lines().filter(line -> line.startsWith(prefix)).count();
    }

    private static long countOperationLines(String text) {
        return text.lines().filter(line -> line.matches("    (get|post|put|patch|delete):")).count();
    }

    private static void pass(String gate) {
        System.out.println("PHASE18_GATE " + gate + ": PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
