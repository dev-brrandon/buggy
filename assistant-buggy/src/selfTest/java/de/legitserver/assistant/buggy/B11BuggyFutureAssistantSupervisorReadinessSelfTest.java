package de.legitserver.assistant.buggy;

import de.legitserver.assistant.buggy.supervision.BuggyDelegationOutcome;
import de.legitserver.assistant.buggy.supervision.BuggyDelegationResult;
import de.legitserver.assistant.buggy.supervision.BuggyDelegationReviewStatus;
import de.legitserver.assistant.buggy.supervision.BuggyFutureAssistantPresence;
import de.legitserver.assistant.buggy.supervision.BuggyFutureAssistantState;
import de.legitserver.assistant.buggy.supervision.BuggyNotStartedFutureAssistantDirectory;
import de.legitserver.assistant.buggy.supervision.BuggySupervisorAuthorityMode;
import de.legitserver.assistant.buggy.supervision.BuggySupervisorContractCatalog;
import de.legitserver.assistant.buggy.supervision.BuggySupervisorOperation;
import de.legitserver.assistant.buggy.supervision.BuggySupervisorReadinessService;
import de.legitserver.assistant.buggy.tasks.BuggyTaskId;
import de.legitserver.assistant.buggy.tools.BuggyToolCatalog;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.observability.health.AssistantHealthStatus;
import de.legitserver.assistant.supervisor.health.AssistantHealthReport;
import de.legitserver.assistant.supervisor.health.AssistantMetricsSnapshot;
import de.legitserver.assistant.supervisor.read.AssistantSupervisorReadPort;
import de.legitserver.assistant.supervisor.read.SupervisorReadContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class B11BuggyFutureAssistantSupervisorReadinessSelfTest {
    private static final Instant NOW = Instant.parse("2026-10-05T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private B11BuggyFutureAssistantSupervisorReadinessSelfTest() {}

    public static void main(String[] args) throws Exception {
        require(BuggySupervisorContractCatalog.futureTargets().equals(Set.of(AssistantId.BYTE, AssistantId.KAESE)),
                "only BYTE/KAESE are reserved future targets");
        expectIllegal(() -> BuggySupervisorContractCatalog.contract(AssistantId.BUGGY), "Buggy cannot supervise itself through B11");

        var byteContract = BuggySupervisorContractCatalog.contract(AssistantId.BYTE);
        require(byteContract.ownershipBoundary().identityOwner().equals("TARGET_ASSISTANT"), "target owns identity");
        require(byteContract.ownershipBoundary().personalityOwner().equals("TARGET_ASSISTANT"), "target owns personality");
        require(byteContract.ownershipBoundary().permissionAuthority().equals("NETWORKCORE"), "NetworkCore owns permissions");
        require(byteContract.ownershipBoundary().targetMayDisagree(), "target may disagree");
        require(byteContract.ownershipBoundary().targetMustReauthorizeDelegatedWork(), "target reauthorizes delegation");

        require(byteContract.operations().get(BuggySupervisorOperation.PAUSE) == BuggySupervisorAuthorityMode.EXECUTE_BOUNDED,
                "pause is bounded execute");
        require(byteContract.operations().get(BuggySupervisorOperation.OVERWRITE_IDENTITY) == BuggySupervisorAuthorityMode.FORBIDDEN,
                "identity overwrite forbidden");
        require(byteContract.operations().get(BuggySupervisorOperation.REWRITE_PERSONALITY) == BuggySupervisorAuthorityMode.FORBIDDEN,
                "personality rewrite forbidden");
        require(byteContract.operations().get(BuggySupervisorOperation.INCREASE_PERMISSIONS) == BuggySupervisorAuthorityMode.FORBIDDEN,
                "permission escalation forbidden");
        require(byteContract.operations().get(BuggySupervisorOperation.REPLACE_SECURITY_AUTHORITY) == BuggySupervisorAuthorityMode.FORBIDDEN,
                "security authority replacement forbidden");
        require(byteContract.operations().get(BuggySupervisorOperation.PERFORM_OWNER_ONLY_DECISION) == BuggySupervisorAuthorityMode.FORBIDDEN,
                "Owner impersonation forbidden");

        var tools = BuggyToolCatalog.byId();
        for (BuggySupervisorOperation operation : BuggySupervisorOperation.values()) {
            if (operation.toolId() != null) require(tools.containsKey(new de.legitserver.assistant.runtime.model.ToolId(operation.toolId())), "B11 tool binding exists: " + operation.toolId());
        }

        CountingSupervisor noReads = new CountingSupervisor();
        BuggySupervisorReadinessService notStarted = new BuggySupervisorReadinessService(
                new BuggyNotStartedFutureAssistantDirectory(CLOCK), noReads, CLOCK);
        var byteReadiness = notStarted.inspect(nullSafeContext(), AssistantId.BYTE);
        require(byteReadiness.presence().state() == BuggyFutureAssistantState.NOT_STARTED, "BYTE remains not started");
        require(byteReadiness.health() == null, "not-started target has no synthetic health");
        require(noReads.healthReads == 0, "Foundation Supervisor not called for not-started target");
        require(!byteReadiness.identityImplementedByB11() && !byteReadiness.personalityImplementedByB11()
                && !byteReadiness.authorityImplementedByB11(), "B11 implements no future assistant identity/personality/authority");
        expectState(() -> notStarted.prepareDelegation(BuggyTaskId.random(), AssistantId.BYTE, "inspect cache regression",
                List.of("incident:42"), List.of("evidence:1"), List.of("diagnostic report")),
                "delegation fails closed while target not started");

        CountingSupervisor reads = new CountingSupervisor();
        var availableDirectory = new FixedDirectory(BuggyFutureAssistantState.AVAILABLE);
        BuggySupervisorReadinessService ready = new BuggySupervisorReadinessService(availableDirectory, reads, CLOCK);
        var health = ready.inspect(nullSafeContext(), AssistantId.KAESE);
        require(health.health() != null && health.health().status() == AssistantHealthStatus.UP,
                "available future target uses Foundation Supervisor read");
        require(reads.healthReads == 1, "Foundation Supervisor invoked exactly once");

        var pausePlan = ready.plan(AssistantId.BYTE, BuggySupervisorOperation.PAUSE, "bounded incident containment");
        require(pausePlan.toolId().equals("tool.assistant.pause"), "pause maps to existing B5 typed tool");
        require(pausePlan.requiresFoundationAuthorization(), "Foundation authorization is mandatory");
        require(!pausePlan.changesTargetAuthority(), "bounded command cannot change authority");
        expectIllegal(() -> ready.plan(AssistantId.BYTE, BuggySupervisorOperation.INCREASE_PERMISSIONS, "forbidden"),
                "permission changes cannot be planned");

        var handoff = ready.prepareDelegation(BuggyTaskId.random(), AssistantId.BYTE, "analyze bounded runtime fault",
                List.of("incident:42", "task:root"), List.of("evidence:deploy", "evidence:log"), List.of("root-cause assessment"));
        require(handoff.targetMustReauthorize(), "delegated work must be reauthorized by target");
        require(handoff.targetMayReject() && handoff.targetMayDisagree(), "independent target may reject/disagree");
        require(!handoff.sourceMayOverrideTargetPolicy(), "Buggy cannot override target policy");

        BuggyDelegationResult disagreement = new BuggyDelegationResult(
                handoff.intent().delegationId(), AssistantId.BYTE, BuggyDelegationOutcome.COMPLETED,
                "Analysis complete with different diagnosis", List.of("evidence:byte-result"),
                "I disagree with Buggy's initial root-cause hypothesis", NOW.plusSeconds(30));
        var review = ready.review(handoff, disagreement);
        require(review.reviewStatus() == BuggyDelegationReviewStatus.FOLLOW_UP_REQUIRED, "disagreement is preserved for follow-up");
        require(review.preservesTargetDisagreement(), "review cannot erase target disagreement");
        require(review.targetResult().disagreement() != null, "original target disagreement remains intact");

        foundationSupervisorBindingIsBuggyCapabilityGated();

        if (args.length == 1) verifyNoFutureAssistantImplementation(Path.of(args[0]));

        System.out.println("B11_BUGGY_FUTURE_ASSISTANT_SUPERVISOR_READINESS_SELF_TEST: PASS");
    }


    private static void foundationSupervisorBindingIsBuggyCapabilityGated() {
        java.util.UUID actorUuid = java.util.UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        var actorId = new de.legitserver.assistant.core.actor.ActorId("actor:b11-owner");
        String networkId = "network:b11-owner";
        var snapshotRef = new java.util.concurrent.atomic.AtomicReference<>(
                de.legitserver.assistant.security.authorization.SecuritySnapshotLookup.found(newSecuritySnapshot(
                        actorUuid,
                        Set.of("networkcore.assistant.use.buggy", "networkcore.assistant.buggy.supervisor.read"))));
        var controls = new de.legitserver.assistant.security.control.InMemorySecurityControlPlane(
                java.util.EnumSet.allOf(de.legitserver.assistant.security.control.SecurityFeature.class), NOW);
        var authorization = new de.legitserver.assistant.security.authorization.AssistantAuthorizationService(
                actor -> de.legitserver.assistant.security.authorization.ActorResolution.found(
                        new de.legitserver.assistant.security.authorization.ResolvedActorIdentity(actorId, networkId, actorUuid, 1)),
                (uuid, minimumRevision) -> snapshotRef.get(),
                new de.legitserver.assistant.security.authorization.buggy.BuggyCapabilityPolicy(),
                (actor, snapshot, capability, resource) -> de.legitserver.assistant.security.authorization.ResourceAuthorizationStatus.ALLOW,
                controls);
        var dependencies = new de.legitserver.assistant.observability.health.DependencyHealthRegistry(CLOCK);
        dependencies.update(de.legitserver.assistant.observability.health.DependencyId.NETWORKCORE,
                de.legitserver.assistant.observability.health.DependencyState.UP, "UP", 1);
        dependencies.update(de.legitserver.assistant.observability.health.DependencyId.DATABASE,
                de.legitserver.assistant.observability.health.DependencyState.UP, "UP", 1);
        var foundation = new de.legitserver.assistant.supervisor.read.AssistantSupervisorReadService(
                new de.legitserver.assistant.buggy.supervision.BuggySupervisorAuthorizationPort(authorization),
                new de.legitserver.assistant.supervisor.health.InMemoryAssistantOperationalTelemetry(CLOCK),
                dependencies,
                new de.legitserver.assistant.supervisor.health.CapabilityDegradationRegistry(),
                new de.legitserver.assistant.observability.audit.AuditRecorder(
                        record -> de.legitserver.assistant.observability.audit.AuditAppendResult.recordedResult()),
                CLOCK);
        SupervisorReadContext context = authorizedContext(actorId, networkId, actorUuid);
        require(foundation.getAssistantHealth(context, AssistantId.BYTE).assistantId() == AssistantId.BYTE,
                "B11 binds real Foundation Supervisor through Buggy B3 supervisor-read capability");

        snapshotRef.set(de.legitserver.assistant.security.authorization.SecuritySnapshotLookup.found(
                newSecuritySnapshot(actorUuid, Set.of("networkcore.assistant.use.buggy"))));
        try {
            foundation.getAssistantHealth(context, AssistantId.BYTE);
            throw new IllegalStateException("B11 expected Foundation Supervisor denial after NetworkCore permission removal");
        } catch (de.legitserver.assistant.supervisor.read.SupervisorAccessDeniedException expected) {
            // expected: NetworkCore remains authoritative
        }
    }

    private static de.legitserver.assistant.security.authorization.AuthoritativeSecuritySnapshot newSecuritySnapshot(
            java.util.UUID actorUuid, Set<String> permissions) {
        return new de.legitserver.assistant.security.authorization.AuthoritativeSecuritySnapshot(
                actorUuid, "owner", 10_000, "OWNER", Set.of("OWNER"), Set.of("usr"), permissions,
                Set.of(), Set.of(), true, 1, NOW);
    }

    private static SupervisorReadContext authorizedContext(
            de.legitserver.assistant.core.actor.ActorId actorId, String networkId, java.util.UUID actorUuid) {
        var requestId = new de.legitserver.assistant.core.request.RequestId("request:b11-foundation");
        byte[] key = "abcdef0123456789abcdef0123456789".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var tokens = new de.legitserver.assistant.security.authn.HmacServiceTokenService(
                key, new de.legitserver.assistant.security.authn.BoundedReplayProtector(16));
        var claims = new de.legitserver.assistant.security.authn.ServiceTokenClaims(
                "b11-foundation", "legit-assistant", Set.of(de.legitserver.assistant.security.authn.ServiceScopes.INVOKE),
                NOW.minusSeconds(1), NOW.plusSeconds(60), "b11-foundation-jti", requestId);
        var authenticated = tokens.verify(tokens.issue(claims), "legit-assistant", requestId,
                Set.of(de.legitserver.assistant.security.authn.ServiceScopes.INVOKE), NOW);
        require(authenticated.authenticated(), "B11 Foundation principal authenticates");
        var actor = new de.legitserver.assistant.core.actor.ActorContext(
                actorId, de.legitserver.assistant.core.actor.ActorType.HUMAN,
                de.legitserver.assistant.core.actor.Origin.PANEL, networkId, actorUuid,
                "owner-panel", null, requestId,
                new de.legitserver.assistant.core.request.TraceId("trace:b11-foundation"), null, 1, NOW);
        var descriptor = new de.legitserver.assistant.core.assistant.AssistantDescriptor(
                AssistantId.BUGGY, "Buggy", true, "buggy-capabilities-v1", "buggy-policy-v1",
                "buggy-knowledge-v1", "buggy-runtime-v1", "buggy-personality-v1", 1, NOW);
        return new SupervisorReadContext(authenticated.principal(), actor, descriptor);
    }

    private static SupervisorReadContext nullSafeContext() {
        var requestId = new de.legitserver.assistant.core.request.RequestId("request:b11");
        byte[] key = "0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var tokens = new de.legitserver.assistant.security.authn.HmacServiceTokenService(
                key, new de.legitserver.assistant.security.authn.BoundedReplayProtector(16));
        var claims = new de.legitserver.assistant.security.authn.ServiceTokenClaims(
                "b11-test", "legit-assistant", Set.of(de.legitserver.assistant.security.authn.ServiceScopes.SUPERVISOR),
                NOW.minusSeconds(1), NOW.plusSeconds(60), "b11-jti", requestId);
        var authenticated = tokens.verify(tokens.issue(claims), "legit-assistant", requestId,
                Set.of(de.legitserver.assistant.security.authn.ServiceScopes.SUPERVISOR), NOW);
        require(authenticated.authenticated(), "B11 test service principal authenticates");
        var actor = new de.legitserver.assistant.core.actor.ActorContext(
                new de.legitserver.assistant.core.actor.ActorId("assistant:buggy"),
                de.legitserver.assistant.core.actor.ActorType.SYSTEM,
                de.legitserver.assistant.core.actor.Origin.INTERNAL,
                "network:b11", null, null, null, requestId,
                new de.legitserver.assistant.core.request.TraceId("trace:b11"), "server:test", 1, NOW);
        var descriptor = new de.legitserver.assistant.core.assistant.AssistantDescriptor(
                AssistantId.BUGGY, "Buggy", true, "buggy-capabilities-v1", "buggy-policy-v1",
                "buggy-knowledge-v1", "buggy-runtime-v1", "buggy-personality-v1", 1, NOW);
        return new SupervisorReadContext(authenticated.principal(), actor, descriptor);
    }

    private static void verifyNoFutureAssistantImplementation(Path root) throws Exception {
        require(!Files.exists(root.resolve("assistant-byte")), "assistant-byte module must not exist in B11");
        require(!Files.exists(root.resolve("assistant-kaese")), "assistant-kaese module must not exist in B11");
        try (var paths = Files.walk(root.resolve("assistant-buggy/src/main/java"))) {
            List<String> forbidden = paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString().toLowerCase())
                    .filter(name -> name.contains("byteidentity") || name.contains("kaeseidentity")
                            || name.contains("bytepersonality") || name.contains("kaesepersonality"))
                    .toList();
            require(forbidden.isEmpty(), "B11 must not introduce BYTE/KAESE identity/personality implementations");
        }
    }

    private static void expectIllegal(Runnable action, String message) {
        try {
            action.run();
            throw new IllegalStateException("B11 expected IllegalArgumentException: " + message);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void expectState(Runnable action, String message) {
        try {
            action.run();
            throw new IllegalStateException("B11 expected IllegalStateException: " + message);
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException("B11 supervisor readiness self-test failed: " + message);
    }

    private static final class FixedDirectory implements de.legitserver.assistant.buggy.supervision.BuggyFutureAssistantDirectoryPort {
        private final BuggyFutureAssistantState state;
        private FixedDirectory(BuggyFutureAssistantState state) { this.state = state; }
        @Override public BuggyFutureAssistantPresence resolve(AssistantId targetAssistant) {
            BuggySupervisorContractCatalog.requireFutureTarget(targetAssistant);
            return new BuggyFutureAssistantPresence(targetAssistant, state, 1, NOW);
        }
    }

    private static final class CountingSupervisor implements AssistantSupervisorReadPort {
        private int healthReads;
        @Override public AssistantHealthReport getAssistantHealth(SupervisorReadContext context, AssistantId targetAssistant) {
            healthReads++;
            return new AssistantHealthReport(targetAssistant, AssistantHealthStatus.UP, NOW,
                    new AssistantMetricsSnapshot(targetAssistant, NOW, 0, 0, 0, 1.0, 0.0,
                            de.legitserver.assistant.observability.metrics.LatencySnapshot.empty(),
                            0, 0, 0, 0, 0, 0, 0), Map.of(), Set.of());
        }
        @Override public AssistantMetricsSnapshot getAssistantMetrics(SupervisorReadContext context, AssistantId targetAssistant) { throw new UnsupportedOperationException(); }
        @Override public List<de.legitserver.assistant.supervisor.health.AssistantFailureEvent> getRecentAssistantFailures(SupervisorReadContext context, AssistantId targetAssistant, int limit) { throw new UnsupportedOperationException(); }
        @Override public List<de.legitserver.assistant.supervisor.health.ToolFailureSummary> getToolFailureSummary(SupervisorReadContext context, AssistantId targetAssistant) { throw new UnsupportedOperationException(); }
        @Override public Map<de.legitserver.assistant.observability.health.DependencyId, de.legitserver.assistant.observability.health.DependencyHealth> getDependencyStatus(SupervisorReadContext context, AssistantId targetAssistant) { throw new UnsupportedOperationException(); }
        @Override public Set<de.legitserver.assistant.core.assistant.AssistantCapabilityId> getDegradedCapabilities(SupervisorReadContext context, AssistantId targetAssistant) { throw new UnsupportedOperationException(); }
    }
}
