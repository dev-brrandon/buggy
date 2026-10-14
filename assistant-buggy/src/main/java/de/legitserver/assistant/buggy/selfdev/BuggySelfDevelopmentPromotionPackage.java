package de.legitserver.assistant.buggy.selfdev;

import de.legitserver.assistant.contracts.source.SourceSnapshot;
import de.legitserver.assistant.runtime.model.ToolRiskLevel;
import java.util.List;
import java.util.Objects;

/** Immutable canonical payload submitted into the existing Foundation Request / Approval system. */
public record BuggySelfDevelopmentPromotionPackage(
        BuggySelfDevelopmentId itemId,
        long submittedRevision,
        SourceSnapshot sourceSnapshot,
        String patchRef,
        String buildArtifactRef,
        ToolRiskLevel riskLevel,
        List<String> evidenceSha256) {
    public BuggySelfDevelopmentPromotionPackage {
        itemId = Objects.requireNonNull(itemId, "itemId");
        if (submittedRevision < 1) throw new IllegalArgumentException("submittedRevision must be >= 1");
        sourceSnapshot = Objects.requireNonNull(sourceSnapshot, "sourceSnapshot");
        patchRef = requireRef(patchRef, "patchRef");
        buildArtifactRef = requireRef(buildArtifactRef, "buildArtifactRef");
        riskLevel = Objects.requireNonNull(riskLevel, "riskLevel");
        evidenceSha256 = List.copyOf(Objects.requireNonNull(evidenceSha256, "evidenceSha256"));
        if (evidenceSha256.isEmpty()) throw new IllegalArgumentException("evidenceSha256 must not be empty");
        for (String hash : evidenceSha256) {
            if (hash == null || !hash.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("evidence hash must be 64 lowercase hex characters");
            }
        }
    }

    public String canonicalBody() {
        return String.join("\n",
                "itemId=" + itemId,
                "submittedRevision=" + submittedRevision,
                "source.component=" + sourceSnapshot.component(),
                "source.version=" + sourceSnapshot.version(),
                "source.build=" + sourceSnapshot.build(),
                "source.revision=" + sourceSnapshot.revision(),
                "source.checksum=" + sourceSnapshot.checksum(),
                "patchRef=" + patchRef,
                "buildArtifactRef=" + buildArtifactRef,
                "risk=" + riskLevel.name(),
                "evidence=" + String.join(",", evidenceSha256));
    }

    private static String requireRef(String value, String label) {
        if (value == null) throw new IllegalArgumentException(label + " must not be null");
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > 512 || !normalized.matches("[A-Za-z0-9][A-Za-z0-9._:/-]*")) {
            throw new IllegalArgumentException(label + " is not a safe reference");
        }
        return normalized;
    }
}
