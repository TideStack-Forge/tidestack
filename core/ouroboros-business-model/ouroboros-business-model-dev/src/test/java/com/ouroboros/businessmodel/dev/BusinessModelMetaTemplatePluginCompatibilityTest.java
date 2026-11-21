package com.ouroboros.businessmodel.dev;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;

public class BusinessModelMetaTemplatePluginCompatibilityTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  public void defaultMetaTemplateBindsPluginFeatureFields() throws IOException {
    String template = readResource("META-INF/business-model-meta-templates/default.json");

    DefaultBusinessModelMeta meta = OBJECT_MAPPER.readValue(template, DefaultBusinessModelMeta.class);

    assertEquals(Boolean.FALSE, meta.getEnableArchiveDelete());
    assertEquals(Boolean.FALSE, meta.getEnableScd2History());

    JsonNode serialized = OBJECT_MAPPER.valueToTree(meta);
    assertFalse(serialized.path("enableArchiveDelete").asBoolean(true));
    assertFalse(serialized.has("archiveDeleteConfig"));
    assertFalse(serialized.path("enableScd2History").asBoolean(true));
    assertFalse(serialized.has("scd2HistoryConfig"));
  }

  private static String readResource(String path) throws IOException {
    try (InputStream stream = BusinessModelMetaTemplatePluginCompatibilityTest.class
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
