package de.legitserver.assistant.security.authn;

/** Initial internal service scopes. */
public final class ServiceScopes {
    public static final ServiceScope INVOKE = new ServiceScope("assistant.invoke");
    public static final ServiceScope SUPERVISOR = new ServiceScope("assistant.supervisor");
    public static final ServiceScope ADMIN = new ServiceScope("assistant.admin");

    private ServiceScopes() {}
}
