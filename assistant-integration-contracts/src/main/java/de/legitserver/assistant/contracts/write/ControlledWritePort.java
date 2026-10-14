package de.legitserver.assistant.contracts.write;

/** Contract only. Phase 12 provides no productive write implementation. */
@FunctionalInterface
public interface ControlledWritePort {
    ControlledWriteResult submit(ControlledWriteRequest request);
}
