package de.legitserver.assistant.buggy.nativecore;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Persisted G1.4 foundation snapshot. Later blocks append histories instead of mutating this baseline. */
public record BuggyNativeCoreState(
        int schemaVersion,
        BuggyBirthRecord birth,
        BuggyCoreProtocolCatalog protocols,
        BuggyNativePersonalityBaseline personalityBaseline,
        BuggyMaturityProfile maturityBaseline,
        List<BuggySelfCapabilityEntry> capabilities,
        String sha256) {

    public static final int SCHEMA_VERSION = 1;

    public BuggyNativeCoreState {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Buggy Native-Core schema version");
        }
        birth = CoreValidation.requireNonNull(birth, "birth");
        protocols = CoreValidation.requireNonNull(protocols, "protocols");
        personalityBaseline = CoreValidation.requireNonNull(personalityBaseline, "personalityBaseline");
        maturityBaseline = CoreValidation.requireNonNull(maturityBaseline, "maturityBaseline");
        capabilities = List.copyOf(CoreValidation.requireNonNull(capabilities, "capabilities"));
        Set<String> ids = new HashSet<>();
        for (BuggySelfCapabilityEntry capability : capabilities) {
            if (!ids.add(capability.capabilityId())) {
                throw new IllegalArgumentException("Duplicate self capability: " + capability.capabilityId());
            }
        }
        sha256 = CoreValidation.requireSafeToken(sha256, "sha256", 64);
        if (!canonicalSha256(birth, protocols, personalityBaseline, maturityBaseline, capabilities).equals(sha256)) {
            throw new IllegalArgumentException("Buggy Native-Core state SHA-256 mismatch");
        }
    }

    public static BuggyNativeCoreState firstActivation(Instant bornAt) {
        BuggyBirthRecord birth = BuggyBirthRecord.firstActivation(bornAt);
        BuggyCoreProtocolCatalog protocols = BuggyCoreProtocolCatalog.canonical();
        BuggyNativePersonalityBaseline personality = BuggyNativePersonalityBaseline.canonical();
        BuggyMaturityProfile maturity = BuggyMaturityProfile.bootstrap();
        List<BuggySelfCapabilityEntry> capabilities = List.of(
                capability("identity.self", BuggySelfCapabilityStatus.AVAILABLE, 10_000, null),
                capability("authority.respect", BuggySelfCapabilityStatus.AVAILABLE, 10_000, null),
                capability("conversation.native", BuggySelfCapabilityStatus.LIMITED, 3_000, "Natural Conversation arrives in G1.6."),
                capability("knowledge.network", BuggySelfCapabilityStatus.LIMITED, 2_500, "Native Knowledge and read-only tools arrive in G1.7."),
                capability("memory.session", BuggySelfCapabilityStatus.LIMITED, 1_500, "Full Memory and Development arrives in G1.5."),
                capability("memory.long-term", BuggySelfCapabilityStatus.UNAVAILABLE, 0, "Long-term Native Memory is not active in G1.4."),
                capability("reflection.native", BuggySelfCapabilityStatus.LIMITED, 1_500, "Reflection lifecycle arrives in G1.5/G1.9."),
                capability("tools.read-only", BuggySelfCapabilityStatus.LIMITED, 1_000, "Native read-only tool set arrives in G1.7."),
                capability("model.external", BuggySelfCapabilityStatus.UNAVAILABLE, 0, "Model Gateway is intentionally deferred to G1.10."),
                capability("source.analysis", BuggySelfCapabilityStatus.UNAVAILABLE, 0, "Advanced source analysis is deferred to G1.11+."));
        return new BuggyNativeCoreState(
                SCHEMA_VERSION,
                birth,
                protocols,
                personality,
                maturity,
                capabilities,
                canonicalSha256(birth, protocols, personality, maturity, capabilities));
    }

    private static BuggySelfCapabilityEntry capability(
            String id, BuggySelfCapabilityStatus status, int confidenceBps, String limitation) {
        return new BuggySelfCapabilityEntry(id, status, confidenceBps, limitation);
    }

    private static String canonicalSha256(
            BuggyBirthRecord birth,
            BuggyCoreProtocolCatalog protocols,
            BuggyNativePersonalityBaseline personality,
            BuggyMaturityProfile maturity,
            List<BuggySelfCapabilityEntry> capabilities) {
        StringBuilder canonical = new StringBuilder()
                .append("schemaVersion=").append(SCHEMA_VERSION).append('\n')
                .append("conceptDate=").append(birth.conceptDate()).append('\n')
                .append("bornAt=").append(birth.bornAt()).append('\n')
                .append("birthType=").append(birth.birthType()).append('\n')
                .append("initialStage=").append(birth.initialStage()).append('\n')
                .append("protocols=").append(protocols.sha256()).append('\n')
                .append("personality=").append(personality.sha256()).append('\n')
                .append("maturityStage=").append(maturity.stage()).append('\n');
        maturity.entries().stream().sorted(Comparator.comparing(entry -> entry.dimension().name())).forEach(entry -> canonical
                .append("maturity.").append(entry.dimension()).append('=').append(entry.scoreBps()).append('\n'));
        capabilities.stream().sorted(Comparator.comparing(BuggySelfCapabilityEntry::capabilityId)).forEach(entry -> canonical
                .append("capability.").append(entry.capabilityId()).append('=')
                .append(entry.status()).append(':')
                .append(entry.confidenceBps()).append(':')
                .append(entry.limitation() == null ? "" : entry.limitation()).append('\n'));
        return BuggyNativeCoreCanonicalizer.sha256(canonical.toString());
    }
}
