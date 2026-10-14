package de.legitserver.assistant.bootstrap.g12;

import de.legitserver.assistant.api.v1.backend.PanelApiReadPort;
import de.legitserver.assistant.api.v1.error.ApiException;
import de.legitserver.assistant.api.v1.model.ApiCallContext;
import de.legitserver.assistant.api.v1.model.ApiResourceView;
import de.legitserver.assistant.api.v1.pagination.ApiPage;
import de.legitserver.assistant.api.v1.pagination.ApiPageRequest;
import de.legitserver.assistant.buggy.service.BuggyIdentityService;
import de.legitserver.assistant.conversations.model.ConversationId;
import de.legitserver.assistant.conversations.repository.ConversationRepository;
import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.integrations.networkcore.NetworkCoreAuthorizationAdapter;
import de.legitserver.assistant.security.authorization.AssistantSecurityCatalog;
import de.legitserver.assistant.security.authorization.AuthoritativeSecuritySnapshot;
import de.legitserver.assistant.security.authorization.AuthorityLookupStatus;
import de.legitserver.assistant.security.authorization.ResolvedActorIdentity;
import de.legitserver.assistant.security.authorization.buggy.BuggySecurityCapabilities;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Minimal secured read model required by the G1.2 Panel chat. Other surfaces remain fail-closed. */
final class G12PanelReadPort implements PanelApiReadPort {
    private final BuggyIdentityService identity;
    private final ConversationRepository conversations;
    private final NetworkCoreAuthorizationAdapter authority;

    G12PanelReadPort(
            BuggyIdentityService identity,
            ConversationRepository conversations,
            NetworkCoreAuthorizationAdapter authority) {
        if (identity == null || conversations == null || authority == null) {
            throw new IllegalArgumentException("G1.2 Panel read dependencies must not be null");
        }
        this.identity = identity;
        this.conversations = conversations;
        this.authority = authority;
    }

    @Override
    public ApiPage<ApiResourceView> listAssistants(ApiCallContext context, ApiPageRequest page) {
        requireBuggyPanelRead(context);
        return ApiPage.slice(List.of(assistantView()), page);
    }

    @Override
    public ApiResourceView getAssistant(ApiCallContext context, String id) {
        requireBuggyPanelRead(context);
        if (!"buggy".equalsIgnoreCase(id)) throw notFound();
        return assistantView();
    }

    @Override
    public ApiResourceView getConversation(ApiCallContext context, String id) {
        ActorContext actor = requireBuggyPanelRead(context).actor();
        try {
            var conversation = conversations.findById(ConversationId.parse(id)).orElseThrow(G12PanelReadPort::notFound);
            if (conversation.assistantId() != AssistantId.BUGGY
                    || conversation.origin() != Origin.PANEL
                    || !conversation.ownerActorId().equals(actor.actorId())) {
                throw notFound();
            }
            return new ApiResourceView(id, Map.of(
                    "assistantId", conversation.assistantId().name(),
                    "state", conversation.state().name(),
                    "classification", conversation.classification().name(),
                    "revision", conversation.revision(),
                    "updatedAt", conversation.updatedAt().toString()));
        } catch (IllegalArgumentException invalid) {
            throw notFound();
        }
    }

    @Override public ApiPage<ApiResourceView> listBugs(ApiCallContext c, ApiPageRequest p) { throw unavailable(); }
    @Override public ApiResourceView getBug(ApiCallContext c, String id) { throw unavailable(); }
    @Override public ApiPage<ApiResourceView> searchKnowledge(ApiCallContext c, String q, ApiPageRequest p) { throw unavailable(); }
    @Override public ApiResourceView getKnowledgeDocument(ApiCallContext c, String id) { throw unavailable(); }
    @Override public ApiResourceView getRequest(ApiCallContext c, String id) { throw unavailable(); }
    @Override public ApiPage<ApiResourceView> listSupervisorAssistants(ApiCallContext c, ApiPageRequest p) { throw unavailable(); }
    @Override public ApiResourceView getAssistantHealth(ApiCallContext c, String id) { throw unavailable(); }
    @Override public ApiPage<ApiResourceView> getAssistantFailures(ApiCallContext c, String id, ApiPageRequest p) { throw unavailable(); }

    private VerifiedPanelActor requireBuggyPanelRead(ApiCallContext context) {
        final ActorContext presented;
        try {
            presented = context.requirePanelRequest().presentedActor();
        } catch (RuntimeException missing) {
            throw notFound();
        }
        if (presented.origin() != Origin.PANEL) throw notFound();

        var actorLookup = authority.resolve(presented);
        if (actorLookup.status() == AuthorityLookupStatus.UNAVAILABLE) throw authorityUnavailable();
        if (actorLookup.status() != AuthorityLookupStatus.FOUND) throw notFound();

        ResolvedActorIdentity canonical = actorLookup.identity();
        if (!bindingMatches(presented, canonical)) throw notFound();

        long minimumRevision = Math.max(presented.securityRevision(), canonical.authorizationRevision());
        var snapshotLookup = authority.lookup(canonical.minecraftUuid(), minimumRevision);
        if (snapshotLookup.status() == AuthorityLookupStatus.UNAVAILABLE) throw authorityUnavailable();
        if (snapshotLookup.status() != AuthorityLookupStatus.FOUND) throw notFound();

        AuthoritativeSecuritySnapshot snapshot = snapshotLookup.snapshot();
        if (!snapshot.actorUuid().equals(canonical.minecraftUuid())) throw notFound();
        if (!snapshot.hasPermission(AssistantSecurityCatalog.assistantUsePermission(AssistantId.BUGGY))) throw notFound();
        String panelChannel = AssistantSecurityCatalog.channelPermission(AssistantId.BUGGY, Origin.PANEL).orElseThrow();
        if (!snapshot.hasPermission(panelChannel)) throw notFound();
        var knowledgeRule = AssistantSecurityCatalog.rule(BuggySecurityCapabilities.KNOWLEDGE_READ).orElseThrow();
        for (String permission : knowledgeRule.allCapabilityPermissions()) {
            if (!snapshot.hasPermission(permission)) throw notFound();
        }
        return new VerifiedPanelActor(presented, canonical, snapshot);
    }

    private static boolean bindingMatches(ActorContext presented, ResolvedActorIdentity canonical) {
        if (!presented.actorId().equals(canonical.actorId())) return false;
        if (!presented.networkIdentityId().equals(canonical.networkIdentityId())) return false;
        return presented.minecraftUuid() == null || presented.minecraftUuid().equals(canonical.minecraftUuid());
    }

    private ApiResourceView assistantView() {
        var descriptor = identity.assistantDescriptor();
        return new ApiResourceView(descriptor.assistantId().name(), Map.of(
                "displayName", descriptor.displayName(),
                "enabled", descriptor.enabled(),
                "capabilityProfileId", descriptor.capabilityProfileId(),
                "runtimeProfileId", descriptor.runtimeProfileId(),
                "revision", descriptor.revision()));
    }

    private record VerifiedPanelActor(
            ActorContext actor,
            ResolvedActorIdentity canonical,
            AuthoritativeSecuritySnapshot snapshot) {}

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "API.NOT_FOUND", "Resource not found", false);
    }

    private static ApiException authorityUnavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "API.AUTHORITY_UNAVAILABLE", "Authority unavailable", true);
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "API.SURFACE_NOT_CONFIGURED", "Surface unavailable", true);
    }
}
