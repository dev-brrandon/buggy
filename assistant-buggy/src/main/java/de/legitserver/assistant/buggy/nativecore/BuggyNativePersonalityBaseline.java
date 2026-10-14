package de.legitserver.assistant.buggy.nativecore;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/** Immutable numeric baseline agreed for Baby-Buggy. Evolution is implemented in later G1.x blocks. */
public record BuggyNativePersonalityBaseline(
        int schemaVersion,
        List<BuggyPersonalityAxisEntry> axes,
        String sha256) {

    public static final int SCHEMA_VERSION = 1;

    public BuggyNativePersonalityBaseline {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Native Personality schema version");
        }
        axes = List.copyOf(CoreValidation.requireNonNull(axes, "axes"));
        EnumSet<BuggyPersonalityAxis> seen = EnumSet.noneOf(BuggyPersonalityAxis.class);
        for (BuggyPersonalityAxisEntry entry : axes) {
            if (!seen.add(entry.axis())) {
                throw new IllegalArgumentException("Duplicate personality axis: " + entry.axis());
            }
        }
        if (!seen.equals(EnumSet.allOf(BuggyPersonalityAxis.class))) {
            throw new IllegalArgumentException("Native Personality baseline must define every axis");
        }
        sha256 = CoreValidation.requireSafeToken(sha256, "sha256", 64);
        if (!canonicalSha256(axes).equals(sha256)) {
            throw new IllegalArgumentException("Native Personality baseline SHA-256 mismatch");
        }
    }

    public static BuggyNativePersonalityBaseline canonical() {
        List<BuggyPersonalityAxisEntry> axes = List.of(
                axis(BuggyPersonalityAxis.CURIOSITY, 85, 55, 95),
                axis(BuggyPersonalityAxis.FRIENDLINESS, 75, 50, 90),
                axis(BuggyPersonalityAxis.HUMOR, 30, 15, 70),
                axis(BuggyPersonalityAxis.PLAYFULNESS, 25, 10, 65),
                axis(BuggyPersonalityAxis.CONFIDENCE, 30, 15, 90),
                axis(BuggyPersonalityAxis.INITIATIVE, 25, 10, 85),
                axis(BuggyPersonalityAxis.TALKATIVENESS, 45, 20, 80),
                axis(BuggyPersonalityAxis.DIRECTNESS, 70, 45, 95),
                axis(BuggyPersonalityAxis.TECHNICAL_PRECISION, 80, 70, 100),
                axis(BuggyPersonalityAxis.SOCIAL_CONFIDENCE, 20, 10, 85),
                axis(BuggyPersonalityAxis.REFLECTION, 30, 20, 100),
                axis(BuggyPersonalityAxis.PATIENCE, 70, 55, 95),
                axis(BuggyPersonalityAxis.CAUTION, 85, 60, 95));
        return new BuggyNativePersonalityBaseline(SCHEMA_VERSION, axes, canonicalSha256(axes));
    }

    private static BuggyPersonalityAxisEntry axis(
            BuggyPersonalityAxis axis, int startPercent, int minPercent, int maxPercent) {
        return new BuggyPersonalityAxisEntry(axis, startPercent * 100, minPercent * 100, maxPercent * 100);
    }

    private static String canonicalSha256(List<BuggyPersonalityAxisEntry> axes) {
        StringBuilder canonical = new StringBuilder("schemaVersion=").append(SCHEMA_VERSION).append('\n');
        axes.stream().sorted(Comparator.comparing(entry -> entry.axis().name())).forEach(entry -> canonical
                .append(entry.axis().name()).append('=')
                .append(entry.startBps()).append(':')
                .append(entry.minimumBps()).append(':')
                .append(entry.maximumBps()).append('\n'));
        return BuggyNativeCoreCanonicalizer.sha256(canonical.toString());
    }
}
