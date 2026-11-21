package com.ouroboros.workflow.approval.activiti6.transpiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import java.util.stream.Collectors;

import org.activiti.bpmn.model.ActivitiListener;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.CallActivity;
import org.activiti.bpmn.model.CustomProperty;
import org.activiti.bpmn.model.ExclusiveGateway;
import org.activiti.bpmn.model.ImplementationType;
import org.activiti.bpmn.model.InclusiveGateway;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.SequenceFlow;
import org.activiti.bpmn.model.ServiceTask;
import org.activiti.bpmn.model.StartEvent;
import org.activiti.bpmn.model.UserTask;
import org.activiti.engine.delegate.ExecutionListener;
import org.activiti.engine.delegate.TaskListener;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.definition.codec.CompositeFlowDefinitionCodec;
import com.ouroboros.workflow.approval.activiti6.util.BpmnUtil;

class Activiti6WorkflowTranspilerAdvancedNodeTest {
  private final CompositeFlowDefinitionCodec codec = new CompositeFlowDefinitionCodec();

  @Test
  void transpileStructuredDefinitionBuildsSubflowCallActivityIoMapping() {
    Process process = transpile(advancedFlowJson()).getMainProcess();

    CallActivity callActivity = (CallActivity) process.getFlowElement("sub-a");
    assertNotNull(callActivity);
    assertEquals("child-flow", callActivity.getCalledElement());
    assertEquals(4, callActivity.getInParameters().size());
    assertEquals(3, callActivity.getOutParameters().size());
    assertEquals("__processStarter", callActivity.getInParameters().get(0).getSource());
    assertEquals("__processStarter", callActivity.getInParameters().get(0).getTarget());
    assertEquals("__userSelectedAssignees", callActivity.getInParameters().get(1).getSource());
    assertEquals("__userSelectedAssignees", callActivity.getInParameters().get(1).getTarget());
    assertEquals("__processLastFinishedTaskId", callActivity.getInParameters().get(2).getSource());
    assertEquals("__processLastFinishedTaskId", callActivity.getInParameters().get(2).getTarget());
    assertEquals("true", callActivity.getInParameters().get(3).getSourceExpression());
    assertEquals("__hasParentFlow", callActivity.getInParameters().get(3).getTarget());
    assertEquals("childResult", callActivity.getOutParameters().get(0).getSource());
    assertEquals("result", callActivity.getOutParameters().get(0).getTarget());
    assertEquals("__processApproved", callActivity.getOutParameters().get(1).getSource());
    assertEquals("sub-a_approved", callActivity.getOutParameters().get(1).getTarget());
    assertEquals("__processLastFinishedTaskId", callActivity.getOutParameters().get(2).getSource());
    assertEquals("__processLastFinishedTaskId", callActivity.getOutParameters().get(2).getTarget());
  }

  @Test
  void transpileStructuredDefinitionUsesDelegateExpressionCallbacks() {
    Process process = transpile(multipleApprovalFlowJson()).getMainProcess();

    StartEvent startEvent = (StartEvent) process.getFlowElement(Constants.SpecialNode.START_EVENT_ID);
    assertNotNull(startEvent);
    assertNotNull(startEvent.getExecutionListeners());
    assertEquals(1, startEvent.getExecutionListeners().size());
    assertDelegateListener(
        startEvent.getExecutionListeners().get(0),
        ExecutionListener.EVENTNAME_START,
        "${flowStartExecutionListener}");

    ServiceTask initializeFlowTask = (ServiceTask) process.getFlowElement(Constants.SpecialNode.INITIALIZE_FLOW_ID);
    assertNotNull(initializeFlowTask);
    assertEquals(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION, initializeFlowTask.getImplementationType());
    assertEquals("${initializeFlowServiceTask}", initializeFlowTask.getImplementation());

    ServiceTask failServiceTask = (ServiceTask) process.getFlowElement(Constants.SpecialNode.FAIL_NODE_ID);
    assertNotNull(failServiceTask);
    assertEquals(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION, failServiceTask.getImplementationType());
    assertEquals("${failServiceTask}", failServiceTask.getImplementation());
  }

  @Test
  void transpileMultipleApprovalAttachesDelegateExpressionLifecycleListeners() {
    Process process = transpile(multipleApprovalFlowJson()).getMainProcess();

    UserTask approvalTask = (UserTask) process.getFlowElement("multi-a");
    assertNotNull(approvalTask);
    assertNotNull(approvalTask.getExecutionListeners());
    assertEquals(2, approvalTask.getExecutionListeners().size());
    assertNotNull(approvalTask.getTaskListeners());
    assertEquals(2, approvalTask.getTaskListeners().size());

    Map<String, ActivitiListener> executionListeners = approvalTask.getExecutionListeners().stream()
        .collect(Collectors.toMap(ActivitiListener::getEvent, listener -> listener));
    Map<String, ActivitiListener> taskListeners = approvalTask.getTaskListeners().stream()
        .collect(Collectors.toMap(ActivitiListener::getEvent, listener -> listener));

    assertDelegateListener(
        executionListeners.get(ExecutionListener.EVENTNAME_START),
        ExecutionListener.EVENTNAME_START,
        "${multipleApprovalStartExecutionListener}");
    assertDelegateListener(
        executionListeners.get(ExecutionListener.EVENTNAME_END),
        ExecutionListener.EVENTNAME_END,
        "${multipleApprovalEndExecutionListener}");
    assertDelegateListener(
        taskListeners.get(TaskListener.EVENTNAME_CREATE),
        TaskListener.EVENTNAME_CREATE,
        "${createMultipleApprovalTaskListener}");
    assertDelegateListener(
        taskListeners.get(TaskListener.EVENTNAME_COMPLETE),
        TaskListener.EVENTNAME_COMPLETE,
        "${multipleApprovalCompletedTaskListener}");
  }

  @Test
  void transpileHandleNodeBuildsUserTaskWithHandleKindAndCreateListener() {
    Process process = transpile(handleFlowJson()).getMainProcess();

    UserTask handleTask = (UserTask) process.getFlowElement("handle-a");
    assertNotNull(handleTask);
    assertEquals("办理任务", handleTask.getName());
    assertEquals("advanced-form", handleTask.getFormKey());
    assertEquals(Constants.Value.TASK_KIND_HANDLE, BpmnUtil.readTaskKind(handleTask));
    assertEquals(1, handleTask.getTaskListeners().size());
    assertDelegateListener(
        handleTask.getTaskListeners().get(0),
        TaskListener.EVENTNAME_CREATE,
        "${createSingleApprovalTaskListener}");
    assertEquals(1, BpmnUtil.readCandidates(handleTask).size());
    assertEquals(1, handleTask.getFormProperties().size());
  }

  @Test
  void transpileEndNodeBuildsOutcomeTaskBeforeSharedEndEvent() {
    Process process = transpile(rejectedEndFlowJson()).getMainProcess();

    ServiceTask outcomeTask = (ServiceTask) process.getFlowElement(
        "end-rejected" + Constants.SpecialNode.OUTCOME_ID_SUFFIX);

    assertNotNull(outcomeTask);
    assertEquals("${setProcessOutcomeServiceTask}", outcomeTask.getImplementation());
    assertEquals("rejected", readCustomProperty(outcomeTask, Constants.Property.PROCESS_OUTCOME));
    assertNotNull(findSequenceFlow(process, outcomeTask.getId(), Constants.SpecialNode.END_EVENT_ID));
  }

  @Test
  void transpileStructuredDefinitionPersistsOnlyCanonicalDefinitionPayloadByDefault() {
    Process process = transpile(multipleApprovalFlowJson()).getMainProcess();

    assertNotNull(BpmnUtil.readFlowDefinitionRawJson(process));
    assertNull(BpmnUtil.getExtensionElementTexts(process, "flowDefinition").stream().findFirst().orElse(null));
  }

  @Test
  void transpileMultipleOutgoingWithoutJoinDefaultsToExclusiveGateway() {
    Process process = transpile(xorBranchFlowJson()).getMainProcess();

    assertNotNull(process.getFlowElement("start-a__xorSplit"));
    assertEquals(ExclusiveGateway.class, process.getFlowElement("start-a__xorSplit").getClass());
    assertNull(process.getFlowElement("start-a__andSplit"));
    assertNull(process.getFlowElement("start-a__orSplit"));
  }

  @Test
  void transpileMultipleOutgoingWithExplicitOrJoinUsesInclusiveGateway() {
    Process process = transpile(orJoinBranchFlowJson()).getMainProcess();

    assertNotNull(process.getFlowElement("start-a__orSplit"));
    assertEquals(InclusiveGateway.class, process.getFlowElement("start-a__orSplit").getClass());
    assertNull(process.getFlowElement("start-a__xorSplit"));
    assertNull(process.getFlowElement("start-a__andSplit"));
  }

  private void assertDelegateListener(ActivitiListener listener, String event, String implementation) {
    assertNotNull(listener);
    assertEquals(event, listener.getEvent());
    assertEquals(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION, listener.getImplementationType());
    assertEquals(implementation, listener.getImplementation());
  }

  private BpmnModel transpile(String json) {
    return new Activiti6WorkflowTranspiler().transpile(codec.decode(json).get());
  }

  private String readCustomProperty(ServiceTask task, String propertyName) {
    for (CustomProperty customProperty : task.getCustomProperties()) {
      if (propertyName.equals(customProperty.getName())) {
        return customProperty.getSimpleValue();
      }
    }
    return null;
  }

  private SequenceFlow findSequenceFlow(Process process, String sourceRef, String targetRef) {
    return process.findFlowElementsOfType(SequenceFlow.class).stream()
        .filter(sequenceFlow -> sourceRef.equals(sequenceFlow.getSourceRef()) && targetRef.equals(sequenceFlow.getTargetRef()))
        .findFirst()
        .orElse(null);
  }

  private String advancedFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-advanced",
          "name": "复杂节点审批",
          "category": "",
          "icon": "shenpi",
          "description": "",
          "formType": "default",
          "formKey": "advanced-form",
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "start-a", "permission": [] },
            { "type": "子流程节点", "name": "子流程", "id": "sub-a", "subFlowKey": "child-flow", "rejectAction": "reject", "inParameters": [], "outParameters": [{ "source": "childResult", "target": "result", "expression": null }] },
            { "type": "结束节点", "name": "结束", "id": "end-a" }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "sub-a" },
            { "id": "e2", "type": "SEQUENCE", "sourceId": "sub-a", "targetId": "end-a" }
          ]
        }
        """;
  }

  private String multipleApprovalFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-multiple",
          "name": "会签监听器",
          "category": "",
          "icon": "shenpi",
          "description": "",
          "formType": "default",
          "formKey": "advanced-form",
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "start-a", "permission": [] },
            { "type": "审批节点", "name": "多人会签", "id": "multi-a", "subType": "会签", "candidates": [], "permission": [], "multipleMode": "并行", "percentage": 75, "rejectAction": "reject" },
            { "type": "结束节点", "name": "结束", "id": "end-a" }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "multi-a" },
            { "id": "e2", "type": "SEQUENCE", "sourceId": "multi-a", "targetId": "end-a" }
          ]
        }
        """;
  }

  private String handleFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-handle",
          "name": "办理节点流程",
          "category": "",
          "icon": "shenpi",
          "description": "",
          "formType": "default",
          "formKey": "advanced-form",
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "start-a", "permission": [] },
            {
              "type": "HandleNode",
              "name": "办理任务",
              "id": "handle-a",
              "candidates": [{ "type": "User", "users": ["user-a"] }],
              "permission": [{ "name": "amount", "label": "金额", "visible": true, "writable": true, "required": false }]
            },
            { "type": "结束节点", "name": "结束", "id": "end-a" }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "handle-a" },
            { "id": "e2", "type": "SEQUENCE", "sourceId": "handle-a", "targetId": "end-a" }
          ]
        }
        """;
  }

  private String rejectedEndFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-rejected-end",
          "name": "拒绝结束流程",
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "start-a", "permission": [] },
            { "type": "结束节点", "name": "拒绝结束", "id": "end-rejected", "outcome": "rejected" }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "end-rejected" }
          ]
        }
        """;
  }

  private String xorBranchFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-xor-branch",
          "name": "默认异或分支",
          "nodes": [
            { "type": "发起节点", "id": "start-a", "permission": [] },
            { "type": "审批节点", "id": "approve-a", "subType": "单签", "candidates": [] },
            { "type": "审批节点", "id": "approve-b", "subType": "单签", "candidates": [] },
            { "type": "结束节点", "id": "end-a" }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "approve-a", "condition": { "left": { "type": "field", "field": "amount" }, "op": "greater", "right": 1000 } },
            { "id": "e2", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "approve-b" },
            { "id": "e3", "type": "SEQUENCE", "sourceId": "approve-a", "targetId": "end-a" },
            { "id": "e4", "type": "SEQUENCE", "sourceId": "approve-b", "targetId": "end-a" }
          ]
        }
        """;
  }

  private String orJoinBranchFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-or-branch",
          "name": "显式OR分支",
          "nodes": [
            { "type": "发起节点", "id": "start-a", "permission": [] },
            { "type": "审批节点", "id": "approve-a", "subType": "单签", "candidates": [] },
            { "type": "审批节点", "id": "approve-b", "subType": "单签", "candidates": [] },
            { "type": "JoinNode", "id": "join-a", "joinType": "OR" },
            { "type": "结束节点", "id": "end-a" }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "approve-a", "condition": { "left": { "type": "field", "field": "amount" }, "op": "greater", "right": 1000 } },
            { "id": "e2", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "approve-b" },
            { "id": "e3", "type": "SEQUENCE", "sourceId": "approve-a", "targetId": "join-a" },
            { "id": "e4", "type": "SEQUENCE", "sourceId": "approve-b", "targetId": "join-a" },
            { "id": "e5", "type": "SEQUENCE", "sourceId": "join-a", "targetId": "end-a" }
          ]
        }
        """;
  }
}
