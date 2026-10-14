package de.legitserver.assistant.buggy.knowledge;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.knowledge.model.KnowledgeDocumentId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/** Deterministic canonicalization for B4 append-only annotation integrity. */
public final class BuggyKnowledgeCanonicalizer {
    private BuggyKnowledgeCanonicalizer() {}

    public static String annotationSha256(
            KnowledgeDocumentId documentId,
            long revision,
            Long previousRevision,
            BuggyKnowledgeState state,
            String reason,
            String evidenceRef,
            BuggyKnowledgeConfidence confidence,
            ActorId createdBy,
            Instant createdAt) {
        String canonical = String.join("\n",
                "documentId=" + documentId.value(),
                "revision=" + revision,
                "previousRevision=" + (previousRevision == null ? "" : previousRevision),
                "state=" + state.name(),
                "reason=" + reason,
                "evidenceRef=" + evidenceRef,
                "confidence=" + confidence.name(),
                "createdBy=" + createdBy.value(),
                "createdAt=" + createdAt);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
