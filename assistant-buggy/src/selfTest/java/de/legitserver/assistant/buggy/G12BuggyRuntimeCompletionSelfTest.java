package de.legitserver.assistant.buggy;

import de.legitserver.assistant.buggy.runtime.g1.BuggyInferencePipelineExecution;
import de.legitserver.assistant.contracts.inference.InferencePort;
import de.legitserver.assistant.contracts.inference.InferenceRequest;
import de.legitserver.assistant.contracts.inference.InferenceResult;
import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.actor.ActorType;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.core.assistant.AssistantDescriptor;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.core.request.RequestContext;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.pipeline.model.PipelineExecutionContext;
import de.legitserver.assistant.pipeline.model.PipelineOperation;
import de.legitserver.assistant.pipeline.model.PipelineRequest;
import de.legitserver.assistant.pipeline.model.PipelineRoute;
import de.legitserver.assistant.pipeline.model.PipelineRouteType;
import de.legitserver.assistant.runtime.execution.ToolFailureCode;
import de.legitserver.assistant.runtime.execution.ToolRuntimeException;
import de.legitserver.assistant.security.authn.BoundedReplayProtector;
import de.legitserver.assistant.security.authn.HmacServiceTokenService;
import de.legitserver.assistant.security.authn.ServiceAuthenticationStatus;
import de.legitserver.assistant.security.authn.ServiceScopes;
import de.legitserver.assistant.security.authn.ServiceTokenClaims;
import de.legitserver.assistant.security.authorization.ResourceRef;
import de.legitserver.assistant.security.authorization.buggy.BuggySecurityCapabilities;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** G1.2 acceptance for the read-only Buggy natural-language execution port. */
public final class G12BuggyRuntimeCompletionSelfTest {
    private static final Instant NOW = Instant.parse("2026-10-05T20:30:00Z");
    private static final RequestId REQUEST_ID = new RequestId("g12-runtime-request");
    private static final TraceId TRACE_ID = new TraceId("g12-runtime-trace");
    private static final ActorId ACTOR_ID = new ActorId("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private G12BuggyRuntimeCompletionSelfTest() {}

    public static void main(String[] args) {
        verifyCompletedInferenceUsesBoundedReadOnlyPrompt();
        verifyConversationContextIsBoundedAndOrdered();
        verifyUnavailableInferenceFailsClosed();
        verifyRejectedInferenceFailsClosedAsAuthorization();
        verifyBlankContentIsRejectedBeforeInference();
        System.out.println("G1_2_BUGGY_RUNTIME_COMPLETION_SELF_TEST: PASS");
    }

    private static void verifyCompletedInferenceUsesBoundedReadOnlyPrompt() {
        AtomicReference<InferenceRequest> captured = new AtomicReference<>();
        InferencePort inference = request -> {
            captured.set(request);
            return new InferenceResult(
                    InferenceResult.Status.COMPLETED,
                    "Runtime antwortet.",
                    DataClassification.INTERNAL,
                    "INFERENCE_OK");
        };
        var output = new BuggyInferencePipelineExecution(inference).execute(context("Wie ist der Status?"));
        require("Runtime antwortet.".equals(output.values().get("response")), "completed inference response is returned");
        require(output.classification() == DataClassification.INTERNAL, "inference classification is preserved");
        InferenceRequest request = captured.get();
        require(request != null, "inference request is emitted");
        require(request.requestId().equals(REQUEST_ID.value()), "request correlation is preserved");
        require(request.inputs().size() == 2, "system and user inputs are the only initial G1.2 inputs");
        require(request.inputs().get(0).role() == InferenceRequest.Role.SYSTEM, "first input is the Buggy system boundary");
        require(request.inputs().get(0).text().contains("ausschließlich read-only"), "system prompt explicitly forbids action claims");
        require(request.inputs().get(1).role() == InferenceRequest.Role.USER, "second input is the user message");
        require("Wie ist der Status?".equals(request.inputs().get(1).text()), "user content is forwarded unchanged");
        require(request.maxOutputCharacters() == 16_384, "output remains bounded");
    }

    private static void verifyConversationContextIsBoundedAndOrdered() {
        AtomicReference<InferenceRequest> captured = new AtomicReference<>();
        InferencePort inference = request -> {
            captured.set(request);
            return new InferenceResult(
                    InferenceResult.Status.COMPLETED,
                    "Mit Kontext.",
                    DataClassification.INTERNAL,
                    "INFERENCE_OK");
        };
        new BuggyInferencePipelineExecution(inference).executeWithContext(
                context("Und jetzt?"),
                java.util.List.of("HUMAN: Hallo", "ASSISTANT: Moin"));
        InferenceRequest request = captured.get();
        require(request.inputs().size() == 4, "conversation history is placed between system and user inputs");
        require(request.inputs().get(1).role() == InferenceRequest.Role.CONTEXT, "first history item uses CONTEXT role");
        require("HUMAN: Hallo".equals(request.inputs().get(1).text()), "human history is preserved");
        require(request.inputs().get(2).role() == InferenceRequest.Role.CONTEXT, "second history item uses CONTEXT role");
        require(request.inputs().get(3).role() == InferenceRequest.Role.USER, "current user message remains last");
    }

    private static void verifyUnavailableInferenceFailsClosed() {
        InferencePort inference = request -> InferenceResult.unavailable();
        requireFailure(
                new BuggyInferencePipelineExecution(inference),
                context("Hallo"),
                ToolFailureCode.EXECUTION_FAILED,
                "unavailable inference fails closed");
    }

    private static void verifyRejectedInferenceFailsClosedAsAuthorization() {
        InferencePort inference = request -> new InferenceResult(
                InferenceResult.Status.REJECTED,
                null,
                DataClassification.INTERNAL,
                "INFERENCE_REJECTED");
        requireFailure(
                new BuggyInferencePipelineExecution(inference),
                context("Hallo"),
                ToolFailureCode.AUTHORIZATION_DENIED,
                "provider rejection maps to bounded authorization denial");
    }

    private static void verifyBlankContentIsRejectedBeforeInference() {
        InferencePort inference = request -> {
            throw new IllegalStateException("must not execute");
        };
        requireFailure(
                new BuggyInferencePipelineExecution(inference),
                context("   "),
                ToolFailureCode.INPUT_SCHEMA_INVALID,
                "blank content fails before inference");
    }

    private static void requireFailure(
            BuggyInferencePipelineExecution execution,
            PipelineExecutionContext context,
            ToolFailureCode expected,
            String message) {
        try {
            execution.execute(context);
            throw new IllegalStateException("G1.2 self-test failed: " + message + " (no failure)");
        } catch (ToolRuntimeException expectedFailure) {
            require(expectedFailure.code() == expected, message);
        }
    }

    private static PipelineExecutionContext context(String content) {
        ActorContext actor = new ActorContext(
                ACTOR_ID,
                ActorType.HUMAN,
                Origin.PANEL,
                ACTOR_UUID.toString(),
                ACTOR_UUID,
                "panel-g12",
                null,
                REQUEST_ID,
                TRACE_ID,
                "network-01",
                12,
                NOW);
        AssistantDescriptor assistant = new AssistantDescriptor(
                AssistantId.BUGGY,
                "Buggy",
                true,
                "buggy-v1",
                "buggy-policy-v1",
                "buggy-knowledge-v1",
                "buggy-runtime-g12",
                "buggy-personality-v1",
                12,
                NOW);
        RequestContext requestContext = new RequestContext(
                REQUEST_ID,
                TRACE_ID,
                ACTOR_ID,
                Origin.PANEL,
                AssistantId.BUGGY,
                null,
                "network-01",
                NOW);
        PipelineOperation operation = new PipelineOperation("assistant.buggy.chat.respond");
        PipelineRequest request = new PipelineRequest(
                requestContext,
                "test-credential",
                actor,
                assistant,
                operation,
                new ResourceRef("buggy-conversation", "g12-test", DataClassification.INTERNAL),
                Map.of("content", content),
                DataClassification.INTERNAL,
                DataClassification.INTERNAL,
                null,
                "g12-runtime-selftest",
                Duration.ofSeconds(30));
        PipelineRoute route = new PipelineRoute(
                "route.g12.buggy.chat",
                PipelineRouteType.DOMAIN,
                BuggySecurityCapabilities.KNOWLEDGE_READ,
                Set.of(),
                Duration.ofSeconds(30));
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 0x42);
        HmacServiceTokenService tokens = new HmacServiceTokenService(key, new BoundedReplayProtector(16));
        String token = tokens.issue(new ServiceTokenClaims(
                "g12-selftest",
                "legit-assistant",
                Set.of(ServiceScopes.INVOKE),
                NOW.minusSeconds(1),
                NOW.plusSeconds(30),
                "g12-runtime-principal",
                REQUEST_ID));
        var authentication = tokens.verify(
                token,
                "legit-assistant",
                REQUEST_ID,
                Set.of(ServiceScopes.INVOKE),
                NOW);
        require(authentication.status() == ServiceAuthenticationStatus.AUTHENTICATED, "self-test service principal authenticates");
        return new PipelineExecutionContext(
                request,
                authentication.principal(),
                actor,
                null,
                BuggySecurityCapabilities.KNOWLEDGE_READ,
                route);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("G1.2 self-test failed: " + message);
    }
}
