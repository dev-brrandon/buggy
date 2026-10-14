package de.legitserver.assistant.runtime.registry;

import de.legitserver.assistant.runtime.execution.ToolHandler;
import de.legitserver.assistant.runtime.model.ToolDescriptor;
import java.util.Objects;
import java.util.concurrent.Semaphore;

/** Internal immutable registration plus bounded concurrency permit pool. */
public final class RegisteredTool {
    private final ToolDescriptor descriptor;
    private final ToolHandler handler;
    private final Semaphore permits;

    RegisteredTool(ToolDescriptor descriptor, ToolHandler handler) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.handler = Objects.requireNonNull(handler, "handler");
        permits = new Semaphore(descriptor.maxConcurrency(), true);
    }

    public ToolDescriptor descriptor() { return descriptor; }
    public ToolHandler handler() { return handler; }
    public boolean tryAcquire() { return permits.tryAcquire(); }
    public void release() { permits.release(); }
    public int activeCount() { return descriptor.maxConcurrency() - permits.availablePermits(); }
}
