package com.ouroboros.workflow.approval.external.runtime.event;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Component;

import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.external.ExternalCallbackEnvelope;
import com.ouroboros.workflow.approval.external.runtime.ExternalEventOrder;
import com.ouroboros.workflow.approval.external.runtime.ExternalEventTimes;
import com.ouroboros.workflow.approval.external.runtime.ExternalStatusMapper;
import com.ouroboros.workflow.approval.external.runtime.ExternalStatusMapper.ResultMappingEntry;

import static com.ouroboros.workflow.approval.external.runtime.ExternalStatusMapper.matchResultMapping;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalTaskBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalTaskBindingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBindingService;
import com.ouroboros.workflow.approval.TrustedFlowTaskAction;
import com.ouroboros.workflow.approval.event.FlowEventOutbox;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingValidator;
import com.ouroboros.workflow.approval.external.runtime.start.ExternalStartFactService;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent.EventType;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;

import io.vavr.control.Try;

/**
 * 外部事件处理器：把 inbox 事件归一化为绑定更新与统一结果事件。
 *
 * <p>处理规则（M9-T4）：领取（PENDING→PROCESSING）→ 乱序检查（可选 sequence）→ 绑定定位 →
 * 结果映射 → 终态/运行中/未知分流 → 状态流转。事件发布与绑定更新在同一事务内；
 * 未知状态不自动结束流程，进入失败重试。
 */
@Component
public class ExternalEventProcessor {

  private static final Logger logger = LoggerFactory.getLogger(ExternalEventProcessor.class);

  /** 终态外部状态字面量（单一来源见 ExternalStatusMapper）。 */
  private static final List<String> TERMINAL_STATUSES = ExternalStatusMapper.TERMINAL_STATUSES;

  /**
   * 事件类型词域（终态规范事件）：归一化平台事件类型（{@link EventType}）字面量，
   * systemEvent 命中即视为终态，按映射解释外部状态。
   *
   * <p>引用关系：{@link #isTerminalEntry}（FULL_PROCESS 按映射解释外部状态，允许自定义
   * 外部状态映射到终态规范事件）。与同类 {@link #TERMINAL_STATUSES}（状态字面量词域，
   * 单一来源见 {@code ExternalStatusMapper}）及
   * {@code ExternalStateReconcileJob.EXTERNAL_TERMINAL_STATUSES}（外部状态词域）分属不同词域，
   * 勿混用。
   */
  private static final List<EventType> TERMINAL_EVENT_TYPES =
      List.of(EventType.APPROVED, EventType.REJECTED, EventType.CANCELLED, EventType.FAILED);

  private static final String CLAIM_OWNER = "external-event-processor";

  private final ExternalEventInboxService inboxService;
  private final ExternalResultBindingService resultBindingService;
  private final ExternalInstanceBindingService instanceBindingService;
  private final ExternalTaskBindingService taskBindingService;
  private final WorkflowEngineMappingService mappingService;
  private final InstanceEventDispatcher instanceEventDispatcher;
  private final TaskEventDispatcher taskEventDispatcher;
  private final ExternalStartFactService startFactService;
  private final TransactionTemplate transactionTemplate;

  @Autowired
  public ExternalEventProcessor(
      ExternalEventInboxService inboxService,
      ExternalResultBindingService resultBindingService,
      ExternalInstanceBindingService instanceBindingService,
      ExternalTaskBindingService taskBindingService,
      WorkflowEngineMappingService mappingService,
      InstanceEventDispatcher instanceEventDispatcher,
      TaskEventDispatcher taskEventDispatcher,
      ExternalStartFactService startFactService,
      PlatformTransactionManager transactionManager) {
    this.inboxService = inboxService;
    this.resultBindingService = resultBindingService;
    this.instanceBindingService = instanceBindingService;
    this.taskBindingService = taskBindingService;
    this.mappingService = mappingService;
    this.instanceEventDispatcher = instanceEventDispatcher;
    this.taskEventDispatcher = taskEventDispatcher;
    this.startFactService = startFactService;
    this.transactionTemplate = transactionManager == null ? null : new TransactionTemplate(transactionManager);
  }

  /** 供不启动 Spring 事务的纯规则测试使用。 */
  public ExternalEventProcessor(
      ExternalEventInboxService inboxService,
      ExternalResultBindingService resultBindingService,
      ExternalInstanceBindingService instanceBindingService,
      ExternalTaskBindingService taskBindingService,
      WorkflowEngineMappingService mappingService,
      InstanceEventDispatcher instanceEventDispatcher,
      TaskEventDispatcher taskEventDispatcher,
      ExternalStartFactService startFactService) {
    this(inboxService, resultBindingService, instanceBindingService, taskBindingService,
        mappingService, instanceEventDispatcher, taskEventDispatcher,
        startFactService, null);
  }

  /** 处理结果分类。 */
  enum Outcome {
    /** 成功处理。 */
    PROCESSED,
    /** 乱序事件：不覆盖状态，条目置 STALE。 */
    STALE,
    /** 未知状态：不自动结束流程，条目进入失败重试。 */
    UNKNOWN
  }

  /**
   * 按信封处理（ingest 触发与重试扫描共用入口）。
   */
  public Try<Void> processByEnvelope(ExternalCallbackEnvelope envelope) {
    Optional<ExternalEventInbox> inboxOpt = inboxService.findByEventId(
        envelope.adapterId(), envelope.connectionId(), envelope.eventId());
    if (inboxOpt.isEmpty()) {
      // 未入箱（幂等键冲突或信封不来自 ingest），按已处理返回
      return Try.success(null);
    }
    return processRow(inboxOpt.get(), envelope);
  }

  /**
   * 按 inbox 行处理（兜底扫描共用入口；envelope 由行字段重建）。
   */
  public Try<Void> processRow(ExternalEventInbox row) {
    return processRow(row, toEnvelope(row));
  }

  public Try<Void> processRow(ExternalEventInbox row, ExternalCallbackEnvelope envelope) {
    Optional<ExternalEventInboxService.Claim> claimOpt =
        inboxService.claim(row.getId(), CLAIM_OWNER, ExternalEventInboxService.DEFAULT_LEASE_SECONDS);
    if (claimOpt.isEmpty()) {
      return Try.success(null);
    }
    ExternalEventInboxService.Claim claim = claimOpt.get();
    ExternalEventInbox claimed = claim.inbox();
    try {
      runBusinessTransaction(claimed, claim.claimToken(), envelope);
      return Try.success(null);
    } catch (RuntimeException ex) {
      boolean recorded = inboxService.markFailure(
          claimed.getId(), claim.claimToken(), String.valueOf(ex.getMessage()));
      if (!recorded) {
        logger.warn("外部事件处理失败，但当前 claim 已失效，无法写入 FAILED: inboxId={}", claimed.getId());
      }
      return Try.failure(ex);
    }
  }

  private void runBusinessTransaction(
      ExternalEventInbox row, String claimToken, ExternalCallbackEnvelope envelope) {
    Runnable work = () -> {
      Outcome outcome = applyRules(row, envelope);
      switch (outcome) {
        case PROCESSED -> requireClaimCompletion(
            inboxService.markProcessed(row.getId(), claimToken), row.getId(), "PROCESSED");
        case STALE -> requireClaimCompletion(
            inboxService.markStale(row.getId(), claimToken), row.getId(), "STALE");
        case UNKNOWN -> throw new IllegalStateException(
            "未知外部状态: " + firstNonBlank(envelope.resultCode(), envelope.eventType(), row.getResultCode(), row.getEventType()));
      }
    };
    if (transactionTemplate == null) {
      work.run();
      return;
    }
    transactionTemplate.executeWithoutResult(status -> work.run());
  }

  private void requireClaimCompletion(boolean updated, String inboxId, String targetStatus) {
    if (!updated) {
      throw new IllegalStateException("外部事件 claim 已失效，无法标记 " + targetStatus + ": inboxId=" + inboxId);
    }
  }

  private Outcome applyRules(ExternalEventInbox row, ExternalCallbackEnvelope envelope) {
    // 1. 绑定定位：结果型优先，其次全过程实例绑定
    Optional<ExternalResultBinding> resultBinding = resultBindingService.findByExternalInstance(
        row.getAdapterId(), row.getConnectionId(), row.getExternalInstanceId());
    if (resultBinding.isPresent()) {
      return applyResultBindingRules(row, envelope, resultBinding.get());
    }
    Optional<ExternalInstanceBinding> instanceBinding = instanceBindingService.findByExternalInstance(
        row.getAdapterId(), row.getConnectionId(), row.getExternalInstanceId());
    if (instanceBinding.isPresent()) {
      if (envelope.externalTaskId() != null && !envelope.externalTaskId().isBlank()) {
        return applyTaskBindingRules(row, envelope, instanceBinding.get());
      }
      return applyInstanceBindingRules(row, envelope, instanceBinding.get());
    }
    throw new IllegalStateException("绑定不存在: adapterId=" + row.getAdapterId()
        + ", externalInstanceId=" + row.getExternalInstanceId());
  }

  private Outcome applyResultBindingRules(ExternalEventInbox row, ExternalCallbackEnvelope envelope, ExternalResultBinding binding) {
    LocalDateTime externalEventTime = parseExternalEventTime(row, envelope);
    int compare = compareEventOrder(
        envelope, binding.getExternalRevision(), externalEventTime,
        binding.getExternalEventTime(), binding.getLastExternalEventId());
    if (compare < 0) {
      return Outcome.STALE;
    }
    if (compare == 0) {
      // 同 sequence 重投：幂等成功，不更新绑定、不重复发布事件
      return Outcome.PROCESSED;
    }
    WorkflowEngineMapping mapping = requireMapping(binding.getMappingId(), binding.getMappingVersion());
    String externalStatus = firstNonBlank(envelope.resultCode(), envelope.eventType(), row.getResultCode(), row.getEventType());
    ResultMappingEntry entry = matchResultMapping(mapping, externalStatus);
    if (entry != null && TERMINAL_STATUSES.contains(entry.externalStatus())) {
      String outcome = firstNonBlank(entry.outcome(), defaultOutcome(entry.externalStatus()));
      Try<Boolean> finished = resultBindingService.markFinished(
          binding.getTrackingId(), outcome, envelope.sequence(), externalEventTime, envelope.eventId());
      if (finished.isFailure()) {
        throw new IllegalStateException(finished.getCause().getMessage());
      }
      if (!finished.get()) {
        return Outcome.STALE;
      }
      releaseStartReservation(binding);
      instanceEventDispatcher.dispatchEvent(buildTerminalEvent(binding, entry.externalStatus(), outcome));
      return Outcome.PROCESSED;
    }
    if (entry != null) {
      Try<Boolean> updated = resultBindingService.updateRevision(
          binding.getTrackingId(), envelope.sequence(), externalEventTime, envelope.eventId());
      if (updated.isFailure()) {
        throw new IllegalStateException(updated.getCause().getMessage());
      }
      return updated.get() ? Outcome.PROCESSED : Outcome.STALE;
    }
    return Outcome.UNKNOWN;
  }

  private Outcome applyInstanceBindingRules(ExternalEventInbox row, ExternalCallbackEnvelope envelope, ExternalInstanceBinding binding) {
    LocalDateTime externalEventTime = parseExternalEventTime(row, envelope);
    int compare = compareEventOrder(
        envelope, binding.getExternalRevision(), externalEventTime,
        binding.getExternalEventTime(), binding.getLastExternalEventId());
    if (compare < 0) {
      return Outcome.STALE;
    }
    if (compare == 0) {
      return Outcome.PROCESSED;
    }
    // FULL_PROCESS 实例状态按映射 resultMappings 解释（设计 L501 规则 5），不硬编码外部状态表
    WorkflowEngineMapping mapping = requireMapping(binding.getMappingId(), binding.getMappingVersion());
    String externalStatus = firstNonBlank(envelope.resultCode(), envelope.eventType(), row.getResultCode(), row.getEventType());
    if (externalStatus == null) {
      return Outcome.UNKNOWN;
    }
    ResultMappingEntry entry = matchResultMapping(mapping, externalStatus);
    if (entry == null) {
      return Outcome.UNKNOWN;
    }
    if (isTerminalEntry(entry)) {
      String outcome = firstNonBlank(entry.outcome(), defaultOutcome(entry.externalStatus()));
      Try<Boolean> updated = instanceBindingService.updateStatus(
          binding.getLocalInstanceId(), ExternalInstanceBinding.STATUS_FINISHED, outcome,
          envelope.sequence(), externalEventTime, envelope.eventId());
      if (updated.isFailure()) {
        throw new IllegalStateException(updated.getCause().getMessage());
      }
      if (!updated.get()) {
        return Outcome.STALE;
      }
      releaseStartReservation(binding);
      instanceEventDispatcher.dispatchEvent(ExternalFlowEventFactory.terminal(
          binding.getFlowKey(), binding.getLocalInstanceId(),
          toEventType(entry.externalStatus(), entry.systemEvent()), outcome, null,
          binding.getBusinessRecordType(), binding.getBusinessRecordId(), binding.getCallbackFlow(),
          binding.getApprovalCaseId(), binding.getSubmissionRound(), envelope.eventId()));
      return Outcome.PROCESSED;
    }
    // 非终态：RUNNING/SUSPENDED 按字面映射，其他非终态保守视为 RUNNING（实例绑定状态只有运行/挂起/终态）
    String mappedStatus = switch (entry.externalStatus()) {
      case "RUNNING" -> ExternalInstanceBinding.STATUS_RUNNING;
      case "SUSPENDED" -> ExternalInstanceBinding.STATUS_SUSPENDED;
      default -> ExternalInstanceBinding.STATUS_RUNNING;
    };
    String previousStatus = binding.getStatus();
    Try<Boolean> updated = instanceBindingService.updateStatus(
        binding.getLocalInstanceId(), mappedStatus, null,
        envelope.sequence(), externalEventTime, envelope.eventId());
    if (updated.isFailure()) {
      throw new IllegalStateException(updated.getCause().getMessage());
    }
    if (!updated.get()) {
      return Outcome.STALE;
    }
    boolean suspensionChanged = ExternalInstanceBinding.STATUS_SUSPENDED.equals(mappedStatus)
        && !ExternalInstanceBinding.STATUS_SUSPENDED.equals(previousStatus);
    boolean resumed = ExternalInstanceBinding.STATUS_RUNNING.equals(mappedStatus)
        && ExternalInstanceBinding.STATUS_SUSPENDED.equals(previousStatus);
    if (suspensionChanged || resumed) {
      EventType lifecycleEvent = suspensionChanged ? EventType.SUSPENDED : EventType.RESUMED;
      instanceEventDispatcher.dispatchEvent(ExternalFlowEventFactory.terminal(
          binding.getFlowKey(), binding.getLocalInstanceId(), lifecycleEvent, null, null,
          binding.getBusinessRecordType(), binding.getBusinessRecordId(), binding.getCallbackFlow(),
          binding.getApprovalCaseId(), binding.getSubmissionRound(), envelope.eventId()));
    }
    return Outcome.PROCESSED;
  }

  private Outcome applyTaskBindingRules(ExternalEventInbox row, ExternalCallbackEnvelope envelope, ExternalInstanceBinding instanceBinding) {
    LocalDateTime externalEventTime = parseExternalEventTime(row, envelope);
    Optional<ExternalTaskBinding> existing = taskBindingService.findByExternalTask(
        row.getAdapterId(), row.getConnectionId(), envelope.externalTaskId());
    if (existing.isPresent()) {
      int compare = compareEventOrder(
          envelope, existing.get().getExternalRevision(), externalEventTime,
          existing.get().getExternalEventTime(), existing.get().getLastExternalEventId());
      if (compare < 0) {
        return Outcome.STALE;
      }
      if (compare == 0) {
        return Outcome.PROCESSED;
      }
    }
    ExternalTaskBinding task;
    if (existing.isPresent()) {
      task = existing.get();
      if (!java.util.Objects.equals(task.getLocalInstanceId(), instanceBinding.getLocalInstanceId())
          || !java.util.Objects.equals(task.getScope(), instanceBinding.getScope())
          || !java.util.Objects.equals(task.getMappingId(), instanceBinding.getMappingId())
          || !java.util.Objects.equals(task.getMappingVersion(), instanceBinding.getMappingVersion())) {
        throw new IllegalStateException("外部任务绑定身份冲突: externalTaskId="
            + envelope.externalTaskId());
      }
    } else {
      ExternalTaskBinding created = new ExternalTaskBinding();
      created.setLocalInstanceId(instanceBinding.getLocalInstanceId());
      created.setScope(instanceBinding.getScope());
      created.setMappingId(instanceBinding.getMappingId());
      created.setMappingVersion(instanceBinding.getMappingVersion());
      created.setAdapterId(row.getAdapterId());
      created.setConnectionId(row.getConnectionId());
      created.setExternalTaskId(envelope.externalTaskId());
      created.setStatus(ExternalTaskBinding.STATUS_CREATED);
      task = taskBindingService.create(created)
          .getOrElseThrow(cause -> new IllegalStateException(
              "外部任务绑定创建失败: externalTaskId=" + envelope.externalTaskId(), cause));
    }
    TaskState state = mapTaskState(envelope);
    if (state == null) {
      return Outcome.UNKNOWN;
    }
    Try<Boolean> updated = taskBindingService.updateStatus(
        task.getLocalTaskId(), taskStateName(state), envelope.sequence(), externalEventTime, envelope.eventId());
    if (updated.isFailure()) {
      throw new IllegalStateException(updated.getCause().getMessage());
    }
    if (!updated.get()) {
      return Outcome.STALE;
    }
    dispatchTaskEvent(task.getLocalTaskId(), instanceBinding.getLocalInstanceId(), state, row.getId(), externalEventTime);
    return Outcome.PROCESSED;
  }

  /** 任务事件构建与派发（FULL_PROCESS 事件路径使用）。 */
  private void dispatchTaskEvent(String taskId, String instanceId, TaskState state, String factId, LocalDateTime occurredAt) {
    var event = new FlowTaskEvent();
    event.setEventFactId(factId);
    event.setTaskId(taskId);
    event.setInstanceId(instanceId);
    event.setState(state);
    event.setEventTime(occurredAt);
    taskEventDispatcher.dispatchEvent(event);
  }

  /**
   * 终态判定：外部状态命中终态字面量，或映射的 systemEvent 命中终态规范事件
   * （FULL_PROCESS 按映射解释外部状态，允许自定义外部状态映射到终态）。
   */
  private boolean isTerminalEntry(ResultMappingEntry entry) {
    if (TERMINAL_STATUSES.contains(entry.externalStatus())) {
      return true;
    }
    if (entry.systemEvent() == null || entry.systemEvent().isBlank()) {
      return false;
    }
    try {
      return TERMINAL_EVENT_TYPES.contains(EventType.valueOf(entry.systemEvent()));
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  /**
   * 事件顺序比较：sequence 双方都有值时先比较 sequence；否则用外部事件时间做单调保护。
   * 小于 0 = 乱序（STALE），等于 0 = 同事件事实重投（幂等 PROCESSED），大于 0 = 正常处理。
   */
  private int compareEventOrder(
      ExternalCallbackEnvelope envelope,
      String externalRevision,
      LocalDateTime externalEventTime,
      LocalDateTime lastExternalEventTime,
      String lastExternalEventId) {
    if (hasText(envelope.sequence())) {
      if (hasText(externalRevision)) {
        int compare = ExternalEventOrder.compareFact(
            envelope.sequence(), externalEventTime, envelope.eventId(),
            externalRevision, lastExternalEventTime, lastExternalEventId);
        if (compare < 0) {
          logger.warn("外部事件乱序跳过: eventId={}, sequence={} < revision={}",
              envelope.eventId(), envelope.sequence(), externalRevision);
        }
        return compare;
      }
      return 1;
    }
    if (lastExternalEventTime == null) {
      return 1;
    }
    if (externalEventTime == null) {
      logger.warn("外部事件缺少 sequence 和 eventTime，跳过以保护已确认状态: eventId={}", envelope.eventId());
      return -1;
    }
    int compare = ExternalEventOrder.compareFact(
        null, externalEventTime, envelope.eventId(),
        externalRevision, lastExternalEventTime, lastExternalEventId);
    if (compare < 0) {
      logger.warn("外部事件乱序跳过: eventId={}, eventTime={} < lastEventTime={}",
          envelope.eventId(), externalEventTime, lastExternalEventTime);
    }
    return compare;
  }

  private LocalDateTime parseExternalEventTime(ExternalEventInbox row, ExternalCallbackEnvelope envelope) {
    return ExternalEventTimes.parseOrNull(firstNonBlank(
        envelope.externalEventTime(), row == null ? null : row.getExternalEventTime()));
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private WorkflowEngineMapping requireMapping(String mappingId, Integer expectedVersion) {
    WorkflowEngineMapping mapping = mappingService.findByIdForSystem(mappingId).orElse(null);
    if (mapping == null) {
      throw new IllegalStateException("映射不存在: " + mappingId);
    }
    if (expectedVersion != null && !expectedVersion.equals(mapping.getVersion())) {
      throw new IllegalStateException("映射版本不匹配: mappingId=" + mappingId
          + ", expectedVersion=" + expectedVersion + ", actualVersion=" + mapping.getVersion());
    }
    return mapping;
  }

  private TaskState mapTaskState(ExternalCallbackEnvelope envelope) {
    return ExternalStatusMapper.taskStateFromEvent(envelope.eventType(), envelope.resultCode());
  }

  private String taskStateName(TaskState state) {
    return switch (state) {
      case CREATED -> ExternalTaskBinding.STATUS_CREATED;
      case APPROVED -> ExternalTaskBinding.STATUS_APPROVED;
      case REJECTED -> ExternalTaskBinding.STATUS_REJECTED;
      case CANCELLED -> ExternalTaskBinding.STATUS_CANCELLED;
      default -> ExternalTaskBinding.STATUS_PROCESSING;
    };
  }

  private FlowInstanceEvent buildTerminalEvent(ExternalResultBinding binding, String externalStatus, String outcome) {
    return ExternalFlowEventFactory.terminalWithTracking(
        binding.getFlowKey(), binding.getTrackingId(), toEventType(externalStatus), outcome, null,
        binding.getBusinessRecordType(), binding.getBusinessRecordId(), null, binding.getExternalUrl(),
        binding.getApprovalCaseId(), binding.getSubmissionRound(), null);
  }

  private void releaseStartReservation(ExternalResultBinding binding) {
    startFactService.releaseBusinessReservation(
        binding.getScope(), binding.getFlowKey(), binding.getBusinessRecordType(),
        binding.getBusinessRecordId(), binding.getTrackingId());
  }

  private void releaseStartReservation(ExternalInstanceBinding binding) {
    startFactService.releaseBusinessReservation(
        binding.getScope(), binding.getFlowKey(), binding.getBusinessRecordType(),
        binding.getBusinessRecordId(), binding.getLocalInstanceId());
  }

  private EventType toEventType(String externalStatus, String systemEvent) {
    return ExternalStatusMapper.instanceResultEventType(externalStatus, systemEvent);
  }

  private EventType toEventType(String externalStatus) {
    return ExternalStatusMapper.instanceResultEventType(externalStatus);
  }

  private String defaultOutcome(String externalStatus) {
    return ExternalStatusMapper.defaultInstanceOutcome(externalStatus);
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private ExternalCallbackEnvelope toEnvelope(ExternalEventInbox row) {
    return new ExternalCallbackEnvelope(
        row.getEventId(),
        row.getAdapterId(),
        row.getConnectionId(),
        row.getExternalInstanceId(),
        row.getExternalTaskId(),
        row.getSequence(),
        row.getExternalEventTime(),
        row.getEventType(),
        row.getResultCode(),
        row.getPayloadDigest());
  }

}
