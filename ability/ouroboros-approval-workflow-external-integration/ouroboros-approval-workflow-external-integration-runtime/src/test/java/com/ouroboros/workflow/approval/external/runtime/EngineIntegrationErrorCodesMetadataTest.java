package com.ouroboros.workflow.approval.external.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.errorcode.model.ErrorCodeDefinition;

/**
 * M8 错误码平台注册测试：元数据文件存在、13 个数字码唯一，且与常量类字符串码一一对应。
 */
class EngineIntegrationErrorCodesMetadataTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void metadataCoversAllErrorCodeConstants() throws Exception {
    List<ErrorCodeDefinition> definitions = loadMetadata();

    // 常量名 -> 数字码 对照表（新增错误码时三处同步：常量类、本清单、error-codes.error.json）
    Map<String, Integer> expected = Map.ofEntries(
        Map.entry("ENGINE_ADAPTER_NOT_FOUND", 46101),
        Map.entry("ENGINE_CONNECTION_UNAVAILABLE", 46102),
        Map.entry("WORKFLOW_MAPPING_NOT_ACTIVE", 46103),
        Map.entry("WORKFLOW_ROUTE_NOT_CONFIGURED", 46104),
        Map.entry("WORKFLOW_RESULT_ONLY_ENTRY_REQUIRED", 46105),
        Map.entry("EXTERNAL_DEFINITION_NOT_FOUND", 46106),
        Map.entry("EXTERNAL_CAPABILITY_UNSUPPORTED", 46107),
        Map.entry("EXTERNAL_ACTION_NOT_MAPPED", 46108),
        Map.entry("EXTERNAL_INTERACTION_REQUIRED", 46109),
        Map.entry("EXTERNAL_EVENT_STALE", 46110),
        Map.entry("EXTERNAL_EVENT_UNKNOWN", 46111),
        Map.entry("EXTERNAL_ACTION_CONFLICT", 46112),
        Map.entry("EXTERNAL_USER_MAPPING_MISSING", 46113),
        Map.entry("EXTERNAL_BINDING_MISSING", 46114),
        Map.entry("EXTERNAL_MAPPING_INVALID", 46115),
        Map.entry("EXTERNAL_PERMISSION_DENIED", 46116),
        Map.entry("WORKFLOW_MAPPING_PERSISTENCE_FAILED", 46117));

    assertThat(definitions).hasSize(expected.size());
    List<Integer> codes = definitions.stream().map(ErrorCodeDefinition::getCode).toList();
    assertThat(codes).doesNotHaveDuplicates();
    assertThat(codes).allMatch(code -> code >= 46101 && code <= 46117);

    // 每个元数据条目必须对应清单中的一个常量名，且全部清单数字码都必须出现
    for (ErrorCodeDefinition definition : definitions) {
      String matchingConstant = expected.entrySet().stream()
          .filter(entry -> entry.getValue().equals(definition.getCode()))
          .map(Map.Entry::getKey)
          .findFirst()
          .orElse(null);
      assertThat(matchingConstant).as("数字码 %s 未对应任何常量", definition.getCode()).isNotNull();
    }
    assertThat(codes).containsAll(expected.values());
  }

  @Test
  void metadataEntriesHaveRequiredFields() throws Exception {
    List<ErrorCodeDefinition> definitions = loadMetadata();

    for (ErrorCodeDefinition definition : definitions) {
      assertThat(definition.getTitle()).isNotBlank();
      assertThat(definition.getDefaultMessageTemplate()).isNotBlank();
      assertThat(definition.getCategory()).isEqualTo("WORKFLOW_EXTERNAL");
      assertThat(definition.isEnabled()).isTrue();
    }
  }

  private List<ErrorCodeDefinition> loadMetadata() throws Exception {
    try (var stream = getClass().getClassLoader()
        .getResourceAsStream("META-INF/ouroboros/metadata/error-codes.error.json")) {
      assertThat(stream).as("错误码元数据文件必须存在").isNotNull();
      return objectMapper.readValue(stream, new TypeReference<List<ErrorCodeDefinition>>() {
      });
    }
  }
}
