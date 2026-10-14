package de.legitserver.assistant.buggy.channel;

import de.legitserver.assistant.core.validation.CoreValidation;

/** Bounded G1 transport reply. It carries presentation text only, never authority. */
public record BuggyChannelReply(String content, String safeCode) {
    public BuggyChannelReply {
        content = CoreValidation.requireText(content, "content", 16_384);
        safeCode = CoreValidation.requireUpperCode(safeCode == null ? "OK" : safeCode, "safeCode", 96);
    }
}
