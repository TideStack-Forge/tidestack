package com.ouroboros.workflow.approval.activiti6.transpiler.processor;

import static com.ouroboros.workflow.approval.activiti6.util.BpmnUtil.buildCustomProperty;
import static com.ouroboros.workflow.approval.activiti6.util.BpmnUtil.buildSequenceFlow;
import static com.ouroboros.workflow.approval.activiti6.util.BpmnUtil.useDelegateBean;

import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.ServiceTask;

import com.ouroboros.workflow.approval.activiti6.delegate.SetProcessOutcomeServiceTask;
import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.nodes.EndNode;
import com.ouroboros.workflow.approval.transpiler.TranspileContext;
import com.ouroboros.workflow.approval.util.TreeGraphNode;

public class EndNodeProcessor extends Activiti6AbstractProcessor {
  @Override
  Runnable doProcess(TranspileContext<BpmnModel> context, Process process, TreeGraphNode node) {
    EndNode flowNode = (EndNode) node.getFlowNode();
    ServiceTask outcomeTask = new ServiceTask();
    outcomeTask.setId(flowNode.getId() + Constants.SpecialNode.OUTCOME_ID_SUFFIX);
    outcomeTask.setName(flowNode.getName());
    useDelegateBean(outcomeTask, SetProcessOutcomeServiceTask.BEAN_NAME);
    outcomeTask.getCustomProperties().add(buildCustomProperty(Constants.Property.PROCESS_OUTCOME, flowNode.resolveOutcome()));

    process.addFlowElement(outcomeTask);
    process.addFlowElement(buildSequenceFlow(outcomeTask.getId(), Constants.SpecialNode.END_EVENT_ID));

    context.setEntryPoint(flowNode.getId(), outcomeTask.getId());
    context.setExitPoint(flowNode.getId(), Constants.SpecialNode.END_EVENT_ID);
    return null;
  }

  @Override
  public boolean supportNode(FlowNode flowNode) {
    return flowNode instanceof EndNode;
  }
}
