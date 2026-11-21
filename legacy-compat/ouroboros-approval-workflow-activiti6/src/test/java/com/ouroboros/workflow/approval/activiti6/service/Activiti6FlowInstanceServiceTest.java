package com.ouroboros.workflow.approval.activiti6.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.Process;
import org.activiti.engine.FormService;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricProcessInstanceQuery;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.history.HistoricTaskInstanceQuery;
import org.activiti.engine.history.HistoricVariableInstance;
import org.activiti.engine.history.HistoricVariableInstanceQuery;
import org.activiti.engine.runtime.Execution;
import org.activiti.engine.runtime.ExecutionQuery;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.runtime.ProcessInstanceQuery;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.repository.ProcessDefinitionQuery;
import org.activiti.engine.task.Comment;
import org.activiti.engine.task.Task;
import org.activiti.engine.task.TaskQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import io.vavr.control.Try;

import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.workflow.approval.FlowFormService;
import com.ouroboros.workflow.approval.FlowInstanceService;
import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.activiti6.util.BpmnUtil;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.listener.FlowInstanceListener;
import com.ouroboros.workflow.approval.model.FlowDurationStatistic;
import com.ouroboros.workflow.approval.model.FlowInstanceDetail;
import com.ouroboros.workflow.approval.model.FlowInstanceStatistic;
import com.ouroboros.workflow.approval.model.FlowTimeline;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.definition.NodeFormFieldPermission;

class Activiti6FlowInstanceServiceTest {
  @Test
  void startHandlesNullInitTaskDeferredAndInitializesFirstTaskImmediately() {
    RuntimeService runtimeService = mock(RuntimeService.class);
    RepositoryService repositoryService = buildRepositoryService("flow-a", singleApprovalFlowJson());
    TaskService taskService = mock(TaskService.class);
    FormService formService = mock(FormService.class);
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);

    Activiti6FlowInstanceService service = new Activiti6FlowInstanceService();
    service.runtimeService = runtimeService;
    service.repositoryService = repositoryService;
    service.taskService = taskService;
    service.formService = formService;
    service.flowDefinitionService = flowDefinitionService;
    service.flowFormServices = Collections.emptyList();

    ProcessInstance processInstance = mock(ProcessInstance.class);
    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);

    when(flowDefinitionService.getUserSelectCandidateApprovalNodes("flow-a")).thenReturn(Collections.emptyList());
    when(runtimeService.startProcessInstanceByKey(eq("flow-a"), any(Map.class))).thenReturn(processInstance);
    when(processInstance.getId()).thenReturn("instance-1");
    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.processInstanceId("instance-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(task.getId()).thenReturn("task-1");

    Try<?> result = service.start("flow-a", "starter-1", Collections.emptyMap(), Collections.emptyMap(), null, null);

    assertTrue(result.isSuccess());
    assertEquals("instance-1", result.get());
    verify(taskService).setAssignee("task-1", "starter-1");
    verify(taskService).setVariableLocal("task-1", Constants.Variable.TASK_APPROVED, true);
    verify(formService).submitTaskFormData("task-1", Collections.emptyMap());
  }

  @Test
  void startMarksTransactionRollbackOnlyWhenInitialFormSubmissionFails() {
    RuntimeService runtimeService = mock(RuntimeService.class);
    RepositoryService repositoryService = buildRepositoryService("flow-a", singleApprovalFlowJson());
    TaskService taskService = mock(TaskService.class);
    FormService formService = mock(FormService.class);
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);
    FlowFormService flowFormService = mock(FlowFormService.class);
    RecordingTransactionManager transactionManager = new RecordingTransactionManager();

    Activiti6FlowInstanceService target = new Activiti6FlowInstanceService();
    target.runtimeService = runtimeService;
    target.repositoryService = repositoryService;
    target.taskService = taskService;
    target.formService = formService;
    target.flowDefinitionService = flowDefinitionService;
    target.flowFormServices = Collections.singletonList(flowFormService);
    FlowInstanceService service = transactionalProxy(target, transactionManager);

    ProcessInstance processInstance = mock(ProcessInstance.class);
    RuntimeException failure = new RuntimeException("submit failed");

    when(flowDefinitionService.getUserSelectCandidateApprovalNodes("flow-a")).thenReturn(Collections.emptyList());
    when(flowFormService.getType()).thenReturn("default");
    when(runtimeService.startProcessInstanceByKey(eq("flow-a"), any(Map.class))).thenReturn(processInstance);
    when(processInstance.getProcessInstanceId()).thenReturn("instance-1");
    when(flowFormService.submitInitialForm(eq("instance-1"), eq("purchase-form"), eq("starter-1"), anyList(), eq(Collections.emptyMap())))
        .thenReturn(Try.failure(failure));

    Try<?> result = service.start("flow-a", "starter-1", Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
        null);

    assertTrue(result.isFailure());
    assertEquals("submit failed", result.getCause().getMessage());
    assertEquals(0, transactionManager.getCommitCount());
    assertEquals(1, transactionManager.getRollbackCount());
    verify(runtimeService, never()).setVariable(anyString(), eq(Constants.TASK_FORM_ID_KEY), any());
  }

  @Test
  void startDefaultsBlankFormTypeToDefaultWhenSubmittingInitialForm() {
    RuntimeService runtimeService = mock(RuntimeService.class);
    RepositoryService repositoryService = buildRepositoryService("flow-a", singleApprovalFlowJsonWithoutFormType());
    TaskService taskService = mock(TaskService.class);
    FormService formService = mock(FormService.class);
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);
    FlowFormService flowFormService = mock(FlowFormService.class);

    Activiti6FlowInstanceService service = new Activiti6FlowInstanceService();
    service.runtimeService = runtimeService;
    service.repositoryService = repositoryService;
    service.taskService = taskService;
    service.formService = formService;
    service.flowDefinitionService = flowDefinitionService;
    service.flowFormServices = Collections.singletonList(flowFormService);

    ProcessInstance processInstance = mock(ProcessInstance.class);
    when(flowDefinitionService.getUserSelectCandidateApprovalNodes("flow-a")).thenReturn(Collections.emptyList());
    when(flowFormService.getType()).thenReturn("default");
    when(runtimeService.startProcessInstanceByKey(eq("flow-a"), any(Map.class))).thenReturn(processInstance);
    when(processInstance.getId()).thenReturn("instance-1");
    when(processInstance.getProcessInstanceId()).thenReturn("instance-1");
    when(flowFormService.submitInitialForm(eq("instance-1"), eq("purchase-form"), eq("starter-1"), anyList(), eq(Collections.emptyMap())))
        .thenReturn(Try.success("form-1"));

    Try<?> result = service.start("flow-a", "starter-1", Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
        new CompletableFuture<Void>());

    assertTrue(result.isSuccess());
    assertEquals("instance-1", result.get());
    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService).startProcessInstanceByKey(eq("flow-a"), variablesCaptor.capture());
    assertEquals("default", variablesCaptor.getValue().get(Constants.TASK_FORM_TYPE_KEY));
    verify(flowFormService).submitInitialForm(eq("instance-1"), eq("purchase-form"), eq("starter-1"), anyList(), eq(Collections.emptyMap()));
    verify(runtimeService).setVariable("instance-1", Constants.TASK_FORM_ID_KEY, "form-1");
  }

  @Test
  void startFallsBackToDefaultFormTypeWhenRawFlowDefinitionMissing() {
    RuntimeService runtimeService = mock(RuntimeService.class);
    RepositoryService repositoryService = buildRepositoryServiceWithoutRawDefinition("flow-a");
    TaskService taskService = mock(TaskService.class);
    FormService formService = mock(FormService.class);
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);

    Activiti6FlowInstanceService service = new Activiti6FlowInstanceService();
    service.runtimeService = runtimeService;
    service.repositoryService = repositoryService;
    service.taskService = taskService;
    service.formService = formService;
    service.flowDefinitionService = flowDefinitionService;
    service.flowFormServices = Collections.emptyList();

    ProcessInstance processInstance = mock(ProcessInstance.class);
    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);

    when(flowDefinitionService.getUserSelectCandidateApprovalNodes("flow-a")).thenReturn(Collections.emptyList());
    when(runtimeService.startProcessInstanceByKey(eq("flow-a"), any(Map.class))).thenReturn(processInstance);
    when(processInstance.getId()).thenReturn("instance-1");
    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.processInstanceId("instance-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(task.getId()).thenReturn("task-1");

    Try<?> result = service.start("flow-a", "starter-1", Collections.emptyMap(), Collections.emptyMap(), null, null);

    assertTrue(result.isSuccess());
    assertEquals("instance-1", result.get());
    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService).startProcessInstanceByKey(eq("flow-a"), variablesCaptor.capture());
    assertEquals("default", variablesCaptor.getValue().get(Constants.TASK_FORM_TYPE_KEY));
    verify(taskService).setAssignee("task-1", "starter-1");
    verify(taskService).setVariableLocal("task-1", Constants.Variable.TASK_APPROVED, true);
    verify(formService).submitTaskFormData("task-1", Collections.emptyMap());
    verify(runtimeService, never()).setVariable(anyString(), eq(Constants.TASK_FORM_ID_KEY), any());
  }

  @Test
  void getInitialFormMatchesFlowFormServiceIgnoringCase() {
    RepositoryService repositoryService = buildRepositoryService("flow-a", singleApprovalFlowJson());
    FlowFormService flowFormService = mock(FlowFormService.class);
    SchemaNode schemaNode = new SchemaNode();
    schemaNode.setType("form");

    Activiti6FlowInstanceService service = new Activiti6FlowInstanceService();
    service.repositoryService = repositoryService;
    service.flowFormServices = Collections.singletonList(flowFormService);

    when(flowFormService.getType()).thenReturn("DEFAULT");
    when(flowFormService.getInitialFormSchema(eq("purchase-form"), anyList())).thenReturn(Try.success(schemaNode));

    Try<?> result = service.getInitialForm("flow-a");

    assertTrue(result.isSuccess());
    assertEquals(schemaNode, result.get());
  }

  @Test
  void getInitialFormUsesEmptyPermissionsWhenStartNodePermissionMissing() {
    RepositoryService repositoryService = buildRepositoryService("flow-a", flowJsonWithoutStartPermission());
    FlowFormService flowFormService = mock(FlowFormService.class);
    SchemaNode schemaNode = new SchemaNode();
    schemaNode.setType("form");

    Activiti6FlowInstanceService service = new Activiti6FlowInstanceService();
    service.repositoryService = repositoryService;
    service.flowFormServices = Collections.singletonList(flowFormService);

    when(flowFormService.getType()).thenReturn("default");
    when(flowFormService.getInitialFormSchema(eq("purchase-form"), anyList())).thenReturn(Try.success(schemaNode));

    Try<?> result = service.getInitialForm("flow-a");

    assertTrue(result.isSuccess());
    assertEquals(schemaNode, result.get());
    ArgumentCaptor<List> permissionsCaptor = ArgumentCaptor.forClass(List.class);
    verify(flowFormService).getInitialFormSchema(eq("purchase-form"), permissionsCaptor.capture());
    assertTrue(permissionsCaptor.getValue().isEmpty());
  }

  @Test
  void getInitialFormUsesStartNodePermissionsEvenWhenStartNodeIsNotFirst() {
    RepositoryService repositoryService = buildRepositoryService("flow-a", flowJsonWithStartNodeNotFirst());
    FlowFormService flowFormService = mock(FlowFormService.class);
    SchemaNode schemaNode = new SchemaNode();
    schemaNode.setType("form");

    Activiti6FlowInstanceService service = new Activiti6FlowInstanceService();
    service.repositoryService = repositoryService;
    service.flowFormServices = Collections.singletonList(flowFormService);

    when(flowFormService.getType()).thenReturn("default");
    when(flowFormService.getInitialFormSchema(eq("purchase-form"), anyList())).thenReturn(Try.success(schemaNode));

    Try<?> result = service.getInitialForm("flow-a");

    assertTrue(result.isSuccess());
    ArgumentCaptor<List> permissionsCaptor = ArgumentCaptor.forClass(List.class);
    verify(flowFormService).getInitialFormSchema(eq("purchase-form"), permissionsCaptor.capture());
    assertEquals(Collections.singletonList("start-field"), permissionNames(permissionsCaptor.getValue()));
  }

  @Test
  void getInitialFormIgnoresStartNodePermissionsForBusinessModelForm() {
    RepositoryService repositoryService = buildRepositoryService("flow-a", businessModelFlowJsonWithStartPermission());
    FlowFormService flowFormService = mock(FlowFormService.class);
    SchemaNode schemaNode = new SchemaNode();
    schemaNode.setType("form");

    Activiti6FlowInstanceService service = new Activiti6FlowInstanceService();
    service.repositoryService = repositoryService;
    service.flowFormServices = Collections.singletonList(flowFormService);

    when(flowFormService.getType()).thenReturn("business-model");
    when(flowFormService.getInitialFormSchema(eq("purchase-form"), anyList())).thenReturn(Try.success(schemaNode));

    Try<?> result = service.getInitialForm("flow-a");

    assertTrue(result.isSuccess());
    ArgumentCaptor<List> permissionsCaptor = ArgumentCaptor.forClass(List.class);
    verify(flowFormService).getInitialFormSchema(eq("purchase-form"), permissionsCaptor.capture());
    assertTrue(permissionsCaptor.getValue().isEmpty());
  }

  @Test
  void startUsesStartNodePermissionsEvenWhenStartNodeIsNotFirst() {
    RuntimeService runtimeService = mock(RuntimeService.class);
    RepositoryService repositoryService = buildRepositoryService("flow-a", flowJsonWithStartNodeNotFirst());
    TaskService taskService = mock(TaskService.class);
    FormService formService = mock(FormService.class);
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);
    FlowFormService flowFormService = mock(FlowFormService.class);

    Activiti6FlowInstanceService service = new Activiti6FlowInstanceService();
    service.runtimeService = runtimeService;
    service.repositoryService = repositoryService;
    service.taskService = taskService;
    service.formService = formService;
    service.flowDefinitionService = flowDefinitionService;
    service.flowFormServices = Collections.singletonList(flowFormService);

    ProcessInstance processInstance = mock(ProcessInstance.class);
    when(flowDefinitionService.getUserSelectCandidateApprovalNodes("flow-a")).thenReturn(Collections.emptyList());
    when(flowFormService.getType()).thenReturn("default");
    when(runtimeService.startProcessInstanceByKey(eq("flow-a"), any(Map.class))).thenReturn(processInstance);
    when(processInstance.getId()).thenReturn("instance-1");
    when(processInstance.getProcessInstanceId()).thenReturn("instance-1");
    when(flowFormService.submitInitialForm(eq("instance-1"), eq("purchase-form"), eq("starter-1"), anyList(), eq(Collections.emptyMap())))
        .thenReturn(Try.success("form-1"));

    Try<?> result = service.start("flow-a", "starter-1", Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
        new CompletableFuture<Void>());

    assertTrue(result.isSuccess());
    ArgumentCaptor<List> permissionsCaptor = ArgumentCaptor.forClass(List.class);
    verify(flowFormService).submitInitialForm(eq("instance-1"), eq("purchase-form"), eq("starter-1"), permissionsCaptor.capture(),
        eq(Collections.emptyMap()));
    assertEquals(Collections.singletonList("start-field"), permissionNames(permissionsCaptor.getValue()));
  }

  @Test
  void pauseStoresVariablesSuspendsInstanceAndDispatchesSuspendedEvent() {
    RuntimeService runtimeService = mock(RuntimeService.class);
    Activiti6FlowInstanceService service = new Activiti6FlowInstanceService();
    service.runtimeService = runtimeService;
    CollectingFlowInstanceListener listener = installInstanceEventCollector(service);

    ProcessInstanceQuery processInstanceQuery = mock(ProcessInstanceQuery.class);
    ProcessInstance processInstance = mock(ProcessInstance.class);
    when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
    when(processInstanceQuery.processInstanceId("instance-1")).thenReturn(processInstanceQuery);
    when(processInstanceQuery.singleResult()).thenReturn(processInstance);
    when(processInstance.isSuspended()).thenReturn(false);
    when(processInstance.isEnded()).thenReturn(false);
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_FLOW_KEY, String.class)).thenReturn("flow-a");
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_STARTER, String.class)).thenReturn("starter-1");
    mockActiveExecution(runtimeService, "instance-1", "execution-1");

    Try<?> result = service.pause("instance-1", "user-a", "维护窗口");

    assertTrue(result.isSuccess());
    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService).setVariables(eq("execution-1"), variablesCaptor.capture());
    Map<String, Object> variables = variablesCaptor.getValue();
    assertEquals("user-a", variables.get(Constants.PROCESS_PAUSE_USER_VARIABLE_NAME));
    assertEquals("维护窗口", variables.get(Constants.PROCESS_PAUSE_REASON_VARIABLE_NAME));
    assertTrue(variables.get(Constants.PROCESS_PAUSE_TIME_VARIABLE_NAME) instanceof LocalDateTime);
    verify(runtimeService).suspendProcessInstanceById("instance-1");
    assertEquals(1, listener.getEvents().size());
    assertEquals(FlowInstanceEvent.EventType.SUSPENDED, listener.getEvents().get(0).getEventType());
    assertEquals("flow-a", listener.getEvents().get(0).getFlowKey());
  }

  @Test
  void pauseMarksTransactionRollbackOnlyWhenInstanceEventDispatchFails() {
    RuntimeService runtimeService = mock(RuntimeService.class);
    RecordingTransactionManager transactionManager = new RecordingTransactionManager();
    Activiti6FlowInstanceService target = new Activiti6FlowInstanceService();
    target.runtimeService = runtimeService;
    installInstanceEventListener(target, new ThrowingFlowInstanceListener("dispatch failed"));
    FlowInstanceService service = transactionalProxy(target, transactionManager);

    ProcessInstanceQuery processInstanceQuery = mock(ProcessInstanceQuery.class);
    ProcessInstance processInstance = mock(ProcessInstance.class);
    when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
    when(processInstanceQuery.processInstanceId("instance-1")).thenReturn(processInstanceQuery);
    when(processInstanceQuery.singleResult()).thenReturn(processInstance);
    when(processInstance.isSuspended()).thenReturn(false);
    when(processInstance.isEnded()).thenReturn(false);
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_FLOW_KEY, String.class)).thenReturn("flow-a");
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_STARTER, String.class)).thenReturn("starter-1");
    mockActiveExecution(runtimeService, "instance-1", "execution-1");

    Try<?> result = service.pause("instance-1", "user-a", "维护窗口");

    assertTrue(result.isFailure());
    assertEquals("dispatch failed", result.getCause().getMessage());
    assertEquals(0, transactionManager.getCommitCount());
    assertEquals(1, transactionManager.getRollbackCount());
    verify(runtimeService).suspendProcessInstanceById("instance-1");
  }

  @Test
  void resumeStoresVariablesActivatesInstanceAndDispatchesResumedEvent() {
    RuntimeService runtimeService = mock(RuntimeService.class);
    Activiti6FlowInstanceService service = new Activiti6FlowInstanceService();
    service.runtimeService = runtimeService;
    CollectingFlowInstanceListener listener = installInstanceEventCollector(service);

    ProcessInstanceQuery processInstanceQuery = mock(ProcessInstanceQuery.class);
    ProcessInstance processInstance = mock(ProcessInstance.class);
    when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
    when(processInstanceQuery.processInstanceId("instance-1")).thenReturn(processInstanceQuery);
    when(processInstanceQuery.singleResult()).thenReturn(processInstance);
    when(processInstance.isSuspended()).thenReturn(true);
    when(processInstance.isEnded()).thenReturn(false);
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_FLOW_KEY, String.class)).thenReturn("flow-a");
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_STARTER, String.class)).thenReturn("starter-1");
    mockActiveExecution(runtimeService, "instance-1", "execution-1");

    Try<?> result = service.resume("instance-1", "user-a");

    assertTrue(result.isSuccess());
    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService).setVariables(eq("execution-1"), variablesCaptor.capture());
    Map<String, Object> variables = variablesCaptor.getValue();
    assertEquals("user-a", variables.get(Constants.PROCESS_RESUME_USER_VARIABLE_NAME));
    assertTrue(variables.get(Constants.PROCESS_RESUME_TIME_VARIABLE_NAME) instanceof LocalDateTime);
    verify(runtimeService).activateProcessInstanceById("instance-1");
    assertEquals(1, listener.getEvents().size());
    assertEquals(FlowInstanceEvent.EventType.RESUMED, listener.getEvents().get(0).getEventType());
    assertEquals("flow-a", listener.getEvents().get(0).getFlowKey());
  }

  @Test
  void cancelDeletesRuntimeInstanceAndDispatchesCancelledEvent() {
    RuntimeService runtimeService = mock(RuntimeService.class);
    HistoryService historyService = mock(HistoryService.class);
    Activiti6FlowInstanceService service = new Activiti6FlowInstanceService();
    service.runtimeService = runtimeService;
    service.historyService = historyService;
    CollectingFlowInstanceListener listener = installInstanceEventCollector(service);

    ProcessInstanceQuery processInstanceQuery = mock(ProcessInstanceQuery.class);
    ProcessInstance processInstance = mock(ProcessInstance.class);
    HistoricTaskInstanceQuery historicTaskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricTaskInstance lastTask = mock(HistoricTaskInstance.class);
    when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
    when(processInstanceQuery.processInstanceId("instance-1")).thenReturn(processInstanceQuery);
    when(processInstanceQuery.singleResult()).thenReturn(processInstance);
    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.processInstanceId("instance-1")).thenReturn(historicTaskQuery);
    when(historicTaskQuery.finished()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.orderByHistoricTaskInstanceEndTime()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.desc()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.listPage(0, 1)).thenReturn(Collections.singletonList(lastTask));
    when(lastTask.getId()).thenReturn("task-last");
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_FLOW_KEY, String.class)).thenReturn("flow-a");
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_STARTER, String.class)).thenReturn("starter-1");
    mockActiveExecution(runtimeService, "instance-1", "execution-1");

    Try<?> result = service.cancel("instance-1", "user-a", "主动取消");

    assertTrue(result.isSuccess());
    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService).setVariables(eq("execution-1"), variablesCaptor.capture());
    Map<String, Object> variables = variablesCaptor.getValue();
    assertEquals("user-a", variables.get(Constants.PROCESS_CANCEL_USER_VARIABLE_NAME));
    assertEquals("主动取消", variables.get(Constants.PROCESS_CANCEL_REASON_VARIABLE_NAME));
    assertEquals(Boolean.FALSE, variables.get(Constants.Variable.PROCESS_APPROVED));
    assertEquals("task-last", variables.get(Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID));
    assertTrue(variables.get(Constants.PROCESS_CANCEL_TIME_VARIABLE_NAME) instanceof LocalDateTime);
    verify(runtimeService).deleteProcessInstance("instance-1", "主动取消");
    assertEquals(1, listener.getEvents().size());
    assertEquals(FlowInstanceEvent.EventType.CANCELLED, listener.getEvents().get(0).getEventType());
    assertEquals("starter-1", listener.getEvents().get(0).getStarter());
    assertEquals("task-last", listener.getEvents().get(0).getLastTaskId());
  }

  @Test
  void cancelPrefersRuntimeLastFinishedTaskIdOverHistoryFallback() {
    RuntimeService runtimeService = mock(RuntimeService.class);
    HistoryService historyService = mock(HistoryService.class);
    Activiti6FlowInstanceService service = new Activiti6FlowInstanceService();
    service.runtimeService = runtimeService;
    service.historyService = historyService;
    CollectingFlowInstanceListener listener = installInstanceEventCollector(service);

    ProcessInstanceQuery processInstanceQuery = mock(ProcessInstanceQuery.class);
    ProcessInstance processInstance = mock(ProcessInstance.class);
    HistoricTaskInstanceQuery historicTaskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricTaskInstance historyTask = mock(HistoricTaskInstance.class);
    when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
    when(processInstanceQuery.processInstanceId("instance-1")).thenReturn(processInstanceQuery);
    when(processInstanceQuery.singleResult()).thenReturn(processInstance);
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID, String.class)).thenReturn("task-runtime");
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_FLOW_KEY, String.class)).thenReturn("flow-a");
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_STARTER, String.class)).thenReturn("starter-1");
    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.processInstanceId("instance-1")).thenReturn(historicTaskQuery);
    when(historicTaskQuery.finished()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.orderByHistoricTaskInstanceEndTime()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.desc()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.listPage(0, 1)).thenReturn(Collections.singletonList(historyTask));
    when(historyTask.getId()).thenReturn("task-history");
    mockActiveExecution(runtimeService, "instance-1", "execution-1");

    Try<?> result = service.cancel("instance-1", "user-a", "主动取消");

    assertTrue(result.isSuccess());
    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService).setVariables(eq("execution-1"), variablesCaptor.capture());
    assertEquals("task-runtime", variablesCaptor.getValue().get(Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID));
    assertEquals("task-runtime", listener.getEvents().get(0).getLastTaskId());
  }

  @Test
  void completeApprovedStoresApprovedFlagAndDispatchesApprovedEvent() {
    RuntimeService runtimeService = mock(RuntimeService.class);
    HistoryService historyService = mock(HistoryService.class);
    Activiti6FlowInstanceService service = new Activiti6FlowInstanceService();
    service.runtimeService = runtimeService;
    service.historyService = historyService;
    CollectingFlowInstanceListener listener = installInstanceEventCollector(service);

    ProcessInstanceQuery processInstanceQuery = mock(ProcessInstanceQuery.class);
    ProcessInstance processInstance = mock(ProcessInstance.class);
    HistoricTaskInstanceQuery historicTaskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricTaskInstance lastTask = mock(HistoricTaskInstance.class);
    when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
    when(processInstanceQuery.processInstanceId("instance-1")).thenReturn(processInstanceQuery);
    when(processInstanceQuery.singleResult()).thenReturn(processInstance);
    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.processInstanceId("instance-1")).thenReturn(historicTaskQuery);
    when(historicTaskQuery.finished()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.orderByHistoricTaskInstanceEndTime()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.desc()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.listPage(0, 1)).thenReturn(Collections.singletonList(lastTask));
    when(lastTask.getId()).thenReturn("task-last");
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_FLOW_KEY, String.class)).thenReturn("flow-a");
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_STARTER, String.class)).thenReturn("starter-1");
    mockActiveExecution(runtimeService, "instance-1", "execution-1");

    Try<?> result = service.complete("instance-1", "user-a", true, "人工通过");

    assertTrue(result.isSuccess());
    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService).setVariables(eq("execution-1"), variablesCaptor.capture());
    Map<String, Object> variables = variablesCaptor.getValue();
    assertEquals(Boolean.TRUE, variables.get(Constants.Variable.PROCESS_APPROVED));
    assertEquals("user-a", variables.get(Constants.PROCESS_CANCEL_USER_VARIABLE_NAME));
    assertEquals("人工通过", variables.get(Constants.PROCESS_CANCEL_REASON_VARIABLE_NAME));
    assertEquals("task-last", variables.get(Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID));
    assertTrue(variables.get(Constants.PROCESS_CANCEL_TIME_VARIABLE_NAME) instanceof LocalDateTime);
    verify(runtimeService).deleteProcessInstance("instance-1", "人工通过");
    assertEquals(1, listener.getEvents().size());
    assertEquals(FlowInstanceEvent.EventType.APPROVED, listener.getEvents().get(0).getEventType());
    assertEquals("task-last", listener.getEvents().get(0).getLastTaskId());
  }

  @Test
  void completeRejectedDispatchesRejectedEvent() {
    RuntimeService runtimeService = mock(RuntimeService.class);
    HistoryService historyService = mock(HistoryService.class);
    Activiti6FlowInstanceService service = new Activiti6FlowInstanceService();
    service.runtimeService = runtimeService;
    service.historyService = historyService;
    CollectingFlowInstanceListener listener = installInstanceEventCollector(service);

    ProcessInstanceQuery processInstanceQuery = mock(ProcessInstanceQuery.class);
    ProcessInstance processInstance = mock(ProcessInstance.class);
    HistoricTaskInstanceQuery historicTaskQuery = mock(HistoricTaskInstanceQuery.class);
    when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
    when(processInstanceQuery.processInstanceId("instance-1")).thenReturn(processInstanceQuery);
    when(processInstanceQuery.singleResult()).thenReturn(processInstance);
    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.processInstanceId("instance-1")).thenReturn(historicTaskQuery);
    when(historicTaskQuery.finished()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.orderByHistoricTaskInstanceEndTime()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.desc()).thenReturn(historicTaskQuery);
    when(historicTaskQuery.listPage(0, 1)).thenReturn(Collections.emptyList());
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_FLOW_KEY, String.class)).thenReturn("flow-a");
    when(runtimeService.getVariable("instance-1", Constants.Variable.PROCESS_STARTER, String.class)).thenReturn("starter-1");
    mockActiveExecution(runtimeService, "instance-1", "execution-1");

    Try<?> result = service.complete("instance-1", "user-a", false, "人工拒绝");

    assertTrue(result.isSuccess());
    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService).setVariables(eq("execution-1"), variablesCaptor.capture());
    assertEquals(Boolean.FALSE, variablesCaptor.getValue().get(Constants.Variable.PROCESS_APPROVED));
    verify(runtimeService).deleteProcessInstance("instance-1", "人工拒绝");
    assertEquals(1, listener.getEvents().size());
    assertEquals(FlowInstanceEvent.EventType.REJECTED, listener.getEvents().get(0).getEventType());
  }

  @Test
  void getTimeLineReturnsEmptyWhenRawFlowDefinitionMissing() {
    TaskService taskService = mock(TaskService.class);
    RepositoryService repositoryService = buildRepositoryServiceWithoutRawDefinition("flow-a");
    HistoryService historyService = mock(HistoryService.class);

    Activiti6FlowInstanceService service = new Activiti6FlowInstanceService();
    service.taskService = taskService;
    service.repositoryService = repositoryService;
    service.historyService = historyService;

    HistoricProcessInstanceQuery processQuery = mock(HistoricProcessInstanceQuery.class);
    HistoricProcessInstance historicProcessInstance = mock(HistoricProcessInstance.class);

    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processQuery);
    when(processQuery.processInstanceId("instance-1")).thenReturn(processQuery);
    when(processQuery.singleResult()).thenReturn(historicProcessInstance);
    when(historicProcessInstance.getProcessDefinitionId()).thenReturn("process-definition-1");

    List<FlowTimeline> timeline = service.getTimeLine("instance-1");

    assertTrue(timeline.isEmpty());
    verify(historyService, never()).createHistoricTaskInstanceQuery();
  }

  @Test
  void getTimeLineUsesSingleResultForTaskApprovedLookup() {
    TaskService taskService = mock(TaskService.class);
    RepositoryService repositoryService = buildRepositoryService("flow-a", singleApprovalFlowJson());
    HistoryService historyService = mock(HistoryService.class);

    Activiti6FlowInstanceService service = new Activiti6FlowInstanceService();
    service.taskService = taskService;
    service.repositoryService = repositoryService;
    service.historyService = historyService;

    HistoricProcessInstanceQuery processQuery = mock(HistoricProcessInstanceQuery.class);
    HistoricProcessInstance historicProcessInstance = mock(HistoricProcessInstance.class);
    HistoricTaskInstanceQuery taskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricTaskInstance historicTask = mock(HistoricTaskInstance.class);
    HistoricVariableInstanceQuery allVariablesQuery = mock(HistoricVariableInstanceQuery.class);
    HistoricVariableInstanceQuery taskApprovedQuery = mock(HistoricVariableInstanceQuery.class);
    HistoricVariableInstance taskApproved = mock(HistoricVariableInstance.class);

    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processQuery);
    when(processQuery.processInstanceId("instance-1")).thenReturn(processQuery);
    when(processQuery.singleResult()).thenReturn(historicProcessInstance);
    when(historicProcessInstance.getProcessDefinitionId()).thenReturn("process-definition-1");

    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskQuery);
    when(taskQuery.processInstanceId("instance-1")).thenReturn(taskQuery);
    when(taskQuery.orderByHistoricTaskInstanceStartTime()).thenReturn(taskQuery);
    when(taskQuery.asc()).thenReturn(taskQuery);
    when(taskQuery.list()).thenReturn(Collections.singletonList(historicTask));

    when(historicTask.getId()).thenReturn("task-1");
    when(historicTask.getName()).thenReturn("经理审批");
    when(historicTask.getTaskDefinitionKey()).thenReturn("approve-a");
    when(historicTask.getProcessInstanceId()).thenReturn("instance-1");
    when(historicTask.getAssignee()).thenReturn("approver-1");
    when(historicTask.getStartTime()).thenReturn(dateOf(2026, Month.MARCH, 18, 10, 0));
    when(historicTask.getEndTime()).thenReturn(dateOf(2026, Month.MARCH, 18, 10, 30));

    when(historyService.createHistoricVariableInstanceQuery()).thenReturn(allVariablesQuery, taskApprovedQuery);
    when(allVariablesQuery.processInstanceId("instance-1")).thenReturn(allVariablesQuery);
    when(allVariablesQuery.list()).thenReturn(Collections.emptyList());

    when(taskApprovedQuery.processInstanceId("instance-1")).thenReturn(taskApprovedQuery);
    when(taskApprovedQuery.taskId("task-1")).thenReturn(taskApprovedQuery);
    when(taskApprovedQuery.variableName(Constants.Variable.TASK_APPROVED)).thenReturn(taskApprovedQuery);
    when(taskApprovedQuery.list()).thenThrow(new AssertionError("task approved lookup should use singleResult()"));
    when(taskApprovedQuery.singleResult()).thenReturn(taskApproved);
    when(taskApproved.getValue()).thenReturn(Boolean.TRUE);

    when(taskService.getProcessInstanceComments("instance-1")).thenReturn(Collections.emptyList());

    List<FlowTimeline> timeline = service.getTimeLine("instance-1");
    FlowTimeline approvalTimeline = timeline.stream()
        .filter(item -> "approve-a".equals(item.getFlowNode().getId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("approve-a timeline not found"));

    assertEquals(TaskState.APPROVED, approvalTimeline.getState());
    assertEquals(TaskState.APPROVED, approvalTimeline.getTaskInstances().get(0).getState());
    verify(taskApprovedQuery).singleResult();
  }

  @Test
  void getTimeLineUsesLatestTaskCommentWhenMultipleCommentsExist() {
    TaskService taskService = mock(TaskService.class);
    RepositoryService repositoryService = buildRepositoryService("flow-a", singleApprovalFlowJson());
    HistoryService historyService = mock(HistoryService.class);

    Activiti6FlowInstanceService service = new Activiti6FlowInstanceService();
    service.taskService = taskService;
    service.repositoryService = repositoryService;
    service.historyService = historyService;

    HistoricProcessInstanceQuery processQuery = mock(HistoricProcessInstanceQuery.class);
    HistoricProcessInstance historicProcessInstance = mock(HistoricProcessInstance.class);
    HistoricTaskInstanceQuery taskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricTaskInstance historicTask = mock(HistoricTaskInstance.class);
    HistoricVariableInstanceQuery allVariablesQuery = mock(HistoricVariableInstanceQuery.class);
    HistoricVariableInstanceQuery taskApprovedQuery = mock(HistoricVariableInstanceQuery.class);
    HistoricVariableInstance taskApproved = mock(HistoricVariableInstance.class);
    Comment olderComment = mock(Comment.class);
    Comment newerComment = mock(Comment.class);

    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processQuery);
    when(processQuery.processInstanceId("instance-1")).thenReturn(processQuery);
    when(processQuery.singleResult()).thenReturn(historicProcessInstance);
    when(historicProcessInstance.getProcessDefinitionId()).thenReturn("process-definition-1");

    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskQuery);
    when(taskQuery.processInstanceId("instance-1")).thenReturn(taskQuery);
    when(taskQuery.orderByHistoricTaskInstanceStartTime()).thenReturn(taskQuery);
    when(taskQuery.asc()).thenReturn(taskQuery);
    when(taskQuery.list()).thenReturn(Collections.singletonList(historicTask));

    when(historicTask.getId()).thenReturn("task-1");
    when(historicTask.getName()).thenReturn("经理审批");
    when(historicTask.getTaskDefinitionKey()).thenReturn("approve-a");
    when(historicTask.getProcessInstanceId()).thenReturn("instance-1");
    when(historicTask.getAssignee()).thenReturn("approver-1");
    when(historicTask.getStartTime()).thenReturn(dateOf(2026, Month.MARCH, 18, 10, 0));
    when(historicTask.getEndTime()).thenReturn(dateOf(2026, Month.MARCH, 18, 10, 30));

    when(historyService.createHistoricVariableInstanceQuery()).thenReturn(allVariablesQuery, taskApprovedQuery);
    when(allVariablesQuery.processInstanceId("instance-1")).thenReturn(allVariablesQuery);
    when(allVariablesQuery.list()).thenReturn(Collections.emptyList());

    when(taskApprovedQuery.processInstanceId("instance-1")).thenReturn(taskApprovedQuery);
    when(taskApprovedQuery.taskId("task-1")).thenReturn(taskApprovedQuery);
    when(taskApprovedQuery.variableName(Constants.Variable.TASK_APPROVED)).thenReturn(taskApprovedQuery);
    when(taskApprovedQuery.singleResult()).thenReturn(taskApproved);
    when(taskApproved.getValue()).thenReturn(Boolean.FALSE);

    when(olderComment.getTaskId()).thenReturn("task-1");
    when(olderComment.getTime()).thenReturn(dateOf(2026, Month.MARCH, 18, 10, 31));
    when(olderComment.getFullMessage()).thenReturn("older-comment");
    when(newerComment.getTaskId()).thenReturn("task-1");
    when(newerComment.getTime()).thenReturn(dateOf(2026, Month.MARCH, 18, 10, 32));
    when(newerComment.getFullMessage()).thenReturn("newer-comment");
    when(taskService.getProcessInstanceComments("instance-1")).thenReturn(Arrays.asList(olderComment, newerComment));

    List<FlowTimeline> timeline = service.getTimeLine("instance-1");
    FlowTimeline approvalTimeline = timeline.stream()
        .filter(item -> "approve-a".equals(item.getFlowNode().getId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("approve-a timeline not found"));

    assertEquals("newer-comment", approvalTimeline.getTaskInstances().get(0).getComment());
  }

  @Test
  void getTimeLineUsesLoadedVariablesForMultipleApprovalSessionStatus() {
    TaskService taskService = mock(TaskService.class);
    RepositoryService repositoryService = buildRepositoryService("flow-multi-a", multipleApprovalFlowJson());
    HistoryService historyService = mock(HistoryService.class);

    Activiti6FlowInstanceService service = new Activiti6FlowInstanceService();
    service.taskService = taskService;
    service.repositoryService = repositoryService;
    service.historyService = historyService;

    HistoricProcessInstanceQuery processQuery = mock(HistoricProcessInstanceQuery.class);
    HistoricProcessInstance historicProcessInstance = mock(HistoricProcessInstance.class);
    HistoricTaskInstanceQuery taskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricTaskInstance historicTask = mock(HistoricTaskInstance.class);
    HistoricVariableInstanceQuery allVariablesQuery = mock(HistoricVariableInstanceQuery.class);
    HistoricVariableInstance sessionVariable = mock(HistoricVariableInstance.class);
    HistoricVariableInstance sessionAssignees = mock(HistoricVariableInstance.class);
    HistoricVariableInstance completedVariable = mock(HistoricVariableInstance.class);
    HistoricVariableInstance approvedVariable = mock(HistoricVariableInstance.class);

    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processQuery);
    when(processQuery.processInstanceId("instance-1")).thenReturn(processQuery);
    when(processQuery.singleResult()).thenReturn(historicProcessInstance);
    when(historicProcessInstance.getProcessDefinitionId()).thenReturn("process-definition-1");

    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskQuery);
    when(taskQuery.processInstanceId("instance-1")).thenReturn(taskQuery);
    when(taskQuery.orderByHistoricTaskInstanceStartTime()).thenReturn(taskQuery);
    when(taskQuery.asc()).thenReturn(taskQuery);
    when(taskQuery.list()).thenReturn(Collections.singletonList(historicTask));

    when(historicTask.getId()).thenReturn("task-1");
    when(historicTask.getName()).thenReturn("会签审批");
    when(historicTask.getTaskDefinitionKey()).thenReturn("multi-a");
    when(historicTask.getProcessInstanceId()).thenReturn("instance-1");
    when(historicTask.getExecutionId()).thenReturn("execution-1");
    when(historicTask.getAssignee()).thenReturn("approver-1");
    when(historicTask.getStartTime()).thenReturn(dateOf(2026, Month.MARCH, 18, 11, 0));
    when(historicTask.getEndTime()).thenReturn(null);

    when(historyService.createHistoricVariableInstanceQuery()).thenReturn(allVariablesQuery);
    when(allVariablesQuery.processInstanceId("instance-1")).thenReturn(allVariablesQuery);
    when(allVariablesQuery.list()).thenReturn(Arrays.asList(sessionVariable, sessionAssignees, completedVariable, approvedVariable));

    when(sessionVariable.getTaskId()).thenReturn("task-1");
    when(sessionVariable.getVariableName()).thenReturn(Constants.Variable.MULTIPLE_APPROVAL_SESSION);
    when(sessionVariable.getValue()).thenReturn("session-1");
    when(sessionAssignees.getVariableName()).thenReturn(
        Constants.Variable.MULTIPLE_APPROVAL_SESSION + "session-1" + Constants.Variable.MULTIPLE_APPROVAL_ASSIGNEE_LIST);
    when(sessionAssignees.getValue()).thenReturn(Arrays.asList("approver-1", "approver-2"));

    when(completedVariable.getVariableName()).thenReturn(
        Constants.Variable.MULTIPLE_APPROVAL_SESSION + "session-1" + Constants.Variable.MULTIPLE_APPROVAL_COMPLETED_SUFFIX);
    when(completedVariable.getValue()).thenReturn(Boolean.FALSE);
    when(approvedVariable.getVariableName()).thenReturn(
        Constants.Variable.MULTIPLE_APPROVAL_SESSION + "session-1" + Constants.Variable.APPROVED_SUFFIX);
    when(approvedVariable.getValue()).thenReturn(Boolean.FALSE);

    when(historyService.getHistoricIdentityLinksForTask("task-1")).thenReturn(Collections.emptyList());
    when(taskService.getProcessInstanceComments("instance-1")).thenReturn(Collections.emptyList());

    List<FlowTimeline> timeline = service.getTimeLine("instance-1");
    FlowTimeline multipleTimeline = timeline.stream()
        .filter(item -> "multi-a".equals(item.getFlowNode().getId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("multi-a timeline not found"));

    assertEquals(TaskState.PROCESSING, multipleTimeline.getState());
    assertEquals(2, multipleTimeline.getTaskInstances().size());
    assertEquals("approver-2", multipleTimeline.getTaskInstances().get(1).getAssignee());
    assertEquals(TaskState.UNCREATED, multipleTimeline.getTaskInstances().get(1).getState());
    verify(historyService).createHistoricVariableInstanceQuery();
  }

  @Test
  void getInstancesSeparatesRunningAndSuspendedInstancesByRuntimeState() {
    RuntimeService runtimeService = mock(RuntimeService.class);
    HistoryService historyService = mock(HistoryService.class);

    Activiti6FlowInstanceService service = new Activiti6FlowInstanceService();
    service.runtimeService = runtimeService;
    service.historyService = historyService;

    HistoricProcessInstanceQuery historicQuery = mock(HistoricProcessInstanceQuery.class);
    HistoricProcessInstance activeInstance = mock(HistoricProcessInstance.class);
    HistoricProcessInstance suspendedInstance = mock(HistoricProcessInstance.class);
    HistoricTaskInstanceQuery taskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricTaskInstance latestTask = mock(HistoricTaskInstance.class);
    HistoricVariableInstanceQuery variableQuery = mock(HistoricVariableInstanceQuery.class);
    HistoricVariableInstance starterVariable = mock(HistoricVariableInstance.class);
    ProcessInstanceQuery runtimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstanceQuery activeRuntimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstanceQuery suspendedRuntimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstance activeRuntime = mock(ProcessInstance.class);
    ProcessInstance suspendedRuntime = mock(ProcessInstance.class);

    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(historicQuery);
    when(historicQuery.processDefinitionKey("flow-a")).thenReturn(historicQuery);
    when(historicQuery.unfinished()).thenReturn(historicQuery);
    when(historicQuery.orderByProcessInstanceStartTime()).thenReturn(historicQuery);
    when(historicQuery.desc()).thenReturn(historicQuery);
    when(historicQuery.list()).thenReturn(Arrays.asList(activeInstance, suspendedInstance));
    when(historicQuery.listPage(eq(0), anyInt())).thenReturn(Arrays.asList(activeInstance, suspendedInstance));

    when(activeInstance.getId()).thenReturn("instance-active");
    when(suspendedInstance.getId()).thenReturn("instance-suspended");

    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskQuery);
    when(taskQuery.processInstanceId(anyString())).thenReturn(taskQuery);
    when(taskQuery.orderByHistoricTaskInstanceStartTime()).thenReturn(taskQuery);
    when(taskQuery.desc()).thenReturn(taskQuery);
    when(taskQuery.list()).thenThrow(new AssertionError("latest historic task lookup should use listPage(0, 1)"));
    when(taskQuery.listPage(0, 1)).thenReturn(Collections.singletonList(latestTask));
    when(latestTask.getId()).thenReturn("task-1");
    when(latestTask.getName()).thenReturn("经理审批");

    when(historyService.createHistoricVariableInstanceQuery()).thenReturn(variableQuery);
    when(variableQuery.processInstanceId(anyString())).thenReturn(variableQuery);
    when(variableQuery.variableName(anyString())).thenReturn(variableQuery);
    when(variableQuery.singleResult()).thenReturn(starterVariable);
    when(starterVariable.getValue()).thenReturn("starter-1");

    when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
    when(runtimeQuery.processDefinitionKey("flow-a")).thenReturn(runtimeQuery);
    when(runtimeQuery.active()).thenReturn(activeRuntimeQuery);
    when(runtimeQuery.suspended()).thenReturn(suspendedRuntimeQuery);
    when(activeRuntimeQuery.processInstanceIds(any())).thenReturn(activeRuntimeQuery);
    when(suspendedRuntimeQuery.processInstanceIds(any())).thenReturn(suspendedRuntimeQuery);
    when(activeRuntimeQuery.list()).thenReturn(Collections.singletonList(activeRuntime));
    when(suspendedRuntimeQuery.list()).thenReturn(Collections.singletonList(suspendedRuntime));
    when(activeRuntime.getId()).thenReturn("instance-active");
    when(suspendedRuntime.getId()).thenReturn("instance-suspended");

    List<FlowInstanceDetail> running = service.getInstances("flow-a", "running", 0, 20);
    List<FlowInstanceDetail> suspended = service.getInstances("flow-a", "suspended", 0, 20);

    assertEquals(1, running.size());
    assertEquals("instance-active", running.get(0).getInstanceId());
    assertEquals("running", running.get(0).getStatus());
    assertEquals(1, suspended.size());
    assertEquals("instance-suspended", suspended.get(0).getInstanceId());
    assertEquals("suspended", suspended.get(0).getStatus());
  }

  @Test
  void getInstancesWithoutStatusStillMarksSuspendedInstancesAsSuspended() {
    RuntimeService runtimeService = mock(RuntimeService.class);
    HistoryService historyService = mock(HistoryService.class);

    Activiti6FlowInstanceService service = new Activiti6FlowInstanceService();
    service.runtimeService = runtimeService;
    service.historyService = historyService;

    HistoricProcessInstanceQuery historicQuery = mock(HistoricProcessInstanceQuery.class);
    HistoricProcessInstance activeInstance = mock(HistoricProcessInstance.class);
    HistoricProcessInstance suspendedInstance = mock(HistoricProcessInstance.class);
    HistoricTaskInstanceQuery taskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricVariableInstanceQuery variableQuery = mock(HistoricVariableInstanceQuery.class);
    ProcessInstanceQuery runtimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstanceQuery activeRuntimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstanceQuery suspendedRuntimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstance activeRuntime = mock(ProcessInstance.class);
    ProcessInstance suspendedRuntime = mock(ProcessInstance.class);

    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(historicQuery);
    when(historicQuery.processDefinitionKey("flow-a")).thenReturn(historicQuery);
    when(historicQuery.orderByProcessInstanceStartTime()).thenReturn(historicQuery);
    when(historicQuery.desc()).thenReturn(historicQuery);
    when(historicQuery.listPage(0, 20)).thenReturn(Arrays.asList(activeInstance, suspendedInstance));

    when(activeInstance.getId()).thenReturn("instance-active");
    when(activeInstance.getProcessDefinitionKey()).thenReturn("flow-a");
    when(activeInstance.getStartTime()).thenReturn(dateOf(2026, Month.MARCH, 18, 9, 0));
    when(activeInstance.getEndTime()).thenReturn(null);
    when(suspendedInstance.getId()).thenReturn("instance-suspended");
    when(suspendedInstance.getProcessDefinitionKey()).thenReturn("flow-a");
    when(suspendedInstance.getStartTime()).thenReturn(dateOf(2026, Month.MARCH, 18, 10, 0));
    when(suspendedInstance.getEndTime()).thenReturn(null);

    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskQuery);
    when(taskQuery.processInstanceId(anyString())).thenReturn(taskQuery);
    when(taskQuery.orderByHistoricTaskInstanceStartTime()).thenReturn(taskQuery);
    when(taskQuery.desc()).thenReturn(taskQuery);
    when(taskQuery.listPage(0, 1)).thenReturn(Collections.emptyList());

    when(historyService.createHistoricVariableInstanceQuery()).thenReturn(variableQuery);
    when(variableQuery.processInstanceId(anyString())).thenReturn(variableQuery);
    when(variableQuery.variableName(anyString())).thenReturn(variableQuery);
    when(variableQuery.singleResult()).thenReturn(null);

    when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
    when(runtimeQuery.processInstanceIds(any())).thenReturn(runtimeQuery);
    when(runtimeQuery.active()).thenReturn(activeRuntimeQuery);
    when(runtimeQuery.suspended()).thenReturn(suspendedRuntimeQuery);
    when(activeRuntimeQuery.list()).thenReturn(Collections.singletonList(activeRuntime));
    when(suspendedRuntimeQuery.list()).thenReturn(Collections.singletonList(suspendedRuntime));
    when(activeRuntime.getId()).thenReturn("instance-active");
    when(suspendedRuntime.getId()).thenReturn("instance-suspended");

    List<FlowInstanceDetail> details = service.getInstances("flow-a", null, 0, 20);

    assertEquals(2, details.size());
    assertEquals("running", details.get(0).getStatus());
    assertEquals("suspended", details.get(1).getStatus());
  }

  @Test
  void getInstancesPagesRuntimeStatusesWithoutLoadingAllHistoricInstances() {
    RuntimeService runtimeService = mock(RuntimeService.class);
    HistoryService historyService = mock(HistoryService.class);

    Activiti6FlowInstanceService service = new Activiti6FlowInstanceService();
    service.runtimeService = runtimeService;
    service.historyService = historyService;

    HistoricProcessInstanceQuery historicQuery = mock(HistoricProcessInstanceQuery.class);
    HistoricProcessInstance firstRunning = mock(HistoricProcessInstance.class);
    HistoricProcessInstance suspendedInstance = mock(HistoricProcessInstance.class);
    HistoricProcessInstance secondRunning = mock(HistoricProcessInstance.class);
    HistoricTaskInstanceQuery taskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricTaskInstance latestTask = mock(HistoricTaskInstance.class);
    HistoricVariableInstanceQuery variableQuery = mock(HistoricVariableInstanceQuery.class);
    HistoricVariableInstance starterVariable = mock(HistoricVariableInstance.class);
    ProcessInstanceQuery runtimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstanceQuery activeRuntimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstance firstRunningRuntime = mock(ProcessInstance.class);
    ProcessInstance secondRunningRuntime = mock(ProcessInstance.class);

    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(historicQuery);
    when(historicQuery.processDefinitionKey("flow-a")).thenReturn(historicQuery);
    when(historicQuery.unfinished()).thenReturn(historicQuery);
    when(historicQuery.orderByProcessInstanceStartTime()).thenReturn(historicQuery);
    when(historicQuery.desc()).thenReturn(historicQuery);
    when(historicQuery.list()).thenThrow(new AssertionError("should page runtime-state history query instead of loading all unfinished instances"));
    when(historicQuery.listPage(eq(0), anyInt())).thenReturn(Arrays.asList(firstRunning, suspendedInstance, secondRunning));

    when(firstRunning.getId()).thenReturn("instance-running-1");
    when(secondRunning.getId()).thenReturn("instance-running-2");

    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskQuery);
    when(taskQuery.processInstanceId(anyString())).thenReturn(taskQuery);
    when(taskQuery.orderByHistoricTaskInstanceStartTime()).thenReturn(taskQuery);
    when(taskQuery.desc()).thenReturn(taskQuery);
    when(taskQuery.list()).thenThrow(new AssertionError("latest historic task lookup should use listPage(0, 1)"));
    when(taskQuery.listPage(0, 1)).thenReturn(Collections.singletonList(latestTask));
    when(latestTask.getId()).thenReturn("task-1");
    when(latestTask.getName()).thenReturn("经理审批");

    when(historyService.createHistoricVariableInstanceQuery()).thenReturn(variableQuery);
    when(variableQuery.processInstanceId(anyString())).thenReturn(variableQuery);
    when(variableQuery.variableName(anyString())).thenReturn(variableQuery);
    when(variableQuery.singleResult()).thenReturn(starterVariable);
    when(starterVariable.getValue()).thenReturn("starter-1");

    when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
    when(runtimeQuery.processDefinitionKey("flow-a")).thenReturn(runtimeQuery);
    when(runtimeQuery.active()).thenReturn(activeRuntimeQuery);
    when(activeRuntimeQuery.processInstanceIds(any())).thenReturn(activeRuntimeQuery);
    when(activeRuntimeQuery.list()).thenReturn(Arrays.asList(firstRunningRuntime, secondRunningRuntime));
    when(firstRunningRuntime.getId()).thenReturn("instance-running-1");
    when(secondRunningRuntime.getId()).thenReturn("instance-running-2");

    List<FlowInstanceDetail> running = service.getInstances("flow-a", "running", 1, 1);

    assertEquals(1, running.size());
    assertEquals("instance-running-2", running.get(0).getInstanceId());
    assertEquals("running", running.get(0).getStatus());
    verify(historicQuery, never()).list();
  }

  @Test
  void getInstancesCountUsesRuntimeStateForRunningAndSuspendedStatuses() {
    RuntimeService runtimeService = mock(RuntimeService.class);
    HistoryService historyService = mock(HistoryService.class);

    Activiti6FlowInstanceService service = new Activiti6FlowInstanceService();
    service.runtimeService = runtimeService;
    service.historyService = historyService;

    HistoricProcessInstanceQuery historicQuery = mock(HistoricProcessInstanceQuery.class);
    ProcessInstanceQuery runtimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstanceQuery activeRuntimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstanceQuery suspendedRuntimeQuery = mock(ProcessInstanceQuery.class);

    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(historicQuery);
    when(historicQuery.processDefinitionKey("flow-a")).thenReturn(historicQuery);
    when(historicQuery.unfinished()).thenReturn(historicQuery);
    when(historicQuery.count()).thenReturn(2L);

    when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
    when(runtimeQuery.processDefinitionKey("flow-a")).thenReturn(runtimeQuery);
    when(runtimeQuery.active()).thenReturn(activeRuntimeQuery);
    when(runtimeQuery.suspended()).thenReturn(suspendedRuntimeQuery);
    when(activeRuntimeQuery.count()).thenReturn(1L);
    when(suspendedRuntimeQuery.count()).thenReturn(1L);

    assertEquals(1L, service.getInstancesCount("flow-a", "running"));
    assertEquals(1L, service.getInstancesCount("flow-a", "suspended"));
  }

  @Test
  void getStatisticUsesActiveRuntimeCountForRunning() {
    RuntimeService runtimeService = mock(RuntimeService.class);
    HistoryService historyService = mock(HistoryService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);

    Activiti6FlowInstanceService service = new Activiti6FlowInstanceService();
    service.runtimeService = runtimeService;
    service.historyService = historyService;
    service.repositoryService = repositoryService;

    ProcessDefinitionQuery definitionQuery = mock(ProcessDefinitionQuery.class);
    ProcessDefinition definition = mock(ProcessDefinition.class);
    HistoricProcessInstanceQuery totalQuery = mock(HistoricProcessInstanceQuery.class);
    HistoricProcessInstanceQuery durationQuery = mock(HistoricProcessInstanceQuery.class);
    HistoricTaskInstanceQuery durationTaskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricProcessInstanceQuery unfinishedQuery = mock(HistoricProcessInstanceQuery.class);
    ProcessInstanceQuery runtimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstanceQuery activeRuntimeQuery = mock(ProcessInstanceQuery.class);
    ProcessInstanceQuery suspendedRuntimeQuery = mock(ProcessInstanceQuery.class);
    HistoricProcessInstance finishedInstance = mock(HistoricProcessInstance.class);
    HistoricTaskInstance approvedTask = mock(HistoricTaskInstance.class);
    HistoricTaskInstance systemTask = mock(HistoricTaskInstance.class);

    when(repositoryService.createProcessDefinitionQuery()).thenReturn(definitionQuery);
    when(definitionQuery.latestVersion()).thenReturn(definitionQuery);
    when(definitionQuery.processDefinitionCategoryLike(Constants.OUROBOROS_NAMESPACE + "/%")).thenReturn(definitionQuery);
    when(definitionQuery.active()).thenReturn(definitionQuery);
    when(definitionQuery.orderByProcessDefinitionKey()).thenReturn(definitionQuery);
    when(definitionQuery.asc()).thenReturn(definitionQuery);
    when(definitionQuery.listPage(0, 20)).thenReturn(Collections.singletonList(definition));
    when(definition.getCategory()).thenReturn(Constants.OUROBOROS_NAMESPACE + "/finance");
    when(definition.getKey()).thenReturn("flow-a");
    when(definition.getName()).thenReturn("采购审批");
    when(definition.getDescription()).thenReturn("采购流程");
    when(definition.isSuspended()).thenReturn(false);

    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(totalQuery, durationQuery);
    when(totalQuery.processDefinitionKey("flow-a")).thenReturn(totalQuery);
    when(totalQuery.count()).thenReturn(5L);
    when(totalQuery.unfinished()).thenReturn(unfinishedQuery);
    when(unfinishedQuery.count()).thenReturn(2L);
    when(durationQuery.processDefinitionKey("flow-a")).thenReturn(durationQuery);
    when(durationQuery.finished()).thenReturn(durationQuery);
    when(durationQuery.list()).thenReturn(Collections.singletonList(finishedInstance));
    when(finishedInstance.getStartTime()).thenReturn(dateOf(2026, Month.JANUARY, 1, 9, 0));
    when(finishedInstance.getEndTime()).thenReturn(dateOf(2026, Month.JANUARY, 1, 11, 0));

    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(durationTaskQuery);
    when(durationTaskQuery.processDefinitionKey("flow-a")).thenReturn(durationTaskQuery);
    when(durationTaskQuery.finished()).thenReturn(durationTaskQuery);
    when(durationTaskQuery.list()).thenReturn(Arrays.asList(approvedTask, systemTask));
    when(approvedTask.getAssignee()).thenReturn("approver-1");
    when(approvedTask.getStartTime()).thenReturn(dateOf(2026, Month.JANUARY, 1, 9, 10));
    when(approvedTask.getEndTime()).thenReturn(dateOf(2026, Month.JANUARY, 1, 9, 40));
    when(systemTask.getStartTime()).thenReturn(dateOf(2026, Month.JANUARY, 1, 9, 45));
    when(systemTask.getEndTime()).thenReturn(dateOf(2026, Month.JANUARY, 1, 10, 15));

    when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
    when(runtimeQuery.processDefinitionKey("flow-a")).thenReturn(runtimeQuery);
    when(runtimeQuery.active()).thenReturn(activeRuntimeQuery);
    when(runtimeQuery.suspended()).thenReturn(suspendedRuntimeQuery);
    when(activeRuntimeQuery.count()).thenReturn(1L);
    when(suspendedRuntimeQuery.count()).thenReturn(1L);

    List<FlowInstanceStatistic> statistics = service.getStatistic(null, "deployed", 0, 20);

    assertEquals(1, statistics.size());
    FlowInstanceStatistic statistic = statistics.get(0);
    assertEquals("flow-a", statistic.getFlowKey());
    assertEquals("采购审批", statistic.getDisplayName());
    assertEquals("finance", statistic.getCategory());
    assertEquals("采购流程", statistic.getDescription());
    assertEquals(5L, statistic.getTotal());
    assertEquals(1L, statistic.getRunning());
    assertEquals(1L, statistic.getSuspended());
    assertEquals(3L, statistic.getFinished());
    assertEquals(7200000L, statistic.getAvgDurationMillis());
    assertEquals(2L, statistic.getNodeCount());
    assertEquals(1800000L, statistic.getAvgNodeDurationMillis());
    assertEquals(1L, statistic.getHandledCount());
    assertEquals(1800000L, statistic.getAvgHandleMillis());
    assertEquals("deployed", statistic.getStatus());
  }

  @Test
  void getDurationStatisticAggregatesFlowNodesAndApproversWithinRange() {
    RepositoryService repositoryService = buildRepositoryService("flow-a", singleApprovalFlowJson());
    HistoryService historyService = mock(HistoryService.class);

    Activiti6FlowInstanceService service = new Activiti6FlowInstanceService();
    service.repositoryService = repositoryService;
    service.historyService = historyService;

    HistoricProcessInstanceQuery processQuery = mock(HistoricProcessInstanceQuery.class);
    HistoricTaskInstanceQuery taskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricProcessInstance inRangeInstance = mock(HistoricProcessInstance.class);
    HistoricProcessInstance outOfRangeInstance = mock(HistoricProcessInstance.class);
    HistoricTaskInstance approveAFirst = mock(HistoricTaskInstance.class);
    HistoricTaskInstance approveASecond = mock(HistoricTaskInstance.class);
    HistoricTaskInstance approveB = mock(HistoricTaskInstance.class);
    HistoricTaskInstance ignoredTask = mock(HistoricTaskInstance.class);

    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processQuery);
    when(processQuery.processDefinitionKey("flow-a")).thenReturn(processQuery);
    when(processQuery.finished()).thenReturn(processQuery);
    when(processQuery.list()).thenReturn(Arrays.asList(inRangeInstance, outOfRangeInstance));
    when(inRangeInstance.getId()).thenReturn("instance-1");
    when(inRangeInstance.getStartTime()).thenReturn(dateOf(2026, Month.JANUARY, 1, 9, 0));
    when(inRangeInstance.getEndTime()).thenReturn(dateOf(2026, Month.JANUARY, 1, 11, 0));
    when(outOfRangeInstance.getId()).thenReturn("instance-2");
    when(outOfRangeInstance.getStartTime()).thenReturn(dateOf(2025, Month.DECEMBER, 31, 9, 0));
    when(outOfRangeInstance.getEndTime()).thenReturn(dateOf(2025, Month.DECEMBER, 31, 10, 0));

    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskQuery);
    when(taskQuery.processDefinitionKey("flow-a")).thenReturn(taskQuery);
    when(taskQuery.finished()).thenReturn(taskQuery);
    when(taskQuery.list()).thenReturn(Arrays.asList(approveAFirst, approveASecond, approveB, ignoredTask));
    mockHistoricTask(approveAFirst, "instance-1", "approve-a", "经理审批", "approver-1",
        dateOf(2026, Month.JANUARY, 1, 9, 10), dateOf(2026, Month.JANUARY, 1, 9, 40));
    mockHistoricTask(approveASecond, "instance-1", "approve-a", "经理审批", "approver-2",
        dateOf(2026, Month.JANUARY, 1, 9, 40), dateOf(2026, Month.JANUARY, 1, 11, 10));
    mockHistoricTask(approveB, "instance-1", "approve-b", "财务审批", "approver-1",
        dateOf(2026, Month.JANUARY, 1, 10, 0), dateOf(2026, Month.JANUARY, 1, 11, 0));
    mockHistoricTask(ignoredTask, "instance-2", "approve-a", "经理审批", "approver-1",
        dateOf(2025, Month.DECEMBER, 31, 9, 0), dateOf(2025, Month.DECEMBER, 31, 10, 0));

    FlowDurationStatistic statistic = service.getDurationStatistic(
        "flow-a",
        dateOf(2026, Month.JANUARY, 1, 0, 0),
        dateOf(2026, Month.JANUARY, 2, 0, 0));

    assertEquals("flow-a", statistic.getFlowKey());
    assertEquals(1L, statistic.getInstanceCount());
    assertEquals(7200000L, statistic.getAvgDurationMillis());
    assertEquals(2, statistic.getNodes().size());
    assertEquals("approve-a", statistic.getNodes().get(0).getNodeId());
    assertEquals("经理审批", statistic.getNodes().get(0).getNodeName());
    assertEquals(2L, statistic.getNodes().get(0).getTaskCount());
    assertEquals(3600000L, statistic.getNodes().get(0).getAvgDurationMillis());
    assertEquals("approve-b", statistic.getNodes().get(1).getNodeId());
    assertEquals(1L, statistic.getNodes().get(1).getTaskCount());
    assertEquals(3600000L, statistic.getNodes().get(1).getAvgDurationMillis());
    assertEquals(2, statistic.getApprovers().size());
    assertEquals("approver-1", statistic.getApprovers().get(0).getUserId());
    assertEquals("approver-1", statistic.getApprovers().get(0).getUserName());
    assertEquals(2L, statistic.getApprovers().get(0).getHandledCount());
    assertEquals(2700000L, statistic.getApprovers().get(0).getAvgHandleMillis());
    assertEquals("approver-2", statistic.getApprovers().get(1).getUserId());
    assertEquals(1L, statistic.getApprovers().get(1).getHandledCount());
    assertEquals(5400000L, statistic.getApprovers().get(1).getAvgHandleMillis());
  }

  @Test
  void getStatisticOrdersDefinitionsByKeyBeforePaging() {
    RuntimeService runtimeService = mock(RuntimeService.class);
    HistoryService historyService = mock(HistoryService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);

    Activiti6FlowInstanceService service = new Activiti6FlowInstanceService();
    service.runtimeService = runtimeService;
    service.historyService = historyService;
    service.repositoryService = repositoryService;

    ProcessDefinitionQuery definitionQuery = mock(ProcessDefinitionQuery.class);
    ProcessDefinitionQuery orderedQuery = mock(ProcessDefinitionQuery.class);

    when(repositoryService.createProcessDefinitionQuery()).thenReturn(definitionQuery);
    when(definitionQuery.latestVersion()).thenReturn(definitionQuery);
    when(definitionQuery.processDefinitionCategoryLike(Constants.OUROBOROS_NAMESPACE + "/%")).thenReturn(definitionQuery);
    when(definitionQuery.active()).thenReturn(definitionQuery);
    when(definitionQuery.listPage(0, 20)).thenThrow(new AssertionError("statistics query should order definitions before paging"));
    when(definitionQuery.orderByProcessDefinitionKey()).thenReturn(orderedQuery);
    when(orderedQuery.asc()).thenReturn(orderedQuery);
    when(orderedQuery.listPage(0, 20)).thenReturn(Collections.emptyList());

    assertEquals(Collections.emptyList(), service.getStatistic(null, "deployed", 0, 20));
  }

  private RepositoryService buildRepositoryService(String flowKey, String rawJson) {
    RepositoryService repositoryService = mock(RepositoryService.class);
    ProcessDefinitionQuery query = mock(ProcessDefinitionQuery.class);
    ProcessDefinition processDefinition = mock(ProcessDefinition.class);

    when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
    when(query.processDefinitionKey(flowKey)).thenReturn(query);
    when(query.latestVersion()).thenReturn(query);
    when(query.singleResult()).thenReturn(processDefinition);
    when(processDefinition.getId()).thenReturn("process-definition-1");
    when(processDefinition.getKey()).thenReturn(flowKey);
    when(processDefinition.isSuspended()).thenReturn(false);
    when(repositoryService.getBpmnModel("process-definition-1")).thenReturn(buildBpmnModel(rawJson));
    return repositoryService;
  }

  private RepositoryService buildRepositoryServiceWithoutRawDefinition(String flowKey) {
    RepositoryService repositoryService = mock(RepositoryService.class);
    ProcessDefinitionQuery query = mock(ProcessDefinitionQuery.class);
    ProcessDefinition processDefinition = mock(ProcessDefinition.class);

    when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
    when(query.processDefinitionKey(flowKey)).thenReturn(query);
    when(query.latestVersion()).thenReturn(query);
    when(query.singleResult()).thenReturn(processDefinition);
    when(processDefinition.getId()).thenReturn("process-definition-1");
    when(processDefinition.getKey()).thenReturn(flowKey);
    when(processDefinition.isSuspended()).thenReturn(false);
    when(repositoryService.getBpmnModel("process-definition-1")).thenReturn(buildBpmnModelWithoutRawDefinition());
    return repositoryService;
  }

  private BpmnModel buildBpmnModel(String rawJson) {
    BpmnModel model = new BpmnModel();
    Process process = new Process();
    model.addProcess(process);
    BpmnUtil.saveFlowDefinitionRawJson(process, rawJson);
    return model;
  }

  private BpmnModel buildBpmnModelWithoutRawDefinition() {
    BpmnModel model = new BpmnModel();
    model.addProcess(new Process());
    return model;
  }

  private Date dateOf(int year, Month month, int day, int hour, int minute) {
    return Date.from(LocalDateTime.of(year, month, day, hour, minute)
        .atZone(ZoneId.systemDefault())
        .toInstant());
  }

  private void mockHistoricTask(
      HistoricTaskInstance task,
      String processInstanceId,
      String nodeId,
      String nodeName,
      String assignee,
      Date startTime,
      Date endTime) {
    when(task.getProcessInstanceId()).thenReturn(processInstanceId);
    when(task.getTaskDefinitionKey()).thenReturn(nodeId);
    when(task.getName()).thenReturn(nodeName);
    when(task.getAssignee()).thenReturn(assignee);
    when(task.getStartTime()).thenReturn(startTime);
    when(task.getEndTime()).thenReturn(endTime);
  }

  private String singleApprovalFlowJson() {
    return "{\n"
        + "  \"formatVersion\": 2,\n"
        + "  \"id\": \"flow-a\",\n"
        + "  \"name\": \"采购审批\",\n"
        + "  \"category\": \"\",\n"
        + "  \"icon\": \"shenpi\",\n"
        + "  \"description\": \"\",\n"
        + "  \"formType\": \"default\",\n"
        + "  \"formKey\": \"purchase-form\",\n"
        + "  \"nodes\": [\n"
        + "    { \"type\": \"发起节点\", \"name\": \"发起节点\", \"id\": \"start-a\", \"permission\": [], \"candidates\": [] },\n"
        + "    {\n"
        + "      \"type\": \"审批节点\",\n"
        + "      \"name\": \"经理审批\",\n"
        + "      \"id\": \"approve-a\",\n"
        + "      \"subType\": \"单签\",\n"
        + "      \"candidates\": [{ \"type\": \"UserSelect\", \"mode\": \"single\" }],\n"
        + "      \"permission\": [],\n"
        + "      \"actions\": [\"reject\"]\n"
        + "    }\n"
        + "  ],\n"
        + "  \"edges\": [\n"
        + "    { \"id\": \"e1\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-a\" }\n"
        + "  ]\n"
        + "}";
  }

  private String singleApprovalFlowJsonWithoutFormType() {
    return "{\n"
        + "  \"formatVersion\": 2,\n"
        + "  \"id\": \"flow-a\",\n"
        + "  \"name\": \"采购审批\",\n"
        + "  \"category\": \"\",\n"
        + "  \"icon\": \"shenpi\",\n"
        + "  \"description\": \"\",\n"
        + "  \"formKey\": \"purchase-form\",\n"
        + "  \"nodes\": [\n"
        + "    { \"type\": \"发起节点\", \"name\": \"发起节点\", \"id\": \"start-a\", \"permission\": [] },\n"
        + "    { \"type\": \"审批节点\", \"name\": \"经理审批\", \"id\": \"approve-a\", \"subType\": \"单签\", \"candidates\": [], \"permission\": [], \"actions\": [\"reject\"] }\n"
        + "  ],\n"
        + "  \"edges\": [\n"
        + "    { \"id\": \"e1\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-a\" }\n"
        + "  ]\n"
        + "}";
  }

  private String flowJsonWithoutStartPermission() {
    return "{\n"
        + "  \"formatVersion\": 2,\n"
        + "  \"id\": \"flow-a\",\n"
        + "  \"name\": \"采购审批\",\n"
        + "  \"category\": \"\",\n"
        + "  \"icon\": \"shenpi\",\n"
        + "  \"description\": \"\",\n"
        + "  \"formType\": \"default\",\n"
        + "  \"formKey\": \"purchase-form\",\n"
        + "  \"nodes\": [\n"
        + "    { \"type\": \"发起节点\", \"name\": \"发起节点\", \"id\": \"start-a\" },\n"
        + "    { \"type\": \"审批节点\", \"name\": \"经理审批\", \"id\": \"approve-a\", \"subType\": \"单签\", \"candidates\": [], \"permission\": [], \"actions\": [\"reject\"] }\n"
        + "  ],\n"
        + "  \"edges\": [\n"
        + "    { \"id\": \"e1\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-a\" }\n"
        + "  ]\n"
        + "}";
  }

  private String flowJsonWithStartNodeNotFirst() {
    return "{\n"
        + "  \"formatVersion\": 2,\n"
        + "  \"id\": \"flow-a\",\n"
        + "  \"name\": \"测试流程\",\n"
        + "  \"category\": \"\",\n"
        + "  \"icon\": \"shenpi\",\n"
        + "  \"description\": \"\",\n"
        + "  \"formType\": \"default\",\n"
        + "  \"formKey\": \"purchase-form\",\n"
        + "  \"nodes\": [\n"
        + "    {\n"
        + "      \"type\": \"审批节点\",\n"
        + "      \"name\": \"审批节点\",\n"
        + "      \"id\": \"approve-a\",\n"
        + "      \"subType\": \"单签\",\n"
        + "      \"candidates\": [],\n"
        + "      \"permission\": [\n"
        + "        { \"name\": \"approval-field\", \"label\": \"审批字段\", \"visible\": true, \"writable\": true }\n"
        + "      ]\n"
        + "    },\n"
        + "    {\n"
        + "      \"type\": \"发起节点\",\n"
        + "      \"name\": \"发起节点\",\n"
        + "      \"id\": \"start-a\",\n"
        + "      \"permission\": [\n"
        + "        { \"name\": \"start-field\", \"label\": \"发起字段\", \"visible\": true, \"writable\": true }\n"
        + "      ],\n"
        + "      \"candidates\": []\n"
        + "    }\n"
        + "  ],\n"
        + "  \"edges\": [\n"
        + "    { \"id\": \"e1\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-a\" }\n"
        + "  ]\n"
        + "}";
  }

  private String businessModelFlowJsonWithStartPermission() {
    return "{\n"
        + "  \"formatVersion\": 2,\n"
        + "  \"id\": \"flow-a\",\n"
        + "  \"name\": \"业务模型审批\",\n"
        + "  \"category\": \"\",\n"
        + "  \"icon\": \"shenpi\",\n"
        + "  \"description\": \"\",\n"
        + "  \"formType\": \"business-model\",\n"
        + "  \"formKey\": \"purchase-form\",\n"
        + "  \"nodes\": [\n"
        + "    {\n"
        + "      \"type\": \"发起节点\",\n"
        + "      \"name\": \"发起节点\",\n"
        + "      \"id\": \"start-a\",\n"
        + "      \"permission\": [\n"
        + "        { \"name\": \"start-field\", \"label\": \"发起字段\", \"visible\": true, \"writable\": true }\n"
        + "      ],\n"
        + "      \"candidates\": []\n"
        + "    },\n"
        + "    { \"type\": \"审批节点\", \"name\": \"经理审批\", \"id\": \"approve-a\", \"subType\": \"单签\", \"candidates\": [], \"permission\": [], \"actions\": [\"reject\"] }\n"
        + "  ],\n"
        + "  \"edges\": [\n"
        + "    { \"id\": \"e1\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-a\" }\n"
        + "  ]\n"
        + "}";
  }

  private List<String> permissionNames(List permissions) {
    List<String> names = new ArrayList<String>();
    for (Object permission : permissions) {
      if (permission instanceof NodeFormFieldPermission) {
        NodeFormFieldPermission nodeFormFieldPermission = (NodeFormFieldPermission) permission;
        names.add(nodeFormFieldPermission.getName());
      }
    }
    return names;
  }

  private String multipleApprovalFlowJson() {
    return "{\n"
        + "  \"formatVersion\": 2,\n"
        + "  \"id\": \"flow-multi-a\",\n"
        + "  \"name\": \"会签审批\",\n"
        + "  \"category\": \"\",\n"
        + "  \"icon\": \"shenpi\",\n"
        + "  \"description\": \"\",\n"
        + "  \"formType\": \"default\",\n"
        + "  \"formKey\": \"multi-form\",\n"
        + "  \"nodes\": [\n"
        + "    { \"type\": \"发起节点\", \"name\": \"发起节点\", \"id\": \"start-a\", \"permission\": [], \"candidates\": [] },\n"
        + "    {\n"
        + "      \"type\": \"审批节点\",\n"
        + "      \"name\": \"会签审批\",\n"
        + "      \"id\": \"multi-a\",\n"
        + "      \"subType\": \"会签\",\n"
        + "      \"candidates\": [],\n"
        + "      \"permission\": [],\n"
        + "      \"actions\": [\"reject\"],\n"
        + "      \"percentage\": 100,\n"
        + "      \"multipleMode\": \"并行\",\n"
        + "      \"rejectAction\": \"reject\"\n"
        + "    }\n"
        + "  ],\n"
        + "  \"edges\": [\n"
        + "    { \"id\": \"e1\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"multi-a\" }\n"
        + "  ]\n"
        + "}";
  }

  private CollectingFlowInstanceListener installInstanceEventCollector(Activiti6FlowInstanceService service) {
    CollectingFlowInstanceListener listener = new CollectingFlowInstanceListener();
    installInstanceEventListener(service, listener);
    return listener;
  }

  private void installInstanceEventListener(Activiti6FlowInstanceService service, FlowInstanceListener listener) {
    try {
      InstanceEventDispatcher dispatcher = new InstanceEventDispatcher();
      Field listenersField = InstanceEventDispatcher.class.getDeclaredField("listeners");
      listenersField.setAccessible(true);
      listenersField.set(dispatcher, Collections.singletonList(listener));
      setField(service, "instanceEventDispatcher", dispatcher);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void setField(Object target, String fieldName, Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private FlowInstanceService transactionalProxy(Activiti6FlowInstanceService target, RecordingTransactionManager transactionManager) {
    ProxyFactory proxyFactory = new ProxyFactory(target);
    proxyFactory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
    return (FlowInstanceService) proxyFactory.getProxy();
  }

  private void mockActiveExecution(RuntimeService runtimeService, String instanceId, String executionId) {
    ExecutionQuery executionQuery = mock(ExecutionQuery.class);
    Execution execution = mock(Execution.class);
    when(runtimeService.createExecutionQuery()).thenReturn(executionQuery);
    when(executionQuery.processInstanceId(instanceId)).thenReturn(executionQuery);
    when(executionQuery.list()).thenReturn(Collections.singletonList(execution));
    when(execution.isEnded()).thenReturn(false);
    when(execution.getId()).thenReturn(executionId);
  }

  private static class CollectingFlowInstanceListener implements FlowInstanceListener {
    private final List<FlowInstanceEvent> events = new ArrayList<FlowInstanceEvent>();

    @Override
    public void onEvent(FlowInstanceEvent event) {
      events.add(event);
    }

    List<FlowInstanceEvent> getEvents() {
      return events;
    }
  }

  private static class ThrowingFlowInstanceListener implements FlowInstanceListener {
    private final String message;

    private ThrowingFlowInstanceListener(String message) {
      this.message = message;
    }

    @Override
    public void onEvent(FlowInstanceEvent event) {
      throw new RuntimeException(message);
    }
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
