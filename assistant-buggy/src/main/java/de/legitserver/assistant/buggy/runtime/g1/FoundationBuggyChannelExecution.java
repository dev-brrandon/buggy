package de.legitserver.assistant.buggy.runtime.g1;

import de.legitserver.assistant.buggy.channel.BuggyChannelExecutionPort;
import de.legitserver.assistant.buggy.channel.BuggyChannelReply;
import de.legitserver.assistant.buggy.channel.BuggyChannelType;
import de.legitserver.assistant.buggy.channel.BuggyInboundMessage;
import de.legitserver.assistant.buggy.service.BuggyIdentityService;
import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.actor.ActorType;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.core.request.RequestContext;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.pipeline.RuntimeRequestPipeline;
import de.legitserver.assistant.pipeline.model.PipelineOperation;
import de.legitserver.assistant.pipeline.model.PipelineRequest;
import de.legitserver.assistant.pipeline.model.PipelineResponse;
import de.legitserver.assistant.pipeline.model.PipelineStatus;
import de.legitserver.assistant.security.authn.HmacServiceTokenService;
import de.legitserver.assistant.security.authn.ServiceScopes;
import de.legitserver.assistant.security.authn.ServiceTokenClaims;
import de.legitserver.assistant.security.authorization.ResourceRef;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Canonical G1.2 channel adapter: normalized channel message -> Foundation RuntimeRequestPipeline. */
public final class FoundationBuggyChannelExecution implements BuggyChannelExecutionPort {
    public static final PipelineOperation OPERATION = new PipelineOperation("assistant.buggy.chat.respond");

    private final RuntimeRequestPipeline pipeline;
    private final BuggyIdentityService identity;
    private final HmacServiceTokenService tokens;
    private final Clock clock;

    public FoundationBuggyChannelExecution(
            RuntimeRequestPipeline pipeline,
            BuggyIdentityService identity,
            HmacServiceTokenService tokens,
            Clock clock) {
        if (pipeline == null || identity == null || tokens == null || clock == null) {
            throw new IllegalArgumentException("Buggy channel runtime dependencies required");
        }
        this.pipeline = pipeline;
        this.identity = identity;
        this.tokens = tokens;
        this.clock = clock;
    }

    @Override
    public BuggyChannelReply execute(BuggyInboundMessage message) {
        if (message == null) throw new IllegalArgumentException("message required");
        RequestId requestId = new RequestId(message.messageId());
        TraceId traceId = new TraceId(message.messageId());
        String panelUserId = message.context().channel() == BuggyChannelType.WEB
                ? message.principal().channelUserId() : null;
        String discordId = message.context().channel() == BuggyChannelType.DISCORD
                ? message.principal().channelUserId() : null;
        UUID presentedMinecraftUuid = parseUuid(message.principal().networkIdentityId());
        ActorContext actor = new ActorContext(
                message.principal().actorId(),
                ActorType.HUMAN,
                message.context().channel().origin(),
                message.principal().networkIdentityId(),
                presentedMinecraftUuid,
                panelUserId,
                discordId,
                requestId,
                traceId,
                message.context().serverId(),
                0L,
                message.receivedAt());
        var assistant = identity.assistantDescriptor();
        RequestContext context = new RequestContext(
                requestId,
                traceId,
                actor.actorId(),
                actor.origin(),
                assistant.assistantId(),
                null,
                actor.serverId(),
                message.receivedAt());
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("content", message.content());
        input.put("channel", message.context().channel().name());
        input.put("conversationKey", message.context().conversationKey());
        input.put("channelUserId", message.principal().channelUserId());
        input.put("metadata", message.context().metadata());
        PipelineRequest request = new PipelineRequest(
                context,
                issue(requestId),
                actor,
                assistant,
                OPERATION,
                new ResourceRef("buggy-conversation", message.context().conversationKey(), DataClassification.INTERNAL),
                input,
                DataClassification.INTERNAL,
                DataClassification.INTERNAL,
                null,
                message.messageId(),
                Duration.ofSeconds(30));
        PipelineResponse response = pipeline.execute(request);
        if (!response.successful()) throw failure(response);
        Object raw = response.output().get("response");
        if (!(raw instanceof String content) || content.isBlank()) {
            throw new BuggyChannelExecutionException(
                    BuggyChannelExecutionException.Kind.UNAVAILABLE, "BUGGY_EMPTY_RESPONSE");
        }
        return new BuggyChannelReply(content, response.safeCode());
    }

    private String issue(RequestId requestId) {
        Instant now = clock.instant();
        return tokens.issue(new ServiceTokenClaims(
                "assistant-g1-channel",
                "legit-assistant",
                Set.of(ServiceScopes.INVOKE),
                now.minusSeconds(1),
                now.plusSeconds(45),
                UUID.randomUUID().toString(),
                requestId));
    }

    private static BuggyChannelExecutionException failure(PipelineResponse response) {
        BuggyChannelExecutionException.Kind kind = switch (response.status()) {
            case ACTOR_RESOLUTION_FAILED, AUTHORIZATION_DENIED, POLICY_DENIED ->
                    BuggyChannelExecutionException.Kind.ACCESS_DENIED;
            default -> BuggyChannelExecutionException.Kind.UNAVAILABLE;
        };
        String code = response.safeCode() == null || !response.safeCode().matches("[A-Z0-9_]{1,96}")
                ? "BUGGY_RUNTIME_UNAVAILABLE" : response.safeCode();
        return new BuggyChannelExecutionException(kind, code);
    }

    private static UUID parseUuid(String value) {
        try { return UUID.fromString(value); }
        catch (RuntimeException ignored) { return null; }
    }
}
