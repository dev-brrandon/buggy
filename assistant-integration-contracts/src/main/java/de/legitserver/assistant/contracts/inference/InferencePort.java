package de.legitserver.assistant.contracts.inference;

/** Provider-neutral inference boundary. No provider SDK/model identifier leaks into the contract. */
@FunctionalInterface
public interface InferencePort {
    InferenceResult infer(InferenceRequest request);
}
