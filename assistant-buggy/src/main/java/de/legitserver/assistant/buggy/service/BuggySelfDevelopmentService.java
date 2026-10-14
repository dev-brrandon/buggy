package de.legitserver.assistant.buggy.service;

import de.legitserver.assistant.buggy.repository.BuggySelfDevelopmentRepository;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentApprovalBinding;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentApprovalPort;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentApprovalStatus;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentChangeType;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentContentPolicy;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentEvidence;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentEvidenceType;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentId;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentPromotionPackage;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentRevision;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentState;
import de.legitserver.assistant.contracts.source.SourceComponentRef;
import de.legitserver.assistant.contracts.source.SourceRegistryPort;
import de.legitserver.assistant.contracts.source.SourceSnapshot;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.request.RequestContext;
import de.legitserver.assistant.observability.audit.AuditAction;
import de.legitserver.assistant.observability.audit.AuditOutcome;
import de.legitserver.assistant.observability.audit.AuditRecord;
import de.legitserver.assistant.observability.audit.AuditRecorder;
import de.legitserver.assistant.runtime.model.ToolId;
import de.legitserver.assistant.runtime.model.ToolRiskLevel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * B7 Self-Development orchestration. Source, approval, tool execution and deployment authority remain Foundation-owned.
 * This service only manages the append-only Buggy product lifecycle and validates evidence references.
 */
public final class BuggySelfDevelopmentService {
    private static final ToolId TOOL_CREATE_WORKSPACE = new ToolId("tool.selfdev.create-workspace");
    private static final ToolId TOOL_MODIFY_SOURCE = new ToolId("tool.selfdev.modify-source");
    private static final ToolId TOOL_BUILD = new ToolId("tool.selfdev.build");
    private static final ToolId TOOL_TEST = new ToolId("tool.selfdev.test");
    private static final ToolId TOOL_BENCHMARK = new ToolId("tool.selfdev.benchmark");
    private static final ToolId TOOL_SECURITY_SCAN = new ToolId("tool.selfdev.security-scan");
    private static final ToolId TOOL_COMPARE = new ToolId("tool.selfdev.compare");
    private static final ToolId TOOL_DEPLOY_VERIFY = new ToolId("tool.deployment.verify");

    private final BuggySelfDevelopmentRepository repository;
    private final SourceRegistryPort sourceRegistry;
    private final BuggySelfDevelopmentApprovalPort approvals;
    private final BuggySelfDevelopmentContentPolicy contentPolicy;
    private final AuditRecorder audit;
    private final Clock clock;

    public BuggySelfDevelopmentService(
            BuggySelfDevelopmentRepository repository,
            SourceRegistryPort sourceRegistry,
            BuggySelfDevelopmentApprovalPort approvals,
            BuggySelfDevelopmentContentPolicy contentPolicy,
            AuditRecorder audit,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.sourceRegistry = Objects.requireNonNull(sourceRegistry, "sourceRegistry");
        this.approvals = Objects.requireNonNull(approvals, "approvals");
        this.contentPolicy = Objects.requireNonNull(contentPolicy, "contentPolicy");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public BuggySelfDevelopmentRevision observe(
            String title,
            String problem,
            SourceComponentRef component,
            RequestContext context) {
        requireBuggyContext(context);
        SourceSnapshot snapshot = sourceRegistry.getSnapshot(Objects.requireNonNull(component, "component"))
                .orElseThrow(() -> new IllegalStateException("SOURCE_SNAPSHOT_UNAVAILABLE"));
        Instant now = clock.instant();
        BuggySelfDevelopmentRevision revision = new BuggySelfDevelopmentRevision(
                BuggySelfDevelopmentId.random(), 1, null, BuggySelfDevelopmentState.OBSERVED,
                text(title, "title", 256), text(problem, "problem", 8192), null, null,
                snapshot, null, null, null, null, null, null, context.actorId(), now);
        BuggySelfDevelopmentRevision persisted = repository.append(0, revision);
        audit(context, persisted, "OBSERVE", false);
        return persisted;
    }

    public BuggySelfDevelopmentRevision propose(
            BuggySelfDevelopmentId itemId,
            long expectedRevision,
            String proposalSummary,
            BuggySelfDevelopmentChangeType changeType,
            ToolRiskLevel riskLevel,
            RequestContext context) {
        BuggySelfDevelopmentRevision current = current(itemId, expectedRevision, context);
        requireState(current, BuggySelfDevelopmentState.OBSERVED);
        Objects.requireNonNull(changeType, "changeType");
        requireNoRiskDiscount(riskLevel);
        BuggySelfDevelopmentRevision next = next(current, BuggySelfDevelopmentState.PROPOSAL,
                text(proposalSummary, "proposalSummary", 8192), changeType, null, null, null, riskLevel, null,
                "Self-Development proposal created", context);
        BuggySelfDevelopmentRevision persisted = repository.append(expectedRevision, next);
        audit(context, persisted, "PROPOSE", riskLevel.isHighRisk());
        return persisted;
    }

    public BuggySelfDevelopmentRevision beginImplementation(
            BuggySelfDevelopmentId itemId,
            long expectedRevision,
            String workspaceRef,
            String workspaceSha256,
            RequestContext context) {
        BuggySelfDevelopmentRevision current = current(itemId, expectedRevision, context);
        requireState(current, BuggySelfDevelopmentState.PROPOSAL);
        BuggySelfDevelopmentEvidence evidence = evidence(current, BuggySelfDevelopmentEvidenceType.WORKSPACE,
                TOOL_CREATE_WORKSPACE, workspaceRef, workspaceSha256, true, context);
        repository.appendEvidence(evidence);
        BuggySelfDevelopmentRevision next = next(current, BuggySelfDevelopmentState.IMPLEMENTING,
                current.proposalSummary(), current.changeType(), evidence.referenceId(), null, null,
                current.riskLevel(), null, "Isolated workspace created", context);
        BuggySelfDevelopmentRevision persisted = repository.append(expectedRevision, next);
        audit(context, persisted, "BEGIN_IMPLEMENTATION", current.riskLevel().isHighRisk());
        return persisted;
    }

    public BuggySelfDevelopmentRevision startTesting(
            BuggySelfDevelopmentId itemId,
            long expectedRevision,
            String patchRef,
            String patchSha256,
            RequestContext context) {
        BuggySelfDevelopmentRevision current = current(itemId, expectedRevision, context);
        requireState(current, BuggySelfDevelopmentState.IMPLEMENTING);
        BuggySelfDevelopmentEvidence evidence = evidence(current, BuggySelfDevelopmentEvidenceType.PATCH,
                TOOL_MODIFY_SOURCE, patchRef, patchSha256, true, context);
        repository.appendEvidence(evidence);
        BuggySelfDevelopmentRevision next = next(current, BuggySelfDevelopmentState.TESTING,
                current.proposalSummary(), current.changeType(), current.workspaceRef(), evidence.referenceId(), null,
                current.riskLevel(), null, "Patch frozen for verification", context);
        BuggySelfDevelopmentRevision persisted = repository.append(expectedRevision, next);
        audit(context, persisted, "START_TESTING", current.riskLevel().isHighRisk());
        return persisted;
    }

    public BuggySelfDevelopmentEvidence recordEvidence(
            BuggySelfDevelopmentId itemId,
            long expectedRevision,
            BuggySelfDevelopmentEvidenceType type,
            ToolId toolId,
            String referenceId,
            String sha256,
            boolean successful,
            RequestContext context) {
        BuggySelfDevelopmentRevision current = current(itemId, expectedRevision, context);
        requireEvidenceState(current, type);
        requireExpectedTool(type, toolId);
        BuggySelfDevelopmentEvidence evidence = evidence(current, type, toolId, referenceId, sha256, successful, context);
        repository.appendEvidence(evidence);
        audit(context, current, "EVIDENCE_" + type.name(), current.riskLevel() != null && current.riskLevel().isHighRisk());
        return evidence;
    }

    public BuggySelfDevelopmentEvidence recordSelfReview(
            BuggySelfDevelopmentId itemId,
            long expectedRevision,
            String reviewReference,
            String reviewSha256,
            boolean accepted,
            RequestContext context) {
        BuggySelfDevelopmentRevision current = current(itemId, expectedRevision, context);
        if (current.state() != BuggySelfDevelopmentState.PROPOSAL
                && current.state() != BuggySelfDevelopmentState.IMPLEMENTING
                && current.state() != BuggySelfDevelopmentState.TESTING) {
            throw new IllegalStateException("SELF_REVIEW_NOT_ALLOWED_IN_STATE_" + current.state());
        }
        BuggySelfDevelopmentEvidence evidence = evidence(current, BuggySelfDevelopmentEvidenceType.SELF_REVIEW,
                null, reviewReference, reviewSha256, accepted, context);
        repository.appendEvidence(evidence);
        audit(context, current, accepted ? "SELF_REVIEW_ACCEPT" : "SELF_REVIEW_REJECT", false);
        return evidence;
    }

    public BuggySelfDevelopmentRevision rejectOwnProposal(
            BuggySelfDevelopmentId itemId,
            long expectedRevision,
            String reason,
            RequestContext context) {
        BuggySelfDevelopmentRevision current = current(itemId, expectedRevision, context);
        if (current.state() != BuggySelfDevelopmentState.PROPOSAL
                && current.state() != BuggySelfDevelopmentState.IMPLEMENTING
                && current.state() != BuggySelfDevelopmentState.TESTING) {
            throw new IllegalStateException("SELF_REJECTION_NOT_ALLOWED_IN_STATE_" + current.state());
        }
        Optional<BuggySelfDevelopmentEvidence> review = latestEvidence(itemId, BuggySelfDevelopmentEvidenceType.SELF_REVIEW);
        if (review.isEmpty() || review.get().successful()) {
            throw new IllegalStateException("REJECTED_BY_BUGGY requires a failing Self-Review evidence record");
        }
        BuggySelfDevelopmentRevision next = next(current, BuggySelfDevelopmentState.REJECTED_BY_BUGGY,
                current.proposalSummary(), current.changeType(), current.workspaceRef(), current.patchRef(),
                current.buildArtifactRef(), current.riskLevel(), null, text(reason, "reason", 4096), context);
        BuggySelfDevelopmentRevision persisted = repository.append(expectedRevision, next);
        audit(context, persisted, "REJECT_OWN_PROPOSAL", false);
        return persisted;
    }

    public BuggySelfDevelopmentRevision submitForApproval(
            BuggySelfDevelopmentId itemId,
            long expectedRevision,
            RequestContext context) {
        BuggySelfDevelopmentRevision current = current(itemId, expectedRevision, context);
        requireState(current, BuggySelfDevelopmentState.TESTING);
        requireSourceStillCurrent(current.sourceSnapshot());
        List<BuggySelfDevelopmentEvidence> evidence = repository.evidence(itemId);
        requirePromotionEvidence(evidence);
        String buildArtifactRef = latestSuccessful(evidence, BuggySelfDevelopmentEvidenceType.BUILD)
                .orElseThrow().referenceId();
        List<String> evidenceDigests = evidence.stream()
                .sorted(Comparator.comparing(BuggySelfDevelopmentEvidence::observedAt)
                        .thenComparing(e -> e.evidenceId().toString()))
                .map(BuggySelfDevelopmentService::evidenceDigest)
                .toList();
        BuggySelfDevelopmentPromotionPackage promotionPackage = new BuggySelfDevelopmentPromotionPackage(
                itemId, current.revision(), current.sourceSnapshot(), current.patchRef(), buildArtifactRef,
                current.riskLevel(), evidenceDigests);
        BuggySelfDevelopmentApprovalBinding binding = approvals.submitPromotion(promotionPackage, context);
        if (!binding.requestType().equals(BuggySelfDevelopmentApprovalPort.PROMOTION_REQUEST_TYPE)
                || binding.submittedItemRevision() != current.revision()
                || binding.riskLevel() != current.riskLevel()) {
            throw new IllegalStateException("Foundation approval binding does not match Self-Development promotion");
        }
        repository.appendEvidence(evidence(current, BuggySelfDevelopmentEvidenceType.APPROVAL_REQUEST, null,
                binding.requestId().toString(), binding.payloadSha256(), true, context));
        BuggySelfDevelopmentRevision next = next(current, BuggySelfDevelopmentState.WAITING_FOR_OWNER_APPROVAL,
                current.proposalSummary(), current.changeType(), current.workspaceRef(), current.patchRef(), buildArtifactRef,
                current.riskLevel(), binding, "Submitted to Foundation Request / Approval", context);
        BuggySelfDevelopmentRevision persisted = repository.append(expectedRevision, next);
        audit(context, persisted, "SUBMIT_FOR_APPROVAL", true);
        return persisted;
    }

    public BuggySelfDevelopmentRevision refreshApproval(
            BuggySelfDevelopmentId itemId,
            long expectedRevision,
            RequestContext context) {
        BuggySelfDevelopmentRevision current = current(itemId, expectedRevision, context);
        requireState(current, BuggySelfDevelopmentState.WAITING_FOR_OWNER_APPROVAL);
        BuggySelfDevelopmentApprovalStatus status = approvals.status(current.approvalBinding());
        if (status == BuggySelfDevelopmentApprovalStatus.PENDING
                || status == BuggySelfDevelopmentApprovalStatus.TERMINAL_FAILURE) {
            return current;
        }
        BuggySelfDevelopmentRevision next = next(current, BuggySelfDevelopmentState.APPROVED,
                current.proposalSummary(), current.changeType(), current.workspaceRef(), current.patchRef(),
                current.buildArtifactRef(), current.riskLevel(), current.approvalBinding(),
                "Foundation approval bound to exact promotion payload", context);
        BuggySelfDevelopmentRevision persisted = repository.append(expectedRevision, next);
        audit(context, persisted, "APPROVED", true);
        return persisted;
    }

    public BuggySelfDevelopmentRevision markDeployed(
            BuggySelfDevelopmentId itemId,
            long expectedRevision,
            RequestContext context) {
        BuggySelfDevelopmentRevision current = current(itemId, expectedRevision, context);
        requireState(current, BuggySelfDevelopmentState.APPROVED);
        if (!current.approvalBinding().requestType().equals(BuggySelfDevelopmentApprovalPort.PROMOTION_REQUEST_TYPE)
                || approvals.status(current.approvalBinding()) != BuggySelfDevelopmentApprovalStatus.EXECUTED) {
            throw new IllegalStateException("CONTROLLED_DEPLOYMENT_NOT_EXECUTED");
        }
        repository.appendEvidence(evidence(current, BuggySelfDevelopmentEvidenceType.DEPLOYMENT, null,
                current.approvalBinding().requestId().toString(), current.approvalBinding().payloadSha256(), true, context));
        BuggySelfDevelopmentRevision next = next(current, BuggySelfDevelopmentState.DEPLOYED,
                current.proposalSummary(), current.changeType(), current.workspaceRef(), current.patchRef(),
                current.buildArtifactRef(), current.riskLevel(), current.approvalBinding(),
                "Controlled deployment completed through Foundation request execution", context);
        BuggySelfDevelopmentRevision persisted = repository.append(expectedRevision, next);
        audit(context, persisted, "DEPLOYED", true);
        return persisted;
    }

    public BuggySelfDevelopmentRevision verifyDeployment(
            BuggySelfDevelopmentId itemId,
            long expectedRevision,
            String verificationRef,
            String verificationSha256,
            RequestContext context) {
        BuggySelfDevelopmentRevision current = current(itemId, expectedRevision, context);
        requireState(current, BuggySelfDevelopmentState.DEPLOYED);
        BuggySelfDevelopmentEvidence proof = evidence(current, BuggySelfDevelopmentEvidenceType.POST_DEPLOY_VERIFICATION,
                TOOL_DEPLOY_VERIFY, verificationRef, verificationSha256, true, context);
        repository.appendEvidence(proof);
        BuggySelfDevelopmentRevision next = next(current, BuggySelfDevelopmentState.VERIFIED,
                current.proposalSummary(), current.changeType(), current.workspaceRef(), current.patchRef(),
                current.buildArtifactRef(), current.riskLevel(), current.approvalBinding(),
                "Post-deployment verification succeeded", context);
        BuggySelfDevelopmentRevision persisted = repository.append(expectedRevision, next);
        audit(context, persisted, "VERIFIED", true);
        return persisted;
    }

    public BuggySelfDevelopmentRevision requestRollback(
            BuggySelfDevelopmentId itemId,
            long expectedRevision,
            String deploymentReference,
            RequestContext context) {
        BuggySelfDevelopmentRevision current = current(itemId, expectedRevision, context);
        if (current.state() != BuggySelfDevelopmentState.DEPLOYED
                && current.state() != BuggySelfDevelopmentState.VERIFIED) {
            throw new IllegalStateException("ROLLBACK_REQUEST_NOT_ALLOWED_IN_STATE_" + current.state());
        }
        BuggySelfDevelopmentApprovalBinding rollback = approvals.submitRollback(
                itemId, current.revision(), safeRef(deploymentReference, "deploymentReference"), current.riskLevel(), context);
        if (!rollback.requestType().equals(BuggySelfDevelopmentApprovalPort.ROLLBACK_REQUEST_TYPE)
                || rollback.submittedItemRevision() != current.revision()
                || rollback.riskLevel() != current.riskLevel()) {
            throw new IllegalStateException("Foundation rollback binding does not match Self-Development item");
        }
        repository.appendEvidence(evidence(current, BuggySelfDevelopmentEvidenceType.APPROVAL_REQUEST, null,
                rollback.requestId().toString(), rollback.payloadSha256(), true, context));
        BuggySelfDevelopmentRevision next = next(current, current.state(), current.proposalSummary(), current.changeType(),
                current.workspaceRef(), current.patchRef(), current.buildArtifactRef(), current.riskLevel(), rollback,
                "Rollback requested through Foundation Request / Approval", context);
        BuggySelfDevelopmentRevision persisted = repository.append(expectedRevision, next);
        audit(context, persisted, "REQUEST_ROLLBACK", true);
        return persisted;
    }

    public BuggySelfDevelopmentRevision markRolledBack(
            BuggySelfDevelopmentId itemId,
            long expectedRevision,
            RequestContext context) {
        BuggySelfDevelopmentRevision current = current(itemId, expectedRevision, context);
        if (current.state() != BuggySelfDevelopmentState.DEPLOYED
                && current.state() != BuggySelfDevelopmentState.VERIFIED) {
            throw new IllegalStateException("ROLLBACK_NOT_ALLOWED_IN_STATE_" + current.state());
        }
        BuggySelfDevelopmentApprovalBinding rollback = current.approvalBinding();
        if (!rollback.requestType().equals(BuggySelfDevelopmentApprovalPort.ROLLBACK_REQUEST_TYPE)
                || approvals.status(rollback) != BuggySelfDevelopmentApprovalStatus.EXECUTED) {
            throw new IllegalStateException("CONTROLLED_ROLLBACK_NOT_EXECUTED");
        }
        repository.appendEvidence(evidence(current, BuggySelfDevelopmentEvidenceType.ROLLBACK, null,
                rollback.requestId().toString(), rollback.payloadSha256(), true, context));
        BuggySelfDevelopmentRevision next = next(current, BuggySelfDevelopmentState.ROLLED_BACK,
                current.proposalSummary(), current.changeType(), current.workspaceRef(), current.patchRef(),
                current.buildArtifactRef(), current.riskLevel(), rollback,
                "Controlled rollback completed through Foundation request execution", context);
        BuggySelfDevelopmentRevision persisted = repository.append(expectedRevision, next);
        audit(context, persisted, "ROLLED_BACK", true);
        return persisted;
    }

    private BuggySelfDevelopmentRevision current(
            BuggySelfDevelopmentId itemId, long expectedRevision, RequestContext context) {
        requireBuggyContext(context);
        if (expectedRevision < 1) throw new IllegalArgumentException("expectedRevision must be >= 1");
        BuggySelfDevelopmentRevision current = repository.findCurrent(Objects.requireNonNull(itemId, "itemId"))
                .orElseThrow(() -> new IllegalArgumentException("Unknown Self-Development item"));
        if (current.revision() != expectedRevision) throw new IllegalStateException("STALE_SELF_DEVELOPMENT_REVISION");
        return current;
    }

    private BuggySelfDevelopmentRevision next(
            BuggySelfDevelopmentRevision current,
            BuggySelfDevelopmentState state,
            String proposalSummary,
            BuggySelfDevelopmentChangeType changeType,
            String workspaceRef,
            String patchRef,
            String buildArtifactRef,
            ToolRiskLevel riskLevel,
            BuggySelfDevelopmentApprovalBinding approvalBinding,
            String reason,
            RequestContext context) {
        return new BuggySelfDevelopmentRevision(
                current.itemId(), current.revision() + 1, current.revision(), state,
                current.title(), current.problem(), proposalSummary, changeType, current.sourceSnapshot(),
                workspaceRef, patchRef, buildArtifactRef, riskLevel, approvalBinding,
                text(reason, "reason", 4096), context.actorId(), clock.instant());
    }

    private BuggySelfDevelopmentEvidence evidence(
            BuggySelfDevelopmentRevision current,
            BuggySelfDevelopmentEvidenceType type,
            ToolId toolId,
            String referenceId,
            String sha256,
            boolean successful,
            RequestContext context) {
        return new BuggySelfDevelopmentEvidence(UUID.randomUUID(), current.itemId(), current.revision(), type, toolId,
                safeRef(referenceId, "referenceId"), requireSha256(sha256), successful, context.actorId(), clock.instant());
    }

    private void requireSourceStillCurrent(SourceSnapshot tested) {
        SourceSnapshot current = sourceRegistry.getSnapshot(new SourceComponentRef(tested.component()))
                .orElseThrow(() -> new IllegalStateException("SOURCE_SNAPSHOT_UNAVAILABLE"));
        if (!sameSourceIdentity(tested, current)) {
            throw new IllegalStateException("SOURCE_SNAPSHOT_STALE_REBASE_REQUIRED");
        }
    }

    private static boolean sameSourceIdentity(SourceSnapshot left, SourceSnapshot right) {
        return left.component().equals(right.component())
                && left.version().equals(right.version())
                && left.build().equals(right.build())
                && left.revision().equals(right.revision())
                && left.checksum().equals(right.checksum());
    }

    private void requirePromotionEvidence(List<BuggySelfDevelopmentEvidence> evidence) {
        requireLatestSuccessful(evidence, BuggySelfDevelopmentEvidenceType.WORKSPACE);
        requireLatestSuccessful(evidence, BuggySelfDevelopmentEvidenceType.PATCH);
        requireLatestSuccessful(evidence, BuggySelfDevelopmentEvidenceType.BUILD);
        requireLatestSuccessful(evidence, BuggySelfDevelopmentEvidenceType.TEST);
        requireLatestSuccessful(evidence, BuggySelfDevelopmentEvidenceType.SECURITY_SCAN);
        if (latestSuccessful(evidence, BuggySelfDevelopmentEvidenceType.BENCHMARK).isEmpty()
                && latestSuccessful(evidence, BuggySelfDevelopmentEvidenceType.IMPACT_COMPARISON).isEmpty()) {
            throw new IllegalStateException("BENCHMARK_OR_IMPACT_EVIDENCE_REQUIRED");
        }
        requireLatestSuccessful(evidence, BuggySelfDevelopmentEvidenceType.SELF_REVIEW);
    }

    private static void requireLatestSuccessful(
            List<BuggySelfDevelopmentEvidence> evidence, BuggySelfDevelopmentEvidenceType type) {
        BuggySelfDevelopmentEvidence latest = latest(evidence, type)
                .orElseThrow(() -> new IllegalStateException(type + "_EVIDENCE_REQUIRED"));
        if (!latest.successful()) throw new IllegalStateException(type + "_LATEST_EVIDENCE_FAILED");
    }

    private static Optional<BuggySelfDevelopmentEvidence> latestSuccessful(
            List<BuggySelfDevelopmentEvidence> evidence, BuggySelfDevelopmentEvidenceType type) {
        Optional<BuggySelfDevelopmentEvidence> latest = latest(evidence, type);
        return latest.filter(BuggySelfDevelopmentEvidence::successful);
    }

    private Optional<BuggySelfDevelopmentEvidence> latestEvidence(
            BuggySelfDevelopmentId itemId, BuggySelfDevelopmentEvidenceType type) {
        return latest(repository.evidence(itemId), type);
    }

    private static Optional<BuggySelfDevelopmentEvidence> latest(
            List<BuggySelfDevelopmentEvidence> evidence, BuggySelfDevelopmentEvidenceType type) {
        for (int index = evidence.size() - 1; index >= 0; index--) {
            BuggySelfDevelopmentEvidence candidate = evidence.get(index);
            if (candidate.type() == type) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    private static void requireEvidenceState(BuggySelfDevelopmentRevision current, BuggySelfDevelopmentEvidenceType type) {
        if (type == BuggySelfDevelopmentEvidenceType.BUILD
                || type == BuggySelfDevelopmentEvidenceType.TEST
                || type == BuggySelfDevelopmentEvidenceType.SECURITY_SCAN
                || type == BuggySelfDevelopmentEvidenceType.BENCHMARK
                || type == BuggySelfDevelopmentEvidenceType.IMPACT_COMPARISON) {
            requireState(current, BuggySelfDevelopmentState.TESTING);
            return;
        }
        if (type == BuggySelfDevelopmentEvidenceType.POST_DEPLOY_VERIFICATION) {
            requireState(current, BuggySelfDevelopmentState.DEPLOYED);
            return;
        }
        throw new IllegalArgumentException("Evidence type is managed by a dedicated B7 lifecycle method: " + type);
    }

    private static void requireExpectedTool(BuggySelfDevelopmentEvidenceType type, ToolId toolId) {
        ToolId expected = switch (type) {
            case BUILD -> TOOL_BUILD;
            case TEST -> TOOL_TEST;
            case SECURITY_SCAN -> TOOL_SECURITY_SCAN;
            case BENCHMARK -> TOOL_BENCHMARK;
            case IMPACT_COMPARISON -> TOOL_COMPARE;
            case POST_DEPLOY_VERIFICATION -> TOOL_DEPLOY_VERIFY;
            default -> throw new IllegalArgumentException("No external tool mapping for evidence type " + type);
        };
        if (!expected.equals(toolId)) throw new IllegalArgumentException("Unexpected tool for " + type + ": " + toolId);
    }

    private static void requireNoRiskDiscount(ToolRiskLevel riskLevel) {
        Objects.requireNonNull(riskLevel, "riskLevel");
        if (riskLevel == ToolRiskLevel.LOW) {
            throw new IllegalArgumentException("Self-authored workspace/source changes cannot receive a LOW risk discount");
        }
    }

    private static void requireState(BuggySelfDevelopmentRevision current, BuggySelfDevelopmentState expected) {
        if (current.state() != expected) {
            throw new IllegalStateException("Expected state " + expected + " but was " + current.state());
        }
    }

    private static void requireBuggyContext(RequestContext context) {
        Objects.requireNonNull(context, "context");
        if (context.assistantId() != AssistantId.BUGGY) {
            throw new IllegalArgumentException("Self-Development context must target BUGGY");
        }
    }

    private String text(String value, String field, int maxLength) {
        return contentPolicy.requirePersistableText(value, field, maxLength);
    }

    private void audit(RequestContext context, BuggySelfDevelopmentRevision revision, String operation, boolean critical) {
        audit.append(new AuditRecord(
                AuditAction.TOOL_INVOCATION,
                AuditOutcome.SUCCESS,
                context.requestId(),
                context.traceId(),
                context.actorId(),
                null,
                AssistantId.BUGGY,
                "buggy-self-development",
                context.serverId(),
                "BUGGY_SELFDEV",
                revision.itemId().toString(),
                clock.instant(),
                Map.of(
                        "operation", operation,
                        "revision", Long.toString(revision.revision()),
                        "state", revision.state().name(),
                        "risk", revision.riskLevel() == null ? "UNCLASSIFIED" : revision.riskLevel().name()),
                critical));
    }

    private static String evidenceDigest(BuggySelfDevelopmentEvidence evidence) {
        String canonical = String.join("|",
                evidence.type().name(),
                evidence.toolId() == null ? "NONE" : evidence.toolId().value(),
                evidence.referenceId(),
                evidence.sha256(),
                Boolean.toString(evidence.successful()));
        return sha256(canonical);
    }

    private static String requireSha256(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be 64 lowercase hex characters");
        }
        return value;
    }

    private static String safeRef(String value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " must not be null");
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > 512 || !normalized.matches("[A-Za-z0-9][A-Za-z0-9._:/-]*")) {
            throw new IllegalArgumentException(field + " is not a safe reference");
        }
        return normalized;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
