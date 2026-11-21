package com.ouroboros.notification;

import java.util.List;
import java.util.Map;

/** Stable event/rule identity plus the initial employee notification request. Retries use its persisted plan. */
public record NotificationDispatchRequest(String sourceFactId, String ruleIdentity, String ruleRevision,
                                          List<String> employeeIds, String template,
                                          Map<String, Object> payload, DispatchOptions options) {
  public NotificationDispatchRequest {
    if (sourceFactId == null || sourceFactId.isBlank() || ruleIdentity == null || ruleIdentity.isBlank()
        || ruleRevision == null || ruleRevision.isBlank()) { throw new IllegalArgumentException("notification identity is required"); }
    employeeIds = List.copyOf(employeeIds);
  }
}
