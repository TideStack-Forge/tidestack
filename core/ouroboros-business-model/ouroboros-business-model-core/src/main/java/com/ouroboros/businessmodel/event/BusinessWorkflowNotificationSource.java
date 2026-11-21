package com.ouroboros.businessmodel.event;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Authorized, receipt-backed notification context; internal approval facts never enter the public Event payload. */
public interface BusinessWorkflowNotificationSource {
  String LISTENER_NAME = "businessNotificationListener";

  enum Rule {
    TASK_CREATED("workflowTaskCreated", "notifyWorkflowTaskCreated", "taskCreatedTemplate"),
    TASK_TIMEOUT_REMIND("workflowTaskTimeoutReminded", "notifyWorkflowTaskTimeoutReminded", "taskTimeoutRemindTemplate"),
    INSTANCE_COMPLETED("workflowApproved", "notifyWorkflowApproved", "instanceCompletedTemplate"),
    INSTANCE_REJECTED("workflowRejected", "notifyWorkflowRejected", "instanceRejectedTemplate");

    private final String eventName;
    private final String subscriptionName;
    private final String templateProperty;

    Rule(String eventName, String subscriptionName, String templateProperty) {
      this.eventName = eventName; this.subscriptionName = subscriptionName; this.templateProperty = templateProperty;
    }

    public String eventName() { return eventName; }
    public String subscriptionName() { return subscriptionName; }
    public String templateProperty() { return templateProperty; }
    public Map<String, Object> listenerConfig() { return Map.of("workflow", true, "templateProperty", templateProperty); }

    public static Optional<Rule> forEvent(String eventName) {
      return java.util.Arrays.stream(values()).filter(rule -> rule.eventName.equals(eventName)).findFirst();
    }
  }

  Optional<Notice> read(BusinessEventOccurrence<?> occurrence, BusinessEventDeliveryContext delivery);

  Eligibility eligibility(BusinessEventOccurrence<?> occurrence, BusinessEventDeliveryContext delivery, String employeeId);

  record Notice(String template, List<String> employeeIds, Map<String, Object> payload) {
    public Notice {
      employeeIds = List.copyOf(employeeIds);
      // Approval template values include LocalDateTime and explicit nulls, as in the original notification path.
      payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
  }

  record Eligibility(boolean applicable, String diagnostic) { }
}
