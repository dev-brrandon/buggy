package de.legitserver.assistant.buggy;

import de.legitserver.assistant.buggy.config.BuggyConfigurationRevision;
import de.legitserver.assistant.buggy.config.BuggyManifest;
import de.legitserver.assistant.buggy.config.BuggyManifestCanonicalizer;
import de.legitserver.assistant.buggy.identity.BuggyBootstrapSeed;
import de.legitserver.assistant.buggy.identity.BuggyIdentityCore;
import de.legitserver.assistant.buggy.identity.BuggyIdentitySnapshot;
import de.legitserver.assistant.buggy.repository.BuggyIdentityRepository;
import de.legitserver.assistant.buggy.runtime.BuggyIdentityRuntime;
import de.legitserver.assistant.buggy.service.BuggyIdentityService;
import de.legitserver.assistant.buggy.service.BuggyIdentityUnavailableException;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.assistant.AssistantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/** Offline B1 verification for persistent identity, Owner binding, manifest integrity and runtime/session separation. */
public final class B1BuggyIdentityRuntimeSelfTest {
    private static final Instant NOW = Instant.parse("2026-10-04T20:00:00Z");

    private B1BuggyIdentityRuntimeSelfTest() {}

    public static void main(String[] args) {
        protectedIdentityCore();
        manifestIsTypedAndSecurityFixed();
        failClosedBeforeProvisioning();
        persistentIdentityAndOwnerBinding();
        runtimeAndSessionAreNotIdentity();
        System.out.println("B1_BUGGY_IDENTITY_RUNTIME_SELF_TEST: PASS");
    }

    private static void protectedIdentityCore() {
        BuggyIdentityCore core = BuggyIdentityCore.canonical(NOW);
        require(core.assistantId() == AssistantId.BUGGY, "canonical assistant id");
        require(core.canonicalId().equals("buggy"), "canonical id");
        require(core.serviceIdentity().equals("assistant:buggy"), "service identity");
        require(core.sha256().equals(BuggyIdentityCore.canonicalSha256()), "identity core hash");
        expectFailure(() -> new BuggyIdentityCore(
                AssistantId.BUGGY,
                "buggy",
                "Not Buggy",
                "assistant:buggy",
                "OWNER_ASSISTANT",
                BuggyIdentityCore.PRIMARY_ROLE,
                BuggyIdentityCore.AUTHORITY_TIER,
                BuggyIdentityCore.SECURITY_AUTHORITY,
                BuggyIdentityCore.SCOPES,
                BuggyIdentityCore.PRINCIPLES,
                1,
                BuggyIdentityCore.canonicalSha256(),
                NOW));
    }

    private static void manifestIsTypedAndSecurityFixed() {
        BuggyManifest manifest = BuggyManifest.initial();
        require(BuggyManifestCanonicalizer.sha256(manifest).length() == 64, "manifest hash");
        require(!manifest.selfApprovalAllowed(), "self approval forbidden");
        require(!manifest.authoritySelfEscalationAllowed(), "authority self escalation forbidden");
        require(!manifest.riskDowngradeAllowed(), "risk downgrade forbidden");
        expectFailure(() -> new BuggyManifest(
                1,
                AssistantId.BUGGY,
                "buggy",
                "Buggy",
                "assistant:buggy",
                "OWNER_ASSISTANT",
                BuggyIdentityCore.SCOPES,
                BuggyIdentityCore.PRIMARY_ROLE,
                BuggyIdentityCore.AUTHORITY_TIER,
                "BUGGY_SELF_AUTHORITY",
                "cap",
                "policy",
                "knowledge",
                "runtime",
                null,
                true,
                true,
                true,
                false,
                false,
                false));
    }

    private static void failClosedBeforeProvisioning() {
        BuggyIdentityService service = new BuggyIdentityService(new InMemoryIdentityRepository());
        try {
            service.loadRequired();
            throw new IllegalStateException("unprovisioned identity did not fail closed");
        } catch (BuggyIdentityUnavailableException expected) {
            // expected
        }
    }

    private static void persistentIdentityAndOwnerBinding() {
        InMemoryIdentityRepository repository = new InMemoryIdentityRepository();
        BuggyIdentityService service = new BuggyIdentityService(repository);
        BuggyBootstrapSeed seed = BuggyBootstrapSeed.create(
                new ActorId("actor:owner-canonical"),
                new ActorId("system:buggy-provisioning"),
                NOW);
        BuggyIdentitySnapshot initialized = service.initialize(seed);
        BuggyIdentitySnapshot loaded = service.loadRequired();
        require(initialized.equals(loaded), "identity persists across loads");
        require(loaded.ownerBinding().ownerActorId().value().equals("actor:owner-canonical"), "Owner binding");
        require(loaded.ownerBinding().authoritySource().equals("NETWORKCORE"), "Owner authority source");
        require(service.assistantDescriptor().assistantId() == AssistantId.BUGGY, "Foundation descriptor projection");
        require(service.assistantDescriptor().displayName().equals("Buggy"), "display name projection");
        require(service.assistantDescriptor().personalityProfileId() == null, "B2 personality not implemented in B1");

        BuggyIdentitySnapshot again = service.initialize(seed);
        require(again.equals(loaded), "provisioning is idempotent for the same seed");
        expectFailure(() -> service.initialize(BuggyBootstrapSeed.create(
                new ActorId("actor:different-owner"),
                new ActorId("system:buggy-provisioning"),
                NOW)));
    }

    private static void runtimeAndSessionAreNotIdentity() {
        InMemoryIdentityRepository repository = new InMemoryIdentityRepository();
        BuggyIdentityService service = new BuggyIdentityService(repository);
        service.initialize(BuggyBootstrapSeed.create(
                new ActorId("actor:owner-canonical"),
                new ActorId("system:buggy-provisioning"),
                NOW));
        Clock clock = Clock.fixed(NOW.plusSeconds(10), ZoneOffset.UTC);
        BuggyIdentityRuntime runtimeA = new BuggyIdentityRuntime(service, clock);
        BuggyIdentityRuntime runtimeB = new BuggyIdentityRuntime(service, clock);
        require(runtimeA.identity().equals(runtimeB.identity()), "restart preserves persistent identity");
        require(!runtimeA.runtimeInstance().runtimeInstanceId().equals(runtimeB.runtimeInstance().runtimeInstanceId()),
                "runtime instances are ephemeral");

        UUID conversation = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        var sessionA = runtimeA.openSession(conversation);
        var sessionB = runtimeA.openSession(conversation);
        require(!sessionA.sessionInstanceId().equals(sessionB.sessionInstanceId()), "sessions are ephemeral");
        require(sessionA.runtimeInstanceId().equals(runtimeA.runtimeInstance().runtimeInstanceId()), "session bound to runtime");
        require(sessionA.assistantId() == AssistantId.BUGGY, "session references identity without defining it");
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new IllegalStateException("Expected failure");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Assertion failed: " + message);
    }

    private static final class InMemoryIdentityRepository implements BuggyIdentityRepository {
        private BuggyIdentitySnapshot snapshot;

        @Override
        public Optional<BuggyIdentitySnapshot> find() {
            return Optional.ofNullable(snapshot);
        }

        @Override
        public BuggyIdentitySnapshot initialize(BuggyBootstrapSeed seed) {
            if (snapshot != null) {
                if (!snapshot.identityCore().equals(seed.identityCore())
                        || !snapshot.ownerBinding().equals(seed.ownerBinding())) {
                    throw new IllegalStateException("Buggy identity already provisioned with different immutable B1 state");
                }
                return snapshot;
            }
            snapshot = new BuggyIdentitySnapshot(
                    seed.identityCore(), seed.ownerBinding(), seed.configuration(), 1L, seed.configuration().createdAt());
            return snapshot;
        }

        @Override
        public BuggyIdentitySnapshot activatePersonalityProfile(
                String profileId, ActorId createdBy, Instant createdAt) {
            if (snapshot == null) throw new IllegalStateException("Buggy identity is not provisioned");
            String active = snapshot.configuration().manifest().personalityProfileId();
            if (profileId.equals(active)) return snapshot;
            if (active != null) throw new IllegalStateException("different personality profile already active");
            BuggyManifest manifest = snapshot.configuration().manifest().withPersonalityProfileId(profileId);
            BuggyConfigurationRevision configuration = new BuggyConfigurationRevision(
                    snapshot.configuration().revision() + 1,
                    manifest,
                    createdBy,
                    createdAt,
                    BuggyManifestCanonicalizer.sha256(manifest));
            snapshot = new BuggyIdentitySnapshot(
                    snapshot.identityCore(),
                    snapshot.ownerBinding(),
                    configuration,
                    snapshot.headRevision() + 1,
                    createdAt);
            return snapshot;
        }
    }
}
