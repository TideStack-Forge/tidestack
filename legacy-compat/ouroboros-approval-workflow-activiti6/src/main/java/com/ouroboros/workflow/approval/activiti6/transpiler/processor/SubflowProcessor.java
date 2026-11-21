package com.ouroboros.workflow.approval.activiti6.transpiler.processor;

import static com.ouroboros.workflow.approval.activiti6.util.BpmnUtil.*;

import java.util.ArrayList;

import org.activiti.bpmn.model.*;
import org.activiti.bpmn.model.Process;

import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.IOParameterSetting;
import com.ouroboros.workflow.approval.definition.nodes.SubflowNode;
import com.ouroboros.workflow.approval.transpiler.TranspileContext;
import com.ouroboros.workflow.approval.util.TreeGraphNode;

public class SubflowProcessor extends Activiti6AbstractProcessor {
  @Override
  Runnable doProcess(TranspileContext<BpmnModel> context, Process process, TreeGraphNode node) {
    final var flowNode = (SubflowNode) node.getFlowNode();
    final var approvedVariableName = flowNode.getId() + Constants.Variable.APPROVED_SUFFIX;

    //子流程调用
    final var callFlowTask = new CallActivity();
    //审批结果判断
    final var decisionGateway = new ExclusiveGateway();
    //审批通过
    final var approvedGateway = new InclusiveGateway();

    final var inParameters = new ArrayList<>(flowNode.getInParameters());
    final var outParameters = new ArrayList<>(flowNode.getOutParameters());

    // 将父流程的starter传递给子流程
    inParameters.add(new IOParameterSetting(Constants.Variable.PROCESS_STARTER, "", Constants.Variable.PROCESS_STARTER));
    inParameters.add(new IOParameterSetting(
        Constants.Variable.USER_SELECTED_ASSIGNEES,
        "",
        Constants.Variable.USER_SELECTED_ASSIGNEES));
    inParameters.add(new IOParameterSetting(
        Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID,
        "",
        Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID));
    inParameters.add(new IOParameterSetting(null, "true", Constants.Variable.HAS_PARENT_FLOW));
    // 子流程的结果传出来，作为当前节点的审批结果
    outParameters.add(new IOParameterSetting(Constants.Variable.PROCESS_APPROVED, "", approvedVariableName));
    outParameters.add(new IOParameterSetting(
        Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID,
        "",
        Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID));

    callFlowTask.setId(flowNode.getId());
    callFlowTask.setName(flowNode.getName());
    callFlowTask.setCalledElement(flowNode.getSubFlowKey());
    callFlowTask.setInParameters(mapIOParameter(inParameters));
    callFlowTask.setOutParameters(mapIOParameter(outParameters));
    saveRejectAction(callFlowTask, flowNode.getRejectAction());

    decisionGateway.setId(flowNode.getId() + Constants.SpecialNode.DECISION_ID_SUFFIX);
    decisionGateway.setName(flowNode.getName() + Constants.SpecialNode.DECISION_NAME_SUFFIX);

    approvedGateway.setId(flowNode.getId() + Constants.SpecialNode.APPROVED_ID_SUFFIX);
    approvedGateway.setName(flowNode.getName() + Constants.SpecialNode.APPROVED_NAME_SUFFIX);

    // 调用子流程 -> 判断网关
    final var callFlow2Decision = buildSequenceFlow(callFlowTask, decisionGateway);
    // 审批通过
    final var decision2Approved = buildSequenceFlow(decisionGateway, approvedGateway, variableEqualsExpression(approvedVariableName, true));
    // 审批拒绝
    final var prevApprovalNode = this.getPrevApprovalNode(node);
    final var rejectedTarget = Constants.Value.ROLLBACK.equalsIgnoreCase(flowNode.getRejectAction()) && prevApprovalNode != null
        ? context.getEntryPoint(prevApprovalNode.getFlowNode().getId())
        : Constants.SpecialNode.FAIL_NODE_ID;
    final var decision2Rejected = buildSequenceFlow(decisionGateway.getId(), rejectedTarget, variableEqualsExpression(approvedVariableName, false));

    // 添加节点
    process.addFlowElement(callFlowTask);
    process.addFlowElement(decisionGateway);
    process.addFlowElement(approvedGateway);

    // 添加连线
    process.addFlowElement(callFlow2Decision);
    process.addFlowElement(decision2Approved);
    process.addFlowElement(decision2Rejected);

    // 登记入口和出口
    context.setEntryPoint(flowNode.getId(), callFlowTask.getId());
    context.setExitPoint(flowNode.getId(), approvedGateway.getId());

    return null;
  }

  @Override
  public boolean supportNode(FlowNode flowNode) {
    return flowNode instanceof SubflowNode;
  }
}
