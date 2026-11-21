package com.ouroboros.workflow.approval.external.runtime.web;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;

/**
 * 默认业务记录权限守卫：没有业务域授权实现时拒绝访问。
 */
public class FailClosedExternalBusinessRecordPermissionGuard implements ExternalBusinessRecordPermissionGuard {

  @Override
  public void requireView(String userId, String scope, String businessRecordType, String businessRecordId) {
    throw new PlatformException(
        "[" + EngineIntegrationErrorCodes.EXTERNAL_PERMISSION_DENIED + "] 无权限：业务记录权限守卫未装配，scope="
            + scope + ", businessRecordType=" + businessRecordType + ", businessRecordId=" + businessRecordId);
  }
}
