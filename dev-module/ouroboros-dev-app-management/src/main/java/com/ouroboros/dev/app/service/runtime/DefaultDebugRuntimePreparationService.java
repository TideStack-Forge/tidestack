package com.ouroboros.dev.app.service.runtime;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.app.service.impl.AppConfigService;
import com.ouroboros.dev.runtime.model.OpenDebugSessionCommand;
import com.ouroboros.dev.runtime.model.ResolvedArtifactSourceType;
import com.ouroboros.dev.runtime.model.ResolvedRuntimeArtifact;
import com.ouroboros.dev.runtime.model.ResolvedRuntimeLock;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.TemporaryDebugArtifact;
import com.ouroboros.dev.runtime.model.DebugOverlay;
import com.ouroboros.dev.runtime.model.TemporaryResolvedRuntimeLock;
import com.ouroboros.dev.runtime.service.DevRuntimeService;
import com.ouroboros.dev.runtime.service.RuntimeLockKey;
import com.ouroboros.dev.runtime.service.RuntimeLockRecord;
import com.ouroboros.dev.runtime.service.RuntimeLockStore;
import com.ouroboros.dev.runtime.service.RuntimeStateStore;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;

@Service
public class DefaultDebugRuntimePreparationService implements DebugRuntimePreparationService {
  private final RuntimeLockStore runtimeLockStore;
  private final RuntimeStateStore runtimeStateStore;
  private final DevelopWorkspaceContext developWorkspaceContext;
  private final DevRuntimeService devRuntimeService;
  private final AppConfigService appConfigService;
  private final Function<String, Instant> instantParser;
  private final ProfileStorageKeyEncoder profileStorageKeyEncoder;

  @Autowired
  public DefaultDebugRuntimePreparationService(RuntimeLockStore runtimeLockStore,
                                               RuntimeStateStore runtimeStateStore,
                                               DevelopWorkspaceContext developWorkspaceContext,
                                               DevRuntimeService devRuntimeService,
                                               AppConfigService appConfigService) {
    this(runtimeLockStore, runtimeStateStore, developWorkspaceContext, devRuntimeService, appConfigService, Instant::parse, new ProfileStorageKeyEncoder());
  }

  DefaultDebugRuntimePreparationService(RuntimeLockStore runtimeLockStore,
                                        RuntimeStateStore runtimeStateStore,
                                        DevelopWorkspaceContext developWorkspaceContext,
                                        DevRuntimeService devRuntimeService,
                                        AppConfigService appConfigService,
                                        Function<String, Instant> instantParser,
                                        ProfileStorageKeyEncoder profileStorageKeyEncoder) {
    this.runtimeLockStore = runtimeLockStore;
    this.runtimeStateStore = runtimeStateStore;
    this.developWorkspaceContext = developWorkspaceContext;
    this.devRuntimeService = devRuntimeService;
    this.appConfigService = appConfigService;
    this.instantParser = instantParser;
    this.profileStorageKeyEncoder = profileStorageKeyEncoder;
  }

  @Override
  public Try<OpenDebugSessionCommand> prepare(String sessionId, OpenDebugSessionCommand command, String uploadBatchId) {
    return Try.of(() -> doPrepare(sessionId, command, uploadBatchId));
  }

  private OpenDebugSessionCommand doPrepare(String sessionId, OpenDebugSessionCommand command, String uploadBatchId) throws Exception {
    if (StringUtils.isBlank(sessionId)) {
      throw new BusinessException("sessionId 不能为空");
    }
    if (command == null) {
      throw new BusinessException("debug command 不能为空");
    }
    if (StringUtils.isBlank(uploadBatchId)) {
      throw new BusinessException("uploadBatchId 不能为空");
    }
    ResolvedRuntimeLock stableLock = requireStableLatestLock(command);
    DebugOverlay debugOverlay = buildDebugOverlay(sessionId, command.getProfile(), uploadBatchId);
    TemporaryResolvedRuntimeLock temporaryLock = buildTemporaryLock(sessionId, uploadBatchId, stableLock, debugOverlay);
    persistPreparationArtifacts(command.getProfile(), sessionId, debugOverlay, temporaryLock);
    RuntimeSpec runtimeSpec = requireRuntimeSpec(command);
    appConfigService.buildRunWorkspace(command.getAppName(), command.getProfile()).get();
    devRuntimeService.reloadWorkspace(runtimeSpec).get();
    return enrichCommand(command, sessionId, uploadBatchId);
  }

  private RuntimeSpec requireRuntimeSpec(OpenDebugSessionCommand command) {
    RuntimeIdentityKey runtimeKey = command.getRuntimeKey();
    Optional<RuntimeSpec> runtimeSpec = runtimeStateStore.findSpec(runtimeKey);
    if (!runtimeSpec.isPresent()) {
      throw new BusinessException("测试容器运行时规格不存在: " + command.getAppName() + "/" + command.getProfile());
    }
    return runtimeSpec.get();
  }

  private ResolvedRuntimeLock requireStableLatestLock(OpenDebugSessionCommand command) {
    RuntimeLockKey slot = RuntimeLockKey.of(command.getAppName(), command.getProfile(), command.getRuntimeKind());
    Optional<RuntimeLockRecord> latest = runtimeLockStore.findLatestSuccessful(slot);
    if (!latest.isPresent() || latest.get().getLock() == null) {
      throw new BusinessException("稳定运行时锁不存在: " + command.getAppName() + "/" + command.getProfile());
    }
    return latest.get().getLock();
  }

  private DebugOverlay buildDebugOverlay(String sessionId, String profileId, String uploadBatchId) throws Exception {
    Path manifestPath = uploadManifestPath(profileId, uploadBatchId);
    if (!Files.exists(manifestPath)) {
      throw new BusinessException("调试上传批次不存在: " + uploadBatchId);
    }
    Map<String, Object> payload = Json.toMap(new String(Files.readAllBytes(manifestPath), StandardCharsets.UTF_8));
    List<TemporaryDebugArtifact> artifacts = toArtifacts(payload.get("artifacts"));
    return DebugOverlay.builder()
        .sessionId(sessionId)
        .uploadBatchId(uploadBatchId)
        .profileId(profileId)
        .artifacts(artifacts)
        .createdAt(Instant.now())
        .build();
  }

  private List<TemporaryDebugArtifact> toArtifacts(Object artifactsValue) {
    List<TemporaryDebugArtifact> artifacts = new ArrayList<TemporaryDebugArtifact>();
    if (!(artifactsValue instanceof List)) {
      return artifacts;
    }
    List values = (List) artifactsValue;
    for (Object value : values) {
      if (!(value instanceof Map)) {
        continue;
      }
      Map artifact = (Map) value;
      artifacts.add(TemporaryDebugArtifact.builder()
          .uploadBatchId(asText(artifact.get("uploadBatchId")))
          .artifactId(asText(artifact.get("artifactId")))
          .fileName(asText(artifact.get("fileName")))
          .relativePath(asText(artifact.get("relativePath")))
          .sha256(asText(artifact.get("sha256")))
          .sizeBytes(asLong(artifact.get("sizeBytes")))
          .createdAt(parseInstant(artifact.get("createdAt")))
          .build());
    }
    return artifacts;
  }

  private TemporaryResolvedRuntimeLock buildTemporaryLock(String sessionId,
                                                         String uploadBatchId,
                                                         ResolvedRuntimeLock stableLock,
                                                         DebugOverlay debugOverlay) {
    List<String> diagnostics = new ArrayList<String>();
    if (stableLock.getDiagnostics() != null) {
      diagnostics.addAll(stableLock.getDiagnostics());
    }
    diagnostics.add("debug overlay session: " + sessionId);
    diagnostics.add("debug upload batch: " + uploadBatchId);
    diagnostics.add("debug artifact count: " + debugOverlay.getArtifacts().size());
    List<ResolvedRuntimeArtifact> resolvedArtifacts = new ArrayList<ResolvedRuntimeArtifact>();
    if (stableLock.getResolvedArtifacts() != null) {
      resolvedArtifacts.addAll(stableLock.getResolvedArtifacts());
    }
    for (TemporaryDebugArtifact artifact : debugOverlay.getArtifacts()) {
      resolvedArtifacts.add(ResolvedRuntimeArtifact.builder()
          .artifactId(artifact.getRelativePath())
          .checksum(artifact.getSha256())
          .packaging("binary")
          .effectiveScope("debug")
          .sourceType(ResolvedArtifactSourceType.DEBUG_OVERLAY)
          .build());
    }
    ResolvedRuntimeLock resolvedLock = ResolvedRuntimeLock.builder()
        .appId(stableLock.getAppId())
        .profileId(stableLock.getProfileId())
        .runtimeKind(stableLock.getRuntimeKind())
        .status(stableLock.getStatus())
        .resolvedAt(stableLock.getResolvedAt())
        .baseBomVersion(stableLock.getBaseBomVersion())
        .topLevelArtifacts(stableLock.getTopLevelArtifacts())
        .resolvedArtifacts(resolvedArtifacts)
        .suppressedArtifacts(stableLock.getSuppressedArtifacts())
        .diagnostics(diagnostics)
        .build();
    return TemporaryResolvedRuntimeLock.builder()
        .sessionId(sessionId)
        .uploadBatchId(uploadBatchId)
        .debugOverlay(debugOverlay)
        .baseLock(stableLock)
        .resolvedLock(resolvedLock)
        .preparedAt(Instant.now())
        .build();
  }

  private void persistPreparationArtifacts(String profileId,
                                           String sessionId,
                                           DebugOverlay debugOverlay,
                                           TemporaryResolvedRuntimeLock temporaryLock) throws Exception {
    Path sessionRoot = sessionRoot(profileId, sessionId);
    Files.createDirectories(sessionRoot);
    Files.write(sessionRoot.resolve("debug-overlay.json"), Json.toPrettyJsonString(debugOverlay).getBytes(StandardCharsets.UTF_8));
    Files.write(sessionRoot.resolve("temporary-runtime-lock.json"), Json.toPrettyJsonString(temporaryLock).getBytes(StandardCharsets.UTF_8));
  }

  private OpenDebugSessionCommand enrichCommand(OpenDebugSessionCommand command, String sessionId, String uploadBatchId) {
    Map<String, String> metadata = new LinkedHashMap<String, String>();
    if (command.getMetadata() != null) {
      metadata.putAll(command.getMetadata());
    }
    metadata.put("debugPreparationSessionId", sessionId);
    metadata.put("debugUploadBatchId", uploadBatchId);
    return OpenDebugSessionCommand.builder()
        .appName(command.getAppName())
        .profile(command.getProfile())
        .runtimeKind(command.getRuntimeKind())
        .backendType(command.getBackendType())
        .targetType(command.getTargetType())
        .expectedGeneration(command.getExpectedGeneration())
        .uploadBatchId(uploadBatchId)
        .preparationSessionId(sessionId)
        .ownerUserId(command.getOwnerUserId())
        .ownerUserName(command.getOwnerUserName())
        .maxLifetimeSeconds(command.getMaxLifetimeSeconds())
        .idleTimeoutSeconds(command.getIdleTimeoutSeconds())
        .reason(command.getReason())
        .metadata(metadata)
        .requestedSessionId(sessionId)
        .build();
  }

  private Path uploadManifestPath(String profileId, String uploadBatchId) {
    return Paths.get(developWorkspaceContext.getDebugSupportFilesDir(),
        profileStorageKeyEncoder.encode(profileId),
        "runtime-debug",
        "uploads",
        uploadBatchId,
        "manifest.json");
  }

  private Path sessionRoot(String profileId, String sessionId) {
    return Paths.get(developWorkspaceContext.getDebugSupportFilesDir(),
        profileStorageKeyEncoder.encode(profileId),
        "runtime-debug",
        "sessions",
        sessionId);
  }

  private String asText(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private Long asLong(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number) {
      return Long.valueOf(((Number) value).longValue());
    }
    return Long.valueOf(String.valueOf(value));
  }

  private Instant parseInstant(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return instantParser.apply(String.valueOf(value));
    } catch (Exception exception) {
      return null;
    }
  }

}
