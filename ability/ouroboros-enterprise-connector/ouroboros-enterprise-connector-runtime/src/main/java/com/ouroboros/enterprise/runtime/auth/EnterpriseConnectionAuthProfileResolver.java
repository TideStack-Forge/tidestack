package com.ouroboros.enterprise.runtime.auth;

import com.ouroboros.enterprise.auth.EnterpriseConnectionAuthProfileProvider;

import com.ouroboros.enterprise.auth.EnterpriseConnectionAuthProfileRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.externalauth.EnterpriseAuthProfileResolver;
import com.ouroboros.security.externalauth.config.ProviderSpec;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;

/** Bridges a published enterprise connection to the security module's OAuth2 profile contract. */
@Component
public class EnterpriseConnectionAuthProfileResolver implements EnterpriseAuthProfileResolver {

  private static final Logger logger = LoggerFactory.getLogger(EnterpriseConnectionAuthProfileResolver.class);

  private final EnterpriseConnectionCatalog connectionConfigProvider;
  private final Map<String, EnterpriseConnectionAuthProfileProvider> profileProviders;
  private final com.ouroboros.enterprise.EnterpriseApiBudget apiBudget;

  public EnterpriseConnectionAuthProfileResolver(
      EnterpriseConnectionCatalog connectionConfigProvider,
      List<EnterpriseConnectionAuthProfileProvider> profileProviders, com.ouroboros.enterprise.EnterpriseApiBudget apiBudget) {
    this.connectionConfigProvider = connectionConfigProvider;
    this.apiBudget = apiBudget;
    this.profileProviders = profileProviders.stream().collect(Collectors.toUnmodifiableMap(
        EnterpriseConnectionAuthProfileProvider::provider,
        provider -> provider,
        (left, right) -> {
          throw new BusinessException("[" + "ENTERPRISE_CONNECTION_UNAVAILABLE"
              + "] provider auth profile 重复");
        }));
  }

  @Override
  public List<String> connectionIds() {
    return connectionConfigProvider.listSummaries().stream()
        .map(EnterpriseConnectionCatalog.ConnectionSummary::connectionId).toList();
  }

  @Override
  public void beforeRequest(String connectionId, String expectedRevision) {
    var current = connectionConfigProvider.findAuthProfileRequest(connectionId)
        .filter(request -> request.authEnabled() && request.revision().equals(expectedRevision))
        .orElseThrow(() -> new BusinessException("企业认证连接已停用或发生变更"));
    apiBudget.acquire(current.provider());
  }

  @Override
  public Resolution resolve(String connectionId) {
    if (connectionId == null || connectionId.isBlank()) {
      return Resolution.notClaimed();
    }
    Optional<EnterpriseConnectionAuthProfileRequest> request;
    try {
      request = connectionConfigProvider.findAuthProfileRequest(connectionId);
    } catch (RuntimeException ex) {
      logger.warn("Enterprise connection lookup failed closed: connectionId={}, errorType={}",
          connectionId, ex.getClass().getSimpleName());
      return Resolution.claimedWithoutProfile();
    }
    if (request.isEmpty()) {
      return Resolution.notClaimed();
    }
    if (!request.get().authEnabled()) {
      return Resolution.claimedWithoutProfile();
    }
    EnterpriseConnectionAuthProfileProvider profileProvider = profileProviders.get(request.get().provider());
    if (profileProvider == null) {
      return Resolution.claimedWithoutProfile();
    }
    try {
      return profileProvider.resolve(request.get())
          .filter(profile -> validFor(request.get(), profile))
          .map(Resolution::claimed)
          .orElseGet(Resolution::claimedWithoutProfile);
    } catch (RuntimeException ex) {
      logger.warn("Enterprise auth profile rejected: connectionId={}, provider={}, errorType={}",
          connectionId, request.get().provider(), ex.getClass().getSimpleName());
      return Resolution.claimedWithoutProfile();
    }
  }

  private boolean validFor(EnterpriseConnectionAuthProfileRequest request, ProviderSpec profile) {
    if (profile == null
        || !request.connectionId().equals(profile.getKey())
        || profile.getType() == null || profile.getType().isBlank()
        || !profile.isEnabled()
        || !profile.isConnectionScoped()
        || !request.revision().equals(profile.getConnectionProfileRevision())) {
      logger.warn("Enterprise auth profile has an invalid scope: connectionId={}", request.connectionId());
      return false;
    }
    return true;
  }
}
