package com.ouroboros.fieldtype.dev;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldTypeDevAutoConfigurationContractTest {

  @Test
  void springFactoriesPointsAtFieldTypeDevRoot() {
    String springFactories = readResource("META-INF/spring.factories");
    String expectedLine = "org.springframework.boot.autoconfigure.EnableAutoConfiguration"
        + "=com.ouroboros.fieldtype.dev.FieldTypeDevAutoConfiguration";
    String oldLine = "org.springframework.boot.autoconfigure.EnableAutoConfiguration"
        + "=com.ouroboros.dev.fieldtype.FieldTypeDevAutoConfiguration";
    List<String> trimmedLines = Arrays.stream(springFactories.split("\\R"))
        .map(String::trim)
        .filter(line -> !line.isEmpty())
        .collect(Collectors.toList());
    assertEquals(1, trimmedLines.size(), "spring.factories should only list the new auto-config entry");
    assertTrue(trimmedLines.contains(expectedLine), "spring.factories must register the new dev auto-config");
    assertFalse(trimmedLines.contains(oldLine), "spring.factories must not reference the old dev FQCN");
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
