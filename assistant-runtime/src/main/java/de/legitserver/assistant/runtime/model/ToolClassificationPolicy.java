package de.legitserver.assistant.runtime.model;

import de.legitserver.assistant.core.classification.DataClassification;
import java.util.Objects;

/** Classification ceilings for data entering and leaving a tool boundary. */
public record ToolClassificationPolicy(
        DataClassification maxInputClassification,
        DataClassification maxOutputClassification) {
    public ToolClassificationPolicy {
        maxInputClassification = Objects.requireNonNull(maxInputClassification, "maxInputClassification");
        maxOutputClassification = Objects.requireNonNull(maxOutputClassification, "maxOutputClassification");
    }

    public boolean allowsInput(DataClassification classification) {
        return Objects.requireNonNull(classification, "classification").ordinal() <= maxInputClassification.ordinal();
    }

    public boolean allowsOutput(DataClassification classification) {
        return Objects.requireNonNull(classification, "classification").ordinal() <= maxOutputClassification.ordinal();
    }
}
