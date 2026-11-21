package com.ouroboros.logicflow.dev.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class LogicFlowDebugSessionSchemaTest {

  @Test
  public void sessionListSchemaShouldUseCurrentSummaryFieldsAndStatusValues() {
    var schema = loadSchemaText("META-INF/ouroboros/metadata/dev/{appName}/logicflow-debug-sessions.ui-schema.json");

    assertTrue(schema.contains("/api/dev/${appName}/logicflow/debug/sessions"));
    assertTrue(schema.contains("\"name\": \"startedAt\""));
    assertTrue(schema.contains("\"name\": \"lastEventAt\""));
    assertTrue(schema.contains("\"ACTIVE\": \"<span class='label label-info'>运行中</span>\""));
    assertTrue(schema.contains("\"COMPLETED\": \"<span class='label label-success'>已完成</span>\""));
    assertTrue(schema.contains("\"FAILED\": \"<span class='label label-danger'>失败</span>\""));
    assertTrue(schema.contains("/dev/${appName}/logicflow/debug/sessions/${sessionId}"));
    assertFalse(schema.contains("\"name\": \"startTime\""));
    assertFalse(schema.contains("\"name\": \"endTime\""));
    assertFalse(schema.contains("\"RUNNING\":"));
  }

  @Test
  public void sessionListUiModelShouldBindRoutePathToDedicatedSchemaId() {
    var uiModel = loadSchemaText("META-INF/ouroboros/metadata/dev/{appName}/logicflow-debug-sessions.ui-model.json");

    assertTrue(uiModel.contains("\"path\": \"/dev/{appName}/logicflow/debug/sessions\""));
    assertTrue(uiModel.contains("\"type\": \"amis\""));
    assertTrue(uiModel.contains("\"schemaId\": \"/dev/{appName}/logicflow-debug-sessions\""));
  }

  @Test
  public void sessionDetailSchemaShouldRenderCurrentSummaryFieldsAndKeepEventReadSurface() {
    var schema = loadSchemaText("META-INF/ouroboros/metadata/dev/{appName}/logicflow-debug-session-detail.ui-schema.json");

    assertTrue(schema.contains("/api/dev/${appName}/logicflow/debug/sessions/${sessionId}"));
    assertTrue(schema.contains("/api/dev/${appName}/logicflow/debug/sessions/${sessionId}/events"));
    assertTrue(schema.contains("${startedAt | date:YYYY-MM-DD HH\\\\:mm\\\\:ss}"));
    assertTrue(schema.contains("${lastEventAt | date:YYYY-MM-DD HH\\\\:mm\\\\:ss}"));
    assertTrue(schema.contains("\"title\": \"最后事件时间\""));
    assertTrue(schema.contains("\"name\": \"payload\""));
    assertFalse(schema.contains("${startTime | date:YYYY-MM-DD HH\\\\:mm\\\\:ss}"));
    assertFalse(schema.contains("${endTime | date:YYYY-MM-DD HH\\\\:mm\\\\:ss}"));
    assertFalse(schema.contains("\"title\": \"结束时间\""));
  }

  @Test
  public void sessionDetailUiModelShouldBindRoutePathToDedicatedSchemaId() {
    var uiModel = loadSchemaText("META-INF/ouroboros/metadata/dev/{appName}/logicflow-debug-session-detail.ui-model.json");

    assertTrue(uiModel.contains("\"path\": \"/dev/{appName}/logicflow/debug/sessions/{sessionId}\""));
    assertTrue(uiModel.contains("\"type\": \"amis\""));
    assertTrue(uiModel.contains("\"schemaId\": \"/dev/{appName}/logicflow-debug-session-detail\""));
  }

  private static String loadSchemaText(String path) {
    try (InputStream inputStream = LogicFlowDebugSessionSchemaTest.class.getClassLoader()
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
