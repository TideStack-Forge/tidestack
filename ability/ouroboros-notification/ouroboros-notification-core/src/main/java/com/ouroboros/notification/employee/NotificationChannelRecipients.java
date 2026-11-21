package com.ouroboros.notification.employee;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A channel owns how employee addresses and their non-secret routing evidence are resolved. */
public interface NotificationChannelRecipients {
  String ROUTING_CONTEXT = "__recipientContext";
  Resolution resolve(Collection<String> employeeIds, Map<String, Object> channelOptions);

  /** Reject changed routing or relationship evidence before reusing a persisted delivery plan. */
  void verify(String employeeId, List<String> addresses, Map<String, Object> frozenOptions);

  record Resolution(Map<String, Object> channelOptions, Map<String, List<String>> addresses) {
    public Resolution {
      channelOptions = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(channelOptions));
      var copy = new LinkedHashMap<String, List<String>>();
      addresses.forEach((employee, values) -> copy.put(employee, List.copyOf(values)));
      addresses = Map.copyOf(copy);
    }
  }
}
