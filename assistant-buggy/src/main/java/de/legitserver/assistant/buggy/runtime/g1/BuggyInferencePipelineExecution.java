package de.legitserver.assistant.buggy.runtime.g1;

import de.legitserver.assistant.contracts.inference.InferencePort;
import de.legitserver.assistant.contracts.inference.InferenceRequest;
import de.legitserver.assistant.contracts.inference.InferenceResult;
import de.legitserver.assistant.pipeline.model.PipelineExecutionContext;
import de.legitserver.assistant.pipeline.model.PipelineExecutionOutput;
import de.legitserver.assistant.pipeline.port.PipelineExecutionPort;
import de.legitserver.assistant.runtime.execution.ToolFailureCode;
import de.legitserver.assistant.runtime.execution.ToolRuntimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** G1.2 read-only natural-language execution. Tool/action execution remains outside this route. */
public final class BuggyInferencePipelineExecution implements PipelineExecutionPort {
    private static final String SYSTEM = """
            Du bist Buggy, der interne Assistent von Legit-Server.de. Antworte präzise und auf Deutsch, sofern der Nutzer nicht ausdrücklich eine andere Sprache verwendet. Behaupte niemals, eine Aktion ausgeführt, Daten geändert oder einen externen Zustand geprüft zu haben, wenn dafür kein freigegebener Tool-Aufruf im Runtime-Kontext vorliegt. Diese Route ist ausschließlich read-only Natural-Language-Ausführung.
            """.strip();

    private final InferencePort inference;

    public BuggyInferencePipelineExecution(InferencePort inference) {
        if (inference == null) throw new IllegalArgumentException("inference required");
        this.inference = inference;
    }

    @Override
    public PipelineExecutionOutput execute(PipelineExecutionContext context) {
        return executeWithContext(context, List.of());
    }

    /** Adds bounded, already-authorized conversation history without changing the canonical request classification. */
    public PipelineExecutionOutput executeWithContext(PipelineExecutionContext context, List<String> conversationContext) {
        Object raw = context.request().input().get("content");
        if (!(raw instanceof String content) || content.isBlank()) {
            throw new ToolRuntimeException(ToolFailureCode.INPUT_SCHEMA_INVALID);
        }
        ArrayList<InferenceRequest.Input> inputs = new ArrayList<>();
        inputs.add(new InferenceRequest.Input(InferenceRequest.Role.SYSTEM, SYSTEM));
        if (conversationContext != null) {
            int count = 0;
            for (String entry : conversationContext) {
                if (entry == null || entry.isBlank()) continue;
                if (count++ >= 24) break;
                String bounded = entry.length() > 12_000 ? entry.substring(0, 12_000) : entry;
                inputs.add(new InferenceRequest.Input(InferenceRequest.Role.CONTEXT, bounded));
            }
        }
        inputs.add(new InferenceRequest.Input(InferenceRequest.Role.USER, content));
        InferenceResult result = inference.infer(new InferenceRequest(
                context.request().context().requestId().value(),
                List.copyOf(inputs),
                context.request().inputClassification(),
                context.request().responseClassificationCeiling(),
                16_384));
        if (result == null || result.status() == InferenceResult.Status.UNAVAILABLE) {
            throw new ToolRuntimeException(ToolFailureCode.EXECUTION_FAILED);
        }
        if (result.status() == InferenceResult.Status.REJECTED) {
            throw new ToolRuntimeException(ToolFailureCode.AUTHORIZATION_DENIED);
        }
        return new PipelineExecutionOutput(Map.of("response", result.text()), result.classification());
    }
}
