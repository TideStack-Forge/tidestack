package com.ouroboros.workflow.approval.external.runtime.action;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.ExternalActionCommand;
import com.ouroboros.workflow.approval.external.ExternalActionResult;
import com.ouroboros.workflow.approval.external.ExternalWorkflowActionExecutor;
import com.ouroboros.workflow.approval.external.runtime.ConnectionAwareAdapterResolver;
import com.ouroboros.workflow.approval.external.runtime.ExternalLeaseHeartbeat;
import com.ouroboros.workflow.approval.external.runtime.ExternalRouteKeys;
import com.ouroboros.workflow.approval.external.runtime.ExternalFactTransaction;
import com.ouroboros.workflow.approval.external.runtime.binding.ActionStatusUpdate;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBindingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalTaskBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalTaskBindingService;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalFlowEventFactory;
import com.ouroboros.workflow.approval.external.runtime.start.ExternalStartFactService;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent.EventType;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

import io.vavr.control.Try;

/** 外部动作的可靠执行边界：事实先落库，远程调用在事务外，本地完成原子提交。 */
@Service
public class ExternalActionExecutionService {

  private enum LocalCompletion {
    COMPLETED,
    CONFLICT
  }

  private final ExternalActionFactService factService;
  private final ConnectionAwareAdapterResolver adapterResolver;
  private final ExternalTaskBindingService taskBindingService;
  private final ExternalInstanceBindingService instanceBindingService;
  private final ExternalResultBindingService resultBindingService;
  private final TaskEventDispatcher taskEventDispatcher;
  private final InstanceEventDispatcher instanceEventDispatcher;
  private final ExternalStartFactService startFactService;
  private final TransactionTemplate transactionTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

  @Autowired
  public ExternalActionExecutionService(
      ExternalActionFactService factService,
      ConnectionAwareAdapterResolver adapterResolver,
      ExternalTaskBindingService taskBindingService,
      ExternalInstanceBindingService instanceBindingService,
      ExternalResultBindingService resultBindingService,
      TaskEventDispatcher taskEventDispatcher,
      InstanceEventDispatcher instanceEventDispatcher,
      ExternalStartFactService startFactService,
      PlatformTransactionManager transactionManager) {
    this.factService = factService;
    this.adapterResolver = adapterResolver;
    this.taskBindingService = taskBindingService;
    this.instanceBindingService = instanceBindingService;
    this.resultBindingService = resultBindingService;
    this.taskEventDispatcher = taskEventDispatcher;
    this.instanceEventDispatcher = instanceEventDispatcher;
    this.startFactService = startFactService;
    this.transactionTemplate = transactionManager == null ? null : new TransactionTemplate(transactionManager);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public Try<Void> executeTask(
      ExternalTaskBinding binding, ExternalActionRequest request, String targetStatus) {
    return reserveAndExecute(
        ExternalActionFact.TARGET_TASK,
        binding.getLocalTaskId(),
        binding.getStatus(),
        targetStatus,
        null,
        null,
        binding.getAdapterId(),
        binding.getConnectionId(),
        binding.getMappingVersion(),
        request);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public Try<Void> executeInstance(
      ExternalInstanceBinding binding,
      ExternalActionRequest request,
      String targetStatus,
      String outcome,
      EventType eventType) {
    if (ExternalInstanceBinding.STATUS_FINISHED.equals(binding.getStatus())) {
      return actionConflict("外部实例已进入终态，拒绝重复动作: " + binding.getLocalInstanceId(), null);
    }
    return reserveAndExecute(
        ExternalActionFact.TARGET_INSTANCE,
        binding.getLocalInstanceId(),
        binding.getStatus(),
        targetStatus,
        outcome,
        eventType,
        binding.getAdapterId(),
        binding.getConnectionId(),
        binding.getMappingVersion(),
        request);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public Try<Void> executeResult(
      ExternalResultBinding binding,
      ExternalActionRequest request,
      String outcome,
      EventType eventType) {
    if (ExternalResultBinding.STATUS_FINISHED.equals(binding.getStatus())
        || ExternalResultBinding.STATUS_FAILED.equals(binding.getStatus())) {
      return actionConflict("外部结果绑定已进入终态，拒绝重复动作: " + binding.getTrackingId(), null);
    }
    return reserveAndExecute(
        ExternalActionFact.TARGET_RESULT,
        binding.getTrackingId(),
        binding.getStatus(),
        ExternalResultBinding.STATUS_FINISHED,
        outcome,
        eventType,
        binding.getAdapterId(),
        binding.getConnectionId(),
        binding.getMappingVersion(),
        request);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public Try<Void> recover(ExternalActionFactService.RecoveryClaim claim) {
    return executeClaimed(claim.fact(), claim.claimToken());
  }

  private Try<Void> reserveAndExecute(
      String targetType,
      String targetLocalId,
      String sourceState,
      String targetStatus,
      String outcome,
      EventType eventType,
      String adapterId,
      String connectionId,
      Integer mappingVersion,
      ExternalActionRequest request) {
    ExternalActionFact fact;
    try {
      fact = newFact(
          targetType, targetLocalId, sourceState, targetStatus, outcome, eventType,
          adapterId, connectionId, mappingVersion, request);
    } catch (RuntimeException ex) {
      return Try.failure(ex);
    }
    Try<ExternalActionFactService.Reservation> reserved = factService.reserve(fact);
    if (reserved.isFailure()) {
      return Try.failure(reserved.getCause());
    }
    ExternalActionFact actual = reserved.get().fact();
    if (!reserved.get().claimed()) {
      return existingResult(actual);
    }
    return executeClaimed(actual, actual.getClaimToken());
  }

  private ExternalActionFact newFact(
      String targetType,
      String targetLocalId,
      String sourceState,
      String targetStatus,
      String outcome,
      EventType eventType,
      String adapterId,
      String connectionId,
      Integer mappingVersion,
      ExternalActionRequest request) {
    String factId = UUID.randomUUID().toString();
    ExternalActionCommand command = toCommand(request, factId);
    ExternalActionFact fact = new ExternalActionFact();
    fact.setId(factId);
    fact.setTargetType(targetType);
    fact.setTargetLocalId(targetLocalId);
    fact.setSourceState(sourceState);
    fact.setTargetStatus(targetStatus);
    fact.setOutcome(outcome);
    fact.setEventType(eventType == null ? null : eventType.name());
    fact.setCanonicalAction(request.canonicalAction());
    fact.setAdapterId(adapterId);
    fact.setConnectionId(connectionId);
    fact.setMappingVersion(mappingVersion);
    try {
      fact.setCommandJson(objectMapper.writeValueAsString(command));
      if (ExternalActionFact.TARGET_TASK.equals(targetType)) {
        var taskFact = taskEvent(fact, command);
        taskEventDispatcher.prepareFact(taskFact);
        fact.setTaskFactJson(objectMapper.writeValueAsString(taskFact));
      }
      fact.setRequestFingerprint(ExternalRouteKeys.of(
          "action-fingerprint", objectMapper.writeValueAsString(request)));
    } catch (Exception ex) {
      throw new BusinessException("外部动作命令序列化失败", ex);
    }
    return fact;
  }

  private Try<Void> executeClaimed(ExternalActionFact fact, String claimToken) {
    ExternalActionCommand command;
    try {
      command = objectMapper.readValue(fact.getCommandJson(), ExternalActionCommand.class);
    } catch (Exception ex) {
      factService.markConflict(fact.getId(), claimToken, "外部动作命令无法反序列化: " + ex.getMessage());
      return Try.failure(new BusinessException("外部动作命令无法反序列化: factId=" + fact.getId(), ex));
    }
    if (!fact.getId().equals(command.idempotencyKey())) {
      factService.markConflict(fact.getId(), claimToken, "动作事实 ID 与远程幂等键不一致");
      return actionConflict("动作事实 ID 与远程幂等键不一致: factId=" + fact.getId(), null);
    }
    if (ExternalActionFact.PHASE_REPLIED.equals(fact.getRemoteCallPhase())) {
      return completeLocal(fact, claimToken, command);
    }

    ExternalWorkflowActionExecutor executor;
    try {
      executor = adapterResolver.resolvePort(
          fact.getAdapterId(), fact.getConnectionId(), ExternalWorkflowActionExecutor.class);
    } catch (RuntimeException ex) {
      factService.markConflict(fact.getId(), claimToken, stableError(ex));
      return Try.failure(ex);
    }
    if (!factService.markInFlight(fact.getId(), claimToken)) {
      return actionConflict("外部动作 claim 已失效: factId=" + fact.getId(), null);
    }

    Try<ExternalActionResult> executed;
    try (ExternalLeaseHeartbeat ignored = ExternalLeaseHeartbeat.start(
        () -> factService.renewClaim(fact.getId(), claimToken, ExternalActionFactService.DEFAULT_LEASE_SECONDS),
        ExternalActionFactService.DEFAULT_LEASE_SECONDS)) {
      executed = executor.execute(command);
    } catch (RuntimeException ex) {
      factService.markUnknown(fact.getId(), claimToken, stableError(ex));
      return actionConflict("外部动作结果未知: factId=" + fact.getId(), ex);
    }
    if (executed == null || executed.isFailure()) {
      Throwable cause = executed == null
          ? new IllegalStateException("适配器返回了 null Try") : executed.getCause();
      factService.markUnknown(fact.getId(), claimToken, stableError(cause));
      return actionConflict("外部动作结果未知: factId=" + fact.getId(), cause);
    }
    ExternalActionResult result = executed.get();
    if (result == null) {
      IllegalStateException cause = new IllegalStateException("适配器返回了空动作结果");
      factService.markUnknown(fact.getId(), claimToken, stableError(cause));
      return actionConflict("外部动作结果未知: factId=" + fact.getId(), cause);
    }
    String resultJson = resultJson(result);
    if (!result.accepted()) {
      factService.markRejected(fact.getId(), claimToken, resultJson);
      return actionConflict("外部系统明确拒绝动作: factId=" + fact.getId(), null);
    }
    if (!factService.markReplied(fact.getId(), claimToken, resultJson)) {
      return actionConflict("外部动作回复写入失败或 claim 已失效: factId=" + fact.getId(), null);
    }
    fact.setRemoteCallPhase(ExternalActionFact.PHASE_REPLIED);
    fact.setRepliedAt(factService.findById(fact.getId()).orElseThrow(
        () -> new BusinessException("外部动作回复事实不存在: " + fact.getId())).getRepliedAt());
    return completeLocal(fact, claimToken, command);
  }

  private Try<Void> completeLocal(
      ExternalActionFact fact, String claimToken, ExternalActionCommand command) {
    try {
      LocalCompletion completion = transactionTemplate == null
          ? completeLocalTransaction(fact, claimToken, command)
          : ExternalFactTransaction.execute(transactionTemplate, ExternalActionFact.class,
              () -> completeLocalTransaction(fact, claimToken, command));
      if (completion == LocalCompletion.CONFLICT) {
        return actionConflict("外部动作已接受，但本地状态已进入不兼容终态: factId=" + fact.getId(), null);
      }
      return Try.success(null);
    } catch (RuntimeException ex) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_PERSISTENCE_FAILED
              + "] 外部动作已回复，但本地完成失败: factId=" + fact.getId(), ex));
    }
  }

  private LocalCompletion completeLocalTransaction(
      ExternalActionFact fact, String claimToken, ExternalActionCommand command) {
    ActionStatusUpdate update = switch (fact.getTargetType()) {
      case ExternalActionFact.TARGET_TASK -> taskBindingService
          .applyActionStatus(fact.getTargetLocalId(), fact.getTargetStatus())
          .getOrElseThrow(cause -> new BusinessException("完成外部任务动作失败", cause));
      case ExternalActionFact.TARGET_INSTANCE -> instanceBindingService
          .applyActionStatus(fact.getTargetLocalId(), fact.getTargetStatus(), fact.getOutcome())
          .getOrElseThrow(cause -> new BusinessException("完成外部实例动作失败", cause));
      case ExternalActionFact.TARGET_RESULT -> resultBindingService
          .applyActionFinished(fact.getTargetLocalId(), fact.getOutcome())
          .getOrElseThrow(cause -> new BusinessException("完成外部结果动作失败", cause));
      default -> throw new BusinessException("未知外部动作目标类型: " + fact.getTargetType());
    };
    if (update == ActionStatusUpdate.CONFLICT) {
      if (!factService.markConflict(fact.getId(), claimToken, "本地绑定已进入不兼容终态")) {
        throw new BusinessException("外部动作冲突事实完成失败: factId=" + fact.getId());
      }
      return LocalCompletion.CONFLICT;
    }
    if (update == ActionStatusUpdate.UPDATED || ExternalActionFact.TARGET_TASK.equals(fact.getTargetType())) {
      dispatchEvent(fact, command);
    }
    releaseStartReservation(fact);
    if (!factService.markSucceeded(fact.getId(), claimToken)) {
      throw new BusinessException("外部动作事实完成失败或 claim 已失效: factId=" + fact.getId());
    }
    return LocalCompletion.COMPLETED;
  }

  private void releaseStartReservation(ExternalActionFact fact) {
    if (ExternalActionFact.TARGET_INSTANCE.equals(fact.getTargetType())
        && ExternalInstanceBinding.STATUS_FINISHED.equals(fact.getTargetStatus())) {
      ExternalInstanceBinding binding = requireInstanceBinding(fact.getTargetLocalId());
      startFactService.releaseBusinessReservation(
          binding.getScope(), binding.getFlowKey(), binding.getBusinessRecordType(),
          binding.getBusinessRecordId(), binding.getLocalInstanceId());
      return;
    }
    if (ExternalActionFact.TARGET_RESULT.equals(fact.getTargetType())) {
      ExternalResultBinding binding = requireResultBinding(fact.getTargetLocalId());
      startFactService.releaseBusinessReservation(
          binding.getScope(), binding.getFlowKey(), binding.getBusinessRecordType(),
          binding.getBusinessRecordId(), binding.getTrackingId());
    }
  }

  private void dispatchEvent(ExternalActionFact fact, ExternalActionCommand command) {
    if (ExternalActionFact.TARGET_TASK.equals(fact.getTargetType())) {
      dispatchTaskEvent(fact, command);
      return;
    }
    if (fact.getEventType() == null || fact.getEventType().isBlank()) {
      return;
    }
    EventType eventType = EventType.valueOf(fact.getEventType());
    if (ExternalActionFact.TARGET_INSTANCE.equals(fact.getTargetType())) {
      ExternalInstanceBinding binding = requireInstanceBinding(fact.getTargetLocalId());
      instanceEventDispatcher.enqueueEvent(ExternalFlowEventFactory.terminal(
          binding.getFlowKey(), binding.getLocalInstanceId(), eventType, fact.getOutcome(), command.userId(),
          binding.getBusinessRecordType(), binding.getBusinessRecordId(), binding.getCallbackFlow(),
          binding.getApprovalCaseId(), binding.getSubmissionRound(), fact.getId()));
      return;
    }
    ExternalResultBinding binding = requireResultBinding(fact.getTargetLocalId());
    instanceEventDispatcher.enqueueEvent(ExternalFlowEventFactory.terminalWithTracking(
        binding.getFlowKey(), binding.getTrackingId(), eventType, fact.getOutcome(), command.userId(),
        binding.getBusinessRecordType(), binding.getBusinessRecordId(), null, binding.getExternalUrl(),
        binding.getApprovalCaseId(), binding.getSubmissionRound(), fact.getId()));
  }

  private void dispatchTaskEvent(ExternalActionFact fact, ExternalActionCommand command) {
    FlowTaskEvent event;
    try {
      // Existing journals retain their original recovery path; new journals carry pre-command ownership.
      event = fact.getTaskFactJson() == null ? taskEvent(fact, command)
          : objectMapper.readValue(fact.getTaskFactJson(), FlowTaskEvent.class);
    } catch (Exception ex) {
      throw new BusinessException("外部任务成功事实无法恢复: " + fact.getId(), ex);
    }
    event.setEventTime(fact.getRepliedAt() == null ? fact.getCreatedAt() : fact.getRepliedAt());
    taskEventDispatcher.enqueueEvent(event);
  }

  private FlowTaskEvent taskEvent(ExternalActionFact fact, ExternalActionCommand command) {
    TaskState state;
    try {
      state = TaskState.valueOf(fact.getTargetStatus());
    } catch (IllegalArgumentException ex) {
      throw new BusinessException("外部任务成功命令缺少规范任务状态: " + fact.getId(), ex);
    }
    ExternalTaskBinding binding = requireTaskBinding(fact.getTargetLocalId());
    FlowTaskEvent event = new FlowTaskEvent();
    event.setEventFactId(fact.getId());
    event.setFactKind(FlowTaskEvent.FactKind.ACTION_COMPLETED);
    event.setTaskId(binding.getLocalTaskId());
    event.setInstanceId(binding.getLocalInstanceId());
    event.setAssignee(binding.getAssignee());
    event.setNodeId(binding.getNodeId());
    event.setActionName(command.canonicalAction());
    event.setComment(command.comment());
    event.setState(state);
    return event;
  }

  private ExternalTaskBinding requireTaskBinding(String id) {
    return taskBindingService.findByLocalId(id)
        .orElseThrow(() -> new BusinessException("外部任务绑定不存在: " + id));
  }

  private ExternalInstanceBinding requireInstanceBinding(String id) {
    return instanceBindingService.findByLocalId(id)
        .orElseThrow(() -> new BusinessException("外部实例绑定不存在: " + id));
  }

  private ExternalResultBinding requireResultBinding(String id) {
    return resultBindingService.findByTrackingId(id)
        .orElseThrow(() -> new BusinessException("外部结果绑定不存在: " + id));
  }

  private Try<Void> existingResult(ExternalActionFact fact) {
    if (ExternalActionFact.STATUS_SUCCEEDED.equals(fact.getStatus())) {
      return Try.success(null);
    }
    return actionConflict(
        "外部动作事实已存在且状态为 " + fact.getStatus() + ": factId=" + fact.getId(), null);
  }

  private ExternalActionCommand toCommand(ExternalActionRequest request, String idempotencyKey) {
    return new ExternalActionCommand(
        request.canonicalAction(), request.externalAction(), request.userId(), request.comment(),
        request.formData(), request.payload(), idempotencyKey, request.mappingVersion(),
        request.externalInstanceRef(), request.externalTaskRef());
  }

  private String resultJson(ExternalActionResult result) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("accepted", result.accepted());
    value.put("resultStatus", result.resultStatus());
    value.put("outcome", result.outcome());
    value.put("externalUrl", result.externalUrl());
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new BusinessException("外部动作结果序列化失败", ex);
    }
  }

  private Try<Void> actionConflict(String message, Throwable cause) {
    BusinessException exception = cause == null
        ? new BusinessException("[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT + "] " + message)
        : new BusinessException("[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT + "] " + message, cause);
    return Try.failure(exception);
  }

  private String stableError(Throwable cause) {
    String message = cause.getMessage();
    return message == null || message.isBlank()
        ? cause.getClass().getSimpleName() : cause.getClass().getSimpleName() + ": " + message;
  }
}
