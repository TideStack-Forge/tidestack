package com.ouroboros.app.runtime;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ouroboros.AppContext;
import com.ouroboros.util.Json;

public class DefaultResolvedRuntimeLockProvider implements ResolvedRuntimeLockProvider {
  private final AppContext appContext;
  private final String appName;
  private final String profile;
  private final String runtimeKind;

  public DefaultResolvedRuntimeLockProvider(AppContext appContext,
                                            String appName,
                                            String profile,
                                            String runtimeKind) {
    this.appContext = appContext;
    this.appName = appName;
    this.profile = profile;
    this.runtimeKind = runtimeKind;
  }

  @Override
  public Optional<Map<String, Object>> findLatestResolvedArtifactInput() {
    Path lockFile = latestLockFile();
    if (!Files.exists(lockFile)) {
      return Optional.empty();
    }
    try {
      String content = new String(Files.readAllBytes(lockFile), StandardCharsets.UTF_8);
      Map<String, Object> payload = Json.toBean(Map.class, content);
      if (payload == null) {
        throw new IllegalStateException("latest runtime lock payload is empty");
      }
      Map<String, Object> lock = lockBody(payload);
      if (!isUsableLatestSuccessLock(lock)) {
        return Optional.empty();
      }
      return Optional.of(toResolvedArtifactInput(lock));
    } catch (Exception error) {
      throw new RuntimeLockConsumptionException(
          String.format("读取运行时最新锁失败: app=%s, profile=%s, runtimeKind=%s", appName, profile, runtimeKind),
          error
      );
    }
  }

  private Path latestLockFile() {
    String profileStorageKey = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(String.valueOf(profile).getBytes(StandardCharsets.UTF_8));
    return Paths.get(appContext.getHomePath(), "runtime-lock", "latest", profileStorageKey, runtimeKind + ".json");
  }

  private Map<String, Object> toResolvedArtifactInput(Map<String, Object> lock) {
    Map<String, Object> input = new LinkedHashMap<String, Object>();
    input.put("status", asText(lock.get("status")));
    input.put("baseBomVersion", asText(lock.get("baseBomVersion")));
    input.put("resolvedAt", asText(lock.get("resolvedAt")));
    input.put("artifacts", artifactCoordinates(lock.get("topLevelArtifacts")));
    return input;
  }

  private boolean isUsableLatestSuccessLock(Map<String, Object> lock) {
    return !lock.isEmpty() && "READY".equals(asText(lock.get("status")));
  }

  private Map<String, Object> lockBody(Map<String, Object> payload) {
    if (payload == null) {
      return new LinkedHashMap<String, Object>();
    }
    Object lock = payload.get("lock");
    if (lock instanceof Map) {
      return (Map<String, Object>) lock;
    }
    return new LinkedHashMap<String, Object>();
  }

  private List<String> artifactCoordinates(Object value) {
    if (!(value instanceof List)) {
      return Collections.emptyList();
    }
    List<?> artifacts = (List<?>) value;
    List<String> coordinates = new ArrayList<String>();
    for (Object artifact : artifacts) {
      Map<String, Object> mapped = asMap(artifact);
      String groupId = asText(mapped.get("groupId"));
      String artifactId = asText(mapped.get("artifactId"));
      String version = asText(mapped.get("version"));
      if (groupId == null || artifactId == null || version == null) {
        continue;
      }
      coordinates.add(groupId + ":" + artifactId + ":" + version);
    }
    return coordinates;
  }

  private Map<String, Object> asMap(Object value) {
    if (value instanceof Map) {
      return (Map<String, Object>) value;
    }
    return new LinkedHashMap<String, Object>();
  }

  private String asText(Object value) {
    return value == null ? null : String.valueOf(value);
  }
}
