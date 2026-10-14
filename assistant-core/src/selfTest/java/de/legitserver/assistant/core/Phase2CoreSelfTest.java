package de.legitserver.assistant.core;

import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.actor.ActorType;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.core.assistant.AssistantCapabilities;
import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.core.assistant.AssistantDescriptor;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.core.error.AssistantError;
import de.legitserver.assistant.core.error.AssistantErrorCodes;
import de.legitserver.assistant.core.request.RequestContext;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Offline Phase-2 tests for immutable core-domain contracts and validation. */
public final class Phase2CoreSelfTest {

    private Phase2CoreSelfTest() {}

    public static void main(String[] args) {
        stableAssistantIds();
        capabilityNamespace();
        assistantDescriptorValidation();
        actorContextValidation();
        requestContextValidation();
        classificationOrdering();
        errorEnvelopeIsImmutableAndBounded();
        invalidValuesFailClosed();
        System.out.println("PHASE2_CORE_SELF_TEST: PASS");
    }

    private static void stableAssistantIds() {
        assertEquals(Set.of("KAESE", "BYTE", "BUGGY"),
                Set.of(AssistantId.KAESE.name(), AssistantId.BYTE.name(), AssistantId.BUGGY.name()),
                "stable assistant ids");
        assertEquals(AssistantId.BUGGY, AssistantId.fromWireValue("BUGGY"), "wire parse");
        expectFailure(() -> AssistantId.fromWireValue("buggy"), "assistant ids are case-sensitive");
    }

    private static void capabilityNamespace() {
        Set<AssistantCapabilityId> capabilities = AssistantCapabilities.initialNamespace();
        assertEquals(17, capabilities.size(), "initial capability count");
        assertTrue(capabilities.contains(AssistantCapabilities.SUPERVISOR_OBSERVE), "supervisor capability");
        assertTrue(capabilities.contains(AssistantCapabilities.SELF_HEAL_LOW), "self-heal capability");
        capabilities.forEach(capability -> assertTrue(capability.value().startsWith("assistant."), "capability namespace"));
    }

    private static void assistantDescriptorValidation() {
        Instant now = Instant.parse("2026-10-03T20:00:00Z");
        AssistantDescriptor descriptor =
                new AssistantDescriptor(
                        AssistantId.BUGGY,
                        "BuggyBot",
                        true,
                        "buggy-capabilities-v1",
                        "buggy-policy-v1",
                        "buggy-knowledge-v1",
                        "buggy-runtime-v1",
                        null,
                        0,
                        now);
        assertEquals("BuggyBot", descriptor.displayName(), "display name");
        assertEquals(null, descriptor.personalityProfileId(), "personality is optional");
        expectFailure(
                () -> new AssistantDescriptor(
                        AssistantId.KAESE, " ", true, "cap", "policy", "knowledge", "runtime", null, 0, now),
                "blank display name");
    }

    private static void actorContextValidation() {
        Instant authenticated = Instant.parse("2026-10-03T20:01:00Z");
        ActorContext context =
                new ActorContext(
                        new ActorId("actor:owner-1"),
                        ActorType.HUMAN,
                        Origin.PANEL,
                        "network:owner-1",
                        UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                        "panel:owner-1",
                        null,
                        new RequestId("req:phase2-1"),
                        new TraceId("trace:phase2-1"),
                        "panel-01",
                        42,
                        authenticated);
        assertEquals(42L, context.securityRevision(), "security revision");
        assertEquals(Origin.PANEL, context.origin(), "origin");
        expectFailure(
                () -> new ActorContext(
                        new ActorId("actor:x"), ActorType.HUMAN, Origin.PANEL, "network:x", null, null, null,
                        new RequestId("req:x"), new TraceId("trace:x"), null, -1, authenticated),
                "negative security revision");
    }

    private static void requestContextValidation() {
        RequestContext request =
                new RequestContext(
                        new RequestId("req:42"),
                        new TraceId("trace:42"),
                        new ActorId("actor:42"),
                        Origin.INTERNAL,
                        AssistantId.BYTE,
                        null,
                        null,
                        Instant.parse("2026-10-03T20:02:00Z"));
        assertEquals(AssistantId.BYTE, request.assistantId(), "assistant in request context");
    }

    private static void classificationOrdering() {
        assertTrue(DataClassification.SECRET.isAtLeast(DataClassification.RESTRICTED), "secret >= restricted");
        assertTrue(!DataClassification.PUBLIC.isAtLeast(DataClassification.INTERNAL), "public < internal");
        assertEquals(
                DataClassification.CONFIDENTIAL,
                DataClassification.max(DataClassification.INTERNAL, DataClassification.CONFIDENTIAL),
                "classification max");
    }

    private static void errorEnvelopeIsImmutableAndBounded() {
        Map<String, String> source = new HashMap<>();
        source.put("field", "displayName");
        AssistantError error =
                new AssistantError(
                        AssistantErrorCodes.INVALID_ARGUMENT,
                        "Invalid request.",
                        new RequestId("req:error-1"),
                        false,
                        source);
        source.put("field", "changed");
        assertEquals("displayName", error.details().get("field"), "defensive copy");
        expectFailure(() -> error.details().put("x", "y"), "immutable details");

        Map<String, String> oversized = new HashMap<>();
        for (int index = 0; index < 17; index++) {
            oversized.put("key" + index, "value");
        }
        expectFailure(
                () -> new AssistantError(
                        AssistantErrorCodes.INTERNAL,
                        "Internal error.",
                        new RequestId("req:error-2"),
                        true,
                        oversized),
                "bounded details");
    }

    private static void invalidValuesFailClosed() {
        expectFailure(() -> new ActorId("actor with spaces"), "actor id unsafe token");
        expectFailure(() -> new AssistantCapabilityId("source.read"), "capability namespace required");
        expectFailure(() -> new RequestId("req\nattack"), "request control characters");
    }

    private static void expectFailure(Runnable action, String message) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new IllegalStateException("Expected failure: " + message);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Assertion failed: " + message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new IllegalStateException(
                    "Assertion failed: " + message + " expected=" + expected + " actual=" + actual);
        }
    }
}
