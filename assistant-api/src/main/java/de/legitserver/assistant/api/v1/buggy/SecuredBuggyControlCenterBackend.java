package de.legitserver.assistant.api.v1.buggy;

import de.legitserver.assistant.api.v1.error.ApiException;
import de.legitserver.assistant.api.v1.model.ApiCallContext;
import de.legitserver.assistant.api.v1.model.ApiResourceView;
import de.legitserver.assistant.api.v1.pagination.ApiPage;
import de.legitserver.assistant.api.v1.pagination.ApiPageRequest;
import de.legitserver.assistant.buggy.config.BuggyManifest;
import de.legitserver.assistant.buggy.controlcenter.BuggyControlCenterReadService;
import de.legitserver.assistant.buggy.controlcenter.BuggyControlCenterResource;
import de.legitserver.assistant.buggy.controlcenter.BuggyControlCenterSurface;
import de.legitserver.assistant.buggy.identity.BuggyIdentitySnapshot;
import de.legitserver.assistant.buggy.service.BuggyIdentityService;
import de.legitserver.assistant.core.assistant.AssistantDescriptor;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.security.authorization.AssistantAuthorizationService;
import de.legitserver.assistant.security.authorization.AuthorizationDecision;
import de.legitserver.assistant.security.authorization.AuthorizationRequest;
import de.legitserver.assistant.security.authorization.ResourceRef;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;

/**
 * B8 server-side authorization and projection adapter. Signed Panel identity is only an identity assertion;
 * effective permissions are always re-resolved through the Foundation authorization service.
 */
public final class SecuredBuggyControlCenterBackend implements BuggyControlCenterBackend {
    private final BuggyControlCenterReadService reads;
    private final BuggyControlCenterExternalReadPort externalReads;
    private final AssistantAuthorizationService authorization;
    private final BuggyIdentityService identities;

    public SecuredBuggyControlCenterBackend(
            BuggyControlCenterReadService reads,
            BuggyControlCenterExternalReadPort externalReads,
            AssistantAuthorizationService authorization,
            BuggyIdentityService identities) {
        this.reads = Objects.requireNonNull(reads, "reads");
        this.externalReads = Objects.requireNonNull(externalReads, "externalReads");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.identities = Objects.requireNonNull(identities, "identities");
    }

    @Override public ApiResourceView overview(ApiCallContext c) {
        authorize(c, BuggyControlCenterSurface.OVERVIEW);
        ApiResourceView local = view(reads.overview());
        ApiResourceView external = externalReads.summary(c);
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>(local.attributes());
        external.attributes().forEach((key, value) -> merged.putIfAbsent("foundation." + key, value));
        return new ApiResourceView(local.id(), merged);
    }
    @Override public ApiResourceView identity(ApiCallContext c) { authorize(c, BuggyControlCenterSurface.IDENTITY); return view(reads.identity()); }
    @Override public ApiResourceView personality(ApiCallContext c) { authorize(c, BuggyControlCenterSurface.PERSONALITY); return view(reads.personality()); }
    @Override public ApiPage<ApiResourceView> tasks(ApiCallContext c, ApiPageRequest p) { authorize(c, BuggyControlCenterSurface.TASKS); return page(reads.tasks(500), p); }
    @Override public ApiPage<ApiResourceView> activity(ApiCallContext c, ApiPageRequest p) { authorize(c, BuggyControlCenterSurface.ACTIVITY); return page(reads.activity(500), p); }
    @Override public ApiPage<ApiResourceView> approvals(ApiCallContext c, ApiPageRequest p) { authorize(c, BuggyControlCenterSurface.APPROVALS); return externalReads.approvals(c, p); }
    @Override public ApiPage<ApiResourceView> incidents(ApiCallContext c, ApiPageRequest p) { authorize(c, BuggyControlCenterSurface.INCIDENTS); return externalReads.incidents(c, p); }
    @Override public ApiPage<ApiResourceView> knowledge(ApiCallContext c, String q, ApiPageRequest p) { authorize(c, BuggyControlCenterSurface.KNOWLEDGE); return externalReads.knowledge(c, q, p); }
    @Override public ApiPage<ApiResourceView> selfDevelopment(ApiCallContext c, ApiPageRequest p) { authorize(c, BuggyControlCenterSurface.SELF_DEVELOPMENT); return page(reads.selfDevelopment(500), p); }
    @Override public ApiPage<ApiResourceView> automations(ApiCallContext c, ApiPageRequest p) { authorize(c, BuggyControlCenterSurface.AUTOMATIONS); return page(reads.automations(500), p); }
    @Override public ApiPage<ApiResourceView> tools(ApiCallContext c, ApiPageRequest p) { authorize(c, BuggyControlCenterSurface.TOOLS); return page(reads.tools(), p); }
    @Override public ApiResourceView security(ApiCallContext c) { authorize(c, BuggyControlCenterSurface.SECURITY); return view(reads.security()); }
    @Override public ApiPage<ApiResourceView> audit(ApiCallContext c, String q, ApiPageRequest p) { authorize(c, BuggyControlCenterSurface.AUDIT); return externalReads.audit(c, q, p); }

    private void authorize(ApiCallContext context, BuggyControlCenterSurface surface) {
        var verified = context.requirePanelRequest();
        BuggyIdentitySnapshot identity = identities.loadRequired();
        BuggyManifest manifest = identity.configuration().manifest();
        AssistantDescriptor descriptor = new AssistantDescriptor(
                identity.identityCore().assistantId(), identity.identityCore().displayName(), true,
                manifest.capabilityProfileId(), manifest.policyProfileId(), manifest.knowledgeProfileId(),
                manifest.runtimeProfileId(), manifest.personalityProfileId(), identity.headRevision(), identity.updatedAt());
        AuthorizationDecision decision = authorization.authorize(new AuthorizationRequest(
                verified.servicePrincipal(), verified.presentedActor(), descriptor, surface.capabilityId(),
                new ResourceRef("buggy-control-center", surface.name().toLowerCase(java.util.Locale.ROOT), DataClassification.RESTRICTED)));
        if (!decision.allowed()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "API.BUGGY_CONTROL_CENTER_DENIED",
                    "Buggy Control Center access denied", false,
                    Map.of("safeCode", decision.denialReason().name(), "surface", surface.name()));
        }
    }

    private static ApiResourceView view(BuggyControlCenterResource resource) {
        return new ApiResourceView(resource.id(), resource.attributes());
    }

    private static ApiPage<ApiResourceView> page(List<BuggyControlCenterResource> source, ApiPageRequest request) {
        return ApiPage.slice(source.stream().map(SecuredBuggyControlCenterBackend::view).toList(), request);
    }
}
