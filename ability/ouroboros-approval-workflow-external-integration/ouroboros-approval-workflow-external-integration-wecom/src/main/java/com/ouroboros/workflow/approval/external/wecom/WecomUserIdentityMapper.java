package com.ouroboros.workflow.approval.external.wecom;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.workflow.approval.external.ExternalUserIdentityMapper;
import com.ouroboros.workflow.approval.external.runtime.identity.ConnectionScopedIdentityResolver;

/** Connection-scoped platform user to Enterprise WeChat userid mapping. */
@Component
public class WecomUserIdentityMapper implements ExternalUserIdentityMapper {

  static final String PROVIDER_ID = "wecom";

  private final ConnectionScopedIdentityResolver mappingResolver;

  @Autowired
  public WecomUserIdentityMapper(ConnectionScopedIdentityResolver mappingResolver) {
    this.mappingResolver = mappingResolver;
  }

  @Override
  public Optional<String> toExternalUser(
      String platformUserId, String adapterId, String connectionId) {
    if (!WecomEngineAdapterProvider.ADAPTER_ID.equals(adapterId) || platformUserId == null) {
      return Optional.empty();
    }
    return mappingResolver.toExternalMember(platformUserId, connectionId);
  }

  @Override
  public Optional<String> toPlatformUser(
      String externalUserId, String adapterId, String connectionId) {
    if (!WecomEngineAdapterProvider.ADAPTER_ID.equals(adapterId) || externalUserId == null) {
      return Optional.empty();
    }
    return mappingResolver.toPlatformUser(externalUserId, connectionId);
  }
}
