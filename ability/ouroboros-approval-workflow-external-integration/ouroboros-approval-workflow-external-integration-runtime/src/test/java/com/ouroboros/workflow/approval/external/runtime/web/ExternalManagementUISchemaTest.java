package com.ouroboros.workflow.approval.external.runtime.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * M19 管理页面 schema 契约测试：三份 amis ui-schema 资源存在、包装合法、
 * 根节点为 page，且引用 M13/M14/M15 的 API 契约（amis 渲染正确性由 M19-T4 联调验收）。
 */
class ExternalManagementUISchemaTest {

  private static final String SCHEMA_DIR = "META-INF/ouroboros/ui-schema/approval-workflow/";
  private static final List<String> RESOURCES = List.of(
      SCHEMA_DIR + "engine-adapter-catalog.ui-schema.json",
      SCHEMA_DIR + "engine-mapping-manage.ui-schema.json",
      SCHEMA_DIR + "external-result-tracking.ui-schema.json");


  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void managementSchemasExistAndAreValidAmisPages() throws Exception {
    for (String resource : RESOURCES) {
      try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
        assertThat(stream).as(resource).isNotNull();
        JsonNode root = objectMapper.readTree(stream);
        assertThat(root.path("name").asText()).as(resource).startsWith("/approval-workflow/");
        assertThat(root.path("schema").path("type").asText()).as(resource).isEqualTo("page");
      }
    }
  }

  @Test
  void catalogSchemaReferencesAdapterApi() throws Exception {
    String schema = load(RESOURCES.get(0));

    assertThat(schema).contains("/api/approval-workflow/engine-adapters");
    assertThat(schema).contains("health");
    assertThat(schema).contains("definitions");
  }

  @Test
  void mappingSchemaReferencesLifecycleEndpoints() throws Exception {
    JsonNode root = objectMapper.readTree(load(RESOURCES.get(1)));
    JsonNode body = root.path("schema").path("body");
    assertThat(body.path("api").asText()).isEqualTo("/api/approval-workflow/engine-mappings");
    assertThat(body.path("columns").findValuesAsText("name"))
        .contains("flowKey", "connectionId", "authority", "integrationMode")
        .doesNotContain("adapterId");

    JsonNode actions = null;
    for (JsonNode column : body.path("columns")) {
      if ("actions".equals(column.path("name").asText())) {
        actions = column.path("buttons");
        break;
      }
    }
    assertThat(actions).isNotNull();
    assertThat(actions.findValuesAsText("api")).anyMatch(api -> api.endsWith("/validate"));
    assertThat(actions.findValuesAsText("api")).anyMatch(api -> api.endsWith("/activate"));
    assertThat(actions.findValuesAsText("api")).anyMatch(api -> api.endsWith("/pause"));
    assertThat(actions.findValuesAsText("api")).anyMatch(api -> api.endsWith("/retire"));

    // 编辑动作按 label 定位、按 dialog.body.api 校验，不依赖它在 actions 里的位置。
    JsonNode editBody = findActionByLabel(actions, "编辑").path("dialog").path("body");
    assertThat(editBody.path("api").asText())
        .isEqualTo("put:/api/approval-workflow/engine-mappings/${id}");
    JsonNode editControls = editBody.path("controls");
    JsonNode createControls = body.path("headerToolbar").get(0)
        .path("dialog").path("body").path("controls");
    assertThat(editControls.findValuesAsText("name")).contains("startOptionsJson");
    assertThat(createControls.findValuesAsText("name")).contains("startOptionsJson");
    assertThat(editControls.findValuesAsText("name")).doesNotContain("adapterId");
    assertThat(createControls.findValuesAsText("name")).doesNotContain("adapterId");
    assertThat(editControls.toString()).contains("/api/approval-workflow/engine-adapters/connections");
    assertThat(createControls.toString()).contains("/api/approval-workflow/engine-adapters/connections");
    assertThat(editControls.findValuesAsText("language")).contains("json");
    assertThat(createControls.findValuesAsText("language")).contains("json");
    assertThat(editControls.findValuesAsText("body"))
        .anyMatch(text -> text.contains("本地内置引擎仍负责执行"));
  }

  @Test
  void trackingSchemaReferencesResultBindingsApi() throws Exception {
    String schema = load(RESOURCES.get(2));

    assertThat(schema).contains("/api/approval-workflow/external/result-bindings/start");
    assertThat(schema).contains("${trackingId}");
  }

  private String load(String resource) throws Exception {
    try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
      assertThat(stream).as(resource).isNotNull();
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  /** 按 label 定位动作节点；找不到即失败，避免位置索引静默取到别的动作。 */
  private JsonNode findActionByLabel(JsonNode actions, String label) {
    for (JsonNode action : actions) {
      if (label.equals(action.path("label").asText())) {
        return action;
      }
    }
    throw new AssertionError("Missing action labelled: " + label);
  }
}