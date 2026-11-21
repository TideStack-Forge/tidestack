package com.ouroboros.workflow.approval.activiti6.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.activiti.bpmn.model.*;
import org.activiti.bpmn.model.Process;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.repository.ProcessDefinition;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.control.Try;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ouroboros.exception.InternalException;
import com.ouroboros.util.Json;
import com.ouroboros.util.SPI;
import com.ouroboros.workflow.approval.CandidateUserResolver;
import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.definition.Candidate;
import com.ouroboros.workflow.approval.definition.FlowDefinition;
import com.ouroboros.workflow.approval.definition.IOParameterSetting;
import com.ouroboros.workflow.approval.definition.NodeFormFieldPermission;
import com.ouroboros.workflow.approval.definition.codec.CompositeFlowDefinitionCodec;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.util.TreeGraphNode;

public class BpmnUtil {
  final static Logger logger = LoggerFactory.getLogger(BpmnUtil.class);
  final static List<CandidateUserResolver> candidateUserResolvers = SPI
      .getCachedReversedServiceStream(CandidateUserResolver.class)
      .collect(Collectors.toList());
  final static CompositeFlowDefinitionCodec flowDefinitionCodec = new CompositeFlowDefinitionCodec();

  //连接两个节点(没有条件)
  static public SequenceFlow buildSequenceFlow(String source, String target) {
    if (StringUtils.isAnyBlank(source, target)) {
      throw new IllegalArgumentException("source, target can not be null");
    }

    SequenceFlow flow = new SequenceFlow();
    flow.setSourceRef(source);
    flow.setTargetRef(target);
    return flow;
  }

  //连接两个节点(有条件)
  static public SequenceFlow buildSequenceFlow(String source, String target, String condition) {
    if (StringUtils.isAnyBlank(source, target, condition)) {
      throw new IllegalArgumentException("source, target, condition can not be null");
    }

    SequenceFlow flow = new SequenceFlow();
    flow.setSourceRef(source);
    flow.setTargetRef(target);
    flow.setConditionExpression("${" + condition + "}");
    return flow;
  }

  static public SequenceFlow buildSequenceFlow(org.activiti.bpmn.model.FlowNode source, org.activiti.bpmn.model.FlowNode target) {
    return buildSequenceFlow(source.getId(), target.getId());
  }


  static public SequenceFlow buildSequenceFlow(org.activiti.bpmn.model.FlowNode source, org.activiti.bpmn.model.FlowNode target, String condition) {
    return buildSequenceFlow(source.getId(), target.getId(), condition);
  }

  static public String executionVariableExpression(String variableName) {
    if (StringUtils.isBlank(variableName)) {
      throw new IllegalArgumentException("variableName can not be null");
    }
    return "execution.getVariable('" + StringUtils.replace(variableName, "'", "\\'") + "')";
  }

  static public String variableEqualsExpression(String variableName, boolean expectedValue) {
    return executionVariableExpression(variableName) + "==" + expectedValue;
  }

  static public String variableEqualsExpression(String variableName, String expectedValue) {
    return executionVariableExpression(variableName)
        + "==\""
        + StringUtils.replace(StringUtils.defaultString(expectedValue), "\"", "\\\"")
        + "\"";
  }

  static public CustomProperty buildCustomProperty(String name, String value) {
    CustomProperty customProperty = new CustomProperty();
    customProperty.setName(name);
    customProperty.setSimpleValue(value);
    return customProperty;
  }

  static public ExtensionElement buildLargeTextExtensionElement(String name, String value) {
    var extensionElement = new ExtensionElement();
    extensionElement.setName(name);
    extensionElement.setNamespace(Constants.OUROBOROS_NAMESPACE);
    extensionElement.setNamespacePrefix(Constants.OUROBOROS_NAMESPACE_PREFIX);
    extensionElement.setElementText(value);
    return extensionElement;
  }

  static public void addExtensionElement(BaseElement element, String name, String value) {
    var extensionElement = buildLargeTextExtensionElement(name, value);
    element.addExtensionElement(extensionElement);
  }

  static public List<String> getExtensionElementTexts(BaseElement element, String name) {
    var extensionElements = element.getExtensionElements().get(name);
    if (extensionElements == null) {
      return Collections.emptyList();
    }
    return extensionElements.stream()
        .filter(ele -> StringUtils.isNotEmpty(ele.getNamespace()) && ele.getNamespace().startsWith(Constants.OUROBOROS_NAMESPACE))
        .map(ExtensionElement::getElementText)
        .collect(Collectors.toList());
  }

  static public ActivitiListener buildActivitiListener(String event, String beanName) {
    ActivitiListener activitiListener = new ActivitiListener();
    activitiListener.setEvent(event);
    activitiListener.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);
    activitiListener.setImplementation("${" + beanName + "}");
    return activitiListener;
  }

  static public void useDelegateBean(ServiceTask serviceTask, String beanName) {
    serviceTask.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);
    serviceTask.setImplementation("${" + beanName + "}");
  }


  /**
   * 审批、会签节点的表单权限，这个权限仅针对当前任务的所有实例
   *
   * @param permission
   * @return
   */
  static public List<FormProperty> mapFormProperties(List<NodeFormFieldPermission> permission) {
    if (permission == null) {
      return Collections.emptyList();
    }

    List<FormProperty> result = new ArrayList<>();
    for (NodeFormFieldPermission p : permission) {
      FormProperty formProperty = new FormProperty();
      formProperty.setName(p.getLabel());
      formProperty.setId(p.getName());
      formProperty.setWriteable(p.isWritable());
      formProperty.setReadable(p.isVisible());
      formProperty.setRequired(p.isRequired());
      result.add(formProperty);
    }
    return result;
  }


  /**
   * 传入子流程参数配置，转化为activiti的参数
   *
   * @param ioParameterSettings
   * @return
   */
  public static List<IOParameter> mapIOParameter(List<IOParameterSetting> ioParameterSettings) {
    if (ioParameterSettings == null) {
      return Collections.emptyList();
    }
    return ioParameterSettings.stream()
        .map(param -> {
          var ioParameter = new IOParameter();
          ioParameter.setSourceExpression(param.getExpression());
          ioParameter.setSource(param.getSource());
          ioParameter.setTarget(param.getTarget());
          return ioParameter;
        })
        .collect(Collectors.toList());
  }

  static public void saveCandidates(Task task, List<Candidate> candidates) {
    var json = candidates == null ? "[]" : Json.toJsonString(candidates);
    addExtensionElement(task, "candidates", json);
  }

  static public List<Candidate> readCandidates(Task task) {
    var json = getExtensionElementTexts(task, "candidates").stream().findFirst().orElse(null);
    if (StringUtils.isBlank(json)) {
      return Collections.emptyList();
    }
    return Json.tryToList(new TypeReference<List<Candidate>>() {
        }, json)
        .toEither()
        .peekLeft(e -> {
          logger.error("读取候选人信息失败", e);
        })
        .getOrElseGet(e -> Collections.emptyList());
  }

  static public List<String> getCandidateUsers(DelegateTask task) {
    var userTask = (UserTask) task.getExecution().getCurrentFlowElement();
    var candidates = readCandidates(userTask);
    return getCandidateUsers(candidates, task.getProcessInstanceId(), task.getId(), task.getName());
  }

  static public List<String> getCandidateUsers(DelegateExecution execution, Task task) {
    var candidates = readCandidates(task);
    return getCandidateUsers(candidates, execution.getProcessInstanceId(), null, task.getName());
  }

  static public List<String> getCandidateUsers(List<Candidate> candidates, String processInstanceId, String taskInstanceId, String taskName) {
    var userList = candidates.stream()
        .flatMap(candidate -> {
          return candidateUserResolvers.stream()
              .filter(resolver -> resolver.support(candidate))
              .findFirst()
              .map(resolver -> resolver.resolve(candidate, processInstanceId, taskInstanceId, taskName))
              .map(List::stream)
              .orElseGet(Stream::empty);
        })
        .collect(Collectors.toList());
    return userList;
  }

  static public void saveRejectAction(Activity activity, String rejectAction) {
    addExtensionElement(activity, "rejectAction", rejectAction);
  }

  static public String readRejectAction(Activity activity) {
    return getExtensionElementTexts(activity, "rejectAction").stream().findFirst().orElse(null);
  }

  static public void saveActions(Activity activity, List<String> actions) {
    addExtensionElement(activity, "actions", Json.toJsonString(actions));
  }

  static public void saveTaskKind(Activity activity, String taskKind) {
    addExtensionElement(activity, "taskKind", taskKind);
  }

  static public String readTaskKind(Activity activity) {
    return getExtensionElementTexts(activity, "taskKind").stream().findFirst().orElse(null);
  }

  static public List<String> readActions(Activity activity) {
    var actions = getExtensionElementTexts(activity, "actions").stream().findFirst().orElse(null);
    if (StringUtils.isBlank(actions)) {
      return Collections.emptyList();
    }
    return Json.toList(new TypeReference<>() {
    }, actions);
  }

  static public void saveFlowDefinition(Process process, FlowDefinition flowDefinition) {
    var json = Json.toJsonString(flowDefinition);
    addExtensionElement(process, "flowDefinition", json);
  }

  /**
   * Compatibility-only BPMN payload for V1 consumers. Default runtime/transpiler mainline should
   * persist and restore definitions from {@code flowDefinitionRawJson} instead.
   */
  @Deprecated
  static public void saveCompatibilityFlowDefinition(Process process, FlowDefinition flowDefinition) {
    saveFlowDefinition(process, flowDefinition);
  }

  static public void saveFlowDefinitionRawJson(Process process, String rawJson) {
    process.getExtensionElements().remove("flowDefinitionRawJson");
    addExtensionElement(process, "flowDefinitionRawJson", rawJson);
  }

  static public String readFlowDefinitionRawJson(Process process) {
    return Stream.of("flowDefinitionRawJson", "flowDefinition")
        .flatMap(name -> getExtensionElementTexts(process, name).stream())
        .filter(StringUtils::isNotBlank)
        .findFirst()
        .orElse(null);
  }

  static public String readFlowDefinitionRawJson(BpmnModel bpmnModel) {
    return readFlowDefinitionRawJson(bpmnModel.getProcesses().get(0));
  }

  static public StructuredFlowDefinition readStructuredFlowDefinition(Process process) {
    var rawJson = readFlowDefinitionRawJson(process);
    if (StringUtils.isBlank(rawJson)) {
      return null;
    }
    return flowDefinitionCodec.decode(rawJson)
        .onFailure(e -> logger.error("读取流程定义失败", e))
        .getOrElse((StructuredFlowDefinition) null);
  }

  static public StructuredFlowDefinition readStructuredFlowDefinition(BpmnModel bpmnModel) {
    return readStructuredFlowDefinition(bpmnModel.getProcesses().get(0));
  }

  static public FlowDefinition readFlowDefinition(Process process) {
    var structuredDefinition = readStructuredFlowDefinition(process);
    if (structuredDefinition == null) {
      return null;
    }
    return flowDefinitionCodec.encodeV1(structuredDefinition)
        .onFailure(e -> logger.error("转换V1流程定义失败", e))
        .getOrElse((FlowDefinition) null);
  }

  static public FlowDefinition readFlowDefinition(BpmnModel bpmnModel) {
    return readFlowDefinition(bpmnModel.getProcesses().get(0));
  }

  static public FlowDefinition.Notification readNotification(
      RepositoryService repositoryService,
      String processDefinitionId) {
    if (repositoryService == null || StringUtils.isBlank(processDefinitionId)) {
      return null;
    }

    return Optional.ofNullable(repositoryService.getBpmnModel(processDefinitionId))
        .map(BpmnUtil::readStructuredFlowDefinition)
        .map(StructuredFlowDefinition::getNotification)
        .orElse(null);
  }

  /**
   * Compatibility-only V1 projection facade. Default runtime mainline should prefer
   * {@link #readStructuredFlowDefinition(Process)} / {@link #readFlowDefinitionRawJson(Process)}.
   */
  @Deprecated
  static public FlowDefinition readCompatibilityFlowDefinition(Process process) {
    return readFlowDefinition(process);
  }

  /**
   * Compatibility-only V1 projection facade. Default runtime mainline should prefer
   * {@link #readStructuredFlowDefinition(BpmnModel)} / {@link #readFlowDefinitionRawJson(BpmnModel)}.
   */
  @Deprecated
  static public FlowDefinition readCompatibilityFlowDefinition(BpmnModel bpmnModel) {
    return readFlowDefinition(bpmnModel);
  }

  static public Supplier<com.ouroboros.workflow.approval.definition.FlowNode> getFlowNodeSupplier(
      RepositoryService repositoryService,
      String processDefinitionId,
      String flowNodeId
  ) {
    return () -> {
      if (repositoryService == null) {
        return null;
      }

      var definitionTry = Try
          .of(() -> {
            var model = repositoryService.getBpmnModel(processDefinitionId);
            if (model == null) {
              return null;
            }
            var flowDefinition = readStructuredFlowDefinition(model);
            if (flowDefinition == null) {
              throw new InternalException("反序列化流程定义失败");
            }
            return flowDefinition;
          })
          .map(def -> def.getNodes()
              .stream()
              .filter(flowNode -> flowNodeId.equals(flowNode.getId()))
              .findFirst());
      return definitionTry.toEither()
          .peekLeft(e -> logger.error("获取任务节点定义失败", e))
          .getOrElse(Optional::empty)
          .orElse(null);
    };
  }

  public static String resolveFlowKey(RepositoryService repositoryService, DelegateExecution execution) {
    if (execution == null) {
      return null;
    }
    return resolveFlowKey(
        repositoryService,
        execution.getVariable(Constants.Variable.PROCESS_FLOW_KEY, String.class),
        execution.getProcessDefinitionId());
  }

  public static String resolveFlowKey(RepositoryService repositoryService, DelegateTask task) {
    if (task == null) {
      return null;
    }
    var flowKey = task.getVariable(Constants.Variable.PROCESS_FLOW_KEY, String.class);
    var execution = task.getExecution();
    if (StringUtils.isBlank(flowKey) && execution != null) {
      flowKey = execution.getVariable(Constants.Variable.PROCESS_FLOW_KEY, String.class);
    }
    return resolveFlowKey(
        repositoryService,
        flowKey,
        task.getProcessDefinitionId());
  }

  private static String resolveFlowKey(RepositoryService repositoryService, String flowKey, String processDefinitionId) {
    if (StringUtils.isNotBlank(flowKey)) {
      return flowKey;
    }

    if (repositoryService == null || StringUtils.isBlank(processDefinitionId)) {
      return null;
    }

    ProcessDefinition processDefinition = repositoryService.getProcessDefinition(processDefinitionId);
    return processDefinition == null ? null : processDefinition.getKey();
  }
}
