package com.ouroboros.workflow.approval.external.runtime.routing;

import java.util.Optional;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingValidator;

/**
 * Resolves the execution authority of a binding from its immutable mapping version.
 * A binding proves correlation with an external system; it does not grant external
 * execution authority by itself.
 */
final class ExternalBindingRouteResolver {

  enum Route {
    BUILT_IN,
    EXTERNAL_ENGINE
  }

  record Decision(Route route, WorkflowEngineMapping mapping) {
    boolean isBuiltIn() {
      return route == Route.BUILT_IN;
    }
  }

  private final WorkflowEngineMappingService mappingService;

  ExternalBindingRouteResolver(WorkflowEngineMappingService mappingService) {
    this.mappingService = mappingService;
  }

  Decision resolve(String mappingId, Integer mappingVersion) {
    if (mappingId == null || mappingId.isBlank() || mappingVersion == null) {
      throw mappingError("绑定缺少 mappingId");
    }
    Optional<WorkflowEngineMapping> mapping = mappingService.findByIdForSystem(mappingId);
    if (mapping.isEmpty()) {
      throw mappingError("映射不存在: " + mappingId);
    }
    if (!mappingVersion.equals(mapping.get().getVersion())) {
      throw mappingError("映射版本不匹配: " + mappingId + "@" + mappingVersion);
    }
    String authority = mapping.get().getAuthority();
    if (WorkflowEngineMappingValidator.AUTHORITY_OUROBOROS.equals(authority)) {
      return new Decision(Route.BUILT_IN, mapping.get());
    }
    if (WorkflowEngineMappingValidator.AUTHORITY_EXTERNAL_ENGINE.equals(authority)) {
      return new Decision(Route.EXTERNAL_ENGINE, mapping.get());
    }
    throw mappingError("映射 authority 非法: " + mappingId);
  }

  private BusinessException mappingError(String message) {
    return new BusinessException(
        "[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_NOT_ACTIVE + "] " + message);
  }
}
