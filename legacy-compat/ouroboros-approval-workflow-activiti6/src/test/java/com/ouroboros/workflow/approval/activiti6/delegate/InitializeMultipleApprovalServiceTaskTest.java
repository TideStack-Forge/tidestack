package com.ouroboros.workflow.approval.activiti6.delegate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.ServiceTask;
import org.activiti.bpmn.model.UserTask;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.impl.el.FixedValue;
import org.activiti.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.activiti6.util.BpmnUtil;
import com.ouroboros.workflow.approval.definition.Candidate;
import com.ouroboros.workflow.approval.definition.candidates.UserCandidate;

class InitializeMultipleApprovalServiceTaskTest {
  @Test
  void executeNoCandidateModeDoesNotCrashWhenDefaultEngineMissing() {
    DelegateExecution execution = mock(DelegateExecution.class);
    ServiceTask serviceTask = new ServiceTask();
    InitializeMultipleApprovalServiceTask delegate = newDelegate(null);

    serviceTask.setId("multi-a_reset_multiple_approval_service_task");

    when(execution.getCurrentFlowElement()).thenReturn(serviceTask);
    when(execution.getVariable(Constants.Variable.USER_SELECTED_ASSIGNEES, Map.class)).thenReturn(Collections.emptyMap());
    when(execution.getVariable(Constants.Variable.PROCESS_STARTER)).thenReturn("starter-1");
    when(execution.getProcessDefinitionId()).thenReturn("process-definition-child");

    setField(delegate, "multipleApprovalTaskId", new FixedValue("multi-a"));
    setField(delegate, "percentage", new FixedValue("100"));
    setField(delegate, "isSequential", new FixedValue("false"));

    assertDoesNotThrow(() -> delegate.execute(execution));
  }

  @Test
  void executeCandidatePlaceholderDoesNotCrashWhenStarterMissing() {
    DelegateExecution execution = mock(DelegateExecution.class);
    ServiceTask serviceTask = new ServiceTask();
    InitializeMultipleApprovalServiceTask delegate = newDelegate(null);
    UserCandidate candidate = new UserCandidate();

    candidate.setUsers(Collections.singletonList("${starter}"));
    serviceTask.setId("multi-a_reset_multiple_approval_service_task");
    BpmnUtil.saveCandidates(serviceTask, Collections.singletonList(candidate));

    when(execution.getCurrentFlowElement()).thenReturn(serviceTask);
    when(execution.getVariable(Constants.Variable.USER_SELECTED_ASSIGNEES, Map.class)).thenReturn(Collections.emptyMap());
    when(execution.getVariable(Constants.Variable.PROCESS_STARTER)).thenReturn(null);
    when(execution.getVariable(Constants.Variable.PROCESS_FLOW_KEY, String.class)).thenReturn("flow-a");
    when(execution.getProcessInstanceId()).thenReturn("instance-child-2");

    setField(delegate, "multipleApprovalTaskId", new FixedValue("multi-a"));
    setField(delegate, "percentage", new FixedValue("100"));
    setField(delegate, "isSequential", new FixedValue("false"));

    assertDoesNotThrow(() -> delegate.execute(execution));
  }

  @Test
  void executeWithCandidatesDoesNotDispatchTaskEventDirectly() {
    DelegateExecution execution = mock(DelegateExecution.class);
    ServiceTask serviceTask = new ServiceTask();
    InitializeMultipleApprovalServiceTask delegate = newDelegate(null);
    UserCandidate candidate = new UserCandidate();

    candidate.setUsers(Collections.singletonList("user-a"));
    serviceTask.setId("multi-a_reset_multiple_approval_service_task");
    BpmnUtil.saveCandidates(serviceTask, Collections.<Candidate>singletonList(candidate));

    when(execution.getCurrentFlowElement()).thenReturn(serviceTask);
    when(execution.getVariable(Constants.Variable.USER_SELECTED_ASSIGNEES, Map.class)).thenReturn(Collections.emptyMap());
    when(execution.getVariable(Constants.Variable.PROCESS_STARTER)).thenReturn("starter-1");
    when(execution.getVariable(Constants.Variable.PROCESS_FLOW_KEY, String.class)).thenReturn("flow-a");
    when(execution.getProcessInstanceId()).thenReturn("instance-child-3");

    setField(delegate, "multipleApprovalTaskId", new FixedValue("multi-a"));
    setField(delegate, "percentage", new FixedValue("100"));
    setField(delegate, "isSequential", new FixedValue("false"));

    delegate.execute(execution);

    verify(execution).setVariable(eq(sessionVariable("multi-a")), anyString());
    verify(execution).setVariable(assigneeListVariable("multi-a"), Collections.singletonList("user-a"));
  }

  @Test
  void executeExplicitEmptySelectedAssigneesUsesNoCandidateModeInsteadOfConfiguredCandidates() {
    RepositoryService repositoryService = mockRepositoryService(
        "process-definition-child",
        "child-smoke-flow",
        bpmnModelWithNoCandidateMode("approved"));
    DelegateExecution execution = mock(DelegateExecution.class);
    ServiceTask serviceTask = new ServiceTask();
    InitializeMultipleApprovalServiceTask delegate = newDelegate(repositoryService);
    UserCandidate candidate = new UserCandidate();
    HashMap<String, List<String>> selected = new HashMap<String, List<String>>();

    candidate.setUsers(Collections.singletonList("fallback-user"));
    serviceTask.setId("multi-a_reset_multiple_approval_service_task");
    BpmnUtil.saveCandidates(serviceTask, Collections.<Candidate>singletonList(candidate));
    selected.put("multi-a", Collections.<String>emptyList());

    when(execution.getCurrentFlowElement()).thenReturn(serviceTask);
    when(execution.getVariable(Constants.Variable.USER_SELECTED_ASSIGNEES, Map.class)).thenReturn(selected);
    when(execution.getVariable(Constants.Variable.PROCESS_STARTER)).thenReturn("starter-1");
    when(execution.getProcessDefinitionId()).thenReturn("process-definition-child");

    setField(delegate, "multipleApprovalTaskId", new FixedValue("multi-a"));
    setField(delegate, "percentage", new FixedValue("100"));
    setField(delegate, "isSequential", new FixedValue("false"));

    delegate.execute(execution);

    verify(execution).setVariable(eq(sessionVariable("multi-a")), anyString());
    verify(execution).setVariable(assigneeListVariable("multi-a"), Collections.emptyList());
    verify(execution).setVariable("multi-a" + Constants.Variable.MULTIPLE_APPROVAL_COMPLETED_SUFFIX, true);
    verify(execution).setVariable("multi-a" + Constants.Variable.APPROVED_SUFFIX, true);
  }

  @Test
  void executeForkedNoCandidateApprovedModeUsesStructuredDefinitionNodeList() {
    RepositoryService repositoryService = mockRepositoryService(
        "process-definition-child",
        "child-smoke-flow",
        forkedBpmnModelWithNoCandidateMode("approved"));
    DelegateExecution execution = mock(DelegateExecution.class);
    ServiceTask serviceTask = new ServiceTask();
    InitializeMultipleApprovalServiceTask delegate = newDelegate(repositoryService);
    UserCandidate candidate = new UserCandidate();
    HashMap<String, List<String>> selected = new HashMap<String, List<String>>();

    candidate.setUsers(Collections.singletonList("fallback-user"));
    serviceTask.setId("multi-auto_reset_multiple_approval_service_task");
    BpmnUtil.saveCandidates(serviceTask, Collections.<Candidate>singletonList(candidate));
    selected.put("multi-auto", Collections.<String>emptyList());

    when(execution.getCurrentFlowElement()).thenReturn(serviceTask);
    when(execution.getVariable(Constants.Variable.USER_SELECTED_ASSIGNEES, Map.class)).thenReturn(selected);
    when(execution.getVariable(Constants.Variable.PROCESS_STARTER)).thenReturn("starter-1");
    when(execution.getProcessDefinitionId()).thenReturn("process-definition-child");

    setField(delegate, "multipleApprovalTaskId", new FixedValue("multi-auto"));
    setField(delegate, "percentage", new FixedValue("50"));
    setField(delegate, "isSequential", new FixedValue("false"));

    delegate.execute(execution);

    verify(execution).setVariable(eq(sessionVariable("multi-auto")), anyString());
    verify(execution).setVariable(assigneeListVariable("multi-auto"), Collections.emptyList());
    verify(execution).setVariable("multi-auto" + Constants.Variable.MULTIPLE_APPROVAL_COMPLETED_SUFFIX, true);
    verify(execution).setVariable("multi-auto" + Constants.Variable.APPROVED_SUFFIX, true);
  }

  private InitializeMultipleApprovalServiceTask newDelegate(RepositoryService repositoryService) {
    InitializeMultipleApprovalServiceTask delegate = new InitializeMultipleApprovalServiceTask();
    setField(delegate, "repositoryService", repositoryService);
    return delegate;
  }

  private RepositoryService mockRepositoryService(String processDefinitionId, String flowKey, BpmnModel bpmnModel) {
    RepositoryService repositoryService = mock(RepositoryService.class);
    ProcessDefinition processDefinition = mock(ProcessDefinition.class);

    when(processDefinition.getKey()).thenReturn(flowKey);
    when(repositoryService.getProcessDefinition(processDefinitionId)).thenReturn(processDefinition);
    if (bpmnModel != null) {
      when(repositoryService.getBpmnModel(processDefinitionId)).thenReturn(bpmnModel);
    }
    return repositoryService;
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

  private BpmnModel bpmnModelWithNoCandidateMode(String noCandidateMode) {
    BpmnModel model = new BpmnModel();
    org.activiti.bpmn.model.Process process = new org.activiti.bpmn.model.Process();
    UserTask userTask = new UserTask();

    userTask.setId("multi-a");
    process.setId("child-smoke-flow");
    process.addFlowElement(userTask);
    BpmnUtil.saveFlowDefinitionRawJson(process, minimalMultipleApprovalJson(noCandidateMode));
    model.addProcess(process);
    return model;
  }

  private BpmnModel forkedBpmnModelWithNoCandidateMode(String noCandidateMode) {
    BpmnModel model = new BpmnModel();
    org.activiti.bpmn.model.Process process = new org.activiti.bpmn.model.Process();
    UserTask userTask = new UserTask();

    userTask.setId("multi-auto");
    process.setId("child-smoke-flow");
    process.addFlowElement(userTask);
    BpmnUtil.saveFlowDefinitionRawJson(process, forkedMultipleApprovalJson(noCandidateMode));
    model.addProcess(process);
    return model;
  }

  private String minimalMultipleApprovalJson(String noCandidateMode) {
    return "{"
        + "\"formatVersion\":2,"
        + "\"id\":\"child-smoke-flow\","
        + "\"name\":\"多人审批\","
        + "\"formType\":\"default\","
        + "\"nodes\":["
        + "{\"type\":\"发起节点\",\"name\":\"发起节点\",\"id\":\"start-a\",\"permission\":[]},"
        + "{\"type\":\"审批节点\",\"name\":\"多人审批\",\"id\":\"multi-a\",\"subType\":\"会签\","
        + "\"candidates\":[],\"permission\":[],\"multipleMode\":\"并行\",\"percentage\":100,"
        + "\"rejectAction\":\"reject\",\"noCandidateMode\":\"" + noCandidateMode + "\"}"
        + "],"
        + "\"edges\":[{\"id\":\"e1\",\"type\":\"SEQUENCE\",\"sourceId\":\"start-a\",\"targetId\":\"multi-a\"}]"
        + "}";
  }

  private String forkedMultipleApprovalJson(String noCandidateMode) {
    return "{"
        + "\"formatVersion\":2,"
        + "\"id\":\"child-smoke-flow\","
        + "\"name\":\"会签无候选人并行通过共享场景\","
        + "\"formType\":\"default\","
        + "\"nodes\":["
        + "{\"type\":\"发起节点\",\"name\":\"发起节点\",\"id\":\"startnode\",\"permission\":[]},"
        + "{\"type\":\"审批节点\",\"name\":\"自动通过会签\",\"id\":\"multi-auto\",\"subType\":\"会签\","
        + "\"candidates\":[],\"permission\":[],\"multipleMode\":\"并行\",\"percentage\":50,"
        + "\"rejectAction\":\"reject\",\"noCandidateMode\":\"" + noCandidateMode + "\"},"
        + "{\"type\":\"审批节点\",\"name\":\"人工会签\",\"id\":\"multi-manual\",\"subType\":\"会签\","
        + "\"candidates\":[],\"permission\":[],\"multipleMode\":\"并行\",\"percentage\":50,"
        + "\"rejectAction\":\"reject\"},"
        + "{\"type\":\"JoinNode\",\"name\":\"并行汇聚\",\"id\":\"join-a\",\"joinType\":\"OR\"},"
        + "{\"type\":\"结束节点\",\"name\":\"结束\",\"id\":\"end-a\","
        + "\"end\":{\"content\":\"done\",\"channel\":[\"message\"],\"candidates\":[]}}"
        + "],"
        + "\"edges\":["
        + "{\"id\":\"e1\",\"type\":\"SEQUENCE\",\"sourceId\":\"startnode\",\"targetId\":\"multi-auto\",\"name\":\"自动通过分支\",\"order\":1},"
        + "{\"id\":\"e2\",\"type\":\"SEQUENCE\",\"sourceId\":\"startnode\",\"targetId\":\"multi-manual\",\"name\":\"人工审批分支\",\"order\":2},"
        + "{\"id\":\"e3\",\"type\":\"SEQUENCE\",\"sourceId\":\"multi-auto\",\"targetId\":\"join-a\"},"
        + "{\"id\":\"e4\",\"type\":\"SEQUENCE\",\"sourceId\":\"multi-manual\",\"targetId\":\"join-a\"},"
        + "{\"id\":\"e5\",\"type\":\"SEQUENCE\",\"sourceId\":\"join-a\",\"targetId\":\"end-a\"}]"
        + "}";
  }

  private String assigneeListVariable(String nodeId) {
    return nodeId + "_" + Constants.Variable.MULTIPLE_APPROVAL_ASSIGNEE_LIST;
  }

  private String sessionVariable(String nodeId) {
    return nodeId + Constants.Variable.MULTIPLE_APPROVAL_SESSION;
  }
}
