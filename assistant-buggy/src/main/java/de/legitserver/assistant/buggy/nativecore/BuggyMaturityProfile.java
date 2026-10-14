package de.legitserver.assistant.buggy.nativecore;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.EnumSet;
import java.util.List;

public record BuggyMaturityProfile(BuggyMaturityStage stage, List<BuggyMaturityEntry> entries) {
    public BuggyMaturityProfile {
        stage = CoreValidation.requireNonNull(stage, "stage");
        entries = List.copyOf(CoreValidation.requireNonNull(entries, "entries"));
        EnumSet<BuggyMaturityDimension> seen = EnumSet.noneOf(BuggyMaturityDimension.class);
        for (BuggyMaturityEntry entry : entries) {
            if (!seen.add(entry.dimension())) {
                throw new IllegalArgumentException("Duplicate maturity dimension: " + entry.dimension());
            }
        }
        if (!seen.equals(EnumSet.allOf(BuggyMaturityDimension.class))) {
            throw new IllegalArgumentException("Maturity profile must define every maturity dimension");
        }
    }

    public static BuggyMaturityProfile bootstrap() {
        return new BuggyMaturityProfile(BuggyMaturityStage.BOOTSTRAP, List.of(
                new BuggyMaturityEntry(BuggyMaturityDimension.TECHNICAL, 1_500),
                new BuggyMaturityEntry(BuggyMaturityDimension.SOCIAL, 500),
                new BuggyMaturityEntry(BuggyMaturityDimension.MEMORY, 1_000),
                new BuggyMaturityEntry(BuggyMaturityDimension.REASONING, 1_000),
                new BuggyMaturityEntry(BuggyMaturityDimension.SELF_REFLECTION, 1_000),
                new BuggyMaturityEntry(BuggyMaturityDimension.SECURITY_RELIABILITY, 10_000),
                new BuggyMaturityEntry(BuggyMaturityDimension.OWNER_RELATIONSHIP, 1_500)));
    }
}
