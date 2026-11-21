package com.ouroboros.workflow.approval.activiti6.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.Process;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricProcessInstanceQuery;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.history.HistoricTaskInstanceQuery;
import org.activiti.engine.history.HistoricVariableInstance;
import org.activiti.engine.history.HistoricVariableInstanceQuery;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.activiti6.util.BpmnUtil;
import com.ouroboros.workflow.approval.model.FlowTimeline;
import com.ouroboros.workflow.approval.model.TaskState;

class TimelineServiceTest {
  @Test
  void getTimeLineIncludesParallelMultipleApprovalNodesFromStructuredDefinition() {
    TaskService taskService = mock(TaskService.class);
    HistoryService historyService = mock(HistoryService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    TimelineService service = new TimelineService(taskService, null, historyService, repositoryService);

    HistoricProcessInstanceQuery processQuery = mock(HistoricProcessInstanceQuery.class);
    HistoricProcessInstance historicProcessInstance = mock(HistoricProcessInstance.class);
    HistoricTaskInstanceQuery taskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricTaskInstance taskA = mock(HistoricTaskInstance.class);
    HistoricTaskInstance taskB = mock(HistoricTaskInstance.class);
    HistoricVariableInstanceQuery variablesQuery = mock(HistoricVariableInstanceQuery.class);
    HistoricVariableInstance sessionA = mock(HistoricVariableInstance.class);
    HistoricVariableInstance sessionAAssignees = mock(HistoricVariableInstance.class);
    HistoricVariableInstance sessionB = mock(HistoricVariableInstance.class);
    HistoricVariableInstance sessionBAssignees = mock(HistoricVariableInstance.class);

    when(repositoryService.getBpmnModel("process-definition-1")).thenReturn(buildBpmnModel(parallelDoubleMultipleFlowJson()));

    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processQuery);
    when(processQuery.processInstanceId("instance-1")).thenReturn(processQuery);
    when(processQuery.singleResult()).thenReturn(historicProcessInstance);
    when(historicProcessInstance.getProcessDefinitionId()).thenReturn("process-definition-1");

    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskQuery);
    when(taskQuery.processInstanceId("instance-1")).thenReturn(taskQuery);
    when(taskQuery.orderByHistoricTaskInstanceStartTime()).thenReturn(taskQuery);
    when(taskQuery.asc()).thenReturn(taskQuery);
    when(taskQuery.list()).thenReturn(Arrays.asList(taskA, taskB));

    when(taskA.getId()).thenReturn("task-a");
    when(taskA.getTaskDefinitionKey()).thenReturn("multi-a");
    when(taskA.getProcessInstanceId()).thenReturn("instance-1");
    when(taskA.getAssignee()).thenReturn("user-a");
    when(taskA.getStartTime()).thenReturn(dateOf(2026, Month.MARCH, 19, 10, 0));
    when(taskA.getEndTime()).thenReturn(null);
    when(historyService.getHistoricIdentityLinksForTask("task-a")).thenReturn(Collections.emptyList());

    when(taskB.getId()).thenReturn("task-b");
    when(taskB.getTaskDefinitionKey()).thenReturn("multi-b");
    when(taskB.getProcessInstanceId()).thenReturn("instance-1");
    when(taskB.getAssignee()).thenReturn("user-c");
    when(taskB.getStartTime()).thenReturn(dateOf(2026, Month.MARCH, 19, 10, 5));
    when(taskB.getEndTime()).thenReturn(null);
    when(historyService.getHistoricIdentityLinksForTask("task-b")).thenReturn(Collections.emptyList());

    when(historyService.createHistoricVariableInstanceQuery()).thenReturn(variablesQuery);
    when(variablesQuery.processInstanceId("instance-1")).thenReturn(variablesQuery);
    when(variablesQuery.list()).thenReturn(Arrays.asList(sessionA, sessionAAssignees, sessionB, sessionBAssignees));

    when(sessionA.getTaskId()).thenReturn("task-a");
    when(sessionA.getVariableName()).thenReturn(Constants.Variable.MULTIPLE_APPROVAL_SESSION);
    when(sessionA.getValue()).thenReturn("session-a");
    when(sessionAAssignees.getVariableName()).thenReturn(
        Constants.Variable.MULTIPLE_APPROVAL_SESSION + "session-a" + Constants.Variable.MULTIPLE_APPROVAL_ASSIGNEE_LIST);
    when(sessionAAssignees.getValue()).thenReturn(Collections.singletonList("user-a"));

    when(sessionB.getTaskId()).thenReturn("task-b");
    when(sessionB.getVariableName()).thenReturn(Constants.Variable.MULTIPLE_APPROVAL_SESSION);
    when(sessionB.getValue()).thenReturn("session-b");
    when(sessionBAssignees.getVariableName()).thenReturn(
        Constants.Variable.MULTIPLE_APPROVAL_SESSION + "session-b" + Constants.Variable.MULTIPLE_APPROVAL_ASSIGNEE_LIST);
    when(sessionBAssignees.getValue()).thenReturn(Collections.singletonList("user-c"));

    when(taskService.getProcessInstanceComments("instance-1")).thenReturn(Collections.emptyList());

    List<FlowTimeline> timeline = service.getTimeLine("instance-1");
    List<String> nodeIds = timeline.stream()
        .map(item -> item.getFlowNode().getId())
        .collect(Collectors.toList());

    assertEquals(3, timeline.size());
    assertTrue(nodeIds.contains("startnode"));
    assertTrue(nodeIds.contains("multi-a"));
    assertTrue(nodeIds.contains("multi-b"));
    assertEquals(TaskState.PROCESSING, timelineForNode(timeline, "multi-a").getState());
    assertEquals(TaskState.PROCESSING, timelineForNode(timeline, "multi-b").getState());
  }

  private FlowTimeline timelineForNode(List<FlowTimeline> timeline, String nodeId) {
    return timeline.stream()
        .filter(item -> nodeId.equals(item.getFlowNode().getId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Timeline node not found: " + nodeId));
  }

  private BpmnModel buildBpmnModel(String rawJson) {
    BpmnModel model = new BpmnModel();
    Process process = new Process();
    model.addProcess(process);
    BpmnUtil.saveFlowDefinitionRawJson(process, rawJson);
    return model;
  }

  private Date dateOf(int year, Month month, int day, int hour, int minute) {
    return Date.from(LocalDateTime.of(year, month, day, hour, minute)
        .atZone(ZoneId.systemDefault())
        .toInstant());
  }

  private String parallelDoubleMultipleFlowJson() {
    return "{\n"
        + "  \"formatVersion\": 2,\n"
        + "  \"id\": \"flow-parallel-double-multiple\",\n"
        + "  \"name\": \"双并行会签共享场景\",\n"
        + "  \"category\": \"\",\n"
        + "  \"icon\": \"shenpi\",\n"
        + "  \"description\": \"\",\n"
        + "  \"formType\": \"default\",\n"
        + "  \"formKey\": \"runtime-form\",\n"
        + "  \"nodes\": [\n"
        + "    { \"type\": \"发起节点\", \"name\": \"发起节点\", \"id\": \"startnode\", \"permission\": [], \"candidates\": [] },\n"
        + "    {\n"
        + "      \"type\": \"审批节点\",\n"
        + "      \"name\": \"会签审批A\",\n"
        + "      \"id\": \"multi-a\",\n"
        + "      \"subType\": \"会签\",\n"
        + "      \"candidates\": [],\n"
        + "      \"permission\": [],\n"
        + "      \"multipleMode\": \"并行\",\n"
        + "      \"percentage\": 50,\n"
        + "      \"rejectAction\": \"reject\"\n"
        + "    },\n"
        + "    {\n"
        + "      \"type\": \"审批节点\",\n"
        + "      \"name\": \"会签审批B\",\n"
        + "      \"id\": \"multi-b\",\n"
        + "      \"subType\": \"会签\",\n"
        + "      \"candidates\": [],\n"
        + "      \"permission\": [],\n"
        + "      \"multipleMode\": \"并行\",\n"
        + "      \"percentage\": 50,\n"
        + "      \"rejectAction\": \"reject\"\n"
        + "    },\n"
        + "    {\n"
        + "      \"type\": \"结束节点\",\n"
        + "      \"name\": \"结束\",\n"
        + "      \"id\": \"end-a\",\n"
        + "      \"end\": {\n"
        + "        \"content\": \"done\",\n"
        + "        \"channel\": [\"message\"],\n"
        + "        \"candidates\": []\n"
        + "      }\n"
        + "    },\n"
        + "    { \"type\": \"JoinNode\", \"name\": \"并行汇聚\", \"id\": \"join-a\", \"joinType\": \"OR\" }\n"
        + "  ],\n"
        + "  \"edges\": [\n"
        + "    { \"id\": \"e1\", \"type\": \"SEQUENCE\", \"sourceId\": \"startnode\", \"targetId\": \"multi-a\", \"name\": \"会签A分支\", \"order\": 1 },\n"
        + "    { \"id\": \"e2\", \"type\": \"SEQUENCE\", \"sourceId\": \"startnode\", \"targetId\": \"multi-b\", \"name\": \"会签B分支\", \"order\": 2 },\n"
        + "    { \"id\": \"e3\", \"type\": \"SEQUENCE\", \"sourceId\": \"multi-a\", \"targetId\": \"join-a\" },\n"
        + "    { \"id\": \"e4\", \"type\": \"SEQUENCE\", \"sourceId\": \"multi-b\", \"targetId\": \"join-a\" },\n"
        + "    { \"id\": \"e5\", \"type\": \"SEQUENCE\", \"sourceId\": \"join-a\", \"targetId\": \"end-a\" }\n"
        + "  ]\n"
        + "}";
  }
}
