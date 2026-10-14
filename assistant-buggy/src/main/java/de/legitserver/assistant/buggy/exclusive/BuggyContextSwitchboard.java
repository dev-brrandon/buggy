package de.legitserver.assistant.buggy.exclusive;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Request/session-local context switchboard. It stores references only; source systems remain authoritative.
 */
public final class BuggyContextSwitchboard {
    private static final int MAX_HISTORY = 32;
    private final Deque<BuggyContextRef> history = new ArrayDeque<>();
    private BuggyContextRef current;

    public synchronized BuggyContextRef switchTo(BuggyContextRef context) {
        Objects.requireNonNull(context, "context");
        if (current != null && !current.equals(context)) {
            history.addFirst(current);
            while (history.size() > MAX_HISTORY) history.removeLast();
        }
        current = context;
        return current;
    }

    public synchronized BuggyContextRef current() {
        if (current == null) throw new IllegalStateException("No Buggy context selected");
        return current;
    }

    public synchronized List<BuggyContextRef> history() {
        return List.copyOf(new ArrayList<>(history));
    }
}
