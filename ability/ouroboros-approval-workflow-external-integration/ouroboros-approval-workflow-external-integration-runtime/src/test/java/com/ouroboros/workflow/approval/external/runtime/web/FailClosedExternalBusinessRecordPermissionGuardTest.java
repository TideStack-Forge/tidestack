package com.ouroboros.workflow.approval.external.runtime.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ouroboros.exception.PlatformException;

/**
 * 业务记录权限守卫默认拒绝，避免 trackingId 泄露业务审批结果。
 */
class FailClosedExternalBusinessRecordPermissionGuardTest {

  @Test
  void defaultGuardDeniesWhenBusinessDomainGuardIsMissing() {
    FailClosedExternalBusinessRecordPermissionGuard guard = new FailClosedExternalBusinessRecordPermissionGuard();

    assertThatThrownBy(() -> guard.requireView("user-1", "scope-a", "supplier", "sup-1"))
        .isInstanceOf(PlatformException.class)
        .hasMessageContaining("业务记录权限守卫未装配")
        .hasMessageContaining("supplier")
        .hasMessageContaining("sup-1");
  }
}
