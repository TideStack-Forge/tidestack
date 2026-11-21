package com.ouroboros.dev.app.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.vavr.control.Try;

import com.ouroboros.dev.app.service.impl.AppConfigService;
import com.ouroboros.dev.runtime.model.AppArtifactBinding;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.DebugOverlay;
import com.ouroboros.dev.runtime.model.OpenDebugSessionCommand;
import com.ouroboros.dev.runtime.model.ResolvedArtifactSourceType;
import com.ouroboros.dev.runtime.model.ResolvedRuntimeLock;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeLockStatus;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.model.TemporaryResolvedRuntimeLock;
import com.ouroboros.dev.runtime.model.WorkspaceMode;
import com.ouroboros.dev.runtime.service.DevRuntimeService;
import com.ouroboros.dev.runtime.service.InMemoryRuntimeLockStore;
import com.ouroboros.dev.runtime.service.InMemoryRuntimeStateStore;
import com.ouroboros.dev.runtime.service.RuntimeLockKey;
import com.ouroboros.dev.runtime.service.RuntimeLockRecord;

class DefaultDebugRuntimePreparationServiceTest {

  @TempDir
  Path tempDir;

  @Test
  void shouldPersistSessionScopedOverlayAndTemporaryLockBeforeReloadingWorkspaceWithoutMutatingStableLatestLock() throws Exception {
    Path debugSupportDir = Files.createDirectories(tempDir.resolve("debug-support"));
    WorkspaceDebugSessionWorkspaceStorage storage = new WorkspaceDebugSessionWorkspaceStorage(RuntimeFactoryTestSupport.workspaceContext(tempDir));
    InMemoryRuntimeLockStore runtimeLockStore = new InMemoryRuntimeLockStore();
    InMemoryRuntimeStateStore runtimeStateStore = new InMemoryRuntimeStateStore();
    ResolvedRuntimeLock stableLock = stableLock();
    runtimeLockStore.saveLatestSuccessful(RuntimeLockRecord.of(RuntimeLockKey.of("crm", "alice/dev", RuntimeKind.TEST_APP), stableLock));
    RuntimeSpec runtimeSpec = runtimeSpec();
    runtimeStateStore.saveSpec(RuntimeIdentityKey.from(runtimeSpec), runtimeSpec);
    RecordingDevRuntimeService devRuntimeService = new RecordingDevRuntimeService();
    RecordingAppConfigService appConfigService = new RecordingAppConfigService();
    DefaultDebugRuntimePreparationService service = new DefaultDebugRuntimePreparationService(
        runtimeLockStore,
        runtimeStateStore,
        RuntimeFactoryTestSupport.workspaceContext(tempDir),
        devRuntimeService,
        appConfigService,
        Instant::parse,
        new ProfileStorageKeyEncoder()
    );
    StoredUploadBatch storedUploadBatch = storeUploadBatch(storage);
    OpenDebugSessionCommand command = OpenDebugSessionCommand.builder()
        .appName("crm")
        .profile("alice/dev")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .ownerUserId("alice")
        .ownerUserName("alice")
        .build();

    Try<OpenDebugSessionCommand> prepared = service.prepare("dbg-prep-001", command, storedUploadBatch.uploadBatchId);

    assertTrue(prepared.isSuccess());
    assertEquals("dbg-prep-001", prepared.get().getRequestedSessionId());
    assertEquals(storedUploadBatch.uploadBatchId, prepared.get().getUploadBatchId());
    assertEquals("dbg-prep-001", prepared.get().getPreparationSessionId());
    assertEquals("dbg-prep-001", prepared.get().getMetadata().get("debugPreparationSessionId"));
    Path sessionRoot = debugSupportDir.resolve(new ProfileStorageKeyEncoder().encode("alice/dev"))
        .resolve("runtime-debug")
        .resolve("sessions")
        .resolve("dbg-prep-001");
    assertTrue(Files.exists(sessionRoot.resolve("debug-overlay.json")));
    assertTrue(Files.exists(sessionRoot.resolve("temporary-runtime-lock.json")));
    assertEquals(Integer.valueOf(1), Integer.valueOf(appConfigService.buildRunWorkspaceCount));
    assertEquals("crm", appConfigService.appName);
    assertEquals("alice/dev", appConfigService.profile);
    assertEquals(Integer.valueOf(1), Integer.valueOf(devRuntimeService.reloadWorkspaceCount));
    assertEquals(runtimeSpec, devRuntimeService.reloadedSpec);
    assertEquals(Integer.valueOf(0), Integer.valueOf(devRuntimeService.restartCount));
    assertEquals(stableLock.getResolvedAt(), runtimeLockStore.findLatestSuccessful(RuntimeLockKey.of("crm", "alice/dev", RuntimeKind.TEST_APP)).get().getLock().getResolvedAt());
    assertEquals(Integer.valueOf(stableLock.getDiagnostics().size()), Integer.valueOf(runtimeLockStore.findLatestSuccessful(RuntimeLockKey.of("crm", "alice/dev", RuntimeKind.TEST_APP)).get().getLock().getDiagnostics().size()));
  }

  @Test
  void shouldFailPreparationWithoutRestartWhenUploadBatchMissingAndKeepStableLatestUntouched() {
    InMemoryRuntimeLockStore runtimeLockStore = new InMemoryRuntimeLockStore();
    InMemoryRuntimeStateStore runtimeStateStore = new InMemoryRuntimeStateStore();
    ResolvedRuntimeLock stableLock = stableLock();
    runtimeLockStore.saveLatestSuccessful(RuntimeLockRecord.of(RuntimeLockKey.of("crm", "alice/dev", RuntimeKind.TEST_APP), stableLock));
    RecordingDevRuntimeService devRuntimeService = new RecordingDevRuntimeService();
    RecordingAppConfigService appConfigService = new RecordingAppConfigService();
    DefaultDebugRuntimePreparationService service = new DefaultDebugRuntimePreparationService(
        runtimeLockStore,
        runtimeStateStore,
        RuntimeFactoryTestSupport.workspaceContext(tempDir),
        devRuntimeService,
        appConfigService,
        Instant::parse,
        new ProfileStorageKeyEncoder()
    );
    OpenDebugSessionCommand command = OpenDebugSessionCommand.builder()
        .appName("crm")
        .profile("alice/dev")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .ownerUserId("alice")
        .ownerUserName("alice")
        .build();

    Try<OpenDebugSessionCommand> prepared = service.prepare("dbg-prep-002", command, "missing-batch");

    assertTrue(prepared.isFailure());
    assertEquals(Integer.valueOf(0), Integer.valueOf(appConfigService.buildRunWorkspaceCount));
    assertEquals(Integer.valueOf(0), Integer.valueOf(devRuntimeService.reloadWorkspaceCount));
    assertEquals(Integer.valueOf(0), Integer.valueOf(devRuntimeService.restartCount));
    assertEquals(stableLock.getBaseBomVersion(), runtimeLockStore.findLatestSuccessful(RuntimeLockKey.of("crm", "alice/dev", RuntimeKind.TEST_APP)).get().getLock().getBaseBomVersion());
  }

  private StoredUploadBatch storeUploadBatch(WorkspaceDebugSessionWorkspaceStorage storage) {
    com.ouroboros.dev.app.model.DebugArtifactUploadRequest request = new com.ouroboros.dev.app.model.DebugArtifactUploadRequest();
    request.setDevKey("dev-key-001");
    com.ouroboros.dev.app.model.DebugArtifactUploadRequest.UploadFile file = new com.ouroboros.dev.app.model.DebugArtifactUploadRequest.UploadFile();
    file.setFileName("agent.jar");
    file.setContentBase64("aGVsbG8=");
    request.getFiles().add(file);
    com.ouroboros.dev.app.model.DebugArtifactUploadResponseDTO response = storage.store("alice/dev", request).get();
    return new StoredUploadBatch(
        response.getUploadBatchId(),
        response.getArtifacts().get(0).getArtifactId(),
        response.getArtifacts().get(0).getRelativePath()
    );
  }

  static class StoredUploadBatch {
    private final String uploadBatchId;
    private final String artifactId;
    private final String relativePath;

    StoredUploadBatch(String uploadBatchId, String artifactId, String relativePath) {
      this.uploadBatchId = uploadBatchId;
      this.artifactId = artifactId;
      this.relativePath = relativePath;
    }
  }

  private static ResolvedRuntimeLock stableLock() {
    AppArtifactBinding artifact = AppArtifactBinding.builder()
        .appId("crm")
        .groupId("com.example")
        .artifactId("crm-runtime")
        .version("1.0.0")
        .packaging("jar")
        .enabled(true)
        .usage("runtime")
        .build();
    return ResolvedRuntimeLock.builder()
        .appId("crm")
        .profileId("alice/dev")
        .runtimeKind(RuntimeKind.TEST_APP)
        .status(RuntimeLockStatus.READY)
        .resolvedAt(Instant.parse("2026-03-24T12:00:00Z"))
        .baseBomVersion("2026.03")
        .topLevelArtifacts(Arrays.asList(artifact))
        .resolvedArtifacts(Arrays.asList())
        .suppressedArtifacts(Arrays.asList())
        .diagnostics(Arrays.asList("base BOM first: 2026.03"))
        .build();
  }

  private static RuntimeSpec runtimeSpec() {
    return RuntimeSpec.builder()
        .appName("crm")
        .profile("alice/dev")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .image("ouroboros/test-app:latest")
        .build();
  }

  static class RecordingAppConfigService extends AppConfigService {
    int buildRunWorkspaceCount;
    String appName;
    String profile;

    RecordingAppConfigService() {
      super(null, null, null);
    }

    @Override
    public Try<Void> buildRunWorkspace(String appName, String profile) {
      buildRunWorkspaceCount++;
      this.appName = appName;
      this.profile = profile;
      return Try.success(null);
    }
  }

  static class RecordingDevRuntimeService implements DevRuntimeService {
    int restartCount;
    int reloadWorkspaceCount;
    RuntimeSpec reloadedSpec;

    @Override
    public Try<RuntimeInstance> create(com.ouroboros.dev.runtime.model.RuntimeSpec spec) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<RuntimeInstance> start(RuntimeIdentityKey key) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<RuntimeInstance> stop(RuntimeIdentityKey key) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<RuntimeInstance> restart(RuntimeIdentityKey key) {
      restartCount++;
      return Try.success(RuntimeInstance.builder()
          .runtimeId("rt-" + key.getAppName() + "-" + key.getProfile())
          .appName(key.getAppName())
          .profile(key.getProfile())
          .runtimeKind(key.getRuntimeKind())
          .backendType(key.getBackendType())
          .runtimeGeneration(Long.valueOf(13L))
          .status(RuntimeStatus.RUNNING)
          .build());
    }

    @Override
    public Try<Void> delete(RuntimeIdentityKey key) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<RuntimeInstance> reloadWorkspace(com.ouroboros.dev.runtime.model.RuntimeSpec spec) {
      reloadWorkspaceCount++;
      reloadedSpec = spec;
      return Try.success(RuntimeInstance.builder()
          .runtimeId("rt-" + spec.getAppName() + "-" + spec.getProfile())
          .appName(spec.getAppName())
          .profile(spec.getProfile())
          .runtimeKind(spec.getRuntimeKind())
          .backendType(spec.getBackendType())
          .runtimeGeneration(Long.valueOf(13L))
          .status(RuntimeStatus.RUNNING)
          .build());
    }

    @Override
    public Try<RuntimeInstance> getStatus(RuntimeIdentityKey key) {
      return Try.success(RuntimeInstance.builder()
          .runtimeId("rt-" + key.getAppName() + "-" + key.getProfile())
          .appName(key.getAppName())
          .profile(key.getProfile())
          .runtimeKind(key.getRuntimeKind())
          .backendType(key.getBackendType())
          .runtimeGeneration(Long.valueOf(12L))
          .status(RuntimeStatus.RUNNING)
          .build());
    }

    @Override
    public Try<List<String>> getLogs(RuntimeIdentityKey key) {
      return Try.success(Collections.<String>emptyList());
    }
  }
}
