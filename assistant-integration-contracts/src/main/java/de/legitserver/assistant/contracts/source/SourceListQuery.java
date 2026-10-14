package de.legitserver.assistant.contracts.source;

import java.util.Objects;

public record SourceListQuery(SourceSnapshot snapshot, SourcePath directory, int limit) {
    public SourceListQuery {
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
        directory = directory == null ? SourcePath.root() : directory;
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit must be 1..1000");
    }
}
