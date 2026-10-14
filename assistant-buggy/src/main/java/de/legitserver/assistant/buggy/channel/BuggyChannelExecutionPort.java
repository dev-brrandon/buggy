package de.legitserver.assistant.buggy.channel;

/**
 * Runtime-owned execution boundary for normalized G1 channel messages.
 *
 * <p>Production implementations MUST enter the canonical Foundation runtime pipeline before any
 * model inference, tool execution, approval-sensitive action, or privileged data access. Channel
 * adapters MUST NOT bind this port directly to a model provider or tool handler.</p>
 */
@FunctionalInterface
public interface BuggyChannelExecutionPort {
    BuggyChannelReply execute(BuggyInboundMessage message);
}
