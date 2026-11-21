package com.ouroboros.businessmodel.projection.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.businessmodel.BusinessModelField;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.openapi.MediaTypeModel;
import com.ouroboros.webapi.openapi.ParameterLocation;
import com.ouroboros.webapi.openapi.ParameterModel;
import com.ouroboros.webapi.openapi.RequestBodyModel;
import com.ouroboros.webapi.openapi.ResponseModel;
import com.ouroboros.webapi.openapi.SchemaModel;

public final class BusinessModelOpenAPISupport {
  private static final String APPLICATION_JSON = "application/json";
  private static final String MULTIPART_FORM_DATA = "multipart/form-data";

  private BusinessModelOpenAPISupport() {
  }

  public static void applyCrudOperation(
      SimpleWebAPIModel.Builder builder,
      DefaultBusinessModel model,
      String operationKey,
      String httpMethod
  ) {
    if ("search".equals(operationKey)) {
      applySearchOperation(builder, model);
      return;
    }
    if ("getOne".equals(operationKey)) {
      applyGetOneOperation(builder, model);
      return;
    }
    if ("add".equals(operationKey)) {
      applyAddOperation(builder, model);
      return;
    }
    if ("update".equals(operationKey)) {
      applyUpdateOperation(builder, model, httpMethod);
      return;
    }
    if ("delete".equals(operationKey)) {
      applyDeleteOperation(builder, model);
      return;
    }
    if ("statusFlow".equals(operationKey)) {
      applyStatusFlowOperation(builder, model);
    }
  }

  public static void applyBatchDeleteOperation(SimpleWebAPIModel.Builder builder, DefaultBusinessModel model) {
    baseOperation(
        builder,
        model,
        "批量删除" + model.getTitle(),
        "按主键集合批量删除" + model.getTitle() + "数据。",
        model.getFullName() + ".batchDelete"
    );
    builder.requestBody(jsonRequestBody("批量删除主键集合", batchDeleteRequestSchema(model), true));
    builder.response("200", jsonResponse("批量删除执行完成", genericObjectSchema()));
  }

  public static void applyImportOperation(SimpleWebAPIModel.Builder builder, DefaultBusinessModel model) {
    baseOperation(
        builder,
        model,
        "导入" + model.getTitle() + "数据",
        "上传 Excel 文件并批量导入" + model.getTitle() + "数据。",
        model.getFullName() + ".import"
    );
    builder.requestBody(multipartRequestBody("待导入的 Excel 文件", importRequestSchema(), true));
    builder.response("200", jsonResponse("导入执行完成", genericObjectSchema()));
  }

  public static void applyStartProcessOperation(SimpleWebAPIModel.Builder builder, DefaultBusinessModel model) {
    baseOperation(
        builder,
        model,
        "发起" + model.getTitle() + "流程",
        "对指定" + model.getTitle() + "记录发起流程，并可附带补充更新数据。",
        model.getFullName() + ".startProcess"
    );
    builder.parameter(primaryKeyParameter(model));
    builder.requestBody(jsonRequestBody("发起流程时附带的业务数据", BusinessModelSchemaSupport.updateRequestSchema(model), false));
    builder.response("200", jsonResponse("流程发起结果", genericObjectSchema()));
  }

  public static void applySubmitTaskOperation(SimpleWebAPIModel.Builder builder, DefaultBusinessModel model) {
    baseOperation(
        builder,
        model,
        "提交" + model.getTitle() + "流程任务",
        "提交指定流程任务的处理结果，并同步提交业务表单数据。该入口属于业务模型 runtime 的历史包装面：外层负责业务记录更新编排，内层再转交审批流任务动作命令；它不是审批流 canonical task action contract。",
        model.getFullName() + ".submitTask"
    );
    builder.parameter(primaryKeyParameter(model));
    builder.parameter(pathParameter("instanceId", "流程实例 ID", SchemaModel.builder().stringType().build()));
    builder.parameter(pathParameter("taskId", "任务 ID", SchemaModel.builder().stringType().build()));
    builder.parameter(pathParameter("ACTION", "历史包装面动作标识（legacy workflow action token）", SchemaModel.builder().stringType().build()));
    builder.requestBody(jsonRequestBody("流程任务提交数据（legacy wrapper；仅描述业务表单数据）", submitTaskRequestSchema(model), false));
    builder.response("200", jsonResponse("流程任务提交结果", genericObjectSchema()));
  }

  public static void applyInitTaskFormOperation(SimpleWebAPIModel.Builder builder, DefaultBusinessModel model) {
    baseOperation(
        builder,
        model,
        "初始化" + model.getTitle() + "流程任务表单",
        "加载指定流程任务的表单初始化数据。",
        model.getFullName() + ".initTaskForm"
    );
    builder.parameter(primaryKeyParameter(model));
    builder.parameter(pathParameter("instanceId", "流程实例 ID", SchemaModel.builder().stringType().build()));
    builder.parameter(pathParameter("taskId", "任务 ID", SchemaModel.builder().stringType().build()));
    builder.response("200", jsonResponse("成功返回流程任务表单初始化结果", genericObjectSchema()));
  }

  public static void applyProcessCallbackOperation(SimpleWebAPIModel.Builder builder, DefaultBusinessModel model) {
    baseOperation(
        builder,
        model,
        "处理" + model.getTitle() + "流程回调",
        "接收流程回调载荷并更新" + model.getTitle() + "流程状态。",
        model.getFullName() + ".processCallback"
    );
    builder.parameter(primaryKeyParameter(model));
    builder.requestBody(jsonRequestBody("流程回调原始载荷", genericObjectSchema(), true));
    builder.response("200", jsonResponse("流程回调处理结果", genericObjectSchema()));
  }

  private static void applySearchOperation(SimpleWebAPIModel.Builder builder, DefaultBusinessModel model) {
    baseOperation(
        builder,
        model,
        "分页查询" + model.getTitle(),
        "按分页、排序和字段筛选条件查询" + model.getTitle() + "列表。",
        model.getFullName() + ".search"
    );
    for (ParameterModel parameter : searchParameters(model)) {
      builder.parameter(parameter);
    }
    builder.response("200", jsonResponse("分页查询成功", pagedDataSchema(model)));
  }

  private static void applyGetOneOperation(SimpleWebAPIModel.Builder builder, DefaultBusinessModel model) {
    baseOperation(
        builder,
        model,
        "获取" + model.getTitle() + "详情",
        "按主键读取单条" + model.getTitle() + "数据。",
        model.getFullName() + ".getOne"
    );
    builder.parameter(primaryKeyParameter(model));
    builder.response("200", jsonResponse("详情查询成功", BusinessModelSchemaSupport.responseSchema(model)));
  }

  private static void applyAddOperation(SimpleWebAPIModel.Builder builder, DefaultBusinessModel model) {
    baseOperation(
        builder,
        model,
        "新增" + model.getTitle(),
        "创建一条新的" + model.getTitle() + "记录。",
        model.getFullName() + ".add"
    );
    builder.requestBody(jsonRequestBody(model.getTitle() + "创建数据", BusinessModelSchemaSupport.createRequestSchema(model), true));
    builder.response("200", jsonResponse(model.getTitle() + "新增成功", BusinessModelSchemaSupport.responseSchema(model)));
  }

  private static void applyUpdateOperation(SimpleWebAPIModel.Builder builder, DefaultBusinessModel model, String httpMethod) {
    var isPatch = "PATCH".equalsIgnoreCase(httpMethod);
    var summary = isPatch ? "部分更新" + model.getTitle() : "更新" + model.getTitle();
    var description = isPatch
        ? "按主键部分更新" + model.getTitle() + "字段。"
        : "按主键更新" + model.getTitle() + "数据。";
    var operationId = isPatch
        ? model.getFullName() + ".patchUpdate"
        : model.getFullName() + ".update";

    baseOperation(builder, model, summary, description, operationId);
    builder.parameter(primaryKeyParameter(model));
    builder.requestBody(jsonRequestBody(model.getTitle() + "更新数据", BusinessModelSchemaSupport.updateRequestSchema(model), true));
    builder.response("200", jsonResponse(model.getTitle() + "更新成功", BusinessModelSchemaSupport.responseSchema(model)));
  }

  private static void applyDeleteOperation(SimpleWebAPIModel.Builder builder, DefaultBusinessModel model) {
    baseOperation(
        builder,
        model,
        "删除" + model.getTitle(),
        "按主键删除指定的" + model.getTitle() + "记录。",
        model.getFullName() + ".delete"
    );
    builder.parameter(primaryKeyParameter(model));
    builder.response("200", jsonResponse(model.getTitle() + "删除成功", BusinessModelSchemaSupport.responseSchema(model)));
  }

  private static void applyStatusFlowOperation(SimpleWebAPIModel.Builder builder, DefaultBusinessModel model) {
    baseOperation(
        builder,
        model,
        "触发" + model.getTitle() + "状态流",
        "按主键触发指定" + model.getTitle() + "记录的状态流逻辑。",
        model.getFullName() + ".statusFlow"
    );
    builder.parameter(primaryKeyParameter(model));
    builder.requestBody(jsonRequestBody("状态流请求数据", genericObjectSchema(), false));
    builder.response("200", jsonResponse("状态流执行结果", genericObjectSchema()));
  }

  private static void baseOperation(
      SimpleWebAPIModel.Builder builder,
      DefaultBusinessModel model,
      String summary,
      String description,
      String operationId
  ) {
    builder.summary(summary);
    builder.description(description);
    builder.operationId(operationId);
    builder.tag(model.getFullName());
  }

  private static List<ParameterModel> searchParameters(DefaultBusinessModel model) {
    var parameters = new ArrayList<ParameterModel>();
    parameters.add(queryParameter(
        "page",
        "分页页码",
        SchemaModel.builder().integerType().minimum(1).defaultValue(1).build()
    ));
    parameters.add(queryParameter(
        "perPage",
        "每页条数",
        SchemaModel.builder().integerType().minimum(1).build()
    ));
    parameters.add(queryParameter(
        "orderBy",
        "排序字段",
        SchemaModel.builder().stringType().build()
    ));
    parameters.add(queryParameter(
        "orderDir",
        "排序方向",
        SchemaModel.builder().stringType().enumValues(asEnum("ASC", "DESC")).build()
    ));

    for (BusinessModelField field : model.getFields()) {
      parameters.add(queryParameter(
          field.getName(),
          field.getTitle() + "筛选条件",
          buildSearchFieldSchema(model, field)
      ));
    }
    return parameters;
  }

  private static SchemaModel buildSearchFieldSchema(DefaultBusinessModel model, BusinessModelField field) {
    var schema = BusinessModelSchemaSupport.responseSchema(model)
        .getProperties()
        .get(field.getName());
    if (schema == null) {
      return SchemaModel.builder().stringType().build();
    }
    return schema;
  }

  private static ParameterModel primaryKeyParameter(DefaultBusinessModel model) {
    return pathParameter(
        primaryKeyFieldName(model),
        BusinessModelSchemaSupport.primaryKeyDescription(model),
        BusinessModelSchemaSupport.primaryKeySchema(model)
    );
  }

  private static String primaryKeyFieldName(DefaultBusinessModel model) {
    return model.getFields().stream()
        .filter(BusinessModelField::getIsPrimaryKey)
        .findFirst()
        .map(BusinessModelField::getName)
        .orElse("id");
  }

  private static ParameterModel pathParameter(String name, String description, SchemaModel schema) {
    return ParameterModel.builder()
        .name(name)
        .in(ParameterLocation.PATH)
        .description(description)
        .required(true)
        .schema(schema)
        .build();
  }

  private static ParameterModel queryParameter(String name, String description, SchemaModel schema) {
    return ParameterModel.builder()
        .name(name)
        .in(ParameterLocation.QUERY)
        .description(description)
        .required(false)
        .schema(schema)
        .build();
  }

  private static RequestBodyModel jsonRequestBody(String description, SchemaModel schema, boolean required) {
    var content = new LinkedHashMap<String, MediaTypeModel>();
    content.put(APPLICATION_JSON, MediaTypeModel.builder().schema(schema).build());
    return RequestBodyModel.builder()
        .description(description)
        .required(required)
        .content(content)
        .build();
  }

  private static RequestBodyModel multipartRequestBody(String description, SchemaModel schema, boolean required) {
    var content = new LinkedHashMap<String, MediaTypeModel>();
    content.put(MULTIPART_FORM_DATA, MediaTypeModel.builder().schema(schema).build());
    return RequestBodyModel.builder()
        .description(description)
        .required(required)
        .content(content)
        .build();
  }

  private static ResponseModel jsonResponse(String description, SchemaModel dataSchema) {
    var content = new LinkedHashMap<String, MediaTypeModel>();
    content.put(APPLICATION_JSON, MediaTypeModel.builder().schema(webApiResultSchema(dataSchema)).build());
    return ResponseModel.builder()
        .description(description)
        .content(content)
        .build();
  }

  private static SchemaModel webApiResultSchema(SchemaModel dataSchema) {
    var properties = new LinkedHashMap<String, SchemaModel>();
    properties.put("code", SchemaModel.builder().integerType().build());
    properties.put("msg", SchemaModel.builder().stringType().nullable(true).build());
    properties.put("data", dataSchema);
    return SchemaModel.builder()
        .objectType()
        .properties(properties)
        .build();
  }

  private static SchemaModel pagedDataSchema(DefaultBusinessModel model) {
    var properties = new LinkedHashMap<String, SchemaModel>();
    properties.put("total", SchemaModel.builder().integerType().description("总记录数").build());
    properties.put("items", SchemaModel.builder()
        .arrayType()
        .description("数据列表")
        .items(BusinessModelSchemaSupport.responseSchema(model))
        .build());
    return SchemaModel.builder()
        .objectType()
        .properties(properties)
        .build();
  }

  private static SchemaModel batchDeleteRequestSchema(DefaultBusinessModel model) {
    var properties = new LinkedHashMap<String, SchemaModel>();
    properties.put("ids", SchemaModel.builder()
        .arrayType()
        .description("待删除的主键集合")
        .items(BusinessModelSchemaSupport.primaryKeySchema(model))
        .build());
    return SchemaModel.builder()
        .objectType()
        .additionalProperties(Boolean.FALSE)
        .required(singletonList("ids"))
        .properties(properties)
        .build();
  }

  private static SchemaModel importRequestSchema() {
    var properties = new LinkedHashMap<String, SchemaModel>();
    properties.put("file", SchemaModel.builder()
        .stringType()
        .format("binary")
        .description("待导入文件")
        .build());
    return SchemaModel.builder()
        .objectType()
        .additionalProperties(Boolean.FALSE)
        .required(singletonList("file"))
        .properties(properties)
        .build();
  }

  private static SchemaModel submitTaskRequestSchema(DefaultBusinessModel model) {
    var properties = new LinkedHashMap<String, SchemaModel>();
    properties.put("formData", BusinessModelSchemaSupport.updateRequestSchema(model));
    return SchemaModel.builder()
        .objectType()
        .additionalProperties(Boolean.FALSE)
        .properties(properties)
        .build();
  }

  private static SchemaModel genericObjectSchema() {
    return SchemaModel.builder()
        .objectType()
        .additionalProperties(Boolean.TRUE)
        .build();
  }

  private static List<String> singletonList(String value) {
    var list = new ArrayList<String>();
    list.add(value);
    return list;
  }

  private static List<Object> asEnum(String... values) {
    var enumValues = new ArrayList<Object>();
    for (String value : values) {
      enumValues.add(value);
    }
    return enumValues;
  }
}
