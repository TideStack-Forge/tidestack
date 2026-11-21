package com.ouroboros.businessmodel.projection;

import java.math.BigDecimal;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import io.vavr.control.Either;

import static org.junit.jupiter.api.Assertions.*;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.meta.BusinessModelFieldMeta;
import com.ouroboros.businessmodel.meta.BusinessModelRelationMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.projection.custom.CustomAPIMPS;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.fieldtype.CompiledFieldType;
import com.ouroboros.fieldtype.DefaultFieldTypeCatalog;
import com.ouroboros.fieldtype.DefaultFieldTypeCompiler;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.fieldtype.FieldTypeCompileException;
import com.ouroboros.fieldtype.FieldTypeDefinition;
import com.ouroboros.fieldtype.FieldTypeDefinitionReader;
import com.ouroboros.fieldtype.FieldTypeDefinitionSource;
import com.ouroboros.fieldtype.JarFieldTypeDefinitionProvider;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.uischema.schema.FormItemNode;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.uimodel.UIModel;
import com.ouroboros.util.Json;
import com.ouroboros.util.JsonSchemas;
import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.WebAPIModel;
import com.ouroboros.webapi.openapi.MediaTypeModel;
import com.ouroboros.webapi.openapi.ParameterLocation;
import com.ouroboros.webapi.openapi.ParameterModel;
import com.ouroboros.webapi.openapi.ResponseModel;
import com.ouroboros.webapi.openapi.SchemaModel;
import com.ouroboros.businessmodel.projection.support.BusinessModelSchemaSupport;

class BusinessModelGeneratedOpenAPISupportTest {
  private static final SpringContextUtil contextBridge = new SpringContextUtil();

  @org.junit.jupiter.api.AfterEach
  void closeSpringContext() { contextBridge.destroy(); }

  @org.junit.jupiter.api.BeforeAll
  static void ensureSnowflakeNodeIdProvider() {
    // 纯单测无 Spring 上下文：初始化雪花 ID 配置代理（默认 0/0），
    // 避免对"其他 Spring 上下文测试先行初始化"的执行顺序产生隐式依赖（Phase 4 M24 依赖调整后暴露）。
    new com.ouroboros.data.config.DefaultSnowflakeNodeIdProviderConfig();
  }

  @Test
  void crudWebApisShouldExposeRichOpenApiMetadata() throws Exception {
    bindFieldTypeCatalog(new InMemoryFieldTypeCatalog(defaultFieldTypes()));

    var provider = new InMemoryBusinessModelProvider();
    var model = createCustomerModel(provider, false, false);

    var context = new ModelProjectionPipelineContext<DefaultBusinessModel>(model);
    context.setAttribute("hasDataModel", true);

    new com.ouroboros.businessmodel.projection.webapi.SearchMPS().process(context);
    new com.ouroboros.businessmodel.projection.webapi.GetOneMPS().process(context);
    new com.ouroboros.businessmodel.projection.webapi.AddMPS().process(context);
    new com.ouroboros.businessmodel.projection.webapi.UpdateMPS().process(context);
    new com.ouroboros.businessmodel.projection.webapi.DeleteMPS().process(context);
    new com.ouroboros.businessmodel.projection.webapi.BatchDeleteMPS().process(context);
    new com.ouroboros.businessmodel.projection.webapi.ImportMPS().process(context);

    var addApi = getWebApi(context, "POST /demo/customer");
    assertEquals("demo.customer", addApi.getExtraProp(String.class, "businessModel").orElseThrow());
    assertEquals("add", addApi.getExtraProp(String.class, "action").orElseThrow());
    assertEquals(com.ouroboros.webapi.HandlerType.BUSINESS_ACTION, addApi.getHandlerType());
    assertEquals("新增客户", addApi.getSummary());
    assertEquals("demo.customer", addApi.getTags().get(0));
    assertEquals("demo.customer.add", addApi.getOperationId());
    assertTrue(addApi.getDescription().contains("创建"), addApi.getDescription());
    assertTrue(addApi.getRequestBody().getRequired());

    var addSchema = jsonSchema(addApi.getRequestBody(), "application/json");
    assertEquals("object", addSchema.getType());
    assertEquals(Boolean.TRUE, addSchema.getAdditionalProperties());
    assertTrue(addSchema.getRequired().contains("name"), addSchema.getRequired().toString());
    assertEquals(Integer.valueOf(64), addSchema.getProperties().get("name").getMaxLength());
    assertEquals("[A-Z]+", addSchema.getProperties().get("name").getPattern());
    assertEquals("integer", addSchema.getProperties().get("age").getType());
    assertEquals(18, addSchema.getProperties().get("age").getMinimum().intValue());
    assertEquals(60, addSchema.getProperties().get("age").getMaximum().intValue());
    assertEquals("email", addSchema.getProperties().get("email").getFormat());

    var addResponseData = unwrapWebApiResultDataSchema(addApi.getResponses().get("200"));
    assertEquals("object", addResponseData.getType());
    assertTrue(addResponseData.getProperties().containsKey("name"));
    assertTrue(addApi.getResponses().get("200").getDescription().contains("新增成功"));

    var searchApi = getWebApi(context, "GET /demo/customer");
    assertEquals("分页查询客户", searchApi.getSummary());
    assertEquals(ParameterLocation.QUERY, parameter(searchApi, "page").getIn());
    assertEquals("分页页码", parameter(searchApi, "page").getDescription());
    assertEquals("客户名称筛选条件", parameter(searchApi, "name").getDescription());
    assertEquals("integer", parameter(searchApi, "age").getSchema().getType());

    var searchResponseData = unwrapWebApiResultDataSchema(searchApi.getResponses().get("200"));
    assertEquals("integer", searchResponseData.getProperties().get("total").getType());
    assertEquals("array", searchResponseData.getProperties().get("items").getType());
    assertEquals("object", searchResponseData.getProperties().get("items").getItems().getType());

    var getOneApi = getWebApi(context, "GET /demo/customer/{id}");
    assertEquals(Map.of("params.record.id", "${ request.pathVariables['id'] }"), getOneApi.getExtraProp(Map.class, "actionInputMapping").orElseThrow());
    assertEquals("获取客户详情", getOneApi.getSummary());
    assertEquals(ParameterLocation.PATH, parameter(getOneApi, "id").getIn());
    assertTrue(parameter(getOneApi, "id").getRequired());
    assertEquals("客户 ID", parameter(getOneApi, "id").getDescription());

    var putApi = getWebApi(context, "PUT /demo/customer/{id}");
    assertEquals("更新客户", putApi.getSummary());
    assertTrue(putApi.getRequestBody().getRequired());
    assertEquals(Boolean.TRUE, jsonSchema(putApi.getRequestBody(), "application/json").getAdditionalProperties());
    assertFalse(containsRequired(putApi, "name"));

    var patchApi = getWebApi(context, "PATCH /demo/customer/{id}");
    assertEquals("部分更新客户", patchApi.getSummary());
    assertEquals("demo.customer.patchUpdate", patchApi.getOperationId());
    assertEquals(Boolean.TRUE, jsonSchema(patchApi.getRequestBody(), "application/json").getAdditionalProperties());
    assertFalse(containsRequired(patchApi, "name"));

    var deleteApi = getWebApi(context, "DELETE /demo/customer/{id}");
    assertEquals("删除客户", deleteApi.getSummary());
    assertEquals("客户 ID", parameter(deleteApi, "id").getDescription());

    var batchDeleteApi = getWebApi(context, "POST /demo/customer/batch-delete");
    assertEquals("批量删除客户", batchDeleteApi.getSummary());
    var batchDeleteSchema = jsonSchema(batchDeleteApi.getRequestBody(), "application/json");
    assertTrue(batchDeleteSchema.getRequired().contains("ids"));
    assertEquals("array", batchDeleteSchema.getProperties().get("ids").getType());
    assertEquals("string", batchDeleteSchema.getProperties().get("ids").getItems().getType());

    var importApi = getWebApi(context, "POST /demo/customer/import");
    assertEquals("导入客户数据", importApi.getSummary());
    var importSchema = jsonSchema(importApi.getRequestBody(), "multipart/form-data");
    assertEquals("object", importSchema.getType());
    assertTrue(importSchema.getRequired().contains("file"));
    assertEquals("string", importSchema.getProperties().get("file").getType());
    assertEquals("binary", importSchema.getProperties().get("file").getFormat());
  }

  @Test
  void customApiRouteUsesExplicitActionIdentityAndInputSources() throws Exception {
    bindFieldTypeCatalog(new InMemoryFieldTypeCatalog(defaultFieldTypes()));
    var model = customActionModel("approve/{id}/{phase}", "id");
    assertTrue(model.getValidationResult().isValid(), validationErrors(model));
    var context = new ModelProjectionPipelineContext<DefaultBusinessModel>(model);

    new CustomAPIMPS().process(context);

    WebAPIModel projected = context.getModelMap(WebAPIModel.class).values().stream()
        .filter(api -> "approve".equals(api.getExtraProp(String.class, "action").orElse(null)))
        .findFirst().orElseThrow();
    assertEquals(com.ouroboros.webapi.HandlerType.BUSINESS_ACTION, projected.getHandlerType());
    assertEquals("demo.targetedOrder", projected.getExtraProp(String.class, "businessModel").orElseThrow());
    assertEquals(Map.of("params.record.id", "$.pathVariables.id", "data", "$.body"),
        projected.getExtraProp(Map.class, "actionInputMapping").orElseThrow());
  }

  @Test
  void currentCustomApiRequiresExplicitInputMapping() throws Exception {
    bindFieldTypeCatalog(new InMemoryFieldTypeCatalog(defaultFieldTypes()));
    var legacyModel = customActionModel("approve/{id}/{phase}", null);
    var meta = legacyModel.getMeta();
    meta.setFormatVersion("2.0");
    meta.setActions(List.of(legacyModel.getBehavior().getActions().get("approve")));
    meta.setExtraProp("custom", List.of(Map.of("name", "approve", "api", Map.of(
        "isEnabled", true, "implementationMode", "ACTION", "action", "approve",
        "httpMethod", "POST", "url", "approve/{id}/{phase}"))));
    var model = new DefaultBusinessModel(meta, new InMemoryBusinessModelProvider());

    assertFalse(model.getValidationResult().isValid());
    assertTrue(validationErrors(model).contains("actions.approve.api.actionInputMapping-required"),
        validationErrors(model));
  }

  @Test
  void customApiMappingPathVariableMustExistInUrl() throws Exception {
    bindFieldTypeCatalog(new InMemoryFieldTypeCatalog(defaultFieldTypes()));
    var model = customActionModel("approve/{id}/{phase}", "missing");

    assertFalse(model.getValidationResult().isValid());
    assertTrue(validationErrors(model).contains("actions.approve.api.actionInputMapping-invalid"),
        validationErrors(model));
  }

  @Test
  void workflowGeneratedWebApisShouldDescribeParametersAndBodies() throws Exception {
    bindFieldTypeCatalog(new InMemoryFieldTypeCatalog(defaultFieldTypes()));

    var provider = new InMemoryBusinessModelProvider();
    var model = createCustomerModel(provider, true, false);
    var context = new ModelProjectionPipelineContext<DefaultBusinessModel>(model);

    new com.ouroboros.businessmodel.projection.workflow.api.StartProcessAPI().process(context);
    new com.ouroboros.businessmodel.projection.workflow.api.SubmitTaskAPI().process(context);
    new com.ouroboros.businessmodel.projection.workflow.api.InitTaskFormAPI().process(context);
    // ProcessCallbackAPI 已退役：不再手动执行，仅断言其不产生 WebAPI

    var startProcessApi = getWebApi(context, "POST /demo/customer/{id}/start-process");
    assertEquals("发起客户流程", startProcessApi.getSummary());
    assertEquals("客户 ID", parameter(startProcessApi, "id").getDescription());
    assertFalse(Boolean.TRUE.equals(startProcessApi.getRequestBody().getRequired()));
    assertEquals("object", jsonSchema(startProcessApi.getRequestBody(), "application/json").getType());

    var submitTaskApi = getWebApi(context, "POST /demo/customer/{id}/process/instance/{instanceId}/task/{taskId}/{ACTION}");
    assertEquals("提交客户流程任务", submitTaskApi.getSummary());
    assertTrue(submitTaskApi.getDescription().contains("历史包装面"), submitTaskApi.getDescription());
    assertEquals("流程实例 ID", parameter(submitTaskApi, "instanceId").getDescription());
    assertEquals("任务 ID", parameter(submitTaskApi, "taskId").getDescription());
    assertEquals("历史包装面动作标识（legacy workflow action token）", parameter(submitTaskApi, "ACTION").getDescription());
    assertEquals("流程任务提交数据（legacy wrapper；仅描述业务表单数据）", submitTaskApi.getRequestBody().getDescription());
    var submitTaskSchema = jsonSchema(submitTaskApi.getRequestBody(), "application/json");
    assertEquals("object", submitTaskSchema.getType());
    assertTrue(submitTaskSchema.getProperties().containsKey("formData"));
    assertEquals("object", submitTaskSchema.getProperties().get("formData").getType());

    var initTaskFormApi = getWebApi(context, "GET /demo/customer/{id}/process/instance/{instanceId}/task/{taskId}/init-form");
    assertEquals("初始化客户流程任务表单", initTaskFormApi.getSummary());
    assertNull(initTaskFormApi.getRequestBody());
    assertEquals("流程实例 ID", parameter(initTaskFormApi, "instanceId").getDescription());

    // 业务回调直连：ProcessCallbackAPI 不再生成（回调逻辑流经绑定目录直连执行）
    assertWebApiAbsent(context, "POST /demo/customer/{id}/process-callback");
  }

  @Test
  void representativeCustomerModelShouldProjectWorkflowApisThroughCanonicalBundlePath() throws Exception {
    bindFieldTypeCatalog(new InMemoryFieldTypeCatalog(defaultFieldTypes()));

    var provider = new InMemoryBusinessModelProvider();
    var model = createCustomerModel(provider, true, true);

    var bundle = model.buildModelBundle();

    assertTrue(model.getExtraPropDeep(Boolean.class, "workflow", "isEnabled").orElse(false));

    var startProcessApi = getWebApi(bundle, "POST /demo/customer/{id}/start-process");
    assertEquals("发起客户流程", startProcessApi.getSummary());
    assertEquals("客户 ID", parameter(startProcessApi, "id").getDescription());
    assertWorkflowLogicFlowTarget(bundle, startProcessApi, "demo.customer.startProcess");

    var submitTaskApi = getWebApi(bundle, "POST /demo/customer/{id}/process/instance/{instanceId}/task/{taskId}/{ACTION}");
    assertEquals("提交客户流程任务", submitTaskApi.getSummary());
    assertEquals("流程实例 ID", parameter(submitTaskApi, "instanceId").getDescription());

    var initTaskFormApi = getWebApi(bundle, "GET /demo/customer/{id}/process/instance/{instanceId}/task/{taskId}/init-form");
    assertWorkflowLogicFlowTarget(bundle, initTaskFormApi, "demo.customer.initTaskForm");

    // 业务回调直连：ProcessCallbackAPI 不再生成（回调逻辑流经绑定目录直连执行）
    assertWebApiAbsent(bundle, "POST /demo/customer/{id}/process-callback");
    assertWorkflowLogicFlowTarget(bundle, submitTaskApi, "demo.customer.submitTask");

    var workflowAuthority = bundle.getModel(Authority.class, "demo.customer.workflow")
        .orElseThrow(() -> new AssertionError("missing workflow authority"));
    assertTrue(workflowAuthority.getApis().contains("POST /api/demo/customer/{id}/start-process"));
    assertTrue(workflowAuthority.getApis().contains("GET /api/demo/customer/{id}/process/instance/{instanceId}/task/{taskId}/init-form"));
    assertTrue(workflowAuthority.getApis().contains("POST /api/demo/customer/{id}/process/instance/{instanceId}/task/{taskId}/{ACTION}"));
    assertTrue(workflowAuthority.getUiViews().contains("/demo/customer/{id}/approval"));
    assertTrue(workflowAuthority.getUiViews().contains("/demo/customer/{id}/process-detail"));
  }

  @Test
  void representativeCustomerModelShouldExposeChildRelationInCrudOpenApiThroughCanonicalBundlePath() throws Exception {
    bindFieldTypeCatalog(new InMemoryFieldTypeCatalog(defaultFieldTypes()));

    var provider = new InMemoryBusinessModelProvider();
    var model = createCustomerModel(provider, true, true);

    var bundle = model.buildModelBundle();

    var addApi = getWebApi(bundle, "POST /demo/customer");
    var addSchema = jsonSchema(addApi.getRequestBody(), "application/json");

    var contactsSchema = addSchema.getProperties().get("contacts");
    assertNotNull(contactsSchema, "missing contacts property");
    assertEquals("array", contactsSchema.getType());

    var contactItem = contactsSchema.getItems();
    assertNotNull(contactItem, "missing contacts items");
    assertEquals("object", contactItem.getType());

    var required = contactItem.getRequired();
    assertNotNull(required, "missing contacts items required");
    assertTrue(required.contains("contactName"), required.toString());
  }

  @Test
  void representativeCustomerModelShouldExposeReadOnlyDevAddUiAndSchemaInCanonicalBundle() throws Exception {
    bindFieldTypeCatalog(new InMemoryFieldTypeCatalog(defaultFieldTypes()));

    var provider = new InMemoryBusinessModelProvider();
    var model = createCustomerModel(provider, true, true);

    var bundle = model.buildModelBundle();
    var addPath = "/demo/customer/add";

    var addUiModel = bundle.getModel(UIModel.class, addPath)
        .orElseThrow(() -> new AssertionError("missing add-form UIModel"));
    assertEquals(addPath, addUiModel.getPath());
    assertEquals(addPath, addUiModel.getExtraProp(String.class, "schemaId").orElse(null));

    bundle.getModel(SchemaNode.class, addPath)
        .orElseThrow(() -> new AssertionError("missing add-form SchemaNode"));
  }

  @Test
  void approvalWorkflowBusinessModelSeedShouldExposeRuntimeUiSchemasInCanonicalBundle() throws Exception {
    bindFieldTypeCatalog(defaultRuntimeFieldTypeCatalog());

    var provider = new InMemoryBusinessModelProvider();
    var model = new DefaultBusinessModel(readApprovalWorkflowBusinessModelSeed(), provider);
    provider.add(model);

    assertTrue(model.isValid(), validationErrors(model));
    assertTrue(model.isRelationValid(), validationErrors(model));

    var bundle = model.buildModelBundle();

    assertEquals("awf-expense-request", model.getFullName());
    assertTrue(bundle.getModel(SchemaNode.class, "/awf-expense-request").isPresent(), "missing list SchemaNode");
    assertTrue(bundle.getModel(SchemaNode.class, "/awf-expense-request/add").isPresent(), "missing add SchemaNode");
    assertTrue(bundle.getModel(SchemaNode.class, "/awf-expense-request/{id}/approval").isPresent(), "missing approval SchemaNode");
    assertTrue(bundle.getModel(SchemaNode.class, "/awf-expense-request/{id}/process-detail").isPresent(), "missing process-detail SchemaNode");

    var startProcessApi = getWebApi(bundle, "POST /awf-expense-request/{id}/start-process");
    assertWorkflowLogicFlowTarget(bundle, startProcessApi, "awf-expense-request.startProcess");
    var submitTaskApi = getWebApi(bundle, "POST /awf-expense-request/{id}/process/instance/{instanceId}/task/{taskId}/{ACTION}");
    assertWorkflowLogicFlowTarget(bundle, submitTaskApi, "awf-expense-request.submitTask");
  }

  @Test
  void addLogicFlowShouldContainResolvedValidationSchemaForNestedInput() throws Exception {
    bindFieldTypeCatalog(new InMemoryFieldTypeCatalog(defaultFieldTypes()));

    var provider = new InMemoryBusinessModelProvider();
    var model = createCustomerModel(provider, false, true);

    var context = new ModelProjectionPipelineContext<DefaultBusinessModel>(model);
    context.setAttribute("hasDataModel", true);
    seedCurrentDataModelMeta(context);

    new com.ouroboros.businessmodel.projection.logicflow.AddMPS().process(context);

    var addFlow = context.getModel(FlowModelMeta.class, "demo.customer.add").orElseThrow(AssertionError::new);
    var validateNode = validateNode(addFlow);
    var validationSchema = validateNode.getExtraProp(Map.class, "schema").orElseThrow(AssertionError::new);

    assertFalse(validationSchema.isEmpty());
    assertEquals("object", validationSchema.get("type"));
    assertEquals(Boolean.TRUE, validationSchema.get("additionalProperties"));

    @SuppressWarnings("unchecked")
    var properties = (Map<String, Object>) validationSchema.get("properties");
    @SuppressWarnings("unchecked")
    var required = (List<String>) validationSchema.get("required");
    assertTrue(required.contains("name"), required.toString());
    assertFalse(required.contains("id"), required.toString());
    assertTrue(properties.containsKey("id"));

    @SuppressWarnings("unchecked")
    var nameSchema = (Map<String, Object>) properties.get("name");
    assertEquals("string", nameSchema.get("type"));
    assertEquals(64, nameSchema.get("maxLength"));
    assertEquals("[A-Z]+", nameSchema.get("pattern"));

    @SuppressWarnings("unchecked")
    var contactsSchema = (Map<String, Object>) properties.get("contacts");
    assertEquals("array", contactsSchema.get("type"));
    @SuppressWarnings("unchecked")
    var contactItemSchema = (Map<String, Object>) contactsSchema.get("items");
    @SuppressWarnings("unchecked")
    var contactRequired = (List<String>) contactItemSchema.get("required");
    assertTrue(contactRequired.contains("contactName"), String.valueOf(contactRequired));
    assertEquals(Boolean.TRUE, contactItemSchema.get("additionalProperties"));
  }

  @Test
  void updateLogicFlowShouldUsePartialValidationSchema() throws Exception {
    bindFieldTypeCatalog(new InMemoryFieldTypeCatalog(defaultFieldTypes()));

    var provider = new InMemoryBusinessModelProvider();
    var model = createCustomerModel(provider, false, true);

    var context = new ModelProjectionPipelineContext<DefaultBusinessModel>(model);
    context.setAttribute("hasDataModel", true);
    seedCurrentDataModelMeta(context);

    new com.ouroboros.businessmodel.projection.logicflow.UpdateMPS().process(context);

    var updateFlow = context.getModel(FlowModelMeta.class, "demo.customer.update").orElseThrow(AssertionError::new);
    var validateNode = validateNode(updateFlow);
    var validationSchema = validateNode.getExtraProp(Map.class, "schema").orElseThrow(AssertionError::new);

    assertFalse(validationSchema.isEmpty());
    assertEquals("object", validationSchema.get("type"));
    assertEquals(Boolean.TRUE, validationSchema.get("additionalProperties"));
    @SuppressWarnings("unchecked")
    var properties = (Map<String, Object>) validationSchema.get("properties");
    @SuppressWarnings("unchecked")
    var required = (List<String>) validationSchema.get("required");
    assertTrue(required == null || !required.contains("name"), String.valueOf(required));
    assertTrue(properties.containsKey("contacts"));
  }

  @Test
  void createAndUpdateValidationSchemaShouldAllowGeneratedAndUnknownFields() throws Exception {
    bindFieldTypeCatalog(new InMemoryFieldTypeCatalog(defaultFieldTypes()));

    var provider = new InMemoryBusinessModelProvider();
    var model = createCustomerModel(provider, false, true);

    var createSchema = BusinessModelSchemaSupport.createValidationSchema(model);
    var updateSchema = BusinessModelSchemaSupport.updateValidationSchema(model);

    assertTrue(JsonSchemas.validate(createSchema, stringMap(
        "name", "ACME",
        "createdAt", "2026-07-08T10:00:00Z",
        "updatedAt", "2026-07-08T10:00:00Z",
        "createdBy", "u-1",
        "updatedBy", "u-2",
        "deletedAt", "2026-07-08T11:00:00Z",
        "deletedBy", "u-3",
        "isDeleted", Boolean.FALSE,
        "extraField", "extra",
        "contacts", Arrays.asList(stringMap(
            "id", "CONTACT-001",
            "contactName", "ALICE",
            "createdAt", "2026-07-08T10:00:00Z",
            "nonModelField", "still-allowed"
        ))
    )));
    assertTrue(JsonSchemas.validate(updateSchema, stringMap(
        "createdAt", "2026-07-08T10:00:00Z",
        "updatedBy", "u-2",
        "isDeleted", Boolean.FALSE,
        "extraField", "extra"
    )));
  }

  @Test
  void generatedValidationSchemaShouldDeclareDraft202012Dialect() throws Exception {
    bindFieldTypeCatalog(new InMemoryFieldTypeCatalog(defaultFieldTypes()));

    var provider = new InMemoryBusinessModelProvider();
    var model = createCustomerModel(provider, false, false);

    var validationSchema = BusinessModelSchemaSupport.createValidationSchema(model);

    assertEquals("https://json-schema.org/draft/2020-12/schema", validationSchema.get("$schema"));
    @SuppressWarnings("unchecked")
    var properties = (Map<String, Object>) validationSchema.get("properties");
    @SuppressWarnings("unchecked")
    var emailSchema = (Map<String, Object>) properties.get("email");
    assertNotNull(emailSchema);
    assertFalse(emailSchema.containsKey("nullable"), String.valueOf(emailSchema));
  }

  @Test
  void specialReferenceFieldsShouldExposeUnionSchemasForRequestsAndValidation() throws Exception {
    bindFieldTypeCatalog(new InMemoryFieldTypeCatalog(defaultFieldTypes()));

    var provider = new InMemoryBusinessModelProvider();
    var model = createOrderModel(provider);

    var context = new ModelProjectionPipelineContext<DefaultBusinessModel>(model);
    context.setAttribute("hasDataModel", true);

    new com.ouroboros.businessmodel.projection.webapi.AddMPS().process(context);
    new com.ouroboros.businessmodel.projection.logicflow.AddMPS().process(context);

    var addApi = getWebApi(context, "POST /demo/order");
    var addSchema = jsonSchema(addApi.getRequestBody(), "application/json");
    var properties = addSchema.getProperties();
    assertUnionScalarOrObject(properties.get("customer"), "string", "code", "string");
    assertUnionScalarOrObject(properties.get("priorityTemplate"), "integer", "level", "integer");
    assertUnionScalarOrObject(properties.get("approver"), "string", "id", "string");
    assertUnionScalarOrObject(properties.get("department"), "string", "id", "string");
    assertUnionScalarOrObject(properties.get("status"), "string", "code", "string");
    assertEquals(Boolean.TRUE, properties.get("customer").getNullable());
    assertEquals(Boolean.TRUE, properties.get("priorityTemplate").getNullable());
    assertEquals(Boolean.TRUE, properties.get("approver").getNullable());
    assertEquals(Boolean.TRUE, properties.get("department").getNullable());
    assertEquals(Boolean.TRUE, properties.get("status").getNullable());

    var addFlow = context.getModel(FlowModelMeta.class, "demo.order.add").orElseThrow(AssertionError::new);
    var validateNode = validateNode(addFlow);
    var validationSchema = validateNode.getExtraProp(Map.class, "schema").orElseThrow(AssertionError::new);
    @SuppressWarnings("unchecked")
    var validationProperties = (Map<String, Object>) validationSchema.get("properties");
    assertUnionScalarOrObjectMap(castMap(validationProperties.get("customer")), "string", "code", "string", true);
    assertUnionScalarOrObjectMap(castMap(validationProperties.get("priorityTemplate")), "integer", "level", "integer", true);
    assertUnionScalarOrObjectMap(castMap(validationProperties.get("approver")), "string", "id", "string", true);
    assertUnionScalarOrObjectMap(castMap(validationProperties.get("department")), "string", "id", "string", true);
    assertUnionScalarOrObjectMap(castMap(validationProperties.get("status")), "string", "code", "string", true);

    assertTrue(JsonSchemas.validate(validationSchema, stringMap(
        "id", "ORD-001",
        "customer", "C001",
        "priorityTemplate", Integer.valueOf(3),
        "approver", "user-1",
        "department", "org-1",
        "status", "enabled"
    )));
    assertTrue(JsonSchemas.validate(validationSchema, stringMap(
        "id", "ORD-002",
        "customer", stringMap("code", "C002"),
        "priorityTemplate", stringMap("level", Integer.valueOf(2)),
        "approver", stringMap("id", "user-2"),
        "department", stringMap("id", "org-2"),
        "status", stringMap("code", "pending")
    )));
    assertTrue(JsonSchemas.validate(validationSchema, stringMap(
        "id", "ORD-003",
        "customer", null,
        "priorityTemplate", null,
        "approver", null,
        "department", null,
        "status", null
    )));
    assertFalse(JsonSchemas.validate(validationSchema, stringMap(
        "id", "ORD-004",
        "customer", stringMap("id", "C004")
    )));
  }

  private static WebAPIModel getWebApi(ModelProjectionPipelineContext<DefaultBusinessModel> context, String api) {
    return context.getModel(WebAPIModel.class, api).orElseThrow(() -> new AssertionError("missing api " + api));
  }

  private static void assertWebApiAbsent(ModelProjectionPipelineContext<DefaultBusinessModel> context, String api) {
    boolean present = context.getModelMap(WebAPIModel.class).values().stream()
        .anyMatch(model -> api.equals(model.getMethod() + " " + model.getPath()));
    assertFalse(present, "不应再生成回调 WebAPI: " + api);
  }

  private static void assertWebApiAbsent(com.ouroboros.businessmodel.projection.ModelBundle bundle, String api) {
    boolean present = bundle.getModelMap(WebAPIModel.class).values().stream()
        .anyMatch(model -> api.equals(model.getMethod() + " " + model.getPath()));
    assertFalse(present, "不应再生成回调 WebAPI: " + api);
  }

  private static WebAPIModel getWebApi(com.ouroboros.businessmodel.projection.ModelBundle bundle, String api) {
    return bundle.getModel(WebAPIModel.class, api).orElseThrow(() -> new AssertionError("missing api " + api));
  }

  private static ParameterModel parameter(WebAPIModel api, String name) {
    return api.getParameters().stream()
        .filter(parameter -> name.equals(parameter.getName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing parameter " + name));
  }

  private static void assertWorkflowLogicFlowTarget(
      com.ouroboros.businessmodel.projection.ModelBundle bundle,
      WebAPIModel api,
      String expectedLogicFlow
  ) {
    assertEquals(HandlerType.BUSINESS_ACTION, api.getHandlerType());
    int separator = expectedLogicFlow.lastIndexOf('.');
    assertEquals(expectedLogicFlow.substring(0, separator), api.getExtraProp(String.class, "businessModel").orElseThrow());
    assertEquals(expectedLogicFlow.substring(separator + 1), api.getExtraProp(String.class, "action").orElseThrow());
    assertTrue(bundle.getModel(FlowModelMeta.class, expectedLogicFlow).isPresent(), "missing logic flow " + expectedLogicFlow);
  }

  private static void assertUnionScalarOrObject(
      SchemaModel schema,
      String scalarType,
      String propertyName,
      String propertyType
  ) {
    assertNotNull(schema);
    var oneOf = schema.getOneOf();
    assertNotNull(oneOf, "missing oneOf");
    assertEquals(2, oneOf.size());

    var scalarOption = oneOf.stream()
        .filter(option -> scalarType.equals(option.getType()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing scalar option " + scalarType));
    assertEquals(scalarType, scalarOption.getType());

    var objectOption = oneOf.stream()
        .filter(option -> "object".equals(option.getType()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing object option"));
    assertEquals(Boolean.TRUE, objectOption.getAdditionalProperties());
    assertNotNull(objectOption.getRequired());
    assertTrue(objectOption.getRequired().contains(propertyName), String.valueOf(objectOption.getRequired()));
    assertNotNull(objectOption.getProperties());
    assertEquals(propertyType, objectOption.getProperties().get(propertyName).getType());
  }

  private static void assertUnionScalarOrObjectMap(
      Map<String, Object> schema,
      String scalarType,
      String propertyName,
      String propertyType,
      boolean nullable
  ) {
    assertNotNull(schema);
    @SuppressWarnings("unchecked")
    var oneOf = (List<Map<String, Object>>) schema.get("oneOf");
    assertNotNull(oneOf, "missing oneOf");
    assertEquals(nullable ? 3 : 2, oneOf.size());

    var scalarOption = oneOf.stream()
        .filter(option -> scalarType.equals(option.get("type")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing scalar option " + scalarType));
    assertEquals(scalarType, scalarOption.get("type"));

    var objectOption = oneOf.stream()
        .filter(option -> "object".equals(option.get("type")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing object option"));
    assertEquals(Boolean.TRUE, objectOption.get("additionalProperties"));
    @SuppressWarnings("unchecked")
    var required = (List<String>) objectOption.get("required");
    assertNotNull(required);
    assertTrue(required.contains(propertyName), String.valueOf(required));
    @SuppressWarnings("unchecked")
    var properties = (Map<String, Object>) objectOption.get("properties");
    assertNotNull(properties);
    @SuppressWarnings("unchecked")
    var propertySchema = (Map<String, Object>) properties.get(propertyName);
    assertEquals(propertyType, propertySchema.get("type"));

    if (nullable) {
      assertTrue(oneOf.stream().anyMatch(option -> "null".equals(option.get("type"))), String.valueOf(oneOf));
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castMap(Object value) {
    return (Map<String, Object>) value;
  }

  private static boolean containsRequired(WebAPIModel api, String propertyName) {
    var schema = jsonSchema(api.getRequestBody(), "application/json");
    return schema.getRequired() != null && schema.getRequired().contains(propertyName);
  }

  private static SchemaModel jsonSchema(com.ouroboros.webapi.openapi.RequestBodyModel requestBody, String contentType) {
    assertNotNull(requestBody);
    var mediaType = requestBody.getContent().get(contentType);
    assertNotNull(mediaType, "missing content type " + contentType);
    return mediaType.getSchema();
  }

  private static SchemaModel unwrapWebApiResultDataSchema(ResponseModel response) {
    assertNotNull(response);
    var schema = mediaTypeSchema(response, "application/json");
    assertEquals("object", schema.getType());
    assertTrue(schema.getProperties().containsKey("code"));
    assertTrue(schema.getProperties().containsKey("msg"));
    assertTrue(schema.getProperties().containsKey("data"));
    return schema.getProperties().get("data");
  }

  private static SchemaModel mediaTypeSchema(ResponseModel response, String contentType) {
    Map<String, MediaTypeModel> content = response.getContent();
    assertNotNull(content);
    var mediaType = content.get(contentType);
    assertNotNull(mediaType, "missing content type " + contentType);
    assertNotNull(mediaType.getSchema());
    return mediaType.getSchema();
  }

  private static FlowNodeMeta validateNode(FlowModelMeta flowModel) {
    return flowModel.getNodes().stream()
        .filter(node -> "validate".equals(node.getType()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing validate node"));
  }

  private static DefaultBusinessModel createCustomerModel(
      InMemoryBusinessModelProvider provider,
      boolean workflowEnabled,
      boolean withChildRelation
  ) {
    var fields = new ArrayList<BusinessModelFieldMeta>();
    fields.add(field("id", "客户 ID", "雪花主键", true, true, stringMap("size", 32, "regexp", ".*")));
    fields.add(field("name", "客户名称", "单行文本", true, false, stringMap("size", 64, "regexp", "[A-Z]+")));
    fields.add(field("age", "年龄", "整数数字", false, false, stringMap("min", 18, "max", 60)));
    fields.add(field("email", "邮箱", "电子邮箱", false, false, stringMap("size", 128)));

    var relations = new ArrayList<BusinessModelRelationMeta>();
    if (withChildRelation) {
      relations.add(childRelation("contacts", "联系人", "demo.customerContact", "customerId", "id", true));
    }

    var modelMeta = new DefaultBusinessModelMeta();
    modelMeta.setName("customer");
    modelMeta.setNamespace("demo");
    modelMeta.setTitle("客户");
    modelMeta.setDescription("客户业务模型");
    modelMeta.setIsEnabled(true);
    modelMeta.setFields(fields);
    modelMeta.setRelations(relations);
    modelMeta.setExtraProp("apis", enabledCrudApis());
    modelMeta.setExtraProp("features", stringMap("isEnabledBatchDelete", true, "isEnabledImport", true));
    if (workflowEnabled) {
      modelMeta.setExtraProp("workflow", workflowConfig());
    }

    var model = new DefaultBusinessModel(modelMeta, provider);
    provider.add(model);
    if (withChildRelation) {
      provider.add(createCustomerContactModel(provider));
    }
    return model;
  }

  private static DefaultBusinessModel customActionModel(String url, String pathVariable) {
    var meta = new DefaultBusinessModelMeta();
    meta.setName("targetedOrder");
    meta.setNamespace("demo");
    meta.setTitle("目标订单");
    meta.setIsEnabled(true);
    meta.setFields(List.of(field("id", "订单 ID", "单行文本", true, true,
        stringMap("size", 32, "regexp", ".*"))));
    var api = stringMap(
        "isEnabled", true,
        "isGenerateImplementation", true,
        "httpMethod", "POST",
        "url", url);
    if (pathVariable != null) {
      api.put("actionInputMapping", Map.of("params.record.id", "$.pathVariables." + pathVariable, "data", "$.body"));
    }
    meta.setExtraProp("custom", List.of(stringMap(
        "name", "approve",
        "api", api)));
    return new DefaultBusinessModel(meta, new InMemoryBusinessModelProvider());
  }

  private static DefaultBusinessModel createCustomerContactModel(InMemoryBusinessModelProvider provider) {
    var fields = new ArrayList<BusinessModelFieldMeta>();
    fields.add(field("id", "联系人 ID", "单行文本", true, true, stringMap("size", 32, "regexp", ".*")));
    fields.add(field("customerId", "客户 ID", "雪花主键", false, false, stringMap("size", 32, "regexp", ".*")));
    fields.add(field("contactName", "联系人名称", "单行文本", true, false, stringMap("size", 32, "regexp", ".*")));
    fields.add(field("phone", "联系电话", "单行文本", false, false, stringMap("size", 20, "regexp", ".*")));

    var modelMeta = new DefaultBusinessModelMeta();
    modelMeta.setName("customerContact");
    modelMeta.setNamespace("demo");
    modelMeta.setTitle("客户联系人");
    modelMeta.setDescription("客户联系人业务模型");
    modelMeta.setIsEnabled(true);
    modelMeta.setFields(fields);
    modelMeta.setRelations(Collections.emptyList());
    modelMeta.setExtraProp("apis", enabledCrudApis());

    return new DefaultBusinessModel(modelMeta, provider);
  }

  private static DefaultBusinessModel createOrderModel(InMemoryBusinessModelProvider provider) {
    provider.add(createCustomerLookupModel(provider));
    provider.add(createPriorityTemplateModel(provider));

    var fields = new ArrayList<BusinessModelFieldMeta>();
    fields.add(field("id", "订单 ID", "单行文本", true, true, stringMap("size", 32, "regexp", ".*")));
    fields.add(field("approver", "审批人", "员工", false, false, Collections.emptyMap()));
    fields.add(field("department", "所属组织", "组织架构", false, false, Collections.emptyMap()));
    fields.add(field("status", "订单状态", "数据字典", false, false, stringMap("dictName", "orderStatus")));

    var relations = new ArrayList<BusinessModelRelationMeta>();
    relations.add(relation("customer", "客户", "demo.customerLookup", "reference", "code", false));
    relations.add(relation("priorityTemplate", "优先级模板", "demo.priorityTemplate", "reference", "level", false));

    var modelMeta = new DefaultBusinessModelMeta();
    modelMeta.setName("order");
    modelMeta.setNamespace("demo");
    modelMeta.setTitle("订单");
    modelMeta.setDescription("订单业务模型");
    modelMeta.setIsEnabled(true);
    modelMeta.setFields(fields);
    modelMeta.setRelations(relations);
    modelMeta.setExtraProp("apis", enabledCrudApis());

    var model = new DefaultBusinessModel(modelMeta, provider);
    provider.add(model);
    return model;
  }

  private static void seedCurrentDataModelMeta(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    new com.ouroboros.businessmodel.projection.data.basic.BasicMPS().process(context);
    new com.ouroboros.businessmodel.projection.data.basic.PrimaryKeyMPS().process(context);
  }

  private static DefaultBusinessModel createCustomerLookupModel(InMemoryBusinessModelProvider provider) {
    var fields = new ArrayList<BusinessModelFieldMeta>();
    fields.add(field("id", "客户主键", "单行文本", true, true, stringMap("size", 32, "regexp", ".*")));
    fields.add(field("code", "客户编码", "单行文本", true, false, stringMap("size", 32, "regexp", "[A-Z0-9_]+")));

    var modelMeta = new DefaultBusinessModelMeta();
    modelMeta.setName("customerLookup");
    modelMeta.setNamespace("demo");
    modelMeta.setTitle("客户档案");
    modelMeta.setDescription("客户档案业务模型");
    modelMeta.setIsEnabled(true);
    modelMeta.setFields(fields);
    modelMeta.setRelations(Collections.emptyList());
    modelMeta.setExtraProp("apis", enabledCrudApis());

    return new DefaultBusinessModel(modelMeta, provider);
  }

  private static DefaultBusinessModel createPriorityTemplateModel(InMemoryBusinessModelProvider provider) {
    var fields = new ArrayList<BusinessModelFieldMeta>();
    fields.add(field("id", "模板 ID", "单行文本", true, true, stringMap("size", 32, "regexp", ".*")));
    fields.add(field("level", "优先级等级", "整数数字", true, false, stringMap("min", 1, "max", 9)));

    var modelMeta = new DefaultBusinessModelMeta();
    modelMeta.setName("priorityTemplate");
    modelMeta.setNamespace("demo");
    modelMeta.setTitle("优先级模板");
    modelMeta.setDescription("优先级模板业务模型");
    modelMeta.setIsEnabled(true);
    modelMeta.setFields(fields);
    modelMeta.setRelations(Collections.emptyList());
    modelMeta.setExtraProp("apis", enabledCrudApis());

    return new DefaultBusinessModel(modelMeta, provider);
  }

  private static BusinessModelFieldMeta field(
      String name,
      String title,
      String type,
      boolean required,
      boolean primaryKey,
      Map<String, Object> extraProps
  ) {
    var fieldMeta = new BusinessModelFieldMeta();
    fieldMeta.setName(name);
    fieldMeta.setTitle(title);
    fieldMeta.setDescription(title);
    fieldMeta.setType(type);
    fieldMeta.setIsRequired(required);
    fieldMeta.setIsPrimaryKey(primaryKey);
    fieldMeta.setExtraProps(extraProps);
    return fieldMeta;
  }

  private static BusinessModelRelationMeta childRelation(
      String name,
      String title,
      String modelName,
      String referenceKey,
      String key,
      boolean required
  ) {
    var relationMeta = new BusinessModelRelationMeta();
    relationMeta.setName(name);
    relationMeta.setTitle(title);
    relationMeta.setDescription(title);
    relationMeta.setModel(modelName);
    relationMeta.setType("child");
    relationMeta.setSubmitTogether(true);
    relationMeta.setIsRequired(required);
    relationMeta.setReferenceKey(referenceKey);
    relationMeta.setExtraProp("key", key);
    return relationMeta;
  }

  private static BusinessModelRelationMeta relation(
      String name,
      String title,
      String modelName,
      String type,
      String referenceKey,
      boolean required
  ) {
    var relationMeta = new BusinessModelRelationMeta();
    relationMeta.setName(name);
    relationMeta.setTitle(title);
    relationMeta.setDescription(title);
    relationMeta.setModel(modelName);
    relationMeta.setType(type);
    relationMeta.setIsRequired(required);
    relationMeta.setReferenceKey(referenceKey);
    return relationMeta;
  }

  private static Map<String, Object> enabledCrudApis() {
    var apis = new LinkedHashMap<String, Object>();
    apis.put("search", stringMap("isEnabled", true));
    apis.put("getOne", stringMap("isEnabled", true));
    apis.put("add", stringMap("isEnabled", true));
    apis.put("update", stringMap("isEnabled", true));
    apis.put("delete", stringMap("isEnabled", true));
    apis.put("statusFlow", stringMap("isEnabled", true));
    return apis;
  }

  private static Map<String, Object> workflowConfig() {
    var statusMap = new LinkedHashMap<String, Object>();
    statusMap.put("draft", status("DRAFT", "草稿"));
    statusMap.put("submitted", status("SUBMITTED", "已提交"));
    statusMap.put("pending", status("PENDING", "待处理"));
    statusMap.put("processing", status("PROCESSING", "处理中"));
    statusMap.put("suspended", status("SUSPENDED", "已暂停"));
    statusMap.put("withdrawn", status("WITHDRAWN", "已撤回"));
    statusMap.put("approved", status("APPROVED", "已通过"));
    statusMap.put("veto", status("VETO", "已否决"));
    statusMap.put("canceled", status("CANCELED", "已撤销"));
    statusMap.put("terminated", status("TERMINATED", "已终止"));

    var workflow = new LinkedHashMap<String, Object>();
    workflow.put("isEnabled", true);
    workflow.put("bindings", Collections.singletonList(stringMap(
        "key", "default",
        "processId", "customer-approval",
        "statusField", "status",
        "statusMap", statusMap,
        "trigger", "manual"
    )));
    return workflow;
  }

  private static Map<String, Object> status(String code, String label) {
    var status = new LinkedHashMap<String, Object>();
    status.put("code", code);
    status.put("label", label);
    return status;
  }

  private static Map<String, FieldTypeDefinition> defaultFieldTypes() {
    var definitions = new LinkedHashMap<String, FieldTypeDefinition>();
    definitions.put("单行文本", textFieldType());
    definitions.put("雪花主键", snowflakeFieldType());
    definitions.put("整数数字", integerFieldType());
    definitions.put("电子邮箱", emailFieldType());
    definitions.put("员工", employeeFieldType());
    definitions.put("组织架构", organizationFieldType());
    definitions.put("数据字典", dataDictFieldType());
    return definitions;
  }

  private static FieldTypeDefinition textFieldType() {
    var definition = new FieldTypeDefinition();
    definition.setName("单行文本");
    definition.setSource(FieldTypeDefinitionSource.DEFAULT);
    definition.setSourceLocation("test");
    definition.setDefaultParams(stringMap("size", 100, "regexp", ".*"));
    definition.setDataType(stringMap("type", "String", "size", "${size}"));
    definition.setValidations(stringMap("matchRegexp", "${regexp}"));
    return definition;
  }

  private static FieldTypeDefinition snowflakeFieldType() {
    var definition = new FieldTypeDefinition();
    definition.setName("雪花主键");
    definition.setSource(FieldTypeDefinitionSource.DEFAULT);
    definition.setSourceLocation("test");
    definition.setDefaultParams(stringMap("size", 32));
    definition.setDataType(stringMap("type", "String", "size", "${size}"));
    definition.setPrimaryKeyGenerator("snowflake");
    return definition;
  }

  private static FieldTypeDefinition integerFieldType() {
    var definition = new FieldTypeDefinition();
    definition.setName("整数数字");
    definition.setSource(FieldTypeDefinitionSource.DEFAULT);
    definition.setSourceLocation("test");
    definition.setDataType(stringMap("type", "Integer"));
    definition.setDefaultParams(Collections.emptyMap());
    return definition;
  }

  private static FieldTypeDefinition emailFieldType() {
    var definition = new FieldTypeDefinition();
    definition.setName("电子邮箱");
    definition.setSource(FieldTypeDefinitionSource.DEFAULT);
    definition.setSourceLocation("test");
    definition.setDefaultParams(stringMap("size", 100));
    definition.setDataType(stringMap("type", "String", "size", "${size}"));
    definition.setValidations(stringMap("isEmail", true));
    return definition;
  }

  private static FieldTypeDefinition employeeFieldType() {
    var definition = new FieldTypeDefinition();
    definition.setName("员工");
    definition.setSource(FieldTypeDefinitionSource.DEFAULT);
    definition.setSourceLocation("test");
    definition.setDefaultParams(Collections.emptyMap());
    definition.setDataType(stringMap("type", "Employee", "size", 20));
    return definition;
  }

  private static FieldTypeDefinition organizationFieldType() {
    var definition = new FieldTypeDefinition();
    definition.setName("组织架构");
    definition.setSource(FieldTypeDefinitionSource.DEFAULT);
    definition.setSourceLocation("test");
    definition.setDefaultParams(Collections.emptyMap());
    definition.setDataType(stringMap("type", "Organization", "size", 20));
    return definition;
  }

  private static FieldTypeDefinition dataDictFieldType() {
    var definition = new FieldTypeDefinition();
    definition.setName("数据字典");
    definition.setSource(FieldTypeDefinitionSource.DEFAULT);
    definition.setSourceLocation("test");
    definition.setDefaultParams(Collections.emptyMap());
    definition.setDataType(stringMap("type", "DataDictItem", "size", 32, "dictName", "${dictName}"));
    return definition;
  }

  private static void bindFieldTypeCatalog(FieldTypeCatalog fieldTypeCatalog) throws Exception {
    var applicationContext = new StaticApplicationContext();
    applicationContext.getBeanFactory().registerSingleton("fieldTypeCatalog", fieldTypeCatalog);
    contextBridge.destroy();
    contextBridge.setApplicationContext((org.springframework.context.ApplicationContext) applicationContext);
  }

  private static FieldTypeCatalog defaultRuntimeFieldTypeCatalog() {
    return new DefaultFieldTypeCatalog(
        List.of(new JarFieldTypeDefinitionProvider(new FieldTypeDefinitionReader())),
        new DefaultFieldTypeCompiler()
    );
  }

  private static DefaultBusinessModelMeta readApprovalWorkflowBusinessModelSeed() throws IOException {
    var fixture = findFromUserDir("testing/common/fixtures/data/approval-workflow-business-model-seed/business-model.json");
    var root = Json.toMap(Files.readString(fixture));
    var businessModel = toStringKeyMap(root.get("businessModel"));
    businessModel.putIfAbsent("modelType", "default");
    return Json.tryToBean(DefaultBusinessModelMeta.class, Json.toJsonString(businessModel)).get();
  }

  private static Path findFromUserDir(String relativePath) {
    var start = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    for (Path current = start; current != null; current = current.getParent()) {
      var candidate = current.resolve(relativePath);
      if (Files.isRegularFile(candidate)) {
        return candidate;
      }
    }
    throw new AssertionError("missing fixture " + relativePath + " from " + start);
  }

  private static String validationErrors(DefaultBusinessModel model) {
    return model.getValidationResult().getErrors().stream()
        .map(Throwable::getMessage)
        .collect(Collectors.joining("; "));
  }

  private static Map<String, Object> toStringKeyMap(Object value) {
    if (!(value instanceof Map<?, ?> rawMap)) {
      throw new AssertionError("expected map value");
    }
    var result = new LinkedHashMap<String, Object>();
    rawMap.forEach((key, entryValue) -> result.put(String.valueOf(key), entryValue));
    return result;
  }

  private static Map<String, Object> stringMap(Object... keyValues) {
    var map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
    }
    return map;
  }

  private static class InMemoryBusinessModelProvider implements BusinessModelProvider {
    private final Map<String, BusinessModel> models = new LinkedHashMap<String, BusinessModel>();

    private void add(DefaultBusinessModel model) {
      models.put(model.getFullName(), model);
    }

    @Override
    public Optional<BusinessModel> getBusinessModel(String fullName) {
      return Optional.ofNullable(models.get(fullName));
    }

    @Override
    public Map<String, BusinessModel> getBusinessModelMap() {
      return Collections.unmodifiableMap(models);
    }
  }

  private static class InMemoryFieldTypeCatalog implements FieldTypeCatalog {
    private final Map<String, FieldTypeDefinition> definitions;
    private final DefaultFieldTypeCompiler compiler = new DefaultFieldTypeCompiler();

    private InMemoryFieldTypeCatalog(Map<String, FieldTypeDefinition> definitions) {
      this.definitions = definitions;
    }

    @Override
    public Optional<FieldTypeDefinition> getMetadata(FieldTypeCatalogContext context, String name) {
      return Optional.ofNullable(definitions.get(name));
    }

    @Override
    public List<FieldTypeDefinition> listMetadata(FieldTypeCatalogContext context) {
      return new ArrayList<FieldTypeDefinition>(definitions.values());
    }

    @Override
    public Either<FieldTypeCompileException, CompiledFieldType> compile(
        FieldTypeCatalogContext context,
        String name,
        Map<String, Object> params
    ) {
      var definition = definitions.get(name);
      if (definition == null) {
        return Either.left(new FieldTypeCompileException(
            "未知字段类型 " + name,
            name,
            FieldTypeDefinitionSource.DEFAULT,
            "test",
            "compile"));
      }
      return compiler.compile(definition, params, context);
    }
  }
}
