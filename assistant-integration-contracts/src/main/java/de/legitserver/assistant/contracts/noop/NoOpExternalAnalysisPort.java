package de.legitserver.assistant.contracts.noop;

import de.legitserver.assistant.contracts.external.ExternalAnalysisPort;
import de.legitserver.assistant.contracts.external.ExternalAnalysisRequest;
import de.legitserver.assistant.contracts.external.ExternalAnalysisResult;

/** Safe default: sends no data anywhere. */
public final class NoOpExternalAnalysisPort implements ExternalAnalysisPort {
    @Override public ExternalAnalysisResult analyze(ExternalAnalysisRequest request) { return ExternalAnalysisResult.unavailable(); }
}
