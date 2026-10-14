package de.legitserver.assistant.pipeline.security;

import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.pipeline.model.PipelineRequest;
import de.legitserver.assistant.pipeline.port.PipelineActorResolution;
import de.legitserver.assistant.pipeline.port.PipelineActorResolverPort;
import de.legitserver.assistant.security.authn.ServicePrincipal;
import de.legitserver.assistant.security.authorization.ActorResolution;
import de.legitserver.assistant.security.authorization.AuthoritativeActorResolverPort;
import de.legitserver.assistant.security.authorization.AuthorityLookupStatus;
import de.legitserver.assistant.security.authorization.ResolvedActorIdentity;

/** Resolves the presented actor against the authoritative identity plane before authorization. */
public final class AuthoritativePipelineActorResolver implements PipelineActorResolverPort {
    private final AuthoritativeActorResolverPort authority;

    public AuthoritativePipelineActorResolver(AuthoritativeActorResolverPort authority) {
        if (authority == null) throw new IllegalArgumentException("authority must not be null");
        this.authority = authority;
    }

    @Override
    public PipelineActorResolution resolve(PipelineRequest request, ServicePrincipal principal) {
        if (request == null || principal == null) return PipelineActorResolution.denied("ACTOR_RESOLUTION_INVALID");
        ActorResolution resolution;
        try {
            resolution = authority.resolve(request.presentedActor());
        } catch (RuntimeException unavailable) {
            return PipelineActorResolution.denied("ACTOR_AUTHORITY_UNAVAILABLE");
        }
        if (resolution == null || resolution.status() == AuthorityLookupStatus.UNAVAILABLE) {
            return PipelineActorResolution.denied("ACTOR_AUTHORITY_UNAVAILABLE");
        }
        if (resolution.status() != AuthorityLookupStatus.FOUND) {
            return PipelineActorResolution.denied("ACTOR_NOT_FOUND");
        }
        ResolvedActorIdentity canonical = resolution.identity();
        ActorContext presented = request.presentedActor();
        if (!presented.actorId().equals(canonical.actorId())
                || !presented.networkIdentityId().equals(canonical.networkIdentityId())) {
            return PipelineActorResolution.denied("ACTOR_BINDING_MISMATCH");
        }
        ActorContext resolved = new ActorContext(
                canonical.actorId(),
                presented.actorType(),
                presented.origin(),
                canonical.networkIdentityId(),
                canonical.minecraftUuid(),
                presented.panelUserId(),
                presented.discordId(),
                presented.requestId(),
                presented.traceId(),
                presented.serverId(),
                canonical.authorizationRevision(),
                presented.authenticatedAt());
        return PipelineActorResolution.found(resolved);
    }
}
