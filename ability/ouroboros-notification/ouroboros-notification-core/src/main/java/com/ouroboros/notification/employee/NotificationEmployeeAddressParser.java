package com.ouroboros.notification.employee;

import java.util.Collection;
import java.util.Map;

public interface NotificationEmployeeAddressParser {

  Map<String, Collection<String>> parse(Collection<String> employeeIds, Collection<String> channels);

  /** Resolve per employee so a durable plan preserves recipient ownership as well as channel options. */
  default Map<String, NotificationChannelRecipients.Resolution> resolve(Collection<String> employeeIds,
      Collection<String> channels, Map<String, Map<String, Object>> channelOptions) {
    var result = new java.util.LinkedHashMap<String, NotificationChannelRecipients.Resolution>();
    for (String channel : channels) {
      var addresses = new java.util.LinkedHashMap<String, java.util.List<String>>();
      for (String employee : employeeIds) {
        var parsed = parse(java.util.List.of(employee), java.util.List.of(channel));
        var values = parsed == null ? null : parsed.get(channel);
        addresses.put(employee, values == null ? java.util.List.of() : java.util.List.copyOf(values));
      }
      result.put(channel, new NotificationChannelRecipients.Resolution(channelOptions.getOrDefault(channel, Map.of()), addresses));
    }
    return Map.copyOf(result);
  }
}
