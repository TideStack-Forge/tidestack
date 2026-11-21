package com.ouroboros.businessmodel;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class BusinessModelApiPopulateEditorTemplateTest {
  @Test
  public void searchAndGetOneTemplatesUsePopulateEditorAndDefaultModelMetaProvider() throws IOException {
    var searchTemplate = readResource("META-INF/editor-schema-templates/default/apis/subViews/search.json.ftl");
    var getOneTemplate = readResource("META-INF/editor-schema-templates/default/apis/subViews/getOne.json.ftl");
    var libTemplate = readResource("META-INF/editor-schema-templates/default/apis/lib.json.ftl");

    assertTrue(searchTemplate.contains("\"type\": \"populate-editor\""));
    assertTrue(searchTemplate.contains("\"name\": \"apis.search.populate\""));
    assertTrue(searchTemplate.contains("\"modelMetaProvider\""));

    assertTrue(getOneTemplate.contains("\"type\": \"populate-editor\""));
    assertTrue(getOneTemplate.contains("\"name\": \"apis.getOne.populate\""));
    assertTrue(getOneTemplate.contains("\"modelMetaProvider\""));

    assertTrue(libTemplate.contains("/business-model-helper/${modelRef}/model-meta"));
    assertTrue(libTemplate.contains("\"modelRefKey\": \"fullName\""));
    assertTrue(libTemplate.contains("\"relationMapping\""));
  }

  private static String readResource(String path) throws IOException {
    try (InputStream stream = BusinessModelApiPopulateEditorTemplateTest.class
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
