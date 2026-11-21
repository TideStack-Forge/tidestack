package com.ouroboros.workflow.approval.external.runtime.routing;

import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.ApprovalBusinessBindingValidator;
import com.ouroboros.workflow.approval.ApprovalInstanceState;
import com.ouroboros.workflow.approval.BuiltInApprovalBusinessBindingValidator;
import com.ouroboros.workflow.approval.external.Capability;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterRegistry;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalTaskBindingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingValidator;
import com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeResolver;
import com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeUnconfiguredException;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService;

/** Validates through the command route, including immutable external binding versions. */
@Service
@Primary
public class RoutedBusinessBindingValidator implements ApprovalBusinessBindingValidator {
  private final BuiltInApprovalBusinessBindingValidator internal;
  private final WorkflowEngineMappingService mappings;
  private final WorkflowScopeResolver scope;
  private final ExternalInstanceBindingService instances;
  private final ExternalTaskBindingService tasks;
  private final ApprovalCaseService cases;
  private final WorkflowEngineAdapterRegistry registry;
  private final String unmappedPolicy;
  private final ExternalBindingRouteResolver routes;
  private final ExternalBusinessStateInspection inspection;

  public RoutedBusinessBindingValidator(BuiltInApprovalBusinessBindingValidator internal,
      WorkflowEngineMappingService mappings, WorkflowScopeResolver scope,
      ExternalInstanceBindingService instances, ExternalTaskBindingService tasks, ApprovalCaseService cases,
      WorkflowEngineAdapterRegistry registry, ExternalBusinessStateInspection inspection,
      @Value("${ouroboros.workflow.approval.external.unmapped-policy:internal}") String unmappedPolicy) {
    this.internal = internal;
    this.mappings = mappings;
    this.scope = scope;
    this.instances = instances;
    this.tasks = tasks;
    this.cases = cases;
    this.registry = registry;
    this.unmappedPolicy = unmappedPolicy;
    this.routes = new ExternalBindingRouteResolver(mappings);
    this.inspection = inspection;
  }

  @Override
  public java.util.function.Supplier<ApprovalInstanceState> prepareInstanceInspection(String flowKey, String instanceId) {
    var prepared = inspection.prepare(flowKey, instanceId);
    return prepared == null ? internal.prepareInstanceInspection(flowKey, instanceId) : prepared;
  }

  @Override
  public void validate(String flowKey, String recordType, String recordId, String instanceId, String taskId) {
    if (instanceId == null && taskId == null) {
      Optional<WorkflowEngineMapping> mapping = startMapping(flowKey);
      if (mapping.isEmpty() || WorkflowEngineMappingValidator.AUTHORITY_OUROBOROS.equals(mapping.get().getAuthority())) {
        internal.validate(flowKey, recordType, recordId, instanceId, taskId);
        return;
      }
      if (!WorkflowEngineMappingValidator.AUTHORITY_EXTERNAL_ENGINE.equals(mapping.get().getAuthority())
          || !registry.capabilitiesOf(mapping.get().getAdapterId()).contains(Capability.EXTERNAL_START)) {
        throw new BusinessException("流程映射未提供审批发起能力");
      }
      // The external Saga reserves its case with its independently committed start intent.
      return;
    }
    var task = tasks.findByLocalId(taskId).orElse(null);
    if (task == null || routes.resolve(task.getMappingId(), task.getMappingVersion()).isBuiltIn()) {
      internal.validate(flowKey, recordType, recordId, instanceId, taskId);
      return;
    }
    var instance = instances.findByLocalId(instanceId)
        .orElseThrow(() -> new BusinessException("审批实例的正式外部绑定不存在"));
    var route = routes.resolve(instance.getMappingId(), instance.getMappingVersion());
    String currentScope = scope.resolve();
    if (route.isBuiltIn() || !Objects.equals(flowKey, instance.getFlowKey())
        || !Objects.equals(instanceId, task.getLocalInstanceId())
        || !Objects.equals(currentScope, instance.getScope()) || !Objects.equals(currentScope, task.getScope())
        || !Objects.equals(instance.getMappingId(), task.getMappingId())
        || !Objects.equals(instance.getMappingVersion(), task.getMappingVersion())
        || !Objects.equals(recordType, instance.getBusinessRecordType())
        || !Objects.equals(recordId, instance.getBusinessRecordId())) {
      throw new BusinessException("业务对象、实例与任务的正式外部绑定不匹配");
    }
    var current = cases.findByBusinessRecord(recordType, recordId, flowKey).orElse(null);
    if (current == null && (instance.getApprovalCaseId() != null || instance.getSubmissionRound() != null)
        || current != null && (!Objects.equals(current.getApprovalCaseId(), instance.getApprovalCaseId())
            || !Objects.equals(current.getSubmissionRound(), instance.getSubmissionRound())
            || !Objects.equals(current.getActiveExecutionId(), instanceId))) {
      throw new BusinessException("外部审批任务已不属于业务对象当前案件轮次");
    }
  }

  private Optional<WorkflowEngineMapping> startMapping(String flowKey) {
    Optional<WorkflowEngineMapping> mapping;
    try {
      mapping = mappings.findActive(scope.resolve(), flowKey);
    } catch (WorkflowScopeUnconfiguredException unconfigured) {
      mapping = Optional.empty();
    }
    if (mapping.isEmpty() && RoutedFlowInstanceService.UNMAPPED_POLICY_REJECT.equalsIgnoreCase(unmappedPolicy)) {
      throw new BusinessException("流程未配置映射，禁止发起");
    }
    return mapping;
  }
}
