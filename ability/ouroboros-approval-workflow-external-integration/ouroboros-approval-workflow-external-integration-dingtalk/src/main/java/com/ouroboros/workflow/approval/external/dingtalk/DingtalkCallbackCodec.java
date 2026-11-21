package com.ouroboros.workflow.approval.external.dingtalk;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.enterprise.callback.EnterpriseCallbackRequest;
import com.ouroboros.enterprise.callback.VerifiedEnterpriseEvent;
import com.ouroboros.enterprise.dingtalk.DingtalkCallbackProtocol;
import com.ouroboros.enterprise.dingtalk.DingtalkCredential;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.workflow.approval.external.ExternalCallbackCodec;
import com.ouroboros.workflow.approval.external.ExternalCallbackEnvelope;
import com.ouroboros.workflow.approval.external.ExternalCallbackRequest;
import com.ouroboros.workflow.approval.external.runtime.ExternalRouteKeys;
import io.vavr.control.Try;

/** Approval translation; provider verification belongs to the neutral connector protocol. */
public final class DingtalkCallbackCodec implements ExternalCallbackCodec {
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DingtalkCallbackCodec.class);
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final DateTimeFormatter DINGTALK_TIME_FORMAT =
      DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
  private static final String DEFAULT_PROVIDER_TIME_ZONE = "Asia/Shanghai";
  private final ResolvedEnterpriseConnection connection;

  public DingtalkCallbackCodec(ResolvedEnterpriseConnection connection, DingtalkCredential credential) {
    this.connection = connection;
  }

  @Override
  public Try<List<ExternalCallbackEnvelope>> verifyAndParseCallback(ExternalCallbackRequest request) {
    return Try.of(() -> {
      if (!connection.connectionId().equals(request.connectionId())) { throw new ValidationException("回调连接不匹配"); }
      return new DingtalkCallbackProtocol().verify(connection,
          new EnterpriseCallbackRequest("POST", Map.of(), request.headers(), request.body()))
          .events().stream().map(DingtalkCallbackCodec::translate).toList();
    }).recoverWith(Exception.class, ex -> Try.failure(DingtalkFailureMapper.callbackRejected("回调", ex)));
  }

  static ExternalCallbackEnvelope translate(VerifiedEnterpriseEvent event) {
    try {
      if (!"dingtalk".equals(event.provider()) || !DingtalkApprovalCallbackSubscriber.EVENT_TYPES.contains(event.eventType())) {
        throw new ValidationException("非钉钉审批事件");
      }
      JsonNode payload = JSON.readTree(event.payload());
      if (payload.path("processInstanceId").asText("").isBlank()) { throw new ValidationException("审批回调缺少实例 ID"); }
      return toEnvelope(payload, event.connectionId());
    } catch (Exception ex) { throw new com.ouroboros.exception.BusinessException("钉钉审批回调解析失败", ex); }
  }

  private static ExternalCallbackEnvelope toEnvelope(JsonNode event, String connectionId) {
    String eventType = event.path("EventType").asText("bpms_instance_change");
    String type = event.path("type").asText(null);
    String result = event.path("result").asText(null);
    boolean taskEvent = "bpms_task_change".equals(eventType);
    String externalTaskId = taskEvent ? event.path("taskId").asText(null) : null;
    String externalEventTime = eventTime(event, type);
    String payloadDigest = ExternalRouteKeys.of("dingtalk-callback", event.toString());
    String eventId = eventId(
        event.path("processInstanceId").asText(), eventType, externalTaskId, type, result, payloadDigest);
    String status = taskEvent
        ? taskStatus(type, result)
        : instanceStatus(type, result);
    return new ExternalCallbackEnvelope(
        eventId,
        DingtalkEngineAdapterProvider.ADAPTER_ID,
        connectionId,
        event.path("processInstanceId").asText(),
        externalTaskId,
        null,
        externalEventTime,
        eventType + "/" + type,
        status,
        payloadDigest);
  }

  private static String taskStatus(String type, String result) {
    if ("start".equals(type)) {
      return "CREATED";
    }
    if ("cancel".equals(type)) {
      return "CANCELED";
    }
    if ("comment".equals(type)) {
      return "PROCESSING";
    }
    if (!"finish".equals(type)) {
      return null;
    }
    return switch (result == null ? "" : result) {
      case "agree" -> "APPROVED";
      case "refuse" -> "REJECTED";
      case "redirect", "audit" -> "PROCESSING";
      default -> null;
    };
  }

  private static String instanceStatus(String type, String result) {
    if ("start".equals(type)) {
      return "RUNNING";
    }
    if ("terminate".equals(type)) {
      return "CANCELED";
    }
    if (!"finish".equals(type)) {
      return null;
    }
    return switch (result == null ? "" : result) {
      case "agree" -> "APPROVED";
      case "refuse" -> "REJECTED";
      default -> null;
    };
  }

  private static String eventId(
      String processInstanceId,
      String eventType,
      String externalTaskId,
      String type,
      String result,
      String payloadDigest) {
    return processInstanceId + ":" + eventType + ":"
        + (externalTaskId == null || externalTaskId.isBlank() ? "_" : externalTaskId)
        + ":" + type + ":" + result + ":" + payloadDigest;
  }

  private static String eventTime(JsonNode event, String type) {
    if ("finish".equals(type) || "terminate".equals(type)) {
      String finishTime = event.path("finishTime").asText(null);
      if (finishTime != null && !finishTime.isBlank()) {
        return normalizeEventTime(finishTime);
      }
    }
    String createTime = event.path("createTime").asText(null);
    return createTime == null || createTime.isBlank() ? null : normalizeEventTime(createTime);
  }

  /** Convert provider-local timestamps to an unambiguous UTC instant before runtime ordering. */
  private static String normalizeEventTime(String value) {
    String trimmed = value.trim();
    try {
      return OffsetDateTime.parse(trimmed).toInstant().toString();
    } catch (DateTimeParseException ex) {
      // DingTalk's documented callback payload uses a local, offset-less timestamp.
      logger.debug("钉钉事件时间不是带偏移量的 ISO 格式，回退解析: value={}", trimmed);
    }
    try {
      return Instant.parse(trimmed).toString();
    } catch (DateTimeParseException ex) {
      // Fall through to the provider-local format.
      logger.debug("钉钉事件时间不是 UTC Instant，回退到提供方本地格式: value={}", trimmed);
    }
    try {
      ZoneId zone = ZoneId.of(DEFAULT_PROVIDER_TIME_ZONE);
      return LocalDateTime.parse(trimmed, DINGTALK_TIME_FORMAT).atZone(zone).toInstant().toString();
    } catch (DateTimeParseException | java.time.zone.ZoneRulesException ex) {
      throw new IllegalStateException("钉钉回调时间格式非法: " + value, ex);
    }
  }

}
