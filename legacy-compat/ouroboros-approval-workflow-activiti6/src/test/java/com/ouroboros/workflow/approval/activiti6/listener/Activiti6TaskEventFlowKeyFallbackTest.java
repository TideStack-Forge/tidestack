package com.ouroboros.workflow.approval.activiti6.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.activiti.bpmn.model.UserTask;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.TaskService;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.task.IdentityLink;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.listener.FlowTaskListener;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

class Activiti6TaskEventFlowKeyFallbackTest {
  @Test
  void createMultipleApprovalEventFallsBackToProcessDefinitionKeyWhenFlowKeyMissing() {
    EventCollector collector = installTaskEventCollector();
    CreateMultipleApprovalTaskListener taskListener = newCreateMultipleListener(
        mockRepositoryService("process-definition-child", "child-smoke-flow"),
        collector.dispatcher);

    taskListener.dispatchEvent(mockTask("task-1", "multi-a", null));

    FlowTaskEvent event = collector.listener.lastEvent();
    assertEquals("child-smoke-flow", event.getFlowKey());
    assertEquals(TaskState.CREATED, event.getState());
  }

  @Test
  void createMultipleApprovalEventUsesCurrentTimeWhenCreateTimeMissing() {
    EventCollector collector = installTaskEventCollector();
    CreateMultipleApprovalTaskListener taskListener = newCreateMultipleListener(null, collector.dispatcher);
    taskListener.dispatchEvent(
        mockTask(
            "task-create-null-time",
            "multi-a",
            null,
            "execution-flow",
            Collections.<IdentityLink>emptySet(),
            Collections.singletonList("user-a"),
            null
        )
    );

    FlowTaskEvent event = collector.listener.lastEvent();
    assertNotNull(event.getEventTime());
    assertEquals(TaskState.CREATED, event.getState());
  }

  @Test
  void createSingleApprovalEventUsesCurrentTimeWhenCreateTimeMissing() {
    EventCollector collector = installTaskEventCollector();
    DelegateTask task = mockTask(
        "task-create-null-time",
        "approve-a",
        null,
        "execution-flow",
        Collections.<IdentityLink>emptySet(),
        Collections.singletonList("user-a"),
        null
    );
    HashMap<String, List<String>> selectedAssignees = new HashMap<String, List<String>>();
    selectedAssignees.put("approve-a", Collections.singletonList("user-a"));
    when(task.getVariable(Constants.Variable.USER_SELECTED_ASSIGNEES)).thenReturn(selectedAssignees);

    newCreateSingleListener(null, null, collector.dispatcher).notify(task);

    FlowTaskEvent event = collector.listener.lastEvent();
    assertEquals("execution-flow", event.getFlowKey());
    assertEquals(TaskState.CREATED, event.getState());
    assertNotNull(event.getEventTime());
  }

  @Test
  void createSingleApprovalEventLeavesFlowNodeNullWhenRepositoryServiceMissing() {
    EventCollector collector = installTaskEventCollector();
    DelegateTask task = mockTask(
        "task-flow-node-null",
        "approve-a",
        "execution-flow",
        "execution-flow",
        Collections.<IdentityLink>emptySet(),
        Collections.singletonList("user-a"),
        null
    );
    HashMap<String, List<String>> selectedAssignees = new HashMap<String, List<String>>();
    selectedAssignees.put("approve-a", Collections.singletonList("user-a"));
    when(task.getVariable(Constants.Variable.USER_SELECTED_ASSIGNEES)).thenReturn(selectedAssignees);

    newCreateSingleListener(null, null, collector.dispatcher).notify(task);

    FlowTaskEvent event = collector.listener.lastEvent();
    assertEquals("approve-a", event.getNodeId());
    assertDoesNotThrow(event::getFlowNode);
    assertEquals(null, event.getFlowNode());
  }

  @Test
  void createSingleApprovalNoCandidateModeDoesNotCrashWhenDefaultEngineMissing() {
    EventCollector collector = installTaskEventCollector();
    DelegateTask task = mockTask(
        "task-no-engine",
        "approve-a",
        null,
        "execution-flow",
        Collections.<IdentityLink>emptySet(),
        Collections.<String>emptyList(),
        dateOf(2026, Month.MARCH, 19, 20, 0)
    );
    when(task.getVariable(Constants.Variable.USER_SELECTED_ASSIGNEES)).thenReturn(Collections.emptyMap());

    assertDoesNotThrow(() -> newCreateSingleListener(null, null, collector.dispatcher).notify(task));
    assertEquals(0, collector.listener.eventCount());
  }

  @Test
  void createSingleApprovalNoCandidateModeDoesNotCrashWhenTaskServiceMissing() {
    EventCollector collector = installTaskEventCollector();
    DelegateTask task = mockTask(
        "task-no-task-service",
        "approve-a",
        null,
        "execution-flow",
        Collections.<IdentityLink>emptySet(),
        Collections.<String>emptyList(),
        dateOf(2026, Month.MARCH, 19, 20, 0)
    );
    when(task.getVariable(Constants.Variable.USER_SELECTED_ASSIGNEES)).thenReturn(Collections.emptyMap());

    assertDoesNotThrow(() -> newCreateSingleListener(null, null, collector.dispatcher).notify(task));
    assertEquals(0, collector.listener.eventCount());
  }

  @Test
  void createSingleApprovalEmptySelectedAssigneesStillFallsBackToNoCandidateHandling() {
    EventCollector collector = installTaskEventCollector();
    DelegateTask task = mockTask(
        "task-empty-selected",
        "approve-a",
        null,
        "execution-flow",
        Collections.<IdentityLink>emptySet(),
        Collections.<String>emptyList(),
        dateOf(2026, Month.MARCH, 19, 20, 0)
    );
    HashMap<String, List<String>> selectedAssignees = new HashMap<String, List<String>>();
    selectedAssignees.put("approve-a", Collections.<String>emptyList());
    when(task.getVariable(Constants.Variable.USER_SELECTED_ASSIGNEES)).thenReturn(selectedAssignees);

    assertDoesNotThrow(() -> newCreateSingleListener(null, null, collector.dispatcher).notify(task));
    assertEquals(0, collector.listener.eventCount());
  }

  @Test
  void createSingleApprovalForkedNoCandidateApprovedModeUsesStructuredDefinitionNodeList() {
    EventCollector collector = installTaskEventCollector();
    RepositoryService repositoryService = mock(RepositoryService.class);
    TaskService taskService = mock(TaskService.class);
    DelegateTask task = mockTask(
        "task-forked-no-candidate-approved",
        "approvalnodea",
        null,
        "execution-flow",
        Collections.<IdentityLink>emptySet(),
        Collections.<String>emptyList(),
        dateOf(2026, Month.MARCH, 19, 20, 0)
    );
    HashMap<String, List<String>> selectedAssignees = new HashMap<String, List<String>>();

    selectedAssignees.put("approvalnodea", Collections.<String>emptyList());
    when(task.getVariable(Constants.Variable.USER_SELECTED_ASSIGNEES)).thenReturn(selectedAssignees);
    when(repositoryService.getBpmnModel("process-definition-child")).thenReturn(forkedSingleNoCandidateApprovedBpmnModel("approved"));

    newCreateSingleListener(repositoryService, taskService, collector.dispatcher).notify(task);

    verify(taskService).setAssignee("task-forked-no-candidate-approved", "$approve");
    verify(taskService).setVariableLocal("task-forked-no-candidate-approved", Constants.Variable.LOCAL_APPROVAL_ACTION, Constants.Value.APPROVED);
    verify(taskService).complete("task-forked-no-candidate-approved");
    assertEquals(0, collector.listener.eventCount());
  }

  @Test
  void createSingleApprovalEventLeavesFlowNodeNullWhenDefaultEngineMissing() {
    EventCollector collector = installTaskEventCollector();
    DelegateTask task = mockTask(
        "task-no-engine-flow-node",
        "approve-a",
        "execution-flow",
        "execution-flow",
        Collections.<IdentityLink>emptySet(),
        Collections.singletonList("user-a"),
        dateOf(2026, Month.MARCH, 19, 20, 0)
    );
    HashMap<String, List<String>> selectedAssignees = new HashMap<String, List<String>>();
    selectedAssignees.put("approve-a", Collections.singletonList("user-a"));
    when(task.getVariable(Constants.Variable.USER_SELECTED_ASSIGNEES)).thenReturn(selectedAssignees);

    newCreateSingleListener(null, null, collector.dispatcher).notify(task);

    FlowTaskEvent event = collector.listener.lastEvent();
    assertEquals("approve-a", event.getNodeId());
    assertDoesNotThrow(event::getFlowNode);
    assertEquals(null, event.getFlowNode());
  }

  @Test
  void singleCompletedEventFallsBackToProcessDefinitionKeyWhenFlowKeyMissing() {
    EventCollector collector = installTaskEventCollector();
    SingleApproveCompletedTaskListener taskListener = newSingleCompletedListener(
        mockRepositoryService("process-definition-child", "child-smoke-flow"),
        null,
        collector.dispatcher);
    taskListener.dispatchEvent(mockTask("task-2", "child-approve", null), true);

    FlowTaskEvent event = collector.listener.lastEvent();
    assertEquals("child-smoke-flow", event.getFlowKey());
    assertEquals(TaskState.APPROVED, event.getState());
  }

  @Test
  void singleCompletedEventUsesExecutionFlowKeyWhenTaskVariableMissing() {
    EventCollector collector = installTaskEventCollector();
    SingleApproveCompletedTaskListener taskListener = newSingleCompletedListener(null, null, collector.dispatcher);
    taskListener.dispatchEvent(mockTask("task-3", "child-approve", null, "execution-flow"), true);

    FlowTaskEvent event = collector.listener.lastEvent();
    assertEquals("execution-flow", event.getFlowKey());
    assertEquals(TaskState.APPROVED, event.getState());
  }

  @Test
  void singleCompletedEventIgnoresNullCandidateLinksAndNullUserIds() {
    EventCollector collector = installTaskEventCollector();
    IdentityLink nullUserLink = mock(IdentityLink.class);
    IdentityLink candidate = mock(IdentityLink.class);

    when(nullUserLink.getUserId()).thenReturn(null);
    when(candidate.getUserId()).thenReturn("user-a");

    SingleApproveCompletedTaskListener taskListener = newSingleCompletedListener(null, null, collector.dispatcher);
    taskListener.dispatchEvent(
        mockTask(
            "task-4",
            "child-approve",
            null,
            "execution-flow",
            linkedCandidates(null, nullUserLink, candidate),
            Collections.singletonList("user-a"),
            dateOf(2026, Month.MARCH, 19, 20, 0)
        ),
        true
    );

    FlowTaskEvent event = collector.listener.lastEvent();
    assertEquals(Collections.singletonList("user-a"), event.getCandidateUsers());
  }

  @Test
  void multipleCompletedEventDefaultsMissingCandidateListToEmptyList() {
    EventCollector collector = installTaskEventCollector();
    MultipleApprovalCompletedTaskListener taskListener = newMultipleCompletedListener(null, null, collector.dispatcher);
    taskListener.dispatchProcessingEvent(
        mockTask(
            "task-5",
            "multi-a",
            null,
            "execution-flow",
            Collections.<IdentityLink>emptySet(),
            null,
            dateOf(2026, Month.MARCH, 19, 20, 0)
        ),
        "approve",
        true
    );

    FlowTaskEvent event = collector.listener.lastEvent();
    assertEquals(Collections.emptyList(), event.getCandidateUsers());
  }

  private RepositoryService mockRepositoryService(String processDefinitionId, String flowKey) {
    RepositoryService repositoryService = mock(RepositoryService.class);
    ProcessDefinition processDefinition = mock(ProcessDefinition.class);

    when(processDefinition.getKey()).thenReturn(flowKey);
    when(repositoryService.getProcessDefinition(processDefinitionId)).thenReturn(processDefinition);
    return repositoryService;
  }

  private org.activiti.bpmn.model.BpmnModel forkedSingleNoCandidateApprovedBpmnModel(String noCandidateMode) {
    org.activiti.bpmn.model.BpmnModel model = new org.activiti.bpmn.model.BpmnModel();
    org.activiti.bpmn.model.Process process = new org.activiti.bpmn.model.Process();
    UserTask userTask = new UserTask();

    userTask.setId("approvalnodea");
    process.setId("child-smoke-flow");
    process.addFlowElement(userTask);
    com.ouroboros.workflow.approval.activiti6.util.BpmnUtil.saveFlowDefinitionRawJson(process, forkedSingleNoCandidateApprovedFlowJson(noCandidateMode));
    model.addProcess(process);
    return model;
  }

  private String forkedSingleNoCandidateApprovedFlowJson(String noCandidateMode) {
    return "{"
        + "\"formatVersion\":2,"
        + "\"id\":\"child-smoke-flow\","
        + "\"name\":\"单签无候选人并行通过共享场景\","
        + "\"category\":\"\","
        + "\"icon\":\"shenpi\","
        + "\"description\":\"\","
        + "\"formType\":\"default\","
        + "\"formKey\":\"runtime-form\","
        + "\"nodes\":["
        + "{\"type\":\"发起节点\",\"name\":\"发起节点\",\"id\":\"startnode\",\"permission\":[]},"
        + "{\"type\":\"审批节点\",\"name\":\"自动通过审批\",\"id\":\"approvalnodea\",\"subType\":\"单签\","
        + "\"candidates\":[],\"permission\":[],\"actions\":[\"reject\"],\"noCandidateMode\":\"" + noCandidateMode + "\"},"
        + "{\"type\":\"审批节点\",\"name\":\"人工审批\",\"id\":\"approvalnodeb\",\"subType\":\"单签\","
        + "\"candidates\":[],\"permission\":[],\"actions\":[\"reject\"]},"
        + "{\"type\":\"JoinNode\",\"name\":\"并行汇聚\",\"id\":\"join-a\",\"joinType\":\"OR\"},"
        + "{\"type\":\"结束节点\",\"name\":\"结束\",\"id\":\"end-a\","
        + "\"end\":{\"content\":\"done\",\"channel\":[\"message\"],\"candidates\":[]}}"
        + "],"
        + "\"edges\":["
        + "{\"id\":\"e1\",\"type\":\"SEQUENCE\",\"sourceId\":\"startnode\",\"targetId\":\"approvalnodea\",\"name\":\"自动通过分支\",\"order\":1},"
        + "{\"id\":\"e2\",\"type\":\"SEQUENCE\",\"sourceId\":\"startnode\",\"targetId\":\"approvalnodeb\",\"name\":\"人工审批分支\",\"order\":2},"
        + "{\"id\":\"e3\",\"type\":\"SEQUENCE\",\"sourceId\":\"approvalnodea\",\"targetId\":\"join-a\"},"
        + "{\"id\":\"e4\",\"type\":\"SEQUENCE\",\"sourceId\":\"approvalnodeb\",\"targetId\":\"join-a\"},"
        + "{\"id\":\"e5\",\"type\":\"SEQUENCE\",\"sourceId\":\"join-a\",\"targetId\":\"end-a\"}]"
        + "}";
  }

  private EventCollector installTaskEventCollector() {
    TaskEventDispatcher dispatcher = new TaskEventDispatcher();
    CapturingFlowTaskListener listener = new CapturingFlowTaskListener();
    setField(dispatcher, "listeners", Collections.singletonList(listener));
    return new EventCollector(dispatcher, listener);
  }

  private CreateMultipleApprovalTaskListener newCreateMultipleListener(
      RepositoryService repositoryService,
      TaskEventDispatcher dispatcher
  ) {
    CreateMultipleApprovalTaskListener listener = new CreateMultipleApprovalTaskListener();
    setField(listener, "repositoryService", repositoryService);
    setField(listener, "taskEventDispatcher", dispatcher);
    return listener;
  }

  private CreateSingleApprovalTaskListener newCreateSingleListener(
      RepositoryService repositoryService,
      TaskService taskService,
      TaskEventDispatcher dispatcher
  ) {
    CreateSingleApprovalTaskListener listener = new CreateSingleApprovalTaskListener();
    setField(listener, "repositoryService", repositoryService);
    setField(listener, "taskService", taskService);
    setField(listener, "taskEventDispatcher", dispatcher);
    return listener;
  }

  private SingleApproveCompletedTaskListener newSingleCompletedListener(
      RepositoryService repositoryService,
      TaskService taskService,
      TaskEventDispatcher dispatcher
  ) {
    SingleApproveCompletedTaskListener listener = new SingleApproveCompletedTaskListener();
    setField(listener, "repositoryService", repositoryService);
    setField(listener, "taskService", taskService);
    setField(listener, "taskEventDispatcher", dispatcher);
    return listener;
  }

  private MultipleApprovalCompletedTaskListener newMultipleCompletedListener(
      RepositoryService repositoryService,
      TaskService taskService,
      TaskEventDispatcher dispatcher
  ) {
    MultipleApprovalCompletedTaskListener listener = new MultipleApprovalCompletedTaskListener();
    setField(listener, "repositoryService", repositoryService);
    setField(listener, "taskService", taskService);
    setField(listener, "taskEventDispatcher", dispatcher);
    return listener;
  }

  private DelegateTask mockTask(String taskId, String nodeId, String flowKey) {
    return mockTask(taskId, nodeId, flowKey, flowKey);
  }

  private DelegateTask mockTask(String taskId, String nodeId, String taskFlowKey, String executionFlowKey) {
    return mockTask(
        taskId,
        nodeId,
        taskFlowKey,
        executionFlowKey,
        Collections.<IdentityLink>emptySet(),
        Collections.singletonList("user-a"),
        dateOf(2026, Month.MARCH, 19, 20, 0)
    );
  }

  private DelegateTask mockTask(String taskId, String nodeId, String taskFlowKey, String executionFlowKey,
      Set<IdentityLink> candidates, List<String> multipleAssignees, Date createTime) {
    DelegateTask task = mock(DelegateTask.class);
    DelegateExecution execution = mock(DelegateExecution.class);
    UserTask userTask = mock(UserTask.class);

    when(task.getId()).thenReturn(taskId);
    when(task.getName()).thenReturn("审批任务");
    when(task.getAssignee()).thenReturn("user-a");
    when(task.getCandidates()).thenReturn(candidates);
    when(task.getExecution()).thenReturn(execution);
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    when(task.getProcessDefinitionId()).thenReturn("process-definition-child");
    when(task.getCreateTime()).thenReturn(createTime);
    when(task.getVariable(Constants.Variable.MULTIPLE_APPROVAL_ASSIGNEE_LIST)).thenReturn(multipleAssignees);
    when(task.getVariable(Constants.Variable.MULTIPLE_APPROVAL_ASSIGNEE_LIST, List.class)).thenReturn(multipleAssignees);
    when(execution.getCurrentFlowElement()).thenReturn(userTask);
    when(execution.getVariable(Constants.Variable.PROCESS_FLOW_KEY, String.class)).thenReturn(executionFlowKey);
    when(task.getVariable(Constants.Variable.PROCESS_FLOW_KEY, String.class)).thenReturn(taskFlowKey);
    when(userTask.getId()).thenReturn(nodeId);
    return task;
  }

  private Set<IdentityLink> linkedCandidates(IdentityLink... links) {
    return new LinkedHashSet<IdentityLink>(Arrays.asList(links));
  }

  private Date dateOf(int year, Month month, int dayOfMonth, int hour, int minute) {
    return Date.from(LocalDateTime.of(year, month, dayOfMonth, hour, minute)
        .atZone(ZoneId.systemDefault())
        .toInstant());
  }

  private void setField(Object target, String fieldName, Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to set field " + fieldName, e);
    }
  }

  private static final class CapturingFlowTaskListener implements FlowTaskListener {
    private FlowTaskEvent lastEvent;
    private int eventCount;

    @Override
    public void onEvent(FlowTaskEvent event) {
      lastEvent = event;
      eventCount++;
    }

    private FlowTaskEvent lastEvent() {
      if (lastEvent == null) {
        throw new AssertionError("No task event captured");
      }
      return lastEvent;
    }

    private int eventCount() {
      return eventCount;
    }
  }

  private static final class EventCollector {
    private final TaskEventDispatcher dispatcher;
    private final CapturingFlowTaskListener listener;

    private EventCollector(TaskEventDispatcher dispatcher, CapturingFlowTaskListener listener) {
      this.dispatcher = dispatcher;
      this.listener = listener;
    }
  }
}
