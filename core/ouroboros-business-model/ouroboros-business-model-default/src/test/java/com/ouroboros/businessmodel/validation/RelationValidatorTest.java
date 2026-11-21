package com.ouroboros.businessmodel.validation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.meta.BusinessModelFieldMeta;
import com.ouroboros.businessmodel.meta.BusinessModelRelationMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RelationValidatorTest {
  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"0.1", "1.0"})
  void legacyRelationsRetainTheirExistingDefinitionAndProjection(String version) {
    var target = model("Customer", List.of(field("id", true)), List.of());
    var owner = model(version, "Order", List.of(field("id", true), field("customer", false)), List.of(
        relation("customer", "reference", "id"), relation("waybill", "master", "waybill")), target);
    String original = com.ouroboros.util.Json.toJsonString(owner.getMeta());

    assertEquals("", validate(owner));
    assertTrue(owner.getValidationResult().isValid(), owner.getValidationResult().getErrors().toString());
    assertTrue(owner.getRelationValidationResult(new java.util.ArrayList<>()).isValid());
    var projected = new com.ouroboros.businessmodel.projection.data.basic.RelationMPS().getRelationFields(owner);

    assertEquals(List.of("customer", "waybill"), projected.stream().map(field -> field.getName()).toList());
    assertEquals("id", projected.get(0).getExtraProp(String.class, "referenceKey").orElseThrow());
    assertEquals("waybill", projected.get(1).getExtraProp(String.class, "referenceKey").orElseThrow());
    assertEquals(original, com.ouroboros.util.Json.toJsonString(owner.getMeta()));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"0.1", "1.0"})
  void legacyModelsStillUseTheExistingRelatedModelAvailabilityCheck(String version) {
    var owner = model(version, "Order", List.of(field("id", true)), List.of(relation("customer", "reference", "id")));

    var result = owner.getRelationValidationResult(new java.util.ArrayList<>());

    assertFalse(result.isValid());
    assertTrue(result.getErrors().toString().contains("sales.Customer"));
  }

  @Test
  void childReverseInferenceUsesTheSameCaseInsensitiveModelIdentityAsValidation() {
    var child = model("Child", List.of(field("id", true)), List.of());
    var childRelation = relation("children", "child", null);
    childRelation.setModel("sales.Child");
    var parent = model("Parent", List.of(field("id", true)), List.of(childRelation), child);
    var master = relation("parent", "master", "id");
    master.setModel("SALES.parent");
    child.getMeta().setRelations(List.of(master));
    assertEquals("", validate(parent));
    var projected = new com.ouroboros.businessmodel.projection.data.basic.RelationMPS().getRelationFields(parent).get(0);
    assertEquals("parent", projected.getExtraProp(String.class, "referenceKey").orElseThrow());
  }

  @Test
  void rejectsDuplicateNamesFieldCollisionsAndUnsupportedCardinality() {
    var target = model("Customer", List.of(field("id", true)), List.of());
    var relations = List.of(relation("customer", "reference", "id"),
        relation("CUSTOMER", "master", "id"), relation("id", "manyToMany", "id"));
    var owner = model("Order", List.of(field("id", true)), relations, target);
    var errors = validate(owner);
    assertTrue(errors.contains("relations.CUSTOMER.duplicate"), errors);
    assertTrue(errors.contains("relations.id.field-name-conflict"), errors);
    assertTrue(errors.contains("relations.id.type-invalid"), errors);
  }

  @Test
  void requiresResolvableUniqueReferenceKeyAndReportsRelationMember() {
    var target = model("Customer", List.of(field("id", true), field("name", false)), List.of());
    var owner = model("Order", List.of(field("id", true)), List.of(
        relation("customer", "reference", "missing"), relation("byName", "master", "name")), target);
    var errors = validate(owner);
    assertTrue(errors.contains("relations.customer.referenceKey-not-found"), errors);
    assertTrue(errors.contains("relations.byName.referenceKey-not-unique"), errors);
  }

  @Test
  void validReferenceUsesTheExistingRelationAndPrimaryKey() {
    var target = model("Customer", List.of(field("id", true)), List.of());
    var owner = model("Order", List.of(field("id", true)), List.of(relation("customer", "reference", null)), target);
    assertEquals("", validate(owner));
    var schema = com.ouroboros.businessmodel.projection.support.BusinessModelSchemaSupport.createValidationSchema(owner);
    assertInstanceOf(Map.class, schema.get("properties"));
    var properties = (Map<?, ?>) schema.get("properties");
    var customer = (Map<?, ?>) properties.get("customer");
    assertEquals(Map.of("modelFullName", "sales.Order", "relationName", "customer"), customer.get("x-business-relation"));
    assertTrue(com.ouroboros.util.JsonSchemas.validate(schema, Map.of("customer", "C-1")));
    assertTrue(com.ouroboros.util.JsonSchemas.validate(schema, Map.of("customer", Map.of("id", "C-1"))));
  }

  private static String validate(DefaultBusinessModel owner) {
    var context = new ValidationContext<>(owner);
    new RelationValidator().doValidate(context);
    return context.getErrors().stream().map(Throwable::getMessage).collect(java.util.stream.Collectors.joining("; "));
  }

  private static BusinessModelFieldMeta field(String name, boolean primary) {
    var field = new BusinessModelFieldMeta();
    field.setName(name);
    field.setType("单行文本");
    field.setIsPrimaryKey(primary);
    return field;
  }

  private static BusinessModelRelationMeta relation(String name, String type, String key) {
    var relation = new BusinessModelRelationMeta();
    relation.setName(name);
    relation.setType(type);
    relation.setModel("sales.Customer");
    relation.setReferenceKey(key);
    return relation;
  }

  private static DefaultBusinessModel model(String name, List<BusinessModelFieldMeta> fields,
      List<BusinessModelRelationMeta> relations, DefaultBusinessModel... targets) {
    return model("2.0", name, fields, relations, targets);
  }

  private static DefaultBusinessModel model(String version, String name, List<BusinessModelFieldMeta> fields,
      List<BusinessModelRelationMeta> relations, DefaultBusinessModel... targets) {
    var meta = new DefaultBusinessModelMeta();
    meta.setFormatVersion(version);
    meta.setName(name);
    meta.setNamespace("sales");
    meta.setIsEnabled(true);
    meta.setFields(fields);
    meta.setRelations(relations);
    var provider = mock(BusinessModelProvider.class);
    for (var target : targets) {
      when(provider.getBusinessModel(target.getFullName())).thenReturn(Optional.of(target));
    }
    return new DefaultBusinessModel(meta, provider);
  }
}
