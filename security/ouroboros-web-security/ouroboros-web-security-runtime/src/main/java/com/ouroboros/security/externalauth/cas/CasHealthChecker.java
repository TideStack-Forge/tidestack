package com.ouroboros.security.externalauth.cas;

import java.net.HttpURLConnection;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ouroboros.security.externalauth.ProviderHealthChecker;
import com.ouroboros.security.externalauth.config.CasProviderSpec;

/**
 * Health checker for CAS providers. Tests reachability of the CAS base URL.
 */
public class CasHealthChecker implements ProviderHealthChecker {

    private static final Logger logger = LoggerFactory.getLogger(CasHealthChecker.class);

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;

    private final CasProviderSpec spec;

    public CasHealthChecker(CasProviderSpec spec) {
        this.spec = spec;
    }

    @Override
    public boolean isHealthy() {
        String checkUrl = spec.getCasBaseUrl();
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
            logger.debug("CAS health check failed for provider {}: {}", spec.getKey(), e.getMessage());
            return false;
        }
    }
}
