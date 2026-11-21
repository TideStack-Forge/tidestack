package com.ouroboros.notification;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import jakarta.annotation.PostConstruct;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ComponentScan;

import com.ouroboros.data.annotation.DataModelScan;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationAutoConfigurationContractTest {

  @Test
  void autoConfigurationShouldBeRegisteredInSpringFactories() {
    String springFactories = readResource("META-INF/spring.factories");
    String target = "com.ouroboros.notification.NotificationAutoConfiguration";

    assertTrue(springFactories.contains("org.springframework.boot.autoconfigure.EnableAutoConfiguration=" + target));
  }

  @Test
  void autoConfigurationShouldDeclareExpectedAnnotationsAndLifecycleHook() throws Exception {
    assertTrue(NotificationAutoConfiguration.class.isAnnotationPresent(DataModelScan.class));
    assertTrue(NotificationAutoConfiguration.class.isAnnotationPresent(ComponentScan.class));

    Method method = NotificationAutoConfiguration.class.getDeclaredMethod("afterPropertiesSet");
    assertTrue(method.isAnnotationPresent(PostConstruct.class));
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
}
