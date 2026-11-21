package com.ouroboros.workflow.approval.activiti6.transpiler.processor;

import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.Process;

import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;
import com.ouroboros.workflow.approval.transpiler.FlowNodeTranspileProcessor;
import com.ouroboros.workflow.approval.transpiler.TranspileContext;
import com.ouroboros.workflow.approval.util.TreeGraphNode;

public abstract class Activiti6AbstractProcessor implements FlowNodeTranspileProcessor<BpmnModel> {
  @Override
  public boolean supportModel(Object flowModel) {
    return flowModel instanceof BpmnModel;
  }

  @Override
  public boolean supportNode(TreeGraphNode flowNode) {
    return supportNode(flowNode.getFlowNode());
  }

  @Override
  final public Runnable process(TranspileContext<BpmnModel> context, TreeGraphNode node) {
    final var process = context.getModel().getMainProcess();
    return doProcess(context, process, node);
  }

  abstract Runnable doProcess(TranspileContext<BpmnModel> context, Process process, TreeGraphNode node);

  abstract boolean supportNode(FlowNode flowNode);

  protected TreeGraphNode getPrevApprovalNode(TreeGraphNode node) {
    if (node == null) {
      return null;
    }
    TreeGraphNode prevNode = node.getPrevNode();
    // 前置节点如果为空，则取父节点
    if (prevNode == null) {
      return getPrevApprovalNode(node.getParentNode());
    }
    // 只有审批节点才是有效的前置节点
    if (prevNode.getFlowNode() instanceof ApprovalNode) {
      return prevNode;
    }
    // 只要不是审批节点就继续递归取前置节点
    return getPrevApprovalNode(prevNode);
  }
}
