package com.ouroboros.notification.service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.notification.constant.NotificationStateConstant;
import com.ouroboros.util.Json;

/** One frozen source/rule plan. A rule revision or changed destination cannot create a second batch. */
public record NotificationDeliveryPlan(String sourceFactId, String ruleIdentity, String ruleRevision,
                                       String template, Map<String, Object> payload,
                                       Map<String, Object> channelOptions, String sendByEmployee,
                                       List<Destination> destinations, Map<String, Object> channelSchemas) {
  public NotificationDeliveryPlan {
    payload = snapshot(payload); channelOptions = snapshot(channelOptions); channelSchemas = snapshot(channelSchemas);
    destinations = List.copyOf(destinations);
  }

  public NotificationDeliveryPlan(String sourceFactId, String ruleIdentity, String ruleRevision, String template,
      Map<String, Object> payload, Map<String, Object> channelOptions, String sendByEmployee, List<Destination> destinations) {
    this(sourceFactId, ruleIdentity, ruleRevision, template, payload, channelOptions, sendByEmployee, destinations, Map.of());
  }

  public record Destination(String recipientId, String channel, List<String> addresses) {
    public Destination { addresses = List.copyOf(addresses).stream().distinct().sorted().toList(); }
    public Destination(String recipientId, String channel, String address) { this(recipientId, channel, List.of(address)); }
    public String address() { return addresses.size() == 1 ? addresses.getFirst() : null; }
  }

  public record Unit(String id, String recipientId, String channel, List<String> addresses,
                     NotificationStateConstant state, int attempt, String attemptToken,
                     LocalDateTime attemptDeadline, String diagnostic) {
    public Unit { addresses = List.copyOf(addresses); }
    public String address() { return addresses.size() == 1 ? addresses.getFirst() : null; }
  }

  public record Snapshot(String notificationId, String ruleRevision, String template,
                         Map<String, Object> payload, Map<String, Object> channelOptions,
                         String sendByEmployee, List<Unit> units, Map<String, Object> channelSchemas) {
    public Snapshot {
      payload = snapshot(payload); channelOptions = snapshot(channelOptions); channelSchemas = snapshot(channelSchemas); units = List.copyOf(units);
    }

    public Snapshot(String notificationId, String ruleRevision, String template, Map<String, Object> payload,
        Map<String, Object> channelOptions, String sendByEmployee, List<Unit> units) {
      this(notificationId, ruleRevision, template, payload, channelOptions, sendByEmployee, units, Map.of());
    }

    public boolean isComplete() {
      return !units.isEmpty() && units.stream().allMatch(unit ->
          unit.state() == NotificationStateConstant.SUCCESS || unit.state() == NotificationStateConstant.SKIPPED);
    }
  }

  public record Claim(String unitId, String token, int attempt) { }

  private static Map<String, Object> snapshot(Map<String, Object> source) {
    if (source == null || source.isEmpty()) { return Map.of(); }
    var json = Json.tryToMap(Json.tryToJsonString(source).getOrElseThrow(error -> new BusinessException("通知计划不是有效 JSON", error)))
        .getOrElseThrow(error -> new BusinessException("通知计划不是有效 JSON 对象", error));
    var result = new LinkedHashMap<String, Object>();
    json.forEach((key, value) -> result.put(key, freeze(value)));
    return Collections.unmodifiableMap(result);
  }

  private static Object freeze(Object value) {
    if (value instanceof Map<?, ?> map) {
      var result = new LinkedHashMap<String, Object>();
      map.forEach((key, nested) -> result.put(String.valueOf(key), freeze(nested)));
      return Collections.unmodifiableMap(result);
    }
    if (value instanceof List<?> list) { return list.stream().map(NotificationDeliveryPlan::freeze).toList(); }
    return value;
  }
}
