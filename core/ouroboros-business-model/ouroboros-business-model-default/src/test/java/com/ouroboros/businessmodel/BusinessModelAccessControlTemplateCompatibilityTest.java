package com.ouroboros.businessmodel;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class BusinessModelAccessControlTemplateCompatibilityTest {
  @Test
  public void accessControlSubViewsRetainLegacyAliasSensitivePaths() throws IOException {
    String searchTemplate = readResource("META-INF/editor-schema-templates/default/accessControl/subViews/read.json.ftl");
    String createTemplate = readResource("META-INF/editor-schema-templates/default/accessControl/subViews/create.json.ftl");
    String updateTemplate = readResource("META-INF/editor-schema-templates/default/accessControl/subViews/update.json.ftl");
    String deleteTemplate = readResource("META-INF/editor-schema-templates/default/accessControl/subViews/delete.json.ftl");

    assertTrue(searchTemplate.contains("\"name\": \"accessControl.search.filters\""));
    assertTrue(searchTemplate.contains("\"name\": \"$byEmployee\""));
    assertTrue(searchTemplate.contains("\"name\": \"$byDepartment\""));
    assertTrue(searchTemplate.contains("\"name\": \"$byCompany\""));

    assertTrue(createTemplate.contains("\"name\": \"accessControl.add.isEnabled\""));
    assertTrue(createTemplate.contains("\"name\": \"accessControl.add.fieldControl\""));
    assertTrue(createTemplate.contains("\"visibleOn\": \"${ accessControl.add.isEnabled }\""));

    assertTrue(updateTemplate.contains("\"name\": \"accessControl.update.filters\""));
    assertTrue(updateTemplate.contains("\"name\": \"$byEmployee\""));
    assertTrue(updateTemplate.contains("\"name\": \"$byDepartment\""));
    assertTrue(updateTemplate.contains("\"name\": \"$byCompany\""));

    assertTrue(deleteTemplate.contains("\"name\": \"accessControl.delete.filters\""));
    assertTrue(deleteTemplate.contains("\"name\": \"$byEmployee\""));
    assertTrue(deleteTemplate.contains("\"name\": \"$byDepartment\""));
    assertTrue(deleteTemplate.contains("\"name\": \"$byCompany\""));
  }

  @Test
  public void viewsLibRetainsLegacyAccessControlFilterMapLiterals() throws IOException {
    String template = readResource("META-INF/editor-schema-templates/default/views/lib.json.ftl");

    assertTrue(template.contains("createdByEmployee"));
    assertTrue(template.contains("accessControl.delete.filters.$byEmployee"));
    assertTrue(template.contains("accessControl.search.filters.$byEmployee"));
    assertTrue(template.contains("accessControl.update.filters.$byEmployee"));

    assertTrue(template.contains("createdByDepartment"));
    assertTrue(template.contains("accessControl.delete.filters.$byDepartment"));
    assertTrue(template.contains("accessControl.search.filters.$byDepartment"));
    assertTrue(template.contains("accessControl.update.filters.$byDepartment"));

    assertTrue(template.contains("createdByCompany"));
    assertTrue(template.contains("accessControl.delete.filters.$byCompany"));
    assertTrue(template.contains("accessControl.search.filters.$byCompany"));
    assertTrue(template.contains("accessControl.update.filters.$byCompany"));
  }

  private static String readResource(String path) throws IOException {
    try (InputStream stream = BusinessModelAccessControlTemplateCompatibilityTest.class
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
