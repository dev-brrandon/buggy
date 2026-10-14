package de.legitserver.assistant.buggy;

import de.legitserver.assistant.buggy.config.BuggyManifest;
import de.legitserver.assistant.buggy.exclusive.BuggyExclusiveFeatureCatalog;
import de.legitserver.assistant.buggy.identity.BuggyIdentityCore;
import de.legitserver.assistant.buggy.personality.BuggyPersonalitySeed;
import de.legitserver.assistant.buggy.supervision.BuggySupervisorContractCatalog;
import de.legitserver.assistant.buggy.tools.BuggyToolCatalog;
import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.actor.ActorType;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.core.assistant.AssistantDescriptor;
import de.legitserver.assistant.core.assistant.AssistantId;
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
import de.legitserver.assistant.security.authorization.AuthorizationDecision;
import de.legitserver.assistant.security.authorization.AuthorizationDenialReason;
import de.legitserver.assistant.security.authorization.AuthorizationRequest;
import de.legitserver.assistant.security.authorization.ResourceAuthorizationStatus;
import de.legitserver.assistant.security.authorization.ResolvedActorIdentity;
import de.legitserver.assistant.security.authorization.SecuritySnapshotLookup;
import de.legitserver.assistant.security.authorization.buggy.BuggyCapabilityPolicy;
import de.legitserver.assistant.security.authorization.buggy.BuggyCapabilityProfile;
import de.legitserver.assistant.security.authorization.buggy.BuggySecurityCapabilities;
import de.legitserver.assistant.security.control.InMemorySecurityControlPlane;
import de.legitserver.assistant.security.control.OperationalMode;
import de.legitserver.assistant.security.control.SecurityFeature;
import de.legitserver.assistant.security.control.SecurityKillSwitch;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Final Buggy V1 acceptance and hardening gate for B12. */
public final class B12BuggyAcceptanceHardeningSelfTest {
    private static final Instant NOW = Instant.parse("2026-10-05T01:30:00Z");
    private static final UUID ACTOR_UUID = UUID.fromString("55555555-6666-7777-8888-999999999999");
    private static final ActorId ACTOR_ID = new ActorId("actor:buggy-b12-owner");
    private static final String NETWORK_ID = "network:buggy-b12-owner";
    private static volatile long blackhole;

    private B12BuggyAcceptanceHardeningSelfTest() {}

    public static void main(String[] args) throws Exception {
        Path projectRoot = args.length == 0
                ? Path.of(".").toAbsolutePath().normalize()
                : Path.of(args[0]).toAbsolutePath().normalize();
        verifyCanonicalDefinitionOfDone(projectRoot);
        verifyEmergencyAndAuthorityFailClosed();
        verifyDeterministicConcurrentReadLoad();
        runBuggyRegressionChain(projectRoot);
        System.out.println("B12_BUGGY_ACCEPTANCE_HARDENING_SELF_TEST: PASS");
    }

    private static void verifyCanonicalDefinitionOfDone(Path projectRoot) throws Exception {
        BuggyIdentityCore identity = BuggyIdentityCore.canonical(NOW);
        require(identity.assistantId() == AssistantId.BUGGY, "persistent Buggy identity");
        require(identity.serviceIdentity().equals("assistant:buggy"), "canonical service identity");
        require(identity.securityAuthority().equals("NETWORKCORE"), "NetworkCore remains identity security authority");

        BuggyManifest manifest = BuggyManifest.initial().withPersonalityProfileId(BuggyPersonalitySeed.PROFILE_ID);
        require(manifest.capabilityProfileId().equals(BuggyCapabilityProfile.PROFILE_ID), "canonical B3 capability profile");
        require(!manifest.selfApprovalAllowed(), "self approval remains impossible");
        require(!manifest.authoritySelfEscalationAllowed(), "self authority escalation remains impossible");
        require(!manifest.riskDowngradeAllowed(), "risk downgrade remains impossible");

        require(BuggySecurityCapabilities.all().size() == 39, "canonical atomic capability count");
        require(BuggyToolCatalog.definitions().size() == 146, "canonical B5 productive tool count");
        require(BuggyExclusiveFeatureCatalog.features().size() >= 10, "B10 exclusive feature inventory present");
        require(BuggySupervisorContractCatalog.futureTargets().equals(Set.of(AssistantId.BYTE, AssistantId.KAESE)),
                "future supervisor targets remain BYTE/KAESE only");

        for (AssistantCapabilityId capability : BuggySecurityCapabilities.all()) {
            require(AssistantSecurityCatalog.rule(capability).isPresent(), "every Buggy capability has Foundation security rule");
        }
        require(AssistantSecurityCatalog.rule(BuggySecurityCapabilities.DEPLOYMENT_PRODUCTION_REQUEST).isPresent(),
                "production deployment remains capability-gated");

        verifyNoFutureAssistantImplementation(projectRoot);
        verifyNoProductionEscapeHatch(projectRoot);
        verifyPanelTrustBoundary(projectRoot);
        verifyBuggyMigrations(projectRoot);
    }

    private static void verifyEmergencyAndAuthorityFailClosed() {
        Fixture readFixture = fixture(permissionFor(BuggySecurityCapabilities.DIAGNOSTICS_READ));
        AuthorizationRequest read = request(readFixture.service(), BuggySecurityCapabilities.DIAGNOSTICS_READ);
        require(readFixture.authorization().authorize(read).allowed(), "baseline read authorization");

        readFixture.controls().setKillSwitch(SecurityKillSwitch.ALL_TOOLS_OFF, true, NOW.plusSeconds(1));
        require(readFixture.authorization().authorize(read).denialReason() == AuthorizationDenialReason.KILL_SWITCH_ACTIVE,
                "ALL_TOOLS_OFF overrides Buggy");
        readFixture.controls().setKillSwitch(SecurityKillSwitch.ALL_TOOLS_OFF, false, NOW.plusSeconds(2));
        readFixture.controls().setOperationalMode(OperationalMode.MAINTENANCE, NOW.plusSeconds(3));
        require(readFixture.authorization().authorize(read).denialReason() == AuthorizationDenialReason.MAINTENANCE_MODE,
                "MAINTENANCE overrides Buggy reads");

        Fixture mutateFixture = fixture(permissionFor(BuggySecurityCapabilities.OPERATIONS_MUTATE_LOW));
        AuthorizationRequest mutate = request(mutateFixture.service(), BuggySecurityCapabilities.OPERATIONS_MUTATE_LOW);
        require(mutateFixture.authorization().authorize(mutate).allowed(), "baseline bounded mutation authorization");
        mutateFixture.controls().setOperationalMode(OperationalMode.READ_ONLY, NOW.plusSeconds(4));
        require(mutateFixture.authorization().authorize(mutate).denialReason() == AuthorizationDenialReason.READ_ONLY_MODE,
                "READ_ONLY blocks Buggy mutation");

        Fixture unavailable = fixture(permissionFor(BuggySecurityCapabilities.DIAGNOSTICS_READ));
        unavailable.securitySnapshot().set(SecuritySnapshotLookup.unavailable());
        require(unavailable.authorization().authorize(request(unavailable.service(), BuggySecurityCapabilities.DIAGNOSTICS_READ))
                        .denialReason() == AuthorizationDenialReason.AUTHORITY_UNAVAILABLE,
                "NetworkCore/security snapshot outage fails closed");
    }

    private static void verifyDeterministicConcurrentReadLoad() throws Exception {
        List<de.legitserver.assistant.runtime.model.ToolId> toolIds = List.copyOf(BuggyToolCatalog.byId().keySet());
        int workers = 8;
        int iterationsPerWorker = 20_000;
        var pool = Executors.newFixedThreadPool(workers);
        try {
            List<Callable<Long>> tasks = new ArrayList<>();
            for (int worker = 0; worker < workers; worker++) {
                int seed = worker;
                tasks.add(() -> {
                    long local = 0L;
                    for (int i = 0; i < iterationsPerWorker; i++) {
                        var tool = BuggyToolCatalog.byId().get(toolIds.get((i + seed) % toolIds.size()));
                        require(tool != null, "concurrent tool lookup must remain deterministic");
                        local += tool.descriptor().maxConcurrency();
                        local += BuggySecurityCapabilities.all().contains(tool.descriptor().capability()) ? 1L : 0L;
                        local += BuggyExclusiveFeatureCatalog.features().size();
                        local += BuggySupervisorContractCatalog.contract((i & 1) == 0 ? AssistantId.BYTE : AssistantId.KAESE)
                                .operations().size();
                    }
                    return local;
                });
            }
            long started = System.nanoTime();
            long checksum = 0L;
            for (var future : pool.invokeAll(tasks)) checksum ^= future.get();
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            blackhole ^= checksum;
            require(elapsedMs < 30_000L, "deterministic B12 concurrent read load exceeded 30s: " + elapsedMs + "ms");
            System.out.println("B12_BUGGY_LOAD: PASS operations=" + (workers * iterationsPerWorker)
                    + " elapsedMs=" + elapsedMs + " checksum=" + Long.toUnsignedString(checksum ^ blackhole));
        } finally {
            pool.shutdownNow();
            require(pool.awaitTermination(5, TimeUnit.SECONDS), "B12 load executor terminates cleanly");
        }
    }

    private static void runBuggyRegressionChain(Path projectRoot) throws Exception {
        B1BuggyIdentityRuntimeSelfTest.main(new String[0]);
        B2BuggyPersonalityEvolutionSelfTest.main(new String[0]);
        B4BuggyKnowledgeDecisionMemorySelfTest.main(new String[0]);
        B5BuggyTypedToolCatalogSelfTest.main(new String[] {projectRoot.toString()});
        B6BuggyTasksActivityAutomationSelfTest.main(new String[0]);
        B7BuggySelfDevelopmentLaboratorySelfTest.main(new String[0]);
        B8BuggyControlCenterBackendSelfTest.main(new String[0]);
        B10BuggyExclusiveFeaturesSelfTest.main(new String[0]);
        B11BuggyFutureAssistantSupervisorReadinessSelfTest.main(new String[] {projectRoot.toString()});
        System.out.println("B12_BUGGY_REGRESSION_CHAIN: PASS B1,B2,B4,B5,B6,B7,B8,B10,B11");
    }

    private static void verifyNoFutureAssistantImplementation(Path projectRoot) {
        require(!Files.exists(projectRoot.resolve("assistant-byte")), "BYTE implementation must remain not started");
        require(!Files.exists(projectRoot.resolve("assistant-kaese")), "KAESE implementation must remain not started");
    }

    private static void verifyNoProductionEscapeHatch(Path projectRoot) throws Exception {
        List<String> forbidden = List.of(
                "Process" + "Builder(",
                "Runtime.get" + "Runtime().exec",
                "tool.shell." + "execute",
                "tool.terminal." + "exec",
                "tool.docker." + "exec_raw",
                "tool.ssh." + "exec_raw",
                "tool.sql." + "execute_raw");
        try (var paths = Files.walk(projectRoot)) {
            for (Path file : paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (String marker : forbidden) {
                    require(!source.contains(marker), "forbidden production escape hatch: " + marker + " in " + file);
                }
            }
        }
    }

    private static void verifyPanelTrustBoundary(Path projectRoot) throws Exception {
        Path script = projectRoot.resolve(
                "assistant-panel/src/main/resources/META-INF/legit-panel/buggy-control-center/assets/control-center.js");
        String source = Files.readString(script);
        for (String forbidden : List.of("fetch(", "XMLHttpRequest", "EventSource", "WebSocket", "/api/assistant/v1",
                "localStorage", "sessionStorage", "document.cookie", ".innerHTML", "eval(")) {
            require(!source.contains(forbidden), "B9 browser trust-boundary regression: " + forbidden);
        }
        require(source.contains("LegitPanelAssistantBridge"), "B9 trusted Panel Backend bridge remains mandatory");
    }

    private static void verifyBuggyMigrations(Path projectRoot) throws Exception {
        Path migrations = projectRoot.resolve("assistant-persistence/src/main/resources/db/migration");
        try (var paths = Files.list(migrations)) {
            List<String> buggy = paths.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("V100_"))
                    .sorted()
                    .toList();
            require(buggy.equals(List.of(
                    "V100_001__create_buggy_identity_runtime.sql",
                    "V100_002__create_buggy_personality_evolution.sql",
                    "V100_003__create_buggy_knowledge_annotations.sql",
                    "V100_004__create_buggy_tasks_activity_automation.sql",
                    "V100_005__create_buggy_self_development.sql",
                    "V100_006__create_buggy_native_core_foundation.sql",
                    "V100_007__create_buggy_memory_development.sql")),
                    "B12/G1.5 allows only the explicitly reviewed Buggy persistence schema: " + buggy);
        }
    }

    private static Fixture fixture(Set<String> permissions) {
        ResolvedActorIdentity resolved = new ResolvedActorIdentity(ACTOR_ID, NETWORK_ID, ACTOR_UUID, 1L);
        AtomicReference<SecuritySnapshotLookup> snapshot = new AtomicReference<>(SecuritySnapshotLookup.found(snapshot(permissions)));
        InMemorySecurityControlPlane controls = new InMemorySecurityControlPlane(EnumSet.allOf(SecurityFeature.class), NOW);
        AssistantAuthorizationService authorization = new AssistantAuthorizationService(
                actor -> ActorResolution.found(resolved),
                (uuid, minimumRevision) -> snapshot.get(),
                new BuggyCapabilityPolicy(),
                (actor, security, capability, resource) -> ResourceAuthorizationStatus.ALLOW,
                controls);
        return new Fixture(authorization, servicePrincipal(), snapshot, controls);
    }

    private static Set<String> permissionFor(AssistantCapabilityId capability) {
        var rule = AssistantSecurityCatalog.rule(capability).orElseThrow();
        java.util.LinkedHashSet<String> permissions = new java.util.LinkedHashSet<>();
        permissions.add("networkcore.assistant.use.buggy");
        permissions.addAll(rule.allCapabilityPermissions());
        return Set.copyOf(permissions);
    }

    private static AuthoritativeSecuritySnapshot snapshot(Set<String> permissions) {
        return new AuthoritativeSecuritySnapshot(
                ACTOR_UUID,
                "owner",
                10_000,
                "OWNER",
                Set.of("OWNER"),
                Set.of("usr"),
                permissions,
                Set.of(),
                Set.of(),
                true,
                1L,
                NOW);
    }

    private static AuthorizationRequest request(ServicePrincipal service, AssistantCapabilityId capability) {
        return new AuthorizationRequest(service, actor(), descriptor(), capability, null);
    }

    private static ActorContext actor() {
        return new ActorContext(
                ACTOR_ID,
                ActorType.HUMAN,
                Origin.PANEL,
                NETWORK_ID,
                ACTOR_UUID,
                "owner-panel",
                null,
                new RequestId("req-b12-authorization"),
                new TraceId("trace-b12-authorization"),
                null,
                1L,
                NOW);
    }

    private static AssistantDescriptor descriptor() {
        return new AssistantDescriptor(
                AssistantId.BUGGY,
                "Buggy",
                true,
                BuggyCapabilityProfile.PROFILE_ID,
                "buggy-policy-v1",
                "buggy-knowledge-v1",
                "buggy-runtime-v1",
                BuggyPersonalitySeed.PROFILE_ID,
                12L,
                NOW);
    }

    private static ServicePrincipal servicePrincipal() {
        byte[] key = "89abcdef0123456789abcdef01234567".getBytes(StandardCharsets.UTF_8);
        RequestId requestId = new RequestId("req-b12-service");
        HmacServiceTokenService tokens = new HmacServiceTokenService(key, new BoundedReplayProtector(16));
        String token = tokens.issue(new ServiceTokenClaims(
                "assistant-runtime-b12",
                "legit-assistant",
                Set.of(ServiceScopes.INVOKE),
                NOW.minusSeconds(1),
                NOW.plusSeconds(60),
                "jti-b12-service",
                requestId));
        var result = tokens.verify(token, "legit-assistant", requestId, Set.of(ServiceScopes.INVOKE), NOW);
        require(result.authenticated(), "B12 service principal authenticates");
        return result.principal();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("B12 acceptance failed: " + message);
    }

    private record Fixture(
            AssistantAuthorizationService authorization,
            ServicePrincipal service,
            AtomicReference<SecuritySnapshotLookup> securitySnapshot,
            InMemorySecurityControlPlane controls) {}
}
