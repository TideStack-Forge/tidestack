package com.ouroboros.workflow.approval.activiti6.service;

import java.util.*;
import java.util.stream.Collectors;

import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.history.HistoricVariableInstance;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.DataObject;
import org.activiti.engine.task.Comment;
import org.activiti.engine.task.IdentityLink;
import org.activiti.engine.task.Task;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import io.vavr.control.Try;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.util.Dates;
import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.BuiltInFlowTaskService;
import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.FlowFormService;
import com.ouroboros.workflow.approval.FlowTaskAction;
import com.ouroboros.workflow.approval.FlowTaskService;
import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.NodeFormFieldPermission;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;
import com.ouroboros.workflow.approval.definition.nodes.HandleNode;
import com.ouroboros.workflow.approval.model.FlowTaskDetail;

@Service
public class Activiti6FlowTaskService implements FlowTaskService, BuiltInFlowTaskService {
  TaskService taskService;
  @Autowired
  HistoryService historyService;
  @Autowired
  RepositoryService repositoryService;
  @Autowired(required = false)
  List<FlowFormService> flowFormServices = Collections.emptyList();
  @Autowired
  FlowDefinitionService flowDefinitionService;
  List<FlowTaskAction<?>> flowTaskActions;

  Activiti6FlowTaskService(TaskService taskService, List<FlowTaskAction<?>> actions) {
    this.taskService = taskService;
    flowTaskActions = actions;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public <T> Try<Void> doAction(String taskId, String actionType, String userId, String comment, Map<String, Object> formData, T payload) {
    var task = taskService.createTaskQuery().taskId(taskId).singleResult();
    if (task == null) {
      return Try.failure(new PlatformException("未找到对应的任务: " + taskId));
    }

    var isUserInCandidateList = taskService.getIdentityLinksForTask(taskId)
        .stream()
        .map(IdentityLink::getUserId)
        .filter(Objects::nonNull)
        .anyMatch(userId::equals);
    if (!isUserInCandidateList) {
      return Try.failure(new PlatformException("当前用户不在任务的候选人列表中: " + taskId));
    }

    if (task.getAssignee() != null && !task.getAssignee().equals(userId)) {
      return Try.failure(new PlatformException("任务已被认领"));
    }

    var action = getAction(task, actionType);
    if (!action.isPresent()) {
      return Try.failure(new PlatformException("未找到对应的流程动作: " + actionType));
    }

    if (task.getAssignee() == null) {
      taskService.claim(taskId, userId);
    }

    if (formData != null) {
      Try<Void> submitTry = submitTaskForm(task, userId, formData);
      if (submitTry.isFailure()) {
        return rollbackFailure(submitTry.getCause());
      }

      // Activiti runtime task forms are backed by FlowFormService here, so keep a local read-model copy.
      taskService.setVariableLocal(taskId, Constants.TASK_FORM_DATA_KEY, formData);
    }

    if (payload != null) {
      var payloadString = Json.toJsonString(payload);
      taskService.setVariableLocal(taskId, Constants.TASK_PAYLOAD_KEY, payloadString);
    }

    if (comment != null) {
      taskService.addComment(taskId, task.getProcessInstanceId(), comment);
    }

    return rollbackOnFailure(action.get().doAction(task));
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

  private <T> Try<T> rollbackOnFailure(Try<T> result) {
    if (result.isFailure()) {
      try {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
      } catch (NoTransactionException noTransaction) {
        // Direct unit tests may invoke the service without a transaction proxy.
        return result;
      }
    }
    return result;
  }

  @Override
  public <T> T getPayload(String taskId, Class<T> clazz) {
    var payloadValue = taskService.getVariableLocal(taskId, Constants.TASK_PAYLOAD_KEY);
    if (payloadValue == null) {
      payloadValue = Optional.ofNullable(taskService.getDataObject(taskId, Constants.TASK_PAYLOAD_KEY))
          .map(DataObject::getValue)
          .orElse(null);
    }
    var payloadBean = Optional.ofNullable(payloadValue)
        .map(String::valueOf)
        .map(s -> Json.toBean(clazz, s))
        .orElse(null);
    return payloadBean;
  }

  @Override
  public Map<String, Object> getTaskFormData(String taskId) {
    return Optional.ofNullable(taskService.getVariableLocal(taskId, Constants.TASK_FORM_DATA_KEY))
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .orElseGet(Collections::emptyMap);
  }

  @Override
  public String getComment(String taskId) {
    var comments = taskService.getTaskComments(taskId);

    return comments.stream()
        .filter(Objects::nonNull)
        .max(Comparator.comparing(Comment::getTime, Comparator.nullsFirst(Date::compareTo)))
        .map(Comment::getFullMessage)
        .orElse(null);
  }

  @Override
  public void setTaskAssignee(String taskId, String userId) {
    taskService.setAssignee(taskId, userId);
  }

  @Override
  public Try<SchemaNode> getTaskForm(String taskId) {
    var task = taskService.createTaskQuery()
        .taskId(taskId)
        .singleResult();
    if (task == null) {
      return Try.failure(new PlatformException("任务不存在"));
    }

    var formKey = task.getFormKey();
    if (formKey == null) {
      return Try.failure(new PlatformException("任务表单不存在"));
    }

    var formType = getTaskFormType(taskId);
    var formId = taskService.getVariable(taskId, Constants.TASK_FORM_ID_KEY, String.class);
    var permissions = getTaskPermissions(task);
    var actions = getTaskActions(task);
    var flowFormServiceTry = getRequiredFlowFormService(formType);
    if (flowFormServiceTry.isFailure()) {
      return Try.failure(flowFormServiceTry.getCause());
    }

    var schemaTry = flowFormServiceTry.get()
        .getTaskFormSchema(task.getProcessInstanceId(), taskId, formKey, formId, permissions, actions);
    if (schemaTry.isFailure()) {
      return Try.failure(schemaTry.getCause());
    }
    if (schemaTry.get() == null) {
      return Try.failure(new PlatformException("表单不存在"));
    }
    return schemaTry;
  }

  @Override
  public List<FlowTaskDetail> getTaskList(String userId) {
    var tasks = taskService.createTaskQuery()
        .taskCandidateOrAssigned(userId)
        .active()
        .orderByTaskCreateTime()
        .desc()
        .list();
    return mapToFlowTaskDetails(tasks);
  }

  @Override
  public List<FlowTaskDetail> getTaskList(String userId, Integer offset, Integer limit) {
    var tasks = taskService.createTaskQuery()
        .taskCandidateOrAssigned(userId)
        .active()
        .orderByTaskCreateTime().desc()
        .listPage(offset, limit);
    return mapToFlowTaskDetails(tasks);
  }

  @Override
  public List<FlowTaskDetail> getTaskList(String userId, String flowKey) {
    var tasks = taskService.createTaskQuery()
        .active()
        .taskCandidateOrAssigned(userId)
        .processDefinitionKey(flowKey)
        .orderByTaskCreateTime()
        .desc()
        .list();
    return mapToFlowTaskDetails(tasks);
  }

  @Override
  public List<FlowTaskDetail> getTaskList(String userId, String flowKey, Integer offset, Integer limit) {
    var tasks = taskService.createTaskQuery()
        .active()
        .taskCandidateOrAssigned(userId)
        .processDefinitionKey(flowKey)
        .orderByTaskCreateTime()
        .desc()
        .listPage(offset, limit);
    return mapToFlowTaskDetails(tasks);
  }

  @Override
  public Long getTaskCount(String userId) {
    var query = taskService.createTaskQuery()
        .active()
        .taskCandidateOrAssigned(userId);
    return query.count();
  }

  @Override
  public Long getTaskCount(String userId, String flowKey) {
    var query = taskService.createTaskQuery()
        .active()
        .taskCandidateOrAssigned(userId)
        .processDefinitionKey(flowKey);
    return query.count();
  }

  @Override
  public List<FlowTaskDetail> getHistoryTaskList(String userId, Integer offset, Integer limit) {
    var query = historyService.createHistoricTaskInstanceQuery()
        .taskAssignee(userId)
        .finished()
        .orderByHistoricTaskInstanceEndTime()
        .desc()
        .listPage(offset, limit);
    return mapHistoryToFlowTaskDetails(query);
  }

  @Override
  public List<FlowTaskDetail> getHistoryTaskList(String userId, String flowKey, Integer offset, Integer limit) {
    var query = historyService.createHistoricTaskInstanceQuery()
        .taskAssignee(userId)
        .processDefinitionKey(flowKey)
        .finished()
        .orderByHistoricTaskInstanceEndTime()
        .desc()
        .listPage(offset, limit);
    return mapHistoryToFlowTaskDetails(query);
  }

  @Override
  public Long getHistoryTaskCount(String userId) {
    return historyService.createHistoricTaskInstanceQuery()
        .taskAssignee(userId)
        .finished()
        .count();
  }

  @Override
  public Long getHistoryTaskCount(String userId, String flowKey) {
    return historyService.createHistoricTaskInstanceQuery()
        .taskAssignee(userId)
        .processDefinitionKey(flowKey)
        .finished()
        .count();
  }

  private List<FlowTaskDetail> mapToFlowTaskDetails(List<Task> tasks) {
    return tasks.stream()
        .map(task -> {
          var starter = historyService.createHistoricVariableInstanceQuery()
              .processInstanceId(task.getProcessInstanceId())
              .variableName(Constants.Variable.PROCESS_STARTER)
              .listPage(0, 1);
          var history = historyService.createHistoricTaskInstanceQuery()
              .taskId(task.getId())
              .singleResult();
          var definition = repositoryService.getProcessDefinition(task.getProcessDefinitionId());
          var starterValue = starter.stream()
              .findFirst()
              .map(HistoricVariableInstance::getValue)
              .map(String::valueOf)
              .orElse(null);
          var startTime = Optional.ofNullable(history)
              .map(HistoricTaskInstance::getStartTime)
              .map(Dates::toLocalDateTime)
              .orElse(null);
          var endTime = Optional.ofNullable(history)
              .map(HistoricTaskInstance::getEndTime)
              .map(Dates::toLocalDateTime)
              .orElse(null);
          var taskDetail = new FlowTaskDetail();
          taskDetail.setStarter(starterValue);
          taskDetail.setInstanceId(task.getProcessInstanceId());
          taskDetail.setId(task.getId());
          taskDetail.setName(task.getName());
          taskDetail.setTaskDefinitionKey(task.getTaskDefinitionKey());
          taskDetail.setStartTime(startTime);
          taskDetail.setEndTime(endTime);
          taskDetail.setFlowKey(Optional.ofNullable(definition).map(ProcessDefinition::getKey).orElse(null));
          taskDetail.setFlowName(Optional.ofNullable(definition).map(ProcessDefinition::getName).orElse(null));
          taskDetail.setAssignee(task.getAssignee());
          taskDetail.setCandidateUsers(getCandidateUsers(task.getId()));
          taskDetail.setActive(true);
          return taskDetail;
        })
        .collect(Collectors.toList());
  }

  private List<FlowTaskDetail> mapHistoryToFlowTaskDetails(List<HistoricTaskInstance> tasks) {
    Map<String, String> processStarters = tasks.stream()
        .map(HistoricTaskInstance::getProcessInstanceId)
        .distinct()
        .collect(Collectors.toMap(
            pid -> pid,
            pid -> Optional.ofNullable(historyService.createHistoricVariableInstanceQuery()
                    .processInstanceId(pid)
                    .variableName(Constants.Variable.PROCESS_STARTER)
                    .singleResult())
                .map(HistoricVariableInstance::getValue)
                .map(String::valueOf)
                .orElse(null),
            (v1, v2) -> v2
        ));
    return tasks.stream()
        .map(task -> {
          var starter = processStarters.get(task.getProcessInstanceId());
          var definition = repositoryService.getProcessDefinition(task.getProcessDefinitionId());
          var startTime = Dates.toLocalDateTime(task.getStartTime());
          var endTime = task.getEndTime() == null ? null : Dates.toLocalDateTime(task.getEndTime());
          var taskDetail = new FlowTaskDetail();
          taskDetail.setStarter(starter);
          taskDetail.setId(task.getId());
          taskDetail.setName(task.getName());
          taskDetail.setInstanceId(task.getProcessInstanceId());
          taskDetail.setTaskDefinitionKey(task.getTaskDefinitionKey());
          taskDetail.setStartTime(startTime);
          taskDetail.setEndTime(endTime);
          taskDetail.setFlowKey(Optional.ofNullable(definition).map(ProcessDefinition::getKey).orElse(null));
          taskDetail.setFlowName(Optional.ofNullable(definition).map(ProcessDefinition::getName).orElse(null));
          taskDetail.setAssignee(task.getAssignee());
          taskDetail.setCandidateUsers(Collections.emptyList());
          taskDetail.setActive(false);
          return taskDetail;
        })
        .collect(Collectors.toList());
  }

  private Optional<FlowTaskAction<Task>> getAction(Task task, String action) {
    return flowTaskActions.stream()
        .filter(a -> a.supportTask(task))
        .filter(a -> a.actionName().equalsIgnoreCase(action))
        .map(a -> (FlowTaskAction<Task>) a)
        .findFirst();
  }

  private String getTaskFormType(String taskId) {
    return StringUtils.defaultIfBlank(taskService.getVariable(taskId, Constants.TASK_FORM_TYPE_KEY, String.class), "default");
  }

  private Optional<FlowFormService> getFlowFormService(String formType) {
    return Optional.ofNullable(flowFormServices)
        .orElse(Collections.emptyList())
        .stream()
        .filter(flowFormService -> flowFormService.getType().equalsIgnoreCase(formType))
        .findFirst();
  }

  private Try<FlowFormService> getRequiredFlowFormService(String formType) {
    return getFlowFormService(formType)
        .map(Try::success)
        .orElseGet(() -> Try.failure(new PlatformException("未找到对应的表单服务")));
  }

  private Try<Void> submitTaskForm(Task task, String userId, Map<String, Object> formData) {
    var formKey = task.getFormKey();
    if (formKey == null) {
      return Try.success(null);
    }

    var flowFormServiceTry = getRequiredFlowFormService(getTaskFormType(task.getId()));
    if (flowFormServiceTry.isFailure()) {
      return Try.failure(flowFormServiceTry.getCause());
    }

    return flowFormServiceTry.get().submitTaskForm(
        task.getProcessInstanceId(),
        task.getId(),
        formKey,
        taskService.getVariable(task.getId(), Constants.TASK_FORM_ID_KEY, String.class),
        userId,
        getTaskPermissions(task),
        formData);
  }

  private List<String> getTaskActions(Task task) {
    return getTaskFlowNode(task)
        .map(flowNode -> {
          if (flowNode instanceof HandleNode) {
            return Collections.singletonList(Constants.Value.COMPLETE);
          }
          if (flowNode instanceof ApprovalNode) {
            return Optional.ofNullable(((ApprovalNode) flowNode).getActions())
                .map(ArrayList::new)
                .orElseGet(ArrayList::new);
          }
          return Collections.<String>emptyList();
        })
        .orElseGet(ArrayList::new);
  }

  private List<NodeFormFieldPermission> getTaskPermissions(Task task) {
    return getTaskFlowNode(task)
        .map(FlowNode::getPermission)
        .orElseGet(Collections::emptyList);
  }

  private Optional<FlowNode> getTaskFlowNode(Task task) {
    if (repositoryService == null || flowDefinitionService == null || task == null || task.getProcessDefinitionId() == null) {
      return Optional.empty();
    }

    var processDefinition = repositoryService.getProcessDefinition(task.getProcessDefinitionId());
    if (processDefinition == null) {
      return Optional.empty();
    }

    return flowDefinitionService.getFlowNodes(processDefinition.getKey()).stream()
        .filter(node -> Objects.equals(node.getId(), task.getTaskDefinitionKey()))
        .findFirst();
  }

  @Override
  public List<String> getCandidateUsers(String taskId) {
    return taskService.getIdentityLinksForTask(taskId)
        .stream()
        .filter(Objects::nonNull)
        .filter(link -> "candidate".equalsIgnoreCase(link.getType())
            || "assignee".equalsIgnoreCase(link.getType()))
        .map(IdentityLink::getUserId)
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.toList());
  }

  @Override
  public void setCandidateUsers(String taskId, List<String> userIds) {
    var normalizedUserIds = Optional.ofNullable(userIds).orElse(Collections.emptyList());
    var oldUsers = getCandidateUsers(taskId);
    var removedUsers = oldUsers.stream()
        .filter(u -> !normalizedUserIds.contains(u))
        .collect(Collectors.toList());
    var addedUsers = normalizedUserIds.stream()
        .filter(u -> !oldUsers.contains(u))
        .collect(Collectors.toList());
    removedUsers.forEach(u -> taskService.deleteCandidateUser(taskId, u));
    addedUsers.forEach(u -> taskService.addCandidateUser(taskId, u));
  }

  @Override
  public String getTaskAssignee(String taskId) {
    return Optional.ofNullable(taskService.createTaskQuery()
            .taskId(taskId)
            .singleResult())
        .map(Task::getAssignee)
        .orElse(null);
  }

  @Override
  public List<FlowTaskDetail> getAllActiveTaskList() {
    var tasks = taskService.createTaskQuery()
        .active()
        .orderByTaskCreateTime()
        .desc()
        .list();
    return mapToFlowTaskDetails(tasks);
  }

  @Override
  public FlowTaskDetail getTaskDetail(String taskId) {
    var task = taskService.createTaskQuery()
        .taskId(taskId)
        .singleResult();
    if (task == null) {
      return null;
    }
    return mapToFlowTaskDetails(Arrays.asList(task)).get(0);
  }
}
