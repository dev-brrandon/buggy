package de.legitserver.assistant.buggy.service;

import de.legitserver.assistant.buggy.nativecore.BuggyNativeCoreState;
import de.legitserver.assistant.buggy.repository.BuggyNativeCoreRepository;
import java.time.Instant;
import java.util.Objects;

/** G1.4 Native-Core lifecycle service. It owns no authority and exposes no privileged action. */
public final class BuggyNativeCoreService {
    private final BuggyNativeCoreRepository repository;

    public BuggyNativeCoreService(BuggyNativeCoreRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public BuggyNativeCoreState loadRequired() {
        return repository.find().orElseThrow(BuggyNativeCoreUnavailableException::new);
    }

    public BuggyNativeCoreState initializeFirstActivation(Instant bornAt) {
        return repository.initialize(BuggyNativeCoreState.firstActivation(Objects.requireNonNull(bornAt, "bornAt")));
    }
}
