package de.legitserver.assistant.contracts.noop;

import de.legitserver.assistant.contracts.write.ControlledWritePort;
import de.legitserver.assistant.contracts.write.ControlledWriteRequest;
import de.legitserver.assistant.contracts.write.ControlledWriteResult;

/** Safe default: never mutates anything. */
public final class NoOpControlledWritePort implements ControlledWritePort {
    @Override public ControlledWriteResult submit(ControlledWriteRequest request) { return ControlledWriteResult.notConfigured(); }
}
