package com.ouroboros.security.externalauth;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.cache.CacheManager;
import com.ouroboros.security.externalauth.config.ExternalAuthProperties;
import com.ouroboros.security.externalauth.config.ExternalAuthConfiguration;

/**
 * Rate limiter for binding requests.
 * Per-token dedup + cross-token dimensions (byExternalId, byUsername, bySourceIp).
 */
@Component
public class BindingRateLimiter {

    private static final String DEDUP_CACHE = "ouroboros:auth:bind-dedup";
    private static final String RATE_EXTERNAL_ID_CACHE = "ouroboros:auth:bind-rate:external-id";
    private static final String RATE_USERNAME_CACHE = "ouroboros:auth:bind-rate:username";
    private static final String RATE_SOURCE_IP_CACHE = "ouroboros:auth:bind-rate:source-ip";

    private static final long DEDUP_TTL_MS = 1000L;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ExternalAuthConfiguration properties;



    /**
     * Check per-token dedup. Returns error message if rate-limited.
     */
    public Optional<String> checkTokenDedup(String bindingToken) {
        Optional<Object> existing = cacheManager.get(DEDUP_CACHE, bindingToken);
        if (existing.isPresent()) {
            return Optional.of("Too many requests for this token");
        }
        cacheManager.put(DEDUP_CACHE, bindingToken, 1, DEDUP_TTL_MS);
        return Optional.empty();
    }

    /**
     * Check cross-token rate limits. Returns error message if rate-limited.
     */
    public Optional<String> checkCrossTokenLimits(String providerKey, String externalId,
                                                   String username, String sourceIp) {
        RateLimitPolicy policy = resolvePolicy(providerKey);
        if (checkDimension(RATE_EXTERNAL_ID_CACHE, providerKey + ":" + externalId, policy.byExternalId)) {
            return Optional.of("Rate limit exceeded for external identity");
        }
        if (checkDimension(RATE_USERNAME_CACHE, providerKey + ":" + username, policy.byUsername)) {
            return Optional.of("Rate limit exceeded for username");
        }
        if (policy.sourceIpEnabled && sourceIp != null
            && checkDimension(RATE_SOURCE_IP_CACHE, providerKey + ":" + sourceIp, policy.bySourceIp)) {
            return Optional.of("Rate limit exceeded for source IP");
        }
        return Optional.empty();
    }

    private RateLimitPolicy resolvePolicy(String providerKey) {
        ExternalAuthProperties.BindingRateLimitConfig config = properties.get().getBindingRateLimit();
        RateLimitPolicy defaultPolicy = buildDefaultPolicy(config);
        return providerKey == null ? defaultPolicy
            : buildProviderPolicies(config).getOrDefault(providerKey, defaultPolicy);
    }

    private RateLimitPolicy buildDefaultPolicy(ExternalAuthProperties.BindingRateLimitConfig config) {
        RateLimitThreshold byExternalId = RateLimitThreshold.parse(config.getByExternalId());
        RateLimitThreshold byUsername = RateLimitThreshold.parse(config.getByUsername());
        boolean sourceIpEnabled = config.isSourceIpEnabled();
        RateLimitThreshold bySourceIp = sourceIpEnabled
            ? RateLimitThreshold.parse(config.getBySourceIp())
            : null;
        return new RateLimitPolicy(byExternalId, byUsername, bySourceIp, sourceIpEnabled);
    }

    private Map<String, RateLimitPolicy> buildProviderPolicies(ExternalAuthProperties.BindingRateLimitConfig config) {
        Map<String, ExternalAuthProperties.ProviderRateLimitOverrideConfig> providerConfigMap = config.getProviders();
        if (providerConfigMap == null || providerConfigMap.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, RateLimitPolicy> result = new HashMap<>();
        for (Map.Entry<String, ExternalAuthProperties.ProviderRateLimitOverrideConfig> entry : providerConfigMap.entrySet()) {
            String providerKey = entry.getKey();
            if (providerKey == null || providerKey.trim().isEmpty()) {
                continue;
            }
            ExternalAuthProperties.ProviderRateLimitOverrideConfig override = entry.getValue();
            if (override == null) {
                continue;
            }
            String externalIdRule = hasText(override.getByExternalId()) ? override.getByExternalId() : config.getByExternalId();
            String usernameRule = hasText(override.getByUsername()) ? override.getByUsername() : config.getByUsername();
            boolean sourceIpEnabled = override.getSourceIpEnabled() != null
                ? override.getSourceIpEnabled()
                : config.isSourceIpEnabled();
            String sourceIpRule = hasText(override.getBySourceIp()) ? override.getBySourceIp() : config.getBySourceIp();
            RateLimitThreshold bySourceIp = sourceIpEnabled ? RateLimitThreshold.parse(sourceIpRule) : null;
            result.put(providerKey, new RateLimitPolicy(
                RateLimitThreshold.parse(externalIdRule),
                RateLimitThreshold.parse(usernameRule),
                bySourceIp,
                sourceIpEnabled
            ));
        }
        return result.isEmpty() ? Collections.emptyMap() : result;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean checkDimension(String cacheName, String key, RateLimitThreshold threshold) {
        String lockKey = (cacheName + ":" + key).intern();
        synchronized (lockKey) {
            Optional<Object> existing = cacheManager.get(cacheName, key);
            if (!existing.isPresent()) {
                cacheManager.put(cacheName, key, 1, threshold.getWindowMillis());
                return false;
            }
            int counter = ((Number) existing.get()).intValue();
            if (counter >= threshold.getCount()) {
                return true;
            }
            cacheManager.put(cacheName, key, counter + 1, threshold.getWindowMillis());
            return false;
        }
    }

    private static class RateLimitPolicy {
        private final RateLimitThreshold byExternalId;
        private final RateLimitThreshold byUsername;
        private final RateLimitThreshold bySourceIp;
        private final boolean sourceIpEnabled;

        private RateLimitPolicy(RateLimitThreshold byExternalId, RateLimitThreshold byUsername,
                                RateLimitThreshold bySourceIp, boolean sourceIpEnabled) {
            this.byExternalId = byExternalId;
            this.byUsername = byUsername;
            this.bySourceIp = bySourceIp;
            this.sourceIpEnabled = sourceIpEnabled;
        }
    }
}
