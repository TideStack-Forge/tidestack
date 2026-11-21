package com.ouroboros.workflow.approval.activiti6.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.repository.ProcessDefinition;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.vavr.control.Try;

import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.BuiltInFlowManageService;
import com.ouroboros.workflow.approval.activiti6.util.BpmnUtil;
import com.ouroboros.workflow.approval.definition.codec.CompositeFlowDefinitionCodec;
import com.ouroboros.workflow.approval.definition.codec.FlowDefinitionV2Normalizer;
import com.ouroboros.workflow.approval.transpiler.ApprovalWorkflowTranspilerBuilder;

@Service
public class Activiti6FlowManageService implements BuiltInFlowManageService {
  final CompositeFlowDefinitionCodec flowDefinitionCodec = new CompositeFlowDefinitionCodec();
  final FlowDefinitionV2Normalizer flowDefinitionV2Normalizer = new FlowDefinitionV2Normalizer();

  @Autowired
  RepositoryService repositoryService;

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Try<String> deploy(String flowKey, String flowDefinitionJson) {
    return deploy(flowKey, flowDefinitionJson, true);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Try<String> deployNewVersion(String flowKey, String flowDefinitionJson) {
    return deploy(flowKey, flowDefinitionJson, false);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Try<Void> offline(String processDefinitionId) {
    return Try.run(() -> repositoryService.suspendProcessDefinitionById(processDefinitionId, false, null));
  }

  private Try<String> deploy(String flowKey, String flowDefinitionJson, boolean reuseUnchangedLatestDefinition) {
    return flowDefinitionV2Normalizer.normalizeToJsonNode(flowDefinitionJson)
        .flatMap(canonicalFlowDefinition -> Try.of(() -> {
      var canonicalFlowDefinitionJson = Json.toJsonString(canonicalFlowDefinition);
      var structuredDefinition = flowDefinitionCodec.decode(canonicalFlowDefinitionJson).get();
      var transpiler = ApprovalWorkflowTranspilerBuilder.get(BpmnModel.class).build();
      var oldDeploymentId = repositoryService.createProcessDefinitionQuery()
          .processDefinitionKey(flowKey)
          .orderByProcessDefinitionVersion()
          .desc()
          .listPage(0, 1)
          .stream()
          .map(ProcessDefinition::getDeploymentId)
          .findFirst()
          .orElse(null);
      var oldFlowDefinitionJson = oldDeploymentId != null ? getFlowDefinitionJson(oldDeploymentId) : null;
      if (reuseUnchangedLatestDefinition
          && Objects.equals(canonicalFlowDefinition, normalizeFlowDefinitionJson(oldFlowDefinitionJson))) {
        ProcessDefinition processDefinition = getProcessDefinition(oldDeploymentId);
        if (processDefinition.isSuspended()) {
          repositoryService.activateProcessDefinitionById(processDefinition.getId(), false, null);
        }
        return processDefinition.getId();
      }
      var model = transpiler.transpile(structuredDefinition);
      BpmnUtil.saveFlowDefinitionRawJson(model.getMainProcess(), canonicalFlowDefinitionJson);

      var deploy = repositoryService.createDeployment()
          .name(structuredDefinition.getName())
          .key(flowKey)
          .category(structuredDefinition.getCategory())
          .addString("flowDefinition", canonicalFlowDefinitionJson)
          .addBpmnModel(flowKey + ".bpmn", model)
          .deploy();

      return getProcessDefinitionId(deploy.getId());
    }));
  }

  private String getFlowDefinitionJson(String deploymentId) {
    try (var stream = repositoryService.getResourceAsStream(deploymentId, "flowDefinition")) {
      var bufferedReader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
      return bufferedReader.lines().collect(Collectors.joining("\n"));
    } catch (Throwable e) {
      return null;
    }
  }

  private String getProcessDefinitionId(String deploymentId) {
    return getProcessDefinition(deploymentId).getId();
  }

  private ProcessDefinition getProcessDefinition(String deploymentId) {
    return repositoryService.createProcessDefinitionQuery()
        .deploymentId(deploymentId)
        .singleResult();
  }

  private JsonNode normalizeFlowDefinitionJson(String flowDefinitionJson) {
    if (StringUtils.isBlank(flowDefinitionJson)) {
      return null;
    }
    return flowDefinitionV2Normalizer.normalizeToJsonNode(flowDefinitionJson).getOrNull();
  }
}
