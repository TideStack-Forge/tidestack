package com.ouroboros.businessmodel.dev;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class BusinessModelSchemaDetailPreviewResourceRenderContextTest {
  @Test
  public void businessModelSchemaDetailProvidesFrontendResourceRenderContext() throws IOException {
    String schemaDetail = readResource(
      "META-INF/ouroboros/metadata/dev/{appName}/business-model/model-bundle/schema-detail.ui-schema.json");

    assertTrue(schemaDetail.contains("\"frontendResourceRenderContext\""));
    assertTrue(schemaDetail.contains("\"mode\": \"dev-app-workspace\""));
    assertTrue(schemaDetail.contains("\"appName\": \"${appName}\""));
  }

  private static String readResource(String path) throws IOException {
    try (InputStream stream = BusinessModelSchemaDetailPreviewResourceRenderContextTest.class
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
