package de.legitserver.assistant.buggy.exclusive;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.List;

public record BuggyChallengeResult(
        String decisionId,
        List<String> counterarguments,
        List<String> blindSpots,
        List<String> verificationQuestions) {
    public BuggyChallengeResult {
        decisionId = CoreValidation.requireSafeToken(decisionId, "decisionId", 160);
        counterarguments = checked(counterarguments, "counterargument");
        blindSpots = checked(blindSpots, "blindSpot");
        verificationQuestions = checked(verificationQuestions, "verificationQuestion");
    }

    private static List<String> checked(List<String> values, String field) {
        if (values == null) throw new IllegalArgumentException(field + " list required");
        return values.stream().map(value -> CoreValidation.requireText(value, field, 2048)).toList();
    }
}
