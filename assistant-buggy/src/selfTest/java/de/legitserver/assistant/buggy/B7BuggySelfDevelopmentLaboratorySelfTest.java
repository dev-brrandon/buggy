package de.legitserver.assistant.buggy;

import de.legitserver.assistant.buggy.repository.BuggySelfDevelopmentRepository;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentApprovalBinding;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentApprovalPort;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentApprovalStatus;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentChangeType;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentEvidence;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentEvidenceType;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentId;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentPromotionPackage;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentRevision;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentState;
import de.legitserver.assistant.buggy.service.BuggySelfDevelopmentService;
import de.legitserver.assistant.buggy.tools.BuggySelfDevelopmentToolCatalog;
import de.legitserver.assistant.buggy.tools.BuggySelfDevelopmentToolRegistrar;
import de.legitserver.assistant.buggy.tools.BuggyToolApprovalPolicy;
import de.legitserver.assistant.buggy.tools.BuggyToolHandlerBinding;
import de.legitserver.assistant.buggy.tools.BuggyToolHandlerKind;
import de.legitserver.assistant.contracts.source.SourceBuildMetadata;
import de.legitserver.assistant.contracts.source.SourceComponentRef;
import de.legitserver.assistant.contracts.source.SourceFileContent;
import de.legitserver.assistant.contracts.source.SourceFileEntry;
import de.legitserver.assistant.contracts.source.SourceListQuery;
import de.legitserver.assistant.contracts.source.SourcePath;
import de.legitserver.assistant.contracts.source.SourceRegistryPort;
import de.legitserver.assistant.contracts.source.SourceSearchHit;
import de.legitserver.assistant.contracts.source.SourceSearchQuery;
import de.legitserver.assistant.contracts.source.SourceSnapshot;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.core.request.RequestContext;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.core.validation.CoreValidation;
import de.legitserver.assistant.observability.audit.AuditAppendResult;
import de.legitserver.assistant.observability.audit.AuditRecorder;
import de.legitserver.assistant.requests.model.ApprovalRequestId;
import de.legitserver.assistant.runtime.model.ToolId;
import de.legitserver.assistant.runtime.model.ToolOutput;
import de.legitserver.assistant.runtime.model.ToolRiskLevel;
import de.legitserver.assistant.runtime.registry.ToolRegistry;
import de.legitserver.assistant.security.authorization.ResourceRef;
import de.legitserver.assistant.security.secret.SecretGuard;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** B7 deterministic verification for Self-Development lifecycle, source freshness, evidence and approval boundaries. */
public final class B7BuggySelfDevelopmentLaboratorySelfTest {
    private static final Instant NOW = Instant.parse("2026-10-05T00:15:00Z");
    private static final ActorId OWNER = new ActorId("owner.b7");
    private static final String H1 = "1111111111111111111111111111111111111111111111111111111111111111";
    private static final String H2 = "2222222222222222222222222222222222222222222222222222222222222222";

    private B7BuggySelfDevelopmentLaboratorySelfTest() {}

    public static void main(String[] args) throws Exception {
        verifyToolCatalog();
        verifyLifecycleAndBoundaries();
        System.out.println("B7_BUGGY_SELF_DEVELOPMENT_LABORATORY_SELF_TEST: PASS");
    }

    private static void verifyToolCatalog() throws Exception {
        require(BuggySelfDevelopmentToolCatalog.CATALOG_ID.equals("buggy-selfdev-tools-v1"), "canonical B7 catalog id");
        require(BuggySelfDevelopmentToolCatalog.definitions().size() == 11, "eleven explicit B7 tools");
        require(BuggySelfDevelopmentToolCatalog.byId().size() == 11, "B7 tool IDs unique");
        require(BuggySelfDevelopmentToolCatalog.definitions().stream().allMatch(d -> d.descriptor().riskLevel() == ToolRiskLevel.MEDIUM),
                "Self-Development receives no LOW risk discount");
        require(BuggySelfDevelopmentToolCatalog.byId().get(new ToolId("tool.selfdev.submit-for-approval"))
                        .approvalPolicy() == BuggyToolApprovalPolicy.REQUEST_ONLY,
                "submission stops at Foundation Request boundary");
        require(BuggySelfDevelopmentToolCatalog.definitions().stream().noneMatch(d -> {
            String id = d.descriptor().toolId().value();
            return id.contains("shell") || id.contains("terminal") || id.contains("exec_raw") || id.contains("execute_raw");
        }), "no universal execution tool");

        ToolRegistry registry = new ToolRegistry();
        BuggySelfDevelopmentToolRegistrar registrar = new BuggySelfDevelopmentToolRegistrar(new SecretGuard());
        registrar.registerAll(registry, toolId -> Optional.of(new BuggyToolHandlerBinding(
                context -> new ToolOutput(Map.of("status", "OK"), DataClassification.CONFIDENTIAL),
                BuggySelfDevelopmentToolCatalog.byId().get(toolId).approvalPolicy() == BuggyToolApprovalPolicy.REQUEST_ONLY
                        ? BuggyToolHandlerKind.REQUEST_CREATOR : BuggyToolHandlerKind.DIRECT_TYPED)));
        require(registry.size() == 11, "all B7 tools register in Foundation ToolRegistry");
        var workspace = registry.find(new ToolId("tool.selfdev.create-workspace")).orElseThrow();
        expectFailure(() -> workspace.handler().execute(new de.legitserver.assistant.runtime.execution.ToolExecutionContext(
                actorContext(), new ResourceRef("host", "network-01", DataClassification.INTERNAL),
                Map.of("item_id", UUID.randomUUID().toString(), "source_snapshot_ref", "src:1", "purpose", "test"))));
    }

    private static void verifyLifecycleAndBoundaries() {
        MemoryRepository repository = new MemoryRepository();
        MutableSourceRegistry source = new MutableSourceRegistry(snapshot("rev-a", H1));
        FakeApprovalPort approvals = new FakeApprovalPort();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        BuggySelfDevelopmentService service = new BuggySelfDevelopmentService(
                repository,
                source,
                approvals,
                (value, field, maxLength) -> CoreValidation.requireText(value, field, maxLength),
                new AuditRecorder(record -> AuditAppendResult.recordedResult()),
                clock);

        BuggySelfDevelopmentRevision observed = service.observe("Improve cache", "Repeated cache misses under load.",
                new SourceComponentRef("assistant-buggy"), context("observe"));
        expectFailure(() -> service.propose(observed.itemId(), 1, "Patch cache policy.",
                BuggySelfDevelopmentChangeType.SOURCE_PATCH, ToolRiskLevel.LOW, context("low-risk")));
        BuggySelfDevelopmentRevision proposal = service.propose(observed.itemId(), 1, "Patch cache policy.",
                BuggySelfDevelopmentChangeType.SOURCE_PATCH, ToolRiskLevel.MEDIUM, context("proposal"));
        BuggySelfDevelopmentRevision implementing = service.beginImplementation(
                proposal.itemId(), proposal.revision(), "workspace:b7-1", H1, context("workspace"));
        BuggySelfDevelopmentRevision testing = service.startTesting(
                implementing.itemId(), implementing.revision(), "patch:b7-1", H2, context("patch"));
        recordPromotionEvidence(service, testing);
        BuggySelfDevelopmentRevision waiting = service.submitForApproval(
                testing.itemId(), testing.revision(), context("submit"));
        require(waiting.state() == BuggySelfDevelopmentState.WAITING_FOR_OWNER_APPROVAL, "promotion waits for Foundation approval");
        expectFailure(() -> service.markDeployed(waiting.itemId(), waiting.revision(), context("early-deploy")));

        approvals.set(waiting.approvalBinding().requestId(), BuggySelfDevelopmentApprovalStatus.APPROVED);
        BuggySelfDevelopmentRevision approved = service.refreshApproval(
                waiting.itemId(), waiting.revision(), context("approved"));
        require(approved.state() == BuggySelfDevelopmentState.APPROVED, "exact payload approval advances state");
        expectFailure(() -> service.markDeployed(approved.itemId(), approved.revision(), context("not-executed")));
        approvals.set(approved.approvalBinding().requestId(), BuggySelfDevelopmentApprovalStatus.EXECUTED);
        BuggySelfDevelopmentRevision deployed = service.markDeployed(
                approved.itemId(), approved.revision(), context("deployed"));
        BuggySelfDevelopmentRevision verified = service.verifyDeployment(
                deployed.itemId(), deployed.revision(), "verify:b7-1", H1, context("verify"));
        require(verified.state() == BuggySelfDevelopmentState.VERIFIED, "post-deploy verification required");

        BuggySelfDevelopmentRevision rollbackRequested = service.requestRollback(
                verified.itemId(), verified.revision(), "deployment:b7-1", context("rollback-request"));
        require(rollbackRequested.state() == BuggySelfDevelopmentState.VERIFIED,
                "requesting rollback does not pretend rollback already happened");
        approvals.set(rollbackRequested.approvalBinding().requestId(), BuggySelfDevelopmentApprovalStatus.EXECUTED);
        BuggySelfDevelopmentRevision rolledBack = service.markRolledBack(
                rollbackRequested.itemId(), rollbackRequested.revision(), context("rolled-back"));
        require(rolledBack.state() == BuggySelfDevelopmentState.ROLLED_BACK, "rollback requires executed Foundation request");
        require(repository.history(rolledBack.itemId()).size() == 10, "lifecycle revisions remain append-only");

        BuggySelfDevelopmentRevision rejectedObserved = service.observe("Bad idea", "Potential simplification.",
                new SourceComponentRef("assistant-buggy"), context("reject-observe"));
        BuggySelfDevelopmentRevision rejectedProposal = service.propose(rejectedObserved.itemId(), 1, "Remove guard.",
                BuggySelfDevelopmentChangeType.WORKFLOW_CHANGE, ToolRiskLevel.MEDIUM, context("reject-propose"));
        service.recordSelfReview(rejectedProposal.itemId(), rejectedProposal.revision(), "selfreview:reject", H1, false,
                context("reject-review"));
        BuggySelfDevelopmentRevision rejected = service.rejectOwnProposal(
                rejectedProposal.itemId(), rejectedProposal.revision(), "Evidence shows weaker safety.", context("reject"));
        require(rejected.state() == BuggySelfDevelopmentState.REJECTED_BY_BUGGY, "Buggy may reject his own evidence-backed idea");

        BuggySelfDevelopmentRevision staleObserved = service.observe("Stale base", "Test stale snapshot protection.",
                new SourceComponentRef("assistant-buggy"), context("stale-observe"));
        BuggySelfDevelopmentRevision staleProposal = service.propose(staleObserved.itemId(), 1, "Candidate patch.",
                BuggySelfDevelopmentChangeType.SOURCE_PATCH, ToolRiskLevel.MEDIUM, context("stale-propose"));
        BuggySelfDevelopmentRevision staleImplementing = service.beginImplementation(
                staleProposal.itemId(), staleProposal.revision(), "workspace:stale", H1, context("stale-workspace"));
        BuggySelfDevelopmentRevision staleTesting = service.startTesting(
                staleImplementing.itemId(), staleImplementing.revision(), "patch:stale", H2, context("stale-patch"));
        recordPromotionEvidence(service, staleTesting);
        source.snapshot = snapshot("rev-b", H2);
        expectFailure(() -> service.submitForApproval(staleTesting.itemId(), staleTesting.revision(), context("stale-submit")));
    }

    private static void recordPromotionEvidence(BuggySelfDevelopmentService service, BuggySelfDevelopmentRevision testing) {
        long revision = testing.revision();
        service.recordEvidence(testing.itemId(), revision, BuggySelfDevelopmentEvidenceType.BUILD,
                new ToolId("tool.selfdev.build"), "artifact:b7", H1, true, context("build"));
        service.recordEvidence(testing.itemId(), revision, BuggySelfDevelopmentEvidenceType.TEST,
                new ToolId("tool.selfdev.test"), "test:b7", H1, true, context("test"));
        service.recordEvidence(testing.itemId(), revision, BuggySelfDevelopmentEvidenceType.SECURITY_SCAN,
                new ToolId("tool.selfdev.security-scan"), "security:b7", H1, true, context("security"));
        service.recordEvidence(testing.itemId(), revision, BuggySelfDevelopmentEvidenceType.BENCHMARK,
                new ToolId("tool.selfdev.benchmark"), "benchmark:b7", H1, true, context("benchmark"));
        service.recordSelfReview(testing.itemId(), revision, "selfreview:accept", H1, true, context("self-review"));
    }

    private static SourceSnapshot snapshot(String revision, String hash) {
        return new SourceSnapshot("assistant-buggy", "0.1.0-dev.18", "b7-test", revision, NOW, hash);
    }

    private static RequestContext context(String suffix) {
        return new RequestContext(new RequestId("req-b7-" + suffix), new TraceId("trace-b7-" + suffix), OWNER,
                Origin.PANEL, AssistantId.BUGGY, "conversation-b7", "network-01", NOW);
    }

    private static de.legitserver.assistant.core.actor.ActorContext actorContext() {
        return new de.legitserver.assistant.core.actor.ActorContext(OWNER,
                de.legitserver.assistant.core.actor.ActorType.HUMAN, Origin.PANEL, "network:owner", null,
                "panel-b7", null, new RequestId("req-b7-tool"), new TraceId("trace-b7-tool"), null, 1, NOW);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void expectFailure(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Exception expected) {
            return;
        }
        throw new AssertionError("Expected operation failure");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class MemoryRepository implements BuggySelfDevelopmentRepository {
        private final Map<BuggySelfDevelopmentId, List<BuggySelfDevelopmentRevision>> revisions = new HashMap<>();
        private final Map<BuggySelfDevelopmentId, List<BuggySelfDevelopmentEvidence>> evidence = new HashMap<>();

        @Override
        public Optional<BuggySelfDevelopmentRevision> findCurrent(BuggySelfDevelopmentId itemId) {
            List<BuggySelfDevelopmentRevision> list = revisions.get(itemId);
            return list == null || list.isEmpty() ? Optional.empty() : Optional.of(list.getLast());
        }

        @Override
        public List<BuggySelfDevelopmentRevision> history(BuggySelfDevelopmentId itemId) {
            return List.copyOf(revisions.getOrDefault(itemId, List.of()));
        }

        @Override
        public BuggySelfDevelopmentRevision append(long expectedCurrentRevision, BuggySelfDevelopmentRevision revision) {
            long actual = findCurrent(revision.itemId()).map(BuggySelfDevelopmentRevision::revision).orElse(0L);
            if (actual != expectedCurrentRevision) throw new IllegalStateException("stale");
            revisions.computeIfAbsent(revision.itemId(), ignored -> new ArrayList<>()).add(revision);
            return revision;
        }

        @Override
        public void appendEvidence(BuggySelfDevelopmentEvidence item) {
            evidence.computeIfAbsent(item.itemId(), ignored -> new ArrayList<>()).add(item);
        }

        @Override
        public List<BuggySelfDevelopmentEvidence> evidence(BuggySelfDevelopmentId itemId) {
            return List.copyOf(evidence.getOrDefault(itemId, List.of()));
        }
    }

    private static final class MutableSourceRegistry implements SourceRegistryPort {
        private SourceSnapshot snapshot;
        private MutableSourceRegistry(SourceSnapshot snapshot) { this.snapshot = snapshot; }
        @Override public Optional<SourceSnapshot> getSnapshot(SourceComponentRef component) {
            return component.value().equals(snapshot.component()) ? Optional.of(snapshot) : Optional.empty();
        }
        @Override public List<SourceFileEntry> listFiles(SourceListQuery query) { return List.of(); }
        @Override public Optional<SourceFileContent> readFile(SourceSnapshot sourceSnapshot, SourcePath path) { return Optional.empty(); }
        @Override public List<SourceSearchHit> searchFiles(SourceSnapshot sourceSnapshot, SourceSearchQuery query) { return List.of(); }
        @Override public Optional<SourceBuildMetadata> getBuildMetadata(SourceSnapshot sourceSnapshot) { return Optional.empty(); }
    }

    private static final class FakeApprovalPort implements BuggySelfDevelopmentApprovalPort {
        private final Map<ApprovalRequestId, BuggySelfDevelopmentApprovalStatus> statuses = new HashMap<>();

        @Override
        public BuggySelfDevelopmentApprovalBinding submitPromotion(
                BuggySelfDevelopmentPromotionPackage promotionPackage, RequestContext context) {
            ApprovalRequestId id = ApprovalRequestId.random();
            statuses.put(id, BuggySelfDevelopmentApprovalStatus.PENDING);
            return new BuggySelfDevelopmentApprovalBinding(id, promotionPackage.submittedRevision(), PROMOTION_REQUEST_TYPE,
                    sha256(promotionPackage.canonicalBody()), promotionPackage.riskLevel());
        }

        @Override
        public BuggySelfDevelopmentApprovalBinding submitRollback(
                BuggySelfDevelopmentId itemId, long submittedRevision, String deploymentReference,
                ToolRiskLevel riskLevel, RequestContext context) {
            ApprovalRequestId id = ApprovalRequestId.random();
            statuses.put(id, BuggySelfDevelopmentApprovalStatus.PENDING);
            return new BuggySelfDevelopmentApprovalBinding(id, submittedRevision, ROLLBACK_REQUEST_TYPE,
                    sha256(itemId + "|" + deploymentReference + "|" + submittedRevision), riskLevel);
        }

        @Override
        public BuggySelfDevelopmentApprovalStatus status(BuggySelfDevelopmentApprovalBinding binding) {
            return statuses.getOrDefault(binding.requestId(), BuggySelfDevelopmentApprovalStatus.TERMINAL_FAILURE);
        }

        void set(ApprovalRequestId id, BuggySelfDevelopmentApprovalStatus status) {
            statuses.put(id, status);
        }
    }
}
