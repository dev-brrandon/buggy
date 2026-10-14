package de.legitserver.assistant.buggy.conversation;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.EnumSet;
import java.util.List;

public record BuggyEntityResolution(List<BuggyConversationEntity> entities) {
    public BuggyEntityResolution {
        entities = List.copyOf(CoreValidation.requireNonNull(entities, "entities"));
        if (entities.size() > 16) throw new IllegalArgumentException("entities exceeds max 16");
        EnumSet<BuggyConversationEntityType> seen = EnumSet.noneOf(BuggyConversationEntityType.class);
        for (BuggyConversationEntity entity : entities) {
            if (!seen.add(entity.type())) throw new IllegalArgumentException("duplicate entity type " + entity.type());
        }
    }

    public boolean contains(BuggyConversationEntityType type) {
        return entities.stream().anyMatch(entity -> entity.type() == type);
    }
}
