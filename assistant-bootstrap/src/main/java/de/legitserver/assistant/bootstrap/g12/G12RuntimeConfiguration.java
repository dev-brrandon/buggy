package de.legitserver.assistant.bootstrap.g12;

import de.legitserver.assistant.api.v1.backend.PanelApiReadPort;
import de.legitserver.assistant.api.v1.backend.PanelAssistantResolutionPort;
import de.legitserver.assistant.api.v1.backend.PanelPipelineOperationPolicy;
import de.legitserver.assistant.api.v1.backend.PanelPipelineOperationPolicyPort;
import de.legitserver.assistant.buggy.channel.BuggyChannelExecutionPort;
import de.legitserver.assistant.buggy.runtime.g1.BuggyNativeConversationPipelineExecution;
import de.legitserver.assistant.buggy.runtime.g1.FoundationBuggyChannelExecution;
import de.legitserver.assistant.buggy.conversation.BuggyConversationContextBuilder;
import de.legitserver.assistant.buggy.conversation.BuggyConversationRole;
import de.legitserver.assistant.buggy.conversation.BuggyConversationTurn;
import de.legitserver.assistant.buggy.conversation.BuggyNaturalConversationEngine;
import de.legitserver.assistant.buggy.identity.BuggyBootstrapSeed;
import de.legitserver.assistant.buggy.memory.BuggyMemoryContentPolicy;
import de.legitserver.assistant.buggy.repository.BuggyDevelopmentRepository;
import de.legitserver.assistant.buggy.repository.BuggyMemoryRepository;
import de.legitserver.assistant.buggy.service.BuggyDevelopmentService;
import de.legitserver.assistant.buggy.service.BuggyIdentityService;
import de.legitserver.assistant.buggy.service.BuggyMemoryService;
import de.legitserver.assistant.buggy.service.BuggyIdentityUnavailableException;
import de.legitserver.assistant.buggy.service.BuggyNativeCoreService;
import de.legitserver.assistant.buggy.service.BuggyNativeCoreUnavailableException;
import de.legitserver.assistant.conversations.model.ConversationContextType;
import de.legitserver.assistant.conversations.model.ConversationId;
import de.legitserver.assistant.conversations.model.MessageAuthorType;
import de.legitserver.assistant.conversations.model.MessageRedactionState;
import de.legitserver.assistant.conversations.model.NewConversationMessage;
import de.legitserver.assistant.conversations.policy.ConversationExpiryPolicy;
import de.legitserver.assistant.conversations.policy.ConversationLimits;
import de.legitserver.assistant.conversations.policy.ConversationStateMachine;
import de.legitserver.assistant.conversations.repository.ConversationRepository;
import de.legitserver.assistant.conversations.service.ConversationService;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.integrations.buggy.FoundationSecretGuardedMemoryContentPolicy;
import de.legitserver.assistant.integrations.buggy.JdbcBuggyDevelopmentRepository;
import de.legitserver.assistant.integrations.buggy.JdbcBuggyIdentityRepository;
import de.legitserver.assistant.integrations.buggy.JdbcBuggyMemoryRepository;
import de.legitserver.assistant.integrations.buggy.JdbcBuggyNativeCoreRepository;
import de.legitserver.assistant.integrations.conversations.JdbcConversationRepository;
import de.legitserver.assistant.integrations.networkcore.HttpNetworkCoreAuthorityClient;
import de.legitserver.assistant.integrations.networkcore.NetworkCoreAuthorityClient;
import de.legitserver.assistant.integrations.networkcore.NetworkCoreAuthorizationAdapter;
import de.legitserver.assistant.observability.audit.AuditRecorder;
import de.legitserver.assistant.observability.health.DependencyHealthRegistry;
import de.legitserver.assistant.observability.metrics.AssistantMetrics;
import de.legitserver.assistant.panel.security.HmacPanelPipelineCredentialIssuer;
import de.legitserver.assistant.panel.security.PanelPipelineCredentialIssuer;
import de.legitserver.assistant.persistence.transaction.JdbcTransactionRunner;
import de.legitserver.assistant.persistence.transaction.TransactionRunner;
import de.legitserver.assistant.pipeline.RuntimeRequestPipeline;
import de.legitserver.assistant.pipeline.audit.PipelineAudit;
import de.legitserver.assistant.pipeline.control.BoundedAdmissionController;
import de.legitserver.assistant.pipeline.control.IdempotencyCoordinator;
import de.legitserver.assistant.pipeline.control.PipelineCancellationRegistry;
import de.legitserver.assistant.pipeline.conversation.RepositoryConversationResolver;
import de.legitserver.assistant.pipeline.model.PipelineExecutionOutput;
import de.legitserver.assistant.pipeline.model.PipelineOperation;
import de.legitserver.assistant.pipeline.model.PipelineRoute;
import de.legitserver.assistant.pipeline.model.PipelineRouteType;
import de.legitserver.assistant.pipeline.policy.RuntimePipelinePolicy;
import de.legitserver.assistant.pipeline.policy.SlidingWindowRateLimiter;
import de.legitserver.assistant.pipeline.response.DefaultPipelineFormatter;
import de.legitserver.assistant.pipeline.response.DefaultPipelineResponseSecurity;
import de.legitserver.assistant.pipeline.routing.RegisteredPipelineRouter;
import de.legitserver.assistant.pipeline.routing.StaticPipelineCapabilityResolver;
import de.legitserver.assistant.pipeline.security.AuthoritativePipelineActorResolver;
import de.legitserver.assistant.pipeline.security.HmacPipelineAuthentication;
import de.legitserver.assistant.pipeline.security.Phase3IngressAuthorization;
import de.legitserver.assistant.security.authn.BoundedReplayProtector;
import de.legitserver.assistant.security.authn.HmacServiceTokenService;
import de.legitserver.assistant.security.authorization.AssistantAuthorizationService;
import de.legitserver.assistant.security.authorization.AuthorizationRequest;
import de.legitserver.assistant.security.authorization.AuthorityLookupStatus;
import de.legitserver.assistant.security.authorization.ResourceAuthorizationStatus;
import de.legitserver.assistant.security.authorization.StaticAssistantCapabilityPolicy;
import de.legitserver.assistant.security.authorization.buggy.BuggySecurityCapabilities;
import de.legitserver.assistant.security.control.InMemorySecurityControlPlane;
import de.legitserver.assistant.security.control.SecurityFeature;
import de.legitserver.assistant.security.secret.SecretGuard;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * G1.2 production composition. It activates only with the Assistant PostgreSQL runtime configured;
 * missing NetworkCore authority stays fail-closed at request time. G1.6 conversation is native and model-free.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("'${LEGIT_ASSISTANT_POSTGRES_URL:}' != '' && '${LEGIT_ASSISTANT_POSTGRES_USER:}' != ''")
public class G12RuntimeConfiguration {
    private static final PipelineOperation CONVERSATION_CREATE = new PipelineOperation("assistant.conversation.create");
    private static final PipelineOperation CONVERSATION_MESSAGE = new PipelineOperation("assistant.conversation.message.append");

    @Bean
    DataSource g12DataSource(Environment environment) {
        return new G12DriverManagerDataSource(
                environment.getRequiredProperty("LEGIT_ASSISTANT_POSTGRES_URL"),
                environment.getRequiredProperty("LEGIT_ASSISTANT_POSTGRES_USER"),
                environment.getProperty("LEGIT_ASSISTANT_POSTGRES_PASSWORD", ""));
    }

    @Bean
    TransactionRunner g12Transactions(DataSource dataSource) {
        return new JdbcTransactionRunner(dataSource);
    }

    @Bean
    ConversationRepository g12ConversationRepository(TransactionRunner transactions) {
        return new JdbcConversationRepository(transactions);
    }

    @Bean
    ConversationService g12ConversationService(ConversationRepository repository, Clock clock) {
        return new ConversationService(
                repository,
                new ConversationStateMachine(),
                new ConversationExpiryPolicy(),
                ConversationLimits.defaults(),
                clock);
    }

    @Bean
    BuggyIdentityService g12BuggyIdentityService(TransactionRunner transactions) {
        return new BuggyIdentityService(new JdbcBuggyIdentityRepository(transactions));
    }

    @Bean
    BuggyNativeCoreService g14BuggyNativeCoreService(TransactionRunner transactions) {
        return new BuggyNativeCoreService(new JdbcBuggyNativeCoreRepository(transactions));
    }

    @Bean
    BuggyMemoryContentPolicy g15BuggyMemoryContentPolicy() {
        return new FoundationSecretGuardedMemoryContentPolicy(new SecretGuard());
    }

    @Bean
    BuggyMemoryRepository g15BuggyMemoryRepository(
            TransactionRunner transactions, BuggyMemoryContentPolicy contentPolicy) {
        return new JdbcBuggyMemoryRepository(transactions, contentPolicy);
    }

    @Bean
    BuggyDevelopmentRepository g15BuggyDevelopmentRepository(
            TransactionRunner transactions, BuggyMemoryContentPolicy contentPolicy) {
        return new JdbcBuggyDevelopmentRepository(transactions, contentPolicy);
    }

    @Bean
    BuggyMemoryService g15BuggyMemoryService(
            BuggyIdentityService identity,
            BuggyMemoryRepository memoryRepository,
            BuggyMemoryContentPolicy contentPolicy,
            Clock clock) {
        return new BuggyMemoryService(identity, memoryRepository, contentPolicy, clock);
    }

    @Bean
    BuggyDevelopmentService g15BuggyDevelopmentService(
            BuggyNativeCoreService nativeCore,
            BuggyMemoryRepository memoryRepository,
            BuggyDevelopmentRepository developmentRepository,
            BuggyMemoryContentPolicy contentPolicy,
            Clock clock) {
        return new BuggyDevelopmentService(
                nativeCore, memoryRepository, developmentRepository, contentPolicy, clock);
    }

    @Bean
    ApplicationRunner g12BuggyIdentityBootstrap(
            BuggyIdentityService identity,
            BuggyNativeCoreService nativeCore,
            Environment environment,
            Clock clock) {
        return arguments -> {
            String configuredOwner = environment.getRequiredProperty("LEGIT_ASSISTANT_BUGGY_PRIMARY_OWNER_ACTOR_ID").strip();
            ActorId owner = new ActorId(UUID.fromString(configuredOwner).toString());
            try {
                var existing = identity.loadRequired();
                if (!existing.ownerBinding().ownerActorId().equals(owner)) {
                    throw new IllegalStateException("Configured Buggy PRIMARY_OWNER does not match persistent identity");
                }
            } catch (BuggyIdentityUnavailableException notProvisioned) {
                identity.initialize(BuggyBootstrapSeed.create(
                        owner,
                        new ActorId("system:bootstrap"),
                        clock.instant()));
            }

            try {
                nativeCore.loadRequired();
            } catch (BuggyNativeCoreUnavailableException notBorn) {
                nativeCore.initializeFirstActivation(clock.instant());
            }
        };
    }

    @Bean
    NetworkCoreAuthorityClient g12NetworkCoreAuthorityClient(Clock clock) {
        NetworkCoreAuthorityClient configured = HttpNetworkCoreAuthorityClient.fromEnvironment(clock);
        if (configured != null) return configured;
        return new NetworkCoreAuthorityClient() {
            @Override public de.legitserver.assistant.integrations.networkcore.NetworkCoreActorLookup resolveActor(
                    de.legitserver.assistant.core.actor.ActorContext presentedActor) {
                return new de.legitserver.assistant.integrations.networkcore.NetworkCoreActorLookup(AuthorityLookupStatus.UNAVAILABLE, null);
            }
            @Override public de.legitserver.assistant.integrations.networkcore.NetworkCoreSecurityLookup securitySnapshot(
                    java.util.UUID actorUuid, long minimumRevision) {
                return new de.legitserver.assistant.integrations.networkcore.NetworkCoreSecurityLookup(AuthorityLookupStatus.UNAVAILABLE, null);
            }
        };
    }

    @Bean
    NetworkCoreAuthorizationAdapter g12NetworkCoreAuthorizationAdapter(
            NetworkCoreAuthorityClient authorityClient, AssistantMetrics metrics) {
        return new NetworkCoreAuthorizationAdapter(authorityClient, metrics);
    }

    @Bean(destroyMethod = "close")
    G12DependencyHealthMonitor g12DependencyHealthMonitor(
            DataSource dataSource,
            NetworkCoreAuthorityClient authorityClient,
            DependencyHealthRegistry dependencyHealth,
            Clock clock) {
        return new G12DependencyHealthMonitor(dataSource, authorityClient, dependencyHealth, clock).start();
    }

    @Bean
    HmacServiceTokenService g12InternalPipelineTokens() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return new HmacServiceTokenService(key, new BoundedReplayProtector(16_384));
    }

    @Bean
    PanelPipelineCredentialIssuer g12PanelPipelineCredentialIssuer(
            HmacServiceTokenService g12InternalPipelineTokens, Clock clock) {
        return new HmacPanelPipelineCredentialIssuer(g12InternalPipelineTokens, clock, Duration.ofSeconds(60));
    }

    @Bean
    InMemorySecurityControlPlane g12SecurityControlPlane(Clock clock) {
        return new InMemorySecurityControlPlane(EnumSet.allOf(SecurityFeature.class), clock.instant());
    }

    @Bean(destroyMethod = "shutdownNow")
    ExecutorService g12PipelineExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    RegisteredPipelineRouter g12Routes(
            ConversationService conversations,
            ConversationRepository conversationRepository,
            BuggyNativeConversationPipelineExecution nativeConversationExecution) {
        RegisteredPipelineRouter routes = new RegisteredPipelineRouter();
        routes.register(
                CONVERSATION_CREATE,
                new PipelineRoute("route.buggy.conversation.create", PipelineRouteType.DOMAIN,
                        BuggySecurityCapabilities.KNOWLEDGE_READ, Set.of(), Duration.ofSeconds(5)),
                context -> {
                    var conversation = conversations.create(
                            AssistantId.BUGGY,
                            context.actor().actorId(),
                            context.actor().origin(),
                            ConversationContextType.GENERAL,
                            context.request().inputClassification(),
                            Map.of("channel", String.valueOf(context.request().input().getOrDefault("channel", "WEB"))));
                    return new PipelineExecutionOutput(
                            Map.of("id", conversation.conversationId().toString(), "state", conversation.state().name()),
                            DataClassification.INTERNAL);
                });
        routes.register(
                CONVERSATION_MESSAGE,
                new PipelineRoute("route.buggy.conversation.message", PipelineRouteType.DOMAIN,
                        BuggySecurityCapabilities.KNOWLEDGE_READ, Set.of(), Duration.ofSeconds(45)),
                context -> {
                    if (context.conversation() == null) throw new IllegalStateException("conversation required");
                    Object raw = context.request().input().get("content");
                    if (!(raw instanceof String content) || content.isBlank()) throw new IllegalArgumentException("content required");
                    long messageCount = conversationRepository.countMessages(context.conversation().conversationId());
                    long afterSequence = Math.max(0L, messageCount - 12L);
                    var persisted = conversationRepository.listMessages(
                            context.conversation().conversationId(), afterSequence, 12);
                    ArrayList<BuggyConversationTurn> history = new ArrayList<>();
                    for (var message : persisted) {
                        if (message.redactionState() == MessageRedactionState.QUARANTINED) continue;
                        if (!context.request().inputClassification().isAtLeast(message.classification())) continue;
                        BuggyConversationRole role = message.authorType() == MessageAuthorType.HUMAN
                                ? BuggyConversationRole.HUMAN : BuggyConversationRole.ASSISTANT;
                        history.add(new BuggyConversationTurn(role, message.content()));
                    }
                    PipelineExecutionOutput generated = nativeConversationExecution.executeWithContext(context, history);
                    Object response = generated.values().get("response");
                    if (!(response instanceof String reply) || reply.isBlank()) throw new IllegalStateException("empty inference response");
                    var human = conversations.append(
                            context.conversation().conversationId(),
                            context.conversation().revision(),
                            new NewConversationMessage(
                                    MessageAuthorType.HUMAN,
                                    context.actor().actorId(),
                                    content,
                                    context.request().inputClassification(),
                                    MessageRedactionState.NONE,
                                    Map.of("channel", String.valueOf(context.request().input().getOrDefault("channel", "WEB")))));
                    conversations.append(
                            human.conversation().conversationId(),
                            human.conversation().revision(),
                            new NewConversationMessage(
                                    MessageAuthorType.ASSISTANT,
                                    null,
                                    reply,
                                    generated.classification(),
                                    MessageRedactionState.NONE,
                                    Map.of("channel", "WEB")));
                    LinkedHashMap<String, Object> output = new LinkedHashMap<>(generated.values());
                    output.put("id", human.conversation().conversationId().toString());
                    return new PipelineExecutionOutput(output, generated.classification());
                });
        routes.register(
                FoundationBuggyChannelExecution.OPERATION,
                new PipelineRoute("route.buggy.channel.respond", PipelineRouteType.DOMAIN,
                        BuggySecurityCapabilities.KNOWLEDGE_READ, Set.of(), Duration.ofSeconds(45)),
                nativeConversationExecution);
        return routes;
    }

    @Bean
    BuggyNativeConversationPipelineExecution g16BuggyNativeConversationExecution() {
        return new BuggyNativeConversationPipelineExecution(
                new BuggyConversationContextBuilder(),
                BuggyNaturalConversationEngine.canonical());
    }

    @Bean
    RuntimeRequestPipeline g12RuntimeRequestPipeline(
            NetworkCoreAuthorizationAdapter authority,
            InMemorySecurityControlPlane controls,
            RegisteredPipelineRouter routes,
            ConversationRepository conversations,
            HmacServiceTokenService g12InternalPipelineTokens,
            DependencyHealthRegistry dependencyHealth,
            AssistantMetrics metrics,
            AuditRecorder auditRecorder,
            ExecutorService g12PipelineExecutor,
            Clock clock) {
        StaticAssistantCapabilityPolicy capabilities = new StaticAssistantCapabilityPolicy(
                Map.of(AssistantId.BUGGY, BuggySecurityCapabilities.all()));
        AssistantAuthorizationService authorization = new AssistantAuthorizationService(
                authority,
                authority,
                capabilities,
                (actor, snapshot, capability, resource) -> {
                    if (!BuggySecurityCapabilities.KNOWLEDGE_READ.equals(capability) || resource == null) {
                        return ResourceAuthorizationStatus.DENY;
                    }
                    return switch (resource.resourceType()) {
                        case "conversation", "buggy-conversation" -> ResourceAuthorizationStatus.ALLOW;
                        default -> ResourceAuthorizationStatus.DENY;
                    };
                },
                controls);
        RuntimePipelinePolicy policy = new RuntimePipelinePolicy(
                context -> authorization.authorize(new AuthorizationRequest(
                        context.servicePrincipal(),
                        context.actor(),
                        context.request().assistant(),
                        context.capabilityId(),
                        context.request().resource())),
                dependencyHealth,
                new SlidingWindowRateLimiter(30, Duration.ofMinutes(1), 8192, clock),
                metrics);
        StaticPipelineCapabilityResolver capabilityResolver = new StaticPipelineCapabilityResolver(Map.of(
                CONVERSATION_CREATE, BuggySecurityCapabilities.KNOWLEDGE_READ,
                CONVERSATION_MESSAGE, BuggySecurityCapabilities.KNOWLEDGE_READ,
                FoundationBuggyChannelExecution.OPERATION, BuggySecurityCapabilities.KNOWLEDGE_READ));
        return new RuntimeRequestPipeline(
                new HmacPipelineAuthentication(g12InternalPipelineTokens),
                new AuthoritativePipelineActorResolver(authority),
                new Phase3IngressAuthorization(authority, controls),
                new RepositoryConversationResolver(conversations),
                capabilityResolver,
                routes,
                policy,
                routes,
                new DefaultPipelineResponseSecurity(),
                new DefaultPipelineFormatter(),
                new PipelineAudit(auditRecorder, clock),
                new BoundedAdmissionController(16, 64, Duration.ofMillis(25), metrics),
                new IdempotencyCoordinator(Duration.ofMinutes(10), 8192, clock),
                new PipelineCancellationRegistry(),
                g12PipelineExecutor,
                metrics,
                (requestId, stage) -> { },
                clock,
                Duration.ofMillis(500));
    }

    @Bean
    BuggyChannelExecutionPort g12BuggyChannelExecution(
            RuntimeRequestPipeline pipeline,
            BuggyIdentityService identity,
            HmacServiceTokenService g12InternalPipelineTokens,
            Clock clock) {
        return new FoundationBuggyChannelExecution(pipeline, identity, g12InternalPipelineTokens, clock);
    }

    @Bean
    PanelAssistantResolutionPort g12PanelAssistantResolution(
            BuggyIdentityService identity,
            ConversationRepository conversations) {
        return (context, operation, resourceId, request) -> {
            if (resourceId == null) {
                Object requested = request.data().get("assistantId");
                if (requested != null && !"buggy".equalsIgnoreCase(String.valueOf(requested))) return null;
                return identity.assistantDescriptor();
            }
            try {
                var conversation = conversations.findById(ConversationId.parse(resourceId)).orElse(null);
                return conversation != null && conversation.assistantId() == AssistantId.BUGGY
                        ? identity.assistantDescriptor() : null;
            } catch (RuntimeException invalid) {
                return null;
            }
        };
    }

    @Bean
    PanelPipelineOperationPolicyPort g12PanelOperationPolicies() {
        return operation -> CONVERSATION_CREATE.equals(operation) || CONVERSATION_MESSAGE.equals(operation)
                ? PanelPipelineOperationPolicy.internalDefault()
                : null;
    }

    @Bean
    PanelApiReadPort g12PanelReadPort(
            BuggyIdentityService identity,
            ConversationRepository conversations,
            NetworkCoreAuthorizationAdapter authority) {
        return new G12PanelReadPort(identity, conversations, authority);
    }
}
