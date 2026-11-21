package com.xinpai.spreadsheet.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import com.alibaba.druid.pool.DruidDataSource;

class MySqlProfilePropertiesTest {
  @Test
  void mysqlProfileBindsDatabaseAddressAndRedisPortOverrides() throws Exception {
    StandardEnvironment environment = new StandardEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test-addresses", Map.of(
        "SPREADSHEET_DB_HOST", "127.0.0.2", "SPREADSHEET_DB_PORT", "13306",
        "SPREADSHEET_DB_NAME", "export_fixture", "SPREADSHEET_REDIS_PORT", "16379")));
    for (var source : new YamlPropertySourceLoader().load("mysql-profile", new FileSystemResource(
        Path.of("src/main/resources/application-mysql.yml")))) {
      environment.getPropertySources().addLast(source);
    }
    Binder binder = Binder.get(environment);
    try (DruidDataSource datasource = binder.bind("db.mysql.druid", Bindable.of(DruidDataSource.class)).get()) {
      assertThat(datasource.getUrl()).isEqualTo("jdbc:mysql://127.0.0.2:13306/export_fixture");
      assertThat(datasource.getDriverClassName()).isEqualTo("com.mysql.cj.jdbc.Driver");
    }
    assertThat(binder.bind("spring.redis", Bindable.of(RedisProperties.class)).get().getPort()).isEqualTo(16379);
  }
}
