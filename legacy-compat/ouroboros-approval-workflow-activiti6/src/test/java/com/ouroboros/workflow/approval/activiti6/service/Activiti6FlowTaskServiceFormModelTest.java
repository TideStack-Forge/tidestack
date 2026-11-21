package com.ouroboros.workflow.approval.activiti6.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.activiti.engine.RepositoryService;
import org.activiti.engine.TaskService;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.task.Task;
import org.activiti.engine.task.TaskQuery;
import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.FlowFormService;
import com.ouroboros.workflow.approval.definition.NodeFormFieldPermission;
import com.ouroboros.workflow.approval.definition.nodes.HandleNode;
import com.ouroboros.workflow.approval.definition.nodes.SingleApprovalNode;
import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;

class Activiti6FlowTaskServiceFormModelTest {
  @Test
  void getTaskFormDefaultsBlankFormTypeToDefaultService() {
    TaskService taskService = mock(TaskService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);
    FlowFormService flowFormService = mock(FlowFormService.class);
    Activiti6FlowTaskService service = new Activiti6FlowTaskService(taskService, Collections.emptyList());
    service.repositoryService = repositoryService;
    service.flowDefinitionService = flowDefinitionService;
    service.flowFormServices = Collections.singletonList(flowFormService);

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    ProcessDefinition processDefinition = mock(ProcessDefinition.class);
    SchemaNode schemaNode = new SchemaNode();
    schemaNode.setType("form");
    List<NodeFormFieldPermission> permissions = Collections.singletonList(permission("amount", "金额"));
    List<String> actions = Arrays.asList("reject", "rollback");

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    when(task.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(task.getTaskDefinitionKey()).thenReturn("approve-a");
    when(task.getFormKey()).thenReturn("purchase-form");
    when(repositoryService.getProcessDefinition("process-definition-1")).thenReturn(processDefinition);
    when(processDefinition.getKey()).thenReturn("flow-a");
    when(flowDefinitionService.getFlowNodes("flow-a")).thenReturn(Collections.singletonList(approvalNode("approve-a", permissions, actions)));
    when(taskService.getVariable("task-1", Constants.TASK_FORM_TYPE_KEY, String.class)).thenReturn("");
    when(taskService.getVariable("task-1", Constants.TASK_FORM_ID_KEY, String.class)).thenReturn("form-1");
    when(flowFormService.getType()).thenReturn("default");
    when(flowFormService.getTaskFormSchema(eq("instance-1"), eq("task-1"), eq("purchase-form"), eq("form-1"), anyList(), eq(actions)))
        .thenReturn(Try.success(schemaNode));

    var result = service.getTaskForm("task-1");

    assertTrue(result.isSuccess());
    assertEquals(schemaNode, result.get());
  }

  @Test
  void getTaskFormUsesFlowDefinitionPermissionsInsteadOfActivitiFormProperties() {
    TaskService taskService = mock(TaskService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);
    FlowFormService flowFormService = mock(FlowFormService.class);
    Activiti6FlowTaskService service = new Activiti6FlowTaskService(taskService, Collections.emptyList());
    service.repositoryService = repositoryService;
    service.flowDefinitionService = flowDefinitionService;
    service.flowFormServices = Collections.singletonList(flowFormService);

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    ProcessDefinition processDefinition = mock(ProcessDefinition.class);
    SchemaNode schemaNode = new SchemaNode();
    schemaNode.setType("form");
    List<NodeFormFieldPermission> permissions = Collections.singletonList(permission("amount", "定义金额"));
    List<String> actions = Arrays.asList("reject", "rollback");

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    when(task.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(task.getTaskDefinitionKey()).thenReturn("approve-a");
    when(task.getFormKey()).thenReturn("purchase-form");
    when(repositoryService.getProcessDefinition("process-definition-1")).thenReturn(processDefinition);
    when(processDefinition.getKey()).thenReturn("flow-a");
    when(flowDefinitionService.getFlowNodes("flow-a")).thenReturn(Collections.singletonList(approvalNode("approve-a", permissions, actions)));
    when(taskService.getVariable("task-1", Constants.TASK_FORM_TYPE_KEY, String.class)).thenReturn("default");
    when(taskService.getVariable("task-1", Constants.TASK_FORM_ID_KEY, String.class)).thenReturn("form-1");
    when(flowFormService.getType()).thenReturn("default");
    when(flowFormService.getTaskFormSchema(eq("instance-1"), eq("task-1"), eq("purchase-form"), eq("form-1"), anyList(), eq(actions)))
        .thenReturn(Try.success(schemaNode));

    var result = service.getTaskForm("task-1");

    assertTrue(result.isSuccess());
    assertEquals(schemaNode, result.get());
    verify(flowFormService).getTaskFormSchema("instance-1", "task-1", "purchase-form", "form-1", permissions, actions);
  }

  @Test
  void getTaskFormExposesCompleteActionForHandleNode() {
    TaskService taskService = mock(TaskService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);
    FlowFormService flowFormService = mock(FlowFormService.class);
    Activiti6FlowTaskService service = new Activiti6FlowTaskService(taskService, Collections.emptyList());
    service.repositoryService = repositoryService;
    service.flowDefinitionService = flowDefinitionService;
    service.flowFormServices = Collections.singletonList(flowFormService);

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    ProcessDefinition processDefinition = mock(ProcessDefinition.class);
    SchemaNode schemaNode = new SchemaNode();
    schemaNode.setType("form");
    List<NodeFormFieldPermission> permissions = Collections.singletonList(permission("amount", "定义金额"));

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    when(task.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(task.getTaskDefinitionKey()).thenReturn("handle-a");
    when(task.getFormKey()).thenReturn("purchase-form");
    when(repositoryService.getProcessDefinition("process-definition-1")).thenReturn(processDefinition);
    when(processDefinition.getKey()).thenReturn("flow-a");
    when(flowDefinitionService.getFlowNodes("flow-a")).thenReturn(Collections.singletonList(handleNode("handle-a", permissions)));
    when(taskService.getVariable("task-1", Constants.TASK_FORM_TYPE_KEY, String.class)).thenReturn("default");
    when(taskService.getVariable("task-1", Constants.TASK_FORM_ID_KEY, String.class)).thenReturn("form-1");
    when(flowFormService.getType()).thenReturn("default");
    when(flowFormService.getTaskFormSchema(
        "instance-1",
        "task-1",
        "purchase-form",
        "form-1",
        permissions,
        Collections.singletonList(Constants.Value.COMPLETE)))
        .thenReturn(Try.success(schemaNode));

    var result = service.getTaskForm("task-1");

    assertTrue(result.isSuccess());
    assertEquals(schemaNode, result.get());
    verify(flowFormService).getTaskFormSchema(
        "instance-1",
        "task-1",
        "purchase-form",
        "form-1",
        permissions,
        Collections.singletonList(Constants.Value.COMPLETE));
  }

  @Test
  void getTaskFormFailsWhenNoMatchingFormServiceExists() {
    TaskService taskService = mock(TaskService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);
    Activiti6FlowTaskService service = new Activiti6FlowTaskService(taskService, Collections.emptyList());
    service.repositoryService = repositoryService;
    service.flowDefinitionService = flowDefinitionService;
    service.flowFormServices = Collections.emptyList();

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    ProcessDefinition processDefinition = mock(ProcessDefinition.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    when(task.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(task.getTaskDefinitionKey()).thenReturn("approve-a");
    when(task.getFormKey()).thenReturn("purchase-form");
    when(repositoryService.getProcessDefinition("process-definition-1")).thenReturn(processDefinition);
    when(processDefinition.getKey()).thenReturn("flow-a");
    when(flowDefinitionService.getFlowNodes("flow-a"))
        .thenReturn(Collections.singletonList(approvalNode("approve-a", Collections.emptyList(), Collections.emptyList())));
    when(taskService.getVariable("task-1", Constants.TASK_FORM_TYPE_KEY, String.class)).thenReturn("default");
    when(taskService.getVariable("task-1", Constants.TASK_FORM_ID_KEY, String.class)).thenReturn("form-1");

    var result = service.getTaskForm("task-1");

    assertTrue(result.isFailure());
    assertEquals("未找到对应的表单服务", result.getCause().getMessage());
  }

  @Test
  void getTaskFormFallsBackToEmptyActionsAndPermissionsWhenProcessDefinitionMissing() {
    TaskService taskService = mock(TaskService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);
    FlowFormService flowFormService = mock(FlowFormService.class);
    Activiti6FlowTaskService service = new Activiti6FlowTaskService(taskService, Collections.emptyList());
    service.repositoryService = repositoryService;
    service.flowDefinitionService = flowDefinitionService;
    service.flowFormServices = Collections.singletonList(flowFormService);

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    SchemaNode schemaNode = new SchemaNode();
    schemaNode.setType("form");

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    when(task.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(task.getTaskDefinitionKey()).thenReturn("approve-a");
    when(task.getFormKey()).thenReturn("purchase-form");
    when(repositoryService.getProcessDefinition("process-definition-1")).thenReturn(null);
    when(taskService.getVariable("task-1", Constants.TASK_FORM_TYPE_KEY, String.class)).thenReturn("default");
    when(taskService.getVariable("task-1", Constants.TASK_FORM_ID_KEY, String.class)).thenReturn("form-1");
    when(flowFormService.getType()).thenReturn("default");
    when(flowFormService.getTaskFormSchema("instance-1", "task-1", "purchase-form", "form-1",
        Collections.emptyList(), Collections.emptyList())).thenReturn(Try.success(schemaNode));

    var result = service.getTaskForm("task-1");

    assertTrue(result.isSuccess());
    assertEquals(schemaNode, result.get());
    verify(flowFormService).getTaskFormSchema("instance-1", "task-1", "purchase-form", "form-1",
        Collections.emptyList(), Collections.emptyList());
  }

  @Test
  void getTaskFormFailsWithPlatformExceptionWhenTaskMissing() {
    TaskService taskService = mock(TaskService.class);
    Activiti6FlowTaskService service = new Activiti6FlowTaskService(taskService, Collections.emptyList());

    TaskQuery taskQuery = mock(TaskQuery.class);
    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-missing")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(null);

    var result = service.getTaskForm("task-missing");

    assertTrue(result.isFailure());
    assertTrue(result.getCause() instanceof PlatformException);
    assertEquals("任务不存在", result.getCause().getMessage());
  }

  @Test
  void getTaskFormFailsWithPlatformExceptionWhenTaskDoesNotDefineForm() {
    TaskService taskService = mock(TaskService.class);
    Activiti6FlowTaskService service = new Activiti6FlowTaskService(taskService, Collections.emptyList());

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(task.getFormKey()).thenReturn(null);

    var result = service.getTaskForm("task-1");

    assertTrue(result.isFailure());
    assertTrue(result.getCause() instanceof PlatformException);
    assertEquals("任务表单不存在", result.getCause().getMessage());
  }

  @Test
  void getTaskFormFailsWithPlatformExceptionWhenSchemaIsEmpty() {
    TaskService taskService = mock(TaskService.class);
    RepositoryService repositoryService = mock(RepositoryService.class);
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);
    FlowFormService flowFormService = mock(FlowFormService.class);
    Activiti6FlowTaskService service = new Activiti6FlowTaskService(taskService, Collections.emptyList());
    service.repositoryService = repositoryService;
    service.flowDefinitionService = flowDefinitionService;
    service.flowFormServices = Collections.singletonList(flowFormService);

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    ProcessDefinition processDefinition = mock(ProcessDefinition.class);

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
    when(taskQuery.singleResult()).thenReturn(task);
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    when(task.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(task.getTaskDefinitionKey()).thenReturn("approve-a");
    when(task.getFormKey()).thenReturn("purchase-form");
    when(repositoryService.getProcessDefinition("process-definition-1")).thenReturn(processDefinition);
    when(processDefinition.getKey()).thenReturn("flow-a");
    when(flowDefinitionService.getFlowNodes("flow-a"))
        .thenReturn(Collections.singletonList(approvalNode("approve-a", Collections.emptyList(), Collections.emptyList())));
    when(taskService.getVariable("task-1", Constants.TASK_FORM_TYPE_KEY, String.class)).thenReturn("default");
    when(taskService.getVariable("task-1", Constants.TASK_FORM_ID_KEY, String.class)).thenReturn("form-1");
    when(flowFormService.getType()).thenReturn("default");
    when(flowFormService.getTaskFormSchema("instance-1", "task-1", "purchase-form", "form-1", Collections.emptyList(), Collections.emptyList()))
        .thenReturn(Try.success(null));

    var result = service.getTaskForm("task-1");

    assertTrue(result.isFailure());
    assertTrue(result.getCause() instanceof PlatformException);
    assertEquals("表单不存在", result.getCause().getMessage());
  }

  private NodeFormFieldPermission permission(String name, String label) {
    NodeFormFieldPermission permission = new NodeFormFieldPermission();
    permission.setName(name);
    permission.setLabel(label);
    permission.setVisible(true);
    permission.setWritable(true);
    permission.setRequired(true);
    return permission;
  }

  private SingleApprovalNode approvalNode(String id, List<NodeFormFieldPermission> permissions, List<String> actions) {
    SingleApprovalNode approvalNode = new SingleApprovalNode();
    approvalNode.setId(id);
    approvalNode.setPermission(permissions);
    approvalNode.setActions(actions);
    return approvalNode;
  }

  private HandleNode handleNode(String id, List<NodeFormFieldPermission> permissions) {
    HandleNode handleNode = new HandleNode();
    handleNode.setId(id);
    handleNode.setPermission(permissions);
    return handleNode;
  }
}
