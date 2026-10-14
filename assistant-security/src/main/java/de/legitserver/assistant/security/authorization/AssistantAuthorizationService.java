package de.legitserver.assistant.security.authorization;

import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.security.authn.ServiceScopes;
import de.legitserver.assistant.security.control.OperationalMode;
import de.legitserver.assistant.security.control.SecurityControlPlane;
import de.legitserver.assistant.security.control.SecurityControlState;

/** Complete fail-closed Phase-3 authorization pipeline. */
public final class AssistantAuthorizationService {
    private static final String EXPECTED_AUDIENCE = "legit-assistant";

    private final AuthoritativeActorResolverPort actorResolver;
    private final AuthoritativeSecuritySnapshotPort securitySnapshots;
    private final AssistantCapabilityPolicy assistantCapabilities;
    private final ResourceAuthorizationPort resourceAuthorization;
    private final SecurityControlPlane controlPlane;

    public AssistantAuthorizationService(
            AuthoritativeActorResolverPort actorResolver,
            AuthoritativeSecuritySnapshotPort securitySnapshots,
            AssistantCapabilityPolicy assistantCapabilities,
            ResourceAuthorizationPort resourceAuthorization,
            SecurityControlPlane controlPlane) {
        if (actorResolver == null || securitySnapshots == null || assistantCapabilities == null
                || resourceAuthorization == null || controlPlane == null) {
            throw new IllegalArgumentException("authorization dependency must not be null");
        }
        this.actorResolver = actorResolver;
        this.securitySnapshots = securitySnapshots;
        this.assistantCapabilities = assistantCapabilities;
        this.resourceAuthorization = resourceAuthorization;
        this.controlPlane = controlPlane;
    }

    public AuthorizationDecision authorize(AuthorizationRequest request) {
        if (request == null) {
            return AuthorizationDecision.deny(AuthorizationDenialReason.SERVICE_UNAUTHENTICATED, 0, null);
        }
        CapabilitySecurityRule rule = AssistantSecurityCatalog.rule(request.capabilityId()).orElse(null);
        if (rule == null) {
            return AuthorizationDecision.deny(AuthorizationDenialReason.CAPABILITY_UNKNOWN, 0, null);
        }
        if (!EXPECTED_AUDIENCE.equals(request.servicePrincipal().audience())) {
            return AuthorizationDecision.deny(AuthorizationDenialReason.SERVICE_UNAUTHENTICATED, 0, rule.riskLevel());
        }
        if (!request.servicePrincipal().hasScope(ServiceScopes.INVOKE)) {
            return AuthorizationDecision.deny(AuthorizationDenialReason.SERVICE_SCOPE_MISSING, 0, rule.riskLevel());
        }
        ActorResolution actorResolution = actorResolver.resolve(request.actor());
        if (actorResolution.status() == AuthorityLookupStatus.UNAVAILABLE) {
            return AuthorizationDecision.deny(AuthorizationDenialReason.AUTHORITY_UNAVAILABLE, 0, rule.riskLevel());
        }
        if (actorResolution.status() != AuthorityLookupStatus.FOUND) {
            return AuthorizationDecision.deny(AuthorizationDenialReason.ACTOR_NOT_FOUND, 0, rule.riskLevel());
        }
        ResolvedActorIdentity identity = actorResolution.identity();
        if (!bindingMatches(request.actor(), identity)) {
            return AuthorizationDecision.deny(
                    AuthorizationDenialReason.ACTOR_BINDING_MISMATCH, identity.authorizationRevision(), rule.riskLevel());
        }
        long minimumRevision = Math.max(request.actor().securityRevision(), identity.authorizationRevision());
        SecuritySnapshotLookup security = securitySnapshots.lookup(identity.minecraftUuid(), minimumRevision);
        if (security.status() == AuthorityLookupStatus.UNAVAILABLE) {
            return AuthorizationDecision.deny(AuthorizationDenialReason.AUTHORITY_UNAVAILABLE, minimumRevision, rule.riskLevel());
        }
        if (security.status() != AuthorityLookupStatus.FOUND) {
            return AuthorizationDecision.deny(AuthorizationDenialReason.ACTOR_NOT_FOUND, minimumRevision, rule.riskLevel());
        }
        AuthoritativeSecuritySnapshot snapshot = security.snapshot();
        if (!snapshot.actorUuid().equals(identity.minecraftUuid())) {
            return AuthorizationDecision.deny(
                    AuthorizationDenialReason.ACTOR_BINDING_MISMATCH, snapshot.securityRevision(), rule.riskLevel());
        }
        if (!request.assistant().enabled()) {
            return AuthorizationDecision.deny(
                    AuthorizationDenialReason.ASSISTANT_DISABLED, snapshot.securityRevision(), rule.riskLevel());
        }
        AssistantId assistantId = request.assistant().assistantId();
        if (!snapshot.hasPermission(AssistantSecurityCatalog.assistantUsePermission(assistantId))) {
            return AuthorizationDecision.deny(
                    AuthorizationDenialReason.ASSISTANT_USE_DENIED, snapshot.securityRevision(), rule.riskLevel());
        }
        if (!assistantCapabilities.hasCapability(request.assistant(), request.capabilityId())) {
            return AuthorizationDecision.deny(
                    AuthorizationDenialReason.ASSISTANT_CAPABILITY_DENIED, snapshot.securityRevision(), rule.riskLevel());
        }
        for (String permission : rule.allCapabilityPermissions()) {
            if (!snapshot.hasPermission(permission)) {
                return AuthorizationDecision.deny(
                        AuthorizationDenialReason.PERMISSION_DENIED, snapshot.securityRevision(), rule.riskLevel());
            }
        }
        ResourceAuthorizationStatus resourceDecision = resourceAuthorization.authorize(
                request.actor(), snapshot, request.capabilityId(), request.resource());
        if (resourceDecision == ResourceAuthorizationStatus.UNAVAILABLE) {
            return AuthorizationDecision.deny(
                    AuthorizationDenialReason.RESOURCE_AUTHORIZATION_UNAVAILABLE,
                    snapshot.securityRevision(),
                    rule.riskLevel());
        }
        if (resourceDecision != ResourceAuthorizationStatus.ALLOW) {
            return AuthorizationDecision.deny(
                    AuthorizationDenialReason.RESOURCE_DENIED, snapshot.securityRevision(), rule.riskLevel());
        }
        SecurityControlState controls = controlPlane.current();
        if (controls.operationalMode() == OperationalMode.MAINTENANCE) {
            return AuthorizationDecision.deny(
                    AuthorizationDenialReason.MAINTENANCE_MODE, snapshot.securityRevision(), rule.riskLevel());
        }
        if (controls.operationalMode() == OperationalMode.READ_ONLY
                && rule.actionNature() == ActionNature.MUTATING) {
            return AuthorizationDecision.deny(
                    AuthorizationDenialReason.READ_ONLY_MODE, snapshot.securityRevision(), rule.riskLevel());
        }
        if (rule.feature() != null && !controls.featureEnabled(rule.feature())) {
            return AuthorizationDecision.deny(
                    AuthorizationDenialReason.FEATURE_DISABLED, snapshot.securityRevision(), rule.riskLevel());
        }
        for (var killSwitch : rule.killSwitches()) {
            if (controls.killSwitchActive(killSwitch)) {
                return AuthorizationDecision.deny(
                        AuthorizationDenialReason.KILL_SWITCH_ACTIVE, snapshot.securityRevision(), rule.riskLevel());
            }
        }
        return AuthorizationDecision.allow(snapshot.securityRevision(), rule.riskLevel());
    }

    private static boolean bindingMatches(ActorContext presented, ResolvedActorIdentity canonical) {
        if (!presented.actorId().equals(canonical.actorId())) return false;
        if (!presented.networkIdentityId().equals(canonical.networkIdentityId())) return false;
        return presented.minecraftUuid() == null || presented.minecraftUuid().equals(canonical.minecraftUuid());
    }
}
