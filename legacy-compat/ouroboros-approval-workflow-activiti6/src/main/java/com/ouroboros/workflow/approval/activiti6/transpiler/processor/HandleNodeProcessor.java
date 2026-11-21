package com.ouroboros.workflow.approval.activiti6.transpiler.processor;

import static com.ouroboros.workflow.approval.activiti6.util.BpmnUtil.buildActivitiListener;
import static com.ouroboros.workflow.approval.activiti6.util.BpmnUtil.mapFormProperties;
import static com.ouroboros.workflow.approval.activiti6.util.BpmnUtil.saveCandidates;
import static com.ouroboros.workflow.approval.activiti6.util.BpmnUtil.saveTaskKind;

import java.util.ArrayList;

import org.activiti.bpmn.model.ActivitiListener;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.UserTask;
import org.activiti.engine.delegate.TaskListener;

import com.ouroboros.workflow.approval.activiti6.listener.CreateSingleApprovalTaskListener;
import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.nodes.HandleNode;
import com.ouroboros.workflow.approval.transpiler.TranspileContext;
import com.ouroboros.workflow.approval.util.TreeGraphNode;

public class HandleNodeProcessor extends Activiti6AbstractProcessor {
  @Override
  Runnable doProcess(TranspileContext<BpmnModel> context, Process process, TreeGraphNode node) {
    final var flowNode = (HandleNode) node.getFlowNode();
    final var formKey = context.getStructuredFlowDefinition().getFormKey();

    final var handleTask = new UserTask();
    final var taskListeners = new ArrayList<ActivitiListener>();

    handleTask.setId(flowNode.getId());
    handleTask.setName(flowNode.getName());
    handleTask.setFormKey(formKey);
    handleTask.setTaskListeners(taskListeners);
    handleTask.setFormProperties(mapFormProperties(flowNode.getPermission()));
    saveCandidates(handleTask, flowNode.getCandidates());
    saveTaskKind(handleTask, Constants.Value.TASK_KIND_HANDLE);

    taskListeners.add(buildActivitiListener(TaskListener.EVENTNAME_CREATE, CreateSingleApprovalTaskListener.BEAN_NAME));

    process.addFlowElement(handleTask);

    context.setEntryPoint(flowNode.getId(), handleTask.getId());
    context.setExitPoint(flowNode.getId(), handleTask.getId());
    return null;
  }

  @Override
  public boolean supportNode(FlowNode flowNode) {
    return flowNode instanceof HandleNode;
  }
}
