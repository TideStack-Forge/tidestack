package com.ouroboros.businessmodel.projection.logicflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class LogicFlowTemplateDirectionContractTest {

  @Test
  void getOneShouldRouteExistingRecordBranchDirectlyToSuccessfulTerminal() throws Exception {
    LogicFlowTemplateContractTestSupport.bindMinimalDataModelCenter();
    var flowModel = LogicFlowTemplateContractTestSupport.generateTemplate("logic-flow/getOne");

    LogicFlowTemplateContractTestSupport.assertExplicitSuccessfulTerminalContract("logic-flow/getOne", flowModel);
    LogicFlowTemplateContractTestSupport.assertExplicitSuccessfulTerminalRemainsValid("logic-flow/getOne", flowModel);
    LogicFlowTemplateContractTestSupport.assertNodeAbsent("logic-flow/getOne", flowModel, "收集数据");

    var successConnection = LogicFlowTemplateContractTestSupport.requireConnection(flowModel, "判断是否存在", 0, "成功返回");
    assertEquals(Integer.valueOf(0), successConnection.getTargetInputPort());
    assertEquals(1, LogicFlowTemplateContractTestSupport.incomingConnections(flowModel, "成功返回").size());
  }

  @Test
  void startProcessShouldRoutePostScriptDirectlyToSuccessfulTerminal() throws Exception {
    LogicFlowTemplateContractTestSupport.bindMinimalDataModelCenter();
    var flowModel = LogicFlowTemplateContractTestSupport.generateTemplate("logic-flow/workflow/startProcess");

    LogicFlowTemplateContractTestSupport.assertExplicitSuccessfulTerminalContract("logic-flow/workflow/startProcess", flowModel);
    LogicFlowTemplateContractTestSupport.assertExplicitSuccessfulTerminalRemainsValid("logic-flow/workflow/startProcess", flowModel);

    var filterNode = LogicFlowTemplateContractTestSupport.requireNode(flowModel, "过滤发起请求业务数据");
    var filterScript = filterNode.getExtraProp(String.class, "script").orElse("");
    assertTrue(filterScript.contains("source.formData instanceof Map"));
    assertTrue(filterScript.contains("formData.remove('taskAssignee')"));

    var prepareUpdateStatement = LogicFlowTemplateContractTestSupport.requireNode(flowModel, "准备更新参数");
    assertEquals(2, prepareUpdateStatement.getInputPorts().size());
    assertEquals("request", prepareUpdateStatement.getInputPorts().get(0).getName());
    assertEquals("formData", prepareUpdateStatement.getInputPorts().get(1).getName());
    assertTrue(prepareUpdateStatement.getExtraProp("mapping").orElse(null) instanceof Map<?, ?>);
    var prepareMapping = (Map<?, ?>) prepareUpdateStatement.getExtraProp("mapping").orElseThrow();
    assertEquals("$request.params.id", prepareMapping.get("where.id"));
    assertEquals("$formData", prepareMapping.get("set"));

    var requestFilterDependency = LogicFlowTemplateContractTestSupport.requireConnection(flowModel, "接受请求", 0, "过滤发起请求业务数据");
    assertEquals(Integer.valueOf(0), requestFilterDependency.getTargetInputPort());
    var requestPrepareDependency = LogicFlowTemplateContractTestSupport.requireConnection(flowModel, "接受请求", 0, "准备更新参数");
    assertEquals(Integer.valueOf(0), requestPrepareDependency.getTargetInputPort());
    var filteredPrepareDependency = LogicFlowTemplateContractTestSupport.requireConnection(flowModel, "过滤发起请求业务数据", 0, "准备更新参数");
    assertEquals(Integer.valueOf(1), filteredPrepareDependency.getTargetInputPort());

    var processMappingNode = LogicFlowTemplateContractTestSupport.requireNode(flowModel, "映射流程参数");
    assertEquals(3, processMappingNode.getInputPorts().size());
    assertEquals("startRequest", processMappingNode.getInputPorts().get(2).getName());
    assertTrue(processMappingNode.getExtraProp("mapping").orElse(null) instanceof Map<?, ?>);
    var processMapping = (Map<?, ?>) processMappingNode.getExtraProp("mapping").orElseThrow();
    assertEquals("$startRequest.data.taskAssignee", processMapping.get("taskAssignee"));
    var requestProcessMappingDependency = LogicFlowTemplateContractTestSupport.requireConnection(flowModel, "接受请求", 0, "映射流程参数");
    assertEquals(Integer.valueOf(2), requestProcessMappingDependency.getTargetInputPort());

    var successConnection = LogicFlowTemplateContractTestSupport.requireConnection(flowModel, "执行后置脚本", 0, "成功返回");
    assertEquals(Integer.valueOf(0), successConnection.getTargetInputPort());
    assertEquals(1, LogicFlowTemplateContractTestSupport.incomingConnections(flowModel, "成功返回").size());
  }

  @Test
  void processCallbackShouldUpdateBusinessStatusBeforeSuccessfulTerminal() throws Exception {
    LogicFlowTemplateContractTestSupport.bindMinimalDataModelCenter();
    var flowModel = LogicFlowTemplateContractTestSupport.generateTemplate("logic-flow/workflow/processCallback");

    LogicFlowTemplateContractTestSupport.assertExplicitSuccessfulTerminalContract("logic-flow/workflow/processCallback", flowModel);
    LogicFlowTemplateContractTestSupport.assertExplicitSuccessfulTerminalRemainsValid("logic-flow/workflow/processCallback", flowModel);
    LogicFlowTemplateContractTestSupport.assertNodeAbsent("logic-flow/workflow/processCallback", flowModel, "返回响应");

    for (var sourceNode : Arrays.asList("审批中", "通过", "否决", "撤销", "终止")) {
      var statusUpdateConnection = LogicFlowTemplateContractTestSupport.requireConnection(flowModel, sourceNode, 0, "准备状态更新参数");
      assertEquals(Integer.valueOf(0), statusUpdateConnection.getTargetInputPort());
    }

    var statusUpdateConnection = LogicFlowTemplateContractTestSupport.requireConnection(flowModel, "更新业务状态", 0, "成功返回");
    assertEquals(Integer.valueOf(0), statusUpdateConnection.getTargetInputPort());

    var incomingConnections = LogicFlowTemplateContractTestSupport.incomingConnections(flowModel, "成功返回");
    assertEquals(2, incomingConnections.size());
    assertTrue(incomingConnections.stream().allMatch(connection -> Integer.valueOf(0).equals(connection.getSourceOutputPort())));
    assertTrue(incomingConnections.stream().allMatch(connection -> Integer.valueOf(0).equals(connection.getTargetInputPort())));

    var sourceNodes = incomingConnections.stream()
        .map(connection -> connection.getSourceNode())
        .collect(Collectors.toSet());
    assertEquals(Arrays.stream(new String[]{"更新业务状态", "其他"}).collect(Collectors.toSet()), sourceNodes);
  }

  @Test
  void submitTaskShouldUpdateBusinessDataAfterWorkflowTaskSubmission() throws Exception {
    LogicFlowTemplateContractTestSupport.bindMinimalDataModelCenter();
    var flowModel = LogicFlowTemplateContractTestSupport.generateTemplate("logic-flow/workflow/submitTask");

    LogicFlowTemplateContractTestSupport.assertExplicitSuccessfulTerminalContract("logic-flow/workflow/submitTask", flowModel);
    LogicFlowTemplateContractTestSupport.assertExplicitSuccessfulTerminalRemainsValid("logic-flow/workflow/submitTask", flowModel);

    var prepareUpdateStatement = LogicFlowTemplateContractTestSupport.requireNode(flowModel, "准备更新参数");
    assertEquals(2, prepareUpdateStatement.getInputPorts().size());
    assertEquals("request", prepareUpdateStatement.getInputPorts().get(0).getName());
    assertEquals("formData", prepareUpdateStatement.getInputPorts().get(1).getName());
    assertTrue(prepareUpdateStatement.getExtraProp("mapping").orElse(null) instanceof Map<?, ?>);
    var prepareMapping = (Map<?, ?>) prepareUpdateStatement.getExtraProp("mapping").orElseThrow();
    assertEquals("$formData", prepareMapping.get("data"));
    assertEquals("$request.params.id", prepareMapping.get("where"));

    var requestDependency = LogicFlowTemplateContractTestSupport.requireConnection(flowModel, "接受请求", 0, "准备更新参数");
    assertEquals(Integer.valueOf(0), requestDependency.getTargetInputPort());

    var filteredBusinessFormDependency =
        LogicFlowTemplateContractTestSupport.requireConnection(flowModel, "过滤业务更新表单数据", 0, "准备更新参数");
    assertEquals(Integer.valueOf(1), filteredBusinessFormDependency.getTargetInputPort());

    var submitDependency = LogicFlowTemplateContractTestSupport.requireConnection(flowModel, "提交工作流任务", 0, "等待任务提交后更新参数");
    assertEquals(Integer.valueOf(1), submitDependency.getTargetInputPort());

    var updateStatementDependency = LogicFlowTemplateContractTestSupport.requireConnection(flowModel, "准备更新参数", 0, "等待任务提交后更新参数");
    assertEquals(Integer.valueOf(0), updateStatementDependency.getTargetInputPort());

    var delayedUpdateStatement = LogicFlowTemplateContractTestSupport.requireNode(flowModel, "等待任务提交后更新参数");
    assertTrue(delayedUpdateStatement.getExtraProp("mapping").orElse(null) instanceof Map<?, ?>);
    var delayedMapping = (Map<?, ?>) delayedUpdateStatement.getExtraProp("mapping").orElseThrow();
    assertEquals("$statement.data", delayedMapping.get("data"));
    assertEquals("$statement.where", delayedMapping.get("where"));

    var delayedUpdateConnection = LogicFlowTemplateContractTestSupport.requireConnection(flowModel, "等待任务提交后更新参数", 0, "更新业务表");
    assertEquals(Integer.valueOf(0), delayedUpdateConnection.getTargetInputPort());

    var successConnection = LogicFlowTemplateContractTestSupport.requireConnection(flowModel, "更新业务表", 0, "成功返回");
    assertEquals(Integer.valueOf(0), successConnection.getTargetInputPort());
    assertEquals(1, LogicFlowTemplateContractTestSupport.incomingConnections(flowModel, "成功返回").size());
  }
}
