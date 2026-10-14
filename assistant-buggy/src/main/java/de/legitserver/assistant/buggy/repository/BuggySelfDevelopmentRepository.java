package de.legitserver.assistant.buggy.repository;

import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentEvidence;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentId;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentRevision;
import java.util.List;
import java.util.Optional;

/** Persistence port for append-only B7 Self-Development history and evidence. */
public interface BuggySelfDevelopmentRepository {
    Optional<BuggySelfDevelopmentRevision> findCurrent(BuggySelfDevelopmentId itemId);

    List<BuggySelfDevelopmentRevision> history(BuggySelfDevelopmentId itemId);
    default List<BuggySelfDevelopmentRevision> listCurrentItems(int limit) { return List.of(); }

    BuggySelfDevelopmentRevision append(long expectedCurrentRevision, BuggySelfDevelopmentRevision revision);

    void appendEvidence(BuggySelfDevelopmentEvidence evidence);

    List<BuggySelfDevelopmentEvidence> evidence(BuggySelfDevelopmentId itemId);
}
