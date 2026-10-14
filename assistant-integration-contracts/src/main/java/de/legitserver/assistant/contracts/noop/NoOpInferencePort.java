package de.legitserver.assistant.contracts.noop;

import de.legitserver.assistant.contracts.inference.InferencePort;
import de.legitserver.assistant.contracts.inference.InferenceRequest;
import de.legitserver.assistant.contracts.inference.InferenceResult;

/** Safe default: no external or local inference is performed. */
public final class NoOpInferencePort implements InferencePort {
    @Override public InferenceResult infer(InferenceRequest request) { return InferenceResult.unavailable(); }
}
