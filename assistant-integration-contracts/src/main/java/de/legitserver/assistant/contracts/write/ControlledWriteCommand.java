package de.legitserver.assistant.contracts.write;

import de.legitserver.assistant.contracts.common.ContractValidation;
import de.legitserver.assistant.core.classification.DataClassification;
import java.util.Objects;

/** Closed set of future write intents. No shell/process escape hatch exists. */
public sealed interface ControlledWriteCommand permits ControlledWriteCommand.ReplaceText, ControlledWriteCommand.DeleteResource, ControlledWriteCommand.ServiceLifecycle {
    String resourceType();
    String resourceId();
    DataClassification classification();

    record ReplaceText(
            String resourceType,
            String resourceId,
            String contentType,
            String content,
            String expectedChecksum,
            DataClassification classification) implements ControlledWriteCommand {
        public ReplaceText {
            resourceType = ContractValidation.id(resourceType, "resourceType");
            resourceId = ContractValidation.id(resourceId, "resourceId");
            contentType = ContractValidation.contentType(contentType);
            if (content == null || content.length() > 2_000_000) throw new IllegalArgumentException("content exceeds contract limit");
            expectedChecksum = expectedChecksum == null ? null : ContractValidation.sha256(expectedChecksum);
            classification = Objects.requireNonNull(classification, "classification");
        }
    }

    record DeleteResource(
            String resourceType,
            String resourceId,
            String expectedChecksum,
            DataClassification classification) implements ControlledWriteCommand {
        public DeleteResource {
            resourceType = ContractValidation.id(resourceType, "resourceType");
            resourceId = ContractValidation.id(resourceId, "resourceId");
            expectedChecksum = ContractValidation.sha256(expectedChecksum);
            classification = Objects.requireNonNull(classification, "classification");
        }
    }

    record ServiceLifecycle(
            String resourceType,
            String resourceId,
            LifecycleAction action,
            DataClassification classification) implements ControlledWriteCommand {
        public ServiceLifecycle {
            resourceType = ContractValidation.id(resourceType, "resourceType");
            resourceId = ContractValidation.id(resourceId, "resourceId");
            action = Objects.requireNonNull(action, "action");
            classification = Objects.requireNonNull(classification, "classification");
        }
    }

    enum LifecycleAction { RESTART }
}
