package de.legitserver.assistant.contracts.inference;

import de.legitserver.assistant.contracts.common.ContractValidation;
import de.legitserver.assistant.core.classification.DataClassification;
import java.util.List;
import java.util.Objects;

/** Provider-neutral inference request with explicit classification ceiling and bounded text inputs. */
public record InferenceRequest(
        String requestId,
        List<Input> inputs,
        DataClassification inputClassification,
        DataClassification maxOutputClassification,
        int maxOutputCharacters) {
    public InferenceRequest {
        requestId = ContractValidation.id(requestId, "requestId");
        if (inputs == null || inputs.isEmpty() || inputs.size() > 32) throw new IllegalArgumentException("inputs must contain 1..32 items");
        inputs = List.copyOf(inputs);
        inputClassification = Objects.requireNonNull(inputClassification, "inputClassification");
        maxOutputClassification = Objects.requireNonNull(maxOutputClassification, "maxOutputClassification");
        if (!maxOutputClassification.isAtLeast(inputClassification)) {
            throw new IllegalArgumentException("output classification ceiling must cover input classification");
        }
        if (maxOutputCharacters < 1 || maxOutputCharacters > 100_000) throw new IllegalArgumentException("maxOutputCharacters must be 1..100000");
    }

    public record Input(Role role, String text) {
        public Input {
            role = Objects.requireNonNull(role, "role");
            text = ContractValidation.text(text, "text", 100_000);
        }
    }

    public enum Role { SYSTEM, USER, CONTEXT }
}
