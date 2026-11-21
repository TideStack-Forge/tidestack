package com.ouroboros.data.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ouroboros.data.AbstractDataIntegrationTest;
import com.ouroboros.data.config.TestDataStationAutoConfiguration;
import com.ouroboros.data.model.TestModels;

/**
 * 数据源连接测试
 * <p>
 * 验证 H2 数据源配置和 DataStation 注册是否正确。
 */
@DisplayName("数据源连接测试")
class DataSourceConnectionTest extends AbstractDataIntegrationTest {

  @Test
  @DisplayName("DataSource 应该被正确配置")
  void dataSource_shouldBeConfigured() {
    assertNotNull(dataSource, "DataSource should not be null");
  }

  @Test
  @DisplayName("数据库连接应该正常工作")
  void connection_shouldWork() throws SQLException {
    try (Connection conn = dataSource.getConnection()) {
      assertFalse(conn.isClosed(), "Connection should be open");
    }
  }

  @Test
  @DisplayName("H2 应该运行在 MySQL 兼容模式")
  void h2Mode_shouldBeMySQL() throws SQLException {
    try (Connection conn = dataSource.getConnection()) {
      ResultSet rs = conn.createStatement()
          .executeQuery("SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME = 'MODE'");
      assertTrue(rs.next(), "Should have MODE setting");
      String mode = rs.getString(1);
      assertEquals("MYSQL", mode.toUpperCase(), "H2 should be in MySQL mode");
    }
  }

  @Test
  @DisplayName("TestDataStation 应该可用")
  void testDataStation_shouldBeAvailable() {
    assertNotNull(testDataStation, "DataStation should not be null");
    assertEquals(TestDataStationAutoConfiguration.STATION_NAME, testDataStation.getName(),
        "DataStation name should match");
  }

  @Test
  @DisplayName("三个测试模型应该已注册")
  void testModels_shouldBeRegistered() {
    assertTrue(testDataStation.getDataModel(TestModels.DEPARTMENT).isPresent(),
        "Department model should be registered");
    assertTrue(testDataStation.getDataModel(TestModels.USER).isPresent(),
        "User model should be registered");
    assertTrue(testDataStation.getDataModel(TestModels.ORDER).isPresent(),
        "Order model should be registered");
  }
}
