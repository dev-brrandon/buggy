package de.legitserver.assistant.buggy.nativecore;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.time.LocalDate;

/** The concept date is historical; bornAt is written only on first successful Native-Core activation. */
public record BuggyBirthRecord(
        LocalDate conceptDate,
        Instant bornAt,
        BuggyBirthType birthType,
        BuggyMaturityStage initialStage) {

    public static final LocalDate CONCEPT_DATE = LocalDate.of(2026, 10, 6);

    public BuggyBirthRecord {
        conceptDate = CoreValidation.requireNonNull(conceptDate, "conceptDate");
        bornAt = CoreValidation.requireNonNull(bornAt, "bornAt");
        birthType = CoreValidation.requireNonNull(birthType, "birthType");
        initialStage = CoreValidation.requireNonNull(initialStage, "initialStage");
        if (!CONCEPT_DATE.equals(conceptDate)) {
            throw new IllegalArgumentException("Buggy Native-Core concept date is protected");
        }
        if (initialStage != BuggyMaturityStage.BOOTSTRAP) {
            throw new IllegalArgumentException("Buggy must be born in BOOTSTRAP stage");
        }
    }

    public static BuggyBirthRecord firstActivation(Instant bornAt) {
        return new BuggyBirthRecord(
                CONCEPT_DATE,
                bornAt,
                BuggyBirthType.NATIVE_CORE_ACTIVATION,
                BuggyMaturityStage.BOOTSTRAP);
    }
}
