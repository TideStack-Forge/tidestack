package com.ouroboros.notification.template;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.configuration.ConfigurationSnapshot;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.notification.adapter.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class NotificationTemplateDefinitionValidatorTest {
  private final ConfigurationService configuration = mock(ConfigurationService.class);
  private final NotificationTemplateDefinitionValidator validator = new NotificationTemplateDefinitionValidator(configuration);

  @Test
  void referencesAndNumericOptionsUseTheSendingTypeContractWithoutSending() {
    var metadata = new ConfigurationMetadata(); metadata.setKey("voice.volume");
    when(configuration.getSnapshot("voice.volume")).thenReturn(Optional.of(new ConfigurationSnapshot("voice.volume", 42, "42", "1", metadata)));
    var channel = Map.of("schema", Map.of("body", "hello"), "options", Map.of("volume", "#{voice.volume}"));
    var template = template(Map.of("managed-test", channel), true);
    assertEquals(List.of(), validator.validate(template, template));
    assertEquals("#{voice.volume}", ((Map<?, ?>) channel.get("options")).get("volume"));
    verify(configuration).getSnapshot("voice.volume");
  }

  @Test
  void computedGetterOutputIsNotMistakenForSubmittedConfiguration() {
    var valid = template(Map.of("managed-test", Map.of("schema", Map.of("body", "hello"), "options", Map.of())), true);
    assertEquals(List.of(), validator.validate(valid, valid));
    var computed = template(Map.of("managed-test", Map.of("schema", Map.of("body", "hello"),
        "options", Map.of("diagnostic", "attempted write"))), true);
    assertFalse(validator.validate(computed, valid).isEmpty());
  }

  @Test
  void fieldErrorsIdentifyUnknownPropertiesRequiredFieldsAndRange() {
    var invalid = template(Map.of("managed-test", Map.of("schema", Map.of("typo", "value"), "options", Map.of("volume", 101))), true);
    var unknown = validator.validate(invalid, invalid);
    assertTrue(unknown.stream().anyMatch(problem -> problem.path().contains("typo") && problem.code().equals("CHANNEL_BINDING_INVALID")));
    var range = template(Map.of("managed-test", Map.of("schema", Map.of("body", "hello"), "options", Map.of("volume", 101))), true);
    assertTrue(validator.validate(range, range).stream().anyMatch(problem -> problem.code().equals("CHANNEL_maximum")));
    var required = template(Map.of("managed-test", Map.of("schema", Map.of("body", "  "), "options", Map.of())), true);
    assertTrue(validator.validate(required, required).stream().anyMatch(problem -> problem.path().contains("body")));
  }

  @Test
  void protectedMissingAndEmptyReferencesDoNotExposeResolvedValues() {
    var metadata = new ConfigurationMetadata(); metadata.setKey("private"); metadata.setExtraProp("managementAccessPolicyRequired", true);
    when(configuration.getSnapshot("private")).thenReturn(Optional.of(new ConfigurationSnapshot("private", "never-show-this", "never-show-this", "1", metadata)));
    when(configuration.getSnapshot("missing")).thenReturn(Optional.empty());
    var blank = new ConfigurationMetadata(); blank.setKey("empty");
    when(configuration.getSnapshot("empty")).thenReturn(Optional.of(new ConfigurationSnapshot("empty", "", "", "1", blank)));
    for (String key : List.of("private", "missing", "empty")) {
      var candidate = template(Map.of("managed-test", Map.of("schema", Map.of("body", "#{" + key + "}"))), true);
      var problems = validator.validate(candidate, candidate);
      assertFalse(problems.isEmpty()); assertFalse(problems.toString().contains("never-show-this"));
    }
  }

  @Test
  void disabledUnchangedLegacyChannelsRemainReadableButEnablingOrChangingRequiresAnAdapter() {
    var previous = template(Map.of("not-installed", Map.of("schema", Map.of("legacy", "preserved"))), false);
    assertEquals(List.of(), validator.validate(previous, previous));
    assertEquals("ADAPTER_MISSING", validator.diagnostics(previous).getFirst().code());
    var enabled = new LinkedHashMap<>(previous); enabled.put("isEnabled", true);
    assertTrue(validator.validate(enabled, previous).stream().anyMatch(problem -> problem.code().equals("ADAPTER_MISSING")));
    var changed = template(Map.of("not-installed", Map.of("schema", Map.of())), false);
    assertTrue(validator.validate(changed, previous).stream().anyMatch(problem -> problem.code().equals("ADAPTER_MISSING")));
    var empty = template(Map.of(), true);
    assertEquals("CHANNEL_REQUIRED", validator.validate(empty, empty).getFirst().code());
  }

  @Test
  void textStorageLimitIsMeasuredInUtf8BytesIncludingJsonStructure() {
    int overhead = com.ouroboros.util.Json.toJsonString(Map.of("legacy", Map.of("schema", Map.of("body", "")))).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    String boundary = "a".repeat(NotificationTemplateDefinitionValidator.MAX_JSON_BYTES - overhead);
    var exact = template(Map.of("legacy", Map.of("schema", Map.of("body", boundary))), false);
    assertEquals(List.of(), validator.validate(exact, exact));
    var tooLarge = template(Map.of("legacy", Map.of("schema", Map.of("body", boundary + "中"))), false);
    assertTrue(validator.validate(tooLarge, tooLarge).stream().anyMatch(problem -> problem.code().equals("STORAGE_CAPACITY")));
  }

  @Test
  void literalReferencesHandleNestedCollectionsAndReplacementMetacharacters() {
    var input = Map.of("nested", List.of(Map.of("value", "hello #{key}")));
    Object resolved = NotificationConfigurationReferences.resolve(input, "", (path, key) -> "$1\\value");
    assertEquals(Map.of("nested", List.of(Map.of("value", "hello $1\\value"))), resolved);
    assertEquals("hello #{key}", ((Map<?, ?>) input.get("nested").getFirst()).get("value"));
  }

  private Map<String, Object> template(Map<String, ?> channels, boolean enabled) {
    return Map.of("payloadSchema", Map.of(), "isEnabled", enabled, "channel", channels);
  }

  public static class Supplier implements NotificationAdapterSupplier {
    public String getName() { return "managed-test"; }
    public Optional<NotificationChannelManagement> getManagement() {
      return Optional.of(new NotificationChannelManagement("Managed test", List.of(),
          Map.of("body", NotificationChannelManagement.Field.required("正文")), Map.of("volume", NotificationChannelManagement.Field.range("音量", 0, 100))));
    }
    public <D extends NotificationChannelSchema, O extends NotificationChannelOptions> NotificationAdapter<D, O> getAdapter() {
      return (NotificationAdapter<D, O>) new Adapter();
    }
  }

  public static class Adapter implements NotificationAdapter<Schema, Options> {
    public void send(Collection<String> addresses, Schema schema, Map<String, Object> payload, Options options) {
      fail("配置检查不允许发送通知");
    }
  }
  public static class Schema implements NotificationChannelSchema {
    private String body;
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
  }
  public static class Options implements NotificationChannelOptions {
    private Integer volume;
    public Integer getVolume() { return volume; }
    public void setVolume(Integer volume) { this.volume = volume; }
    public String getDiagnostic() { return "computed"; }
  }
}
