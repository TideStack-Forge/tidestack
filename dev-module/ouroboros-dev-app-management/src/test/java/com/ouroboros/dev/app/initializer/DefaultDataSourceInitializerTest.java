package com.ouroboros.dev.app.initializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.dev.app.constant.ConfigurationConstant;

class DefaultDataSourceInitializerTest {

  @Test
  void shouldResolveDriverFromLegacyDriverClassNameEnvironmentProperty() throws Exception {
    ConfigurationService configurationService = mock(ConfigurationService.class);
    when(configurationService.get(eq(String.class), eq(ConfigurationConstant.TEST_APP_DEFAULT_DATASOURCE_DRIVER_CLASS)))
        .thenReturn(Optional.empty());
    MockEnvironment environment = new MockEnvironment()
        .withProperty("driverClassName", "com.mysql.cj.jdbc.Driver");
    DefaultDataSourceInitializer initializer = new DefaultDataSourceInitializer(
        configurationService,
        environment,
        mock(DataSource.class));

    assertEquals("com.mysql.cj.jdbc.Driver", invokeGetDataSourceDriverClassName(initializer));
  }

  @Test
  void shouldIgnoreNullLiteralConfiguredDriverAndUseEnvironmentFallback() throws Exception {
    ConfigurationService configurationService = mock(ConfigurationService.class);
    when(configurationService.get(eq(String.class), eq(ConfigurationConstant.TEST_APP_DEFAULT_DATASOURCE_DRIVER_CLASS)))
        .thenReturn(Optional.of("null"));
    MockEnvironment environment = new MockEnvironment()
        .withProperty("driverClassName", "com.mysql.cj.jdbc.Driver");
    DefaultDataSourceInitializer initializer = new DefaultDataSourceInitializer(
        configurationService,
        environment,
        mock(DataSource.class));

    assertEquals("com.mysql.cj.jdbc.Driver", invokeGetDataSourceDriverClassName(initializer));
  }

  @Test
  void shouldPreferConfiguredDriverOverEnvironmentFallback() throws Exception {
    ConfigurationService configurationService = mock(ConfigurationService.class);
    when(configurationService.get(eq(String.class), eq(ConfigurationConstant.TEST_APP_DEFAULT_DATASOURCE_DRIVER_CLASS)))
        .thenReturn(Optional.of("org.postgresql.Driver"));
    MockEnvironment environment = new MockEnvironment()
        .withProperty("driverClassName", "com.mysql.cj.jdbc.Driver");
    DefaultDataSourceInitializer initializer = new DefaultDataSourceInitializer(
        configurationService,
        environment,
        mock(DataSource.class));

    assertEquals("org.postgresql.Driver", invokeGetDataSourceDriverClassName(initializer));
  }

  private static String invokeGetDataSourceDriverClassName(DefaultDataSourceInitializer initializer) throws Exception {
    Method method = DefaultDataSourceInitializer.class.getDeclaredMethod("getDataSourceDriverClassName");
    method.setAccessible(true);
    return (String) method.invoke(initializer);
  }
}
