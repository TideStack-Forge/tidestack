package com.ouroboros.workflow.approval.activiti6.service;

import static com.ouroboros.workflow.approval.activiti6.util.BpmnUtil.readFlowDefinition;
import static com.ouroboros.workflow.approval.activiti6.util.BpmnUtil.readStructuredFlowDefinition;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.activiti.bpmn.model.Process;
import org.activiti.engine.FormService;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.repository.ProcessDefinition;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import io.vavr.control.Either;
import io.vavr.control.Try;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.FlowFormService;
import com.ouroboros.workflow.approval.BuiltInFlowInstanceService;
import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.definition.FlowDefinition;
import com.ouroboros.workflow.approval.definition.NodeFormFieldPermission;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.definition.codec.FlowDefinitionV2Normalizer;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.model.FlowInstanceDetail;
import com.ouroboros.workflow.approval.model.FlowInstanceStatistic;
import com.ouroboros.workflow.approval.model.FlowTimeline;

@Service
public class Activiti6FlowInstanceService implements BuiltInFlowInstanceService {
  final static Logger logger = LoggerFactory.getLogger(Activiti6FlowInstanceService.class);
  final FlowDefinitionV2Normalizer flowDefinitionV2Normalizer = new FlowDefinitionV2Normalizer();

  @Autowired(required = false)
  List<FlowFormService> flowFormServices = Collections.emptyList();
  @Autowired
  FormService formService;
  @Autowired
  TaskService taskService;
  @Autowired
  RepositoryService repositoryService;
  @Autowired
  RuntimeService runtimeService;
  @Autowired
  HistoryService historyService;
  @Autowired
  FlowDefinitionService flowDefinitionService;
  @Autowired
  InstanceEventDispatcher instanceEventDispatcher;

  @Autowired(required = false)
  FlowInstanceCommandService commandService;
  @Autowired(required = false)
  FlowInstanceQueryService queryService;
  @Autowired(required = false)
  TimelineService timelineService;

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Try<String> start(String flowKey,
                           String userId,
                           Map<String, List<String>> taskAssignee,
                           Map<String, Object> instanceVariables,
                           Map<String, Object> formData,
                           CompletableFuture<Void> initTaskDeferred) {
    return getCommandService().start(flowKey, userId, taskAssignee, instanceVariables, formData, initTaskDeferred);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Try<Void> pause(String instanceId, String userId, String reason) {
    return getCommandService().pause(instanceId, userId, reason);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Try<Void> resume(String instanceId, String userId) {
    return getCommandService().resume(instanceId, userId);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Try<Void> cancel(String instanceId, String userId, String reason) {
    return getCommandService().cancel(instanceId, userId, reason);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Try<Void> complete(String instanceId, String userId, Boolean success, String comment) {
    return getCommandService().complete(instanceId, userId, success, comment);
  }

  @Override
  public List<FlowTimeline> getTimeLine(String instanceId) {
    return getTimelineService().getTimeLine(instanceId);
  }

  @Override
  public Try<SchemaNode> getInitialForm(String flowKey) {
    var definition = getProcessDefinition(flowKey);
    if (definition.isLeft()) {
      return Try.failure(definition.getLeft());
    }

    var process = getProcess(definition.get());
    var flowDefinition = readStructuredFlowDefinition(process);
    if (flowDefinition == null) {
      return Try.failure(new BusinessException("审批流定义不存在"));
    }
    var formType = StringUtils.defaultIfBlank(flowDefinition.getFormType(), "default");
    var formKey = flowDefinition.getFormKey();

    if (StringUtils.isEmpty(formKey)) {
      return Try.failure(new BusinessException("流程未绑定表单"));
    }

    var formPermission = getInitialFormPermissions(flowDefinition, formType);
    var formService = flowFormServices.stream()
        .filter(x -> x.getType().equalsIgnoreCase(formType))
        .map(Try::success)
        .findFirst()
        .orElseGet(() -> Try.failure(new BusinessException("未找到对应的表单服务")));
    return formService.flatMap(x -> x.getInitialFormSchema(formKey, formPermission));
  }

  @Override
  public List<FlowInstanceStatistic> getStatistic(String keyword, String status, Integer offset, Integer limit) {
    return getQueryService().getStatistic(keyword, status, offset, limit);
  }

  @Override
  public Long getStatisticCount(String keyword, String status) {
    return getQueryService().getStatisticCount(keyword, status);
  }

  @Override
  public com.ouroboros.workflow.approval.model.FlowDurationStatistic getDurationStatistic(String flowKey, java.util.Date from, java.util.Date to) {
    return getQueryService().getDurationStatistic(flowKey, from, to);
  }

  @Override
  public List<FlowInstanceDetail> getInstances(String flowKey, String status, Integer offset, Integer limit) {
    return getQueryService().getInstances(flowKey, status, offset, limit);
  }

  @Override
  public Long getInstancesCount(String flowKey, String status) {
    return getQueryService().getInstancesCount(flowKey, status);
  }

  @Override
  public FlowDefinition getInstanceFlowDefinition(String instanceId) {
    var instance = historyService.createHistoricProcessInstanceQuery()
        .processInstanceId(instanceId)
        .singleResult();
    if (instance == null) {
      return null;
    }
    var process = repositoryService.getBpmnModel(instance.getProcessDefinitionId()).getMainProcess();
    return readFlowDefinition(process);
  }

  @Override
  public Try<JsonNode> getInstanceFlowDefinitionV2(String instanceId) {
    return Try.of(() -> historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(instanceId)
            .singleResult())
        .flatMap(instance -> {
          if (instance == null) {
            return Try.failure(new BusinessException("流程实例不存在"));
          }
          return Try.success(instance);
        })
        .map(instance -> repositoryService.getBpmnModel(instance.getProcessDefinitionId()))
        .map(com.ouroboros.workflow.approval.activiti6.util.BpmnUtil::readFlowDefinitionRawJson)
        .flatMap(flowDefinitionV2Normalizer::normalizeToJsonNode);
  }

  @Override
  public List<String> getActiveActivityIds(String instanceId) {
    var runtimeInstance = runtimeService.createProcessInstanceQuery()
        .processInstanceId(instanceId)
        .singleResult();
    if (runtimeInstance == null) {
      return Collections.emptyList();
    }
    return runtimeService.getActiveActivityIds(instanceId);
  }

  Either<BusinessException, ProcessDefinition> getProcessDefinition(String flowKey) {
    var definition = repositoryService.createProcessDefinitionQuery()
        .processDefinitionKey(flowKey)
        .latestVersion()
        .singleResult();

    if (definition == null) {
      return Either.left(new BusinessException("未找到已部署的流程"));
    }
    if (definition.isSuspended()) {
      return Either.left(new BusinessException("流程已挂起"));
    }
    return Either.right(definition);
  }

  private String getDeploymentStringResource(String deploymentId, String resourceName, String defaultValue) {
    try (var resource = repositoryService.getResourceAsStream(deploymentId, resourceName)) {
      if (resource == null) {
        return defaultValue;
      }
      var bufferedReader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8));
      return bufferedReader.lines().collect(Collectors.joining("\n"));
    } catch (Exception e) {
      logger.error("获取流程部署资源失败，部署ID：{}, 资源名：{}", deploymentId, resourceName, e);
      return defaultValue;
    }
  }

  private <T> T getDeployBeanResource(String deploymentId, String resourceName, Class<T> clazz) {
    var json = getDeploymentStringResource(deploymentId, resourceName, "null");
    return Json.tryToBean(clazz, json)
        .onFailure(e -> logger.error("反序列化流程部署资源失败，部署ID：{}, 资源名：{}, Bean类型：{}", deploymentId, resourceName, clazz, e))
        .getOrElse(() -> null);
  }

  private Process getProcess(ProcessDefinition definition) {
    return repositoryService.getBpmnModel(definition.getId()).getMainProcess();
  }

  private List<NodeFormFieldPermission> getStartNodePermissions(StructuredFlowDefinition flowDefinition) {
    return java.util.Optional.ofNullable(flowDefinition)
        .flatMap(StructuredFlowDefinition::getStartFlowNode)
        .map(com.ouroboros.workflow.approval.definition.FlowNode::getPermission)
        .orElseGet(Collections::emptyList);
  }

  private List<NodeFormFieldPermission> getInitialFormPermissions(StructuredFlowDefinition flowDefinition, String formType) {
    if (!StringUtils.equalsIgnoreCase(StringUtils.defaultIfBlank(formType, "default"), "default")) {
      return Collections.emptyList();
    }
    return getStartNodePermissions(flowDefinition);
  }

  private FlowInstanceCommandService getCommandService() {
    if (commandService == null) {
      commandService = new FlowInstanceCommandService(
          flowFormServices,
          formService,
          taskService,
          repositoryService,
          runtimeService,
          historyService,
          flowDefinitionService,
          instanceEventDispatcher);
    }
    return commandService;
  }

  private FlowInstanceQueryService getQueryService() {
    if (queryService == null) {
      queryService = new FlowInstanceQueryService(runtimeService, historyService, repositoryService);
    }
    return queryService;
  }

  private TimelineService getTimelineService() {
    if (timelineService == null) {
      timelineService = new TimelineService(taskService, runtimeService, historyService, repositoryService);
    }
    return timelineService;
  }
}
