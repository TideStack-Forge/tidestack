package com.ouroboros.workflow.approval.external.feishu;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.workflow.approval.external.ExternalUserIdentityMapper;
import com.ouroboros.workflow.approval.external.runtime.identity.ConnectionScopedIdentityResolver;

/** Connection-scoped platform user to Feishu user identifier mapping. */
@Component
public class FeishuUserIdentityMapper implements ExternalUserIdentityMapper {

  static final String PROVIDER_ID = "feishu";

  private final ConnectionScopedIdentityResolver mappingResolver;

  @Autowired
  public FeishuUserIdentityMapper(ConnectionScopedIdentityResolver mappingResolver) {
    this.mappingResolver = mappingResolver;
  }

  @Override
  public Optional<String> toExternalUser(
      String platformUserId, String adapterId, String connectionId) {
    if (!FeishuEngineAdapterProvider.ADAPTER_ID.equals(adapterId) || platformUserId == null) {
      return Optional.empty();
    }
    return mappingResolver.toExternalMember(platformUserId, connectionId);
  }

  @Override
  public Optional<String> toPlatformUser(
      String externalUserId, String adapterId, String connectionId) {
    if (!FeishuEngineAdapterProvider.ADAPTER_ID.equals(adapterId) || externalUserId == null) {
      return Optional.empty();
    }
    return mappingResolver.toPlatformUser(externalUserId, connectionId);
  }
}
