package com.ouroboros.workflow.approval.activiti6.transpiler.processor;

import static com.ouroboros.workflow.approval.activiti6.util.BpmnUtil.buildLargeTextExtensionElement;
import static com.ouroboros.workflow.approval.activiti6.util.BpmnUtil.useDelegateBean;

import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.ServiceTask;

import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.activiti6.delegate.CcServiceTask;
import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.nodes.CcNode;
import com.ouroboros.workflow.approval.transpiler.TranspileContext;
import com.ouroboros.workflow.approval.util.TreeGraphNode;

public class CcNodeProcessor extends Activiti6AbstractProcessor {
  @Override
  Runnable doProcess(TranspileContext<BpmnModel> context, Process process, TreeGraphNode node) {
    final var flowNode = (CcNode) node.getFlowNode();
    final var ccServiceTask = new ServiceTask();
    final var extensionElement = buildLargeTextExtensionElement("cc", Json.toJsonString(flowNode));

    ccServiceTask.setId(flowNode.getId() + Constants.SpecialNode.APPROVED_ID_SUFFIX);
    ccServiceTask.setName(flowNode.getName());
    useDelegateBean(ccServiceTask, CcServiceTask.BEAN_NAME);
    ccServiceTask.addExtensionElement(extensionElement);

    process.addFlowElement(ccServiceTask);

    context.setEntryPoint(flowNode.getId(), ccServiceTask.getId());
    context.setExitPoint(flowNode.getId(), ccServiceTask.getId());

    return null;
  }

  @Override
  public boolean supportNode(FlowNode flowNode) {
    return flowNode instanceof CcNode;
  }
}
