package com.ouroboros.security.externalauth;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.ouroboros.security.externalauth.SecurityTokens.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.vavr.control.Either;

import com.ouroboros.cache.CacheManager;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.rbac.entity.UserSsoEntity;
import com.ouroboros.security.rbac.service.UserSsoService;
import com.ouroboros.security.session.UserSession;
import com.ouroboros.security.session.UserSessionManager;
import com.ouroboros.security.session.client.ClientTypeHolder;

/**
 * Orchestrates external auth flow: validate identity → resolve binding → issue token or bindingToken.
 */
@Service
public class ExternalAuthService {

    private static final Logger logger = LoggerFactory.getLogger(ExternalAuthService.class);

    private static final String BINDING_TOKEN_CACHE_NAME = "ouroboros:auth:binding-token";
    private static final long BINDING_TOKEN_TTL_MS = 300_000L; // 5 minutes

    private static final String LOGIN_CODE_CACHE_NAME = "ouroboros:auth:login-code";
    private static final long LOGIN_CODE_TTL_MS = 60_000L; // 60 seconds

    static final int MAX_BINDING_ATTEMPTS = 5;

    @Autowired
    private UserSsoService userSsoService;

    @Autowired
    private UserCenter userCenter;

    @Autowired
    private UserSessionManager<? extends UserSession> userSessionManager;

    @Autowired
    private CacheManager cacheManager;

    @Autowired(required = false)
    private EnterpriseAuthProfileResolver enterpriseAuthProfileResolver;

    /**
     * Process a verified external identity: resolve binding, check user status, issue token or bindingToken.
     *
     * @param identity      the verified external identity
     * @param correlationId tracing id for structured logging
     */
    public Either<AuthError, AuthResult> processExternalIdentity(ExternalIdentity identity, String correlationId) {
        return processExternalIdentity(identity, correlationId, null);
    }

    /** The verified connection revision stays with the login proof, never with the persistent binding. */
    public Either<AuthError, AuthResult> processExternalIdentity(ExternalIdentity identity, String correlationId,
                                                                String connectionProfileRevision) {
        if (!isCurrentConnectionProfile(identity.getProviderKey(), connectionProfileRevision)) {
            return Either.left(AuthError.invalidState("Connection profile changed during authorization"));
        }
        // 1. Lookup binding
        logger.info("[cid={}] Binding lookup: provider={}, externalId={}", correlationId,
            identity.getProviderKey(), identity.getExternalId());
        Optional<UserSsoEntity> binding = userSsoService.findByChannelAndExternalId(
            identity.getProviderKey(), identity.getExternalId());

        if (!binding.isPresent()) {
            // No binding — generate bindingToken and cache the identity with retry counter
            String bindingToken = generateSecureToken();
            BindingTokenEntry entry = new BindingTokenEntry(identity, MAX_BINDING_ATTEMPTS, connectionProfileRevision);
            cacheManager.put(BINDING_TOKEN_CACHE_NAME, bindingToken, entry, BINDING_TOKEN_TTL_MS);
            logger.info("[cid={}] No binding found, bindingToken issued: provider={}", correlationId,
                identity.getProviderKey());
            return Either.right(AuthResult.bindingRequired(bindingToken));
        }

        String userIdentity = binding.get().getUserIdentity();
        logger.info("[cid={}] Binding found: provider={}, userIdentity={}", correlationId,
            identity.getProviderKey(), userIdentity);
        return issueSessionForUserIdentity(userIdentity, correlationId, identity.getProviderKey());
    }

    public Either<AuthError, AuthResult> issueSessionForUserIdentity(String userIdentity, String correlationId) {
        return issueSessionForUserIdentity(userIdentity, correlationId, null);
    }

    private Either<AuthError, AuthResult> issueSessionForUserIdentity(String userIdentity, String correlationId,
                                                                       String providerKeyForLog) {
        Optional<User> user = userCenter.getUser(userIdentity);

        if (!user.isPresent()) {
            if (providerKeyForLog != null) {
                logger.warn("[cid={}] Binding exists but user not found: provider={}, userIdentity={}",
                    correlationId, providerKeyForLog, userIdentity);
            } else {
                logger.warn("[cid={}] User not found: userIdentity={}", correlationId, userIdentity);
            }
            return Either.left(AuthError.userUnavailable("User unavailable"));
        }

        logger.info("[cid={}] User status check: userIdentity={}, enabled={}", correlationId,
            userIdentity, user.get().isEnabled());
        if (!user.get().isEnabled()) {
            return Either.left(AuthError.userUnavailable("User disabled"));
        }

        String clientType = ClientTypeHolder.getClientType();
        return userSessionManager.createSession(user.get(), clientType)
            .mapLeft(e -> new AuthError(50201, "Session creation failed"))
            .map(session -> {
                if (providerKeyForLog != null) {
                    logger.info("[cid={}] Session issued: provider={}, userIdentity={}", correlationId,
                        providerKeyForLog, userIdentity);
                } else {
                    logger.info("[cid={}] Session issued: userIdentity={}", correlationId, userIdentity);
                }
                return AuthResult.loggedIn(session, userIdentity);
            });
    }

    /**
     * Validate a bindingToken: check existence and return cached external identity.
     * Does not consume the token — caller decides whether to consume or decrement.
     */
    public Optional<ExternalIdentity> validateBindingToken(String bindingToken) {
        Optional<Object> cached = cacheManager.get(BINDING_TOKEN_CACHE_NAME, bindingToken);
        if (!cached.isPresent()) {
            return Optional.empty();
        }
        BindingTokenEntry entry = (BindingTokenEntry) cached.get();
        if (!isCurrentConnectionProfile(entry.getExternalIdentity().getProviderKey(), entry.connectionProfileRevision)) {
            cacheManager.evict(BINDING_TOKEN_CACHE_NAME, bindingToken);
            return Optional.empty();
        }
        return Optional.of(entry.getExternalIdentity());
    }

    private boolean isCurrentConnectionProfile(String providerKey, String revision) {
        if (revision == null) {
            return true;
        }
        if (revision.isBlank() || enterpriseAuthProfileResolver == null) {
            return false;
        }
        try {
            var resolution = enterpriseAuthProfileResolver.resolve(providerKey);
            return resolution != null && resolution.connectionClaimed() && resolution.profile()
                .filter(profile -> profile.isEnabled() && profile.isConnectionScoped()
                    && providerKey.equals(profile.getKey())
                    && revision.equals(profile.getConnectionProfileRevision())).isPresent();
        } catch (RuntimeException ex) {
            logger.warn("Connection proof validation failed closed: provider={}, errorType={}",
                providerKey, ex.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Consume (delete) a bindingToken from cache. Called on successful bind.
     */
    public void consumeBindingToken(String bindingToken) {
        cacheManager.evict(BINDING_TOKEN_CACHE_NAME, bindingToken);
    }

    /**
     * Decrement remaining attempts for a bindingToken. Called on failed password verification.
     * If remaining attempts reach 0, the token is auto-invalidated (evicted).
     */
  public void decrementBindingToken(String bindingToken) {
        String lockKey = (BINDING_TOKEN_CACHE_NAME + ":" + bindingToken).intern();
        synchronized (lockKey) {
            Optional<Object> cached = cacheManager.get(BINDING_TOKEN_CACHE_NAME, bindingToken);
            if (!cached.isPresent()) {
                return;
            }
            BindingTokenEntry entry = (BindingTokenEntry) cached.get();
            int remaining = entry.getRemainingAttempts() - 1;
            if (remaining <= 0) {
                cacheManager.evict(BINDING_TOKEN_CACHE_NAME, bindingToken);
                logger.info("BindingToken exhausted all attempts, auto-invalidated");
            } else {
                BindingTokenEntry updated = new BindingTokenEntry(entry.getExternalIdentity(), remaining,
                    entry.connectionProfileRevision);
                cacheManager.put(BINDING_TOKEN_CACHE_NAME, bindingToken, updated, BINDING_TOKEN_TTL_MS);
            }
        }
  }

    /**
     * Generate a one-time loginCode for redirect flows. Stores userIdentity and returnUrl in cache.
     */
    public String generateLoginCode(String userIdentity, String returnUrl) {
        String loginCode = generateSecureToken();
        Map<String, String> entry = new HashMap<>();
        entry.put("userIdentity", userIdentity);
        entry.put("returnUrl", returnUrl);
        cacheManager.put(LOGIN_CODE_CACHE_NAME, loginCode, entry, LOGIN_CODE_TTL_MS);
        return loginCode;
    }

    /**
     * Atomically consume a loginCode. Returns the cached entry (userIdentity + returnUrl) if valid.
     */
    public Optional<Map<String, String>> consumeLoginCode(String loginCode) {
        Optional<Object> cached = cacheManager.getAndEvict(LOGIN_CODE_CACHE_NAME, loginCode);
        if (!cached.isPresent()) {
            return Optional.empty();
        }
        Map<String, String> entry = safeCastToStringMap(cached.get());
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> safeCastToStringMap(Object obj) {
        if (!(obj instanceof Map)) {
            return null;
        }
        Map<?, ?> rawMap = (Map<?, ?>) obj;
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String)) {
                return null;
            }
        }
        return (Map<String, String>) rawMap;
    }

    /**
     * Structured cache entry for bindingToken, containing the external identity and retry counter.
     */
    static class BindingTokenEntry implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        private final ExternalIdentity externalIdentity;
        private final int remainingAttempts;
        private final String connectionProfileRevision;

        BindingTokenEntry(ExternalIdentity externalIdentity, int remainingAttempts) {
            this(externalIdentity, remainingAttempts, null);
        }

        BindingTokenEntry(ExternalIdentity externalIdentity, int remainingAttempts, String connectionProfileRevision) {
            this.externalIdentity = externalIdentity;
            this.remainingAttempts = remainingAttempts;
            this.connectionProfileRevision = connectionProfileRevision;
        }

        ExternalIdentity getExternalIdentity() {
            return externalIdentity;
        }

        int getRemainingAttempts() {
            return remainingAttempts;
        }
    }
}
