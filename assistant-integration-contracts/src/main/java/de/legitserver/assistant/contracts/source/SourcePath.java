package de.legitserver.assistant.contracts.source;

import java.util.ArrayList;
import java.util.List;

/** Logical snapshot-relative source path. Absolute/traversal paths are impossible by construction. */
public record SourcePath(String value) {
    public SourcePath {
        if (value == null) throw new IllegalArgumentException("path must not be null");
        String normalized = value.trim().replace('\\', '/');
        while (normalized.contains("//")) normalized = normalized.replace("//", "/");
        if (normalized.startsWith("/") || normalized.indexOf('\0') >= 0 || normalized.length() > 1024) {
            throw new IllegalArgumentException("source path must be logical and relative");
        }
        List<String> segments = new ArrayList<>();
        if (!normalized.isEmpty()) {
            for (String segment : normalized.split("/")) {
                if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                    throw new IllegalArgumentException("source path contains invalid segment");
                }
                segments.add(segment);
            }
        }
        value = String.join("/", segments);
    }

    public static SourcePath root() {
        return new SourcePath("");
    }
}
