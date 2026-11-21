package com.ouroboros.logicflow.dev.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class LogicFlowModelValidationDiagnosticSchemaTest {
  private static final String HAS_PERSISTED_DIAGNOSTICS_EXPRESSION =
      "${(validationDiagnostics && validationDiagnostics.length) || "
          + "(validationErrors && validationErrors.length) || validationSummary}";

  @Test
  public void editSchemaShouldDelegateDiagnosticsRenderingToEditorWhileKeepingNavigationProps() {
    var schema = loadSchemaText("META-INF/ouroboros/metadata/dev/{appName}/logicflow-model/{fullName}.ui-schema.json");

    assertTrue(schema.contains("\"type\": \"logic-flow-editor\""));
    assertTrue(schema.contains("\"name\": \"flowModel\""));
    assertTrue(schema.contains("\"validationSnapshot\": \"${validationSnapshot}\""));
    assertTrue(schema.contains("navigationTargetNodeName"));
    assertTrue(schema.contains("navigationTargetRequestKey"));
    assertFalse(schema.contains("\"name\": \"flowModel.validationDiagnostics\""));
    assertFalse(schema.contains("\"name\": \"flowModel.validationErrors\""));
    assertFalse(schema.contains("\"name\": \"nodeNames\""));
    assertFalse(schema.contains("${item.message}"));
    assertFalse(schema.contains("${item.nodeNames && item.nodeNames.length}"));
    assertFalse(schema.contains("定位到节点「${item}」"));
    assertFalse(schema.contains("查看校验错误"));
    assertFalse(schema.contains("validationState == 'INVALID'"));
    assertFalse(schema.contains("validationState === 'INVALID'"));
    assertFalse(schema.contains("navigationTargetRequestKey || 0"));
    assertFalse(schema.contains("!flowModel.validationDiagnostics || !flowModel.validationDiagnostics.length"));
    assertFalse(schema.contains(".map(message =>"));
    assertFalse(schema.contains("(item.nodeNames || []).map"));
    assertFalse(schema.contains("nodeNames|join"));
    assertFalse(schema.contains("| toDate | date"));
  }

  @Test
  public void listSchemaShouldRemainViewOnlyWhileShowingStructuredDiagnosticContext() {
    var schema = loadSchemaText("META-INF/ouroboros/metadata/dev/{appName}/logicflow-model.ui-schema.json");

    assertTrue(schema.contains("validationDiagnostics"));
    assertTrue(schema.contains("请进入编辑页定位节点"));
    assertFalse(schema.contains("navigationTargetNodeName"));
    assertTrue(schema.contains("\"includeValidationSnapshot\": true"));
    assertTrue(schema.contains("\"name\": \"validationDiagnostics\""));
    assertTrue(schema.contains("\"name\": \"validationErrors\""));
    assertTrue(schema.contains("${item.message}"));
    assertTrue(schema.contains("${item.nodeNames && item.nodeNames.length}"));
    assertTrue(schema.contains(HAS_PERSISTED_DIAGNOSTICS_EXPRESSION));
    assertFalse(schema.contains("!validationDiagnostics || !validationDiagnostics.length"));
    assertFalse(schema.contains("validationState == 'INVALID'"));
    assertFalse(schema.contains("validationState === 'INVALID'"));
    assertFalse(schema.contains("icon || 'fas fa-gears'"));
    assertFalse(schema.contains("nodeNames|join"));
    assertFalse(schema.contains(".map(message =>"));
  }

  private static String loadSchemaText(String path) {
    try (InputStream inputStream = LogicFlowModelValidationDiagnosticSchemaTest.class.getClassLoader()
        .getResourceAsStream(path)) {
      assertNotNull("Expected schema resource to exist: " + path, inputStream);
      var outputStream = new ByteArrayOutputStream();
      var buffer = new byte[1024];
      int read;
      while ((read = inputStream.read(buffer)) != -1) {
        outputStream.write(buffer, 0, read);
      }
      return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new AssertionError("Failed to load schema resource: " + path, e);
    }
  }
}
