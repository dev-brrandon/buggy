package de.legitserver.assistant.security.authorization.buggy;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.List;

/** Immutable canonical bundle for one BUGGYBOT-SPEC-V1 capability group. */
public record BuggyCapabilityBundle(BuggyCapabilityGroup group, List<BuggyCapabilityBinding> bindings) {
    public BuggyCapabilityBundle {
        BuggyCapabilityGroup normalizedGroup = CoreValidation.requireNonNull(group, "group");
        group = normalizedGroup;
        bindings = List.copyOf(CoreValidation.requireNonNull(bindings, "bindings"));
        if (bindings.isEmpty()) throw new IllegalArgumentException("Buggy capability bundle must not be empty");
        if (bindings.stream().anyMatch(binding -> binding.group() != normalizedGroup)) {
            throw new IllegalArgumentException("Buggy capability bundle contains a binding from another group");
        }
        long unique = bindings.stream().map(BuggyCapabilityBinding::capabilityId).distinct().count();
        if (unique != bindings.size()) throw new IllegalArgumentException("Duplicate capability in Buggy bundle");
    }
}
