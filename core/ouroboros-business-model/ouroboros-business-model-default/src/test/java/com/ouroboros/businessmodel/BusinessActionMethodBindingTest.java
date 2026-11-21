package com.ouroboros.businessmodel;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.action.BusinessActionInput;
import com.ouroboros.businessmodel.action.BusinessObjectReference;
import com.ouroboros.businessmodel.action.PreparedBusinessActionInput;
import com.ouroboros.businessmodel.annotation.BusinessActionData;
import com.ouroboros.businessmodel.annotation.BusinessActionParam;
import com.ouroboros.businessmodel.behavior.ActionDefinition;

import static org.junit.jupiter.api.Assertions.*;

class BusinessActionMethodBindingTest {
  private static final Map<String, Object> REFERENCE = Map.of("type", "object", "x-business-object-ref", "sales.Order",
      "properties", Map.of("id", Map.of("type", "string")), "required", List.of("id"));
  private static final Map<String, Object> SCHEMA = Map.of("type", "object", "required", List.of("params", "data"), "properties", Map.of(
      "params", Map.of("type", "object", "required", List.of("orders"), "properties", Map.of(
          "orders", Map.of("type", "array", "items", REFERENCE),
          "send", Map.of("type", "boolean", "default", false))),
      "data", Map.of("type", "object", "properties", Map.of("comment", Map.of("type", "string")))));

  @Test
  void bindsNamedListsDefaultsAndWholeDtoWithoutInheritance() throws Exception {
    var action = action(SCHEMA);
    var binding = binding("approve", List.class, boolean.class, Comment.class);
    binding.validate(action);
    var input = PreparedBusinessActionInput.prepare(BusinessActionInput.of(Map.of("orders", List.of(Map.of("id", "one"), Map.of("id", "two"))),
        Map.of("comment", "approved")), SCHEMA);
    Object[] args = binding.bind(null, input, SCHEMA);
    assertEquals(List.of(new BusinessObjectReference("sales.Order", "one"), new BusinessObjectReference("sales.Order", "two")), args[0]);
    assertEquals(false, args[1]);
    assertEquals(new Comment("approved"), args[2]);
  }

  @Test
  void injectsTheSamePreparedInputAndAcceptsNoInput() throws Exception {
    var binding = binding("input", BusinessActionInput.class);
    binding.validate(action(SCHEMA));
    var input = PreparedBusinessActionInput.prepare(BusinessActionInput.empty(), Map.of());
    assertSame(input, binding.bind(null, input, SCHEMA)[0]);
    binding("none").validate(action(Map.of()));
    assertThrows(IllegalStateException.class, () -> binding("none").validate(action(SCHEMA)));
  }

  @Test
  void rejectsAmbiguousSignaturesAndOptionalPrimitiveAtRegistration() throws Exception {
    for (var method : Invalid.class.getDeclaredMethods()) {
      assertThrows(IllegalStateException.class, () -> new BusinessActionMethodBinding(method), method.getName());
    }
    var optional = Map.<String, Object>of("type", "object", "properties", Map.of("params", Map.of("type", "object",
        "properties", Map.of("send", Map.of("type", "boolean")))));
    assertThrows(IllegalStateException.class, () -> binding("send", boolean.class).validate(action(optional)));
    assertThrows(IllegalStateException.class, () -> binding("text", String.class).validate(action(optional)));
  }

  @Test
  void dtoProjectionRejectsUnknownPropertiesInsteadOfDiscardingThem() throws Exception {
    var binding = binding("comment", Comment.class);
    var schema = Map.<String, Object>of("type", "object", "properties", Map.of("data", Map.of("type", "object")));
    binding.validate(action(schema));
    var input = PreparedBusinessActionInput.prepare(BusinessActionInput.dataOnly(Map.of("comment", "ok", "unknown", true)), schema);
    assertThrows(com.ouroboros.exception.BusinessException.class, () -> binding.bind(null, input, schema));
  }

  @Test
  void nestedDtosAndReferenceArraysReceiveDeclaredModels() throws Exception {
    var schema = Map.<String, Object>of("type", "object", "properties", Map.of("data", Map.of("type", "object",
        "properties", Map.of("record", REFERENCE, "records", Map.of("type", "array", "items", REFERENCE)))));
    var binding = binding("roles", Roles.class);
    binding.validate(action(schema));
    var input = PreparedBusinessActionInput.prepare(BusinessActionInput.dataOnly(Map.of("record", Map.of("id", "one"),
        "records", List.of(Map.of("id", "two")))), schema);
    assertEquals(new Roles(new BusinessObjectReference("sales.Order", "one"),
        List.of(new BusinessObjectReference("sales.Order", "two"))), binding.bind(null, input, schema)[0]);
    var arrays = binding("array", BusinessObjectReference[].class);
    arrays.validate(action(Map.of("type", "object", "properties", Map.of("data", Map.of("type", "array", "items", REFERENCE)))));
    var arrayInput = PreparedBusinessActionInput.prepare(BusinessActionInput.dataOnly(List.of(Map.of("id", "one"))), Map.of());
    assertArrayEquals(new BusinessObjectReference[]{new BusinessObjectReference("sales.Order", "one")},
        (BusinessObjectReference[]) arrays.bind(null, arrayInput, Map.of("properties", Map.of("data", Map.of("items", REFERENCE))))[0]);
  }

  @Test
  void rejectsDtoPrimitiveFieldsOutsideThePublishedContract() throws Exception {
    var schema = Map.<String, Object>of("type", "object", "properties", Map.of("data", Map.of("type", "object",
        "properties", Map.of("comment", Map.of("type", "string")))));
    assertThrows(IllegalStateException.class, () -> binding("extra", Extra.class).validate(action(schema)));
  }

  private BusinessActionMethodBinding binding(String name, Class<?>... types) throws Exception {
    return new BusinessActionMethodBinding(Methods.class.getDeclaredMethod(name, types));
  }

  private static ActionDefinition action(Map<String, Object> schema) {
    var action = new ActionDefinition();
    action.setInputSchema(schema);
    return action;
  }

  public record Comment(String comment) { }
  public record Roles(BusinessObjectReference record, List<BusinessObjectReference> records) { }
  public record Extra(String comment, int undeclared) { }

  static class Methods {
    public void approve(@BusinessActionParam("orders") List<BusinessObjectReference> orders,
                        @BusinessActionParam("send") boolean send, @BusinessActionData Comment comment) { }
    public void input(BusinessActionInput<?, ?> input) { }
    public void none() { }
    public void send(@BusinessActionParam("send") boolean send) { }
    public void text(@BusinessActionParam("send") String text) { }
    public void comment(@BusinessActionData Comment data) { }
    public void roles(@BusinessActionData Roles data) { }
    public void extra(@BusinessActionData Extra data) { }
    public void array(@BusinessActionData BusinessObjectReference[] records) { }
  }

  static class Invalid {
    public void duplicate(@BusinessActionParam("send") boolean one, @BusinessActionParam("send") boolean two) { }
    public void mixed(@BusinessActionParam Map<String, Object> all, @BusinessActionParam("send") boolean send) { }
    public void unannotated(String value) { }
    public void raw(@BusinessActionData List data) { }
    public void unresolved(@BusinessActionData List<?> data) { }
    public void typedInput(BusinessActionInput<Map<String, Object>, Comment> input) { }
    public void boundedInput(BusinessActionInput<? extends Map<String, Object>, ?> input) { }
    public void inputAndData(BusinessActionInput<?, ?> input, @BusinessActionData Comment data) { }
    public void path(@BusinessActionParam("order.id") String id) { }
    public void twoAnnotations(@BusinessActionParam @BusinessActionData String data) { }
  }
}
