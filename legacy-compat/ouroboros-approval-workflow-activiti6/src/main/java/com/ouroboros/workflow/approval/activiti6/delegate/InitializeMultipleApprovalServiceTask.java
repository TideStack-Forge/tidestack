package com.ouroboros.workflow.approval.activiti6.delegate;

import static com.ouroboros.workflow.approval.activiti6.util.BpmnUtil.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.activiti.bpmn.model.ServiceTask;
import org.activiti.bpmn.model.Task;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;
import org.activiti.engine.impl.el.FixedValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.exception.InternalException;
import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.activiti6.util.BpmnUtil;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;

/**
 * 初始化多人审批节点
 * 将多人审批相关变量归零
 */
@Component(InitializeMultipleApprovalServiceTask.BEAN_NAME)
public class InitializeMultipleApprovalServiceTask implements JavaDelegate {
  public static final String BEAN_NAME = "initializeMultipleApprovalServiceTask";

  final static Logger logger = LoggerFactory.getLogger(InitializeMultipleApprovalServiceTask.class);
  @Autowired
  private RepositoryService repositoryService;
  FixedValue percentage;
  FixedValue isSequential;
  FixedValue multipleApprovalTaskId;

  @Override
  public void execute(DelegateExecution execution) {
    logger.debug("Initialize Multiple Approval ServiceTask");

    org.activiti.bpmn.model.FlowElement flowElement = execution.getCurrentFlowElement();
    if (!(flowElement instanceof ServiceTask)) {
      String message = "Initialize Multiple Approval ServiceTask Fail: flowElement is not ServiceTask";
      logger.error(message);
      throw new InternalException(message);
    }
    ServiceTask serviceTask = (ServiceTask) flowElement;
    String approvalTaskId = multipleApprovalTaskId.getExpressionText();
    int percent = Integer.parseInt(percentage.getExpressionText());
    boolean isSequential = Boolean.parseBoolean(this.isSequential.getExpressionText());
    String rejectAction = readRejectAction(serviceTask);

    if (approvalTaskId == null) {
      String message = "Initialize Multiple Approval ServiceTask fail: multipleApprovalTaskId is null";
      logger.error(message);
      throw new InternalException(message);
    }

    List<String> assigneeList = getTaskAssignees(execution, approvalTaskId, serviceTask);


    // 如果没有候选人，则根据回退配置执行回退逻辑
    if (assigneeList.isEmpty()) {
      Optional<ApprovalNode> approvalNode = resolveApprovalNode(execution, approvalTaskId);
      if (!approvalNode.isPresent()) {
        logger.error("致命错误！未找到任务节点，默认执行拒绝逻辑");
        rejectMultiTasks(execution, approvalTaskId, rejectAction, "审批人为空，未找到任务节点");
        return;
      }
      ApprovalNode.NoCandidateMode mode = approvalNode.get().getNoCandidateMode();
      switch (mode) {
        case rejected:
          rejectMultiTasks(execution, approvalTaskId, rejectAction, "审批人为空，执行拒绝逻辑");
          return;
        case approved:
          approvalMultiTasks(execution, approvalTaskId, rejectAction, "审批人为空，执行通过逻辑");
          return;
        case reAssignUser:
          assigneeList = Optional.ofNullable(approvalNode.get().getReAssignUsers())
              .orElseGet(Collections::emptyList);
          if (assigneeList.isEmpty()) {
            logger.warn("未找到回退配置的候选人，执行拒绝逻辑");
            rejectMultiTasks(execution, approvalTaskId, rejectAction, "审批人为空，且未找到回退配置的候选人");
            return;
          }
          break;
        default:
          logger.warn("未配置回退机制，默认执行拒绝逻辑");
          rejectMultiTasks(execution, approvalTaskId, rejectAction, "审批人为空，且未配置回退逻辑");
          return;
      }
    }

    setMultipleSession(execution, approvalTaskId, assigneeList, false, false);
    // 设置会签控制变量
    setControlVariables(execution, approvalTaskId, percent, isSequential, rejectAction, false, false);
  }

  private Optional<ApprovalNode> resolveApprovalNode(DelegateExecution execution, String approvalTaskId) {
    if (repositoryService == null || execution.getProcessDefinitionId() == null) {
      return Optional.empty();
    }

    org.activiti.bpmn.model.BpmnModel bpmnModel = repositoryService.getBpmnModel(execution.getProcessDefinitionId());
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
        .filter(node -> node.getId().equals(approvalTaskId))
        .findFirst();
  }

  private void setMultipleSession(
      DelegateExecution execution,
      String approvalTaskId,
      List<String> assigneeList,
      Boolean approved,
      Boolean completed
  ) {
    String sessionId = UUID.randomUUID().toString();
    execution.setVariable(Constants.Variable.getMultipleApprovalSessionVariableName(approvalTaskId), sessionId);
    execution.setVariable(Constants.Variable.getMultipleApprovalAssigneeListVariableName(approvalTaskId), assigneeList);
    execution.setVariable(Constants.Variable.MULTIPLE_APPROVAL_SESSION + sessionId + Constants.Variable.APPROVED_SUFFIX, approved);
    execution.setVariable(Constants.Variable.MULTIPLE_APPROVAL_SESSION + sessionId + Constants.Variable.MULTIPLE_APPROVAL_COMPLETED_SUFFIX, completed);
    execution.setVariable(Constants.Variable.MULTIPLE_APPROVAL_SESSION + sessionId + Constants.Variable.MULTIPLE_APPROVAL_ASSIGNEE_LIST, assigneeList);
  }

  private void setControlVariables(DelegateExecution execution, String approvalTaskId, Integer percent, Boolean isSequential, String rejectAction, Boolean approved, Boolean completed) {
    execution.setVariable(approvalTaskId + Constants.Variable.APPROVAL_ACTION_SUFFIX, rejectAction);
    execution.setVariable(approvalTaskId + Constants.Variable.MULTIPLE_APPROVED_PERCENTAGE_SUFFIX, percent);
    execution.setVariable(approvalTaskId + Constants.Variable.MULTIPLE_APPROVAL_COMPLETED_SUFFIX, completed);
    execution.setVariable(approvalTaskId + Constants.Variable.MULTIPLE_APPROVAL_IS_SEQUENTIAL_SUFFIX, isSequential);

    execution.setVariable(approvalTaskId + Constants.Variable.APPROVED_SUFFIX, approved);
    execution.setVariable(approvalTaskId + Constants.Variable.NR_OF_APPROVED_SUFFIX, 0);
    execution.setVariable(approvalTaskId + Constants.Variable.NR_OF_REJECTED_SUFFIX, 0);
  }

  private List<String> getTaskAssignees(DelegateExecution execution, String approvalTaskId, Task task) {
    // 如果流程变量中 USER_SELECTED_ASSIGNEES 里有值，则从中取
    Map assigneeFromVar = execution.getVariable(Constants.Variable.USER_SELECTED_ASSIGNEES, Map.class);
    if (assigneeFromVar != null) {
      Map<String, List<String>> assigneeMap = (Map<String, List<String>>) assigneeFromVar;
      if (assigneeMap.containsKey(approvalTaskId)) {
        List<String> assignees = assigneeMap.get(approvalTaskId);
        return assignees == null ? Collections.emptyList() : assignees;
      }
    }

    // 否则从candidate中取
    String starter = Optional.ofNullable(execution.getVariable(Constants.Variable.PROCESS_STARTER))
        .map(Object::toString)
        .orElse("");
    return getCandidateUsers(execution, task).stream()
        .map(assignee -> assignee.replace("${starter}", starter))
        .collect(Collectors.toList());
  }
  void rejectMultiTasks(DelegateExecution execution, String approvalTaskId, String rejectAction, String reason) {
    setMultipleSession(execution, approvalTaskId, Collections.emptyList(), false, true);
    setControlVariables(execution, approvalTaskId, 0, false, rejectAction, false, true);
  }

  void approvalMultiTasks(DelegateExecution execution, String approvalTaskId, String rejectAction, String reason) {
    setMultipleSession(execution, approvalTaskId, Collections.emptyList(), true, true);
    setControlVariables(execution, approvalTaskId, 0, false, rejectAction, true, true);
  }
}
