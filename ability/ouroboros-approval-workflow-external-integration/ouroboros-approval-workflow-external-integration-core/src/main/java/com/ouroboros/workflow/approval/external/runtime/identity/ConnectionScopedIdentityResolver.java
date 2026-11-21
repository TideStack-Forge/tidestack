package com.ouroboros.workflow.approval.external.runtime.identity;

import java.util.Optional;

import com.ouroboros.security.identity.mapping.EnterpriseIdentityMappingService;
import com.ouroboros.security.identity.mapping.PlatformPrincipalRef;

/**
 * Adapts the shared connection-scoped identity port to the approval ACL.
 *
 * <p>The runtime intentionally knows neither the identity binding entity nor
 * the storage used by a provider. A provider-specific implementation can be
 * supplied behind {@link EnterpriseIdentityMappingService} without changing
 * workflow code.
 *
 * <p>出站方向解析的是企业内成员标识（外部审批 API 所需的用户标识），不是外部登录身份。
 */
public class ConnectionScopedIdentityResolver {

  private final EnterpriseIdentityMappingService mappingService;

  public ConnectionScopedIdentityResolver(EnterpriseIdentityMappingService mappingService) {
    this.mappingService = mappingService;
  }

  /** 平台用户 → 该连接下的企业内成员标识。 */
  public Optional<String> toExternalMember(String platformUserId, String connectionId) {
    if (isBlank(platformUserId) || isBlank(connectionId)) {
      return Optional.empty();
    }
    return mappingService.toExternalMember(connectionId, new PlatformPrincipalRef(platformUserId));
  }

  /** 企业内成员标识 → 平台用户。 */
  public Optional<String> toPlatformUser(String externalMemberId, String connectionId) {
    if (isBlank(externalMemberId) || isBlank(connectionId)) {
      return Optional.empty();
    }
    return mappingService.toPlatform(connectionId, externalMemberId)
        .map(PlatformPrincipalRef::userIdentity)
        .filter(value -> !isBlank(value));
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
