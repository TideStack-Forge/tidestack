package com.ouroboros.workflow.approval.external.runtime.routing;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.BuiltInFlowDefinitionService;
import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.definition.FlowDefinition;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.definition.codec.CompositeFlowDefinitionCodec;
import com.ouroboros.workflow.approval.definition.metadata.ApprovalWorkflowDefinitionDocument;
import com.ouroboros.workflow.approval.manage.service.WorkflowModelService;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;
import com.ouroboros.workflow.approval.definition.startrequirements.StartRequirements;
import com.ouroboros.workflow.approval.definition.startrequirements.BuiltInStartRequirementsResolver;
import com.ouroboros.workflow.approval.definition.startrequirements.StartRequirementsResolver;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingValidator;
import com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeResolver;
import com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeUnconfiguredException;
import com.ouroboros.workflow.approval.external.runtime.startrequirements.ExternalStartRequirementsParser;
import com.ouroboros.workflow.approval.model.FlowDefinitionVersion;
import com.ouroboros.workflow.approval.util.StructuredFlowRuntimeNavigator;

import io.vavr.control.Try;

/**
 * 流程定义路由门面：外部映射从平台流程模型读取表单和节点，发起需求读取映射声明；
 * 内置流程继续读取其已部署定义。
 */
@Service
@Primary
public class RoutedFlowDefinitionService implements FlowDefinitionService, StartRequirementsResolver {

  @Autowired
  private WorkflowModelService workflowModels;

  private final FlowDefinitionService internal;
  private final WorkflowEngineMappingService mappingService;
  private final WorkflowScopeResolver scopeResolver;
  private final ObjectProvider<BuiltInStartRequirementsResolver> internalResolverProvider;
  private final ExternalStartRequirementsParser externalStartRequirementsParser;

  @Autowired
  public RoutedFlowDefinitionService(
      ObjectProvider<BuiltInFlowDefinitionService> internalProvider,
      WorkflowEngineMappingService mappingService,
      WorkflowScopeResolver scopeResolver,
      ObjectProvider<BuiltInStartRequirementsResolver> internalResolverProvider,
      ExternalStartRequirementsParser externalStartRequirementsParser) {
    this.internal = internalProvider.stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("未找到内置流程定义服务 Bean"));
    this.mappingService = mappingService;
    this.scopeResolver = scopeResolver;
    this.internalResolverProvider = internalResolverProvider;
    this.externalStartRequirementsParser = externalStartRequirementsParser;
  }

  @Override
  public Try<StartRequirements> getStartRequirements(String flowKey, Map<String, Object> formData) {
    Optional<WorkflowEngineMapping> mappingOpt;
    try {
      Optional<String> scope = resolveScopeForRead();
      if (scope.isEmpty()) {
        return internalResolver().getStartRequirements(flowKey, formData);
      }
      mappingOpt = mappingService.findActive(scope.get(), flowKey);
    } catch (BusinessException ex) {
      return Try.failure(ex);
    }
    if (mappingOpt.isPresent()
        && WorkflowEngineMappingValidator.AUTHORITY_EXTERNAL_ENGINE.equals(
            mappingOpt.get().getAuthority())) {
      return externalStartRequirementsParser.parse(mappingOpt.get(), formData);
    }
    return internalResolver().getStartRequirements(flowKey, formData);
  }

  private StartRequirementsResolver internalResolver() {
    return internalResolverProvider.stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("未找到内置发起需求解析器 Bean"));
  }

  private String resolveScope() {
    return scopeResolver.resolve();
  }

  /**
   * 读路径的作用域：未装配解析器时返回空，表示平台未启用外部协作，读路径降级为内置结果。
   *
   * <p>只容忍 {@link WorkflowScopeUnconfiguredException}；已装配解析器抛出的权限异常仍然传播。
   */
  private Optional<String> resolveScopeForRead() {
    try {
      return Optional.of(resolveScope());
    } catch (WorkflowScopeUnconfiguredException unconfigured) {
      return Optional.empty();
    }
  }

  @Override
  public Try<FlowDefinition> getFlowDefinition(String flowKey) {
    return externalMapping(flowKey).flatMap(mapping -> mapping.isPresent()
        ? platformDefinition(flowKey).flatMap(new CompositeFlowDefinitionCodec()::encodeV1)
        : internal.getFlowDefinition(flowKey));
  }

  @Override
  public Try<StructuredFlowDefinition> getStructuredFlowDefinition(String flowKey) {
    return externalMapping(flowKey).flatMap(mapping -> mapping.isPresent()
        ? platformDefinition(flowKey) : internal.getStructuredFlowDefinition(flowKey));
  }

  private Try<Optional<WorkflowEngineMapping>> externalMapping(String flowKey) {
    return Try.of(() -> resolveScopeForRead()
        .flatMap(scope -> mappingService.findActive(scope, flowKey))
        .filter(mapping -> WorkflowEngineMappingValidator.AUTHORITY_EXTERNAL_ENGINE
            .equals(mapping.getAuthority())));
  }

  private Try<StructuredFlowDefinition> platformDefinition(String flowKey) {
    return Try.of(() -> workflowModels.getByPrimaryKey(flowKey)).flatMap(model -> {
      if (model == null) {
        return Try.failure(new BusinessException("审批流定义不存在: " + flowKey));
      }
      return ApprovalWorkflowDefinitionDocument.decode(model.getJson()).flatMap(document -> {
        if (!document.metadata().isEnabledEffective()) {
          return Try.failure(new BusinessException("审批流定义已禁用: " + flowKey));
        }
        return document.toRuntimeJson().flatMap(new CompositeFlowDefinitionCodec()::decode);
      });
    });
  }

  @Override
  public Try<JsonNode> getFlowDefinitionV2(String flowKey, Integer version) {
    return internal.getFlowDefinitionV2(flowKey, version);
  }

  @Override
  public List<FlowNode> getFlowNodes(String flowKey) {
    return externalMapping(flowKey).flatMap(mapping -> mapping.isPresent()
        ? platformDefinition(flowKey).map(new StructuredFlowRuntimeNavigator()::getFlowNodes)
        : Try.of(() -> internal.getFlowNodes(flowKey)))
        .getOrElseThrow(cause -> new BusinessException("读取流程节点失败: " + flowKey, cause));
  }

  @Override
  public List<ApprovalNode> getUserSelectCandidateApprovalNodes(String flowKey) {
    return internal.getUserSelectCandidateApprovalNodes(flowKey);
  }

  @Override
  public List<ApprovalNode> getUserSelectCandidateApprovalNodes(
      String flowKey, Map<String, Object> conditionContext) {
    return internal.getUserSelectCandidateApprovalNodes(flowKey, conditionContext);
  }

  @Override
  public Try<List<FlowDefinitionVersion>> listFlowDefinitionVersions(String flowKey) {
    return internal.listFlowDefinitionVersions(flowKey);
  }

  @Override
  public Try<FlowDefinitionVersion> getFlowDefinitionVersion(String flowKey, Integer version) {
    return internal.getFlowDefinitionVersion(flowKey, version);
  }
}
