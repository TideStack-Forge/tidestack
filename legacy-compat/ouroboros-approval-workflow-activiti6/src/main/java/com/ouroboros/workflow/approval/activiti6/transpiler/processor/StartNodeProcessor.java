package com.ouroboros.workflow.approval.activiti6.transpiler.processor;

import static com.ouroboros.workflow.approval.activiti6.util.BpmnUtil.*;

import java.util.ArrayList;

import org.activiti.bpmn.model.*;
import org.activiti.bpmn.model.Process;
import org.activiti.engine.delegate.ExecutionListener;

import com.ouroboros.workflow.approval.activiti6.delegate.InitializeFlowServiceTask;
import com.ouroboros.workflow.approval.activiti6.listener.FlowStartExecutionListener;
import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.nodes.StartNode;
import com.ouroboros.workflow.approval.transpiler.TranspileContext;
import com.ouroboros.workflow.approval.util.TreeGraphNode;

public class StartNodeProcessor extends Activiti6AbstractProcessor {
  @Override
  Runnable doProcess(TranspileContext<BpmnModel> context, Process process, TreeGraphNode node) {
    final var flowNode = node.getFlowNode();
    final var formKey = context.getStructuredFlowDefinition().getFormKey();

    //开始事件
    final var startEvent = new StartEvent();
    //InitialTask 任务
    final var initializeFlowServiceTask = new ServiceTask();
    //决定是否跳过起始任务
    final var initialForkGateway = new ExclusiveGateway();
    //合流网关
    final var initialJoinGateway = new InclusiveGateway();
    //起始任务
    final var startUserTask = new UserTask();

    final var executionListeners = new ArrayList<ActivitiListener>();

    startEvent.setId(Constants.SpecialNode.START_EVENT_ID);
    startEvent.setName(Constants.SpecialNode.START_EVENT_NAME);
    startEvent.setExecutionListeners(executionListeners);

    // 当作为子流程被调用时监听器
    executionListeners.add(buildActivitiListener(ExecutionListener.EVENTNAME_START, FlowStartExecutionListener.BEAN_NAME));

    initializeFlowServiceTask.setId(Constants.SpecialNode.INITIALIZE_FLOW_ID);
    initializeFlowServiceTask.setName(Constants.SpecialNode.INITIALIZE_FLOW_NAME);
    useDelegateBean(initializeFlowServiceTask, InitializeFlowServiceTask.BEAN_NAME);

    initialForkGateway.setId(Constants.SpecialNode.INITIAL_FORK_GATEWAY_ID);
    initialForkGateway.setName(Constants.SpecialNode.INITIAL_FORK_GATEWAY_NAME);

    startUserTask.setId(flowNode.getId());
    startUserTask.setName(flowNode.getName());
    startUserTask.setFormKey(formKey);
    startUserTask.setFormProperties(mapFormProperties(flowNode.getPermission()));

    initialJoinGateway.setId(Constants.SpecialNode.INITIAL_JOIN_GATEWAY_ID);
    initialJoinGateway.setName(Constants.SpecialNode.INITIAL_JOIN_GATEWAY_NAME);

    // 开始 -> 初始化
    final var start2Initialize = buildSequenceFlow(startEvent, initializeFlowServiceTask);
    // 初始化 -> 分流
    final var initial2Fork = buildSequenceFlow(initializeFlowServiceTask, initialForkGateway);
    // 分流 -> 起始任务 -> 合流
    final var fork2UserTask = buildSequenceFlow(initialForkGateway, startUserTask, variableEqualsExpression(Constants.Variable.HAS_PARENT_FLOW, false));
    final var userTask2Join = buildSequenceFlow(startUserTask, initialJoinGateway);
    // 分流 -> 合流
    final var fork2Join = buildSequenceFlow(initialForkGateway, initialJoinGateway, variableEqualsExpression(Constants.Variable.HAS_PARENT_FLOW, true));

    // 添加节点
    process.addFlowElement(startEvent);
    process.addFlowElement(initializeFlowServiceTask);
    process.addFlowElement(initialForkGateway);
    process.addFlowElement(startUserTask);
    process.addFlowElement(initialJoinGateway);

    // 添加连线
    process.addFlowElement(start2Initialize);
    process.addFlowElement(initial2Fork);
    process.addFlowElement(fork2UserTask);
    process.addFlowElement(fork2Join);
    process.addFlowElement(userTask2Join);

    // 登记出入口
    context.setEntryPoint(flowNode.getId(), startUserTask.getId());
    context.setExitPoint(flowNode.getId(), initialJoinGateway.getId());

    return null;
  }

  @Override
  public boolean supportNode(FlowNode flowNode) {
    return flowNode instanceof StartNode;
  }
}
