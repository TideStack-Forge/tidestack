package com.ouroboros.workflow.approval.activiti6.transpiler.processor;

import static com.ouroboros.workflow.approval.activiti6.util.BpmnUtil.*;

import java.util.ArrayList;

import org.activiti.bpmn.model.*;
import org.activiti.bpmn.model.Process;
import org.activiti.engine.delegate.TaskListener;

import com.ouroboros.workflow.approval.activiti6.listener.CreateSingleApprovalTaskListener;
import com.ouroboros.workflow.approval.activiti6.listener.SingleApproveCompletedTaskListener;
import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.nodes.SingleApprovalNode;
import com.ouroboros.workflow.approval.transpiler.TranspileContext;
import com.ouroboros.workflow.approval.util.TreeGraphNode;

public class SingleApprovalProcessor extends Activiti6AbstractProcessor {
  @Override
  Runnable doProcess(TranspileContext<BpmnModel> context, Process process, TreeGraphNode node) {
    final var flowNode = (SingleApprovalNode) node.getFlowNode();
    final var approvedVar = flowNode.getId() + Constants.Variable.APPROVED_SUFFIX;
    final var approvalActionVar = flowNode.getId() + Constants.Variable.APPROVAL_ACTION_SUFFIX;
    final var formKey = context.getStructuredFlowDefinition().getFormKey();

    //审批任务
    final var approvalTask = new UserTask();
    //审批结果决策
    final var decisionGateway = new ExclusiveGateway();
    //审批通过
    final var approvedGateway = new InclusiveGateway();

    final var taskListeners = new ArrayList<ActivitiListener>();

    approvalTask.setId(flowNode.getId());
    approvalTask.setName(flowNode.getName());
    approvalTask.setFormKey(formKey);
    approvalTask.setTaskListeners(taskListeners);
    approvalTask.setFormProperties(mapFormProperties(flowNode.getPermission()));
    saveCandidates(approvalTask, flowNode.getCandidates());
    saveActions(approvalTask, flowNode.getActions());

    taskListeners.add(buildActivitiListener(TaskListener.EVENTNAME_CREATE, CreateSingleApprovalTaskListener.BEAN_NAME));
    taskListeners.add(buildActivitiListener(TaskListener.EVENTNAME_COMPLETE, SingleApproveCompletedTaskListener.BEAN_NAME));


    decisionGateway.setId(flowNode.getId() + Constants.SpecialNode.DECISION_ID_SUFFIX);
    decisionGateway.setName(flowNode.getName() + Constants.SpecialNode.DECISION_NAME_SUFFIX);

    approvedGateway.setId(flowNode.getId() + Constants.SpecialNode.APPROVED_ID_SUFFIX);
    approvedGateway.setName(flowNode.getName() + Constants.SpecialNode.APPROVED_NAME_SUFFIX);


    // 审批 -> 决策
    final var approval2Decision = buildSequenceFlow(approvalTask, decisionGateway);
    // 决策 -> 通过
    final var decision2Approved = buildSequenceFlow(decisionGateway, approvedGateway, variableEqualsExpression(approvedVar, true));
    // 决策 -> 驳回
    final var decision2Rejected = buildSequenceFlow(
        decisionGateway.getId(),
        Constants.SpecialNode.FAIL_NODE_ID,
        variableEqualsExpression(approvedVar, false) + " && " + variableEqualsExpression(approvalActionVar, Constants.Value.REJECTED));
    // 决策 -> 退回
    final var prevApprovalNode = getPrevApprovalNode(node);
    final var rollbackId = prevApprovalNode == null
        ? Constants.SpecialNode.FAIL_NODE_ID
        : context.getEntryPoint(prevApprovalNode.getFlowNode().getId());
    final var decision2Rollback = buildSequenceFlow(
        decisionGateway.getId(),
        rollbackId,
        variableEqualsExpression(approvedVar, false) + " && " + variableEqualsExpression(approvalActionVar, Constants.Value.ROLLBACK));

    // 添加节点
    process.addFlowElement(approvalTask);
    process.addFlowElement(decisionGateway);
    process.addFlowElement(approvedGateway);

    // 添加连线
    process.addFlowElement(approval2Decision);
    process.addFlowElement(decision2Approved);
    process.addFlowElement(decision2Rejected);
    process.addFlowElement(decision2Rollback);

    // 登记入口和出口
    context.setEntryPoint(flowNode.getId(), approvalTask.getId());
    context.setExitPoint(flowNode.getId(), approvedGateway.getId());

    // 无回调
    return null;
  }

  @Override
  public boolean supportNode(FlowNode flowNode) {
    return flowNode instanceof SingleApprovalNode;
  }
}
