package com.ouroboros.businessmodel.validation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import com.ouroboros.businessmodel.meta.CompositeViewBusinessModelMeta;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.WebAPIModel;
import com.ouroboros.webapi.openapi.*;

class CompositeViewContractValidatorTest {
  private CompositeViewBusinessModelMeta view() {
    var view = new CompositeViewBusinessModelMeta();
    view.setDatasetName("demo.orders");
    var column = new CompositeViewBusinessModelMeta.ColumnDef();
    column.setField("id");
    column.setTitle("编号");
    view.setColumns(List.of(column));
    view.getLayoutConfig().setPageSize(20);
    return view;
  }

  private CompositeViewSourceSchema source() {
    return new CompositeViewSourceSchema(true, List.of(
        new CompositeViewSourceSchema.Field("id", "integer", false, true, true),
        new CompositeViewSourceSchema.Field("title", "string", true, false, false)));
  }

  private WebAPIModel api(SchemaModel target) {
    return SimpleWebAPIModel.builder().post("/api/orders/approve")
        .operationId("orders.approve").requestBody(RequestBodyModel.builder().required(true).content(Map.of(
        "application/json", MediaTypeModel.builder().schema(SchemaModel.builder().type("object")
            .properties(Map.of("orderId", target)).required(List.of("orderId")).build()).build())).build()).build();
  }

  private CompositeViewContractValidator validator(WebAPIModel api) {
    return new CompositeViewContractValidator(name -> Optional.of(source()), ref -> {
      if (!"orders.approve".equals(ref)) {
        throw new BusinessException("composite-view.action-api-invalid");
      }
      return api;
    });
  }

  private CompositeViewBusinessModelMeta.ActionButtonDef action(String scope, String source) {
    var action = new CompositeViewBusinessModelMeta.ActionButtonDef();
    action.setLabel("处理");
    action.setScope(scope);
    action.setApiRef("orders.approve");
    action.setContextBinding(Map.of("orderId", source));
    return action;
  }

  @Test
  void rejectsMissingDisabledAndSchemaLessDatasets() {
    for (var contract : List.of(Optional.<CompositeViewSourceSchema>empty(),
        Optional.of(new CompositeViewSourceSchema(false, source().fields())),
        Optional.of(new CompositeViewSourceSchema(true, List.of())))) {
      var validator = new CompositeViewContractValidator(name -> contract, ref -> api(SchemaModel.builder().type("integer").build()));
      assertFalse(validator.validate(view()).isValid());
    }
  }

  @Test
  void checksColumnFilterAndDefaultSortAgainstSourceCapabilities() {
    var view = view();
    view.getColumns().getFirst().setField("missing");
    var filter = new CompositeViewBusinessModelMeta.FilterItemDef();
    filter.setField("title");
    view.getFilterConfig().setItems(List.of(filter));
    var sort = new CompositeViewBusinessModelMeta.SortDef();
    sort.setField("title");
    view.getLayoutConfig().setDefaultSort(List.of(sort));
    var result = validator(api(SchemaModel.builder().type("integer").build())).validate(view);
    assertEquals(3, result.getErrors().size());
    assertTrue(result.getErrors().getFirst().getMessage().contains("columns[0].field"));
  }

  @Test
  void acceptsRowAndBatchBindingsWithCompatibleTypes() {
    var view = view();
    view.setActionButtons(List.of(action("row", "${id}")));
    assertTrue(validator(api(SchemaModel.builder().type("number").format("double").build())).validate(view).isValid());
    view.setActionButtons(List.of(action("batch", "id")));
    assertTrue(validator(api(SchemaModel.builder().type("array")
        .items(SchemaModel.builder().type("integer").build()).build())).validate(view).isValid());
  }

  @Test
  void rejectsUnknownApiFieldsTypesTargetsAndMissingRequiredBindings() {
    var view = view();
    var action = action("row", "missing");
    view.setActionButtons(List.of(action));
    var validator = validator(api(SchemaModel.builder().type("string").build()));
    assertTrue(validator.validate(view).getErrors().getFirst().getMessage().contains("源字段不存在"));
    action.setContextBinding(Map.of("orderId", "id"));
    assertTrue(validator.validate(view).getErrors().getFirst().getMessage().contains("类型不兼容"));
    action.setContextBinding(Map.of("unknown", "id"));
    assertEquals(2, validator.validate(view).getErrors().size());
    action.setContextBinding(Map.of());
    assertTrue(validator.validate(view).getErrors().getFirst().getMessage().contains("必填参数"));
    action.setApiRef("missing");
    assertTrue(validator.validate(view).getErrors().getFirst().getMessage().contains("action-api-invalid"));
  }

  @Test
  void rejectsScalarBatchTargetsAndUnresolvedSchemaReferences() {
    var view = view();
    view.setActionButtons(List.of(action("batch", "id")));
    assertFalse(validator(api(SchemaModel.builder().type("integer").build())).validate(view).isValid());
    assertFalse(validator(api(SchemaModel.builder().ref("#/components/schemas/Unknown").build())).validate(view).isValid());
  }

  @Test
  void validatesSelectedRecordCollectionsAgainstRequiredObjectFields() {
    var view = view();
    view.setActionButtons(List.of(action("batch", "selectedItems")));
    var item = SchemaModel.builder().type("object").required(List.of("id"))
        .properties(Map.of("id", SchemaModel.builder().type("integer").build())).build();
    assertTrue(validator(api(SchemaModel.builder().type("array").items(item).build())).validate(view).isValid());
    item = SchemaModel.builder().type("object").required(List.of("missing"))
        .properties(Map.of("missing", SchemaModel.builder().type("integer").build())).build();
    assertFalse(validator(api(SchemaModel.builder().type("array").items(item).build())).validate(view).isValid());
  }

  @Test
  void rejectsBindingsToSourceFieldsOmittedFromProjectedColumns() {
    var view = view();
    view.setActionButtons(List.of(action("row", "title")));
    var target = SchemaModel.builder().type("string").nullable(true).build();
    assertTrue(validator(api(target)).validate(view).getErrors().getFirst().getMessage().contains("源字段不存在"));
  }

  @Test
  void validatesNullabilityAlongsideParameterTypes() {
    var source = new CompositeViewSourceSchema(true, List.of(
        new CompositeViewSourceSchema.Field("id", "integer", true, true, true)));
    var view = view();
    view.setActionButtons(List.of(action("row", "id")));
    var validator = new CompositeViewContractValidator(name -> Optional.of(source),
        ref -> api(SchemaModel.builder().type("integer").build()));
    assertFalse(validator.validate(view).isValid());
    validator = new CompositeViewContractValidator(name -> Optional.of(source),
        ref -> api(SchemaModel.builder().type("integer").nullable(true).build()));
    assertTrue(validator.validate(view).isValid());
  }

  @Test
  void checksRequiredChildrenWhenAnOptionalBodyOrNestedObjectIsMapped() {
    var payload = SchemaModel.builder().type("object").properties(Map.of(
        "orderId", SchemaModel.builder().type("integer").build(),
        "tenantId", SchemaModel.builder().type("integer").build()))
        .required(List.of("orderId", "tenantId")).build();
    var body = SchemaModel.builder().type("object").properties(Map.of("payload", payload)).build();
    var api = SimpleWebAPIModel.builder().post("/api/orders/approve").requestBody(RequestBodyModel.builder()
        .required(false).content(Map.of("application/json", MediaTypeModel.builder().schema(body).build())).build()).build();
    var view = view();
    var action = action("row", "id");
    view.setActionButtons(List.of(action));
    action.setContextBinding(Map.of());
    assertTrue(validator(api).validate(view).isValid());
    action.setContextBinding(Map.of("payload.orderId", "id"));
    assertTrue(validator(api).validate(view).getErrors().getFirst().getMessage().contains("payload.tenantId"));
    action.setContextBinding(Map.of("payload.orderId", "id", "payload.tenantId", "id"));
    assertTrue(validator(api).validate(view).isValid());
  }

  @Test
  void reportsUnsupportedRequestBodyContracts() {
    var api = SimpleWebAPIModel.builder().post("/api/orders/approve").requestBody(RequestBodyModel.builder()
        .required(true).content(Map.of("text/plain", MediaTypeModel.builder()
            .schema(SchemaModel.builder().type("string").build()).build())).build()).build();
    var view = view();
    var action = action("row", "id");
    action.setContextBinding(Map.of());
    view.setActionButtons(List.of(action));
    assertTrue(validator(api).validate(view).getErrors().getFirst().getMessage().contains("JSON 对象合同"));
    api = SimpleWebAPIModel.builder().post("/api/orders/approve").requestBody(RequestBodyModel.builder()
        .required(true).content(Map.of("application/json", MediaTypeModel.builder()
            .schema(SchemaModel.builder().type("array").items(SchemaModel.builder().type("integer").build()).build())
            .build())).build()).build();
    assertTrue(validator(api).validate(view).getErrors().getFirst().getMessage().contains("对象参数合同"));
  }
}
