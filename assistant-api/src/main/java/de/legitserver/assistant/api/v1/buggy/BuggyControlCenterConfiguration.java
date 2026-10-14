package de.legitserver.assistant.api.v1.buggy;

import de.legitserver.assistant.buggy.controlcenter.BuggyControlCenterReadService;
import de.legitserver.assistant.buggy.service.BuggyIdentityService;
import de.legitserver.assistant.security.authorization.AssistantAuthorizationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** B8 wiring is fail-closed if any authoritative read/security dependency is absent. */
@Configuration(proxyBeanMethods = false)
public class BuggyControlCenterConfiguration {
    @Bean
    @ConditionalOnMissingBean(BuggyControlCenterBackend.class)
    BuggyControlCenterBackend buggyControlCenterBackend(
            ObjectProvider<BuggyControlCenterReadService> readProvider,
            ObjectProvider<BuggyControlCenterExternalReadPort> externalProvider,
            ObjectProvider<AssistantAuthorizationService> authorizationProvider,
            ObjectProvider<BuggyIdentityService> identityProvider) {
        BuggyControlCenterReadService reads = readProvider.getIfAvailable();
        BuggyControlCenterExternalReadPort external = externalProvider.getIfAvailable();
        AssistantAuthorizationService authorization = authorizationProvider.getIfAvailable();
        BuggyIdentityService identities = identityProvider.getIfAvailable();
        if (reads == null || external == null || authorization == null || identities == null) {
            return new UnavailableBuggyControlCenterBackend();
        }
        return new SecuredBuggyControlCenterBackend(reads, external, authorization, identities);
    }
}
