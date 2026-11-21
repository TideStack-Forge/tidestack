package com.ouroboros.businessmodel.projection.logicflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.springframework.context.support.StaticApplicationContext;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.meta.BusinessModelFieldMeta;
import com.ouroboros.businessmodel.meta.BusinessModelRelationMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.util.PrototypeTemplate;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.fieldtype.CompiledFieldType;
import com.ouroboros.fieldtype.DefaultFieldTypeCompiler;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.fieldtype.FieldTypeCompileException;
import com.ouroboros.fieldtype.FieldTypeDefinition;
import com.ouroboros.fieldtype.FieldTypeDefinitionSource;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowModelFactory;
import com.ouroboros.logicflow.model.FlowConnectionMeta;
import com.ouroboros.logicflow.model.FlowModelCenter;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.logicflow.model.FlowModelProvider;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.model.FlowNodePortMeta;
import com.ouroboros.logicflow.nodes.AbstractSimpleNode;
import com.ouroboros.logicflow.nodes.DataModelReferences;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.nodes.NodeBuilder;
import com.ouroboros.logicflow.nodes.NodeFactory;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.nodes.NodeReferenceSet;

import io.vavr.control.Either;
import io.vavr.control.Try;

final class LogicFlowTemplateContractTestSupport {
  private static final SpringContextUtil contextBridge = new SpringContextUtil();

  static void closeSpringContext() { contextBridge.destroy(); }
  static final String MODEL_FULL_NAME = "demo.customer";
  static final String MODEL_TITLE = "客户";
  static final String PROCESS_ID = "customer-approval";

  private LogicFlowTemplateContractTestSupport() {
  }

  static FlowModelMeta generateTemplate(String templateName) {
    return PrototypeTemplate.generate(templateName, templateParams(templateName), FlowModelMeta.class);
  }

  static void assertExplicitSuccessfulTerminalContract(String label, FlowModelMeta flowModel) {
    assertNull(flowModel.getExitNode(), label + " should not declare exitNode");

    var returnNodes = flowModel.getNodes().stream()
        .filter(node -> "成功返回".equals(node.getName()))
        .collect(Collectors.toList());
    assertEquals(1, returnNodes.size(), label + " should contain exactly one 成功返回 node");

    var returnNode = returnNodes.get(0);
    assertEquals("return", returnNode.getType(), label + " should use ReturnNode as successful terminal");
    assertEquals(1, returnNode.getInputPorts().size(), label + " should expose exactly one return input");
    assertTrue(returnNode.getOutputPorts().isEmpty(), label + " should not expose any return outputs");
    assertFalse(incomingConnections(flowModel, "成功返回").isEmpty(), label + " should connect some success path into 成功返回");
  }

  static void assertExplicitSuccessfulTerminalRemainsValid(String label, FlowModelMeta flowModel) {
    withContractValidationContext(() -> {
      var validatedFlow = FlowModelFactory.build(flowModel, contractReferences());
      assertTrue(validatedFlow.isValid(), () -> label + " should remain valid, but found: " + validatedFlow.getErrors());
    });
  }

  static void assertNodeAbsent(String label, FlowModelMeta flowModel, String nodeName) {
    assertFalse(flowModel.getNodes().stream().anyMatch(node -> nodeName.equals(node.getName())),
        label + " should not contain legacy node " + nodeName);
  }

  static FlowNodeMeta requireNode(FlowModelMeta flowModel, String nodeName) {
    return flowModel.getNodes().stream()
        .filter(node -> nodeName.equals(node.getName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing node " + nodeName));
  }

  static FlowConnectionMeta requireConnection(
      FlowModelMeta flowModel,
      String sourceNode,
      int sourceOutputPort,
      String targetNode
  ) {
    return flowModel.getConnections().stream()
        .filter(connection -> sourceNode.equals(connection.getSourceNode()))
        .filter(connection -> Integer.valueOf(sourceOutputPort).equals(connection.getSourceOutputPort()))
        .filter(connection -> targetNode.equals(connection.getTargetNode()))
        .findFirst()
        .orElseThrow(() -> new AssertionError(
            "missing connection " + sourceNode + "[" + sourceOutputPort + "] -> " + targetNode));
  }

  static List<FlowConnectionMeta> incomingConnections(FlowModelMeta flowModel, String targetNode) {
    return flowModel.getConnections().stream()
        .filter(connection -> targetNode.equals(connection.getTargetNode()))
        .collect(Collectors.toList());
  }

  static void bindMinimalFieldTypeCatalog() throws Exception {
    var applicationContext = new StaticApplicationContext();
    applicationContext.getBeanFactory().registerSingleton("fieldTypeCatalog", new InMemoryFieldTypeCatalog(defaultFieldTypes()));
    contextBridge.destroy();
    contextBridge.setApplicationContext((org.springframework.context.ApplicationContext) applicationContext);
  }

  static void bindMinimalDataModelCenter() throws Exception {
    var modelMap = new TreeMap<String, DataModel>(String::compareToIgnoreCase);
    modelMap.put(MODEL_FULL_NAME, stubDataModel(MODEL_FULL_NAME));
    modelMap.put("demo.customerContact", stubDataModel("demo.customerContact"));
    Field field = DataModelCenter.class.getDeclaredField("dataModelMap");
    field.setAccessible(true);
    field.set(null, modelMap);
  }

  static void seedCurrentDataModelMeta(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    new com.ouroboros.businessmodel.projection.data.basic.BasicMPS().process(context);
    new com.ouroboros.businessmodel.projection.data.basic.PrimaryKeyMPS().process(context);
  }

  static DefaultBusinessModel createCustomerModel(boolean workflowEnabled, boolean withChildRelation) {
    var provider = new InMemoryBusinessModelProvider();
    var fields = new ArrayList<BusinessModelFieldMeta>();
    fields.add(field("id", "客户 ID", "单行文本", true, true, mapOf("size", 32, "regexp", ".*")));
    fields.add(field("name", "客户名称", "单行文本", true, false, mapOf("size", 64, "regexp", "[A-Z]+")));
    fields.add(field("age", "年龄", "整数数字", false, false, Collections.emptyMap()));
    fields.add(field("email", "邮箱", "电子邮箱", false, false, mapOf("size", 128)));

    var relations = new ArrayList<BusinessModelRelationMeta>();
    if (withChildRelation) {
      relations.add(childRelation("contacts", "联系人", "demo.customerContact", "customerId", "id", true));
    }

    var modelMeta = new DefaultBusinessModelMeta();
    modelMeta.setName("customer");
    modelMeta.setNamespace("demo");
    modelMeta.setTitle(MODEL_TITLE);
    modelMeta.setDescription("客户业务模型");
    modelMeta.setIsEnabled(true);
    modelMeta.setFields(fields);
    modelMeta.setRelations(relations);
    modelMeta.setExtraProp("apis", enabledCrudApis());
    modelMeta.setExtraProp("features", mapOf("isEnabledBatchDelete", true, "isEnabledImport", true));
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

  private static Map<String, Object> templateParams(String templateName) {
    var params = new LinkedHashMap<String, Object>();
    switch (templateName) {
      case "logic-flow/singleScript":
        params.put("fullName", MODEL_FULL_NAME + ".singleScript");
        params.put("description", "脚本逻辑模型");
        params.put("modelName", MODEL_FULL_NAME);
        params.put("script", "");
        params.put("scriptLanguage", "javascript");
        return params;
      case "logic-flow/add":
        params.put("fullName", MODEL_FULL_NAME + ".add");
        params.put("description", "添加客户单条数据");
        params.put("modelName", MODEL_FULL_NAME);
        params.put("subTables", Collections.emptyList());
        params.put("validationSchema", minimalValidationSchema());
        return params;
      case "logic-flow/delete":
        params.put("fullName", MODEL_FULL_NAME + ".delete");
        params.put("description", "删除客户单条数据");
        params.put("modelName", MODEL_FULL_NAME);
        params.put("subTables", Collections.emptyList());
        return params;
      case "logic-flow/update":
        params.put("fullName", MODEL_FULL_NAME + ".update");
        params.put("description", "更新客户单条数据");
        params.put("modelName", MODEL_FULL_NAME);
        params.put("subTables", Collections.emptyList());
        params.put("validationSchema", minimalValidationSchema());
        return params;
      case "logic-flow/search":
        params.put("fullName", MODEL_FULL_NAME + ".search");
        params.put("description", "分页查询客户");
        params.put("modelName", MODEL_FULL_NAME);
        params.put("workflowEnabled", false);
        return params;
      case "logic-flow/getOne":
        params.put("fullName", MODEL_FULL_NAME + ".getOne");
        params.put("description", "获取客户详情");
        params.put("modelName", MODEL_FULL_NAME);
        return params;
      case "logic-flow/import":
        params.put("fullName", MODEL_FULL_NAME + ".import");
        params.put("modelName", MODEL_FULL_NAME);
        params.put("modelTitle", MODEL_TITLE);
        params.put("dataMapping", mapOf("name", "$item.get('客户名称')"));
        return params;
      case "logic-flow/batchDelete":
        params.put("fullName", MODEL_FULL_NAME + ".batchDelete");
        params.put("modelName", MODEL_FULL_NAME);
        params.put("modelTitle", MODEL_TITLE);
        return params;
      case "logic-flow/workflow/startProcess":
        params.put("fullName", MODEL_FULL_NAME + ".startProcess");
        params.put("modelTitle", MODEL_TITLE);
        params.put("modelName", MODEL_FULL_NAME);
        params.put("processId", PROCESS_ID);
        params.put("modelPath", "/demo/customer/");
        params.put("statusField", "status");
        params.put("pendingStatus", "PENDING");
        params.put("sendEditUrl", Boolean.FALSE);
        params.put("callbackFlow", MODEL_FULL_NAME + ".processCallback");
        return params;
      case "logic-flow/workflow/initTaskForm":
        params.put("fullName", MODEL_FULL_NAME + ".initTaskForm");
        params.put("modelTitle", MODEL_TITLE);
        params.put("modelName", MODEL_FULL_NAME);
        return params;
      case "logic-flow/workflow/submitTask":
        params.put("fullName", MODEL_FULL_NAME + ".submitTask");
        params.put("modelTitle", MODEL_TITLE);
        params.put("modelName", MODEL_FULL_NAME);
        params.put("businessUpdateFormDataScript", businessUpdateFormDataScript());
        return params;
      case "logic-flow/workflow/processCallback":
        params.put("fullName", MODEL_FULL_NAME + ".processCallback");
        params.put("modelTitle", MODEL_TITLE);
        params.put("modelName", MODEL_FULL_NAME);
        params.put("processId", PROCESS_ID);
        params.put("statusField", "status");
        params.put("statusMap", workflowStatusMap());
        return params;
      default:
        throw new IllegalArgumentException("Unsupported template " + templateName);
    }
  }

  private static Map<String, Object> minimalValidationSchema() {
    return mapOf("type", "object", "properties", Collections.emptyMap());
  }

  private static DefaultBusinessModel createCustomerContactModel(InMemoryBusinessModelProvider provider) {
    var fields = new ArrayList<BusinessModelFieldMeta>();
    fields.add(field("id", "联系人 ID", "单行文本", true, true, mapOf("size", 32, "regexp", ".*")));
    fields.add(field("customerId", "客户 ID", "单行文本", false, false, mapOf("size", 32, "regexp", ".*")));
    fields.add(field("contactName", "联系人名称", "单行文本", true, false, mapOf("size", 32, "regexp", ".*")));
    fields.add(field("phone", "联系电话", "单行文本", false, false, mapOf("size", 20, "regexp", ".*")));

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

  private static Map<String, Object> enabledCrudApis() {
    var apis = new LinkedHashMap<String, Object>();
    apis.put("search", mapOf("isEnabled", true));
    apis.put("getOne", mapOf("isEnabled", true));
    apis.put("add", mapOf("isEnabled", true));
    apis.put("update", mapOf("isEnabled", true));
    apis.put("delete", mapOf("isEnabled", true));
    return apis;
  }

  private static Map<String, Object> workflowConfig() {
    return mapOf(
        "isEnabled", true,
        "bindings", Collections.singletonList(mapOf(
            "key", "default",
            "processId", PROCESS_ID,
            "statusField", "status",
            "statusMap", workflowStatusMap(),
            "trigger", "manual"
        ))
    );
  }

  private static Map<String, Object> workflowStatusMap() {
    var statusMap = new LinkedHashMap<String, Object>();
    statusMap.put("pending", status("PENDING", "待处理"));
    statusMap.put("processing", status("PROCESSING", "处理中"));
    statusMap.put("suspended", status("SUSPENDED", "已暂停"));
    statusMap.put("withdrawn", status("WITHDRAWN", "已撤回"));
    statusMap.put("approved", status("APPROVED", "已通过"));
    statusMap.put("veto", status("VETO", "已否决"));
    statusMap.put("canceled", status("CANCELED", "已撤销"));
    statusMap.put("terminated", status("TERMINATED", "已终止"));
    return statusMap;
  }

  private static String businessUpdateFormDataScript() {
    return """
        def source = payload instanceof Map ? payload : [:]
        def formData = source.formData instanceof Map ? new java.util.LinkedHashMap(source.formData) : new java.util.LinkedHashMap()
        def statusField = "status"
        if (statusField != null && statusField.trim()) {
          formData.remove(statusField)
        }
        return formData
        """;
  }

  private static Map<String, Object> status(String code, String label) {
    return mapOf("code", code, "label", label);
  }

  private static Map<String, FieldTypeDefinition> defaultFieldTypes() {
    var definitions = new LinkedHashMap<String, FieldTypeDefinition>();
    definitions.put("单行文本", textFieldType());
    definitions.put("整数数字", integerFieldType());
    definitions.put("电子邮箱", emailFieldType());
    return definitions;
  }

  private static FieldTypeDefinition textFieldType() {
    var definition = new FieldTypeDefinition();
    definition.setName("单行文本");
    definition.setSource(FieldTypeDefinitionSource.DEFAULT);
    definition.setSourceLocation("test");
    definition.setDefaultParams(mapOf("size", 100, "regexp", ".*"));
    definition.setDataType(mapOf("type", "String", "size", "${size}"));
    definition.setValidations(mapOf("matchRegexp", "${regexp}"));
    return definition;
  }

  private static FieldTypeDefinition integerFieldType() {
    var definition = new FieldTypeDefinition();
    definition.setName("整数数字");
    definition.setSource(FieldTypeDefinitionSource.DEFAULT);
    definition.setSourceLocation("test");
    definition.setDataType(mapOf("type", "Integer"));
    definition.setDefaultParams(Collections.emptyMap());
    return definition;
  }

  private static FieldTypeDefinition emailFieldType() {
    var definition = new FieldTypeDefinition();
    definition.setName("电子邮箱");
    definition.setSource(FieldTypeDefinitionSource.DEFAULT);
    definition.setSourceLocation("test");
    definition.setDefaultParams(mapOf("size", 100));
    definition.setDataType(mapOf("type", "String", "size", "${size}"));
    definition.setValidations(mapOf("isEmail", true));
    return definition;
  }

  private static Map<String, Object> mapOf(Object... keyValues) {
    var map = new LinkedHashMap<String, Object>();
    for (int index = 0; index < keyValues.length; index += 2) {
      map.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
    }
    return map;
  }

  private static void withContractValidationContext(ThrowingRunnable assertion) {
    try {
      var buildersField = NodeFactory.class.getDeclaredField("builders");
      buildersField.setAccessible(true);
      @SuppressWarnings("unchecked")
      var originalBuilders = (List<NodeBuilder>) buildersField.get(null);
      var patchedBuilders = new ArrayList<NodeBuilder>(originalBuilders);
      useContractBuilder(patchedBuilders, "callFlow", new ContractWorkflowPassthroughNodeBuilder("callFlow"));
      useContractBuilder(patchedBuilders, "startProcess", new ContractWorkflowPassthroughNodeBuilder("startProcess"));
      useContractBuilder(patchedBuilders, "submitTask", new ContractWorkflowPassthroughNodeBuilder("submitTask"));
      useContractBuilder(patchedBuilders, "getTaskInfo", new ContractWorkflowPassthroughNodeBuilder("getTaskInfo"));

      var providersField = FlowModelCenter.class.getDeclaredField("providers");
      providersField.setAccessible(true);
      @SuppressWarnings("unchecked")
      var originalProviders = (List<FlowModelProvider>) providersField.get(null);
      var patchedProviders = new ArrayList<FlowModelProvider>(originalProviders);
      patchedProviders.add(new InMemoryFlowModelProvider(minimalReferencedFlow(MODEL_FULL_NAME + ".getOne")));

      try {
        buildersField.set(null, patchedBuilders);
        providersField.set(null, patchedProviders);
        assertion.run();
      } finally {
        buildersField.set(null, originalBuilders);
        providersField.set(null, originalProviders);
      }
    } catch (Exception e) {
      throw new AssertionError("Failed to install contract validation support", e);
    }
  }

  private static void useContractBuilder(List<NodeBuilder> builders, String nodeType, NodeBuilder contractBuilder) {
    builders.removeIf(builder -> supportsNodeType(builder, nodeType));
    builders.add(0, contractBuilder);
  }

  private static boolean supportsNodeType(NodeBuilder builder, String nodeType) {
    var probe = FlowNodeMeta.builder()
        .name("__contract_test_probe__")
        .type(nodeType)
        .build();
    return builder.support(probe);
  }

  private static FlowModel minimalReferencedFlow(String flowName) {
    var entry = FlowNodeMeta.builder()
        .name("接受输入")
        .type("mutex")
        .inputPort("")
        .outputPort(FlowNodePortMeta.builder().name("").build())
        .build();
    var success = FlowNodeMeta.builder()
        .name("成功返回")
        .type("return")
        .inputPort("")
        .build();
    var meta = FlowModelMeta.builder()
        .name(flowName)
        .description("contract-test minimal referenced flow")
        .entryNode(entry)
        .node(success)
        .connection(FlowConnectionMeta.builder()
            .sourceNode("接受输入")
            .sourceOutputPort(0)
            .targetNode("成功返回")
            .targetInputPort(0)
            .build())
        .build();
    return FlowModelFactory.build(meta, NodeReferenceCatalog.empty());
  }

  private static DataModel stubDataModel(String fullName) {
    var lastDotIndex = fullName.lastIndexOf('.');
    var namespace = lastDotIndex >= 0 ? fullName.substring(0, lastDotIndex) : "";
    var name = lastDotIndex >= 0 ? fullName.substring(lastDotIndex + 1) : fullName;
    var self = new AtomicReference<DataModel>();
    InvocationHandler handler = (proxy, method, args) -> {
      switch (method.getName()) {
        case "getFullName":
          return fullName;
        case "getNamespace":
          return namespace;
        case "getName":
        case "getLabel":
        case "getRawName":
          return name;
        case "getVersion":
        case "getSource":
        case "getDescription":
          return "";
        case "getExtraProps":
          return Collections.emptyMap();
        case "getExtraProp":
          return Optional.empty();
        case "getFields":
        case "getPrimaryKeys":
          return Collections.emptyList();
        case "getField":
          return Optional.empty();
        case "withPlugins":
        case "withoutPlugins":
          return self.get();
        case "hasPlugin":
          return false;
        case "toString":
          return "StubDataModel(" + fullName + ")";
        case "hashCode":
          return System.identityHashCode(proxy);
        case "equals":
          return proxy == args[0];
        default:
          Class<?> returnType = method.getReturnType();
          if (Optional.class.equals(returnType)) {
            return Optional.empty();
          }
          if (List.class.equals(returnType)) {
            return Collections.emptyList();
          }
          if (Map.class.equals(returnType)) {
            return Collections.emptyMap();
          }
          if (Boolean.TYPE.equals(returnType)) {
            return false;
          }
          if (Integer.TYPE.equals(returnType)) {
            return 0;
          }
          if (Long.TYPE.equals(returnType)) {
            return 0L;
          }
          if (Try.class.equals(returnType)) {
            return Try.failure(new UnsupportedOperationException("Stub DataModel should not execute in contract tests"));
          }
          return null;
      }
    };
    var proxy = (DataModel) Proxy.newProxyInstance(
        DataModel.class.getClassLoader(),
        new Class<?>[]{DataModel.class},
        handler);
    self.set(proxy);
    return proxy;
  }

  private static final class InMemoryBusinessModelProvider implements BusinessModelProvider {
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

  private static final class InMemoryFieldTypeCatalog implements FieldTypeCatalog {
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
      assertNotNull(definition, "Unknown field type " + name + ". Available types: " + Arrays.toString(definitions.keySet().toArray()));
      return compiler.compile(definition, params, context);
    }
  }

  private static final class InMemoryFlowModelProvider implements FlowModelProvider {
    private final Map<String, FlowModel> flowModels = new LinkedHashMap<String, FlowModel>();

    private InMemoryFlowModelProvider(FlowModel... flowModels) {
      for (var flowModel : flowModels) {
        this.flowModels.put(flowModel.getName(), flowModel);
      }
    }

    @Override
    public Optional<FlowModel> getFlowModel(String flowName) {
      return Optional.ofNullable(flowModels.get(flowName));
    }

    @Override
    public Map<String, FlowModel> getFlowModelMap() {
      return Collections.unmodifiableMap(flowModels);
    }
  }

  private static final class ContractWorkflowPassthroughNodeBuilder implements NodeBuilder<ContractWorkflowPassthroughNode> {
    private final String supportedType;

    private ContractWorkflowPassthroughNodeBuilder(String supportedType) {
      this.supportedType = supportedType;
    }

    @Override
    public Boolean support(FlowNodeMeta meta) {
      return supportedType.equalsIgnoreCase(meta.getType());
    }

    @Override
    public ContractWorkflowPassthroughNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new ContractWorkflowPassthroughNode(owner, metadata);
    }
  }

  private static final class ContractWorkflowPassthroughNode extends AbstractSimpleNode<Object> {
    private ContractWorkflowPassthroughNode(FlowModel flowModel, FlowNodeMeta meta) {
      super(flowModel, meta);
    }

    @Override
    protected Object doExecute(Object input) {
      return input;
    }
  }

  private static NodeReferenceCatalog contractReferences() {
    return NodeReferenceCatalog.of(List.of(NodeReferenceSet.complete(
        DataModelReferences.KIND,
        Map.of(
            MODEL_FULL_NAME, stubDataModel(MODEL_FULL_NAME),
            "demo.customerContact", stubDataModel("demo.customerContact")
        ),
        String::compareToIgnoreCase
    )));
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }
}
