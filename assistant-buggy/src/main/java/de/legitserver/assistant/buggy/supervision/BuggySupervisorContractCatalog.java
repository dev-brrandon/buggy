package de.legitserver.assistant.buggy.supervision;

import de.legitserver.assistant.core.assistant.AssistantId;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** Canonical B11 contracts for reserved future assistants, without implementing those assistants. */
public final class BuggySupervisorContractCatalog {
    public static final String VERSION = "buggy-supervisor-contract-v1";
    private static final Set<AssistantId> FUTURE_TARGETS = Set.of(AssistantId.BYTE, AssistantId.KAESE);
    private static final Map<BuggySupervisorOperation, BuggySupervisorAuthorityMode> OPERATIONS = operations();

    private BuggySupervisorContractCatalog() {}

    public static Set<AssistantId> futureTargets() {
        return FUTURE_TARGETS;
    }

    public static BuggyFutureAssistantContract contract(AssistantId targetAssistant) {
        requireFutureTarget(targetAssistant);
        return new BuggyFutureAssistantContract(VERSION, targetAssistant, OPERATIONS, BuggySupervisorOwnershipBoundary.canonical());
    }

    public static Map<AssistantId, BuggyFutureAssistantContract> allContracts() {
        EnumMap<AssistantId, BuggyFutureAssistantContract> result = new EnumMap<>(AssistantId.class);
        FUTURE_TARGETS.forEach(target -> result.put(target, contract(target)));
        return Map.copyOf(result);
    }

    public static void requireFutureTarget(AssistantId targetAssistant) {
        if (targetAssistant == null || !FUTURE_TARGETS.contains(targetAssistant)) {
            throw new IllegalArgumentException("supervisor target must be reserved BYTE or KAESE");
        }
    }

    private static Map<BuggySupervisorOperation, BuggySupervisorAuthorityMode> operations() {
        EnumMap<BuggySupervisorOperation, BuggySupervisorAuthorityMode> result = new EnumMap<>(BuggySupervisorOperation.class);
        Arrays.stream(BuggySupervisorOperation.values()).forEach(operation -> result.put(operation, operation.authorityMode()));
        return Map.copyOf(result);
    }
}
