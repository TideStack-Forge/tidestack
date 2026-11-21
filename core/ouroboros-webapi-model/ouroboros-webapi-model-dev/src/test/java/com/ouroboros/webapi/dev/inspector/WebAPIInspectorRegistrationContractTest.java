package com.ouroboros.webapi.dev.inspector;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WebAPIInspectorRegistrationContractTest {

  @Test
  void vcsInspectorSpiPointsAtDevInspectorProxyOnly() {
    String spi = readResource("META-INF/services/com.ouroboros.vcs.file.inspector.VCSFileInspector").trim();
    assertEquals("com.ouroboros.webapi.dev.inspector.WebAPIInspector$Proxy", spi);
    assertNotEquals("com.ouroboros.webapi.inspector.WebAPIInspector$Proxy", spi);
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
