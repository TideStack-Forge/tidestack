package com.ouroboros.mq.rabbit;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.stream.binder.BinderFactory;
import org.springframework.cloud.stream.binder.rabbit.properties.RabbitExtendedBindingProperties;
import org.springframework.cloud.stream.function.StreamBridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class OuroborosRabbitMQAutoConfigurationContractTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(OuroborosRabbitMQAutoConfiguration.class))
      .withBean(StreamBridge.class, OuroborosRabbitMQAutoConfigurationContractTest::newStreamBridge)
      .withBean(BinderFactory.class, () -> org.mockito.Mockito.mock(BinderFactory.class))
      .withBean(RabbitExtendedBindingProperties.class, RabbitExtendedBindingProperties::new);

  @Test
  void dualRegistrationPointsAtTheSameRabbitAutoConfiguration() {
    String springFactories = readResource("META-INF/spring.factories");
    String autoConfigurationImports = readResource("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
    String target = "com.ouroboros.mq.rabbit.OuroborosRabbitMQAutoConfiguration";

    assertTrue(springFactories.contains("org.springframework.boot.autoconfigure.EnableAutoConfiguration=" + target));
    assertTrue(autoConfigurationImports.contains(target));
  }

  @Test
  void rabbitAutoConfigurationUsesBootStyleAutoConfigurationAnnotation() {
    assertTrue(OuroborosRabbitMQAutoConfiguration.class.isAnnotationPresent(AutoConfiguration.class));
  }

  @Test
  void autoConfigurationCreatesRabbitSpecificBeansInBoundedContext() {
    contextRunner.run(context -> {
      assertThat(context).hasSingleBean(RabbitMQBinderReadyChecker.class);
      assertThat(context).hasSingleBean(RabbitMQProducerCreator.class);
    });
  }

  private String readResource(String resourcePath) {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    assertNotNull(classLoader, "classLoader should exist");

    try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
      assertNotNull(inputStream, "resource should exist: " + resourcePath);
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int read;
      while ((read = inputStream.read(buffer)) != -1) {
        outputStream.write(buffer, 0, read);
      }
      return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new AssertionError("failed to read resource: " + resourcePath, e);
    }
  }

  private static StreamBridge newStreamBridge() {
    return mock(StreamBridge.class);
  }
}
