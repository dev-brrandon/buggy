package de.legitserver.assistant.contracts.source;

import java.util.List;
import java.util.Optional;

/** Read-only source registry. Implementations must address immutable snapshots, never host paths. */
public interface SourceRegistryPort {
    Optional<SourceSnapshot> getSnapshot(SourceComponentRef component);

    List<SourceFileEntry> listFiles(SourceListQuery query);

    Optional<SourceFileContent> readFile(SourceSnapshot snapshot, SourcePath path);

    List<SourceSearchHit> searchFiles(SourceSnapshot snapshot, SourceSearchQuery query);

    Optional<SourceBuildMetadata> getBuildMetadata(SourceSnapshot snapshot);
}
