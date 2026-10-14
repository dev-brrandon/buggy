package de.legitserver.assistant.buggy.personality;

import de.legitserver.assistant.core.actor.ActorId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.HexFormat;

/** Deterministic B2 canonicalization for seed, observations and Personality State revisions. */
public final class BuggyPersonalityCanonicalizer {
    private static final Comparator<BuggyPersonalityStateEntry> ENTRY_ORDER = Comparator
            .comparing((BuggyPersonalityStateEntry entry) -> entry.domain().name())
            .thenComparing(BuggyPersonalityStateEntry::attributeKey);

    private BuggyPersonalityCanonicalizer() {}

    public static void requireUniqueEntries(List<BuggyPersonalityStateEntry> entries) {
        Set<String> keys = new HashSet<>();
        for (BuggyPersonalityStateEntry entry : entries) {
            if (!keys.add(entry.identityKey())) {
                throw new IllegalArgumentException("Duplicate personality state entry: " + entry.identityKey());
            }
        }
    }

    public static String seedSha256(
            String profileId, int seedVersion, List<BuggyPersonalityStateEntry> entries) {
        StringBuilder payload = new StringBuilder();
        payload.append("profileId=").append(profileId).append('\n');
        payload.append("seedVersion=").append(seedVersion).append('\n');
        appendEntries(payload, entries);
        return sha256(payload.toString());
    }

    public static String observationSha256(
            String observationId,
            BuggyPersonalityDomain domain,
            String attributeKey,
            String observedValue,
            BuggyPersonalityObservationOrigin origin,
            int confidenceBps,
            List<BuggyPersonalityEvidenceRef> evidence,
            String note,
            ActorId createdBy,
            Instant createdAt) {
        StringBuilder payload = new StringBuilder();
        payload.append("observationId=").append(observationId).append('\n');
        payload.append("domain=").append(domain.name()).append('\n');
        payload.append("attributeKey=").append(attributeKey).append('\n');
        payload.append("observedValue=").append(observedValue).append('\n');
        payload.append("origin=").append(origin.name()).append('\n');
        payload.append("confidenceBps=").append(confidenceBps).append('\n');
        for (int i = 0; i < evidence.size(); i++) {
            BuggyPersonalityEvidenceRef item = evidence.get(i);
            payload.append("evidence[").append(i).append("]=")
                    .append(item.type().name()).append('|')
                    .append(item.referenceId()).append('|')
                    .append(item.observedAt()).append('\n');
        }
        payload.append("note=").append(note).append('\n');
        payload.append("createdBy=").append(createdBy.value()).append('\n');
        payload.append("createdAt=").append(createdAt).append('\n');
        return sha256(payload.toString());
    }

    public static String revisionSha256(
            long revision,
            Long previousRevision,
            BuggyPersonalityRevisionOrigin origin,
            String reason,
            int confidenceBps,
            Long rollbackTargetRevision,
            List<String> changedAttributes,
            List<String> evidenceObservationIds,
            List<BuggyPersonalityStateEntry> entries,
            ActorId createdBy,
            Instant createdAt) {
        StringBuilder payload = new StringBuilder();
        payload.append("revision=").append(revision).append('\n');
        payload.append("previousRevision=").append(previousRevision == null ? "" : previousRevision).append('\n');
        payload.append("origin=").append(origin.name()).append('\n');
        payload.append("reason=").append(reason).append('\n');
        payload.append("confidenceBps=").append(confidenceBps).append('\n');
        payload.append("rollbackTargetRevision=")
                .append(rollbackTargetRevision == null ? "" : rollbackTargetRevision).append('\n');
        changedAttributes.stream().sorted().forEach(value -> payload.append("changed=").append(value).append('\n'));
        evidenceObservationIds.stream().sorted().forEach(value -> payload.append("evidenceObservation=").append(value).append('\n'));
        appendEntries(payload, entries);
        payload.append("createdBy=").append(createdBy.value()).append('\n');
        payload.append("createdAt=").append(createdAt).append('\n');
        return sha256(payload.toString());
    }

    private static void appendEntries(StringBuilder payload, List<BuggyPersonalityStateEntry> entries) {
        entries.stream().sorted(ENTRY_ORDER).forEach(entry -> payload.append("entry=")
                .append(entry.domain().name()).append('|')
                .append(entry.attributeKey()).append('|')
                .append(entry.value()).append('|')
                .append(entry.confidenceBps()).append('|')
                .append(entry.sourceObservationId() == null ? "" : entry.sourceObservationId())
                .append('\n'));
    }

    private static String sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }
}
