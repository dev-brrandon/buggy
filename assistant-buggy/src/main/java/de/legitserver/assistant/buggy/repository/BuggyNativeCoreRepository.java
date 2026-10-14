package de.legitserver.assistant.buggy.repository;

import de.legitserver.assistant.buggy.nativecore.BuggyNativeCoreState;
import java.util.Optional;

/** Persistence port for the immutable G1.4 Native-Core baseline. */
public interface BuggyNativeCoreRepository {
    Optional<BuggyNativeCoreState> find();

    /** Initializes the Native-Core birth/baseline exactly once; existing equivalent state is returned unchanged. */
    BuggyNativeCoreState initialize(BuggyNativeCoreState state);
}
