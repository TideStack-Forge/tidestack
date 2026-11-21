package com.ouroboros.dev.runtime.kubernetes.mapper;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeKind;

public final class KubernetesResourceNameFactory {
  private static final int DNS_LABEL_MAX_LENGTH = 63;

  private KubernetesResourceNameFactory() {
  }

  public static String deploymentName(RuntimeIdentityKey key) {
    return baseName(key);
  }

  public static String serviceName(RuntimeIdentityKey key) {
    return suffix(baseName(key), "svc");
  }

  public static String ingressName(RuntimeIdentityKey key) {
    return suffix(baseName(key), "ing");
  }

  public static Map<String, String> labels(RuntimeIdentityKey key) {
    Map<String, String> labels = new LinkedHashMap<String, String>();
    if (key.getWorkloadId() != null) labels.put("ouroboros.dev/runtime-workload", key.getWorkloadId());
    else labels.put("ouroboros.dev/runtime-app", key.getAppName());
    if (key.getProfile() != null) {
      labels.put("ouroboros.dev/runtime-profile", key.getProfile());
    }
    if (key.getRuntimeKind() != null) {
      labels.put("ouroboros.dev/runtime-kind", key.getRuntimeKind().name());
    }
    if (key.getBackendType() != null) {
      labels.put("ouroboros.dev/runtime-backend", key.getBackendType().name());
    }
    return labels;
  }

  private static String baseName(RuntimeIdentityKey key) {
    if (key.getRuntimeKind() == RuntimeKind.LANGUAGE_SERVICE) {
      return "devrt-lang-" + java.util.UUID.nameUUIDFromBytes(key.getWorkloadId().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().replace("-", "") + "-" + sanitize(key.getProfile());
    }
    return join(
        "devrt",
        runtimeKindToken(key.getRuntimeKind()),
        sanitize(key.getAppName()),
        includeProfile(key) ? sanitize(key.getProfile()) : null
    );
  }

  private static boolean includeProfile(RuntimeIdentityKey key) {
    return key != null
        && key.getRuntimeKind() != RuntimeKind.DEV_HELPER
        && key.getProfile() != null
        && !key.getProfile().trim().isEmpty();
  }

  private static String runtimeKindToken(RuntimeKind runtimeKind) {
    if (runtimeKind == null) {
      return "runtime";
    }
    switch (runtimeKind) {
      case TEST_APP:
        return "test-app";
      case DEV_HELPER:
        return "dev-helper";
      default:
        return sanitize(runtimeKind.name());
    }
  }

  private static String suffix(String value, String suffix) {
    return trimToDnsLabel(value + "-" + suffix);
  }

  private static String join(String... parts) {
    StringBuilder builder = new StringBuilder();
    for (String part : parts) {
      if (part == null || part.isEmpty()) {
        continue;
      }
      if (builder.length() > 0) {
        builder.append('-');
      }
      builder.append(part);
    }
    return trimToDnsLabel(builder.toString());
  }

  private static String sanitize(String value) {
    if (value == null) {
      return "x";
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    normalized = normalized.replaceAll("^-+", "").replaceAll("-+$", "").replaceAll("-{2,}", "-");
    return normalized.isEmpty() ? "x" : normalized;
  }

  private static String trimToDnsLabel(String value) {
    String normalized = value;
    if (normalized.length() > DNS_LABEL_MAX_LENGTH) {
      normalized = normalized.substring(0, DNS_LABEL_MAX_LENGTH);
    }
    return normalized.replaceAll("-+$", "");
  }
}
