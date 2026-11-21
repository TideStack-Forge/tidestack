package com.ouroboros.dev.app.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ContainerDatasourceTest {

  @Test
  void shouldPreserveBlankAndNullLiteralDatasourceEnvValues() {
    ContainerDatasource datasource = new ContainerDatasource()
        .setDriverClassName("null")
        .setUrl("  ")
        .setUsername("root")
        .setPassword(null);

    Map<String, String> env = datasource.asEnvMap();

    assertEquals("com.alibaba.druid.pool.DruidDataSource", env.get("SPRING_DATASOURCE_TYPE"));
    assertEquals("null", env.get("SPRING_DATASOURCE_DRIVERCLASSNAME"));
    assertEquals("  ", env.get("SPRING_DATASOURCE_URL"));
    assertEquals("root", env.get("SPRING_DATASOURCE_USERNAME"));
    assertEquals(null, env.get("SPRING_DATASOURCE_PASSWORD"));
  }
}
