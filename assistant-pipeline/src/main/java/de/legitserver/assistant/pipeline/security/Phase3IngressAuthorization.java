package de.legitserver.assistant.pipeline.security;

import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.pipeline.model.PipelineRequest;
import de.legitserver.assistant.pipeline.port.PipelineAuthorizationDecision;
import de.legitserver.assistant.pipeline.port.PipelineAuthorizationPort;
import de.legitserver.assistant.security.authn.ServicePrincipal;
import de.legitserver.assistant.security.authn.ServiceScopes;
import de.legitserver.assistant.security.authorization.AssistantSecurityCatalog;
import de.legitserver.assistant.security.authorization.AuthoritativeSecuritySnapshot;
import de.legitserver.assistant.security.authorization.AuthoritativeSecuritySnapshotPort;
import de.legitserver.assistant.security.authorization.AuthorityLookupStatus;
import de.legitserver.assistant.security.authorization.SecuritySnapshotLookup;
import de.legitserver.assistant.security.control.OperationalMode;
import de.legitserver.assistant.security.control.SecurityControlPlane;

/**
 * Capability-independent ingress authorization used at the canonical early Authorization stage.
 * Capability/resource authorization still runs later in Policy through the existing Phase-3 authority.
 */
public final class Phase3IngressAuthorization implements PipelineAuthorizationPort {
    private final AuthoritativeSecuritySnapshotPort snapshots;
    private final SecurityControlPlane controls;

    public Phase3IngressAuthorization(AuthoritativeSecuritySnapshotPort snapshots, SecurityControlPlane controls) {
        if (snapshots == null || controls == null) throw new IllegalArgumentException("authorization dependencies must not be null");
        this.snapshots = snapshots;
        this.controls = controls;
    }

    @Override
    public PipelineAuthorizationDecision authorize(PipelineRequest request, ServicePrincipal principal, ActorContext actor) {
        if (!"legit-assistant".equals(principal.audience()) || !principal.hasScope(ServiceScopes.INVOKE)) {
            return PipelineAuthorizationDecision.deny("SERVICE_NOT_AUTHORIZED", actor.securityRevision());
        }
        if (!request.assistant().enabled()) {
            return PipelineAuthorizationDecision.deny("ASSISTANT_DISABLED", actor.securityRevision());
        }
        if (actor.minecraftUuid() == null) {
            return PipelineAuthorizationDecision.deny("ACTOR_UUID_REQUIRED", actor.securityRevision());
        }
        SecuritySnapshotLookup lookup;
        try {
            lookup = snapshots.lookup(actor.minecraftUuid(), actor.securityRevision());
        } catch (RuntimeException unavailable) {
            return PipelineAuthorizationDecision.deny("AUTHORITY_UNAVAILABLE", actor.securityRevision());
        }
        if (lookup == null || lookup.status() == AuthorityLookupStatus.UNAVAILABLE) {
            return PipelineAuthorizationDecision.deny("AUTHORITY_UNAVAILABLE", actor.securityRevision());
        }
        if (lookup.status() != AuthorityLookupStatus.FOUND) {
            return PipelineAuthorizationDecision.deny("ACTOR_NOT_FOUND", actor.securityRevision());
        }
        AuthoritativeSecuritySnapshot snapshot = lookup.snapshot();
        if (!snapshot.actorUuid().equals(actor.minecraftUuid())) {
            return PipelineAuthorizationDecision.deny("ACTOR_BINDING_MISMATCH", snapshot.securityRevision());
        }
        if (!snapshot.hasPermission(AssistantSecurityCatalog.assistantUsePermission(request.assistant().assistantId()))) {
            return PipelineAuthorizationDecision.deny("ASSISTANT_USE_DENIED", snapshot.securityRevision());
        }
        var channelPermission = AssistantSecurityCatalog.channelPermission(
                request.assistant().assistantId(), actor.origin());
        if (channelPermission.isPresent() && !snapshot.hasPermission(channelPermission.get())) {
            return PipelineAuthorizationDecision.deny("ASSISTANT_CHANNEL_DENIED", snapshot.securityRevision());
        }
        if (controls.current().operationalMode() == OperationalMode.MAINTENANCE) {
            return PipelineAuthorizationDecision.deny("MAINTENANCE_MODE", snapshot.securityRevision());
        }
        return PipelineAuthorizationDecision.allow(snapshot.securityRevision());
    }
}
