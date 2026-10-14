package de.legitserver.assistant.buggy.selfdev;

import de.legitserver.assistant.contracts.source.SourceSnapshot;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.validation.CoreValidation;
import de.legitserver.assistant.runtime.model.ToolRiskLevel;
import java.time.Instant;
import java.util.Objects;

/** Append-only B7 state revision. Source/workspace/build values are references, never uncontrolled source copies. */
public record BuggySelfDevelopmentRevision(
        BuggySelfDevelopmentId itemId,
        long revision,
        Long previousRevision,
        BuggySelfDevelopmentState state,
        String title,
        String problem,
        String proposalSummary,
        BuggySelfDevelopmentChangeType changeType,
        SourceSnapshot sourceSnapshot,
        String workspaceRef,
        String patchRef,
        String buildArtifactRef,
        ToolRiskLevel riskLevel,
        BuggySelfDevelopmentApprovalBinding approvalBinding,
        String reason,
        ActorId changedBy,
        Instant changedAt) {
    public BuggySelfDevelopmentRevision {
        itemId = Objects.requireNonNull(itemId, "itemId");
        if (revision < 1) throw new IllegalArgumentException("revision must be >= 1");
        if ((revision == 1 && previousRevision != null)
                || (revision > 1 && !Long.valueOf(revision - 1).equals(previousRevision))) {
            throw new IllegalArgumentException("revision chain must be contiguous");
        }
        state = Objects.requireNonNull(state, "state");
        title = CoreValidation.requireText(title, "title", 256);
        problem = CoreValidation.requireText(problem, "problem", 8192);
        proposalSummary = optionalMultiline(proposalSummary, "proposalSummary", 8192);
        sourceSnapshot = Objects.requireNonNull(sourceSnapshot, "sourceSnapshot");
        workspaceRef = optionalRef(workspaceRef, "workspaceRef");
        patchRef = optionalRef(patchRef, "patchRef");
        buildArtifactRef = optionalRef(buildArtifactRef, "buildArtifactRef");
        reason = optionalMultiline(reason, "reason", 4096);
        changedBy = Objects.requireNonNull(changedBy, "changedBy");
        changedAt = Objects.requireNonNull(changedAt, "changedAt");
        validateStateFields(state, proposalSummary, changeType, workspaceRef, patchRef, buildArtifactRef, riskLevel, approvalBinding);
    }

    private static void validateStateFields(
            BuggySelfDevelopmentState state,
            String proposalSummary,
            BuggySelfDevelopmentChangeType changeType,
            String workspaceRef,
            String patchRef,
            String buildArtifactRef,
            ToolRiskLevel riskLevel,
            BuggySelfDevelopmentApprovalBinding approvalBinding) {
        if (state == BuggySelfDevelopmentState.OBSERVED) {
            if (proposalSummary != null || changeType != null || riskLevel != null || workspaceRef != null
                    || patchRef != null || buildArtifactRef != null || approvalBinding != null) {
                throw new IllegalArgumentException("OBSERVED state may only contain observation/source metadata");
            }
            return;
        }
        if (proposalSummary == null || changeType == null || riskLevel == null) {
            throw new IllegalArgumentException("proposalSummary, changeType and riskLevel are required after OBSERVED");
        }
        if (state.ordinal() >= BuggySelfDevelopmentState.IMPLEMENTING.ordinal() && state != BuggySelfDevelopmentState.REJECTED_BY_BUGGY
                && workspaceRef == null) {
            throw new IllegalArgumentException("workspaceRef is required from IMPLEMENTING onward");
        }
        if ((state == BuggySelfDevelopmentState.TESTING
                || state == BuggySelfDevelopmentState.WAITING_FOR_OWNER_APPROVAL
                || state == BuggySelfDevelopmentState.APPROVED
                || state == BuggySelfDevelopmentState.DEPLOYED
                || state == BuggySelfDevelopmentState.VERIFIED
                || state == BuggySelfDevelopmentState.ROLLED_BACK) && patchRef == null) {
            throw new IllegalArgumentException("patchRef is required from TESTING onward");
        }
        if ((state == BuggySelfDevelopmentState.WAITING_FOR_OWNER_APPROVAL
                || state == BuggySelfDevelopmentState.APPROVED
                || state == BuggySelfDevelopmentState.DEPLOYED
                || state == BuggySelfDevelopmentState.VERIFIED
                || state == BuggySelfDevelopmentState.ROLLED_BACK) && buildArtifactRef == null) {
            throw new IllegalArgumentException("buildArtifactRef is required for promotion/deployment states");
        }
        boolean approvalState = state == BuggySelfDevelopmentState.WAITING_FOR_OWNER_APPROVAL
                || state == BuggySelfDevelopmentState.APPROVED
                || state == BuggySelfDevelopmentState.DEPLOYED
                || state == BuggySelfDevelopmentState.VERIFIED
                || state == BuggySelfDevelopmentState.ROLLED_BACK;
        if (approvalState != (approvalBinding != null)) {
            throw new IllegalArgumentException("approvalBinding must exist exactly for approval/deployment states");
        }
    }

    private static String optionalMultiline(String value, String label, int maxLength) {
        if (value == null) return null;
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > maxLength || normalized.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(label + " length out of range");
        }
        return normalized;
    }

    private static String optionalRef(String value, String label) {
        if (value == null) return null;
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > 512 || !normalized.matches("[A-Za-z0-9][A-Za-z0-9._:/-]*")) {
            throw new IllegalArgumentException(label + " is not a safe reference");
        }
        return normalized;
    }
}
