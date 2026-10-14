package de.legitserver.assistant.contracts.noop;

import de.legitserver.assistant.contracts.diagnostics.ContainerStatus;
import de.legitserver.assistant.contracts.diagnostics.DiagnosticReadPort;
import de.legitserver.assistant.contracts.diagnostics.DiagnosticResult;
import de.legitserver.assistant.contracts.diagnostics.DiagnosticTailRequest;
import de.legitserver.assistant.contracts.diagnostics.DiagnosticTarget;
import de.legitserver.assistant.contracts.diagnostics.DiagnosticTextTail;
import de.legitserver.assistant.contracts.diagnostics.ResourceUsage;
import de.legitserver.assistant.contracts.diagnostics.ServiceStatus;
import java.util.Objects;

/** Safe default: returns unavailable and executes nothing. */
public final class NoOpDiagnosticReadPort implements DiagnosticReadPort {
    @Override public DiagnosticResult<ContainerStatus> getContainerStatus(DiagnosticTarget target) {
        Objects.requireNonNull(target, "target");
        return DiagnosticResult.unavailable();
    }
    @Override public DiagnosticResult<DiagnosticTextTail> getRecentLogs(DiagnosticTailRequest request) {
        Objects.requireNonNull(request, "request");
        return DiagnosticResult.unavailable();
    }
    @Override public DiagnosticResult<ServiceStatus> getServiceStatus(DiagnosticTarget target) {
        Objects.requireNonNull(target, "target");
        return DiagnosticResult.unavailable();
    }
    @Override public DiagnosticResult<ResourceUsage> getResourceUsage(DiagnosticTarget target) {
        Objects.requireNonNull(target, "target");
        return DiagnosticResult.unavailable();
    }
    @Override public DiagnosticResult<DiagnosticTextTail> getMinecraftConsoleTail(DiagnosticTailRequest request) {
        Objects.requireNonNull(request, "request");
        return DiagnosticResult.unavailable();
    }
}
