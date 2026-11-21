package com.ouroboros.businessmodel.projection.logicflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.logicflow.expression.LogicFlowExpression;
import com.ouroboros.logicflow.model.FlowModelMeta;

class LogicFlowTemplateProjectionIntegrationContractTest {
  @org.junit.jupiter.api.AfterEach
  void closeSpringContext() { LogicFlowTemplateContractTestSupport.closeSpringContext(); }


  @Test
  void crudProjectionEntryPointsShouldPreserveExplicitSuccessfulTerminalContract() throws Exception {
    LogicFlowTemplateContractTestSupport.bindMinimalFieldTypeCatalog();
    LogicFlowTemplateContractTestSupport.bindMinimalDataModelCenter();

    var model = LogicFlowTemplateContractTestSupport.createCustomerModel(false, false);
    var context = new ModelProjectionPipelineContext<DefaultBusinessModel>(model);
    context.setAttribute("hasDataModel", true);

    new AddMPS().process(context);
    new GetOneMPS().process(context);

    var addFlow = context.getModel(FlowModelMeta.class, "demo.customer.add")
        .orElseThrow(() -> new AssertionError("missing add logic flow"));
    LogicFlowTemplateContractTestSupport.assertExplicitSuccessfulTerminalContract("demo.customer.add", addFlow);
    LogicFlowTemplateContractTestSupport.assertExplicitSuccessfulTerminalRemainsValid("demo.customer.add", addFlow);

    var getOneFlow = context.getModel(FlowModelMeta.class, "demo.customer.getOne")
        .orElseThrow(() -> new AssertionError("missing getOne logic flow"));
    LogicFlowTemplateContractTestSupport.assertExplicitSuccessfulTerminalContract("demo.customer.getOne", getOneFlow);
    LogicFlowTemplateContractTestSupport.assertExplicitSuccessfulTerminalRemainsValid("demo.customer.getOne", getOneFlow);
    LogicFlowTemplateContractTestSupport.requireConnection(getOneFlow, "判断是否存在", 0, "成功返回");
  }

  @Test
  void workflowBundleProjectionShouldPreserveExplicitSuccessfulTerminalContract() throws Exception {
    LogicFlowTemplateContractTestSupport.bindMinimalFieldTypeCatalog();
    LogicFlowTemplateContractTestSupport.bindMinimalDataModelCenter();

    var model = LogicFlowTemplateContractTestSupport.createCustomerModel(true, true);
    var bundle = model.buildModelBundle();

    for (var flowName : Arrays.asList(
        "demo.customer.startProcess",
        "demo.customer.initTaskForm",
        "demo.customer.submitTask",
        "demo.customer.processCallback")) {
      var flowModel = bundle.getModel(FlowModelMeta.class, flowName)
          .orElseThrow(() -> new AssertionError("missing workflow logic flow " + flowName));
      LogicFlowTemplateContractTestSupport.assertExplicitSuccessfulTerminalContract(flowName, flowModel);
      LogicFlowTemplateContractTestSupport.assertExplicitSuccessfulTerminalRemainsValid(flowName, flowModel);
    }

    var processCallbackFlow = bundle.getModel(FlowModelMeta.class, "demo.customer.processCallback")
        .orElseThrow(() -> new AssertionError("missing processCallback logic flow"));
    var sourceNodes = LogicFlowTemplateContractTestSupport.incomingConnections(processCallbackFlow, "成功返回").stream()
        .map(connection -> connection.getSourceNode())
        .collect(Collectors.toSet());
    assertEquals(Arrays.stream(new String[]{"更新业务状态", "其他"}).collect(Collectors.toSet()), sourceNodes);
  }

  @Test
  void workflowStartOptionsRemainSeparateFromBusinessFieldsAndUseTheExistingExpressionEngine() throws Exception {
    LogicFlowTemplateContractTestSupport.bindMinimalDataModelCenter();
    var flow = LogicFlowTemplateContractTestSupport.generateTemplate("logic-flow/workflow/startProcess");
    var options = new java.util.LinkedHashMap<String, Object>();
    options.put("amount", 1200);
    options.put("taskAssignee", Map.of("approve", List.of("user-2")));
    options.put("approvers", Map.of("review", List.of("user-3")));
    options.put("cc", Map.of("userIds", List.of("user-4")));
    options.put("idempotencyKey", "body-key");
    var request = Map.<String, Object>of("params", Map.of("idempotencyKey", "query-key"), "data", options);
    var filter = LogicFlowTemplateContractTestSupport.requireNode(flow, "过滤发起请求业务数据");
    var script = filter.getExtraProp(String.class, "script").orElseThrow();
    var binding = new groovy.lang.Binding(new java.util.LinkedHashMap<>(Map.of("payload", request)));
    Object businessData = new groovy.lang.GroovyShell(binding).evaluate(script);
    assertEquals(Map.of("amount", 1200), businessData);
    assertEquals("body-key", options.get("idempotencyKey"));
    var mapping = (Map<?, ?>) LogicFlowTemplateContractTestSupport.requireNode(flow, "映射流程参数")
        .getExtraProp("mapping").orElseThrow();
    for (String name : List.of("taskAssignee", "cc", "approvers")) {
      var result = LogicFlowExpression.eval(LogicFlowExpression.wrapExpression((String) mapping.get(name)),
          com.ouroboros.logicflow.util.NodeUtil.buildContextMap(List.of(request), List.of("startRequest")));
      assertTrue(result.isRight(), result::toString);
      assertEquals(options.get(name), result.get());
    }
    String keyExpression = LogicFlowExpression.wrapExpression((String) mapping.get("idempotencyKey"));
    assertEquals("query-key", LogicFlowExpression.eval(keyExpression, com.ouroboros.logicflow.util.NodeUtil.buildContextMap(List.of(request), List.of("startRequest"))).get());
    assertEquals("body-key", LogicFlowExpression.eval(keyExpression,
        com.ouroboros.logicflow.util.NodeUtil.buildContextMap(
            List.of(Map.of("params", Map.of(), "data", options)), List.of("startRequest"))).get());
    assertEquals(null, LogicFlowExpression.eval(keyExpression,
        com.ouroboros.logicflow.util.NodeUtil.buildContextMap(
            List.of(Map.of("params", Map.of(), "data", Map.of())), List.of("startRequest"))).get());
    binding.setVariable("payload", Map.of("data", Map.of("formData", Map.of("amount", 50), "cc", options.get("cc"))));
    assertEquals(Map.of("amount", 50), new groovy.lang.GroovyShell(binding).evaluate(script));
  }

  @Test
  void searchProjectionPaginationMappingShouldEvaluateStringPageParameters() throws Exception {
    LogicFlowTemplateContractTestSupport.bindMinimalFieldTypeCatalog();
    LogicFlowTemplateContractTestSupport.bindMinimalDataModelCenter();

    var model = LogicFlowTemplateContractTestSupport.createCustomerModel(false, false);
    var context = new ModelProjectionPipelineContext<DefaultBusinessModel>(model);
    context.setAttribute("hasDataModel", true);

    new SearchMPS().process(context);

    var searchFlow = context.getModel(FlowModelMeta.class, "demo.customer.search")
        .orElseThrow(() -> new AssertionError("missing search logic flow"));
    var paginationMapping = LogicFlowTemplateContractTestSupport.requireNode(searchFlow, "映射分页参数")
        .getExtraProp(Map.class, "mapping")
        .orElseThrow(() -> new AssertionError("missing pagination mapping"));

    var variables = Map.<String, Object>of("page", "3", "perPage", "20");
    var limit = LogicFlowExpression.eval(
        LogicFlowExpression.wrapExpression((String) paginationMapping.get("limit")),
        variables);
    var skip = LogicFlowExpression.eval(
        LogicFlowExpression.wrapExpression((String) paginationMapping.get("skip")),
        variables);

    assertTrue(limit.isRight(), () -> "limit mapping should evaluate: " + limit.getLeft());
    assertTrue(skip.isRight(), () -> "skip mapping should evaluate: " + skip.getLeft());
    assertEquals(20, limit.get());
    assertEquals(40, skip.get());
  }

  @Test
  void addProjectionShouldUseSingleChildCollectionSyncNode() throws Exception {
    LogicFlowTemplateContractTestSupport.bindMinimalFieldTypeCatalog();
    LogicFlowTemplateContractTestSupport.bindMinimalDataModelCenter();

    var model = LogicFlowTemplateContractTestSupport.createCustomerModel(false, true);
    var context = new ModelProjectionPipelineContext<DefaultBusinessModel>(model);
    context.setAttribute("hasDataModel", true);
    LogicFlowTemplateContractTestSupport.seedCurrentDataModelMeta(context);

    new AddMPS().process(context);

    var addFlow = context.getModel(FlowModelMeta.class, "demo.customer.add")
        .orElseThrow(() -> new AssertionError("missing add logic flow"));
    assertSyncChildCollectionNode(addFlow, "create");
    assertNoLegacyChildCrudNodes(addFlow);
    assertEquals(Integer.valueOf(0),
        LogicFlowTemplateContractTestSupport.requireConnection(addFlow, "执行插入操作", 0, "同步子表").getTargetInputPort());
    assertEquals(Integer.valueOf(1),
        LogicFlowTemplateContractTestSupport.requireConnection(addFlow, "执行前置脚本", 0, "同步子表").getTargetInputPort());
    assertMergeNode(addFlow);
    assertEquals(Integer.valueOf(0),
        LogicFlowTemplateContractTestSupport.requireConnection(addFlow, "执行插入操作", 0, "合并结果").getTargetInputPort());
    assertEquals(Integer.valueOf(1),
        LogicFlowTemplateContractTestSupport.requireConnection(addFlow, "同步子表", 0, "合并结果").getTargetInputPort());
    assertEquals(Integer.valueOf(0),
        LogicFlowTemplateContractTestSupport.requireConnection(addFlow, "合并结果", 0, "执行后置脚本").getTargetInputPort());
    LogicFlowTemplateContractTestSupport.assertExplicitSuccessfulTerminalRemainsValid("demo.customer.add", addFlow);
  }

  @Test
  void updateProjectionShouldUseSingleChildCollectionSyncNode() throws Exception {
    LogicFlowTemplateContractTestSupport.bindMinimalFieldTypeCatalog();
    LogicFlowTemplateContractTestSupport.bindMinimalDataModelCenter();

    var model = LogicFlowTemplateContractTestSupport.createCustomerModel(false, true);
    var context = new ModelProjectionPipelineContext<DefaultBusinessModel>(model);
    context.setAttribute("hasDataModel", true);
    LogicFlowTemplateContractTestSupport.seedCurrentDataModelMeta(context);

    new UpdateMPS().process(context);

    var updateFlow = context.getModel(FlowModelMeta.class, "demo.customer.update")
        .orElseThrow(() -> new AssertionError("missing update logic flow"));
    assertSyncChildCollectionNode(updateFlow, "replace");
    assertNoLegacyChildCrudNodes(updateFlow);
    assertEquals(Integer.valueOf(0),
        LogicFlowTemplateContractTestSupport.requireConnection(updateFlow, "执行更新操作", 0, "同步子表").getTargetInputPort());
    assertEquals(Integer.valueOf(0), LogicFlowTemplateContractTestSupport
        .requireConnection(updateFlow, "执行前置脚本", 0, "提取子表同步提交数据")
        .getTargetInputPort());
    assertEquals(Integer.valueOf(1), LogicFlowTemplateContractTestSupport
        .requireConnection(updateFlow, "提取子表同步提交数据", 0, "同步子表")
        .getTargetInputPort());
    assertMergeNode(updateFlow);
    assertEquals(Integer.valueOf(0),
        LogicFlowTemplateContractTestSupport.requireConnection(updateFlow, "执行更新操作", 0, "合并结果").getTargetInputPort());
    assertEquals(Integer.valueOf(1),
        LogicFlowTemplateContractTestSupport.requireConnection(updateFlow, "同步子表", 0, "合并结果").getTargetInputPort());
    assertEquals(Integer.valueOf(0),
        LogicFlowTemplateContractTestSupport.requireConnection(updateFlow, "合并结果", 0, "执行后置脚本").getTargetInputPort());
    LogicFlowTemplateContractTestSupport.assertExplicitSuccessfulTerminalRemainsValid("demo.customer.update", updateFlow);
  }

  private void assertSyncChildCollectionNode(FlowModelMeta flowModel, String mode) {
    var syncNode = LogicFlowTemplateContractTestSupport.requireNode(flowModel, "同步子表");
    assertEquals("syncChildCollections", syncNode.getType());
    assertEquals(mode, syncNode.getExtraProp(String.class, "mode").orElse(null));
    assertTrue(syncNode.getExtraProp("plans").orElse(null) instanceof List<?>);
    assertFalse(((List<?>) syncNode.getExtraProp("plans").orElseThrow()).isEmpty());
  }

  private void assertMergeNode(FlowModelMeta flowModel) {
    var mergeNode = LogicFlowTemplateContractTestSupport.requireNode(flowModel, "合并结果");
    assertEquals("merge", mergeNode.getType());
    assertEquals(Boolean.FALSE, mergeNode.getExtraProp(Boolean.class, "deepMerge").orElse(null));
  }

  private void assertNoLegacyChildCrudNodes(FlowModelMeta flowModel) {
    assertFalse(flowModel.getNodes().stream().anyMatch(node ->
        "callFlow".equals(node.getType())
            && node.getExtraProp(Boolean.class, "executeForEachItem").orElse(false)));
    assertFalse(flowModel.getNodes().stream().anyMatch(node ->
        "subtract".equals(node.getType()) || "intersect".equals(node.getType())));
  }
}
