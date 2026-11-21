package com.ouroboros.workflow.approval.external.runtime.routing;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ouroboros.data.model.TypedDataModelCenter;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.ApprovalInstanceState;
import com.ouroboros.workflow.approval.event.FlowEventOutbox;
import com.ouroboros.workflow.approval.event.FlowInstanceFactState;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCase;
import com.ouroboros.workflow.approval.external.ExternalInstanceDetailReader;
import com.ouroboros.workflow.approval.external.ExternalInstanceRef;
import com.ouroboros.workflow.approval.external.ExternalInstanceSnapshot;
import com.ouroboros.workflow.approval.external.runtime.ConnectionAwareAdapterResolver;
import com.ouroboros.workflow.approval.external.runtime.ExternalFactTransaction;
import com.ouroboros.workflow.approval.external.runtime.ExternalStatusMapper;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBindingService;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalSnapshotEnvelopeFactory;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent.EventType;

/** Prepares a provider snapshot, then fences it against the locked canonical binding before baselining. */
@Component
public class ExternalBusinessStateInspection {
  private final ExternalInstanceBindingService instances;
  private final ExternalResultBindingService results;
  private final WorkflowEngineMappingService mappings;
  private final ConnectionAwareAdapterResolver adapters;
  private final ExternalBindingRouteResolver routes;

  public ExternalBusinessStateInspection(ExternalInstanceBindingService instances, ExternalResultBindingService results,
      WorkflowEngineMappingService mappings, ConnectionAwareAdapterResolver adapters) {
    this.instances = instances; this.results = results; this.mappings = mappings; this.adapters = adapters;
    this.routes = new ExternalBindingRouteResolver(mappings);
  }

  /** Returns null only when no external binding exists; a broken external route never falls back locally. */
  public Supplier<ApprovalInstanceState> prepare(String flowKey, String instanceId) {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new BusinessException("外部审批状态读取必须先挂起本地事务并在案件加锁前准备");
    }
    Binding binding = binding(instanceId);
    if (binding == null) { return null; }
    var route = routes.resolve(binding.mappingId(), binding.mappingVersion());
    if (route.isBuiltIn()) { return null; }
    requireRoute(flowKey, binding, route.mapping());
    ExternalInstanceDetailReader reader = adapters.resolvePort(route.mapping(), ExternalInstanceDetailReader.class);
    var response = reader.getInstance(new ExternalInstanceRef(binding.externalId(), binding.connectionId()));
    if (response == null) { throw new BusinessException("外部审批状态核验端口返回空结果"); }
    ExternalInstanceSnapshot snapshot = response.getOrElseThrow(error -> new BusinessException("外部审批状态核验失败，保留原 Outbox 责任", error));
    ExternalSnapshotEnvelopeFactory.create(binding.adapterId(), binding.connectionId(), snapshot);
    if (!Objects.equals(binding.externalId(), snapshot.externalInstanceId())) {
      throw new BusinessException("外部审批状态快照与正式实例绑定不匹配");
    }
    return () -> inspectLocked(flowKey, binding, snapshot);
  }

  private ApprovalInstanceState inspectLocked(String flowKey, Binding expected, ExternalInstanceSnapshot snapshot) {
    Binding current = binding(expected.instanceId());
    if (!expected.equals(current)) { throw new BusinessException("外部审批状态读取后正式绑定已改变，请重试原 Outbox"); }
    WorkflowEngineMapping mapping = mappings.findByIdForSystem(expected.mappingId())
        .orElseThrow(() -> new BusinessException("外部审批固定映射不存在，保留原 Outbox 责任"));
    requireRoute(flowKey, current, mapping);
    Object source = ExternalFactTransaction.resource(FlowEventOutbox.class);
    if (source != ExternalFactTransaction.resource(current.resultOnly() ? ExternalResultBinding.class : ExternalInstanceBinding.class)
        || source != ExternalFactTransaction.resource(ApprovalCase.class)
        || source != ExternalFactTransaction.resource(FlowInstanceFactState.class)) {
      throw new BusinessException("外部审批状态绑定与源事实不属于同一提交资源");
    }
    EventType type;
    if ("RUNNING".equals(snapshot.lifecycleStatus()) && "RUNNING".equals(current.status())) {
      if (snapshot.resultCode() != null || snapshot.outcome() != null) { throw inconsistent(); }
      type = current.resultOnly() || !hasResumed(flowKey, current.instanceId()) ? EventType.STARTED : EventType.RESUMED;
    } else if ("SUSPENDED".equals(snapshot.lifecycleStatus()) && "SUSPENDED".equals(current.status())) {
      if (snapshot.resultCode() != null || snapshot.outcome() != null) { throw inconsistent(); }
      type = EventType.SUSPENDED;
    } else if ("FINISHED".equals(current.status())
        && ("FINISHED".equals(snapshot.lifecycleStatus()) || "CANCELED".equals(snapshot.lifecycleStatus()))) {
      var result = ExternalStatusMapper.matchResultMapping(mapping, snapshot.resultCode());
      if (result == null) { throw inconsistent(); }
      type = current.resultOnly() ? ExternalStatusMapper.instanceResultEventType(snapshot.resultCode())
          : ExternalStatusMapper.instanceResultEventType(snapshot.resultCode(), result.systemEvent());
      if (!java.util.Set.of(EventType.APPROVED, EventType.REJECTED, EventType.CANCELLED, EventType.FAILED).contains(type)) {
        throw inconsistent();
      }
      String outcome = result.outcome() == null || result.outcome().isBlank()
          ? ExternalStatusMapper.defaultInstanceOutcome(snapshot.resultCode()) : result.outcome();
      if (!Objects.equals(outcome, current.outcome())
          || snapshot.outcome() != null && !snapshot.outcome().isBlank() && !snapshot.outcome().equals(current.outcome())) {
        throw inconsistent();
      }
    } else { throw inconsistent(); }
    return new ApprovalInstanceState(flowKey, current.instanceId(),
        new FlowInstanceEvent.BusinessContext(current.recordType(), current.recordId()), current.caseId(), current.round(),
        current.callback(), type, current.outcome());
  }

  private boolean hasResumed(String flowKey, String instanceId) {
    return TypedDataModelCenter.getDataModel(FlowEventOutbox.class).orElseThrow().getDataModel()
        .count(Map.of("flowKey", flowKey, "instanceId", instanceId,
            "eventKind", FlowEventOutbox.EVENT_KIND_INSTANCE, "eventType", EventType.RESUMED.name()))
        .getOrElseThrow(error -> new BusinessException("外部审批恢复状态没有可核验的持久来源", error)) > 0;
  }

  private void requireRoute(String flowKey, Binding binding, WorkflowEngineMapping mapping) {
    if (!Objects.equals(flowKey, binding.flowKey()) || !Objects.equals(flowKey, mapping.getFlowKey())
        || !Objects.equals(binding.mappingVersion(), mapping.getVersion())
        || !Objects.equals(binding.scope(), mapping.getScope()) || !Objects.equals(binding.adapterId(), mapping.getAdapterId())
        || !Objects.equals(binding.connectionId(), mapping.getConnectionId())
        || !"EXTERNAL_ENGINE".equals(mapping.getAuthority()) || blank(binding.recordType()) || blank(binding.recordId())
        || blank(binding.externalId()) || (binding.caseId() == null) != (binding.round() == null)) {
      throw new BusinessException("外部审批状态基线与正式固定映射或业务对象归属冲突");
    }
  }

  private Binding binding(String instanceId) {
    var instance = instances.findByLocalId(instanceId).orElse(null);
    var result = results.findByTrackingId(instanceId).orElse(null);
    if (instance != null && result != null) { throw new BusinessException("审批实例同时存在两种正式外部绑定"); }
    if (instance != null) {
      return new Binding(instanceId, false, instance.getMappingId(), instance.getMappingVersion(), instance.getScope(),
          instance.getFlowKey(), instance.getAdapterId(), instance.getConnectionId(), instance.getExternalInstanceId(),
          instance.getBusinessRecordType(), instance.getBusinessRecordId(), instance.getApprovalCaseId(), instance.getSubmissionRound(),
          instance.getCallbackFlow(), instance.getStatus(), instance.getOutcome(), instance.getExternalRevision(),
          instance.getExternalEventTime(), instance.getLastExternalEventId());
    }
    return result == null ? null : new Binding(instanceId, true, result.getMappingId(), result.getMappingVersion(), result.getScope(),
        result.getFlowKey(), result.getAdapterId(), result.getConnectionId(), result.getExternalInstanceId(),
        result.getBusinessRecordType(), result.getBusinessRecordId(), result.getApprovalCaseId(), result.getSubmissionRound(),
        null, result.getStatus(), result.getOutcome(), result.getExternalRevision(), result.getExternalEventTime(), result.getLastExternalEventId());
  }

  private boolean blank(String value) { return value == null || value.isBlank(); }
  private BusinessException inconsistent() { return new BusinessException("外部权威状态与正式已提交绑定不一致，保留原 Outbox 等待同步"); }

  private record Binding(String instanceId, boolean resultOnly, String mappingId, Integer mappingVersion, String scope,
      String flowKey, String adapterId, String connectionId, String externalId, String recordType, String recordId,
      String caseId, Integer round, String callback, String status, String outcome, String revision,
      LocalDateTime eventTime, String lastEventId) { }
}
