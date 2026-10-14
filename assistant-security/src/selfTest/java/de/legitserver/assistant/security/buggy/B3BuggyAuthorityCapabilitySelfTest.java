package de.legitserver.assistant.security.buggy;

import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.actor.ActorType;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.core.assistant.AssistantDescriptor;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.security.authn.BoundedReplayProtector;
import de.legitserver.assistant.security.authn.HmacServiceTokenService;
import de.legitserver.assistant.security.authn.ServicePrincipal;
import de.legitserver.assistant.security.authn.ServiceScopes;
import de.legitserver.assistant.security.authn.ServiceTokenClaims;
import de.legitserver.assistant.security.authorization.ActorResolution;
import de.legitserver.assistant.security.authorization.AssistantAuthorizationService;
import de.legitserver.assistant.security.authorization.AssistantSecurityCatalog;
import de.legitserver.assistant.security.authorization.AuthoritativeSecuritySnapshot;
import de.legitserver.assistant.security.authorization.AuthorizationDecision;
import de.legitserver.assistant.security.authorization.AuthorizationDenialReason;
import de.legitserver.assistant.security.authorization.AuthorizationRequest;
import de.legitserver.assistant.security.authorization.ResourceAuthorizationStatus;
import de.legitserver.assistant.security.authorization.ResolvedActorIdentity;
import de.legitserver.assistant.security.authorization.SecuritySnapshotLookup;
import de.legitserver.assistant.security.authorization.buggy.BuggyAuthorityMode;
import de.legitserver.assistant.security.authorization.buggy.BuggyCapabilityGroup;
import de.legitserver.assistant.security.authorization.buggy.BuggyCapabilityPolicy;
import de.legitserver.assistant.security.authorization.buggy.BuggyCapabilityProfile;
import de.legitserver.assistant.security.authorization.buggy.BuggyOwnerOnlyAction;
import de.legitserver.assistant.security.authorization.buggy.BuggySecurityCapabilities;
import de.legitserver.assistant.security.authorization.buggy.BuggySecurityCatalog;
import de.legitserver.assistant.security.control.InMemorySecurityControlPlane;
import de.legitserver.assistant.security.control.OperationalMode;
import de.legitserver.assistant.security.control.SecurityFeature;
import de.legitserver.assistant.security.control.SecurityKillSwitch;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** B3 verification: Buggy profile binding delegates every allow/deny decision to existing Foundation security contracts. */
public final class B3BuggyAuthorityCapabilitySelfTest {
    private static final Instant NOW = Instant.parse("2026-10-04T21:00:00Z");
    private static final UUID ACTOR_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final ActorId ACTOR_ID = new ActorId("actor:buggy-owner");
    private static final String NETWORK_ID = "network:buggy-owner";

    private B3BuggyAuthorityCapabilitySelfTest() {}

    public static void main(String[] args) {
        canonicalProfileMatchesSpec();
        catalogIsNetworkCoreBacked();
        ownerOnlyBoundaryHasNoDirectCapability();
        descriptorProfileIsFailClosed();
        networkCorePermissionIsAuthoritative();
        emergencyControlsOverrideBuggy();
        System.out.println("B3_BUGGY_AUTHORITY_CAPABILITY_SELF_TEST: PASS");
    }

    private static void canonicalProfileMatchesSpec() {
        BuggyCapabilityProfile profile = BuggyCapabilityProfile.canonical();
        require(profile.profileId().equals("buggy-capabilities-v1"), "canonical profile id");
        require(profile.assistantId() == AssistantId.BUGGY, "profile belongs only to Buggy");
        require(profile.bundles().keySet().equals(Set.of(BuggyCapabilityGroup.values())), "all V1 capability groups present");
        require(profile.capabilities().equals(BuggySecurityCapabilities.all()), "profile covers atomic namespace exactly");
        require(profile.bindings().stream().noneMatch(binding -> binding.authorityMode() == BuggyAuthorityMode.OWNER_ONLY),
                "OWNER_ONLY is never a Buggy-granted capability");
        require(profile.bindings().stream().anyMatch(binding -> binding.authorityMode() == BuggyAuthorityMode.REQUEST_ONLY),
                "privileged domains expose request-only bindings");
        require(profile.bindings().stream().anyMatch(binding -> binding.authorityMode() == BuggyAuthorityMode.READ),
                "broad read bindings exist");
    }

    private static void catalogIsNetworkCoreBacked() {
        require(BuggySecurityCatalog.rules().keySet().equals(BuggySecurityCapabilities.all()),
                "each Buggy atomic capability has exactly one security rule");
        require(AssistantSecurityCatalog.rules().keySet().containsAll(BuggySecurityCapabilities.all()),
                "Foundation security catalog exposes every B3 rule");
        require(BuggySecurityCatalog.requiredNetworkCorePermissions().contains("networkcore.assistant.use.buggy"),
                "Buggy use permission is part of the NetworkCore provisioning contract");
        for (var entry : BuggySecurityCatalog.rules().entrySet()) {
            require(entry.getValue().assistantPermission().startsWith("networkcore.assistant.buggy."),
                    "B3 capability permission must be NetworkCore-backed: " + entry.getKey());
            require(entry.getValue().killSwitches().contains(SecurityKillSwitch.ALL_TOOLS_OFF),
                    "B3 capability must obey global tool kill switch: " + entry.getKey());
        }
    }

    private static void ownerOnlyBoundaryHasNoDirectCapability() {
        require(BuggyOwnerOnlyAction.values().length >= 10, "canonical Owner-only boundary must be explicit");
        for (AssistantCapabilityId capability : BuggySecurityCapabilities.all()) {
            String id = capability.value();
            require(!id.contains("grant.permission"), "no direct permission-grant capability");
            require(!id.contains("self.approve"), "no self-approval capability");
            require(!id.contains("risk.downgrade"), "no risk-downgrade capability");
            require(!id.contains("kill.switch.disable"), "no kill-switch bypass capability");
        }
    }

    private static void descriptorProfileIsFailClosed() {
        Fixture fixture = fixture(permissionFor(BuggySecurityCapabilities.DIAGNOSTICS_READ));
        AuthorizationDecision allowed = fixture.authorization.authorize(request(
                fixture.service, descriptor(BuggyCapabilityProfile.PROFILE_ID), BuggySecurityCapabilities.DIAGNOSTICS_READ));
        require(allowed.allowed(), "canonical Buggy profile must authorize when NetworkCore permits it");

        AuthorizationDecision wrongProfile = fixture.authorization.authorize(request(
                fixture.service, descriptor("tampered-capability-profile"), BuggySecurityCapabilities.DIAGNOSTICS_READ));
        require(wrongProfile.denialReason() == AuthorizationDenialReason.ASSISTANT_CAPABILITY_DENIED,
                "wrong capability profile must fail closed");

        BuggyCapabilityPolicy policy = new BuggyCapabilityPolicy();
        require(!policy.hasCapability(AssistantId.BYTE, BuggySecurityCapabilities.DIAGNOSTICS_READ),
                "B3 must not start BYTE");
        require(!policy.hasCapability(AssistantId.KAESE, BuggySecurityCapabilities.DIAGNOSTICS_READ),
                "B3 must not start KAESE");
    }

    private static void networkCorePermissionIsAuthoritative() {
        Fixture fixture = fixture(permissionFor(BuggySecurityCapabilities.DIAGNOSTICS_READ));
        AuthorizationRequest request = request(
                fixture.service, descriptor(BuggyCapabilityProfile.PROFILE_ID), BuggySecurityCapabilities.DIAGNOSTICS_READ);
        require(fixture.authorization.authorize(request).allowed(), "NetworkCore-backed capability should authorize");

        fixture.securitySnapshot.set(SecuritySnapshotLookup.found(snapshot(Set.of("networkcore.assistant.use.buggy"))));
        AuthorizationDecision revoked = fixture.authorization.authorize(request);
        require(revoked.denialReason() == AuthorizationDenialReason.PERMISSION_DENIED,
                "revoked NetworkCore capability permission must deny immediately");

        fixture.securitySnapshot.set(SecuritySnapshotLookup.found(snapshot(Set.of(
                AssistantSecurityCatalog.rule(BuggySecurityCapabilities.DIAGNOSTICS_READ).orElseThrow().assistantPermission()))));
        AuthorizationDecision useRevoked = fixture.authorization.authorize(request);
        require(useRevoked.denialReason() == AuthorizationDenialReason.ASSISTANT_USE_DENIED,
                "revoked networkcore.assistant.use.buggy must deny Buggy");
    }

    private static void emergencyControlsOverrideBuggy() {
        Fixture diagnosticFixture = fixture(permissionFor(BuggySecurityCapabilities.DIAGNOSTICS_READ));
        AuthorizationRequest diagnostics = request(
                diagnosticFixture.service,
                descriptor(BuggyCapabilityProfile.PROFILE_ID),
                BuggySecurityCapabilities.DIAGNOSTICS_READ);
        diagnosticFixture.controls.setKillSwitch(SecurityKillSwitch.ALL_TOOLS_OFF, true, NOW.plusSeconds(1));
        require(diagnosticFixture.authorization.authorize(diagnostics).denialReason() == AuthorizationDenialReason.KILL_SWITCH_ACTIVE,
                "global kill switch must override Buggy read capability");

        Fixture mutatingFixture = fixture(permissionFor(BuggySecurityCapabilities.OPERATIONS_MUTATE_LOW));
        AuthorizationRequest mutate = request(
                mutatingFixture.service,
                descriptor(BuggyCapabilityProfile.PROFILE_ID),
                BuggySecurityCapabilities.OPERATIONS_MUTATE_LOW);
        mutatingFixture.controls.setOperationalMode(OperationalMode.READ_ONLY, NOW.plusSeconds(2));
        require(mutatingFixture.authorization.authorize(mutate).denialReason() == AuthorizationDenialReason.READ_ONLY_MODE,
                "READ_ONLY must block Buggy mutation");
        mutatingFixture.controls.setOperationalMode(OperationalMode.MAINTENANCE, NOW.plusSeconds(3));
        require(mutatingFixture.authorization.authorize(mutate).denialReason() == AuthorizationDenialReason.MAINTENANCE_MODE,
                "MAINTENANCE must block Buggy completely");
    }

    private static Fixture fixture(Set<String> permissions) {
        ResolvedActorIdentity resolved = new ResolvedActorIdentity(ACTOR_ID, NETWORK_ID, ACTOR_UUID, 1L);
        AtomicReference<SecuritySnapshotLookup> snapshot = new AtomicReference<>(SecuritySnapshotLookup.found(snapshot(permissions)));
        InMemorySecurityControlPlane controls = new InMemorySecurityControlPlane(EnumSet.allOf(SecurityFeature.class), NOW);
        AssistantAuthorizationService authorization = new AssistantAuthorizationService(
                actor -> ActorResolution.found(resolved),
                (uuid, minimumRevision) -> snapshot.get(),
                new BuggyCapabilityPolicy(),
                (actor, security, capability, resource) -> ResourceAuthorizationStatus.ALLOW,
                controls);
        return new Fixture(authorization, servicePrincipal(), snapshot, controls);
    }

    private static Set<String> permissionFor(AssistantCapabilityId capability) {
        var rule = AssistantSecurityCatalog.rule(capability).orElseThrow();
        java.util.LinkedHashSet<String> permissions = new java.util.LinkedHashSet<>();
        permissions.add("networkcore.assistant.use.buggy");
        permissions.addAll(rule.allCapabilityPermissions());
        return Set.copyOf(permissions);
    }

    private static AuthoritativeSecuritySnapshot snapshot(Set<String> permissions) {
        return new AuthoritativeSecuritySnapshot(
                ACTOR_UUID,
                "owner",
                10_000,
                "OWNER",
                Set.of("OWNER"),
                Set.of("usr"),
                permissions,
                Set.of(),
                Set.of(),
                true,
                1L,
                NOW);
    }

    private static AuthorizationRequest request(
            ServicePrincipal service, AssistantDescriptor descriptor, AssistantCapabilityId capability) {
        return new AuthorizationRequest(service, actor(), descriptor, capability, null);
    }

    private static ActorContext actor() {
        return new ActorContext(
                ACTOR_ID,
                ActorType.HUMAN,
                Origin.PANEL,
                NETWORK_ID,
                ACTOR_UUID,
                "owner-panel",
                null,
                new RequestId("req-b3-authorization"),
                new TraceId("trace-b3-authorization"),
                null,
                1L,
                NOW);
    }

    private static AssistantDescriptor descriptor(String capabilityProfileId) {
        return new AssistantDescriptor(
                AssistantId.BUGGY,
                "Buggy",
                true,
                capabilityProfileId,
                "buggy-policy-v1",
                "buggy-knowledge-v1",
                "buggy-runtime-v1",
                "buggy-personality-v1",
                3L,
                NOW);
    }

    private static ServicePrincipal servicePrincipal() {
        byte[] key = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        RequestId requestId = new RequestId("req-b3-service");
        HmacServiceTokenService tokens = new HmacServiceTokenService(key, new BoundedReplayProtector(16));
        String token = tokens.issue(new ServiceTokenClaims(
                "assistant-runtime",
                "legit-assistant",
                Set.of(ServiceScopes.INVOKE),
                NOW.minusSeconds(1),
                NOW.plusSeconds(60),
                "jti-b3-service",
                requestId));
        var result = tokens.verify(token, "legit-assistant", requestId, Set.of(ServiceScopes.INVOKE), NOW);
        if (!result.authenticated()) throw new IllegalStateException("B3 test service token failed verification");
        return result.principal();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Assertion failed: " + message);
    }

    private record Fixture(
            AssistantAuthorizationService authorization,
            ServicePrincipal service,
            AtomicReference<SecuritySnapshotLookup> securitySnapshot,
            InMemorySecurityControlPlane controls) {}
}
