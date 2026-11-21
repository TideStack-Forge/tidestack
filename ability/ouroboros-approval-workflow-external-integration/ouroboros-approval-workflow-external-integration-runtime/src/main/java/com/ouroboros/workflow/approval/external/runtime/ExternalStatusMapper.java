package com.ouroboros.workflow.approval.external.runtime;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent.EventType;

/**
 * 外部状态词表映射（单一事实源）：集中外部状态/动作/事件到平台状态与 TaskState 的映射，
 * 替换各服务手写的分散词表，避免 "approve"/"APPROVED"/子串匹配三套规则漂移。
 */
public final class ExternalStatusMapper {

  /**
   * 归一化状态词域（终态结果字面量，单一来源）：外部事件经映射归一化后的平台终态状态
   * （APPROVED/REJECTED/CANCELED/ERROR），与校验器 REQUIRED_RESULT_STATUSES 的终态子集
   * 一致（RUNNING 为运行中非终态）。
   *
   * <p>引用关系：{@code ExternalEventProcessor} 以其为 TERMINAL_STATUSES 别名并用于终态判定
   * （applyResultMappingRules / isTerminalEntry）。与
   * {@code ExternalStateReconcileJob.EXTERNAL_TERMINAL_STATUSES}（外部状态词域）及
   * {@code ExternalEventProcessor.TERMINAL_EVENT_TYPES}（事件类型词域）分属不同词域，勿混用。
   */
  public static final List<String> TERMINAL_STATUSES = List.of("APPROVED", "REJECTED", "CANCELED", "ERROR");

  private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ExternalStatusMapper.class);
  private static final ObjectMapper JSON = new ObjectMapper();

  private ExternalStatusMapper() {
  }

  public static EventType instanceResultEventType(String result) {
    return switch (result) {
      case "APPROVED" -> EventType.APPROVED;
      case "REJECTED" -> EventType.REJECTED;
      case "CANCELED" -> EventType.CANCELLED;
      case "ERROR" -> EventType.FAILED;
      default -> throw new IllegalStateException("非终态状态无法映射事件: " + result);
    };
  }

  public static EventType instanceResultEventType(String result, String systemEvent) {
    if (systemEvent != null && !systemEvent.isBlank()) {
      try { return EventType.valueOf(systemEvent); }
      catch (IllegalArgumentException error) {
        LOGGER.debug("systemEvent 不是规范事件枚举名，回退外部状态映射: {}", systemEvent);
      }
    }
    return instanceResultEventType(result);
  }

  public static String defaultInstanceOutcome(String result) {
    return switch (result) {
      case "APPROVED" -> "approved";
      case "REJECTED" -> "rejected";
      case "CANCELED" -> "cancelled";
      case "ERROR" -> "failed";
      default -> null;
    };
  }

  /** Uses the same pinned result mapping for source processing and legacy state inspection. */
  public static ResultMappingEntry matchResultMapping(WorkflowEngineMapping mapping, String externalStatus) {
    if (externalStatus == null || mapping.getResultMappingsJson() == null) { return null; }
    try {
      var array = JSON.readTree(mapping.getResultMappingsJson());
      if (array == null || !array.isArray()) { return null; }
      for (var item : array) {
        String status = item.path("externalStatus").asText(null);
        if (externalStatus.equals(status)) {
          return new ResultMappingEntry(status, item.path("systemEvent").asText(null), item.path("outcome").asText(null));
        }
      }
      return null;
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("结果映射解析失败: " + error.getMessage(), error);
    }
  }

  public record ResultMappingEntry(String externalStatus, String systemEvent, String outcome) { }

  /** 外部审批记录动作 → TaskState（时间线）。未知动作返回 null。 */
  public static TaskState recordActionToTaskState(String action) {
    if (action == null) {
      return null;
    }
    return switch (action) {
      case "approve" -> TaskState.APPROVED;
      case "reject" -> TaskState.REJECTED;
      case "cancel" -> TaskState.CANCELLED;
      case "transfer" -> TaskState.PROCESSING;
      default -> null;
    };
  }

  /**
   * 任务事件 eventType/resultCode → TaskState。resultCode 是适配器归一化后的任务结果，优先于
   * 事件类型；未知值返回 null，由事件处理器进入重试/人工处理，禁止静默写成处理中。
   */
  public static TaskState taskStateFromEvent(String eventType, String resultCode) {
    if (resultCode != null && !resultCode.isBlank()) {
      TaskState resultState = switch (resultCode.toLowerCase()) {
        case "agree", "approve", "approved" -> TaskState.APPROVED;
        case "refuse", "reject", "rejected" -> TaskState.REJECTED;
        case "cancel", "canceled", "cancelled" -> TaskState.CANCELLED;
        case "redirect", "audit", "running", "processing" -> TaskState.PROCESSING;
        case "create", "created" -> TaskState.CREATED;
        default -> null;
      };
      if (resultState != null) {
        return resultState;
      }
    }
    if (eventType == null || eventType.isBlank()) {
      return null;
    }
    String normalizedType = eventType.toLowerCase();
    int separator = normalizedType.lastIndexOf('/');
    String operation = separator < 0 ? normalizedType : normalizedType.substring(separator + 1);
    return switch (operation) {
      case "start", "create", "created" -> TaskState.CREATED;
      case "cancel", "canceled", "cancelled" -> TaskState.CANCELLED;
      case "redirect", "comment" -> TaskState.PROCESSING;
      default -> null;
    };
  }

  /** 查询状态（running/suspended/finished）→ 实例绑定状态；未知返回 null。 */
  public static String instanceStatusFromQuery(String status) {
    if (status == null) {
      return null;
    }
    return switch (status) {
      case "running" -> com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding.STATUS_RUNNING;
      case "suspended" -> com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding.STATUS_SUSPENDED;
      case "finished" -> com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding.STATUS_FINISHED;
      default -> null;
    };
  }

}
