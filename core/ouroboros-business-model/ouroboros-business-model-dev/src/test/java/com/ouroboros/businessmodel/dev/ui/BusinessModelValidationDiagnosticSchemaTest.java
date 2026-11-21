package com.ouroboros.businessmodel.dev.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class BusinessModelValidationDiagnosticSchemaTest {

  @Test
  void listSchemaShouldRequestAndDisplayValidationDiagnostics() throws IOException {
    var schema = resource("META-INF/ouroboros/metadata/dev/{appName}/business-model.ui-schema.json");

    assertTrue(schema.contains("\"includeValidationSnapshot\": true"));
    assertTrue(schema.contains("\"&\": \"$model.businessModel\""));
    assertTrue(schema.contains("\"validationSummary\": \"$model.validationSnapshot.validationSummary\""));
    assertTrue(schema.contains("\"validationErrors\": \"$model.validationSnapshot.validationErrors\""));
    assertTrue(schema.contains("showValidationErrorsDialog"));
    assertTrue(schema.contains("查看校验错误"));
  }

  @Test
  void fullEditSchemaShouldShowDiagnosticsAndSubmitFormalBusinessModelOnly() throws IOException {
    var schema = resource("META-INF/ouroboros/metadata/dev/{appName}/business-model/{fullName}.ui-schema.json");

    assertTrue(schema.contains("includeValidationSnapshot=true"));
    assertTrue(schema.contains("\"appName\": \"$appName\""));
    assertTrue(schema.contains("\"appName\": \"${result.data.appName}\""));
    assertTrue(schema.contains("\"validationSnapshot\": \"$validationSnapshot\""));
    assertTrue(schema.contains("validationSnapshot.validationErrors"));
    assertTrue(schema.contains("已保存，但存在后端校验错误"));
    assertTrue(schema.contains("delete api.data.validationSnapshot"));
    assertFalse(schema.contains("\"data\": \"${validationSnapshot}\""));
  }

  @Test
  void dialogEditSchemaShouldShowDiagnosticsAndSubmitFormalBusinessModelOnly() throws IOException {
    var schema = resource("META-INF/ouroboros/metadata/dev/{appName}/business-model/edit.ui-schema.json");

    assertTrue(schema.contains("includeValidationSnapshot=true"));
    assertTrue(schema.contains("\"appName\": \"$appName\""));
    assertTrue(schema.contains("\"appName\": \"${result.data.appName}\""));
    assertTrue(schema.contains("validationSnapshot.validationErrors"));
    assertTrue(schema.contains("已保存，但存在后端校验错误"));
    assertTrue(schema.contains("delete api.data.validationSnapshot"));
    assertFalse(schema.contains("\"data\": \"${validationSnapshot}\""));
  }

  private static String resource(String path) throws IOException {
    try (var input = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
      if (input == null) {
        throw new IOException("Missing resource: " + path);
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
