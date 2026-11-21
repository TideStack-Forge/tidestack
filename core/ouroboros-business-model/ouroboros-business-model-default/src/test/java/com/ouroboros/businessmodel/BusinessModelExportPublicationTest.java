package com.ouroboros.businessmodel;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.behavior.BusinessModelBehavior;
import com.ouroboros.businessmodel.projection.ModelBundle;
import com.ouroboros.businessmodel.validation.ValidationResult;
import com.ouroboros.data.model.DataModelMeta;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessModelExportPublicationTest {
  private Field modelsField;
  private Field exportField;
  private Object previousModels;
  private Object previousExport;
  private BusinessModelExport export;
  private final BusinessModelExport.SingletonHolder provider = new BusinessModelExport.SingletonHolder();

  @BeforeEach
  void setUp() throws Exception {
    modelsField = BusinessModelCenter.class.getDeclaredField("businessModelMap");
    modelsField.setAccessible(true);
    previousModels = modelsField.get(null);
    exportField = BusinessModelExport.SingletonHolder.class.getDeclaredField("INSTANCE");
    exportField.setAccessible(true);
    previousExport = exportField.get(null);
    var constructor = BusinessModelExport.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    export = constructor.newInstance();
  }

  @AfterEach
  void restore() throws Exception {
    modelsField.set(null, previousModels);
    exportField.set(null, previousExport);
  }

  @Test
  void explicitChildReferenceFieldIsReusedByTheOfficialProjection() throws Exception {
    var pair = relationModels(true);
    var context = new com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext<>(pair.get(0));
    var addedFields = new com.ouroboros.businessmodel.projection.data.basic.ReferenceRelationMPS()
        .getReferenceRelationFields(context, pair.get(0));
    assertTrue(context.getErrors().isEmpty(), context.getErrors().toString());
    assertTrue(addedFields.isEmpty());
    install(pair.toArray(BusinessModel[]::new));
    export.refresh();
    var child = provider.getDataModelMetaMap().get("sales.Child");
    assertEquals(1, child.getFields().stream().filter(field -> "parentId".equals(field.getName())).count());
    assertEquals("String", child.getFields().stream().filter(field -> "parentId".equals(field.getName()))
        .findFirst().orElseThrow().getType());
  }

  @Test
  void recordedOfficialProjectionErrorsRejectTheCandidateAndRetainTheOldExport() throws Exception {
    var old = model("sales.Old");
    when(old.buildModelBundle()).thenReturn(bundle("sales.Old"));
    install(old);
    export.refresh();
    var previousData = provider.getDataModelMetaMap();
    var previousBehavior = provider.getBusinessModelBehaviorMap();
    var pair = relationModels(false);
    assertTrue(pair.stream().allMatch(BusinessModel::isValid));
    var child = pair.get(0);
    var failure = assertThrows(RuntimeException.class, child::buildModelBundle);
    assertTrue(failure.getMessage().contains("sales.Child"));
    assertTrue(failure.getMessage().contains("parentId"));
    install(pair.toArray(BusinessModel[]::new));
    var exportFailure = assertThrows(RuntimeException.class, export::refresh);
    assertTrue(exportFailure.getMessage().contains("parentId"));
    assertEquals(previousData, provider.getDataModelMetaMap());
    assertEquals(previousBehavior, provider.getBusinessModelBehaviorMap());
  }

  private static List<DefaultBusinessModel> relationModels(boolean scalarReference) {
    Map<String, BusinessModel> values = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    var models = new BusinessModelProvider() {
      public java.util.Optional<BusinessModel> getBusinessModel(String name) {
        return java.util.Optional.ofNullable(values.get(name));
      }

      public Map<String, BusinessModel> getBusinessModelMap() {
        return values;
      }
    };
    var parentMeta = new com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta();
    parentMeta.setName("Parent");
    parentMeta.setNamespace("sales");
    parentMeta.setIsEnabled(true);
    var childMeta = new com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta();
    childMeta.setName("Child");
    childMeta.setNamespace("sales");
    childMeta.setIsEnabled(true);
    var id = new com.ouroboros.businessmodel.meta.BusinessModelFieldMeta();
    id.setName("id");
    id.setType("单行文本");
    id.setIsPrimaryKey(true);
    parentMeta.setFields(List.of(id));
    childMeta.setFields(List.of(id));
    var children = new com.ouroboros.businessmodel.meta.BusinessModelRelationMeta();
    children.setName("children");
    children.setType("child");
    children.setModel("sales.Child");
    children.setReferenceKey("parentId");
    parentMeta.setRelations(List.of(children));
    if (scalarReference) {
      var reference = new com.ouroboros.businessmodel.meta.BusinessModelFieldMeta();
      reference.setName("parentId");
      reference.setType("单行文本");
      childMeta.setFields(List.of(id, reference));
    } else {
      var incompatible = new com.ouroboros.businessmodel.meta.BusinessModelRelationMeta();
      incompatible.setName("parentId");
      incompatible.setType("reference");
      incompatible.setModel("sales.Parent");
      childMeta.setRelations(List.of(incompatible));
    }
    var child = new DefaultBusinessModel(childMeta, models);
    var parent = new DefaultBusinessModel(parentMeta, models);
    values.put(child.getFullName(), child);
    values.put(parent.getFullName(), parent);
    return List.of(child, parent);
  }

  @Test
  void failedProjectionRetainsEveryPreviouslyPublishedProduct() throws Exception {
    var oldModel = model("sales.Old");
    var oldBundle = bundle("sales.Old");
    when(oldModel.buildModelBundle()).thenReturn(oldBundle);
    install(oldModel);
    export.refresh();
    var oldData = provider.getDataModelMetaMap();
    var oldBehavior = provider.getBusinessModelBehaviorMap();
    var oldModules = provider.getAppModules();

    var first = model("sales.First");
    when(first.buildModelBundle()).thenReturn(bundle("sales.First"));
    var second = model("sales.Second");
    when(second.buildModelBundle()).thenThrow(new IllegalStateException("projection failed"));
    install(first, second);

    var failure = assertThrows(RuntimeException.class, export::refresh);
    assertTrue(failure.getMessage().contains("sales.Second"));
    assertEquals("projection failed", failure.getCause().getMessage());
    assertEquals(oldData, provider.getDataModelMetaMap());
    assertEquals(oldBehavior, provider.getBusinessModelBehaviorMap());
    assertEquals(oldModules, provider.getAppModules());
    assertFalse(provider.getBusinessModelBehavior("sales.First").isPresent());
  }

  @Test
  void invalidEnabledModelRejectsWholeCandidateWithModelAndMemberDiagnostics() throws Exception {
    var invalid = model("sales.Invalid");
    when(invalid.isValid()).thenReturn(false);
    when(invalid.getValidationResult()).thenReturn(new ValidationResult(List.of(
        new IllegalArgumentException("actions.submit.binding.handlerName-not-found"))));
    install(invalid);

    var failure = assertThrows(RuntimeException.class, export::refresh);
    assertTrue(failure.getMessage().contains("sales.Invalid"));
    assertTrue(failure.getMessage().contains("actions.submit.binding.handlerName-not-found"));
    assertTrue(provider.getBusinessModelBehaviorMap().isEmpty());
    verify(invalid, never()).buildModelBundle();
  }

  @Test
  void disabledModelIsExcludedAndSuccessfulPublicationReplacesWholeSnapshot() throws Exception {
    var disabled = model("sales.Disabled");
    when(disabled.isEnabled()).thenReturn(false);
    var enabled = model("sales.Enabled");
    when(enabled.buildModelBundle()).thenReturn(bundle("sales.Enabled"));
    install(disabled, enabled);
    export.refresh();

    assertEquals(List.of("sales.Enabled"), List.copyOf(provider.getBusinessModelBehaviorMap().keySet()));
    assertEquals(1, provider.getAppModules().size());
    verify(disabled, never()).buildModelBundle();
    assertThrows(UnsupportedOperationException.class, () -> provider.getDataModelMetaMap().clear());

    install();
    export.refresh();
    assertTrue(provider.getBusinessModelBehaviorMap().isEmpty());
    assertTrue(provider.getDataModelMetaMap().isEmpty());
    assertTrue(provider.getAppModules().isEmpty());
  }

  @Test
  void conflictingProjectedIdentityCannotSilentlyOverwriteAnotherModel() throws Exception {
    var first = model("sales.First");
    when(first.buildModelBundle()).thenReturn(bundle("shared.Name"));
    var second = model("sales.Second");
    when(second.buildModelBundle()).thenReturn(bundle("SHARED.name"));
    install(first, second);

    var failure = assertThrows(RuntimeException.class, export::refresh);
    assertTrue(failure.getMessage().contains("sales.Second"));
    assertTrue(failure.getMessage().toLowerCase().contains("shared.name"));
    assertTrue(provider.getDataModelMetaMap().isEmpty());
  }

  @Test
  void compositeViewResolvesCandidateBusinessActionsOnTheFirstExport() throws Exception {
    var actionModel = model("sales.Actions");
    var actionBundle = bundle("sales.Actions");
    var api = mock(com.ouroboros.webapi.WebAPIModel.class);
    when(api.getOperationId()).thenReturn("sales.approve");
    when(api.getMethod()).thenReturn("POST");
    when(api.getPath()).thenReturn("/api/sales/{orderId}/approve");
    when(api.getApi()).thenReturn("POST /api/sales/{orderId}/approve");
    when(api.getParameters()).thenReturn(List.of(com.ouroboros.webapi.openapi.ParameterModel.builder()
        .name("orderId").in(com.ouroboros.webapi.openapi.ParameterLocation.PATH).required(true)
        .schema(com.ouroboros.webapi.openapi.SchemaModel.builder().type("string").build()).build()));
    var bulkApi = com.ouroboros.webapi.SimpleWebAPIModel.builder().post("/api/sales/approve-batch")
        .operationId("sales.approveBatch")
        .parameters(List.of(com.ouroboros.webapi.openapi.ParameterModel.builder().name("ids")
            .in(com.ouroboros.webapi.openapi.ParameterLocation.QUERY).required(true)
            .schema(com.ouroboros.webapi.openapi.SchemaModel.builder().type("array")
                .items(com.ouroboros.webapi.openapi.SchemaModel.builder().type("string").build()).build()).build()))
        .build();
    actionBundle.putModel(com.ouroboros.webapi.WebAPIModel.class, bulkApi.getApi(), bulkApi);
    actionBundle.putModel(com.ouroboros.webapi.WebAPIModel.class, "POST /api/sales/approve", api);
    when(actionModel.buildModelBundle()).thenReturn(actionBundle);
    var meta = new com.ouroboros.businessmodel.meta.CompositeViewBusinessModelMeta();
    meta.setName("list");
    meta.setNamespace("sales");
    meta.setTitle("Sales");
    meta.setIsEnabled(true);
    meta.setDatasetName("sales.orders");
    var column = new com.ouroboros.businessmodel.meta.CompositeViewBusinessModelMeta.ColumnDef();
    column.setField("id");
    column.setTitle("ID");
    meta.setColumns(List.of(column));
    meta.getLayoutConfig().setPageSize(25);
    var button = new com.ouroboros.businessmodel.meta.CompositeViewBusinessModelMeta.ActionButtonDef();
    button.setLabel("Approve");
    button.setScope("row");
    button.setApiRef("sales.approve");
    button.setContextBinding(Map.of("orderId", "id"));
    var bulk = new com.ouroboros.businessmodel.meta.CompositeViewBusinessModelMeta.ActionButtonDef();
    bulk.setLabel("Approve selected");
    bulk.setScope("batch");
    bulk.setApiRef("sales.approveBatch");
    bulk.setContextBinding(Map.of("ids", "id"));
    meta.setActionButtons(List.of(button, bulk));
    var view = new CompositeViewBusinessModel(meta, BusinessModelCenter.buildInProvider);
    install(view, actionModel);

    export.refresh();

    String schema = com.ouroboros.util.Json.toJsonString(provider.getUISchemaMap());
    assertTrue(schema.contains("/api/runtime/composite-view/sales.list/execute"));
    assertTrue(schema.contains("/api/sales/${id}/approve"));
    assertTrue(schema.contains("${id}"));
    assertTrue(schema.contains("${selectedItems|pick:id}"));
    assertTrue(provider.getWebAPIModelMap().containsKey("POST /api/sales/approve"));
    when(actionModel.isEnabled()).thenReturn(false);
    assertFalse(view.getRelationValidationResult().isValid());
    assertThrows(com.ouroboros.exception.BusinessException.class, export::refresh);
  }

  private void install(BusinessModel... models) throws Exception {
    Map<String, BusinessModel> values = new LinkedHashMap<>();
    for (var model : models) {
      values.put(model.getFullName(), model);
    }
    modelsField.set(null, values);
  }

  private static BusinessModel model(String name) {
    var model = mock(BusinessModel.class);
    when(model.getFullName()).thenReturn(name);
    when(model.getName()).thenReturn(name);
    when(model.isEnabled()).thenReturn(true);
    when(model.isValid()).thenReturn(true);
    when(model.isRelationValid()).thenReturn(true);
    when(model.isAttachment()).thenReturn(false);
    when(model.getValidationResult()).thenReturn(new ValidationResult(List.of()));
    when(model.getRelationValidationResult()).thenReturn(new ValidationResult(List.of()));
    return model;
  }

  private static ModelBundle bundle(String name) {
    var bundle = new ModelBundle();
    bundle.putModel(DataModelMeta.class, name, mock(DataModelMeta.class));
    bundle.putModel(BusinessModelBehavior.class, name, new BusinessModelBehavior(name, List.of(), List.of(), List.of()));
    return bundle;
  }
}
