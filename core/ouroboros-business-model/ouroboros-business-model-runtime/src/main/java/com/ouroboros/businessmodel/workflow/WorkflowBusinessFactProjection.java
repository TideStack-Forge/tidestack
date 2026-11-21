package com.ouroboros.businessmodel.workflow;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.action.BusinessActionExecutionScope;
import com.ouroboros.businessmodel.event.BusinessEventEmissionContext;
import com.ouroboros.businessmodel.event.BusinessEventIdentity;
import com.ouroboros.businessmodel.event.BusinessEventPort;
import com.ouroboros.businessmodel.event.BusinessEventSubject;
import com.ouroboros.businessmodel.publication.BusinessModelPublication;
import com.ouroboros.businessmodel.transaction.BusinessTransactionResources;
import com.ouroboros.businessmodel.workflow.store.WorkflowBusinessFactStore;
import com.ouroboros.businessmodel.workflow.store.WorkflowBusinessFactCursor;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.model.DataModelExecutionContext;
import com.ouroboros.data.model.TypedDataModelCenter;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.ApprovalBusinessBindingValidator;
import com.ouroboros.workflow.approval.ApprovalInstanceState;
import com.ouroboros.workflow.approval.callback.CallbackBinding;
import com.ouroboros.workflow.approval.event.ApprovalBusinessFactProjection;
import com.ouroboros.workflow.approval.event.FlowEventOutboxService;
import com.ouroboros.workflow.approval.event.FlowInstanceFactState;
import com.ouroboros.workflow.approval.service.FlowInstanceFactStateService;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCase;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

/** The existing approval callback hands off a fact once, inside an independent receiver transaction. */
public class WorkflowBusinessFactProjection implements ApprovalBusinessFactProjection {
  private final BusinessModelProvider models;
  private final FlowEventOutboxService source;
  private final ApprovalCaseService cases;
  private final FlowInstanceFactStateService sourceStates;
  private final WorkflowBusinessFactStore store;
  private final WorkflowBusinessEventProducer producer;
  private final BusinessEventPort events;
  private final PlatformTransactionManager manager;
  private final Supplier<ApprovalBusinessBindingValidator> bindings;
  private final Supplier<com.ouroboros.workflow.approval.event.FlowBusinessNotificationOwnership> notificationOwnership;

  WorkflowBusinessFactProjection(BusinessModelProvider models, FlowEventOutboxService source, ApprovalCaseService cases,
      FlowInstanceFactStateService sourceStates, WorkflowBusinessFactStore store, WorkflowBusinessEventProducer producer,
      BusinessEventPort events, PlatformTransactionManager manager, Supplier<ApprovalBusinessBindingValidator> bindings) {
    this(models, source, cases, sourceStates, store, producer, events, manager, bindings, () -> null);
  }

  WorkflowBusinessFactProjection(BusinessModelProvider models, FlowEventOutboxService source, ApprovalCaseService cases,
      FlowInstanceFactStateService sourceStates, WorkflowBusinessFactStore store, WorkflowBusinessEventProducer producer,
      BusinessEventPort events, PlatformTransactionManager manager, Supplier<ApprovalBusinessBindingValidator> bindings,
      Supplier<com.ouroboros.workflow.approval.event.FlowBusinessNotificationOwnership> notificationOwnership) {
    this.models = models;
    this.source = source;
    this.cases = cases;
    this.sourceStates = sourceStates;
    this.store = store;
    this.producer = producer;
    this.events = events;
    this.manager = manager;
    this.bindings = bindings;
    this.notificationOwnership = notificationOwnership;
  }

  @Override
  public boolean ownsInstance(FlowInstanceEvent event) {
    return event != null && owner(event.getBusinessContext(), null) != null;
  }

  @Override
  public boolean projectInstance(FlowInstanceEvent event, CallbackBinding binding, Runnable requiredCallback) {
    return BusinessModelPublication.withPublishedContext(() -> projectPublishedInstance(event, binding, requiredCallback));
  }

  private boolean projectPublishedInstance(FlowInstanceEvent event, CallbackBinding binding, Runnable requiredCallback) {
    if (event == null) { return false; }
    BusinessModel model = owner(event.getBusinessContext(), binding);
    if (model == null) { return false; }
    Supplier<ApprovalInstanceState> inspection = event.getProjectionVersion() == null ? prepareInspection(model, event, binding) : null;
    return receive(() -> {
      source.requirePersistedFact(event);
      if (binding == null) { throw new BusinessException("业务工作流实例事实缺少正式回调绑定"); }
      requireBinding(model, event.getFlowKey(), event.getCallbackFlow(), binding);
      ApprovalCase current = lockCase(event.getBusinessContext(), event.getFlowKey());
      var state = sourceStates.lock(event.getInstanceId());
      Object recordId = recordId(model, event.getBusinessContext());
      var cursor = store.acquire(model.getFullName(), recordId, event.getFlowKey());
      if (store.findReceipt(cursor, event.getEventFactId()).isPresent()) { return true; }
      if (event.getProjectionVersion() == null) {
        return projectLegacy(model, event, current, state.orElse(null), cursor, recordId, requiredCallback, inspection);
      }
      requireVersion(event.getProjectionVersion(), event.getOccurredAt());
      requireNotificationHandoff(event.getBusinessNotificationOwner());
      if (event.getStateSequence() == null || event.getStateSequence() < 1 || state.isEmpty()
          || event.getStateSequence() > state.get().getStateSequence()) {
        throw new BusinessException("业务工作流实例事实缺少有效源状态序号");
      }
      var authoritative = state.orElseThrow();
      if (!Objects.equals(authoritative.getBusinessRecordType(), event.getBusinessContext().recordType())
          || !Objects.equals(authoritative.getBusinessRecordId(), event.getBusinessContext().recordId())
          || !Objects.equals(authoritative.getFlowKey(), event.getFlowKey())
          || !Objects.equals(authoritative.getApprovalCaseId(), event.getApprovalCaseId())
          || !Objects.equals(authoritative.getSubmissionRound(), event.getSubmissionRound())) {
        throw new BusinessException("业务工作流事实与源实例的不可变归属冲突");
      }
      boolean applicable = Objects.equals(current.getApprovalCaseId(), event.getApprovalCaseId())
          && Objects.equals(current.getSubmissionRound(), event.getSubmissionRound())
          && (Objects.equals(current.getLastStateInstanceId(), event.getInstanceId())
              || Objects.equals(current.getActiveExecutionId(), event.getInstanceId()))
          && Objects.equals(state.get().getStateSequence(), event.getStateSequence())
          && Objects.equals(state.get().getLastEventFactId(), event.getEventFactId())
          && (!Objects.equals(cursor.getInstanceId(), event.getInstanceId()) || event.getStateSequence() > cursor.getStateSequence());
      var identity = new BusinessEventIdentity(model.getFullName(), instanceEventName(event));
      return WorkflowProjectionSecurity.withFact(identity, recordId, () -> {
        if (applicable) {
          requiredCallback.run();
          store.advance(cursor, event);
        }
        String eventId = emit(identity, recordId, event.getEventFactId(), event.getInstanceId(), event.getOccurredAt(),
            payload(event));
        store.record(cursor, event.getEventFactId(), event.getInstanceId(), event.getStateSequence(), eventId,
            applicable ? "APPLIED" : "STALE_STATE");
        return true;
      });
    });
  }

  private boolean projectLegacy(BusinessModel model, FlowInstanceEvent event, ApprovalCase current, FlowInstanceFactState state,
      WorkflowBusinessFactCursor cursor, Object recordId, Runnable callback, Supplier<ApprovalInstanceState> inspection) {
    if (event.getStateSequence() != null || event.getEventType() == null) {
      throw new BusinessException("旧审批事实的版本 / 生命周期无效，不能降级处理新版错误事实");
    }
    if (state != null && state.getStateSequence() > 0 || current.getLastStateSequence() != null && current.getLastStateSequence() > 0) {
      store.record(cursor, event.getEventFactId(), event.getInstanceId(), null, null, "LEGACY_STATE_SUPERSEDED");
      return true;
    }
    if (event.getApprovalCaseId() != null && !Objects.equals(current.getApprovalCaseId(), event.getApprovalCaseId())
        || event.getSubmissionRound() != null && !Objects.equals(current.getSubmissionRound(), event.getSubmissionRound())
        || current.getActiveExecutionId() != null && !current.getActiveExecutionId().equals(event.getInstanceId())
        || current.getLastStateInstanceId() != null && !current.getLastStateInstanceId().equals(event.getInstanceId())) {
      store.record(cursor, event.getEventFactId(), event.getInstanceId(), null, null, "LEGACY_STALE_ROUND");
      return true;
    }
    if (state == null) {
      var snapshot = inspection.get();
      if (snapshot == null || !Objects.equals(event.getFlowKey(), snapshot.flowKey())
          || !Objects.equals(event.getInstanceId(), snapshot.instanceId())
          || !Objects.equals(event.getBusinessContext(), snapshot.businessContext())
          || snapshot.callbackFlow() != null && !snapshot.callbackFlow().equals(model.getFullName() + ".processCallback")) {
        throw new BusinessException("审批状态基线与源事实正式归属不一致");
      }
      if (snapshot.approvalCaseId() == null && snapshot.submissionRound() == null
          && Objects.equals(current.getActiveExecutionId(), event.getInstanceId())) {
        snapshot = new ApprovalInstanceState(snapshot.flowKey(), snapshot.instanceId(), snapshot.businessContext(),
            current.getApprovalCaseId(), current.getSubmissionRound(), snapshot.callbackFlow(), snapshot.eventType(), snapshot.outcome());
      }
      if (snapshot.approvalCaseId() == null || snapshot.submissionRound() == null) {
        throw new BusinessException("旧审批状态基线无法核验当前案件轮次，保留原 Outbox 责任");
      }
      if (!Objects.equals(current.getApprovalCaseId(), snapshot.approvalCaseId())
          || !Objects.equals(current.getSubmissionRound(), snapshot.submissionRound())) {
        store.record(cursor, event.getEventFactId(), event.getInstanceId(), null, null, "LEGACY_STALE_ROUND");
        return true;
      }
      state = sourceStates.establishBaseline(snapshot);
      cases.establishStateBaseline(snapshot);
    }
    if (!Objects.equals(state.getBusinessRecordType(), event.getBusinessContext().recordType())
        || !Objects.equals(state.getBusinessRecordId(), event.getBusinessContext().recordId())
        || !Objects.equals(state.getFlowKey(), event.getFlowKey())
        || !Objects.equals(state.getApprovalCaseId(), current.getApprovalCaseId())
        || !Objects.equals(state.getSubmissionRound(), current.getSubmissionRound())) {
      throw new BusinessException("旧审批状态基线的不可变归属冲突");
    }
    boolean applicable = event.getEventType() != null && event.getEventType().name().equals(state.getEventType())
        && (event.getEventType() != FlowInstanceEvent.EventType.CANCELLED
            || Objects.equals(cancellationOutcome(event.getOutcome()), cancellationOutcome(state.getOutcome())));
    if (applicable) {
      WorkflowProjectionSecurity.withFact(new BusinessEventIdentity(model.getFullName(), instanceEventName(event)), recordId, () -> {
        callback.run();
        return null;
      });
    }
    store.record(cursor, event.getEventFactId(), event.getInstanceId(), null, null, applicable ? "LEGACY_APPLIED" : "LEGACY_STALE_STATE");
    return true;
  }

  private Supplier<ApprovalInstanceState> prepareInspection(BusinessModel model, FlowInstanceEvent event, CallbackBinding binding) {
    if (binding == null) { throw new BusinessException("业务工作流实例事实缺少正式回调绑定"); }
    requireBinding(model, event.getFlowKey(), event.getCallbackFlow(), binding);
    var transaction = new TransactionTemplate(manager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    transaction.executeWithoutResult(status -> source.requirePersistedFact(event));
    var prepared = io.vavr.control.Try.of(() -> transaction.execute(status -> {
      var validator = bindings.get();
      if (validator == null) { throw new BusinessException("旧审批事实尚无可信状态基线核验服务，保留原 Outbox 责任"); }
      return validator.prepareInstanceInspection(event.getFlowKey(), event.getInstanceId());
    }));
    // Superseded or already-received legacy facts must not depend on remote availability.
    return () -> prepared.get().get();
  }

  private String cancellationOutcome(String outcome) {
    return "withdrawn".equalsIgnoreCase(outcome) ? "withdrawn" : "terminated".equalsIgnoreCase(outcome) ? "terminated" : "cancelled";
  }

  @Override
  public boolean projectTask(FlowTaskEvent event) {
    return BusinessModelPublication.withPublishedContext(() -> projectPublishedTask(event));
  }

  private boolean projectPublishedTask(FlowTaskEvent event) {
    if (event == null || event.getProjectionVersion() == null) { return false; }
    BusinessModel model = owner(event.getBusinessContext(), null);
    if (model == null) { return false; }
    return receive(() -> {
      source.requirePersistedFact(event);
      requireVersion(event.getProjectionVersion(), event.getOccurredAt());
      requireBinding(model, event.getFlowKey(), event.getCallbackFlow(), null);
      var current = lockCase(event.getBusinessContext(), event.getFlowKey());
      if (!Objects.equals(current.getApprovalCaseId(), event.getApprovalCaseId())
          || event.getSubmissionRound() == null || event.getSubmissionRound() < 1) {
        throw new BusinessException("业务工作流任务事实缺少可信案件 / 轮次归属");
      }
      Object recordId = recordId(model, event.getBusinessContext());
      var cursor = store.acquire(model.getFullName(), recordId, event.getFlowKey());
      if (store.findReceipt(cursor, event.getEventFactId()).isPresent()) { return true; }
      requireNotificationHandoff(event.getBusinessNotificationOwner());
      String name = taskEventName(event);
      if (name == null) {
        store.record(cursor, event.getEventFactId(), event.getInstanceId(), null, null, "NO_BUSINESS_EVENT");
        return true;
      }
      requireText(event.getTaskId(), "任务身份");
      var identity = new BusinessEventIdentity(model.getFullName(), name);
      return WorkflowProjectionSecurity.withFact(identity, recordId, () -> {
        String eventId = emit(identity, recordId, event.getEventFactId(), event.getInstanceId(), event.getOccurredAt(), payload(event));
        store.record(cursor, event.getEventFactId(), event.getInstanceId(), null, eventId, "TASK_FACT");
        return true;
      });
    });
  }

  private <T> T receive(Supplier<T> operation) {
    return BusinessActionExecutionScope.detached(() ->
        DataModelExecutionContext.withPlatformWrite(() -> {
          var transaction = new TransactionTemplate(manager);
          transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
          // Source-proof reads must not pin a MySQL snapshot older than the canonical case lock.
          transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
          return transaction.execute(status -> DataModelExecutionContext.withWriteGuard(
              BusinessTransactionResources.capture(manager, status), () -> {
                T result = operation.get();
                if (status.isRollbackOnly()) { throw new BusinessException("业务工作流接收事务已标记回滚"); }
                return result;
              }));
        }));
  }

  private void requireNotificationHandoff(com.ouroboros.workflow.approval.event.FlowBusinessNotificationOwner owner) {
    if (owner == null) { return; }
    var ownership = notificationOwnership.get();
    if (ownership == null) { throw new BusinessException("工作流通知持久责任的交接服务不可用，保留审批 Outbox 责任"); }
    ownership.requireHandoff(owner);
  }

  private BusinessModel owner(FlowInstanceEvent.BusinessContext context, CallbackBinding binding) {
    String type = context == null ? binding == null ? null : binding.recordType() : context.recordType();
    if (type == null) { return null; }
    BusinessModel model = models.getBusinessModel(type).orElse(null);
    if (model == null) { return null; }
    if (context == null || context.recordId() == null || context.recordId().isBlank()) {
      throw new BusinessException("业务工作流事实缺少可信业务对象归属");
    }
    return model;
  }

  private void requireBinding(BusinessModel model, String flowKey, String callbackFlow, CallbackBinding binding) {
    String configured = model.getExtraPropDeep(String.class, "workflow", "bindings", "0", "processId").orElse(null);
    if (!Objects.equals(configured, flowKey) || configured == null
        || callbackFlow != null && !callbackFlow.equals(model.getFullName() + ".processCallback")
        || binding != null && (!model.getFullName().equals(binding.recordType())
            || !Objects.equals(flowKey, binding.flowKey())
            || !(model.getFullName() + ".processCallback").equals(binding.callbackFlow()))) {
      throw new BusinessException("业务工作流事实与正式模型 / 流程 / 回调绑定不一致");
    }
  }

  private ApprovalCase lockCase(FlowInstanceEvent.BusinessContext context, String flowKey) {
    // These locks arbitrate with source commands; prove the same actual resource before using them.
    requireLockResource(ApprovalCase.class);
    requireLockResource(FlowInstanceFactState.class);
    return cases.lockByBusinessRecord(context.recordType(), context.recordId(), flowKey)
        .orElseThrow(() -> new BusinessException("业务工作流事实缺少正式案件关联，需先建立可信在途基线"));
  }

  private void requireLockResource(Class<?> type) {
    var model = TypedDataModelCenter.getDataModel(String.class, type)
        .orElseThrow(() -> new BusinessException("业务工作流归属模型不可用"));
    DataModelExecutionContext.getWriteGuard().beforeWrite(model.getDataModel());
  }

  private Object recordId(BusinessModel model, FlowInstanceEvent.BusinessContext context) {
    var data = DataModelCenter.getDataModel(model.getFullName()).orElseThrow(() -> new BusinessException("业务对象模型未发布"));
    if (data.getPrimaryKeys().size() != 1 || !"id".equalsIgnoreCase(data.getPrimaryKeys().getFirst().getName())) {
      throw new BusinessException("业务工作流事件要求唯一 id 主键");
    }
    return data.getPrimaryKeys().getFirst().getValueType().convert(context.recordId());
  }

  private String emit(BusinessEventIdentity identity, Object recordId, String factId, String instanceId,
      Instant occurredAt, Map<String, Object> payload) {
    var context = new BusinessEventEmissionContext(WorkflowBusinessEventProducer.NAME, instanceId, factId).withOccurredAt(occurredAt);
    return producer.withFact(identity, factId, () -> events.emit(identity,
        BusinessEventSubject.instance(identity.modelFullName(), recordId), payload, context).eventId());
  }

  private void requireVersion(Integer version, Instant time) {
    if (!Integer.valueOf(1).equals(version) || time == null) { throw new BusinessException("业务工作流源事实版本或发生时间无效"); }
  }

  private String instanceEventName(FlowInstanceEvent event) {
    if (event.getEventType() == null) { throw new BusinessException("审批实例事实缺少生命周期类型"); }
    return switch (event.getEventType()) {
      case STARTED -> "workflowStarted";
      case APPROVED -> "workflowApproved";
      case REJECTED -> "workflowRejected";
      case CANCELLED -> "withdrawn".equalsIgnoreCase(event.getOutcome()) ? "workflowWithdrawn" : "workflowCancelled";
      case SUSPENDED -> "workflowSuspended";
      case RESUMED -> "workflowResumed";
      case FAILED -> "workflowFailed";
    };
  }

  private String taskEventName(FlowTaskEvent event) {
    if (event.getFactKind() == FlowTaskEvent.FactKind.ACTION_COMPLETED) {
      requireText(event.getActionName(), "规范任务操作");
      return "workflowTaskActionCompleted";
    }
    if (event.getState() == TaskState.CREATED) { return "workflowTaskCreated"; }
    return event.getState() == TaskState.TIMEOUT_REMIND ? "workflowTaskTimeoutReminded" : null;
  }

  private Map<String, Object> payload(FlowInstanceEvent event) {
    var payload = basePayload(event.getEventFactId(), event.getOccurredAt(), event.getFlowKey(), event.getInstanceId(),
        event.getApprovalCaseId(), event.getSubmissionRound());
    payload.put("stateSequence", event.getStateSequence());
    if (event.getOutcome() != null) { payload.put("outcome", event.getOutcome()); }
    return payload;
  }

  private Map<String, Object> payload(FlowTaskEvent event) {
    var payload = basePayload(event.getEventFactId(), event.getOccurredAt(), event.getFlowKey(), event.getInstanceId(),
        event.getApprovalCaseId(), event.getSubmissionRound());
    payload.put("taskId", event.getTaskId());
    if (event.getFactKind() == FlowTaskEvent.FactKind.ACTION_COMPLETED) { payload.put("action", event.getActionName()); }
    return payload;
  }

  private Map<String, Object> basePayload(String factId, Instant time, String flowKey, String instanceId, String caseId, Integer round) {
    requireText(factId, "源事实身份");
    requireText(flowKey, "流程身份");
    requireText(instanceId, "实例身份");
    var payload = new LinkedHashMap<String, Object>();
    payload.put("eventFactId", factId);
    payload.put("eventTime", time.toString());
    payload.put("flowKey", flowKey);
    payload.put("instanceId", instanceId);
    if (caseId != null) { payload.put("approvalCaseId", caseId); }
    if (round != null) { payload.put("submissionRound", round); }
    return payload;
  }

  private void requireText(String value, String label) {
    if (value == null || value.isBlank()) { throw new BusinessException("工作流源事实缺少" + label); }
  }
}
