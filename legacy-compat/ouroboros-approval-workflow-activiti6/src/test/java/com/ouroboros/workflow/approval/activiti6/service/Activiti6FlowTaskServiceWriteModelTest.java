package com.ouroboros.workflow.approval.activiti6.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.Process;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.TaskService;
import org.activiti.engine.task.IdentityLink;
import org.activiti.engine.task.Task;
import org.activiti.engine.task.TaskQuery;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import io.vavr.control.Try;

import com.ouroboros.workflow.approval.FlowFormService;
import com.ouroboros.workflow.approval.FlowTaskAction;
import com.ouroboros.workflow.approval.FlowTaskService;
import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.activiti6.util.BpmnUtil;
import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.definition.nodes.SingleApprovalNode;

class Activiti6FlowTaskServiceWriteModelTest {
  @Test
  void doActionMarksTransactionRollbackOnlyWhenTaskFormSubmissionFailsAfterClaim() {
    TaskService taskService = mock(TaskService.class);
    RepositoryService repositoryService = buildRepositoryService("flow-a", singleApprovalFlowJson());
    FlowFormService flowFormService = mock(FlowFormService.class);
    RecordingTransactionManager transactionManager = new RecordingTransactionManager();
    RuntimeException failure = new RuntimeException("submit failed");

    @SuppressWarnings("unchecked")
    FlowTaskAction<Task> action = mock(FlowTaskAction.class);
    Activiti6FlowTaskService target = new Activiti6FlowTaskService(taskService, Collections.singletonList(action));
    target.repositoryService = repositoryService;
    target.flowFormServices = Collections.singletonList(flowFormService);
    FlowTaskService service = transactionalProxy(target, transactionManager);

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    IdentityLink candidate = mock(IdentityLink.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.singletonList(candidate));
    when(candidate.getUserId()).thenReturn("user-a");
    when(task.getId()).thenReturn("task-1");
    when(task.getAssignee()).thenReturn(null);
    when(task.getFormKey()).thenReturn("purchase-form");
    when(task.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(task.getTaskDefinitionKey()).thenReturn("approve-a");
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    when(taskService.getVariable("task-1", Constants.TASK_FORM_ID_KEY, String.class)).thenReturn(null);
    when(flowFormService.getType()).thenReturn("default");
    when(flowFormService.submitTaskForm(eq("instance-1"), eq("task-1"), eq("purchase-form"), eq(null), eq("user-a"), anyList(), eq(Collections.singletonMap("amount", 99))))
        .thenReturn(Try.failure(failure));
    when(action.supportTask(task)).thenReturn(true);
    when(action.actionName()).thenReturn("approve");

    var result = service.doAction("task-1", "approve", "user-a", null, Collections.singletonMap("amount", 99), null);

    assertTrue(result.isFailure());
    assertEquals("submit failed", result.getCause().getMessage());
    assertEquals(0, transactionManager.getCommitCount());
    assertEquals(1, transactionManager.getRollbackCount());
    verify(taskService).claim("task-1", "user-a");
    verify(taskService, never()).setVariableLocal(eq("task-1"), eq(Constants.TASK_FORM_DATA_KEY), eq(Collections.singletonMap("amount", 99)));
    verify(action, never()).doAction(task);
  }

  @Test
  void doActionDoesNotClaimCandidateTaskWhenMatchingActionDoesNotExist() {
    TaskService taskService = mock(TaskService.class);
    @SuppressWarnings("unchecked")
    FlowTaskAction<Task> action = mock(FlowTaskAction.class);
    Activiti6FlowTaskService service = new Activiti6FlowTaskService(taskService, Collections.singletonList(action));

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    IdentityLink candidate = mock(IdentityLink.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.singletonList(candidate));
    when(candidate.getUserId()).thenReturn("user-a");
    when(task.getAssignee()).thenReturn(null);
    when(action.supportTask(task)).thenReturn(false);
    when(action.actionName()).thenReturn("approve");

    var result = service.doAction("task-1", "approve", "user-a", null, null, null);

    assertTrue(result.isFailure());
    assertEquals("未找到对应的流程动作: approve", result.getCause().getMessage());
    verify(taskService, never()).claim("task-1", "user-a");
    verify(action, never()).doAction(task);
  }

  @Test
  void doActionMarksTransactionRollbackOnlyWhenEngineActionReturnsFailure() {
    TaskService taskService = mock(TaskService.class);
    RecordingTransactionManager transactionManager = new RecordingTransactionManager();
    RuntimeException failure = new RuntimeException("engine action failed");

    @SuppressWarnings("unchecked")
    FlowTaskAction<Task> action = mock(FlowTaskAction.class);
    Activiti6FlowTaskService target = new Activiti6FlowTaskService(taskService, Collections.singletonList(action));
    FlowTaskService service = transactionalProxy(target, transactionManager);

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    IdentityLink candidate = mock(IdentityLink.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.singletonList(candidate));
    when(candidate.getUserId()).thenReturn("user-a");
    when(task.getId()).thenReturn("task-1");
    when(task.getAssignee()).thenReturn(null);
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    when(action.supportTask(task)).thenReturn(true);
    when(action.actionName()).thenReturn("approve");
    when(action.doAction(task)).thenReturn(Try.failure(failure));

    var result = service.doAction("task-1", "approve", "user-a", "looks good", null, null);

    assertTrue(result.isFailure());
    assertEquals("engine action failed", result.getCause().getMessage());
    assertEquals(0, transactionManager.getCommitCount());
    assertEquals(1, transactionManager.getRollbackCount());
    verify(taskService).claim("task-1", "user-a");
    verify(taskService).addComment("task-1", "instance-1", "looks good");
    verify(action).doAction(task);
  }

  @Test
  void doActionSubmitsTaskFormThroughDefaultServiceWhenTaskFormTypeBlank() {
    TaskService taskService = mock(TaskService.class);
    RepositoryService repositoryService = buildRepositoryService("flow-a", singleApprovalFlowJsonWithoutFormType());
    FlowFormService flowFormService = mock(FlowFormService.class);

    @SuppressWarnings("unchecked")
    FlowTaskAction<Task> action = mock(FlowTaskAction.class);
    Activiti6FlowTaskService service = new Activiti6FlowTaskService(taskService, Collections.singletonList(action));
    service.repositoryService = repositoryService;
    service.flowFormServices = Collections.singletonList(flowFormService);

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    IdentityLink candidate = mock(IdentityLink.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.singletonList(candidate));
    when(candidate.getUserId()).thenReturn("user-a");
    when(task.getId()).thenReturn("task-1");
    when(task.getAssignee()).thenReturn(null);
    when(task.getFormKey()).thenReturn("purchase-form");
    when(task.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(task.getTaskDefinitionKey()).thenReturn("approve-a");
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    when(taskService.getVariable("task-1", Constants.TASK_FORM_TYPE_KEY, String.class)).thenReturn("");
    when(taskService.getVariable("task-1", Constants.TASK_FORM_ID_KEY, String.class)).thenReturn("form-1");
    when(flowFormService.getType()).thenReturn("default");
    when(flowFormService.submitTaskForm(eq("instance-1"), eq("task-1"), eq("purchase-form"), eq("form-1"), eq("user-a"), anyList(), eq(Collections.singletonMap("amount", 99))))
        .thenReturn(Try.success(null));
    when(action.supportTask(task)).thenReturn(true);
    when(action.actionName()).thenReturn("approve");
    when(action.doAction(task)).thenReturn(Try.success(null));

    var result = service.doAction("task-1", "approve", "user-a", null, Collections.singletonMap("amount", 99), null);

    assertTrue(result.isSuccess());
    verify(flowFormService).submitTaskForm(eq("instance-1"), eq("task-1"), eq("purchase-form"), eq("form-1"), eq("user-a"), anyList(), eq(Collections.singletonMap("amount", 99)));
    verify(taskService).setVariableLocal("task-1", Constants.TASK_FORM_DATA_KEY, Collections.singletonMap("amount", 99));
    verify(action).doAction(task);
  }

  @Test
  void doActionFailsWhenNoMatchingFormServiceExists() {
    TaskService taskService = mock(TaskService.class);
    RepositoryService repositoryService = buildRepositoryService("flow-a", singleApprovalFlowJson());
    RecordingTransactionManager transactionManager = new RecordingTransactionManager();

    @SuppressWarnings("unchecked")
    FlowTaskAction<Task> action = mock(FlowTaskAction.class);
    Activiti6FlowTaskService target = new Activiti6FlowTaskService(taskService, Collections.singletonList(action));
    target.repositoryService = repositoryService;
    target.flowFormServices = Collections.emptyList();
    FlowTaskService service = transactionalProxy(target, transactionManager);

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    IdentityLink candidate = mock(IdentityLink.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.singletonList(candidate));
    when(candidate.getUserId()).thenReturn("user-a");
    when(task.getId()).thenReturn("task-1");
    when(task.getAssignee()).thenReturn(null);
    when(task.getFormKey()).thenReturn("purchase-form");
    when(task.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(task.getTaskDefinitionKey()).thenReturn("approve-a");
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    when(taskService.getVariable("task-1", Constants.TASK_FORM_TYPE_KEY, String.class)).thenReturn("default");
    when(taskService.getVariable("task-1", Constants.TASK_FORM_ID_KEY, String.class)).thenReturn("form-1");
    when(action.supportTask(task)).thenReturn(true);
    when(action.actionName()).thenReturn("approve");

    var result = service.doAction("task-1", "approve", "user-a", null, Collections.singletonMap("amount", 99), null);

    assertTrue(result.isFailure());
    assertEquals("未找到对应的表单服务", result.getCause().getMessage());
    assertEquals(0, transactionManager.getCommitCount());
    assertEquals(1, transactionManager.getRollbackCount());
    verify(taskService).claim("task-1", "user-a");
    verify(taskService, never()).setVariableLocal(eq("task-1"), eq(Constants.TASK_FORM_DATA_KEY), eq(Collections.singletonMap("amount", 99)));
    verify(action, never()).doAction(task);
  }

  @Test
  void doActionSubmitsTaskFormWhenBpmnModelUnavailable() {
    TaskService taskService = mock(TaskService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);
    FlowFormService flowFormService = mock(FlowFormService.class);

    @SuppressWarnings("unchecked")
    FlowTaskAction<Task> action = mock(FlowTaskAction.class);
    Activiti6FlowTaskService service = new Activiti6FlowTaskService(taskService, Collections.singletonList(action));
    service.repositoryService = repositoryService;
    service.flowDefinitionService = flowDefinitionService;
    service.flowFormServices = Collections.singletonList(flowFormService);

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    IdentityLink candidate = mock(IdentityLink.class);
    org.activiti.engine.repository.ProcessDefinition processDefinition = mock(org.activiti.engine.repository.ProcessDefinition.class);
    SingleApprovalNode approvalNode = new SingleApprovalNode();
    approvalNode.setId("approve-a");
    approvalNode.setPermission(Collections.emptyList());

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(taskService.getIdentityLinksForTask("task-1")).thenReturn(Collections.singletonList(candidate));
    when(candidate.getUserId()).thenReturn("user-a");
    when(task.getId()).thenReturn("task-1");
    when(task.getAssignee()).thenReturn(null);
    when(task.getFormKey()).thenReturn("purchase-form");
    when(task.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(task.getTaskDefinitionKey()).thenReturn("approve-a");
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    when(taskService.getVariable("task-1", Constants.TASK_FORM_TYPE_KEY, String.class)).thenReturn("default");
    when(taskService.getVariable("task-1", Constants.TASK_FORM_ID_KEY, String.class)).thenReturn("form-1");
    when(repositoryService.getBpmnModel("process-definition-1")).thenReturn(null);
    when(repositoryService.getProcessDefinition("process-definition-1")).thenReturn(processDefinition);
    when(processDefinition.getKey()).thenReturn("flow-a");
    when(flowDefinitionService.getFlowNodes("flow-a")).thenReturn(Collections.singletonList(approvalNode));
    when(flowFormService.getType()).thenReturn("default");
    when(flowFormService.submitTaskForm("instance-1", "task-1", "purchase-form", "form-1", "user-a",
        Collections.emptyList(), Collections.singletonMap("amount", 99))).thenReturn(Try.success(null));
    when(action.supportTask(task)).thenReturn(true);
    when(action.actionName()).thenReturn("approve");
    when(action.doAction(task)).thenReturn(Try.success(null));

    var result = assertDoesNotThrow(() ->
        service.doAction("task-1", "approve", "user-a", null, Collections.singletonMap("amount", 99), null));

    assertTrue(result.isSuccess());
    verify(flowFormService).submitTaskForm("instance-1", "task-1", "purchase-form", "form-1", "user-a",
        Collections.emptyList(), Collections.singletonMap("amount", 99));
    verify(taskService).setVariableLocal("task-1", Constants.TASK_FORM_DATA_KEY, Collections.singletonMap("amount", 99));
    verify(action).doAction(task);
  }

  private RepositoryService buildRepositoryService(String flowKey, String rawJson) {
    RepositoryService repositoryService = mock(RepositoryService.class);
    org.activiti.engine.repository.ProcessDefinition processDefinition = mock(org.activiti.engine.repository.ProcessDefinition.class);
    when(repositoryService.getProcessDefinition("process-definition-1")).thenReturn(processDefinition);
    when(processDefinition.getKey()).thenReturn(flowKey);
    when(repositoryService.getBpmnModel("process-definition-1")).thenReturn(buildBpmnModel(rawJson));
    return repositoryService;
  }

  private BpmnModel buildBpmnModel(String rawJson) {
    BpmnModel model = new BpmnModel();
    Process process = new Process();
    model.addProcess(process);
    BpmnUtil.saveFlowDefinitionRawJson(process, rawJson);
    return model;
  }

  private String singleApprovalFlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-a",
          "name": "采购审批",
          "category": "",
          "icon": "shenpi",
          "description": "",
          "formType": "default",
          "formKey": "purchase-form",
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "start-a", "permission": [] },
            {
              "type": "审批节点",
              "name": "经理审批",
              "id": "approve-a",
              "subType": "单签",
              "candidates": [],
              "permission": [],
              "actions": ["reject"]
            }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "approve-a" }
          ]
        }
        """;
  }

  private String singleApprovalFlowJsonWithoutFormType() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-a",
          "name": "采购审批",
          "category": "",
          "icon": "shenpi",
          "description": "",
          "formKey": "purchase-form",
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "start-a", "permission": [] },
            {
              "type": "审批节点",
              "name": "经理审批",
              "id": "approve-a",
              "subType": "单签",
              "candidates": [],
              "permission": [],
              "actions": ["reject"]
            }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "approve-a" }
          ]
        }
        """;
  }

  private FlowTaskService transactionalProxy(Activiti6FlowTaskService target, RecordingTransactionManager transactionManager) {
    ProxyFactory proxyFactory = new ProxyFactory(target);
    proxyFactory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
    return (FlowTaskService) proxyFactory.getProxy();
  }

  private static class RecordingTransactionManager implements PlatformTransactionManager {
    private int commitCount;
    private int rollbackCount;

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
      return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
      if (status.isRollbackOnly()) {
        rollbackCount++;
        return;
      }
      commitCount++;
    }

    @Override
    public void rollback(TransactionStatus status) {
      rollbackCount++;
    }

    int getCommitCount() {
      return commitCount;
    }

    int getRollbackCount() {
      return rollbackCount;
    }
  }
}
