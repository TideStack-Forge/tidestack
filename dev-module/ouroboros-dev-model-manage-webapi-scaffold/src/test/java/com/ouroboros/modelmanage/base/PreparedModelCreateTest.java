package com.ouroboros.modelmanage.base;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.file.attribute.PosixFilePermission;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.vavr.control.Try;

import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.VCSUserProvider;
import com.ouroboros.vcs.action.DefaultBatchAction;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.DefaultVCSFile;
import com.ouroboros.vcs.filesystem.LocalFileSystemManager;
import com.ouroboros.vcs.lock.LocalFileLockManager;
import com.ouroboros.vcs.versioning.git.GitManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PreparedModelCreateTest {
  @TempDir Path home;
  private LocalFileSystemManager files;
  private LocalFileLockManager locks;
  private GitManager versions;
  private VCSManager vcs;
  private TestService service;
  private final List<Object> events = new ArrayList<>();
  private final VCSUserProvider user = new VCSUserProvider() {
    public String getCurrentUser() { return "modeler"; }
    public String getCurrentUserEmail() { return "modeler@example.test"; }
  };

  @BeforeEach
  void setUp() throws Exception {
    DevAppHolder.setAppName("prepare-test");
    var repository = Files.createDirectory(home.resolve("metadata"));
    files = new LocalFileSystemManager(repository.toString());
    locks = new LocalFileLockManager(home.resolve("metadata.lock").toString());
    versions = new GitManager(repository.toString(), "main");
    files.createFile("models/Order.model.json", Json.toJsonString(new Model("Order", "old", true))).get();
    versions.startChain().user(user.getCurrentUser()).email(user.getCurrentUserEmail())
        .add("models/Order.model.json").commit("baseline").get();
    vcs = mock(VCSManager.class);
    when(vcs.getFile(anyString())).thenAnswer(invocation -> new DefaultVCSFile(
        versions, files, locks, user, name -> name, invocation.getArgument(0)));
    when(vcs.lock(anyCollection(), eq(false))).thenAnswer(invocation ->
        locks.lock(invocation.<java.util.Collection<String>>getArgument(0), user.getCurrentUser()));
    when(vcs.unlock(anyCollection(), eq(false))).thenAnswer(invocation ->
        locks.unlock(invocation.<java.util.Collection<String>>getArgument(0), user.getCurrentUser()));
    when(vcs.walkDirectory(anyString(), anyString())).thenAnswer(invocation -> {
      String directory = invocation.getArgument(0), extension = invocation.getArgument(1);
      return Files.list(repository.resolve(directory))
          .filter(path -> path.toString().endsWith(extension))
          .map(path -> vcs.getFile(directory + "/" + path.getFileName()));
    });
    when(vcs.getChangeLogs(anyString())).thenAnswer(invocation -> versions.getChangeLogs(invocation.<String>getArgument(0)));
    when(vcs.getChangeLogs(anyString(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
        .thenAnswer(invocation -> versions.getChangeLogs(invocation.<String>getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
    when(vcs.batchOperation()).thenAnswer(ignore -> new DefaultBatchAction(versions, files, locks, user));
    service = new TestService(vcs);
  }

  @AfterEach
  void tearDown() {
    DevAppHolder.removeAppName();
    locks.destroy(); versions.destroy(); files.destroy();
  }

  @Test
  void preparationDoesNotWriteLockCommitOrPublishAndJoinsTheExistingUpdateBatch() {
    var prepared = service.prepareCreate(new Model("approve", "script", true)).get();
    assertEquals("models/approve.model.json", prepared.getPrimaryPath());
    assertEquals("script", Json.toMap(prepared.getPrimaryContent()).get("content"));
    assertFalse(files.exists(prepared.getPrimaryPath()));
    assertTrue(versions.isWorkspaceClean().get());
    assertTrue(events.isEmpty());
    var updated = service.updateWith(new Model("Order", "2.0", true), prepared::appendTo);
    assertTrue(updated.isSuccess(), () -> String.valueOf(updated.getCause()));
    assertEquals("2.0", Json.toMap(files.getFileContent("models/Order.model.json").get()).get("content"));
    assertEquals(prepared.getPrimaryContent(), files.getFileContent(prepared.getPrimaryPath()).get());
    assertEquals(1, events.size());
    assertEquals(1, versions.getChangeLogs("models/Order.model.json").size());
    assertNull(locks.getLockOwner(prepared.getPrimaryPath()));
  }

  @Test
  void validationAndSerializationFailureLeaveTheWholeWorkspaceUntouched() {
    assertTrue(service.prepareCreate(new Model("bad", "script", false)).isFailure());
    service.serializationFails = true;
    assertTrue(service.prepareCreate(new Model("bad", "script", true)).isFailure());
    assertTrue(versions.isWorkspaceClean().get());
    assertFalse(files.exists("models/bad.model.json"));
    assertTrue(events.isEmpty());
  }

  @Test
  void bothPrimaryAndLegacyNamesAreCheckedWithoutOverwritingEither() {
    assertTrue(service.prepareCreate(new Model("Order", "replace", true)).isFailure());
    files.createFile("models/approve.json", "legacy content").get();
    assertTrue(service.prepareCreate(new Model("approve", "script", true)).isFailure());
    assertEquals("legacy content", files.getFileContent("models/approve.json").get());
    assertFalse(files.exists("models/approve.model.json"));
  }

  @Test
  void associatedBinaryContentsAreReadAndClosedBeforeAnyMutation() {
    byte[] binary = {0, 1, -1, 23};
    boolean[] closed = {false};
    service.associated = Map.of("attachments/source.bin", () -> new ByteArrayInputStream(binary) {
      @Override public void close() { closed[0] = true; }
    });
    var prepared = service.prepareCreate(new Model("approve", "script", true)).get();
    assertTrue(closed[0]);
    binary[0] = 99;
    assertFalse(files.exists("attachments/source.bin"));
    var batch = vcs.batchOperation();
    prepared.appendTo(batch);
    assertTrue(batch.write(false).get());
    assertArrayEquals(new byte[]{0, 1, -1, 23}, files.getFileBytes("attachments/source.bin").get());
    assertEquals(2, prepared.getPaths().size());
  }

  @Test
  void associatedReadFailureOrDuplicatePathRejectsPreparationWithoutPartialFiles() {
    service.associated = Map.of("attachments/bad.bin", () -> new InputStream() {
      @Override public int read() { throw new BusinessException("cannot read source"); }
    });
    assertEquals("cannot read source", service.prepareCreate(new Model("approve", "script", true)).getCause().getMessage());
    service.associated = Map.of("models/approve.model.json", () -> InputStream.nullInputStream());
    assertTrue(service.prepareCreate(new Model("approve", "script", true)).isFailure());
    assertTrue(versions.isWorkspaceClean().get());
    assertFalse(files.exists("models/approve.model.json"));
  }

  @Test
  void resourcesCreatedAfterPreviewAreRejectedBeforeAppendingAnyWrites() {
    var prepared = service.prepareCreate(new Model("approve", "script", true)).get();
    files.createFile("models/approve.json", "created by another operation").get();
    assertThrows(BusinessException.class, () -> prepared.appendTo(vcs.batchOperation()));
    assertFalse(files.exists(prepared.getPrimaryPath()));
    assertEquals("created by another operation", files.getFileContent("models/approve.json").get());
  }

  @Test
  void resourceOccupationAfterAppendingIsRecheckedBeforeTheFirstWrite() {
    var prepared = service.prepareCreate(new Model("approve", "script", true)).get();
    var batch = vcs.batchOperation().update("models/Order.model.json", "2.0");
    var before = files.getFileBytes("models/Order.model.json").get();
    prepared.appendTo(batch);
    files.createFile("models/approve.json", "concurrent resource").get();
    var result = batch.autoLock().commit("must reject stale preparation");
    assertTrue(result.isFailure());
    assertArrayEquals(before, files.getFileBytes("models/Order.model.json").get());
    assertFalse(files.exists(prepared.getPrimaryPath()));
    assertEquals("concurrent resource", files.getFileContent("models/approve.json").get());
  }

  @Test
  void aLaterWriteFailureRestoresTheOldModelAndAllPreparedResources() {
    var original = files.getFileBytes("models/Order.model.json").get();
    var prepared = service.prepareCreate(new Model("approve", "script", true)).get();
    var result = service.updateWith(new Model("Order", "2.0", true), batch -> {
      prepared.appendTo(batch);
      batch.create("models/Order.model.json", "late collision");
    });
    assertTrue(result.isFailure());
    assertArrayEquals(original, files.getFileBytes("models/Order.model.json").get());
    assertFalse(files.exists(prepared.getPrimaryPath()));
    assertTrue(events.isEmpty());
    assertTrue(versions.isWorkspaceClean().get());
  }

  @Test
  void explicitResourceUpdateCommitsTheWholeSetBeforePublishingTheModelChange() {
    var prepared = service.prepareCreate(new Model("approve", "script", true)).get();
    assertTrue(service.updateAndCommit(new Model("Order", "2.0", true), prepared::appendTo).isSuccess());
    assertEquals(prepared.getPrimaryContent(), versions.getFileContent(prepared.getPrimaryPath(), "HEAD").get());
    assertEquals("2.0", Json.toMap(versions.getFileContent("models/Order.model.json", "HEAD").get()).get("content"));
    assertTrue(versions.isWorkspaceClean().get());
    assertEquals(1, events.size());
    assertNull(locks.getLockOwner("models/Order.model.json"));
    assertNull(locks.getLockOwner(prepared.getPrimaryPath()));
  }

  @Test
  void rejectedResourceCommitKeepsTheOldVersionAndPublishesNoModelChange() throws Exception {
    var original = files.getFileBytes("models/Order.model.json").get();
    var prepared = service.prepareCreate(new Model("approve", "script", true)).get();
    Path hook = home.resolve("metadata/.git/hooks/pre-commit");
    Files.writeString(hook, "#!/bin/sh\nexit 1\n");
    Files.setPosixFilePermissions(hook, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE));
    var result = service.updateAndCommit(new Model("Order", "2.0", true), prepared::appendTo);
    assertTrue(result.isFailure());
    assertNotNull(result.getCause().getCause());
    assertArrayEquals(original, files.getFileBytes("models/Order.model.json").get());
    assertFalse(files.exists(prepared.getPrimaryPath()));
    assertTrue(versions.isWorkspaceClean().get());
    assertTrue(events.isEmpty());
    assertNull(locks.getLockOwner(prepared.getPrimaryPath()));
  }

  @Test
  void preparedAppendConflictReturnsFailureWithoutWritingOrPublishing() {
    var prepared = service.prepareCreate(new Model("approve", "script", true)).get();
    files.createFile("models/approve.json", "new competing file").get();
    var original = files.getFileBytes("models/Order.model.json").get();
    var result = service.updateAndCommit(new Model("Order", "2.0", true), prepared::appendTo);
    assertTrue(result.isFailure());
    assertArrayEquals(original, files.getFileBytes("models/Order.model.json").get());
    assertEquals("new competing file", files.getFileContent("models/approve.json").get());
    assertFalse(files.exists(prepared.getPrimaryPath()));
    assertTrue(events.isEmpty());
  }

  @Test
  void committedContentStillPublishesItsModelChangeWhenOnlyUnlockFails() {
    var prepared = service.prepareCreate(new Model("approve", "script", true)).get();
    when(vcs.batchOperation()).thenAnswer(ignore -> new DefaultBatchAction(versions, files, locks, user) {
      @Override protected void doUnlock(String username) { throw new BusinessException("unlock failed"); }
    });
    var result = service.updateAndCommit(new Model("Order", "2.0", true), prepared::appendTo);
    assertTrue(result.isFailure());
    assertInstanceOf(com.ouroboros.vcs.exception.VCSPostCommitException.class, result.getCause().getCause());
    assertEquals(prepared.getPrimaryContent(), versions.getFileContent(prepared.getPrimaryPath(), "HEAD").get());
    assertEquals(1, events.size());
    assertTrue(versions.isWorkspaceClean().get());
  }

  @Test
  void originalModelCreationStillCommitsUnlocksAndPublishesExactlyOnce() {
    var created = new Model("New", "created", true);
    assertEquals(created, service.create(created, "").get());
    assertEquals(created, service.getOne("New").orElseThrow());
    assertEquals("创建测试模型 (New)", versions.getChangeLogs("models/New.model.json").getFirst().getMessage());
    assertNull(locks.getLockOwner("models/New.model.json"));
    assertEquals(1, events.size());
    assertTrue(service.getOne("missing").isEmpty());
    assertEquals(created, service.getInitFormData("New").get());
    var status = (com.ouroboros.modelmanage.web.vo.SingleFileStatus) service.getStatus("New").get();
    assertEquals("NOTCHANGED", status.getVersionState());
    assertEquals("UNLOCKED", status.getLockState());
    assertNull(status.getLockOwner());
  }

  @Test
  void workspaceOnlyStrategyRetainsLockUntilExplicitCommitAndLeavesUnrelatedFilesAlone() {
    service.autoCreate = false;
    var model = new Model("New", "first", true);
    assertTrue(service.create(model, "").isSuccess());
    assertEquals("modeler", locks.getLockOwner("models/New.model.json"));
    assertTrue(service.getAllChangeLogs("New").isEmpty());
    service.update("New", new Model("New", "latest", true)).get();
    files.createFile("unrelated.json", "{}").get();
    assertTrue(service.commit("New", "explicit").get());
    assertEquals("latest", Json.toMap(versions.getFileContent("models/New.model.json", "HEAD").get()).get("content"));
    assertEquals(1, service.getChangeLogs("New", 0, 10).size());
    assertEquals(Set.of("models/New.model.json"), service.getAllChangeLogs("New").getFirst().getChanges().keySet());
    assertNull(locks.getLockOwner("models/New.model.json"));
    assertFalse(versions.isWorkspaceClean().get());
  }

  @Test
  void updateRejectsInvalidMissingOccupiedAndUnserializableModelsWithoutChangingTheBaseline() {
    var baseline = files.getFileContent("models/Order.model.json").get();
    assertTrue(service.update("Order", new Model("Order", "bad", false)).isFailure());
    assertTrue(service.update("missing", new Model("missing", "new", true)).isFailure());
    files.createFile("models/occupied.json", "legacy").get();
    assertTrue(service.update("Order", new Model("occupied", "new", true)).isFailure());
    service.serializationFails = true;
    assertTrue(service.update("Order", new Model("Order", "new", true)).isFailure());
    assertEquals(baseline, files.getFileContent("models/Order.model.json").get());
    assertTrue(events.isEmpty());
  }

  @Test
  void originalAutoCommitUpdateSupportsRenameAndLegacyReadsWithoutDuplicateSearchResults() {
    service.autoUpdate = true;
    service.lock("Order", false).get();
    service.update("Order", new Model("Renamed", "new", true)).get();
    assertFalse(files.exists("models/Order.model.json"));
    assertEquals("new", service.getOne("Renamed").orElseThrow().content());
    assertEquals("更新测试模型 (Order)", versions.getChangeLogs("models/Renamed.model.json").getFirst().getMessage());
    files.createFile("models/Renamed.json", Json.toJsonString(new Model("Renamed", "obsolete", true))).get();
    files.createFile("models/Legacy.json", Json.toJsonString(new Model("Legacy", "legacy", true))).get();
    files.createFile("models/corrupt.json", "{bad").get();
    var result = service.search(Map.of());
    assertEquals(2, result.size());
    assertTrue(result.contains(new Model("Renamed", "new", true)));
    assertEquals("legacy", service.getOne("Legacy").orElseThrow().content());
  }

  @Test
  void rollbackAndDeleteRespectResourceLocksAndPublishOnlySuccessfulChanges() {
    service.lock("Order", false).get();
    service.update("Order", new Model("Order", "unsaved-version", true)).get();
    assertTrue(service.rollback("Order").get());
    assertEquals("old", service.getOne("Order").orElseThrow().content());
    assertNull(locks.getLockOwner("models/Order.model.json"));
    locks.lock("models/Order.model.json", "other-user").get();
    assertTrue(service.lock("Order", false).isFailure());
    assertTrue(service.unlock("Order", false).isFailure());
    assertTrue(service.delete("Order", "must fail").isFailure());
    assertTrue(service.getOne("Order").isPresent());
    locks.unlock("models/Order.model.json").get();
    service.lock("Order", false).get();
    service.unlock("Order", false).get();
    assertTrue(service.delete("Order", "").isSuccess());
    assertTrue(service.getOne("Order").isEmpty());
    assertTrue(service.delete("missing", "").isSuccess());
    assertEquals(3, events.size());
  }

  private record Model(String name, String content, boolean valid) { }

  private final class TestService extends BaseDevelopService<Model> {
    private boolean serializationFails;
    private boolean autoCreate = true;
    private boolean autoUpdate;
    private Map<String, Supplier<InputStream>> associated = Map.of();
    TestService(VCSManager manager) {
      vcsManagerCenter = mock(VCSManagerCenter.class);
      when(vcsManagerCenter.getVcsManager("prepare-test")).thenReturn(manager);
      publisher = events::add;
    }
    protected String getModelFileDirectory() { return "models"; }
    protected String getModelFileExtension() { return ".model.json"; }
    protected List<String> getLegacyModelFileExtensions() { return List.of(".json"); }
    protected String getModelDisplayName() { return "测试模型"; }
    protected String getFullName(Model model) { return model.name(); }
    protected boolean isAutoCommitWhenCreate() { return autoCreate; }
    protected boolean isAutoCommitWhenUpdate() { return autoUpdate; }
    protected Try<Void> validateModel(Model model) {
      return model.valid() ? Try.success(null) : Try.failure(new BusinessException("invalid source"));
    }
    protected Try<String> getPermanentJson(Model model) {
      return serializationFails ? Try.failure(new BusinessException("cannot serialize source")) : super.getPermanentJson(model);
    }
    protected Map<String, Supplier<InputStream>> getAssociatedFilesForCreate(String name, Model model) { return associated; }
    Try<Model> updateWith(Model model, java.util.function.Consumer<com.ouroboros.vcs.action.VCSAction.BatchAction> operation) {
      return doUpdate("Order", model, List.of(operation));
    }
    Try<Model> updateAndCommit(Model model, java.util.function.Consumer<com.ouroboros.vcs.action.VCSAction.BatchAction> operation) {
      return doUpdateAndCommit("Order", model, List.of(operation), "upgrade model and associated resources");
    }
  }
}
