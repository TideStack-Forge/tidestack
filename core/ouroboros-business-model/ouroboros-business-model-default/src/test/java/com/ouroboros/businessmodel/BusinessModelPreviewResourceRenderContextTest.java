package com.ouroboros.businessmodel;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class BusinessModelPreviewResourceRenderContextTest {
  @Test
  public void businessModelPreviewTemplateProvidesFrontendResourceRenderContext() throws IOException {
    String template = readResource("META-INF/editor-schema-templates/default/views/lib.json.ftl");

    assertTrue(template.contains("\"frontendResourceRenderContext\""));
    assertTrue(template.contains("\"mode\": \"dev-app-workspace\""));
    assertTrue(template.contains("\"appName\": \"${appName}\""));
  }

  private static String readResource(String path) throws IOException {
    try (InputStream stream = BusinessModelPreviewResourceRenderContextTest.class
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
