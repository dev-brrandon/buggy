package de.legitserver.assistant.contracts.external;

@FunctionalInterface
public interface ExternalAnalysisPort {
    ExternalAnalysisResult analyze(ExternalAnalysisRequest request);
}
