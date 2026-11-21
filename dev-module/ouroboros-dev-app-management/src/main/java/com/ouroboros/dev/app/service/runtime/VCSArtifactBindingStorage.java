package com.ouroboros.dev.app.service.runtime;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.AppArtifactBinding;
import com.ouroboros.dev.runtime.model.ProfileArtifactOverlay;
import com.ouroboros.dev.runtime.model.RepositoryRef;
import com.ouroboros.dev.runtime.model.ResolvedArtifactSourceType;
import com.ouroboros.dev.runtime.model.ResolvedRuntimeArtifact;
import com.ouroboros.dev.runtime.model.ResolvedRuntimeLock;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeLockStatus;
import com.ouroboros.dev.runtime.service.ArtifactBindingService;
import com.ouroboros.dev.runtime.service.PersistentRuntimeLockStore;
import com.ouroboros.dev.runtime.service.RuntimeLockKey;
import com.ouroboros.dev.runtime.service.RuntimeLockRecord;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;

public class VCSArtifactBindingStorage implements ArtifactBindingService.StorageGateway, PersistentRuntimeLockStore.PersistenceGateway {
  private static final String EXTEND_JAR_DIR = "extend";
  private static final String RUNTIME_LOCK_DIR = "runtime-lock";

  private final VCSManagerCenter vcsManagerCenter;

  public VCSArtifactBindingStorage(VCSManagerCenter vcsManagerCenter) {
    this.vcsManagerCenter = vcsManagerCenter;
  }

  @Override
  public Try<Void> saveRepositoryRefs(String appId, List<RepositoryRef> repositoryRefs) {
    return writeJsonDocument(appId,
        Paths.get(RUNTIME_LOCK_DIR, "repository-refs.json").toString(),
        repositoryRefs,
        "更新运行时锁仓库引用");
  }

  @Override
  public Try<Void> saveAppArtifactBindings(String appId, List<AppArtifactBinding> appArtifactBindings) {
    return writeJsonDocument(appId,
        Paths.get(RUNTIME_LOCK_DIR, "app-artifact-bindings.json").toString(),
        appArtifactBindings,
        "更新运行时锁应用制品绑定");
  }

  @Override
  public Try<Void> saveProfileArtifactOverlays(String appId, String profileId, List<ProfileArtifactOverlay> overlays) {
    return writeJsonDocument(appId,
        Paths.get(RUNTIME_LOCK_DIR, "profiles", profileStorageKey(profileId), "artifact-overlays.json").toString(),
        overlays,
        String.format("更新运行时锁Profile覆盖(%s)", profileId));
  }

  @Override
  public Try<Void> markResolveRequested(String appId, String profileId, RuntimeKind runtimeKind) {
    String requestId = buildRequestId();
    Map<String, Object> payload = new LinkedHashMap<String, Object>();
    payload.put("requestId", requestId);
    payload.put("requestedAt", Instant.now().toString());
    payload.put("appId", appId);
    payload.put("profileId", profileId);
    payload.put("runtimeKind", runtimeKind == null ? null : runtimeKind.name());
    return createJsonDocument(appId,
        Paths.get(RUNTIME_LOCK_DIR,
            "requests",
            profileStorageKey(profileId),
            runtimeKind.name(),
            requestId + ".json").toString(),
        payload,
        String.format("触发运行时锁重新解析(%s/%s#%s)", profileId, runtimeKind.name(), requestId));
  }

  @Override
  public Optional<RuntimeLockRecord> findLatestSuccessful(RuntimeLockKey slot) {
    String path = latestSlotPath(slot);
    VCSManager vcsManager = getVcsManager(slot.getAppId());
    if (!vcsManager.getFile(path).exists()) {
      return Optional.empty();
    }
    String content = vcsManager.getFile(path).getContent();
    Map<String, Object> payload = Json.toBean(Map.class, content);
    return Optional.of(documentToRecord(payload));
  }

  @Override
  public void saveLatestSuccessful(RuntimeLockRecord record) {
    writeJsonDocument(
        record.getSlot().getAppId(),
        latestSlotPath(record.getSlot()),
        recordToDocument(record),
        String.format("更新运行时锁最新快照(%s/%s)", record.getSlot().getProfileId(), record.getSlot().getRuntimeKind().name())
    ).get();
  }

  public Try<Void> addUploadedArtifact(String appId, String jarName, InputStream jarContent) {
    return Try.of(() -> normalizeJarWrite(appId, jarName, jarContent))
        .flatMap(ignore -> {
          String path = Paths.get(EXTEND_JAR_DIR, jarName).toString();
          VCSManager vcsManager = getVcsManager(appId);
          if (vcsManager.getFile(path).exists()) {
            return Try.failure(new BusinessException("jar文件已存在"));
          }
          return vcsManager.createFile(path)
              .content(jarContent)
              .unlockWhenCommit()
              .commit(String.format("添加扩展jar(%s)", jarName))
              .map(ignoreCommit -> (Void) null);
        });
  }

  public Try<Void> updateUploadedArtifact(String appId, String jarName, InputStream jarContent) {
    return Try.of(() -> normalizeJarWrite(appId, jarName, jarContent))
        .flatMap(ignore -> {
          String path = Paths.get(EXTEND_JAR_DIR, jarName).toString();
          VCSManager vcsManager = getVcsManager(appId);
          if (!vcsManager.getFile(path).exists()) {
            return Try.failure(new BusinessException("jar文件不存在"));
          }
          return vcsManager.updateFile(path)
              .content(jarContent)
              .unlockWhenCommit()
              .commit(String.format("更新扩展jar(%s)", jarName))
              .map(ignoreCommit -> (Void) null);
        });
  }

  public Try<Void> deleteUploadedArtifact(String appId, String jarName) {
    return Try.of(() -> {
      normalizeAppId(appId);
      requireText(jarName, "jarName 不能为空");
      return jarName;
    }).flatMap(ignore -> {
      String path = Paths.get(EXTEND_JAR_DIR, jarName).toString();
      VCSManager vcsManager = getVcsManager(appId);
      if (!vcsManager.getFile(path).exists()) {
        return Try.failure(new BusinessException("jar文件不存在"));
      }
      return vcsManager.removeFile(path)
          .unlockWhenCommit()
          .commit(String.format("删除扩展jar(%s)", jarName))
          .map(ignoreCommit -> (Void) null);
    });
  }

  String profileStorageKey(String profileId) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(requireText(profileId, "profileId 不能为空").getBytes(StandardCharsets.UTF_8));
  }

  private String buildRequestId() {
    return Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString();
  }

  private Try<Void> writeJsonDocument(String appId, String path, Object payload, String commitMessage) {
    String content = Json.toPrettyJsonString(payload);
    VCSManager vcsManager = getVcsManager(appId);
    if (vcsManager.getFile(path).exists()) {
      return vcsManager.updateFile(path)
          .content(content)
          .unlockWhenCommit()
          .commit(commitMessage)
          .map(ignore -> (Void) null);
    }
    return vcsManager.createFile(path)
        .content(content)
        .unlockWhenCommit()
        .commit(commitMessage)
        .map(ignore -> (Void) null);
  }

  private Try<Void> createJsonDocument(String appId, String path, Object payload, String commitMessage) {
    return getVcsManager(appId).createFile(path)
        .content(Json.toPrettyJsonString(payload))
        .unlockWhenCommit()
        .commit(commitMessage)
        .map(ignore -> (Void) null);
  }

  private String latestSlotPath(RuntimeLockKey slot) {
    RuntimeKind runtimeKind = slot.getRuntimeKind();
    if (runtimeKind == null) {
      throw new IllegalArgumentException("runtimeKind 不能为空");
    }
    return Paths.get(RUNTIME_LOCK_DIR, "latest", profileStorageKey(slot.getProfileId()), runtimeKind.name() + ".json").toString();
  }

  private Map<String, Object> recordToDocument(RuntimeLockRecord record) {
    Map<String, Object> slot = new LinkedHashMap<String, Object>();
    slot.put("appId", record.getSlot().getAppId());
    slot.put("profileId", record.getSlot().getProfileId());
    slot.put("runtimeKind", record.getSlot().getRuntimeKind() == null ? null : record.getSlot().getRuntimeKind().name());

    Map<String, Object> payload = new LinkedHashMap<String, Object>();
    payload.put("slot", slot);
    payload.put("savedAt", record.getSavedAt() == null ? null : record.getSavedAt().toString());
    payload.put("lock", lockToDocument(record.getLock()));
    return payload;
  }

  private RuntimeLockRecord documentToRecord(Map<String, Object> payload) {
    Map<String, Object> slot = asMap(payload.get("slot"));
    RuntimeLockKey key = RuntimeLockKey.of(
        asText(slot.get("appId")),
        asText(slot.get("profileId")),
        RuntimeKind.valueOf(asText(slot.get("runtimeKind")))
    );
    ResolvedRuntimeLock lock = documentToLock(asMap(payload.get("lock")));
    Instant savedAt = parseInstant(payload.get("savedAt"));
    return RuntimeLockRecord.of(key, lock, savedAt);
  }

  private Map<String, Object> lockToDocument(ResolvedRuntimeLock lock) {
    if (lock == null) {
      return Collections.emptyMap();
    }
    Map<String, Object> payload = new LinkedHashMap<String, Object>();
    payload.put("appId", lock.getAppId());
    payload.put("profileId", lock.getProfileId());
    payload.put("runtimeKind", lock.getRuntimeKind() == null ? null : lock.getRuntimeKind().name());
    payload.put("status", lock.getStatus() == null ? null : lock.getStatus().name());
    payload.put("resolvedAt", lock.getResolvedAt() == null ? null : lock.getResolvedAt().toString());
    payload.put("baseBomVersion", lock.getBaseBomVersion());
    payload.put("topLevelArtifacts", lock.getTopLevelArtifacts());
    payload.put("resolvedArtifacts", lock.getResolvedArtifacts());
    payload.put("suppressedArtifacts", lock.getSuppressedArtifacts());
    payload.put("diagnostics", lock.getDiagnostics());
    return payload;
  }

  private ResolvedRuntimeLock documentToLock(Map<String, Object> lock) {
    RuntimeKind runtimeKind = lock.get("runtimeKind") == null ? null : RuntimeKind.valueOf(asText(lock.get("runtimeKind")));
    RuntimeLockStatus status = lock.get("status") == null ? null : RuntimeLockStatus.valueOf(asText(lock.get("status")));
    Instant resolvedAt = parseInstant(lock.get("resolvedAt"));
    return ResolvedRuntimeLock.builder()
        .appId(asText(lock.get("appId")))
        .profileId(asText(lock.get("profileId")))
        .runtimeKind(runtimeKind)
        .status(status)
        .resolvedAt(resolvedAt)
        .baseBomVersion(asText(lock.get("baseBomVersion")))
        .topLevelArtifacts(parseTopLevelArtifacts(lock.get("topLevelArtifacts")))
        .resolvedArtifacts(parseResolvedArtifacts(lock.get("resolvedArtifacts")))
        .suppressedArtifacts(parseResolvedArtifacts(lock.get("suppressedArtifacts")))
        .diagnostics(parseDiagnostics(lock.get("diagnostics")))
        .build();
  }

  private List<AppArtifactBinding> parseTopLevelArtifacts(Object rawArtifacts) {
    if (!(rawArtifacts instanceof List)) {
      return Collections.emptyList();
    }
    List<?> values = (List<?>) rawArtifacts;
    java.util.ArrayList<AppArtifactBinding> result = new java.util.ArrayList<AppArtifactBinding>();
    for (Object value : values) {
      Map<String, Object> item = asMap(value);
      result.add(AppArtifactBinding.builder()
          .appId(asText(item.get("appId")))
          .groupId(asText(item.get("groupId")))
          .artifactId(asText(item.get("artifactId")))
          .version(asText(item.get("version")))
          .packaging(asText(item.get("packaging")))
          .classifier(asText(item.get("classifier")))
          .enabled(Boolean.TRUE.equals(item.get("enabled")))
          .usage(asText(item.get("usage")))
          .repositoryRef(parseRepositoryRef(item.get("repositoryRef")))
          .build());
    }
    return result;
  }

  private List<ResolvedRuntimeArtifact> parseResolvedArtifacts(Object rawArtifacts) {
    if (!(rawArtifacts instanceof List)) {
      return Collections.emptyList();
    }
    List<?> values = (List<?>) rawArtifacts;
    java.util.ArrayList<ResolvedRuntimeArtifact> result = new java.util.ArrayList<ResolvedRuntimeArtifact>();
    for (Object value : values) {
      Map<String, Object> item = asMap(value);
      ResolvedArtifactSourceType sourceType = item.get("sourceType") == null
          ? null
          : ResolvedArtifactSourceType.valueOf(asText(item.get("sourceType")));
      result.add(ResolvedRuntimeArtifact.builder()
          .groupId(asText(item.get("groupId")))
          .artifactId(asText(item.get("artifactId")))
          .version(asText(item.get("version")))
          .packaging(asText(item.get("packaging")))
          .classifier(asText(item.get("classifier")))
          .checksum(asText(item.get("checksum")))
          .repositoryRef(parseRepositoryRef(item.get("repositoryRef")))
          .effectiveScope(asText(item.get("effectiveScope")))
          .sourceType(sourceType)
          .build());
    }
    return result;
  }

  private List<String> parseDiagnostics(Object rawDiagnostics) {
    if (!(rawDiagnostics instanceof List)) {
      return Collections.emptyList();
    }
    List<?> values = (List<?>) rawDiagnostics;
    java.util.ArrayList<String> result = new java.util.ArrayList<String>();
    for (Object value : values) {
      result.add(asText(value));
    }
    return result;
  }

  private RepositoryRef parseRepositoryRef(Object rawRef) {
    if (!(rawRef instanceof Map)) {
      return null;
    }
    Map<String, Object> item = asMap(rawRef);
    return RepositoryRef.builder()
        .repositoryId(asText(item.get("repositoryId")))
        .repositoryType(item.get("repositoryType") == null ? null : com.ouroboros.dev.runtime.model.RepositoryType.valueOf(asText(item.get("repositoryType"))))
        .repositoryUrl(asText(item.get("repositoryUrl")))
        .credentialRef(asText(item.get("credentialRef")))
        .defaultEnabled(Boolean.TRUE.equals(item.get("defaultEnabled")))
        .build();
  }

  private Map<String, Object> asMap(Object value) {
    if (!(value instanceof Map)) {
      return new LinkedHashMap<String, Object>();
    }
    return (Map<String, Object>) value;
  }

  private String asText(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private Instant parseInstant(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return Instant.parse(String.valueOf(value));
    } catch (DateTimeParseException error) {
      return null;
    }
  }

  private VCSManager getVcsManager(String appId) {
    return vcsManagerCenter.getVcsManager(normalizeAppId(appId));
  }

  private String normalizeJarWrite(String appId, String jarName, InputStream jarContent) {
    normalizeAppId(appId);
    requireText(jarName, "jarName 不能为空");
    if (jarContent == null) {
      throw new IllegalArgumentException("jarContent 不能为空");
    }
    return jarName;
  }

  private String normalizeAppId(String appId) {
    return requireText(appId, "appId 不能为空");
  }

  private String requireText(String value, String message) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(message);
    }
    return value.trim();
  }
}
