package com.ouroboros.workflow.approval.activiti6.transpiler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.MultiInstanceLoopCharacteristics;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.SequenceFlow;
import org.activiti.bpmn.model.UserTask;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.definition.codec.CompositeFlowDefinitionCodec;

class Activiti6WorkflowTranspilerApprovalConditionTest {
  private final CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();

  @Test
  void transpileSingleApprovalUsesExecutionVariableAccessForHyphenatedNodeIds() {
    Process process = transpile(singleApprovalFlowJson()).getMainProcess();

    List<String> conditions = process.findFlowElementsOfType(SequenceFlow.class).stream()
        .filter(flow -> "approve-a_decision_gateway".equals(flow.getSourceRef()))
        .map(SequenceFlow::getConditionExpression)
        .sorted()
        .collect(Collectors.toList());

    assertEquals(
        Arrays.asList(
            "${execution.getVariable('approve-a_approved')==false && execution.getVariable('approve-a_approval_action')==\"reject\"}",
            "${execution.getVariable('approve-a_approved')==false && execution.getVariable('approve-a_approval_action')==\"rollback\"}",
            "${execution.getVariable('approve-a_approved')==true}"),
        conditions);
  }

  @Test
  void transpileMultipleApprovalUsesExecutionVariableAccessForHyphenatedNodeIds() {
    Process process = transpile(multipleApprovalFlowJson()).getMainProcess();

    UserTask approvalTask = (UserTask) process.getFlowElement("multi-a");
    MultiInstanceLoopCharacteristics loop = approvalTask.getLoopCharacteristics();
    assertEquals(
        "${(nrOfCompletedInstances >= nrOfInstances) || (execution.getVariable('multi-a_multiple_approval_completed')==true)}",
        loop.getCompletionCondition());

    List<String> conditions = process.findFlowElementsOfType(SequenceFlow.class).stream()
        .filter(flow -> "multi-a_decision_gateway".equals(flow.getSourceRef()))
        .map(SequenceFlow::getConditionExpression)
        .sorted()
        .collect(Collectors.toList());

    assertEquals(
        Arrays.asList(
            "${execution.getVariable('multi-a_approved')==false}",
            "${execution.getVariable('multi-a_approved')==true}"),
        conditions);
  }

  private BpmnModel transpile(String json) {
    return new Activiti6WorkflowTranspiler().transpile(codec.decode(json).get());
  }

  private String singleApprovalFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-single-a",
          "name": "单签审批",
          "category": "",
          "icon": "shenpi",
          "description": "",
          "formType": "default",
          "formKey": "single-form",
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "start-a", "permission": [] },
            { "type": "审批节点", "name": "经理审批", "id": "approve-a", "subType": "单签", "candidates": [], "permission": [], "actions": ["reject", "rollback"] },
            { "type": "结束节点", "name": "结束", "id": "end-a" }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "approve-a" },
            { "id": "e2", "type": "SEQUENCE", "sourceId": "approve-a", "targetId": "end-a" }
          ]
        }
        """;
  }

  private String multipleApprovalFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-multi-a",
          "name": "会签审批",
          "category": "",
          "icon": "shenpi",
          "description": "",
          "formType": "default",
          "formKey": "multi-form",
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "start-a", "permission": [] },
            {
              "type": "审批节点",
              "name": "会签审批",
              "id": "multi-a",
              "subType": "会签",
              "candidates": [],
              "permission": [],
              "actions": ["reject"],
              "percentage": 100,
              "multipleMode": "并行",
              "rejectAction": "reject"
            },
            { "type": "结束节点", "name": "结束", "id": "end-a" }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "multi-a" },
            { "id": "e2", "type": "SEQUENCE", "sourceId": "multi-a", "targetId": "end-a" }
          ]
        }
        """;
  }
}
