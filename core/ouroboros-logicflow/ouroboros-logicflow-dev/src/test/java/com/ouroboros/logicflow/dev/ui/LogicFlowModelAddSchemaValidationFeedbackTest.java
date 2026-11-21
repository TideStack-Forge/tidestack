package com.ouroboros.logicflow.dev.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.ouroboros.util.Json;

public class LogicFlowModelAddSchemaValidationFeedbackTest {
  private static final String HAS_PERSISTED_DIAGNOSTICS_EXPRESSION =
      "${(result.data.validationSnapshot.validationDiagnostics && result.data.validationSnapshot.validationDiagnostics.length) || "
          + "(result.data.validationSnapshot.validationErrors && result.data.validationSnapshot.validationErrors.length) || "
          + "result.data.validationSnapshot.validationSummary}";

  private static final String HAS_NO_PERSISTED_DIAGNOSTICS_EXPRESSION =
      "${!((result.data.validationSnapshot.validationDiagnostics && result.data.validationSnapshot.validationDiagnostics.length) || "
          + "(result.data.validationSnapshot.validationErrors && result.data.validationSnapshot.validationErrors.length) || "
          + "result.data.validationSnapshot.validationSummary)}";

  @Test
  public void addSchemaShouldExposeValidationErrorFeedbackWithoutClaimingAutoDisable() {
    var schema = loadSchema("META-INF/ouroboros/metadata/dev/{appName}/logicflow-model/add.ui-schema.json");

    var api = getMap(schema, "api");
    assertEquals("/api/dev/${appName}/logic-flow?includeValidationSnapshot=true", api.get("url"));

    assertEquals("modelList", schema.get("reload"));

    var onEvent = getMap(schema, "onEvent");
    var submitSucc = getMap(onEvent, "submitSucc");
    var actions = getList(submitSucc, "actions");

    assertTrue(actions.stream().anyMatch(action ->
        "toast".equals(action.get("actionType"))
            && HAS_PERSISTED_DIAGNOSTICS_EXPRESSION.equals(action.get("expression"))
            && "warning".equals(getMap(action, "args").get("msgType"))
            && "模型已添加，但存在后端校验错误，请查看列表或编辑页中的校验错误"
                .equals(getMap(action, "args").get("msg"))));

    assertTrue(actions.stream().anyMatch(action ->
        "toast".equals(action.get("actionType"))
            && HAS_NO_PERSISTED_DIAGNOSTICS_EXPRESSION.equals(action.get("expression"))
            && "success".equals(getMap(action, "args").get("msgType"))
            && "模型已添加".equals(getMap(action, "args").get("msg"))));

    assertTrue(actions.stream().anyMatch(action ->
        "reload".equals(action.get("actionType"))
            && "modelList".equals(action.get("componentName"))));

    assertTrue(actions.stream().anyMatch(action -> "closeDialog".equals(action.get("actionType"))));
  }

  @Test
  public void addSchemaShouldOnlyHardBlockBlankModelName() {
    var schema = loadSchema("META-INF/ouroboros/metadata/dev/{appName}/logicflow-model/add.ui-schema.json");
    var body = getList(schema, "body");
    var nameField = body.stream()
        .filter(field -> "name".equals(field.get("name")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Expected add schema to contain a name field"));

    assertEquals(Boolean.TRUE, nameField.get("required"));
    assertNull(nameField.get("validations"));
    assertNull(nameField.get("validationErrors"));
  }

  private static Map<?, ?> loadSchema(String path) {
    try (InputStream inputStream = LogicFlowModelAddSchemaValidationFeedbackTest.class.getClassLoader()
        .getResourceAsStream(path)) {
      assertNotNull("Expected schema resource to exist: " + path, inputStream);
      Map<?, ?> resource = Json.toBean(Map.class, inputStream);
      return asMap(resource.get("schema"));
    } catch (Exception e) {
      throw new AssertionError("Failed to load schema resource: " + path, e);
    }
  }

  private static Map<?, ?> getMap(Map<?, ?> source, String key) {
    return asMap(source.get(key));
  }

  private static List<Map<?, ?>> getList(Map<?, ?> source, String key) {
    Object value = source.get(key);
    if (value instanceof List<?> list) {
      return list.stream()
          .map(LogicFlowModelAddSchemaValidationFeedbackTest::asMap)
          .toList();
    }
    throw new AssertionError("Expected list for key: " + key);
  }

  private static Map<?, ?> asMap(Object value) {
    if (value instanceof Map<?, ?> map) {
      return map;
    }
    throw new AssertionError("Expected map but got: " + value);
  }
}
