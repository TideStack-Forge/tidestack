package com.ouroboros.businessmodel.dev.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.Test;

import io.vavr.control.Either;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.meta.BusinessModelRelationMeta;
import com.ouroboros.businessmodel.meta.BusinessModelFieldMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.dev.service.impl.BusinessModelDevService;
import com.ouroboros.fieldtype.CompiledFieldType;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.fieldtype.FieldTypeCompileException;
import com.ouroboros.fieldtype.FieldTypeDefinition;
import com.ouroboros.fieldtype.DefaultFieldTypeCompiler;
import com.ouroboros.fieldtype.DefaultCompiledFieldType;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.uischema.schema.FormItemNode;

class BusinessModelHelperControllerTest {

  @Test
  void approvalWorkflowFieldsEndpointReturnsValueTypeCompiledFromFieldParameters() {
    var definition = new FieldTypeDefinition();
    definition.setName("自定义字段");
    definition.setDataType(Map.of("type", "${storageType}"));
    definition.setDefaultParams(Map.of("storageType", "String"));
    RecordingFieldTypeCatalog catalog = new RecordingFieldTypeCatalog() {
      @Override
      public Either<FieldTypeCompileException, CompiledFieldType> compile(FieldTypeCatalogContext context,
                                                                          String name, Map<String, Object> params) {
        super.compile(context, name, params);
        return new DefaultFieldTypeCompiler().compile(definition, params, context);
      }
    };
    var controller = new BusinessModelHelperController(catalog);
    var model = buildWorkflowBoundModel("oa", "purchase", "采购申请", "flow-purchase");
    model.getFields().get(0).getExtraProps().put("storageType", "Decimal");
    controller.businessModelDevService = new StubBusinessModelDevService(Map.of("oa.purchase", model));
    controller.approvalFieldSourceService = new com.ouroboros.businessmodel.dev.service.BusinessModelApprovalFieldSourceService(catalog, controller.businessModelDevService);

    var result = controller.getApprovalWorkflowFields("demo-app", "business-model", "oa.purchase", null);
    var field = (Map<?, ?>) ((List<?>) result.getData().get("fields")).get(0);

    assertEquals("数字", field.get("type"));
    assertEquals("Decimal", field.get("valueType"));
    assertEquals("demo-app", catalog.lastCompileContext.getAppName());
    assertEquals(FieldTypeCatalogContext.devApp("demo-app").getScene(), catalog.lastCompileContext.getScene());

    model.getFields().get(0).getExtraProps().remove("storageType");
    var defaultResult = controller.getApprovalWorkflowFields("demo-app", "business-model", "oa.purchase", null);
    var defaultField = (Map<?, ?>) ((List<?>) defaultResult.getData().get("fields")).get(0);
    assertEquals("String", defaultField.get("valueType"));
  }

  @Test
  void approvalWorkflowFieldsEndpointKeepsFieldsWithoutDataTypesForPermissionEditing() {
    var catalog = new RecordingFieldTypeCatalog();
    catalog.compiledField = new DefaultCompiledFieldType(null, null, null, null);
    var controller = new BusinessModelHelperController(catalog);
    var model = buildWorkflowBoundModel("oa", "purchase", "采购申请", "flow-purchase");
    controller.businessModelDevService = new StubBusinessModelDevService(Map.of("oa.purchase", model));
    controller.approvalFieldSourceService = new com.ouroboros.businessmodel.dev.service.BusinessModelApprovalFieldSourceService(catalog, controller.businessModelDevService);

    var result = controller.getApprovalWorkflowFields("demo-app", "business-model", "oa.purchase", null);
    var field = (Map<?, ?>) ((List<?>) result.getData().get("fields")).get(0);

    assertEquals("amount", field.get("name"));
    assertFalse(field.containsKey("valueType"));
  }

  @Test
  void approvalWorkflowFieldsEndpointReportsFieldTypeCompilationFailures() {
    var cause = new FieldTypeCompileException("字段类型不存在", "数字", null, null, "compile");
    RecordingFieldTypeCatalog catalog = new RecordingFieldTypeCatalog() {
      @Override
      public Either<FieldTypeCompileException, CompiledFieldType> compile(FieldTypeCatalogContext context,
                                                                          String name, Map<String, Object> params) {
        return Either.left(cause);
      }
    };
    var controller = new BusinessModelHelperController(catalog);
    var model = buildWorkflowBoundModel("oa", "purchase", "采购申请", "flow-purchase");
    controller.businessModelDevService = new StubBusinessModelDevService(Map.of("oa.purchase", model));
    controller.approvalFieldSourceService = new com.ouroboros.businessmodel.dev.service.BusinessModelApprovalFieldSourceService(catalog, controller.businessModelDevService);

    var exception = assertThrows(BusinessException.class,
        () -> controller.getApprovalWorkflowFields("demo-app", "business-model", "oa.purchase", null));

    assertTrue(exception.getMessage().contains("amount"));
    assertSame(cause, exception.getCause());
  }

  @Test
  void fieldSchemaEndpoints_useDevAppContextForCompilation() {
    var catalog = new RecordingFieldTypeCatalog();
    catalog.compiledField = compiledField("input-text", "static-text");
    var controller = new BusinessModelHelperController(catalog);

    var field = new BusinessModelFieldMeta();
    field.setType("测试字段");
    field.setExtraProp("size", 32);

    var inputResult = controller.getFieldSchema("crm", "demo.customer", field);
    var displayResult = controller.getFieldDisplaySchema("crm", "demo.customer", field);

    assertEquals("input-text", inputResult.getData().getType());
    assertEquals("static-text", displayResult.getData().getType());
    assertEquals(FieldTypeCatalogContext.devApp("crm").getScene(), catalog.lastCompileContext.getScene());
    assertEquals("crm", catalog.lastCompileContext.getAppName());
  }

  @Test
  void modelMetaEndpointReturnsNormalizedFieldsAndRelations() {
    var catalog = new RecordingFieldTypeCatalog();
    var controller = new BusinessModelHelperController(catalog);
    var userModel = buildUserModel();
    var relatedModels = new LinkedHashMap<String, BusinessModel>();
    relatedModels.put("sys.user", userModel);
    var customerModel = buildCustomerModel(relatedModels);
    var models = new LinkedHashMap<String, BusinessModel>();
    models.put("crm.customer", customerModel);
    models.put("sys.user", userModel);
    controller.businessModelDevService = new StubBusinessModelDevService(models);
    controller.approvalFieldSourceService = new com.ouroboros.businessmodel.dev.service.BusinessModelApprovalFieldSourceService(catalog, controller.businessModelDevService);

    var result = controller.getModelMeta("demo-app", "crm.customer");

    assertNotNull(result.getData());
    assertEquals("crm.customer", ((Map<?, ?>) result.getData().get("model")).get("name"));
    assertEquals("客户", ((Map<?, ?>) result.getData().get("model")).get("label"));

    var fields = (List<?>) result.getData().get("fields");
    assertEquals("name", ((Map<?, ?>) fields.get(0)).get("name"));
    assertEquals("单行文本", ((Map<?, ?>) fields.get(0)).get("type"));
    assertEquals("status", ((Map<?, ?>) fields.get(1)).get("name"));
    assertEquals("数据字典", ((Map<?, ?>) fields.get(1)).get("type"));
    assertEquals(
        "enabled",
        ((Map<?, ?>) ((List<?>) ((Map<?, ?>) fields.get(1)).get("options")).get(0)).get("value")
    );

    var relations = (List<?>) result.getData().get("relations");
    assertEquals("owner", ((Map<?, ?>) relations.get(0)).get("name"));
    assertEquals("sys.user", ((Map<?, ?>) relations.get(0)).get("targetModel"));
    assertEquals("用户", ((Map<?, ?>) relations.get(0)).get("targetModelLabel"));
  }

  @Test
  void approvalWorkflowFieldsEndpointReturnsFormFieldSource() {
    var catalog = new RecordingFieldTypeCatalog();
    var controller = new BusinessModelHelperController(catalog);
    var userModel = buildUserModel();
    var relatedModels = new LinkedHashMap<String, BusinessModel>();
    relatedModels.put("sys.user", userModel);
    var customerModel = buildCustomerModel(relatedModels);
    var models = new LinkedHashMap<String, BusinessModel>();
    models.put("crm.customer", customerModel);
    models.put("sys.user", userModel);
    controller.businessModelDevService = new StubBusinessModelDevService(models);
    controller.approvalFieldSourceService = new com.ouroboros.businessmodel.dev.service.BusinessModelApprovalFieldSourceService(catalog, controller.businessModelDevService);

    var result = controller.getApprovalWorkflowFields("demo-app", "business-model", "crm.customer", null);

    assertNotNull(result.getData());
    assertEquals("form-fields", result.getData().get("sourceKind"));
    assertEquals("form-fields:crm.customer:2", result.getData().get("revisionToken"));

    var fields = (List<?>) result.getData().get("fields");
    assertEquals("name", ((Map<?, ?>) fields.get(0)).get("name"));
    assertEquals("name", ((Map<?, ?>) fields.get(0)).get("value"));
    assertEquals("客户名称", ((Map<?, ?>) fields.get(0)).get("label"));
    assertEquals("status", ((Map<?, ?>) fields.get(1)).get("name"));
    assertEquals("status", ((Map<?, ?>) fields.get(1)).get("value"));
    assertEquals("状态", ((Map<?, ?>) fields.get(1)).get("label"));
  }

  @Test
  void approvalWorkflowFieldsEndpointPrefersFormBindingAndReturnsBindingSummaries() {
    var catalog = new RecordingFieldTypeCatalog();
    var controller = new BusinessModelHelperController(catalog);
    var purchaseModel = buildWorkflowBoundModel("oa", "purchase", "采购申请", "flow-purchase");
    var expenseModel = buildWorkflowBoundModel("oa", "expense", "费用申请", "flow-purchase");
    var routeModel = buildCustomerModel(Collections.emptyMap());
    var models = new LinkedHashMap<String, BusinessModel>();
    models.put("oa.purchase", purchaseModel);
    models.put("oa.expense", expenseModel);
    models.put("crm.customer", routeModel);
    controller.businessModelDevService = new StubBusinessModelDevService(models);
    controller.approvalFieldSourceService = new com.ouroboros.businessmodel.dev.service.BusinessModelApprovalFieldSourceService(catalog, controller.businessModelDevService);

    var result = controller.getApprovalWorkflowFields("demo-app", "business-model", "oa.purchase", "flow-purchase");

    assertNotNull(result.getData());
    assertEquals("form-fields:oa.purchase:1", result.getData().get("revisionToken"));
    assertEquals("采购申请（oa.purchase）", result.getData().get("boundFormSummary"));

    var boundForm = (Map<?, ?>) result.getData().get("boundForm");
    assertEquals("business-model", boundForm.get("formType"));
    assertEquals("oa.purchase", boundForm.get("formKey"));
    assertEquals("采购申请", boundForm.get("formTitle"));

    var fields = (List<?>) result.getData().get("fields");
    assertEquals("amount", ((Map<?, ?>) fields.get(0)).get("name"));

    var defaultBoundForms = (List<?>) result.getData().get("defaultBoundForms");
    assertEquals(2, defaultBoundForms.size());
    assertEquals("business-model", ((Map<?, ?>) defaultBoundForms.get(0)).get("formType"));
    assertEquals("oa.expense", ((Map<?, ?>) defaultBoundForms.get(0)).get("formKey"));
    assertEquals("费用申请", ((Map<?, ?>) defaultBoundForms.get(0)).get("formTitle"));
    assertEquals("oa.purchase", ((Map<?, ?>) defaultBoundForms.get(1)).get("formKey"));
    assertEquals("费用申请、采购申请", result.getData().get("defaultBoundFormSummary"));
  }

  @Test
  void approvalWorkflowFieldsEndpointDoesNotTreatNonBusinessModelFormAsBusinessModelBinding() {
    var catalog = new RecordingFieldTypeCatalog();
    var controller = new BusinessModelHelperController(catalog);
    var purchaseModel = buildWorkflowBoundModel("oa", "purchase", "采购申请", "flow-purchase");
    var routeModel = buildCustomerModel(Collections.emptyMap());
    var models = new LinkedHashMap<String, BusinessModel>();
    models.put("oa.purchase", purchaseModel);
    models.put("crm.customer", routeModel);
    controller.businessModelDevService = new StubBusinessModelDevService(models);
    controller.approvalFieldSourceService = new com.ouroboros.businessmodel.dev.service.BusinessModelApprovalFieldSourceService(catalog, controller.businessModelDevService);

    var result = controller.getApprovalWorkflowFields("demo-app", "default", "oa.purchase", "flow-purchase");

    assertNotNull(result.getData());
    assertEquals("form-fields:oa.purchase:0", result.getData().get("revisionToken"));
    assertNull(result.getData().get("boundForm"));
    assertEquals("", result.getData().get("boundFormSummary"));
    assertTrue(((List<?>) result.getData().get("fields")).isEmpty());

    var defaultBoundForms = (List<?>) result.getData().get("defaultBoundForms");
    assertEquals(1, defaultBoundForms.size());
    assertEquals("oa.purchase", ((Map<?, ?>) defaultBoundForms.get(0)).get("formKey"));
    assertEquals("采购申请", ((Map<?, ?>) defaultBoundForms.get(0)).get("formTitle"));
    assertEquals("采购申请", result.getData().get("defaultBoundFormSummary"));
  }

  @Test
  void approvalWorkflowFieldsEndpointReturnsEmptySourceWhenFormKeyIsBlankOrModelMissing() {
    var catalog = new RecordingFieldTypeCatalog();
    var controller = new BusinessModelHelperController(catalog);
    var purchaseModel = buildWorkflowBoundModel("oa", "purchase", "采购申请", "flow-purchase");
    var models = new LinkedHashMap<String, BusinessModel>();
    models.put("oa.purchase", purchaseModel);
    controller.businessModelDevService = new StubBusinessModelDevService(models);
    controller.approvalFieldSourceService = new com.ouroboros.businessmodel.dev.service.BusinessModelApprovalFieldSourceService(catalog, controller.businessModelDevService);

    var blankResult = controller.getApprovalWorkflowFields("demo-app", "business-model", "", "flow-purchase");
    assertNotNull(blankResult.getData());
    assertEquals("form-fields:__empty__:0", blankResult.getData().get("revisionToken"));
    assertNull(blankResult.getData().get("boundForm"));
    assertEquals("", blankResult.getData().get("boundFormSummary"));
    assertTrue(((List<?>) blankResult.getData().get("fields")).isEmpty());

    var missingResult = controller.getApprovalWorkflowFields("demo-app", "business-model", "oa.missing", "flow-purchase");
    assertNotNull(missingResult.getData());
    assertEquals("form-fields:oa.missing:0", missingResult.getData().get("revisionToken"));
    assertNull(missingResult.getData().get("boundForm"));
    assertEquals("", missingResult.getData().get("boundFormSummary"));
    assertTrue(((List<?>) missingResult.getData().get("fields")).isEmpty());
  }

  private static CompiledFieldType compiledField(String inputType, String displayType) {
    return new CompiledFieldType() {
      @Override
      public com.ouroboros.data.model.DataModelFieldMeta toDataFieldMeta() {
        var meta = new com.ouroboros.data.model.DataModelFieldMeta();
        meta.setType("String");
        return meta;
      }

      @Override
      public FormItemNode toInputFormItem() {
        var node = new FormItemNode();
        node.setType(inputType);
        return node;
      }

      @Override
      public FormItemNode toDisplayFormItem() {
        var node = new FormItemNode();
        node.setType(displayType);
        return node;
      }

      @Override
      public String getPrimaryKeyGenerator() {
        return null;
      }
    };
  }

  private static class RecordingFieldTypeCatalog implements FieldTypeCatalog {
    private FieldTypeCatalogContext lastCompileContext;
    private CompiledFieldType compiledField = compiledField("input-text", "static-text");

    @Override
    public Optional<FieldTypeDefinition> getMetadata(FieldTypeCatalogContext context, String name) {
      return Optional.empty();
    }

    @Override
    public java.util.List<FieldTypeDefinition> listMetadata(FieldTypeCatalogContext context) {
      return Collections.emptyList();
    }

    @Override
    public Either<FieldTypeCompileException, CompiledFieldType> compile(FieldTypeCatalogContext context,
                                                                        String name,
                                                                        Map<String, Object> params) {
      this.lastCompileContext = context;
      return Either.right(compiledField);
    }
  }

  private static DefaultBusinessModel buildCustomerModel(Map<String, BusinessModel> relatedModels) {
    var meta = new DefaultBusinessModelMeta();
    meta.setNamespace("crm");
    meta.setName("customer");
    meta.setTitle("客户");

    var nameField = new BusinessModelFieldMeta();
    nameField.setName("name");
    nameField.setTitle("客户名称");
    nameField.setType("单行文本");

    var statusField = new BusinessModelFieldMeta();
    statusField.setName("status");
    statusField.setTitle("状态");
    statusField.setType("数据字典");
    statusField.setExtraProp("options", Arrays.asList(
        mapOf("label", "启用", "value", "enabled"),
        mapOf("label", "停用", "value", "disabled")
    ));

    meta.setFields(Arrays.asList(nameField, statusField));

    var ownerRelation = new BusinessModelRelationMeta();
    ownerRelation.setName("owner");
    ownerRelation.setTitle("负责人");
    ownerRelation.setType("master");
    ownerRelation.setModel("sys.user");
    meta.setRelations(Collections.singletonList(ownerRelation));

    return new DefaultBusinessModel(meta, new StubBusinessModelProvider(relatedModels));
  }

  private static DefaultBusinessModel buildUserModel() {
    var meta = new DefaultBusinessModelMeta();
    meta.setNamespace("sys");
    meta.setName("user");
    meta.setTitle("用户");

    var nameField = new BusinessModelFieldMeta();
    nameField.setName("name");
    nameField.setTitle("用户名");
    nameField.setType("单行文本");
    meta.setFields(Collections.singletonList(nameField));
    meta.setRelations(Collections.emptyList());

    return new DefaultBusinessModel(meta, new StubBusinessModelProvider(Collections.emptyMap()));
  }

  private static DefaultBusinessModel buildWorkflowBoundModel(
      String namespace,
      String name,
      String title,
      String processId) {
    var meta = new DefaultBusinessModelMeta();
    meta.setNamespace(namespace);
    meta.setName(name);
    meta.setTitle(title);
    meta.setExtraProp("workflow", mapOf(
        "bindings", Collections.singletonList(mapOf(
            "key", "default",
            "processId", processId,
            "trigger", "manual"
        ))
    ));

    var amountField = new BusinessModelFieldMeta();
    amountField.setName("amount");
    amountField.setTitle("金额");
    amountField.setType("数字");
    meta.setFields(Collections.singletonList(amountField));
    meta.setRelations(Collections.emptyList());

    return new DefaultBusinessModel(meta, new StubBusinessModelProvider(Collections.emptyMap()));
  }

  private static class StubBusinessModelProvider implements com.ouroboros.businessmodel.BusinessModelProvider {
    private final Map<String, BusinessModel> models;

    private StubBusinessModelProvider(Map<String, BusinessModel> models) {
      this.models = models;
    }

    @Override
    public Optional<BusinessModel> getBusinessModel(String fullName) {
      return Optional.ofNullable(models.get(fullName));
    }

    @Override
    public Map<String, BusinessModel> getBusinessModelMap() {
      return models;
    }
  }

  private static Map<String, Object> mapOf(Object... keyValues) {
    var map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
    }
    return map;
  }

  private static class StubBusinessModelDevService extends BusinessModelDevService {
    private final Map<String, BusinessModel> models;

    private StubBusinessModelDevService(Map<String, BusinessModel> models) {
      this.models = models;
    }

    @Override
    public Optional<BusinessModel> getBusinessModel(String fullName) {
      return Optional.ofNullable(models.get(fullName));
    }

    @Override
    public Map<String, BusinessModel> getBusinessModelMap() {
      return models;
    }
  }
}
