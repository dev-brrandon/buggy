package de.legitserver.assistant.buggy.personality;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Complete append-only Personality State revision with explicit delta/evidence metadata. */
public record BuggyPersonalityRevision(
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
        Instant createdAt,
        String sha256) {

    public BuggyPersonalityRevision {
        if (revision < 1) throw new IllegalArgumentException("revision must be >= 1");
        if (revision == 1 && previousRevision != null) {
            throw new IllegalArgumentException("Initial personality revision cannot have previousRevision");
        }
        if (revision > 1 && (previousRevision == null || previousRevision != revision - 1)) {
            throw new IllegalArgumentException("Personality revisions must form a contiguous append-only chain");
        }
        origin = CoreValidation.requireNonNull(origin, "origin");
        reason = CoreValidation.requireText(reason, "reason", 512);
        if (confidenceBps < 0 || confidenceBps > 10_000) {
            throw new IllegalArgumentException("confidenceBps must be between 0 and 10000");
        }
        if (origin == BuggyPersonalityRevisionOrigin.ROLLBACK) {
            if (rollbackTargetRevision == null || rollbackTargetRevision < 1 || rollbackTargetRevision >= revision) {
                throw new IllegalArgumentException("Rollback revision requires an earlier rollback target");
            }
        } else if (rollbackTargetRevision != null) {
            throw new IllegalArgumentException("Only rollback revisions may carry rollbackTargetRevision");
        }

        changedAttributes = validateTokens(changedAttributes, "changedAttributes", 160, true);
        evidenceObservationIds = validateTokens(evidenceObservationIds, "evidenceObservationIds", 64, false);
        if (origin == BuggyPersonalityRevisionOrigin.OBSERVATION_PROMOTION && evidenceObservationIds.isEmpty()) {
            throw new IllegalArgumentException("Observation promotion requires evidence observation IDs");
        }
        if (origin != BuggyPersonalityRevisionOrigin.OBSERVATION_PROMOTION && !evidenceObservationIds.isEmpty()) {
            throw new IllegalArgumentException("Only observation promotion revisions may carry observation evidence IDs");
        }

        entries = List.copyOf(CoreValidation.requireNonNull(entries, "entries"));
        if (entries.isEmpty()) throw new IllegalArgumentException("Personality state must contain entries");
        BuggyPersonalityCanonicalizer.requireUniqueEntries(entries);
        Set<String> stateKeys = new HashSet<>();
        for (BuggyPersonalityStateEntry entry : entries) stateKeys.add(entry.identityKey());
        if (origin != BuggyPersonalityRevisionOrigin.ROLLBACK && !stateKeys.containsAll(changedAttributes)) {
            throw new IllegalArgumentException("Changed personality attributes must exist in the resulting state");
        }
        if (origin == BuggyPersonalityRevisionOrigin.OBSERVATION_PROMOTION) {
            Set<String> evidenceIds = Set.copyOf(evidenceObservationIds);
            for (BuggyPersonalityStateEntry entry : entries) {
                if (changedAttributes.contains(entry.identityKey())
                        && (entry.sourceObservationId() == null || !evidenceIds.contains(entry.sourceObservationId()))) {
                    throw new IllegalArgumentException("Changed promotion entry must reference revision evidence");
                }
            }
        }

        createdBy = CoreValidation.requireNonNull(createdBy, "createdBy");
        createdAt = CoreValidation.requireNonNull(createdAt, "createdAt");
        sha256 = CoreValidation.requireSafeToken(sha256, "sha256", 64);
        String expected = BuggyPersonalityCanonicalizer.revisionSha256(
                revision,
                previousRevision,
                origin,
                reason,
                confidenceBps,
                rollbackTargetRevision,
                changedAttributes,
                evidenceObservationIds,
                entries,
                createdBy,
                createdAt);
        if (!expected.equals(sha256)) throw new IllegalArgumentException("Buggy personality revision SHA-256 mismatch");
    }

    public String revisionId() {
        return "P-" + String.format("%04d", revision);
    }

    public static BuggyPersonalityRevision initial(
            BuggyPersonalitySeed seed, ActorId createdBy, Instant createdAt) {
        String reason = "Canonical Buggy V1 personality seed";
        List<BuggyPersonalityStateEntry> entries = seed.entries();
        List<String> changed = entries.stream().map(BuggyPersonalityStateEntry::identityKey).sorted().toList();
        List<String> evidence = List.of();
        String sha = BuggyPersonalityCanonicalizer.revisionSha256(
                1L,
                null,
                BuggyPersonalityRevisionOrigin.SEED,
                reason,
                10_000,
                null,
                changed,
                evidence,
                entries,
                createdBy,
                createdAt);
        return new BuggyPersonalityRevision(
                1L,
                null,
                BuggyPersonalityRevisionOrigin.SEED,
                reason,
                10_000,
                null,
                changed,
                evidence,
                entries,
                createdBy,
                createdAt,
                sha);
    }

    private static List<String> validateTokens(
            List<String> values, String field, int maxLength, boolean requireNonEmpty) {
        List<String> copy = List.copyOf(CoreValidation.requireNonNull(values, field));
        if (requireNonEmpty && copy.isEmpty()) throw new IllegalArgumentException(field + " must not be empty");
        Set<String> unique = new HashSet<>();
        for (String value : copy) {
            String token = CoreValidation.requireSafeToken(value, field, maxLength);
            if (!unique.add(token)) throw new IllegalArgumentException(field + " contains duplicates");
        }
        return copy;
    }
}
