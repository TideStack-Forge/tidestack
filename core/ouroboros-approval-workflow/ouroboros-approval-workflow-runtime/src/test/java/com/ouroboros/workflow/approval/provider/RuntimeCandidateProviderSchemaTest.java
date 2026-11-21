package com.ouroboros.workflow.approval.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ouroboros.uischema.schema.SchemaNode;

class RuntimeCandidateProviderSchemaTest {

  @Test
  void userSelectProviderShouldExposeEnumSelectorSchema() {
    UserSelectCandidateProvider provider = new UserSelectCandidateProvider();

    SchemaNode schema = provider.getSchema();

    assertNotNull(schema);
    assertEquals("list-select", schema.getType());
    assertEquals("mode", schema.getName());
    assertEquals("请选择用户自选", schema.getProp("label"));
    assertEquals(true, schema.getProp("clearable"));
    assertEquals(2, ((List<?>) schema.getProp("options")).size());
  }

  @Test
  void starterProviderShouldExposeHiddenCheckedSchema() {
    StarterCandidateProvider provider = new StarterCandidateProvider();

    SchemaNode schema = provider.getSchema();

    assertNotNull(schema);
    assertEquals("hidden", schema.getType());
    assertEquals("isChecked", schema.getName());
    assertEquals(true, schema.getProp("value"));
  }

  @Test
  void roleAndPositionProvidersShouldExposeSchemaDrivenMultiSelect() {
    RoleCandidateProvider roleProvider = new RoleCandidateProvider();
    PositionCandidateProvider positionProvider = new PositionCandidateProvider();

    SchemaNode roleSchema = roleProvider.getSchema();
    SchemaNode positionSchema = positionProvider.getSchema();

    assertEquals("select", roleSchema.getType());
    assertEquals("roles", roleSchema.getName());
    assertEquals("请选择角色 ( 1 - 1 )", roleSchema.getProp("label"));
    assertEquals(true, roleSchema.getProp("multiple"));

    assertEquals("select", positionSchema.getType());
    assertEquals("positions", positionSchema.getName());
    assertEquals("请选择岗位 ( 1 - 1 )", positionSchema.getProp("label"));
    assertEquals(true, positionSchema.getProp("multiple"));
  }

  @Test
  void userProviderShouldExposeDynamicPickerSchema() {
    UserCandidateProvider provider = new UserCandidateProvider();

    SchemaNode schema = provider.getSchema();

    assertNotNull(schema);
    assertEquals("dynamic-tree-transfer-picker", schema.getType());
    assertEquals("users", schema.getName());
    assertEquals("请选择用户", schema.getProp("label"));
    assertEquals("lg", schema.getProp("pickerSize"));
    assertEquals(true, schema.getProp("required"));
  }

  @Test
  void formFieldProviderShouldExposeCandidateFieldSelectorSchema() {
    FormFieldCandidateProvider provider = new FormFieldCandidateProvider();

    SchemaNode schema = provider.getSchema();

    assertNotNull(schema);
    assertEquals("select", schema.getType());
    assertEquals("field", schema.getName());
    assertEquals("请选择处理人字段", schema.getProp("label"));
    assertEquals("$candidateFieldSource.fields", schema.getProp("source"));
    assertEquals("label", schema.getProp("labelField"));
    assertEquals("value", schema.getProp("valueField"));
    assertEquals(true, schema.getProp("clearable"));
  }
}
