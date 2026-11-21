package com.ouroboros.uischema.dev;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class DevUiSchemaAmisEditorResourceContextTest {
  @Test
  public void devUiSchemaAddPageProvidesResourceBrowserContext() throws IOException {
    String addPage = readResource("META-INF/ouroboros/metadata/dev/{appName}/ui-schema/add.ui-schema.json");

    assertTrue(addPage.contains("\"resourceBrowserContext\""));
    assertTrue(addPage.contains("\"mode\": \"dev-app-frontend-resource\""));
    assertTrue(addPage.contains("\"appName\": \"${appName}\""));
  }

  @Test
  public void devUiSchemaEditPageProvidesResourceBrowserContext() throws IOException {
    String editPage = readResource("META-INF/ouroboros/metadata/dev/{appName}/ui-schema/{fullName}.ui-schema.json");

    assertTrue(editPage.contains("\"resourceBrowserContext\""));
    assertTrue(editPage.contains("\"mode\": \"dev-app-frontend-resource\""));
    assertTrue(editPage.contains("\"appName\": \"${appName}\""));
  }

  private static String readResource(String path) throws IOException {
    try (InputStream stream = DevUiSchemaAmisEditorResourceContextTest.class
      .getClassLoader()
      .getResourceAsStream(path)) {
      if (stream == null) {
        throw new IOException("Missing resource: " + path);
      }
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int length;
      while ((length = stream.read(buffer)) != -1) {
        output.write(buffer, 0, length);
      }
      return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
  }
}
