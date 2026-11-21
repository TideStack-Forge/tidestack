package com.ouroboros.workflow.approval.external.runtime.web;

/**
 * 外部结果跟踪的业务记录权限守卫。
 */
public interface ExternalBusinessRecordPermissionGuard {

  /**
   * 校验当前用户是否可以查看指定业务记录的外部审批结果。
   */
  void requireView(String userId, String scope, String businessRecordType, String businessRecordId);
}
