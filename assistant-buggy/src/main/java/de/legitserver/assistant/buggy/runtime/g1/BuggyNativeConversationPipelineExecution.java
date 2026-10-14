package de.legitserver.assistant.buggy.runtime.g1;

import de.legitserver.assistant.buggy.channel.BuggyChannelType;
import de.legitserver.assistant.buggy.conversation.BuggyConversationContext;
import de.legitserver.assistant.buggy.conversation.BuggyConversationContextBuilder;
import de.legitserver.assistant.buggy.conversation.BuggyConversationTurn;
import de.legitserver.assistant.buggy.conversation.BuggyNaturalConversationEngine;
import de.legitserver.assistant.pipeline.model.PipelineExecutionContext;
import de.legitserver.assistant.pipeline.model.PipelineExecutionOutput;
import de.legitserver.assistant.pipeline.port.PipelineExecutionPort;
import de.legitserver.assistant.runtime.execution.ToolFailureCode;
import de.legitserver.assistant.runtime.execution.ToolRuntimeException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** G1.6 native, model-free conversation execution after the canonical Foundation request gates. */
public final class BuggyNativeConversationPipelineExecution implements PipelineExecutionPort {
    private final BuggyConversationContextBuilder contexts;
    private final BuggyNaturalConversationEngine engine;

    public BuggyNativeConversationPipelineExecution(
            BuggyConversationContextBuilder contexts,
            BuggyNaturalConversationEngine engine) {
        this.contexts = Objects.requireNonNull(contexts, "contexts");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @Override
    public PipelineExecutionOutput execute(PipelineExecutionContext context) {
        return executeWithContext(context, List.of());
    }

    public PipelineExecutionOutput executeWithContext(
            PipelineExecutionContext context,
            List<BuggyConversationTurn> recentTurns) {
        Objects.requireNonNull(context, "context");
        Object raw = context.request().input().get("content");
        if (!(raw instanceof String content) || content.isBlank()) {
            throw new ToolRuntimeException(ToolFailureCode.INPUT_SCHEMA_INVALID);
        }
        BuggyConversationContext conversation = contexts.build(
                context.actor().actorId(),
                channel(context.request().input().get("channel")),
                conversationKey(context),
                context.actor().serverId(),
                content,
                recentTurns,
                metadata(context.request().input().get("metadata")));
        var result = engine.respond(conversation);
        LinkedHashMap<String, Object> output = new LinkedHashMap<>();
        output.put("response", result.response());
        output.put("intent", result.intent().intent().name());
        output.put("intentConfidenceBps", result.intent().confidenceBps());
        output.put("entities", result.entities().entities().stream().map(entity -> entity.type().name()).toList());
        output.put("nativeConversation", true);
        return new PipelineExecutionOutput(output, context.request().inputClassification());
    }

    private static BuggyChannelType channel(Object value) {
        if (value == null) return BuggyChannelType.WEB;
        try {
            return BuggyChannelType.valueOf(String.valueOf(value).strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new ToolRuntimeException(ToolFailureCode.INPUT_SCHEMA_INVALID);
        }
    }

    private static String conversationKey(PipelineExecutionContext context) {
        Object raw = context.request().input().get("conversationKey");
        if (raw instanceof String value && !value.isBlank()) return value;
        if (context.conversation() != null) return context.conversation().conversationId().toString();
        return context.request().context().requestId().value();
    }

    private static Map<String, String> metadata(Object raw) {
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) return Map.of();
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (result.size() >= BuggyConversationContext.MAX_METADATA_ENTRIES) break;
            if (entry.getKey() == null || entry.getValue() == null) continue;
            result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
        return Map.copyOf(result);
    }
}
