package com.ouroboros.workflow.approval.external.dingtalk;

import java.util.Map;
import java.util.Optional;

import com.ouroboros.workflow.approval.external.ExternalUserIdentityMapper;

/**
 * 测试身份映射器：user-1 ↔ ding-1 双向映射（契约测试与适配器单测用）。
 */
class TestDingtalkUserIdentityMapper implements ExternalUserIdentityMapper {

  @Override
  public Optional<String> toExternalUser(String platformUserId, String adapterId, String connectionId) {
    return DingtalkEngineAdapterProvider.ADAPTER_ID.equals(adapterId)
        && "user-1".equals(platformUserId) ? Optional.of("ding-1") : Optional.empty();
  }

  @Override
  public Optional<String> toPlatformUser(String externalUserId, String adapterId, String connectionId) {
    return DingtalkEngineAdapterProvider.ADAPTER_ID.equals(adapterId)
        && "ding-1".equals(externalUserId) ? Optional.of("user-1") : Optional.empty();
  }
}
