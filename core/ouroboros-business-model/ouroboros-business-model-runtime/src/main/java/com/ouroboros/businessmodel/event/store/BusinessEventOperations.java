package com.ouroboros.businessmodel.event.store;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ouroboros.businessmodel.publication.BusinessModelPublication;
import com.ouroboros.businessmodel.security.BusinessBehaviorSecurity;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.exception.BusinessException;

/** Bounded diagnostics without payload or lease tokens, and explicitly authorized redrive. */
public class BusinessEventOperations {
  private final BusinessEventOutbox outbox;

  public BusinessEventOperations(BusinessEventOutbox outbox) {
    this.outbox = outbox;
  }

  public record Fact(String eventId, String modelFullName, String eventName, String modelRevision, Object occurredAt) {
  }

  public record Delivery(String deliveryId, String consumerModelFullName, String subscriptionName,
                         String status, int attempt, int retryCount, String diagnosticCode, Object nextAttemptAt,
                         String matchedRevision, String attemptRevision, String targetRevision,
                         String implementationRef, String implementationVersion, String executionMode, String user) {
    public Delivery(String deliveryId, String consumerModelFullName, String subscriptionName, String status,
        int attempt, int retryCount, String diagnosticCode, Object nextAttemptAt, String matchedRevision,
        String attemptRevision, String targetRevision, String implementationRef, String implementationVersion) {
      this(deliveryId, consumerModelFullName, subscriptionName, status, attempt, retryCount, diagnosticCode,
          nextAttemptAt, matchedRevision, attemptRevision, targetRevision, implementationRef, implementationVersion, null, null);
    }
  }

  public record Diagnostics(Optional<Fact> fact, List<Delivery> deliveries, boolean canRedrive) {
  }

  public Diagnostics inspect(String eventId) {
    requireAuthority(com.ouroboros.businessmodel.event.BusinessEventAuthorities.INSPECT_DELIVERIES);
    return BusinessModelPublication.withPublishedContext(() -> {
      var facts = DataModelCenter.getDataModel(DataModelBusinessEventOccurrenceRecorder.MODEL_FULL_NAME)
          .orElseThrow(() -> new BusinessException("业务 Event 事实模型不可用"));
      var rows = facts.query().select(List.of("id", "modelFullName", "eventName", "modelRevision", "occurredAt"))
          .where(Map.of("id", eventId)).clause("LIMIT", 1).execute()
          .getOrElseThrow(error -> new BusinessException("业务 Event 诊断读取失败", error));
      var fact = rows.stream().findFirst().map(row -> new Fact(text(row, "id"), text(row, "modelFullName"),
          text(row, "eventName"), text(row, "modelRevision"), row.get("occurredAt")));
      var model = DataModelCenter.getDataModel(BusinessEventOutbox.MODEL_FULL_NAME)
          .orElseThrow(() -> new BusinessException("业务 Event 投递模型不可用"));
      var deliveries = model.query().select(List.of("id", "consumerModelFullName", "subscriptionName", "status", "attempt",
          "retryCount", "lastError", "nextAttemptAt", "matchedRevision", "attemptRevision", "targetRevision",
          "implementationRef", "implementationVersion", "executionMode", "user")).where(Map.of("eventId", eventId))
          .clause("ORDER", Map.of("id", "asc")).clause("LIMIT", 100).execute()
          .getOrElseThrow(error -> new BusinessException("业务 Event 交付诊断读取失败", error)).stream()
          .map(row -> new Delivery(text(row, "id"), text(row, "consumerModelFullName"), text(row, "subscriptionName"),
              text(row, "status"), number(row, "attempt"), number(row, "retryCount"), text(row, "lastError"), row.get("nextAttemptAt"),
              text(row, "matchedRevision"), text(row, "attemptRevision"), text(row, "targetRevision"),
              text(row, "implementationRef"), text(row, "implementationVersion"), text(row, "executionMode"), text(row, "user")))
          .toList();
      return new Diagnostics(fact, deliveries, BusinessBehaviorSecurity.hasAuthority(
          com.ouroboros.businessmodel.event.BusinessEventAuthorities.REDRIVE_DELIVERIES));
    });
  }

  public boolean redrive(String deliveryId) {
    requireAuthority(com.ouroboros.businessmodel.event.BusinessEventAuthorities.REDRIVE_DELIVERIES);
    return outbox.redrive(deliveryId);
  }

  private static void requireAuthority(String authority) {
    if (!BusinessBehaviorSecurity.hasAuthority(authority)) {
      throw new BusinessException("业务 Event 操作未授权");
    }
  }

  private static String text(Map<String, Object> row, String name) {
    return row.get(name) == null ? null : String.valueOf(row.get(name));
  }

  private static int number(Map<String, Object> row, String name) {
    return row.get(name) instanceof Number number ? number.intValue() : 0;
  }
}
