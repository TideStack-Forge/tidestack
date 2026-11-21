package com.ouroboros.security.externalauth;

/**
 * Health check probe for external authentication providers.
 */
public interface ProviderHealthChecker {

    /**
     * @return true if the provider's external system is reachable
     */
    boolean isHealthy();
}
