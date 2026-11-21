package com.ouroboros.workflow.approval.activiti6.service;

import static com.ouroboros.workflow.approval.activiti6.util.BpmnUtil.readStructuredFlowDefinition;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.activiti.bpmn.model.Process;
import org.activiti.engine.FormService;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import io.vavr.Tuple;
import io.vavr.control.Either;
import io.vavr.control.Try;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Dates;
import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.UserSelectAssigneeRangeChecker;
import com.ouroboros.workflow.approval.FlowFormService;
import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.NodeFormFieldPermission;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;

@Service
public class FlowInstanceCommandService {
  List<FlowFormService> flowFormServices = Collections.emptyList();
  FormService formService;
  TaskService taskService;
  RepositoryService repositoryService;
  RuntimeService runtimeService;
  HistoryService historyService;
  FlowDefinitionService flowDefinitionService;
  InstanceEventDispatcher instanceEventDispatcher;

  @Autowired
  public FlowInstanceCommandService(@Autowired(required = false) List<FlowFormService> flowFormServices,
                                    FormService formService,
                                    TaskService taskService,
                                    RepositoryService repositoryService,
                                    RuntimeService runtimeService,
                                    HistoryService historyService,
                                    FlowDefinitionService flowDefinitionService,
                                    InstanceEventDispatcher instanceEventDispatcher) {
    this.flowFormServices = flowFormServices == null ? Collections.emptyList() : flowFormServices;
    this.formService = formService;
    this.taskService = taskService;
    this.repositoryService = repositoryService;
    this.runtimeService = runtimeService;
    this.historyService = historyService;
    this.flowDefinitionService = flowDefinitionService;
    this.instanceEventDispatcher = instanceEventDispatcher;
  }

  public Try<String> start(String flowKey,
                           String userId,
                           Map<String, List<String>> taskAssignee,
                           Map<String, Object> instanceVariables,
                           Map<String, Object> formData,
                           CompletableFuture<Void> initTaskDeferred) {
    var definition = getProcessDefinition(flowKey);
    if (definition.isLeft()) {
      return Try.failure(definition.getLeft());
    }

    var process = getProcess(definition.get());
    var flowDefinition = readStructuredFlowDefinition(process);
    var finalTaskAssignee = filterUserSelectAssignee(flowKey, instanceVariables, taskAssignee);
    if (finalTaskAssignee.isFailure()) {
      return Try.failure(finalTaskAssignee.getCause());
    }

    var formType = flowDefinition == null ? "default" : StringUtils.defaultIfBlank(flowDefinition.getFormType(), "default");
    var formKey = flowDefinition == null ? null : flowDefinition.getFormKey();

    var definitionKey = definition.get().getKey();
    var variables = new HashMap<String, Object>();
    variables.put(Constants.Variable.PROCESS_STARTER, userId);
    variables.put(Constants.Variable.PROCESS_FLOW_KEY, flowKey);
    variables.put(Constants.Variable.USER_SELECTED_ASSIGNEES, finalTaskAssignee.get());
    variables.put(Constants.TASK_FORM_TYPE_KEY, formType);
    if (instanceVariables != null) {
      variables.putAll(instanceVariables);
    }

    List<NodeFormFieldPermission> permissions = getStartNodePermissions(flowDefinition);
    var instance = runtimeService.startProcessInstanceByKey(definitionKey, variables);

    Optional<FlowFormService> flowFormServiceOptional = flowFormServices.stream()
        .filter(x -> x.getType().equalsIgnoreCase(formType))
        .findFirst();
    if (flowFormServiceOptional.isPresent() && ObjectUtils.isNotEmpty(formKey)) {
      FlowFormService flowFormService = flowFormServiceOptional.get();
      Try<String> submitTry = flowFormService.submitInitialForm(instance.getProcessInstanceId(), formKey, userId, permissions, formData);
      if (submitTry.isFailure()) {
        return rollbackFailure(submitTry.getCause());
      }

      String formId = submitTry.get();
      if (StringUtils.isNotEmpty(formId)) {
        runtimeService.setVariable(instance.getProcessInstanceId(), Constants.TASK_FORM_ID_KEY, formId);
      }
    }

    Optional.ofNullable(initTaskDeferred)
        .orElseGet(() -> CompletableFuture.completedFuture(null))
        .thenRun(() -> {
          var task = taskService.createTaskQuery()
              .processInstanceId(instance.getId())
              .singleResult();
          taskService.setAssignee(task.getId(), userId);
          taskService.setVariableLocal(task.getId(), Constants.Variable.TASK_APPROVED, true);

          var formStrValues = formData == null
              ? Collections.<String, String>emptyMap()
              : formData.entrySet().stream()
              .map(entry -> Tuple.of(entry.getKey(), entry.getValue() == null ? null : entry.getValue().toString()))
              .collect(LinkedHashMap<String, String>::new, (map, tuple) -> map.put(tuple._1, tuple._2), LinkedHashMap::putAll);
          formService.submitTaskFormData(task.getId(), formStrValues);
        });

    return Try.of(instance::getId);
  }

  public Try<Void> pause(String instanceId, String userId, String reason) {
    return getInstance(instanceId)
        .map(instance -> {
          if (instance.isSuspended() || instance.isEnded()) {
            return null;
          }
          Map<String, Object> variables = new HashMap<>();
          variables.put(Constants.PROCESS_PAUSE_USER_VARIABLE_NAME, userId);
          variables.put(Constants.PROCESS_PAUSE_REASON_VARIABLE_NAME, reason);
          variables.put(Constants.PROCESS_PAUSE_TIME_VARIABLE_NAME, Dates.now());
          setInstanceVariables(instanceId, variables);
          runtimeService.suspendProcessInstanceById(instanceId);
          dispatchInstanceEvent(instanceId, instance, FlowInstanceEvent.EventType.SUSPENDED, null);
          return null;
        });
  }

  public Try<Void> resume(String instanceId, String userId) {
    return getInstance(instanceId)
        .map(instance -> {
          if (!instance.isSuspended() || instance.isEnded()) {
            return null;
          }
          runtimeService.activateProcessInstanceById(instanceId);
          Map<String, Object> variables = new HashMap<>();
          variables.put(Constants.PROCESS_RESUME_USER_VARIABLE_NAME, userId);
          variables.put(Constants.PROCESS_RESUME_TIME_VARIABLE_NAME, Dates.now());
          setInstanceVariables(instanceId, variables);
          dispatchInstanceEvent(instanceId, instance, FlowInstanceEvent.EventType.RESUMED, null);
          return null;
        });
  }

  public Try<Void> cancel(String instanceId, String userId, String reason) {
    return getInstance(instanceId)
        .map(instance -> {
          var lastTaskId = getLastFinishedTaskId(instanceId);
          var flowKey = getFlowKey(instanceId, instance);
          var starter = getRuntimeStringVariable(instanceId, Constants.Variable.PROCESS_STARTER);
          var variables = new HashMap<String, Object>();
          variables.put(Constants.PROCESS_CANCEL_USER_VARIABLE_NAME, userId);
          variables.put(Constants.PROCESS_CANCEL_REASON_VARIABLE_NAME, reason);
          variables.put(Constants.PROCESS_CANCEL_TIME_VARIABLE_NAME, LocalDateTime.now());
          variables.put(Constants.Variable.PROCESS_APPROVED, false);
          variables.put(Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID, lastTaskId);
          setInstanceVariables(instanceId, variables);

          runtimeService.deleteProcessInstance(instanceId, reason);
          dispatchInstanceEvent(instanceId, flowKey, starter, FlowInstanceEvent.EventType.CANCELLED, lastTaskId);
          return null;
        });
  }

  public Try<Void> complete(String instanceId, String userId, Boolean success, String comment) {
    return getInstance(instanceId)
        .map(instance -> {
          var approvedResult = Boolean.TRUE.equals(success);
          var lastTaskId = getLastFinishedTaskId(instanceId);
          var flowKey = getFlowKey(instanceId, instance);
          var starter = getRuntimeStringVariable(instanceId, Constants.Variable.PROCESS_STARTER);
          var variables = new HashMap<String, Object>();
          variables.put(Constants.PROCESS_CANCEL_USER_VARIABLE_NAME, userId);
          variables.put(Constants.PROCESS_CANCEL_REASON_VARIABLE_NAME, comment);
          variables.put(Constants.PROCESS_CANCEL_TIME_VARIABLE_NAME, LocalDateTime.now());
          variables.put(Constants.Variable.PROCESS_APPROVED, approvedResult);
          variables.put(Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID, lastTaskId);
          setInstanceVariables(instanceId, variables);

          runtimeService.deleteProcessInstance(instanceId, comment);
          dispatchInstanceEvent(
              instanceId,
              flowKey,
              starter,
              approvedResult ? FlowInstanceEvent.EventType.APPROVED : FlowInstanceEvent.EventType.REJECTED,
              lastTaskId);
          return null;
        });
  }

  private Either<BusinessException, ProcessDefinition> getProcessDefinition(String flowKey) {
    var definition = repositoryService.createProcessDefinitionQuery()
        .processDefinitionKey(flowKey)
        .latestVersion()
        .singleResult();

    if (definition == null) {
      return Either.left(new BusinessException("未找到已部署的流程"));
    }
    if (definition.isSuspended()) {
      return Either.left(new BusinessException("流程已挂起"));
    }
    return Either.right(definition);
  }

  private <T> Try<T> rollbackFailure(Throwable cause) {
    try {
      TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    } catch (NoTransactionException noTransaction) {
      // Direct unit tests may invoke the service without a transaction proxy.
      return Try.failure(cause);
    }
    return Try.failure(cause);
  }

  private Try<ProcessInstance> getInstance(String instanceId) {
    return Try.of(() -> {
      var instance = runtimeService.createProcessInstanceQuery()
          .processInstanceId(instanceId)
          .singleResult();
      if (instance == null) {
        throw new BusinessException("流程实例不存在");
      }
      return instance;
    });
  }

  private String getLastFinishedTaskId(String instanceId) {
    return Optional.ofNullable(getRuntimeStringVariable(instanceId, Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID))
        .orElseGet(() -> Optional.ofNullable(historyService)
            .map(service -> service.createHistoricTaskInstanceQuery()
                .processInstanceId(instanceId)
                .finished()
                .orderByHistoricTaskInstanceEndTime()
                .desc()
                .listPage(0, 1))
            .filter(tasks -> !tasks.isEmpty())
            .map(tasks -> tasks.get(0).getId())
            .orElse(null));
  }

  public void setInstanceVariables(String instanceId, Map<String, Object> variables) {
    runtimeService.createExecutionQuery()
        .processInstanceId(instanceId)
        .list()
        .stream()
        .filter(execution -> !execution.isEnded())
        .forEach(execution -> runtimeService.setVariables(execution.getId(), variables));
  }

  private void dispatchInstanceEvent(String instanceId, ProcessInstance instance, FlowInstanceEvent.EventType eventType, String lastTaskId) {
    dispatchInstanceEvent(instanceId, getFlowKey(instanceId, instance), getRuntimeStringVariable(instanceId, Constants.Variable.PROCESS_STARTER),
        eventType, lastTaskId);
  }

  private void dispatchInstanceEvent(String instanceId, String flowKey, String starter, FlowInstanceEvent.EventType eventType, String lastTaskId) {
    var event = new FlowInstanceEvent();
    event.setInstanceId(instanceId);
    event.setFlowKey(flowKey);
    event.setEventType(eventType);
    event.setEventTime(Dates.now());
    event.setStarter(starter);
    event.setLastTaskId(lastTaskId);
    instanceEventDispatcher.dispatchEvent(event);
  }

  private String getFlowKey(String instanceId, ProcessInstance instance) {
    return Optional.ofNullable(getRuntimeStringVariable(instanceId, Constants.Variable.PROCESS_FLOW_KEY))
        .orElseGet(() -> Optional.ofNullable(instance)
            .map(ProcessInstance::getProcessDefinitionId)
            .map(repositoryService::getProcessDefinition)
            .map(ProcessDefinition::getKey)
            .orElse(null));
  }

  private String getRuntimeStringVariable(String instanceId, String variableName) {
    return runtimeService.getVariable(instanceId, variableName, String.class);
  }

  private Process getProcess(ProcessDefinition definition) {
    return repositoryService.getBpmnModel(definition.getId()).getMainProcess();
  }

  private List<NodeFormFieldPermission> getStartNodePermissions(StructuredFlowDefinition flowDefinition) {
    return Optional.ofNullable(flowDefinition)
        .flatMap(StructuredFlowDefinition::getStartFlowNode)
        .map(FlowNode::getPermission)
        .orElseGet(Collections::emptyList);
  }

  private Try<Map<String, List<String>>> filterUserSelectAssignee(String flowKey,
                                                                   Map<String, Object> variableMap,
                                                                   Map<String, List<String>> assigneeMap) {
    if (assigneeMap == null) {
      assigneeMap = Collections.emptyMap();
    }

    var userSelectCandidateApprovalNodes = ObjectUtils.isEmpty(variableMap)
        ? flowDefinitionService.getUserSelectCandidateApprovalNodes(flowKey)
        : flowDefinitionService.getUserSelectCandidateApprovalNodes(flowKey, variableMap);
    if (!ObjectUtils.isEmpty(variableMap) && ObjectUtils.isEmpty(userSelectCandidateApprovalNodes)) {
      userSelectCandidateApprovalNodes = flowDefinitionService.getUserSelectCandidateApprovalNodes(flowKey);
    }

    if (ObjectUtils.isEmpty(userSelectCandidateApprovalNodes)) {
      return Try.success(assigneeMap);
    }

    Map<String, List<String>> finalAssigneeMap = assigneeMap;
    Map<String, List<String>> result = userSelectCandidateApprovalNodes
        .stream()
        .map(FlowNode::getId)
        .collect(HashMap::new, (map, nodeId) -> map.put(nodeId, finalAssigneeMap.get(nodeId)), HashMap::putAll);

    List<String> taskAssigneeEmptyNodeIds = result.keySet().stream()
        .filter(nodeId -> ObjectUtils.isEmpty(result.get(nodeId)))
        .collect(java.util.stream.Collectors.toList());

    if (ObjectUtils.isNotEmpty(taskAssigneeEmptyNodeIds)) {
      String taskAssigneeEmptyNodeNames = userSelectCandidateApprovalNodes
          .stream()
          .filter(node -> taskAssigneeEmptyNodeIds.contains(node.getId()))
          .map(FlowNode::getName)
          .collect(java.util.stream.Collectors.joining(", "));
      return Try.failure(new BusinessException("节点：【" + taskAssigneeEmptyNodeNames + "】 未选择人员"));
    }

    var rangeFailure = UserSelectAssigneeRangeChecker.checkSingle(userSelectCandidateApprovalNodes, result);
    if (rangeFailure.isPresent()) {
      return Try.failure(rangeFailure.get());
    }

    return Try.success(result);
  }
}
