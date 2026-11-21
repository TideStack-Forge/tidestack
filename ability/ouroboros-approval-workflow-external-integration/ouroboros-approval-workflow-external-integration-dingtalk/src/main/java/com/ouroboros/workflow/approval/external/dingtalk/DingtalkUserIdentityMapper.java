package com.ouroboros.workflow.approval.external.dingtalk;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.workflow.approval.external.ExternalUserIdentityMapper;
import com.ouroboros.workflow.approval.external.runtime.identity.ConnectionScopedIdentityResolver;

/**
 * 钉钉用户身份映射（平台规范用户 ID 与钉钉 user_id 的 connection-scoped 绑定）。
 *
 * <p>数据来源由公共身份映射端口决定；本 provider 不解析渠道私有 JSON，
 * 也不在找不到映射时透传平台 ID。
 */
@Component
public class DingtalkUserIdentityMapper implements ExternalUserIdentityMapper {

  static final String PROVIDER_ID = "dingtalk";

  private final ConnectionScopedIdentityResolver mappingResolver;

  @Autowired
  public DingtalkUserIdentityMapper(ConnectionScopedIdentityResolver mappingResolver) {
    this.mappingResolver = mappingResolver;
  }

  @Override
  public Optional<String> toExternalUser(String platformUserId, String adapterId, String connectionId) {
    if (!DingtalkEngineAdapterProvider.ADAPTER_ID.equals(adapterId) || platformUserId == null) {
      return Optional.empty();
    }
    return mappingResolver.toExternalMember(platformUserId, connectionId);
  }

  @Override
  public Optional<String> toPlatformUser(String externalUserId, String adapterId, String connectionId) {
    if (!DingtalkEngineAdapterProvider.ADAPTER_ID.equals(adapterId) || externalUserId == null) {
      return Optional.empty();
    }
    return mappingResolver.toPlatformUser(externalUserId, connectionId);
  }
}
