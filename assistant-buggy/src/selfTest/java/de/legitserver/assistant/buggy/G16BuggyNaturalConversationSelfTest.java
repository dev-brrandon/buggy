package de.legitserver.assistant.buggy;

import de.legitserver.assistant.buggy.channel.BuggyChannelType;
import de.legitserver.assistant.buggy.conversation.BuggyConversationContext;
import de.legitserver.assistant.buggy.conversation.BuggyConversationContextBuilder;
import de.legitserver.assistant.buggy.conversation.BuggyConversationEntityType;
import de.legitserver.assistant.buggy.conversation.BuggyConversationIntent;
import de.legitserver.assistant.buggy.conversation.BuggyConversationLanguage;
import de.legitserver.assistant.buggy.conversation.BuggyConversationRole;
import de.legitserver.assistant.buggy.conversation.BuggyConversationTurn;
import de.legitserver.assistant.buggy.conversation.BuggyNaturalConversationEngine;
import de.legitserver.assistant.buggy.conversation.BuggyResponseAct;
import de.legitserver.assistant.buggy.runtime.g1.BuggyNativeConversationPipelineExecution;
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
import de.legitserver.assistant.security.authn.BoundedReplayProtector;
import de.legitserver.assistant.security.authn.HmacServiceTokenService;
import de.legitserver.assistant.security.authn.ServiceAuthenticationStatus;
import de.legitserver.assistant.security.authn.ServiceScopes;
import de.legitserver.assistant.security.authn.ServiceTokenClaims;
import de.legitserver.assistant.security.authorization.ResourceRef;
import de.legitserver.assistant.security.authorization.buggy.BuggySecurityCapabilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** G1.6 acceptance for native context, intent, entity resolution, planning, voice and model-free runtime wiring. */
public final class G16BuggyNaturalConversationSelfTest {
    private static final Instant NOW = Instant.parse("2026-10-06T16:45:00Z");
    private static final RequestId REQUEST_ID = new RequestId("g16-runtime-request");
    private static final TraceId TRACE_ID = new TraceId("g16-runtime-trace");
    private static final ActorId ACTOR_ID = new ActorId("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR_UUID = UUID.fromString(ACTOR_ID.value());
    private static final BuggyConversationContextBuilder CONTEXTS = new BuggyConversationContextBuilder();
    private static final BuggyNaturalConversationEngine ENGINE = BuggyNaturalConversationEngine.canonical();

    private G16BuggyNaturalConversationSelfTest() {}

    public static void main(String[] args) throws Exception {
        verifyContextIsBoundedAndLanguageAware();
        verifyCoreConversationalIntents();
        verifyContextualEntityResolution();
        verifyG17BoundariesRemainIntact();
        verifyNativePipelineExecution();
        if (args.length > 0) verifyProductionWiringIsModelFree(Path.of(args[0]));
        System.out.println("G1_6_BUGGY_NATURAL_CONVERSATION_SELF_TEST: PASS");
    }

    private static void verifyContextIsBoundedAndLanguageAware() {
        ArrayList<BuggyConversationTurn> turns = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            turns.add(new BuggyConversationTurn(BuggyConversationRole.HUMAN, "Kontext " + i));
        }
        BuggyConversationContext context = CONTEXTS.build(
                ACTOR_ID, BuggyChannelType.WEB, "g16-context", "network-01", "Wie geht es dir?", turns, Map.of());
        require(context.recentTurns().size() == 24, "context keeps only the newest 24 turns");
        require("Kontext 6".equals(context.recentTurns().getFirst().content()), "oldest overflow context is discarded");
        require(context.language() == BuggyConversationLanguage.GERMAN, "German is detected deterministically");
        BuggyConversationContext english = context("What can you do?", List.of());
        require(english.language() == BuggyConversationLanguage.ENGLISH, "English is detected deterministically");
    }

    private static void verifyCoreConversationalIntents() {
        require(ENGINE.respond(context("Moin", List.of())).intent().intent() == BuggyConversationIntent.GREETING,
                "greeting intent");
        var identity = ENGINE.respond(context("Wer bist du?", List.of()));
        require(identity.intent().intent() == BuggyConversationIntent.IDENTITY_QUERY, "identity intent");
        require(identity.entities().contains(BuggyConversationEntityType.BUGGY_SELF), "self entity is resolved");
        require(identity.response().contains("Buggy"), "identity response uses protected display name");
        var capability = ENGINE.respond(context("Was kannst du jetzt?", List.of()));
        require(capability.intent().intent() == BuggyConversationIntent.CAPABILITY_QUERY, "capability intent");
        require(capability.response().contains("G1.7") && capability.response().contains("G1.10"),
                "capability answer states deferred native/tool and model boundaries");
        var continuation = ENGINE.respond(context("Okay dann weiter", List.of()));
        require(continuation.intent().intent() == BuggyConversationIntent.CONTINUATION, "continuation beats action matching");
        require(continuation.plan().acts().getFirst() == BuggyResponseAct.ACKNOWLEDGE_CONTINUATION,
                "continuation gets a non-action response plan");
    }

    private static void verifyContextualEntityResolution() {
        List<BuggyConversationTurn> history = List.of(
                new BuggyConversationTurn(BuggyConversationRole.HUMAN, "Wie ist der Survival Server aufgebaut?"),
                new BuggyConversationTurn(BuggyConversationRole.ASSISTANT, "Dazu brauche ich G1.7."));
        var result = ENGINE.respond(context("Und was ist damit?", history));
        require(result.entities().contains(BuggyConversationEntityType.SERVER),
                "ambiguous reference resolves against recent conversation context");
        require(result.plan().requiresExternalKnowledge(), "resolved server query remains a G1.7 knowledge dependency");
    }

    private static void verifyG17BoundariesRemainIntact() {
        var status = ENGINE.respond(context("Wie ist der Status vom Survival Server?", List.of()));
        require(status.intent().intent() == BuggyConversationIntent.INFORMATION_QUERY, "server status is an information query");
        require(status.plan().acts().getFirst() == BuggyResponseAct.EXPLAIN_G17_KNOWLEDGE_LIMIT,
                "live server status is deferred to G1.7");
        require(status.response().contains("erfinde"), "native response explicitly refuses fabricated live state");

        var action = ENGINE.respond(context("Starte den Survival Server neu", List.of()));
        require(action.intent().intent() == BuggyConversationIntent.ACTION_REQUEST, "operational imperative is an action request");
        require(action.plan().requestsAction(), "plan marks action semantics");
        require(action.plan().acts().getFirst() == BuggyResponseAct.EXPLAIN_G17_ACTION_LIMIT,
                "G1.6 never executes operational actions");
    }

    private static void verifyNativePipelineExecution() {
        BuggyNativeConversationPipelineExecution execution = new BuggyNativeConversationPipelineExecution(CONTEXTS, ENGINE);
        var output = execution.executeWithContext(
                pipelineContext("Wer bist du?"),
                List.of(new BuggyConversationTurn(BuggyConversationRole.HUMAN, "Hallo Buggy")));
        require(Boolean.TRUE.equals(output.values().get("nativeConversation")), "pipeline marks native conversation execution");
        require("IDENTITY_QUERY".equals(output.values().get("intent")), "pipeline exposes bounded intent result");
        require(output.classification() == DataClassification.INTERNAL, "input classification is preserved");
        Object response = output.values().get("response");
        require(response instanceof String value && value.contains("NetworkCore"), "native response is composed without provider output");
    }

    private static void verifyProductionWiringIsModelFree(Path root) throws Exception {
        Path config = root.resolve("assistant-bootstrap/src/main/java/de/legitserver/assistant/bootstrap/g12/G12RuntimeConfiguration.java");
        String source = Files.readString(config);
        require(source.contains("BuggyNativeConversationPipelineExecution"), "production route wires G1.6 native execution");
        require(!source.contains("OpenAiCompatibleInferencePort"), "production G1.6 runtime has no external inference provider wiring");
        require(!source.contains("BuggyInferencePipelineExecution"), "production G1.6 route no longer delegates to legacy inference");
    }

    private static BuggyConversationContext context(String content, List<BuggyConversationTurn> turns) {
        return CONTEXTS.build(
                ACTOR_ID,
                BuggyChannelType.WEB,
                "g16-test",
                "survival-01",
                content,
                turns,
                Map.of("surface", "selftest"));
    }

    private static PipelineExecutionContext pipelineContext(String content) {
        ActorContext actor = new ActorContext(
                ACTOR_ID,
                ActorType.HUMAN,
                Origin.PANEL,
                ACTOR_UUID.toString(),
                ACTOR_UUID,
                "panel-g16",
                null,
                REQUEST_ID,
                TRACE_ID,
                "survival-01",
                13,
                NOW);
        AssistantDescriptor assistant = new AssistantDescriptor(
                AssistantId.BUGGY,
                "Buggy",
                true,
                "buggy-v1",
                "buggy-policy-v1",
                "buggy-knowledge-v1",
                "buggy-runtime-g16",
                "buggy-personality-v1",
                13,
                NOW);
        RequestContext requestContext = new RequestContext(
                REQUEST_ID,
                TRACE_ID,
                ACTOR_ID,
                Origin.PANEL,
                AssistantId.BUGGY,
                null,
                "survival-01",
                NOW);
        PipelineRequest request = new PipelineRequest(
                requestContext,
                "test-credential",
                actor,
                assistant,
                new PipelineOperation("assistant.buggy.chat.respond"),
                new ResourceRef("buggy-conversation", "g16-test", DataClassification.INTERNAL),
                Map.of("content", content, "channel", "WEB", "conversationKey", "g16-test"),
                DataClassification.INTERNAL,
                DataClassification.INTERNAL,
                null,
                "g16-runtime-selftest",
                Duration.ofSeconds(30));
        PipelineRoute route = new PipelineRoute(
                "route.g16.buggy.chat",
                PipelineRouteType.DOMAIN,
                BuggySecurityCapabilities.KNOWLEDGE_READ,
                Set.of(),
                Duration.ofSeconds(30));
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 0x43);
        HmacServiceTokenService tokens = new HmacServiceTokenService(key, new BoundedReplayProtector(16));
        String token = tokens.issue(new ServiceTokenClaims(
                "g16-selftest",
                "legit-assistant",
                Set.of(ServiceScopes.INVOKE),
                NOW.minusSeconds(1),
                NOW.plusSeconds(30),
                "g16-runtime-principal",
                REQUEST_ID));
        var authentication = tokens.verify(
                token,
                "legit-assistant",
                REQUEST_ID,
                Set.of(ServiceScopes.INVOKE),
                NOW);
        require(authentication.status() == ServiceAuthenticationStatus.AUTHENTICATED,
                "self-test service principal authenticates");
        return new PipelineExecutionContext(
                request,
                authentication.principal(),
                actor,
                null,
                BuggySecurityCapabilities.KNOWLEDGE_READ,
                route);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("G1.6 self-test failed: " + message);
    }
}
