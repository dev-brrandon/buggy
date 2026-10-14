package de.legitserver.assistant.security.authn;

/** Fail-closed result of validating a service credential. */
public record ServiceAuthenticationResult(
        ServiceAuthenticationStatus status, ServicePrincipal principal) {

    public ServiceAuthenticationResult {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (status == ServiceAuthenticationStatus.AUTHENTICATED && principal == null) {
            throw new IllegalArgumentException("authenticated result requires principal");
        }
        if (status != ServiceAuthenticationStatus.AUTHENTICATED && principal != null) {
            throw new IllegalArgumentException("denied result must not expose principal");
        }
    }

    public boolean authenticated() {
        return status == ServiceAuthenticationStatus.AUTHENTICATED;
    }

    public static ServiceAuthenticationResult allow(ServicePrincipal principal) {
        return new ServiceAuthenticationResult(ServiceAuthenticationStatus.AUTHENTICATED, principal);
    }

    public static ServiceAuthenticationResult deny(ServiceAuthenticationStatus status) {
        if (status == ServiceAuthenticationStatus.AUTHENTICATED) {
            throw new IllegalArgumentException("deny status must not be AUTHENTICATED");
        }
        return new ServiceAuthenticationResult(status, null);
    }
}
