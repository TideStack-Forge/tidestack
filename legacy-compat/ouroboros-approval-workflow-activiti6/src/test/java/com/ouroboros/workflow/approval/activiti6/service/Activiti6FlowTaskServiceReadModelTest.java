package com.ouroboros.workflow.approval.activiti6.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.history.HistoricTaskInstanceQuery;
import org.activiti.engine.history.HistoricVariableInstance;
import org.activiti.engine.history.HistoricVariableInstanceQuery;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.DataObject;
import org.activiti.engine.task.Comment;
import org.activiti.engine.task.IdentityLink;
import org.activiti.engine.task.Task;
import org.activiti.engine.task.TaskQuery;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.model.FlowTaskDetail;

class Activiti6FlowTaskServiceReadModelTest {
  @Test
  void getCommentUsesLatestTaskCommentWhenMultipleCommentsExist() {
    TaskService taskService = mock(TaskService.class);
    Activiti6FlowTaskService service = new Activiti6FlowTaskService(taskService, Collections.emptyList());
    Comment olderComment = mock(Comment.class);
    Comment newerComment = mock(Comment.class);

    when(olderComment.getTime()).thenReturn(dateOf(2026, Month.MARCH, 19, 18, 0));
    when(olderComment.getFullMessage()).thenReturn("older-comment");
    when(newerComment.getTime()).thenReturn(dateOf(2026, Month.MARCH, 19, 18, 1));
    when(newerComment.getFullMessage()).thenReturn("newer-comment");
    when(taskService.getTaskComments("task-1")).thenReturn(java.util.Arrays.asList(olderComment, newerComment));

    assertEquals("newer-comment", service.getComment("task-1"));
  }

  @Test
  void getHistoryTaskListOrdersByEndTimeDescAndMapsFinishedTaskDetails() {
    TaskService taskService = mock(TaskService.class);
    HistoryService historyService = mock(HistoryService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    Activiti6FlowTaskService service = new Activiti6FlowTaskService(taskService, Collections.emptyList());
    service.historyService = historyService;
    service.repositoryService = repositoryService;

    HistoricTaskInstanceQuery historicTaskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricTaskInstanceQuery orderedQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricTaskInstance historicTask = mock(HistoricTaskInstance.class);
    HistoricVariableInstanceQuery variableQuery = mock(HistoricVariableInstanceQuery.class);
    HistoricVariableInstance starterVariable = mock(HistoricVariableInstance.class);
    ProcessDefinition definition = mock(ProcessDefinition.class);

    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.taskAssignee("user-a")).thenReturn(historicTaskQuery);
    when(historicTaskQuery.finished()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.listPage(0, 20)).thenThrow(new AssertionError("history task list should order by end time before paging"));
    when(historicTaskQuery.orderByHistoricTaskInstanceEndTime()).thenReturn(orderedQuery);
    when(orderedQuery.desc()).thenReturn(orderedQuery);
    when(orderedQuery.listPage(0, 20)).thenReturn(Collections.singletonList(historicTask));

    when(historicTask.getId()).thenReturn("task-h-1");
    when(historicTask.getName()).thenReturn("财务审批");
    when(historicTask.getProcessInstanceId()).thenReturn("instance-2");
    when(historicTask.getProcessDefinitionId()).thenReturn("process-definition-2");
    when(historicTask.getTaskDefinitionKey()).thenReturn("approve-b");
    when(historicTask.getAssignee()).thenReturn("user-a");
    when(historicTask.getStartTime()).thenReturn(dateOf(2026, Month.MARCH, 17, 10, 0));
    when(historicTask.getEndTime()).thenReturn(dateOf(2026, Month.MARCH, 17, 11, 15));

    when(historyService.createHistoricVariableInstanceQuery()).thenReturn(variableQuery);
    when(variableQuery.processInstanceId("instance-2")).thenReturn(variableQuery);
    when(variableQuery.variableName(Constants.Variable.PROCESS_STARTER)).thenReturn(variableQuery);
    when(variableQuery.singleResult()).thenReturn(starterVariable);
    when(starterVariable.getValue()).thenReturn("starter-2");

    when(repositoryService.getProcessDefinition("process-definition-2")).thenReturn(definition);
    when(definition.getKey()).thenReturn("flow-b");
    when(definition.getName()).thenReturn("报销审批");

    List<FlowTaskDetail> details = service.getHistoryTaskList("user-a", 0, 20);

    assertEquals(1, details.size());
    FlowTaskDetail detail = details.get(0);
    assertEquals("task-h-1", detail.getId());
    assertEquals("instance-2", detail.getInstanceId());
    assertEquals("approve-b", detail.getTaskDefinitionKey());
    assertEquals("flow-b", detail.getFlowKey());
    assertEquals("报销审批", detail.getFlowName());
    assertEquals("starter-2", detail.getStarter());
    assertEquals("user-a", detail.getAssignee());
    assertEquals(LocalDateTime.of(2026, 3, 17, 10, 0), detail.getStartTime());
    assertEquals(LocalDateTime.of(2026, 3, 17, 11, 15), detail.getEndTime());
    assertEquals(Boolean.FALSE, detail.isActive());
    assertEquals(Collections.emptyList(), detail.getCandidateUsers());
  }

  @Test
  void getHistoryTaskListByFlowKeyOrdersByEndTimeDescBeforePaging() {
    TaskService taskService = mock(TaskService.class);
    HistoryService historyService = mock(HistoryService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    Activiti6FlowTaskService service = new Activiti6FlowTaskService(taskService, Collections.emptyList());
    service.historyService = historyService;
    service.repositoryService = repositoryService;

    HistoricTaskInstanceQuery historicTaskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricTaskInstanceQuery orderedQuery = mock(HistoricTaskInstanceQuery.class);

    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.taskAssignee("user-a")).thenReturn(historicTaskQuery);
    when(historicTaskQuery.processDefinitionKey("flow-a")).thenReturn(historicTaskQuery);
    when(historicTaskQuery.finished()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.listPage(0, 20)).thenThrow(new AssertionError("filtered history task list should order by end time before paging"));
    when(historicTaskQuery.orderByHistoricTaskInstanceEndTime()).thenReturn(orderedQuery);
    when(orderedQuery.desc()).thenReturn(orderedQuery);
    when(orderedQuery.listPage(0, 20)).thenReturn(Collections.emptyList());

    assertEquals(Collections.emptyList(), service.getHistoryTaskList("user-a", "flow-a", 0, 20));
  }

  @Test
  void getTaskListToleratesMissingHistoryAndProcessDefinition() {
    TaskService taskService = mock(TaskService.class);
    HistoryService historyService = mock(HistoryService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    Activiti6FlowTaskService service = new Activiti6FlowTaskService(taskService, Collections.emptyList());
    service.historyService = historyService;
    service.repositoryService = repositoryService;

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    HistoricTaskInstanceQuery historicTaskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricVariableInstanceQuery variableQuery = mock(HistoricVariableInstanceQuery.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskCandidateOrAssigned("user-a")).thenReturn(taskQuery);
    when(taskQuery.active()).thenReturn(taskQuery);
    when(taskQuery.orderByTaskCreateTime()).thenReturn(taskQuery);
    when(taskQuery.desc()).thenReturn(taskQuery);
    when(taskQuery.listPage(0, 20)).thenReturn(Collections.singletonList(task));

    when(task.getId()).thenReturn("task-1");
    when(task.getName()).thenReturn("经理审批");
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    when(task.getProcessDefinitionId()).thenReturn("process-definition-missing");
    when(task.getTaskDefinitionKey()).thenReturn("approve-a");
    when(task.getAssignee()).thenReturn("approver-1");

    when(historyService.createHistoricVariableInstanceQuery()).thenReturn(variableQuery);
    when(variableQuery.processInstanceId("instance-1")).thenReturn(variableQuery);
    when(variableQuery.variableName(Constants.Variable.PROCESS_STARTER)).thenReturn(variableQuery);
    when(variableQuery.listPage(0, 1)).thenReturn(Collections.emptyList());

    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.taskId("task-1")).thenReturn(historicTaskQuery);
    when(historicTaskQuery.singleResult()).thenReturn(null);

    when(repositoryService.getProcessDefinition("process-definition-missing")).thenReturn(null);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.emptyList());

    List<FlowTaskDetail> details = service.getTaskList("user-a", 0, 20);

    assertEquals(1, details.size());
    FlowTaskDetail detail = details.get(0);
    assertEquals("task-1", detail.getId());
    assertEquals("instance-1", detail.getInstanceId());
    assertEquals("approve-a", detail.getTaskDefinitionKey());
    assertNull(detail.getFlowKey());
    assertNull(detail.getFlowName());
    assertNull(detail.getStarter());
    assertNull(detail.getStartTime());
    assertNull(detail.getEndTime());
    assertEquals("approver-1", detail.getAssignee());
    assertEquals(Collections.emptyList(), detail.getCandidateUsers());
  }

  @Test
  void getTaskListToleratesNullStarterVariableValue() {
    TaskService taskService = mock(TaskService.class);
    HistoryService historyService = mock(HistoryService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    Activiti6FlowTaskService service = new Activiti6FlowTaskService(taskService, Collections.emptyList());
    service.historyService = historyService;
    service.repositoryService = repositoryService;

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    HistoricTaskInstanceQuery historicTaskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricTaskInstance historicTask = mock(HistoricTaskInstance.class);
    HistoricVariableInstanceQuery variableQuery = mock(HistoricVariableInstanceQuery.class);
    HistoricVariableInstance starterVariable = mock(HistoricVariableInstance.class);
    ProcessDefinition definition = mock(ProcessDefinition.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskCandidateOrAssigned("user-a")).thenReturn(taskQuery);
    when(taskQuery.active()).thenReturn(taskQuery);
    when(taskQuery.orderByTaskCreateTime()).thenReturn(taskQuery);
    when(taskQuery.desc()).thenReturn(taskQuery);
    when(taskQuery.listPage(0, 20)).thenReturn(Collections.singletonList(task));

    when(task.getId()).thenReturn("task-1");
    when(task.getName()).thenReturn("经理审批");
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    when(task.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(task.getTaskDefinitionKey()).thenReturn("approve-a");
    when(task.getAssignee()).thenReturn("approver-1");

    when(historyService.createHistoricVariableInstanceQuery()).thenReturn(variableQuery);
    when(variableQuery.processInstanceId("instance-1")).thenReturn(variableQuery);
    when(variableQuery.variableName(Constants.Variable.PROCESS_STARTER)).thenReturn(variableQuery);
    when(variableQuery.listPage(0, 1)).thenReturn(Collections.singletonList(starterVariable));
    when(starterVariable.getValue()).thenReturn(null);

    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.taskId("task-1")).thenReturn(historicTaskQuery);
    when(historicTaskQuery.singleResult()).thenReturn(historicTask);
    when(historicTask.getStartTime()).thenReturn(dateOf(2026, Month.MARCH, 18, 9, 30));

    when(repositoryService.getProcessDefinition("process-definition-1")).thenReturn(definition);
    when(definition.getKey()).thenReturn("flow-a");
    when(definition.getName()).thenReturn("采购审批");
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.emptyList());

    List<FlowTaskDetail> details = service.getTaskList("user-a", 0, 20);

    assertEquals(1, details.size());
    assertNull(details.get(0).getStarter());
  }

  @Test
  void getTaskListOrdersByCreateTimeDescBeforeListing() {
    TaskService taskService = mock(TaskService.class);
    HistoryService historyService = mock(HistoryService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    Activiti6FlowTaskService service = new Activiti6FlowTaskService(taskService, Collections.emptyList());
    service.historyService = historyService;
    service.repositoryService = repositoryService;

    TaskQuery taskQuery = mock(TaskQuery.class);
    TaskQuery orderedQuery = mock(TaskQuery.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskCandidateOrAssigned("user-a")).thenReturn(taskQuery);
    when(taskQuery.active()).thenReturn(taskQuery);
    when(taskQuery.list()).thenThrow(new AssertionError("active task list should order by create time before listing"));
    when(taskQuery.orderByTaskCreateTime()).thenReturn(orderedQuery);
    when(orderedQuery.desc()).thenReturn(orderedQuery);
    when(orderedQuery.list()).thenReturn(Collections.emptyList());

    assertEquals(Collections.emptyList(), service.getTaskList("user-a"));
  }

  @Test
  void getTaskListByFlowKeyOrdersByCreateTimeDescBeforeListing() {
    TaskService taskService = mock(TaskService.class);
    HistoryService historyService = mock(HistoryService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    Activiti6FlowTaskService service = new Activiti6FlowTaskService(taskService, Collections.emptyList());
    service.historyService = historyService;
    service.repositoryService = repositoryService;

    TaskQuery taskQuery = mock(TaskQuery.class);
    TaskQuery orderedQuery = mock(TaskQuery.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.active()).thenReturn(taskQuery);
    when(taskQuery.taskCandidateOrAssigned("user-a")).thenReturn(taskQuery);
    when(taskQuery.processDefinitionKey("flow-a")).thenReturn(taskQuery);
    when(taskQuery.list()).thenThrow(new AssertionError("filtered active task list should order by create time before listing"));
    when(taskQuery.orderByTaskCreateTime()).thenReturn(orderedQuery);
    when(orderedQuery.desc()).thenReturn(orderedQuery);
    when(orderedQuery.list()).thenReturn(Collections.emptyList());

    assertEquals(Collections.emptyList(), service.getTaskList("user-a", "flow-a"));
  }

  @Test
  void getTaskListByFlowKeyOrdersByCreateTimeDescBeforePaging() {
    TaskService taskService = mock(TaskService.class);
    HistoryService historyService = mock(HistoryService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    Activiti6FlowTaskService service = new Activiti6FlowTaskService(taskService, Collections.emptyList());
    service.historyService = historyService;
    service.repositoryService = repositoryService;

    TaskQuery taskQuery = mock(TaskQuery.class);
    TaskQuery orderedQuery = mock(TaskQuery.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.active()).thenReturn(taskQuery);
    when(taskQuery.taskCandidateOrAssigned("user-a")).thenReturn(taskQuery);
    when(taskQuery.processDefinitionKey("flow-a")).thenReturn(taskQuery);
    when(taskQuery.listPage(0, 20)).thenThrow(new AssertionError("filtered active task list should order by create time before paging"));
    when(taskQuery.orderByTaskCreateTime()).thenReturn(orderedQuery);
    when(orderedQuery.desc()).thenReturn(orderedQuery);
    when(orderedQuery.listPage(0, 20)).thenReturn(Collections.emptyList());

    assertEquals(Collections.emptyList(), service.getTaskList("user-a", "flow-a", 0, 20));
  }

  @Test
  void getAllActiveTaskListOrdersByCreateTimeDescBeforeListing() {
    TaskService taskService = mock(TaskService.class);
    HistoryService historyService = mock(HistoryService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    Activiti6FlowTaskService service = new Activiti6FlowTaskService(taskService, Collections.emptyList());
    service.historyService = historyService;
    service.repositoryService = repositoryService;

    TaskQuery taskQuery = mock(TaskQuery.class);
    TaskQuery orderedQuery = mock(TaskQuery.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.active()).thenReturn(taskQuery);
    when(taskQuery.list()).thenThrow(new AssertionError("all active task list should order by create time before listing"));
    when(taskQuery.orderByTaskCreateTime()).thenReturn(orderedQuery);
    when(orderedQuery.desc()).thenReturn(orderedQuery);
    when(orderedQuery.list()).thenReturn(Collections.emptyList());

    assertEquals(Collections.emptyList(), service.getAllActiveTaskList());
  }

  @Test
  void readHelpersExposePayloadFormCommentAndAssignee() {
    TaskService taskService = mock(TaskService.class);
    Activiti6FlowTaskService service = new Activiti6FlowTaskService(taskService, Collections.emptyList());

    DataObject dataObject = mock(DataObject.class);
    Comment oldComment = mock(Comment.class);
    Comment latestComment = mock(Comment.class);
    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    Map<String, Object> formData = new HashMap<>();
    formData.put("amount", 99);

    when(taskService.getVariableLocal("task-1", Constants.TASK_FORM_DATA_KEY)).thenReturn(formData);
    when(taskService.getVariableLocal("task-1", Constants.TASK_PAYLOAD_KEY)).thenReturn("{\"approved\":true}");
    when(taskService.getDataObject("task-1", Constants.TASK_PAYLOAD_KEY)).thenReturn(dataObject);
    when(dataObject.getValue()).thenReturn("{\"approved\":false}");
    when(taskService.getTaskComments("task-1")).thenReturn(java.util.Arrays.asList(oldComment, latestComment));
    when(oldComment.getTime()).thenReturn(dateOf(2026, Month.MARCH, 18, 8, 0));
    when(oldComment.getFullMessage()).thenReturn("old comment");
    when(latestComment.getTime()).thenReturn(dateOf(2026, Month.MARCH, 18, 8, 30));
    when(latestComment.getFullMessage()).thenReturn("latest comment");
    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(task.getAssignee()).thenReturn("approver-1");

    Map<?, ?> payload = service.getPayload("task-1", HashMap.class);

    assertEquals(Boolean.TRUE, payload.get("approved"));
    assertEquals(formData, service.getTaskFormData("task-1"));
    assertEquals("latest comment", service.getComment("task-1"));
    assertEquals("approver-1", service.getTaskAssignee("task-1"));

    service.setTaskAssignee("task-1", "approver-2");

    verify(taskService).setAssignee("task-1", "approver-2");
  }

  @Test
  void getTaskAssigneeReturnsNullWhenTaskMissing() {
    TaskService taskService = mock(TaskService.class);
    Activiti6FlowTaskService service = new Activiti6FlowTaskService(taskService, Collections.emptyList());

    TaskQuery taskQuery = mock(TaskQuery.class);
    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-missing")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(null);

    assertNull(service.getTaskAssignee("task-missing"));
  }

  @Test
  void getCandidateUsersIgnoresNullLinksAndNullUserIds() {
    TaskService taskService = mock(TaskService.class);
    Activiti6FlowTaskService service = new Activiti6FlowTaskService(taskService, Collections.emptyList());

    IdentityLink nullUser = mock(IdentityLink.class);
    IdentityLink candidate = mock(IdentityLink.class);
    IdentityLink assignee = mock(IdentityLink.class);

    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Arrays.asList(null, nullUser, candidate, assignee));
    when(nullUser.getType()).thenReturn("candidate");
    when(nullUser.getUserId()).thenReturn(null);
    when(candidate.getType()).thenReturn("candidate");
    when(candidate.getUserId()).thenReturn("user-a");
    when(assignee.getType()).thenReturn("assignee");
    when(assignee.getUserId()).thenReturn("user-b");

    assertEquals(Arrays.asList("user-a", "user-b"), service.getCandidateUsers("task-1"));
  }

  @Test
  void setCandidateUsersTreatsNullInputAsClearingCandidates() {
    TaskService taskService = mock(TaskService.class);
    Activiti6FlowTaskService service = new Activiti6FlowTaskService(taskService, Collections.emptyList());

    IdentityLink keep = mock(IdentityLink.class);
    IdentityLink remove = mock(IdentityLink.class);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Arrays.asList(keep, remove));
    when(keep.getType()).thenReturn("candidate");
    when(keep.getUserId()).thenReturn("user-keep");
    when(remove.getType()).thenReturn("candidate");
    when(remove.getUserId()).thenReturn("user-remove");

    service.setCandidateUsers("task-1", null);

    verify(taskService).deleteCandidateUser("task-1", "user-keep");
    verify(taskService).deleteCandidateUser("task-1", "user-remove");
    verify(taskService, never()).addCandidateUser("task-1", "user-keep");
    verify(taskService, never()).addCandidateUser("task-1", "user-remove");
  }

  private Date dateOf(int year, Month month, int day, int hour, int minute) {
    return Date.from(LocalDateTime.of(year, month, day, hour, minute)
        .atZone(ZoneId.systemDefault())
        .toInstant());
  }
}
