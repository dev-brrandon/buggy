package de.legitserver.assistant.buggy.tools;

import de.legitserver.assistant.runtime.execution.ToolHandler;
import java.util.Objects;

/** Explicit handler plus its trusted integration kind; prevents request-only tools from binding direct executors by accident. */
public record BuggyToolHandlerBinding(ToolHandler handler, BuggyToolHandlerKind kind) {
    public BuggyToolHandlerBinding {
        handler = Objects.requireNonNull(handler, "handler");
        kind = Objects.requireNonNull(kind, "kind");
    }
}
