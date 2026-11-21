package com.ouroboros.workflow.approval.external.runtime.web;

import com.ouroboros.workflow.approval.CurrentUserSupplier;

/**
 * 测试用当前用户提供者（test SPI）：固定返回 test-admin，供映射管理控制器/守卫单测使用。
 */
public class TestCurrentUserSupplier implements CurrentUserSupplier {

  @Override
  public String getIdentity() {
    return "test-admin";
  }
}
