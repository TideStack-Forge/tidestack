package com.ouroboros.workflow.approval.activiti6.transpiler.processor;

import static com.ouroboros.workflow.approval.activiti6.util.BpmnUtil.*;

import java.util.ArrayList;

import org.activiti.bpmn.model.*;
import org.activiti.bpmn.model.Process;
import org.activiti.engine.delegate.ExecutionListener;
import org.activiti.engine.delegate.TaskListener;

import com.ouroboros.workflow.approval.activiti6.delegate.InitializeMultipleApprovalServiceTask;
import com.ouroboros.workflow.approval.activiti6.listener.CreateMultipleApprovalTaskListener;
import com.ouroboros.workflow.approval.activiti6.listener.MultipleApprovalCompletedTaskListener;
import com.ouroboros.workflow.approval.activiti6.listener.MultipleApprovalEndExecutionListener;
import com.ouroboros.workflow.approval.activiti6.listener.MultipleApprovalStartExecutionListener;
import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.nodes.MultipleApprovalNode;
import com.ouroboros.workflow.approval.transpiler.TranspileContext;
import com.ouroboros.workflow.approval.util.TreeGraphNode;

public class MultipleApprovalProcessor extends Activiti6AbstractProcessor {

  /**
   * 比较复杂的逻辑关系，必须使用监听器了
   * 所有设定的局部变量：
   * nrOfApproves                 已通过人数
   * percentage                   通过百分比
   * assigneeList                 审批人列表
   * approveList                  审批通过列表
   * loopCounter                  表示当前任务索引（activiti内置）
   * nrOfCompletedInstance        已完成实例数（activiti内置）
   * nrOfInstances                实例总数（activiti内置）
   * nrOfActiveInstances          进行中实例数（activiti内置）
   */
  @Override
  Runnable doProcess(TranspileContext<BpmnModel> context, Process process, TreeGraphNode node) {
    final var flowNode = (MultipleApprovalNode) node.getFlowNode();
    final var approvedVar = flowNode.getId() + Constants.Variable.APPROVED_SUFFIX;
    final var multipleApprovalCompletedVar = flowNode.getId() + Constants.Variable.MULTIPLE_APPROVAL_COMPLETED_SUFFIX;
    final var formKey = context.getStructuredFlowDefinition().getFormKey();
    final var isSequential = Constants.Value.MULTIPLE_APPROVAL_MODE_SEQUENTIAL.equalsIgnoreCase(flowNode.getMultipleMode());

    //初始化会签任务
    final var initialMultipleApprovalServiceTask = new ServiceTask();
    // 审批任务
    final var approvalTask = new UserTask();
    // 审批判断网关
    final var decisionGateway = new ExclusiveGateway();
    // 审批通过网关
    final var approvedGateway = new InclusiveGateway();

    final var multi = new MultiInstanceLoopCharacteristics();
    //任务监听器
    final var taskListeners = new ArrayList<ActivitiListener>();
    //执行监听器
    final var executionListeners = new ArrayList<ActivitiListener>();
    //自定义属性
    final var customProperties = new ArrayList<CustomProperty>();

    // 需要重置节点会签的状态
    initialMultipleApprovalServiceTask.setId(flowNode.getId() + Constants.SpecialNode.RESET_MULTIPLE_APPROVAL_ID_SUFFIX);
    initialMultipleApprovalServiceTask.setName(Constants.SpecialNode.RESET_MULTIPLE_APPROVAL_NAME);
    useDelegateBean(initialMultipleApprovalServiceTask, InitializeMultipleApprovalServiceTask.BEAN_NAME);
    initialMultipleApprovalServiceTask.setCustomProperties(customProperties);
    saveRejectAction(initialMultipleApprovalServiceTask, flowNode.getRejectAction());
    saveCandidates(initialMultipleApprovalServiceTask, flowNode.getCandidates());

    // 通过比例填入自定义属性
    customProperties.add(buildCustomProperty(Constants.Property.PERCENTAGE, String.valueOf(flowNode.getPercentage())));
    customProperties.add(buildCustomProperty(Constants.Property.MULTIPLE_APPROVAL_TASK_ID, flowNode.getId()));
    customProperties.add(buildCustomProperty(Constants.Property.IS_SEQUENTIAL, String.valueOf(isSequential)));

    approvalTask.setId(flowNode.getId());
    approvalTask.setName(flowNode.getName());
    approvalTask.setAssignee("${assignee}");
    approvalTask.setFormKey(formKey);
    approvalTask.setLoopCharacteristics(multi);
    approvalTask.setFormProperties(mapFormProperties(flowNode.getPermission()));
    approvalTask.setExecutionListeners(executionListeners);
    approvalTask.setTaskListeners(taskListeners);

    multi.setCompletionCondition(
        "${(nrOfCompletedInstances >= nrOfInstances) || ("
            + variableEqualsExpression(multipleApprovalCompletedVar, true)
            + ")}");
    multi.setInputDataItem(Constants.Variable.getMultipleApprovalAssigneeListVariableName(flowNode.getId()));
    //与上面的 setAssignee("${assignee}") 对应
    multi.setElementVariable("assignee");
    multi.setSequential(isSequential);

    // 监听任务创建，创建节点
    taskListeners.add(buildActivitiListener(TaskListener.EVENTNAME_CREATE, CreateMultipleApprovalTaskListener.BEAN_NAME));
    //每一个任务完成都更新同意情况
    taskListeners.add(buildActivitiListener(TaskListener.EVENTNAME_COMPLETE, MultipleApprovalCompletedTaskListener.BEAN_NAME));
    executionListeners.add(buildActivitiListener(ExecutionListener.EVENTNAME_START, MultipleApprovalStartExecutionListener.BEAN_NAME));
    executionListeners.add(buildActivitiListener(ExecutionListener.EVENTNAME_END, MultipleApprovalEndExecutionListener.BEAN_NAME));

    // 监听任务开始，填入执行人
    decisionGateway.setId(flowNode.getId() + Constants.SpecialNode.DECISION_ID_SUFFIX);
    decisionGateway.setName(flowNode.getName() + Constants.SpecialNode.DECISION_NAME_SUFFIX);

    approvedGateway.setId(flowNode.getId() + Constants.SpecialNode.APPROVED_ID_SUFFIX);
    approvedGateway.setName(flowNode.getName() + Constants.SpecialNode.APPROVED_NAME_SUFFIX);

    // 初始化 -> 审批
    final var initial2Approval = buildSequenceFlow(initialMultipleApprovalServiceTask, approvalTask);
    // 审批 -> 决策
    final var approval2Decision = buildSequenceFlow(approvalTask, decisionGateway);
    // 决策 -> 通过
    final var decision2Approved = buildSequenceFlow(decisionGateway, approvedGateway, variableEqualsExpression(approvedVar, true));
    // 决策 -> 拒绝
    final var prevApprovalNode = this.getPrevApprovalNode(node);
    final var rejectedTarget = Constants.Value.ROLLBACK.equalsIgnoreCase(flowNode.getRejectAction()) && prevApprovalNode != null
        ? context.getEntryPoint(prevApprovalNode.getFlowNode().getId())
        : Constants.SpecialNode.FAIL_NODE_ID;
    final var decision2Rejected = buildSequenceFlow(decisionGateway.getId(), rejectedTarget, variableEqualsExpression(approvedVar, false));

    // 添加节点
    process.addFlowElement(initialMultipleApprovalServiceTask);
    process.addFlowElement(approvalTask);
    process.addFlowElement(decisionGateway);
    process.addFlowElement(approvedGateway);

    // 添加连线
    process.addFlowElement(initial2Approval);
    process.addFlowElement(approval2Decision);
    process.addFlowElement(decision2Approved);
    process.addFlowElement(decision2Rejected);

    // 登记入口和出口
    context.setEntryPoint(flowNode.getId(), initialMultipleApprovalServiceTask.getId());
    context.setExitPoint(flowNode.getId(), approvedGateway.getId());

    return null;
  }

  @Override
  public boolean supportNode(FlowNode flowNode) {
    return flowNode instanceof MultipleApprovalNode;
  }
}
