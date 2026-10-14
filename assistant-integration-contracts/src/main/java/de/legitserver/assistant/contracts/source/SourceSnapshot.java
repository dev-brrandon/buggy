package de.legitserver.assistant.contracts.source;

import de.legitserver.assistant.contracts.common.ContractValidation;
import java.time.Instant;
import java.util.Objects;

/** Immutable identity for one source/build snapshot. */
public record SourceSnapshot(
        String component,
        String version,
        String build,
        String revision,
        Instant createdAt,
        String checksum) {
    public SourceSnapshot {
        component = ContractValidation.id(component, "component");
        version = ContractValidation.text(version, "version", 160);
        build = ContractValidation.text(build, "build", 160);
        revision = ContractValidation.text(revision, "revision", 200);
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        checksum = ContractValidation.sha256(checksum);
    }
}
