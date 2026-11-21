package com.ouroboros.workflow.approval.external.feishu;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.enterprise.callback.EnterpriseCallbackRequest;
import com.ouroboros.enterprise.callback.VerifiedEnterpriseEvent;
import com.ouroboros.enterprise.feishu.FeishuCallbackProtocol;
import com.ouroboros.enterprise.feishu.FeishuCredential;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.workflow.approval.external.ExternalCallbackCodec;
import com.ouroboros.workflow.approval.external.ExternalCallbackEnvelope;
import com.ouroboros.workflow.approval.external.ExternalCallbackRequest;
import com.ouroboros.workflow.approval.external.runtime.ExternalRouteKeys;
import io.vavr.control.Try;

/** Approval translation delegates all transport verification to the provider protocol. */
public final class FeishuCallbackCodec implements ExternalCallbackCodec {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final ResolvedEnterpriseConnection connection;

  public FeishuCallbackCodec(ResolvedEnterpriseConnection connection, FeishuCredential credential) {
    this.connection = connection;
  }

  @Override
  public Try<List<ExternalCallbackEnvelope>> verifyAndParseCallback(ExternalCallbackRequest request) {
    return Try.of(() -> {
      if (!connection.connectionId().equals(request.connectionId())) { throw new ValidationException("回调连接不匹配"); }
      return new FeishuCallbackProtocol().verify(connection,
          new EnterpriseCallbackRequest("POST", request.headers(), Map.of(), request.body()))
          .events().stream().map(FeishuCallbackCodec::translate).toList();
    }).recoverWith(Exception.class, ex -> Try.failure(FeishuFailureMapper.callbackRejected(ex)));
  }

  static ExternalCallbackEnvelope translate(VerifiedEnterpriseEvent verified) {
    try {
      if (!"feishu".equals(verified.provider()) || !FeishuApprovalCallbackSubscriber.EVENT_TYPES.contains(verified.eventType())) {
        throw new ValidationException("非飞书审批事件");
      }
      String plain = verified.payload();
      JsonNode root = JSON.readTree(plain);
      String digest = ExternalRouteKeys.of("feishu-callback-payload", plain);
      JsonNode header = root.path("header");
      JsonNode event = root.path("event");
      String eventType = header.path("event_type").asText(root.path("type").asText(null));
      if (eventType == null || eventType.isBlank()) {
        throw new IllegalStateException("飞书回调缺少 event_type");
      }
      String instanceCode = event.path("instance_code").asText(null);
      if (instanceCode == null || instanceCode.isBlank()) {
        throw new IllegalStateException("飞书审批回调缺少 instance_code");
      }
      boolean taskEvent = eventType.toLowerCase().contains("task");
      String taskId = taskEvent ? event.path("task_id").asText(null) : null;
      if (taskEvent && (taskId == null || taskId.isBlank())) {
        throw new IllegalStateException("飞书任务回调缺少 task_id");
      }
      String status = event.path("status").asText(null);
      if (status == null || status.isBlank()) {
        throw new IllegalStateException("飞书审批回调缺少 status");
      }
      String eventId = header.path("event_id").asText(null);
      if (eventId == null || eventId.isBlank()) {
        eventId = ExternalRouteKeys.of("feishu-event", eventType, instanceCode,
            String.valueOf(taskId), status, digest);
      }
      ExternalCallbackEnvelope envelope = new ExternalCallbackEnvelope(
          eventId,
          FeishuEngineAdapterProvider.ADAPTER_ID,
          verified.connectionId(),
          instanceCode,
          taskId,
          null,
          normalizeEventTime(event.path("operate_time").asText(
              header.path("create_time").asText(null))),
          eventType,
          taskEvent ? normalizeTaskStatus(status) : normalizeInstanceStatus(status),
          digest);
      return envelope;
    } catch (Exception ex) { throw new com.ouroboros.exception.BusinessException("飞书审批回调解析失败", ex); }
  }

  private static String normalizeInstanceStatus(String status) {
    return switch (status) {
      case "PENDING", "OVERTIME_RECOVER" -> "RUNNING";
      case "APPROVED" -> "APPROVED";
      case "REJECTED" -> "REJECTED";
      case "CANCELED" -> "CANCELED";
      case "DELETED" -> "FEISHU_DELETED";
      case "REVERTED" -> "FEISHU_REVERTED";
      case "OVERTIME_CLOSE" -> "FEISHU_OVERTIME_CLOSE";
      default -> "FEISHU_STATUS_" + status;
    };
  }

  private static String normalizeTaskStatus(String status) {
    return switch (status) {
      case "PENDING", "OVERTIME_RECOVER" -> "PROCESSING";
      case "APPROVED" -> "APPROVED";
      case "REJECTED" -> "REJECTED";
      case "CANCELED" -> "CANCELED";
      case "TRANSFERRED" -> "FEISHU_TASK_TRANSFERRED";
      case "ROLLBACK" -> "FEISHU_TASK_ROLLBACK";
      case "DONE" -> "FEISHU_TASK_DONE";
      case "OVERTIME_CLOSE" -> "FEISHU_TASK_OVERTIME_CLOSE";
      default -> "FEISHU_TASK_STATUS_" + status;
    };
  }

  private static String normalizeEventTime(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    long epoch = Long.parseLong(value);
    return (value.length() >= 13 ? Instant.ofEpochMilli(epoch) : Instant.ofEpochSecond(epoch)).toString();
  }

}
