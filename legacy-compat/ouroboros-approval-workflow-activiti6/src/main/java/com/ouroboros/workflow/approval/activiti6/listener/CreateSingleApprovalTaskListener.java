package com.ouroboros.workflow.approval.activiti6.listener;

import static com.ouroboros.workflow.approval.activiti6.util.BpmnUtil.getCandidateUsers;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.activiti.bpmn.model.UserTask;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.TaskService;
import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.delegate.TaskListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.util.Dates;
import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.activiti6.util.BpmnUtil;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

@Component(CreateSingleApprovalTaskListener.BEAN_NAME)
public class CreateSingleApprovalTaskListener implements TaskListener {
  public static final String BEAN_NAME = "createSingleApprovalTaskListener";

  final static Logger logger = LoggerFactory.getLogger(CreateSingleApprovalTaskListener.class);
  @Autowired
  private RepositoryService repositoryService;
  @Autowired
  private TaskService taskService;
  @Autowired
  private TaskEventDispatcher taskEventDispatcher;

  /**
   * 单签任务节点创建时触发
   * -> 主要任务是将节点上的扩展属性设置为任务节点的局部变量，经过多种测试，如果要保存 定义的元素->流程变量 这样比较方便 <- 已不再使用
   */
  @Override
  public void notify(DelegateTask task) {
    var execution = task.getExecution();
    var flowElement = execution.getCurrentFlowElement();
    if (!(flowElement instanceof UserTask)) {
      logger.warn("flowElement is not UserTask");
      return;
    }
    UserTask userTask = (UserTask) flowElement;
    var nodeId = task.getTaskDefinitionKey();
    if (StringUtils.isBlank(nodeId)) {
      nodeId = userTask.getId();
    }
    markTaskKind(task, userTask);

    // 设置候选人
    // 如果流程变量中 USER_SELECTED_ASSIGNEES 里有值，则从中取
    var assigneeFromVar = task.getVariable(Constants.Variable.USER_SELECTED_ASSIGNEES);
    if (assigneeFromVar instanceof Map<?, ?>) {
      Map<String, List<String>> assigneeMap = (Map<String, List<String>>) assigneeFromVar;
      if (assigneeMap.containsKey(nodeId)) {
        var assignees = assigneeMap.get(nodeId);
        if (assignees != null && !assignees.isEmpty()) {
          task.addCandidateUsers(assignees);
          dispatchEvent(task, assignees);
          return;
        }

        var candidates = handleNoCandidateMode(task, nodeId);
        if (!candidates.isEmpty()) {
          task.addCandidateUsers(candidates);
          dispatchEvent(task, candidates);
        }
        return;
      }
    }

    // 否则从candidate中取
    var starter = Optional.ofNullable(execution.getVariable(Constants.Variable.PROCESS_STARTER))
        .map(Object::toString)
        .orElse("");
    var candidates = getCandidateUsers(task).stream()
        .map(assignee -> assignee.replace("${starter}", starter))
        .collect(Collectors.toList());

    // 如果没有候选人，则根据回退配置执行回退逻辑
    if (candidates.isEmpty()) {
      candidates = handleNoCandidateMode(task, nodeId);
      if (candidates.isEmpty()) {
        return;
      }
    }

    task.addCandidateUsers(candidates);

    // Activiti create listener is the point where we publish the external CREATED task-event semantic.
    dispatchEvent(task, candidates);
  }

  private void markTaskKind(DelegateTask task, UserTask userTask) {
    if (taskService == null || StringUtils.isBlank(task.getId())) {
      return;
    }

    String taskKind = BpmnUtil.readTaskKind(userTask);
    if (StringUtils.isNotBlank(taskKind)) {
      taskService.setVariableLocal(task.getId(), Constants.Variable.TASK_KIND, taskKind);
    }
  }

  private List<String> handleNoCandidateMode(DelegateTask task, String nodeId) {
    var taskDefine = resolveApprovalNode(task, nodeId);
    if (!taskDefine.isPresent()) {
      logger.error("致命错误！未找到任务节点，默认执行拒绝逻辑");
      autoComplete(task, Constants.Value.REJECTED, "审批人为空，未找到任务节点");
      return Collections.emptyList();
    }
    var approvalNode = taskDefine.get();
    var mode = approvalNode.getNoCandidateMode();
    switch (mode) {
      case approved:
        autoComplete(task, Constants.Value.APPROVED, "审批人为空，执行通过逻辑");
        return Collections.emptyList();
      case rejected:
        autoComplete(task, Constants.Value.REJECTED, "审批人为空，执行拒绝逻辑");
        return Collections.emptyList();
      case reAssignUser:
        var candidates = Optional.ofNullable(approvalNode.getReAssignUsers()).orElse(Collections.emptyList());
        if (candidates.isEmpty()) {
          logger.warn("未找到回退配置的候选人，执行拒绝逻辑");
          autoComplete(task, Constants.Value.REJECTED, "审批人为空，且未找到回退配置的候选人");
          return Collections.emptyList();
        }
        return candidates;
      default:
        logger.warn("未配置回退机制，默认执行拒绝逻辑");
        autoComplete(task, Constants.Value.REJECTED, "审批人为空，且未配置回退逻辑");
        return Collections.emptyList();
    }
  }

  private Optional<ApprovalNode> resolveApprovalNode(DelegateTask task, String nodeId) {
    if (repositoryService == null) {
      return Optional.empty();
    }

    var bpmnModel = repositoryService.getBpmnModel(task.getProcessDefinitionId());
    if (bpmnModel == null) {
      return Optional.empty();
    }

    var definition = BpmnUtil.readStructuredFlowDefinition(bpmnModel);
    if (definition == null || definition.getNodes() == null) {
      return Optional.empty();
    }

    return definition.getNodes()
        .stream()
        .filter(ApprovalNode.class::isInstance)
        .map(ApprovalNode.class::cast)
        .filter(node -> node.getId().equals(nodeId))
        .findFirst();
  }

  private void autoComplete(DelegateTask task, String action, String comment) {
    if (taskService == null) {
      logger.warn("No Activiti6 task service found while auto-completing task {}", task.getId());
      return;
    }

    var autoUserId = "$" + action;
    task.addCandidateUsers(Collections.singleton(autoUserId));
    taskService.setAssignee(task.getId(), autoUserId);
    taskService.addComment(task.getId(), task.getProcessInstanceId(), comment);
    taskService.setVariableLocal(task.getId(), Constants.Variable.LOCAL_APPROVAL_ACTION, action);
    taskService.complete(task.getId());
  }

  private void dispatchEvent(DelegateTask task, List<String> candidates) {
    var taskEvent = new FlowTaskEvent();
    var nodeId = StringUtils.defaultIfBlank(task.getTaskDefinitionKey(),
        task.getExecution() == null || task.getExecution().getCurrentFlowElement() == null
            ? null
            : task.getExecution().getCurrentFlowElement().getId());
    taskEvent.setInstanceId(task.getProcessInstanceId());
    taskEvent.setTaskId(task.getId());
    taskEvent.setTaskName(task.getName());
    taskEvent.setCandidateUsers(candidates);
    taskEvent.setAssignee(task.getAssignee());
    taskEvent.setFlowKey(BpmnUtil.resolveFlowKey(repositoryService, task));
    taskEvent.setNodeId(nodeId);
    taskEvent.setState(TaskState.CREATED);
    taskEvent.setNotification(BpmnUtil.readNotification(repositoryService, task.getProcessDefinitionId()));
    if (task.getCreateTime() == null) {
      taskEvent.setEventTime(Dates.now());
    } else {
      taskEvent.setEventTime(task.getCreateTime());
    }

    // 设置任务节点定义Supplier
    taskEvent.setFlowNodeSupplier(BpmnUtil.getFlowNodeSupplier(repositoryService, task.getProcessDefinitionId(), nodeId));

    taskEventDispatcher.dispatchEvent(taskEvent);
  }
}
