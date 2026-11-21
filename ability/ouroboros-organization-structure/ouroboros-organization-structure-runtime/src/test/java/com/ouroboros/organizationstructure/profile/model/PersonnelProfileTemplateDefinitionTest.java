package com.ouroboros.organizationstructure.profile.model;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.exception.ValidationException;
import com.ouroboros.organizationstructure.profile.PersonnelProfileConfigurationDefaults;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PersonnelProfileTemplateDefinitionTest {

  @Test
  void validateShouldRejectDuplicateFieldNames() {
    PersonnelProfileTemplateDefinition definition = PersonnelProfileConfigurationDefaults.defaultTemplateDefinition();
    definition.setFields(Arrays.asList(
        field("name", "姓名", "String", 10),
        field("name", "姓名重复", "String", 20)));

    assertThrows(ValidationException.class, definition::validate);
  }

  @Test
  void jsonRoundTripShouldPreserveTemplateAndLayoutConfig() {
    PersonnelProfileTemplateDefinition definition = PersonnelProfileConfigurationDefaults.defaultTemplateDefinition();
    definition.setDescription("员工档案模板");
    definition.setMetadata(metadata("version", "1"));

    String templateJson = Json.toJsonString(definition);
    PersonnelProfileTemplateDefinition restored = Json.toBean(PersonnelProfileTemplateDefinition.class, templateJson);

    assertEquals(definition.getKey(), restored.getKey());
    assertEquals(definition.getLabel(), restored.getLabel());
    assertEquals(definition.getFields().size(), restored.getFields().size());
    assertEquals(definition.getMetadata().get("version"), restored.getMetadata().get("version"));
    restored.validate();

    PersonnelProfileRuntimeLayoutConfig layoutConfig = new PersonnelProfileRuntimeLayoutConfig();
    layoutConfig.setTemplateKey(definition.getKey());
    layoutConfig.setFields(Arrays.asList(
        field("gender", "性别", "String", 10),
        field("name", "姓名", "String", 20),
        field("mobile", "手机号", "String", 30)));

    String layoutJson = Json.toJsonString(layoutConfig);
    PersonnelProfileRuntimeLayoutConfig restoredLayout = Json.toBean(PersonnelProfileRuntimeLayoutConfig.class, layoutJson);

    assertEquals(layoutConfig.getTemplateKey(), restoredLayout.getTemplateKey());
    assertEquals(layoutConfig.getFields().size(), restoredLayout.getFields().size());
    assertEquals("gender", restoredLayout.getFields().get(0).getName());
    assertEquals("name", restoredLayout.getFields().get(1).getName());
  }

  private PersonnelProfileFieldDefinition field(String name, String label, String type, Integer order) {
    PersonnelProfileFieldDefinition field = new PersonnelProfileFieldDefinition();
    field.setName(name);
    field.setLabel(label);
    field.setType(type);
    field.setOrder(order);
    field.setHidden(Boolean.FALSE);
    return field;
  }

  private Map<String, Object> metadata(String key, Object value) {
    Map<String, Object> metadata = new LinkedHashMap<String, Object>();
    metadata.put(key, value);
    return metadata;
  }
}
