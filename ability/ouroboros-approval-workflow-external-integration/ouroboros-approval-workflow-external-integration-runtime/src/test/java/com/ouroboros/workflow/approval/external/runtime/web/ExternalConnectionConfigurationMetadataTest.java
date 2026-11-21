package com.ouroboros.workflow.approval.external.runtime.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 系统配置中心企业协作连接编辑契约。 */
class ExternalConnectionConfigurationMetadataTest {

  private static final String RESOURCE = "META-INF/ouroboros/metadata/configurations.json";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void connectionsUseNativeComboWithChineseLabels() throws Exception {
    try (InputStream stream = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
      assertThat(stream).as(RESOURCE).isNotNull();

      JsonNode connections = objectMapper.readTree(stream).get(0);
      JsonNode editorSchema = connections.path("editorSchema");
      JsonNode controls = editorSchema.path("items").get(0).path("body");

      assertThat(editorSchema.path("type").asText()).isEqualTo("combo");
      assertThat(editorSchema.toString()).doesNotContain("external-connection-editor");
      assertThat(controls.findValuesAsText("label"))
          .containsAll(List.of("连接标识", "流程引擎", "安全凭据引用"))
          .doesNotContain("connectionId", "provider", "credentialRef", "corpId", "departmentId");

      JsonNode provider = findControl(controls, "provider");
      assertThat(provider.path("type").asText()).isEqualTo("select");
      assertThat(provider.path("source").asText())
          .isEqualTo("/api/approval-workflow/engine-adapters");
      assertThat(provider.path("labelField").asText()).isEqualTo("displayName");
      assertThat(provider.path("valueField").asText()).isEqualTo("provider");
      assertThat(provider.has("options")).isFalse();

      // 按控件类型查找，不用位置索引：增删字段或调整顺序不应让断言假通过/假失败。
      JsonNode auth = findControlByType(controls, "fieldSet");
      assertThat(auth.path("title").asText()).isEqualTo("认证设置");
      assertThat(auth.path("body").findValuesAsText("name")).contains("auth.enabled");

      JsonNode dynamicSchema = findControlByType(controls, "amis-schema");
      assertThat(dynamicSchema.path("source").path("url").asText())
          .isEqualTo("/api/approval-workflow/engine-adapters/connection-editor-schema?provider=${provider}");
      // provider 专属字段必须由服务端 schema 动态提供，不得固化进静态元数据。
      assertThat(dynamicSchema.path("provider").asText()).isEmpty();
    }
  }

  private JsonNode findControlByType(JsonNode controls, String type) {
    for (JsonNode control : controls) {
      if (type.equals(control.path("type").asText())) {
        return control;
      }
    }
    throw new AssertionError("Missing configuration control of type: " + type);
  }

  private JsonNode findControl(JsonNode controls, String name) {
    for (JsonNode control : controls) {
      if (name.equals(control.path("name").asText())) {
        return control;
      }
    }
    throw new AssertionError("Missing configuration control: " + name);
  }
}
