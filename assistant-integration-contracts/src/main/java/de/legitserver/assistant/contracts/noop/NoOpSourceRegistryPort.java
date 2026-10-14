package de.legitserver.assistant.contracts.noop;

import de.legitserver.assistant.contracts.source.SourceBuildMetadata;
import de.legitserver.assistant.contracts.source.SourceComponentRef;
import de.legitserver.assistant.contracts.source.SourceFileContent;
import de.legitserver.assistant.contracts.source.SourceFileEntry;
import de.legitserver.assistant.contracts.source.SourceListQuery;
import de.legitserver.assistant.contracts.source.SourcePath;
import de.legitserver.assistant.contracts.source.SourceRegistryPort;
import de.legitserver.assistant.contracts.source.SourceSearchHit;
import de.legitserver.assistant.contracts.source.SourceSearchQuery;
import de.legitserver.assistant.contracts.source.SourceSnapshot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Safe default: exposes no source data. */
public final class NoOpSourceRegistryPort implements SourceRegistryPort {
    @Override public Optional<SourceSnapshot> getSnapshot(SourceComponentRef component) {
        Objects.requireNonNull(component, "component");
        return Optional.empty();
    }
    @Override public List<SourceFileEntry> listFiles(SourceListQuery query) {
        Objects.requireNonNull(query, "query");
        return List.of();
    }
    @Override public Optional<SourceFileContent> readFile(SourceSnapshot snapshot, SourcePath path) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(path, "path");
        return Optional.empty();
    }
    @Override public List<SourceSearchHit> searchFiles(SourceSnapshot snapshot, SourceSearchQuery query) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(query, "query");
        return List.of();
    }
    @Override public Optional<SourceBuildMetadata> getBuildMetadata(SourceSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return Optional.empty();
    }
}
