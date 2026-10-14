package de.legitserver.assistant.contracts;

import de.legitserver.assistant.contracts.common.ApprovalBinding;
import de.legitserver.assistant.contracts.common.ContractValidation;
import de.legitserver.assistant.contracts.diagnostics.DiagnosticStatus;
import de.legitserver.assistant.contracts.diagnostics.DiagnosticTarget;
import de.legitserver.assistant.contracts.diagnostics.DiagnosticTailRequest;
import de.legitserver.assistant.contracts.external.ExternalAnalysisApprovalCanonicalizer;
import de.legitserver.assistant.contracts.external.ExternalAnalysisPayload;
import de.legitserver.assistant.contracts.external.ExternalAnalysisRequest;
import de.legitserver.assistant.contracts.external.ExternalPayloadItem;
import de.legitserver.assistant.contracts.external.PayloadManifest;
import de.legitserver.assistant.contracts.inference.InferenceRequest;
import de.legitserver.assistant.contracts.noop.NoOpControlledWritePort;
import de.legitserver.assistant.contracts.noop.NoOpDiagnosticReadPort;
import de.legitserver.assistant.contracts.noop.NoOpExternalAnalysisPort;
import de.legitserver.assistant.contracts.noop.NoOpInferencePort;
import de.legitserver.assistant.contracts.noop.NoOpSourceRegistryPort;
import de.legitserver.assistant.contracts.source.SourceComponentRef;
import de.legitserver.assistant.contracts.source.SourcePath;
import de.legitserver.assistant.contracts.write.ControlledWriteApprovalCanonicalizer;
import de.legitserver.assistant.contracts.write.ControlledWriteCommand;
import de.legitserver.assistant.contracts.write.ControlledWriteRequest;
import de.legitserver.assistant.contracts.write.ControlledWriteResult;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.requests.model.ApprovalRequestId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class Phase12IntegrationContractsSelfTest {
    private Phase12IntegrationContractsSelfTest() {}

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        testSourcePathsAndNoOp();
        testDiagnosticsNoOp();
        testControlledWriteApprovalAndNoOp();
        testProviderNeutralInference();
        testExternalManifestAndSanitization();
        testNoDangerousImplementationTokens(root);
        System.out.println("PHASE12_INTEGRATION_CONTRACTS_SELF_TEST: PASS");
    }

    private static void testSourcePathsAndNoOp() {
        require(SourcePath.root().value().isEmpty(), "root path");
        require(new SourcePath("src/main/App.java").value().equals("src/main/App.java"), "logical source path");
        expectIllegal(() -> new SourcePath("../secret"));
        expectIllegal(() -> new SourcePath("/etc/passwd"));
        NoOpSourceRegistryPort port = new NoOpSourceRegistryPort();
        require(port.getSnapshot(new SourceComponentRef("networkcore")).isEmpty(), "no-op source must expose no snapshot");
    }

    private static void testDiagnosticsNoOp() {
        NoOpDiagnosticReadPort port = new NoOpDiagnosticReadPort();
        DiagnosticTarget target = new DiagnosticTarget("service", "survival-01");
        require(port.getContainerStatus(target).status() == DiagnosticStatus.UNAVAILABLE, "container unavailable");
        require(port.getRecentLogs(new DiagnosticTailRequest(target, 50)).status() == DiagnosticStatus.UNAVAILABLE, "logs unavailable");
        require(port.getServiceStatus(target).status() == DiagnosticStatus.UNAVAILABLE, "service unavailable");
        require(port.getResourceUsage(target).status() == DiagnosticStatus.UNAVAILABLE, "resources unavailable");
        require(port.getMinecraftConsoleTail(new DiagnosticTailRequest(target, 50)).status() == DiagnosticStatus.UNAVAILABLE, "console unavailable");
    }

    private static void testControlledWriteApprovalAndNoOp() {
        ApprovalBinding approval = approvalBinding("write-payload");
        ControlledWriteCommand command = new ControlledWriteCommand.ReplaceText(
                "config", "survival-01/server.properties", "text/plain", "motd=Legit", null, DataClassification.INTERNAL);
        ControlledWriteRequest request = new ControlledWriteRequest("write-001", command, controlledWriteApproval("write-001", command));
        ControlledWriteResult result = new NoOpControlledWritePort().submit(request);
        require(result.status() == ControlledWriteResult.Status.NOT_CONFIGURED, "no-op controlled write must never apply");
        expectNull(() -> new ControlledWriteRequest("write-002", command, null));
        expectIllegal(() -> new ControlledWriteRequest("write-003", command, approval));
    }

    private static void testProviderNeutralInference() {
        InferenceRequest request = new InferenceRequest(
                "infer-001",
                List.of(new InferenceRequest.Input(InferenceRequest.Role.USER, "summarize this")),
                DataClassification.INTERNAL,
                DataClassification.INTERNAL,
                4000);
        require(new NoOpInferencePort().infer(request).status().name().equals("UNAVAILABLE"), "inference no-op");
        expectIllegal(() -> new InferenceRequest(
                "infer-002",
                List.of(new InferenceRequest.Input(InferenceRequest.Role.USER, "secret")),
                DataClassification.SECRET,
                DataClassification.INTERNAL,
                4000));
    }

    private static void testExternalManifestAndSanitization() {
        ExternalPayloadItem item = new ExternalPayloadItem(
                "diagnostic", "text/plain", "sanitized diagnostics", DataClassification.INTERNAL, true);
        ExternalAnalysisPayload payload = ExternalAnalysisPayload.of(List.of(item));
        PayloadManifest expected = PayloadManifest.from(List.of(item));
        require(payload.manifest().equals(expected), "deterministic payload manifest");
        ExternalAnalysisRequest request = new ExternalAnalysisRequest("root-cause", payload, externalApproval("root-cause", payload.manifest()));
        require(new NoOpExternalAnalysisPort().analyze(request).status().name().equals("UNAVAILABLE"), "external no-op");
        expectIllegal(() -> new ExternalPayloadItem(
                "unsafe", "text/plain", "raw", DataClassification.CONFIDENTIAL, false));
        PayloadManifest tampered = new PayloadManifest(1, expected.entries(), expected.totalBytes(), ContractValidation.sha256Of("tampered"));
        expectIllegal(() -> new ExternalAnalysisPayload(List.of(item), tampered));
    }

    private static ApprovalBinding approvalBinding(String payload) {
        return new ApprovalBinding(ApprovalRequestId.random(), 3, ContractValidation.sha256Of(payload));
    }

    private static ApprovalBinding controlledWriteApproval(String idempotencyKey, ControlledWriteCommand command) {
        return new ApprovalBinding(
                ApprovalRequestId.random(),
                3,
                ControlledWriteApprovalCanonicalizer.payloadSha256(idempotencyKey, command));
    }

    private static ApprovalBinding externalApproval(String analysisType, PayloadManifest manifest) {
        return new ApprovalBinding(
                ApprovalRequestId.random(),
                2,
                ExternalAnalysisApprovalCanonicalizer.payloadSha256(analysisType, manifest));
    }

    private static void testNoDangerousImplementationTokens(Path root) throws Exception {
        Path main = root.resolve("assistant-integration-contracts/src/main/java");
        List<String> forbidden = List.of(
                "ProcessBuilder",
                "Runtime.getRuntime",
                "java.lang.Process",
                "javax.sql",
                "java.sql.",
                "org.springframework",
                "jakarta.",
                "executeCommand",
                "shellCommand");
        try (var paths = Files.walk(main)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                for (String token : forbidden) {
                    require(!source.contains(token), "forbidden integration token " + token + " in " + path.getFileName());
                }
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expectIllegal(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void expectNull(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected NullPointerException");
        } catch (NullPointerException expected) {
            // expected
        }
    }
}
