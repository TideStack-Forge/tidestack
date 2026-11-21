package com.ouroboros.uischema;

import static org.junit.Assert.assertFalse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class CustomUiSchemaAmisEditorContextTest {
  @Test
  public void customUiSchemaEditorsStayWithoutDevResourceContext() throws IOException {
    String editPage = readResource("META-INF/ouroboros/metadata/system-setting/custom-ui-schema/edit.ui-schema.json");

    assertFalse(editPage.contains("\"resourceBrowserContext\""));
  }

  private static String readResource(String path) throws IOException {
    try (InputStream stream = CustomUiSchemaAmisEditorContextTest.class
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
