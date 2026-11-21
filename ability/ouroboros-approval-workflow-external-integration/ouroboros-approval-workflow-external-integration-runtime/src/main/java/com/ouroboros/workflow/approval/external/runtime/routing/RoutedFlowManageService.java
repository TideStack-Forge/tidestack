package com.ouroboros.workflow.approval.external.runtime.routing;

import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.BuiltInFlowManageService;
import com.ouroboros.workflow.approval.FlowDeploymentResult;
import com.ouroboros.workflow.approval.FlowManageService;
import com.ouroboros.workflow.approval.definition.FlowDefinition;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionValidator;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionRef;
import com.ouroboros.workflow.approval.external.runtime.ConnectionAwareAdapterResolver;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterRegistry;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeResolver;
import com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeUnconfiguredException;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingValidator;

import io.vavr.control.Try;

/**
 * 流程管理路由门面：对 {@code authority=EXTERNAL_ENGINE} 的映射只执行外部定义验证，不调用内置引擎部署；
 * 无映射时委托内置引擎。
 */
@Service
@Primary
public class RoutedFlowManageService implements FlowManageService {

  private final FlowManageService internal;
  private final WorkflowEngineMappingService mappingService;
  private final ConnectionAwareAdapterResolver adapterResolver;
  private final WorkflowScopeResolver scopeResolver;
  /** 可选注入：用于派生接入模式；缺失时按 RESULT_ONLY 处理。 */
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private WorkflowEngineAdapterRegistry registry;

  public RoutedFlowManageService(
      ObjectProvider<BuiltInFlowManageService> internalProvider,
      WorkflowEngineMappingService mappingService,
      ConnectionAwareAdapterResolver adapterResolver,
      WorkflowScopeResolver scopeResolver) {
    this.internal = internalProvider.stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("未找到内置流程管理服务 Bean"));
    this.mappingService = mappingService;
    this.adapterResolver = adapterResolver;
    this.scopeResolver = scopeResolver;
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
  public Try<String> deploy(String flowKey, String flowDefinitionJson) {
    return internal.deploy(flowKey, flowDefinitionJson);
  }

  @Override
  public Try<Void> offline(String processDefinitionId) {
    return internal.offline(processDefinitionId);
  }

  @Override
  public Try<String> deployNewVersion(String flowKey, String flowDefinitionJson) {
    return internal.deployNewVersion(flowKey, flowDefinitionJson);
  }

  @Override
  public Try<String> deploy(String flowKey, FlowDefinition flowDefinition) {
    return internal.deploy(flowKey, flowDefinition);
  }

  @Override
  public Try<FlowDeploymentResult> deployForRuntime(String flowKey, String flowDefinitionJson) {
    Optional<WorkflowEngineMapping> mappingOpt;
    try {
      Optional<String> scope = resolveScopeForRead();
      if (scope.isEmpty()) {
        return internal.deployForRuntime(flowKey, flowDefinitionJson);
      }
      mappingOpt = mappingService.findActive(scope.get(), flowKey);
    } catch (BusinessException ex) {
      return Try.failure(ex);
    }
    if (mappingOpt.isEmpty()) {
      return internal.deployForRuntime(flowKey, flowDefinitionJson);
    }
    WorkflowEngineMapping mapping = mappingOpt.get();

    if (WorkflowEngineMappingValidator.AUTHORITY_OUROBOROS.equals(mapping.getAuthority())) {
      Try<Void> validation = validateExternalDefinition(mapping);
      if (validation.isFailure()) {
        return Try.failure(validation.getCause());
      }
      return internal.deployForRuntime(flowKey, flowDefinitionJson)
          .map(result -> FlowDeploymentResult.mirror(
              result, mapping.getAdapterId(), mapping.getId(), mapping.getVersion(),
              mapping.getExternalDefinitionId()));
    }
    if (!WorkflowEngineMappingValidator.AUTHORITY_EXTERNAL_ENGINE.equals(mapping.getAuthority())) {
      return Try.failure(new BusinessException("映射 authority 非法: " + mapping.getId()));
    }

    return validateExternalDefinition(mapping).map(ignored -> new FlowDeploymentResult(
        "EXTERNAL",
        registry == null ? WorkflowEngineAdapterRegistry.MODE_RESULT_ONLY
            : registry.integrationModeOf(mapping.getAdapterId()),
        mapping.getAuthority(),
        mapping.getAdapterId(),
        mapping.getId(),
        mapping.getVersion(),
        null,
        mapping.getExternalDefinitionId()));
  }

  private Try<Void> validateExternalDefinition(WorkflowEngineMapping mapping) {
    ExternalProcessDefinitionValidator validator;
    try {
      validator = adapterResolver.resolvePort(mapping, ExternalProcessDefinitionValidator.class);
    } catch (BusinessException ex) {
      return Try.failure(ex);
    }
    ExternalProcessDefinitionRef ref = new ExternalProcessDefinitionRef(
        mapping.getExternalDefinitionNamespace(),
        mapping.getExternalDefinitionId(),
        mapping.getExternalDefinitionVersion(),
        mapping.getExternalDefinitionSchemaHash());
    Try<?> validateTry = validator.validateDefinition(ref);
    if (validateTry.isFailure()) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_DEFINITION_NOT_FOUND + "] 外部定义校验失败: "
              + validateTry.getCause().getMessage(), validateTry.getCause()));
    }
    return Try.success(null);
  }
}
