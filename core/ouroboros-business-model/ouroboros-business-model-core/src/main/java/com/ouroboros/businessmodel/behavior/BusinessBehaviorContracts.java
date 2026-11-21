package com.ouroboros.businessmodel.behavior;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.ouroboros.util.Json;

/** Fingerprints of the dynamic wire contracts consumed by generated clients. */
public final class BusinessBehaviorContracts {
  private BusinessBehaviorContracts() {
  }

  public static String actionFingerprint(ActionDefinition action) {
    return schemaFingerprint(Map.of("inputProtocol", "params-data-v1", "input", action.getInputSchema(),
        "output", action.getOutputSchema()));
  }

  public static String subscriptionFingerprint(EventSubscriptionDefinition subscription) {
    var contract = new java.util.LinkedHashMap<>(Json.toMap(Json.toJsonString(subscription)));
    // Missing and explicit true are the same effective contract, including historical owner fingerprints.
    if (!Boolean.FALSE.equals(subscription.getIsEnabled())) { contract.remove("isEnabled"); }
    return schemaFingerprint(contract);
  }

  public static String schemaFingerprint(Map<String, Object> schema) {
    byte[] serialized = Json.toJsonString(canonical(schema)).getBytes(StandardCharsets.UTF_8);
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serialized));
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("当前 Java 运行时不支持 SHA-256", failure);
    }
  }

  private static Object canonical(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> sorted = new TreeMap<>();
      map.forEach((key, nested) -> sorted.put(String.valueOf(key), canonical(nested)));
      return sorted;
    }
    if (value instanceof List<?> list) {
      return list.stream().map(BusinessBehaviorContracts::canonical).toList();
    }
    return value;
  }
}
