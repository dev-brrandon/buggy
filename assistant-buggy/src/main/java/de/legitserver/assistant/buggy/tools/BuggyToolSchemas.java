package de.legitserver.assistant.buggy.tools;

import de.legitserver.assistant.runtime.schema.ToolFieldSchema;
import de.legitserver.assistant.runtime.schema.ToolSchema;
import de.legitserver.assistant.runtime.schema.ToolValueType;

/** Reusable strict schema fragments for the B5 catalog. */
final class BuggyToolSchemas {
    static final ToolSchema EMPTY = ToolSchema.empty();
    static final ToolSchema QUERY = ToolSchema.of(
            string("query", true, 4096), integer("limit", false), string("cursor", false, 512));
    static final ToolSchema FILTER = ToolSchema.of(
            string("filter", false, 2048), integer("limit", false), string("cursor", false, 512));
    static final ToolSchema PATH = ToolSchema.of(string("path", true, 4096));
    static final ToolSchema PATH_QUERY = ToolSchema.of(
            string("path", false, 4096), string("query", true, 4096), integer("limit", false));
    static final ToolSchema REF_PAIR = ToolSchema.of(
            string("left_ref", true, 256), string("right_ref", true, 256));
    static final ToolSchema WORKSPACE_CREATE = ToolSchema.of(
            string("base_ref", true, 256), string("purpose", true, 2048));
    static final ToolSchema WORKSPACE_MODIFY = ToolSchema.of(
            string("workspace_id", true, 256), string("path", true, 4096), string("content", true, 65536));
    static final ToolSchema PATCH_CREATE = ToolSchema.of(
            string("workspace_id", true, 256), string("title", true, 512));
    static final ToolSchema TARGETS = ToolSchema.of(
            strings("targets", false, 64), bool("clean", false));
    static final ToolSchema TEST = ToolSchema.of(
            strings("selectors", false, 64), bool("fail_fast", false));
    static final ToolSchema BENCHMARK = ToolSchema.of(
            strings("selectors", false, 64), integer("iterations", false));
    static final ToolSchema ARTIFACT = ToolSchema.of(string("artifact_id", true, 256));
    static final ToolSchema DEPLOY_PLAN = ToolSchema.of(
            string("artifact_id", true, 256), string("target", true, 256));
    static final ToolSchema REASON = ToolSchema.of(string("reason", true, 4096));
    static final ToolSchema LOG_TAIL = ToolSchema.of(integer("lines", false), string("contains", false, 2048));
    static final ToolSchema CORRELATION = ToolSchema.of(string("correlation_id", true, 256));
    static final ToolSchema READONLY_SQL = ToolSchema.of(
            string("statement", true, 16384), integer("max_rows", false));
    static final ToolSchema HOST_PORT = ToolSchema.of(
            string("host", true, 253), integer("port", true));
    static final ToolSchema HOST = ToolSchema.of(string("host", true, 253));
    static final ToolSchema URL = ToolSchema.of(string("url", true, 4096));
    static final ToolSchema KNOWLEDGE_DRAFT = ToolSchema.of(
            string("title", true, 512), string("content", true, 65536), strings("source_refs", false, 64));
    static final ToolSchema REVISION = ToolSchema.of(
            string("document_id", true, 256), string("content", true, 65536), string("reason", true, 4096));
    static final ToolSchema MARK = ToolSchema.of(
            string("document_id", true, 256), string("reason", true, 4096), string("evidence_ref", true, 512));
    static final ToolSchema RELATE = ToolSchema.of(
            string("left_id", true, 256), string("right_id", true, 256), string("relation", true, 128));
    static final ToolSchema BUG_CREATE = ToolSchema.of(
            string("title", true, 512), string("summary", true, 8192), string("evidence_ref", false, 512));
    static final ToolSchema BUG_UPDATE = ToolSchema.of(
            string("bug_id", true, 256), string("note", true, 8192));
    static final ToolSchema REQUEST_CREATE = ToolSchema.of(
            string("request_type", true, 128), string("summary", true, 4096), string("payload_ref", false, 512));
    static final ToolSchema REQUEST_UPDATE = ToolSchema.of(
            string("request_id", true, 256), string("payload_ref", true, 512));
    static final ToolSchema REQUEST_ID = ToolSchema.of(string("request_id", true, 256));
    static final ToolSchema MESSAGE = ToolSchema.of(
            string("channel_id", true, 256), string("message", true, 8000));
    static final ToolSchema MESSAGE_EDIT = ToolSchema.of(
            string("channel_id", true, 256), string("message_id", true, 256), string("message", true, 8000));
    static final ToolSchema MESSAGE_ID = ToolSchema.of(
            string("channel_id", true, 256), string("message_id", true, 256));
    static final ToolSchema WHITELIST = ToolSchema.of(string("player_id", true, 256));
    static final ToolSchema MAIL_DRAFT = ToolSchema.of(
            string("recipient", true, 512), string("subject", true, 998), string("body", true, 65536));
    static final ToolSchema MAIL_SEND_REQUEST = ToolSchema.of(
            string("draft_ref", true, 512), string("reason", true, 4096));
    static final ToolSchema SELFDEV_OBSERVE = ToolSchema.of(
            string("component", true, 256), string("title", true, 256), string("problem", true, 8192));
    static final ToolSchema SELFDEV_PROPOSAL = ToolSchema.of(
            string("item_id", true, 64), string("proposal", true, 8192), string("change_type", true, 64),
            string("risk", true, 32));
    static final ToolSchema SELFDEV_ITEM = ToolSchema.of(string("item_id", true, 64));
    static final ToolSchema SELFDEV_WORKSPACE = ToolSchema.of(
            string("item_id", true, 64), string("source_snapshot_ref", true, 512), string("purpose", true, 2048));
    static final ToolSchema SELFDEV_MODIFY = ToolSchema.of(
            string("item_id", true, 64), string("workspace_id", true, 256), string("path", true, 4096),
            string("content", true, 65536));
    static final ToolSchema SELFDEV_RUN = ToolSchema.of(
            string("item_id", true, 64), string("workspace_id", true, 256), strings("selectors", false, 64));
    static final ToolSchema SELFDEV_BENCHMARK = ToolSchema.of(
            string("item_id", true, 64), string("workspace_id", true, 256), strings("selectors", false, 64),
            integer("iterations", false));
    static final ToolSchema SELFDEV_COMPARE = ToolSchema.of(
            string("item_id", true, 64), string("baseline_ref", true, 512), string("candidate_ref", true, 512));
    static final ToolSchema SELFDEV_REJECT = ToolSchema.of(
            string("item_id", true, 64), string("reason", true, 4096), string("self_review_ref", true, 512));
    static final ToolSchema SELFDEV_SUBMIT = ToolSchema.of(
            string("item_id", true, 64), string("promotion_ref", true, 512));

    static final ToolSchema READ_OUTPUT = ToolSchema.of(
            string("summary", true, 16384), strings("items", false, 256), string("next_cursor", false, 512));
    static final ToolSchema STATUS_OUTPUT = ToolSchema.of(
            string("status", true, 128), string("summary", false, 16384));
    static final ToolSchema ACTION_OUTPUT = ToolSchema.of(
            string("status", true, 128), string("reference_id", false, 512), string("summary", false, 16384));
    static final ToolSchema ARTIFACT_OUTPUT = ToolSchema.of(
            string("artifact_id", true, 512), string("summary", true, 16384));
    static final ToolSchema REQUEST_OUTPUT = ToolSchema.of(
            string("request_id", true, 256), string("status", true, 128), string("summary", false, 16384));

    private BuggyToolSchemas() {}

    private static ToolFieldSchema string(String name, boolean required, int maxSize) {
        return new ToolFieldSchema(name, ToolValueType.STRING, required, maxSize);
    }

    private static ToolFieldSchema strings(String name, boolean required, int maxSize) {
        return new ToolFieldSchema(name, ToolValueType.STRING_LIST, required, maxSize);
    }

    private static ToolFieldSchema integer(String name, boolean required) {
        return new ToolFieldSchema(name, ToolValueType.INTEGER, required, 0);
    }

    private static ToolFieldSchema bool(String name, boolean required) {
        return new ToolFieldSchema(name, ToolValueType.BOOLEAN, required, 0);
    }
}
