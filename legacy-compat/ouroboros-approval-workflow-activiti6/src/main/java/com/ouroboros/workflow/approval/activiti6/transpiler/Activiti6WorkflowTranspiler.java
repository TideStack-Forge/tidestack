package com.ouroboros.workflow.approval.activiti6.transpiler;

import static com.ouroboros.workflow.approval.activiti6.util.BpmnUtil.buildSequenceFlow;
import static com.ouroboros.workflow.approval.activiti6.util.BpmnUtil.buildActivitiListener;
import static com.ouroboros.workflow.approval.activiti6.util.BpmnUtil.saveFlowDefinitionRawJson;
import static com.ouroboros.workflow.approval.activiti6.util.BpmnUtil.useDelegateBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.activiti.bpmn.BpmnAutoLayout;
import org.activiti.bpmn.model.ActivitiListener;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.EndEvent;
import org.activiti.bpmn.model.ExclusiveGateway;
import org.activiti.bpmn.model.Gateway;
import org.activiti.bpmn.model.InclusiveGateway;
import org.activiti.bpmn.model.ParallelGateway;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.SequenceFlow;
import org.activiti.bpmn.model.ServiceTask;
import org.activiti.engine.delegate.ExecutionListener;

import io.vavr.control.Try;

import com.ouroboros.util.SPI;
import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.definition.codec.CompositeFlowDefinitionCodec;
import com.ouroboros.workflow.approval.activiti6.delegate.FailServiceTask;
import com.ouroboros.workflow.approval.activiti6.listener.FlowEndExecutionListener;
import com.ouroboros.workflow.approval.semantics.SplitType;
import com.ouroboros.workflow.approval.semantics.StableSplitSemantics;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.nodes.EndNode;
import com.ouroboros.workflow.approval.definition.condition.Condition;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeV2;
import com.ouroboros.workflow.approval.transpiler.AbstractApprovalWorkflowTranspiler;
import com.ouroboros.workflow.approval.transpiler.ApprovalWorkflowTranspilerBuilder;
import com.ouroboros.workflow.approval.transpiler.FlowNodeTranspileProcessor;
import com.ouroboros.workflow.approval.transpiler.TranspileContext;

public class Activiti6WorkflowTranspiler extends AbstractApprovalWorkflowTranspiler<BpmnModel> {
  private static final CompositeFlowDefinitionCodec FLOW_DEFINITION_CODEC = new CompositeFlowDefinitionCodec();

  @Override
  protected List<FlowNodeTranspileProcessor<BpmnModel>> getTranspileProcessors() {
    BpmnModel bpmnModel = new BpmnModel();
    return (List) SPI.getServiceStream(FlowNodeTranspileProcessor.class)
        .filter(processor -> processor.supportModel(bpmnModel))
        .collect(Collectors.toList());
  }

  @Override
  protected TranspileContext<BpmnModel> initContext(StructuredFlowDefinition flowDefinition) {
    BpmnModel model = new BpmnModel();
    Process process = new Process();
    TranspileContext<BpmnModel> context = new TranspileContext<BpmnModel>(flowDefinition, model);

    ServiceTask failServiceTask = new ServiceTask();
    EndEvent endEvent = new EndEvent();
    ArrayList<ActivitiListener> endEventListeners = new ArrayList<ActivitiListener>();

    model.setTargetNamespace(Constants.OUROBOROS_NAMESPACE + "/" + flowDefinition.getCategory());
    model.addProcess(process);

    process.setId(flowDefinition.getId());
    process.setName(flowDefinition.getName());
    process.setDocumentation(flowDefinition.getDescription());
    saveFlowDefinitionRawJson(process, toCanonicalFlowDefinitionJson(context.getStructuredFlowDefinition()));

    // 定义审批失败节点，所有不通过的流程都会走到这个节点
    // 该节点会将流程审批通过变量设置为false，然后流程结束
    failServiceTask.setId(Constants.SpecialNode.FAIL_NODE_ID);
    failServiceTask.setName(Constants.SpecialNode.FAIL_NODE_NAME);
    useDelegateBean(failServiceTask, FailServiceTask.BEAN_NAME);

    endEvent.setId(Constants.SpecialNode.END_EVENT_ID);
    endEvent.setName(Constants.SpecialNode.END_EVENT_NAME);
    endEvent.setExecutionListeners(endEventListeners);

    ActivitiListener endEventListener = buildActivitiListener(ExecutionListener.EVENTNAME_END, FlowEndExecutionListener.BEAN_NAME);

    endEventListeners.add(endEventListener);

    // 审批失败 -> 结束
    SequenceFlow fail2EndEvent = buildSequenceFlow(failServiceTask, endEvent);

    // 添加节点
    process.addFlowElement(failServiceTask);
    process.addFlowElement(endEvent);

    // 添加连接
    process.addFlowElement(fail2EndEvent);

    return context;
  }

  @Override
  protected void postTranspile(TranspileContext<BpmnModel> context) {
    BpmnModel model = context.getModel();
    Process process = model.getMainProcess();

    connectStructuredEdges(context, process);
    connectTerminalNodes(context, process);

    // 自动布局
    // 由于 Activiti6 的自动布局可能会触发 Comparison method violates its general contract! 异常
    // 所以这里使用 Try.run() 包装一下
    Try.run(() -> new BpmnAutoLayout(model).execute());
  }

  private String toCanonicalFlowDefinitionJson(StructuredFlowDefinition flowDefinition) {
    return FLOW_DEFINITION_CODEC.encodeV2(flowDefinition)
        .flatMap(Json::tryToJsonString)
        .get();
  }

  private void connectStructuredEdges(TranspileContext<BpmnModel> context, Process process) {
    Map<String, List<FlowEdgeV2>> outgoing = new LinkedHashMap<String, List<FlowEdgeV2>>();
    for (FlowEdgeV2 edge : context.getStructuredFlowDefinition().getEdges()) {
      outgoing.computeIfAbsent(edge.getSourceId(), key -> new ArrayList<FlowEdgeV2>()).add(edge);
    }

    for (FlowNode node : context.getStructuredFlowDefinition().getNodes()) {
      String sourceId = node.getId();
      String sourceExit = context.getExitPoint(sourceId);
      if (sourceExit == null) {
        continue;
      }
      List<FlowEdgeV2> outgoingEdges = outgoing.getOrDefault(sourceId, Collections.<FlowEdgeV2>emptyList());
      if (outgoingEdges.isEmpty()) {
        continue;
      }

      if (outgoingEdges.size() == 1) {
        FlowEdgeV2 edge = outgoingEdges.get(0);
        String targetEntry = context.getEntryPoint(edge.getTargetId());
        if (targetEntry != null) {
          process.addFlowElement(buildFlow(sourceExit, targetEntry, edge));
        }
        continue;
      }

      Gateway gateway = inferGatewayType(context, outgoingEdges, sourceId, node.getName());
      process.addFlowElement(gateway);
      process.addFlowElement(buildSequenceFlow(sourceExit, gateway.getId()));

      for (FlowEdgeV2 edge : outgoingEdges) {
        String targetEntry = context.getEntryPoint(edge.getTargetId());
        if (targetEntry != null) {
          process.addFlowElement(buildFlow(gateway.getId(), targetEntry, edge));
        }
      }
    }
  }

  private Gateway inferGatewayType(TranspileContext<BpmnModel> context, List<FlowEdgeV2> outgoingEdges, String sourceId, String nodeName) {
    SplitType gatewayType = StableSplitSemantics.inferSplitType(context.getStructuredFlowDefinition(), outgoingEdges);

    Gateway gateway;
    if (gatewayType == SplitType.AND) {
      // 全部出边无条件 → ParallelGateway (AND)
      gateway = new ParallelGateway();
    } else if (gatewayType == SplitType.XOR) {
      // 全部出边有条件 → ExclusiveGateway (XOR)
      gateway = new ExclusiveGateway();
    } else {
      // 混合（部分有条件、部分无条件） → InclusiveGateway (OR)
      gateway = new InclusiveGateway();
    }

    gateway.setId(sourceId + "__" + gatewayType.toString().toLowerCase() + "Split");
    gateway.setName(nodeName + " " + gatewayType + " Split");
    return gateway;
  }

  private SequenceFlow buildFlow(String source, String target, FlowEdgeV2 edge) {
    Condition cond = edge.getCondition();
    String conditionExpr = cond == null ? null : cond.toExpression();
    if (conditionExpr == null || conditionExpr.trim().isEmpty()) {
      return buildSequenceFlow(source, target);
    }
    return buildSequenceFlow(source, target, conditionExpr);
  }

  private void connectTerminalNodes(TranspileContext<BpmnModel> context, Process process) {
    for (FlowNode node : context.getStructuredFlowDefinition().getNodes()) {
      if (node instanceof EndNode) {
        continue;
      }
      if (!context.getStructuredFlowDefinition().getOutgoingEdges(node.getId()).isEmpty()) {
        continue;
      }
      String exitPoint = context.getExitPoint(node.getId());
      if (exitPoint != null && !Constants.SpecialNode.END_EVENT_ID.equals(exitPoint)) {
        process.addFlowElement(buildSequenceFlow(exitPoint, Constants.SpecialNode.END_EVENT_ID));
      }
    }
  }

  public static class Builder implements ApprovalWorkflowTranspilerBuilder<BpmnModel> {
    @Override
    public Activiti6WorkflowTranspiler build() {
      return new Activiti6WorkflowTranspiler();
    }
  }
}
