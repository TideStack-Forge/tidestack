package com.ouroboros.businessmodel.dev;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class BusinessModelMetaTemplateAccessControlCompatibilityTest {
  @Test
  public void defaultMetaTemplateRetainsLegacyAccessControlStructure() throws IOException {
    String template = readResource("META-INF/business-model-meta-templates/default.json");

    assertTrue(template.contains(String.join("\n",
      "  \"accessControl\": {",
      "    \"search\": {",
      "      \"isEnabled\": false,",
      "      \"fieldControl\": false,",
      "      \"filters\": {",
      "        \"$byEmployee\": false,",
      "        \"$byDepartment\": false,",
      "        \"$byCompany\": false",
      "      }",
      "    },",
      "    \"add\": {",
      "      \"isEnabled\": false,",
      "      \"fieldControl\": false",
      "    },",
      "    \"update\": {",
      "      \"isEnabled\": false,",
      "      \"fieldControl\": false,",
      "      \"filters\": {",
      "        \"$byEmployee\": false,",
      "        \"$byDepartment\": false,",
      "        \"$byCompany\": false",
      "      }",
      "    },",
      "    \"delete\": {",
      "      \"isEnabled\": false,",
      "      \"filters\": {",
      "        \"$byEmployee\": false,",
      "        \"$byDepartment\": false,",
      "        \"$byCompany\": false",
      "      }",
      "    }",
      "  }"
    )));
  }

  private static String readResource(String path) throws IOException {
    try (InputStream stream = BusinessModelMetaTemplateAccessControlCompatibilityTest.class
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
