package de.legitserver.assistant.contracts.diagnostics;

/** Fixed read-only diagnostics. Deliberately has no generic command, shell, exec, or process method. */
public interface DiagnosticReadPort {
    DiagnosticResult<ContainerStatus> getContainerStatus(DiagnosticTarget target);

    DiagnosticResult<DiagnosticTextTail> getRecentLogs(DiagnosticTailRequest request);

    DiagnosticResult<ServiceStatus> getServiceStatus(DiagnosticTarget target);

    DiagnosticResult<ResourceUsage> getResourceUsage(DiagnosticTarget target);

    DiagnosticResult<DiagnosticTextTail> getMinecraftConsoleTail(DiagnosticTailRequest request);
}
