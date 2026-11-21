package com.ouroboros.security.externalauth.oauth2;

import java.net.HttpURLConnection;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ouroboros.security.externalauth.ProviderHealthChecker;
import com.ouroboros.security.externalauth.config.OAuth2ProviderSpec;

/**
 * Health checker for OAuth2 providers. Tests reachability of the authorization URI.
 */
public class OAuth2HealthChecker implements ProviderHealthChecker {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2HealthChecker.class);

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;

    private final OAuth2ProviderSpec spec;

    public OAuth2HealthChecker(OAuth2ProviderSpec spec) {
        this.spec = spec;
    }

    @Override
    public boolean isHealthy() {
        String checkUrl = resolveCheckUrl();
        if (checkUrl == null || checkUrl.isEmpty()) {
            return false;
        }
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(checkUrl).openConnection();
            try {
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setInstanceFollowRedirects(false);
                conn.getResponseCode();
                return true;
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            logger.debug("OAuth2 health check failed for provider {}: {}", spec.getKey(), e.getMessage());
            return false;
        }
    }

    private String resolveCheckUrl() {
        String authorizationUri = spec.getAuthorizationUri();
        if (authorizationUri != null && !authorizationUri.isEmpty()) {
            return authorizationUri;
        }
        return spec.getJwksUri();
    }
}
