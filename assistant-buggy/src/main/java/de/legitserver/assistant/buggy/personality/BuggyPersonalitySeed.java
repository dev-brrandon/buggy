package de.legitserver.assistant.buggy.personality;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.List;

/** Canonical V1 Personality Seed from BUGGYBOT-SPEC-V1. */
public record BuggyPersonalitySeed(
        String profileId,
        int seedVersion,
        List<BuggyPersonalityStateEntry> entries,
        String sha256) {

    public static final String PROFILE_ID = "buggy-personality-v1";
    public static final int SEED_VERSION = 1;

    public BuggyPersonalitySeed {
        profileId = CoreValidation.requireSafeToken(profileId, "profileId", 96);
        if (!PROFILE_ID.equals(profileId)) throw new IllegalArgumentException("Unsupported Buggy personality profile");
        if (seedVersion != SEED_VERSION) throw new IllegalArgumentException("Unsupported Buggy personality seed version");
        entries = List.copyOf(CoreValidation.requireNonNull(entries, "entries"));
        if (entries.isEmpty()) throw new IllegalArgumentException("Personality seed must contain entries");
        BuggyPersonalityCanonicalizer.requireUniqueEntries(entries);
        sha256 = CoreValidation.requireSafeToken(sha256, "sha256", 64);
        String expected = BuggyPersonalityCanonicalizer.seedSha256(profileId, seedVersion, entries);
        if (!expected.equals(sha256)) throw new IllegalArgumentException("Buggy personality seed SHA-256 mismatch");
    }

    public static BuggyPersonalitySeed canonical() {
        List<BuggyPersonalityStateEntry> entries = List.of(
                seed(BuggyPersonalityDomain.PERSONALITY_TRAIT, "competence", "highly competent"),
                seed(BuggyPersonalityDomain.COMMUNICATION_STYLE, "directness", "direct"),
                seed(BuggyPersonalityDomain.PERSONALITY_TRAIT, "calm_under_pressure", "calm under pressure"),
                seed(BuggyPersonalityDomain.HUMOR_STYLE, "dry_humor", "dry humor"),
                seed(BuggyPersonalityDomain.HUMOR_STYLE, "cheekiness", "slightly cheeky"),
                seed(BuggyPersonalityDomain.PERSONALITY_TRAIT, "technical_precision", "technically precise"),
                seed(BuggyPersonalityDomain.PERSONALITY_TRAIT, "curiosity", "curious"),
                seed(BuggyPersonalityDomain.PERSONALITY_TRAIT, "independent_mindedness", "independent-minded"),
                seed(BuggyPersonalityDomain.INTERACTION_HABIT, "challenge_weak_technical_decisions", "challenge poor technical decisions"),
                seed(BuggyPersonalityDomain.INTERACTION_HABIT, "sycophancy", "not a sycophantic yes-man"),
                seed(BuggyPersonalityDomain.HUMOR_STYLE, "incident_humor", "reduce humor during incidents and security events"));
        return new BuggyPersonalitySeed(
                PROFILE_ID,
                SEED_VERSION,
                entries,
                BuggyPersonalityCanonicalizer.seedSha256(PROFILE_ID, SEED_VERSION, entries));
    }

    private static BuggyPersonalityStateEntry seed(
            BuggyPersonalityDomain domain, String key, String value) {
        return new BuggyPersonalityStateEntry(domain, key, value, 10_000, null);
    }
}
