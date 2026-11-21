package com.ouroboros.data.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ouroboros.data.AbstractDataIntegrationTest;

@DisplayName("字段原始名集成测试")
class FieldRawNameIntegrationTest extends AbstractDataIntegrationTest {

  @Test
  @DisplayName("未显式配置 rawName 的 camelCase 字段应映射为 snake_case")
  void rawName_shouldFallbackToSnakeCase() {
    assertEquals("department_id", user().getField("departmentId")
        .orElseThrow(() -> new AssertionError("departmentId field should exist")).getRawName());
    assertEquals("created_at", user().getField("createdAt")
        .orElseThrow(() -> new AssertionError("createdAt field should exist")).getRawName());
    assertEquals("order_id", getModel("OrderItem").getField("orderId")
        .orElseThrow(() -> new AssertionError("orderId field should exist")).getRawName());
  }
}
