package de.legitserver.assistant.contracts.write;

import de.legitserver.assistant.contracts.common.ContractValidation;

/** Canonical Phase-9 payload for one exact controlled-write intent. */
public final class ControlledWriteApprovalCanonicalizer {
    public static final String CONTENT_TYPE = "application/vnd.legit.controlled-write+text;v=1";

    private ControlledWriteApprovalCanonicalizer() {}

    public static String canonicalPayload(String idempotencyKey, ControlledWriteCommand command) {
        StringBuilder out = new StringBuilder("LEGIT_CONTROLLED_WRITE_V1\n");
        out.append("idempotencyKey=").append(idempotencyKey).append('\n');
        out.append("resourceType=").append(command.resourceType()).append('\n');
        out.append("resourceId=").append(command.resourceId()).append('\n');
        out.append("classification=").append(command.classification().name()).append('\n');
        if (command instanceof ControlledWriteCommand.ReplaceText replace) {
            out.append("kind=REPLACE_TEXT\n");
            out.append("contentType=").append(replace.contentType()).append('\n');
            out.append("expectedChecksum=").append(replace.expectedChecksum() == null ? "" : replace.expectedChecksum()).append('\n');
            out.append("contentSha256=").append(ContractValidation.sha256Of(replace.content())).append('\n');
        } else if (command instanceof ControlledWriteCommand.DeleteResource delete) {
            out.append("kind=DELETE_RESOURCE\n");
            out.append("expectedChecksum=").append(delete.expectedChecksum()).append('\n');
        } else if (command instanceof ControlledWriteCommand.ServiceLifecycle lifecycle) {
            out.append("kind=SERVICE_LIFECYCLE\n");
            out.append("action=").append(lifecycle.action().name()).append('\n');
        } else {
            throw new IllegalArgumentException("unsupported controlled write command");
        }
        return out.toString();
    }

    public static String payloadSha256(String idempotencyKey, ControlledWriteCommand command) {
        return ContractValidation.sha256Of(canonicalPayload(idempotencyKey, command));
    }
}
