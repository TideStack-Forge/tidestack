package com.ouroboros.workflow.approval.external;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * M1 合同契约测试：批量回调信封序列化、命令可选字段可空、错误码常量唯一。
 */
class EngineIntegrationContractsTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void callbackEnvelopeListRoundTrip() throws Exception {
    var envelope = new ExternalCallbackEnvelope(
        "event-1",
        "dingtalk-oa",
        "dingtalk-corp-main",
        "inst-1001",
        null,
        null,
        "2026-08-24T10:00:00",
        "bpms_instance_change",
        "agree",
        "sha256:abc");
    var json = objectMapper.writeValueAsString(List.of(envelope));
    List<ExternalCallbackEnvelope> parsed = objectMapper.readValue(
        json,
        objectMapper.getTypeFactory().constructCollectionType(List.class, ExternalCallbackEnvelope.class));

    assertThat(parsed).hasSize(1);
    assertThat(parsed.get(0).eventId()).isEqualTo("event-1");
    assertThat(parsed.get(0).externalTaskId()).isNull();
    assertThat(parsed.get(0).sequence()).isNull();
    assertThat(parsed.get(0).eventType()).isEqualTo("bpms_instance_change");
  }

  @Test
  void startCommandOptionalFieldsAreNullable() {
    var command = new ExternalStartCommand(
        "user-1",
        new ExternalProcessDefinitionRef("corp-a", "PROC_EXPENSE_V3", null, null),
        new ExternalStartCommand.ExternalBusinessRecord("supplier", "sup-1"),
        null,
        null,
        "idem-1",
        null,
        null,
        null);

    assertThat(command.originatorUserId()).isEqualTo("user-1");
    assertThat(command.formData()).isNull();
    assertThat(command.cc()).isNull();
    assertThat(command.approvers()).isNull();
    assertThat(command.targetSelectActioners()).isNull();
    assertThat(command.externalDefinitionRef().definitionVersion()).isNull();
    assertThat(command.externalDefinitionRef().schemaHash()).isNull();
  }

  @Test
  void startCommandRoundTripKeepsAllFields() throws Exception {
    var command = new ExternalStartCommand(
        "user-1",
        new ExternalProcessDefinitionRef("corp-a", "PROC_EXPENSE_V3", "42", "sha256:x"),
        new ExternalStartCommand.ExternalBusinessRecord("supplier", "sup-1"),
        java.util.Map.of("amount", 100),
        java.util.Map.of("k", "v"),
        "idem-1",
        List.of(new CcTarget("user", "user-2", CcTarget.POSITION_START_FINISH)),
        java.util.Map.of("node-1", List.of("user-3")),
        java.util.Map.of("node-2", List.of("user-4")));

    var json = objectMapper.writeValueAsString(command);
    var parsed = objectMapper.readValue(json, ExternalStartCommand.class);

    assertThat(parsed).isEqualTo(command);
    assertThat(parsed.cc().get(0).position()).isEqualTo(CcTarget.POSITION_START_FINISH);
  }

  @Test
  void errorCodeConstantsAreUnique() throws Exception {
    Set<String> values = new HashSet<>();
    for (Field field : EngineIntegrationErrorCodes.class.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
        String value = (String) field.get(null);
        assertThat(values.add(value)).as("错误码重复: %s", value).isTrue();
      }
    }
    assertThat(values).hasSize(17);
  }

  @Test
  void capabilityEnumHasNoLinkCapabilities() {
    List<Capability> linkLike = Arrays.stream(Capability.values())
        .filter(c -> c.name().startsWith("LINK"))
        .toList();
    assertThat(linkLike).isEmpty();
  }
}
