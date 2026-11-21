package com.ouroboros.uimodel.dev;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class DevUiPreviewResourceRenderContextTest {
  @Test
  public void devUiPreviewProvidesFrontendResourceRenderContext() throws IOException {
    String previewPage = readResource("META-INF/ouroboros/metadata/dev/{appName}/ui/preview-amis.ui-schema.json");

    assertTrue(previewPage.contains("\"frontendResourceRenderContext\""));
    assertTrue(previewPage.contains("\"mode\": \"dev-app-workspace\""));
    assertTrue(previewPage.contains("\"appName\": \"${appName}\""));
  }

  private static String readResource(String path) throws IOException {
    try (InputStream stream = DevUiPreviewResourceRenderContextTest.class
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
