package com.ouroboros.rpc.dev.service.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.rpc.metadata.RPCModelMetadata;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.VCSUserProvider;
import com.ouroboros.vcs.action.DefaultBatchAction;
import com.ouroboros.vcs.file.DefaultVCSFile;
import com.ouroboros.vcs.filesystem.LocalFileSystemManager;
import com.ouroboros.vcs.lock.LocalFileLockManager;
import com.ouroboros.vcs.versioning.git.GitManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RpcModelWorkspaceSaveTest {
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
    DevAppHolder.setAppName("rpc-save-test");
    var repository = Files.createDirectory(home.resolve("metadata"));
    files = new LocalFileSystemManager(repository.toString());
    locks = new LocalFileLockManager(home.resolve("metadata.lock").toString());
    versions = new GitManager(repository.toString(), "main");
    files.createFile("baseline.json", "{}").get();
    versions.startChain().user(user.getCurrentUser()).email(user.getCurrentUserEmail())
        .add("baseline.json").commit("baseline").get();
    vcs = mock(VCSManager.class);
    when(vcs.getFile(anyString())).thenAnswer(invocation -> new DefaultVCSFile(
        versions, files, locks, user, name -> name, invocation.getArgument(0)));
    when(vcs.batchOperation()).thenAnswer(ignore -> new DefaultBatchAction(versions, files, locks, user));
    when(vcs.lock(anyCollection(), eq(false))).thenAnswer(invocation -> {
      Collection<String> paths = invocation.getArgument(0);
      return locks.lock(paths, user.getCurrentUser());
    });
    when(vcs.unlock(anyCollection(), eq(false))).thenAnswer(invocation -> {
      Collection<String> paths = invocation.getArgument(0);
      return locks.unlock(paths, user.getCurrentUser());
    });
    service = new TestService();
  }

  @AfterEach
  void tearDown() {
    DevAppHolder.removeAppName();
    locks.destroy(); versions.destroy(); files.destroy();
  }

  @Test
  void repeatedSaveWritesLatestWorkspaceWithoutCreatingVersionsAndCommitOnlyIncludesThisModel() {
    assertTrue(service.create(model("one"), "ignored on save").isSuccess());
    assertEquals("modeler", locks.getLockOwner(path()));
    assertTrue(versions.getChangeLogs(path()).isEmpty());
    assertTrue(service.update("Lookup", model("two")).isSuccess());
    assertEquals("two", service.getOne("Lookup").orElseThrow().getTitle());
    assertTrue(versions.getChangeLogs(path()).isEmpty());
    files.createFile("unrelated.json", "{}").get();
    assertTrue(service.commit("Lookup", "  支持客户查询  ").isSuccess());
    var history = versions.getChangeLogs(path());
    assertEquals(1, history.size());
    assertEquals("支持客户查询", history.getFirst().getMessage());
    assertEquals(Set.of(path()), history.getFirst().getChanges().keySet());
    assertEquals("two", Json.toBean(RPCModelMetadata.class, versions.getFileContent(path(), "HEAD").get()).getTitle());
    assertNull(locks.getLockOwner(path()));
    assertFalse(versions.isWorkspaceClean().get());
    assertEquals(2, events.size());
  }

  @Test
  void savingRegroupingAndClearingGroupKeepsTheSameModelFile() {
    var model = model("查询");
    model.setGroup(" 钉钉 / 通讯录 ");
    service.create(model, "").get();
    assertEquals("钉钉/通讯录", service.getOne("Lookup").orElseThrow().getGroup());
    model.setGroup("业务/订单");
    service.update("Lookup", model).get();
    assertEquals("业务/订单", Json.toBean(RPCModelMetadata.class, files.getFileContent(path()).get()).getGroup());
    model.setGroup(null);
    service.update("Lookup", model).get();
    assertNull(service.getOne("Lookup").orElseThrow().getGroup());
    assertEquals(1, files.walk("rpc-model").filter(".rpc.json").stream().count());
    assertTrue(versions.getChangeLogs(path()).isEmpty());
  }

  @Test
  void invalidCommitMessagesDoNotCreateVersionOrDropSavedContent() {
    service.create(model("saved"), "").get();
    for (String message : new String[]{null, "", "   ", "x".repeat(201)}) {
      assertTrue(service.commit("Lookup", message).isFailure());
    }
    assertTrue(versions.getChangeLogs(path()).isEmpty());
    assertEquals("saved", service.getOne("Lookup").orElseThrow().getTitle());
    assertEquals("modeler", locks.getLockOwner(path()));
    assertTrue(service.commit("Lookup", "x".repeat(200)).isSuccess());
  }

  @Test
  void occupiedResourceAndForeignLockRejectCreateWithoutChangingTheWorkspace() {
    locks.lock(path(), "another-user").get();
    assertTrue(service.create(model("one"), "").isFailure());
    assertFalse(files.exists(path()));
    assertEquals("another-user", locks.getLockOwner(path()));
    locks.unlock(path()).get();
    service.create(model("one"), "").get();
    assertTrue(service.create(model("overwrite"), "").isFailure());
    assertEquals("one", service.getOne("Lookup").orElseThrow().getTitle());
    assertEquals(1, events.size());
  }

  @Test
  void failedWriteReleasesOnlyNewLocksAndRetainsExistingUserLocks() {
    when(vcs.batchOperation()).thenAnswer(ignore -> new DefaultBatchAction(versions, files, locks, user) {
      @Override protected boolean doWriteContent() { throw new BusinessException("disk unavailable"); }
    });
    assertTrue(service.create(model("one"), "").isFailure());
    assertFalse(files.exists(path()));
    assertNull(locks.getLockOwner(path()));
    locks.lock(path(), "modeler").get();
    assertTrue(service.create(model("one"), "").isFailure());
    assertEquals("modeler", locks.getLockOwner(path()));
    assertTrue(events.isEmpty());
  }

  @Test
  void rejectedCommitKeepsTheSavedWorkspaceAndCanRetryCommitAlone() throws Exception {
    service.create(model("saved"), "").get();
    var original = files.getFileBytes(path()).get();
    Path hook = home.resolve("metadata/.git/hooks/pre-commit");
    Files.writeString(hook, "#!/bin/sh\nexit 1\n");
    Files.setPosixFilePermissions(hook, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
    var result = service.commit("Lookup", "first attempt");
    assertTrue(result.isFailure());
    assertNotNull(result.getCause().getCause());
    assertTrue(versions.getChangeLogs(path()).isEmpty());
    assertArrayEquals(original, files.getFileBytes(path()).get());
    assertEquals("modeler", locks.getLockOwner(path()));
    Files.delete(hook);
    service.commit("Lookup", "retry").get();
    assertEquals(1, versions.getChangeLogs(path()).size());
    assertEquals(1, events.size());
  }

  @Test
  void originalCreateStrategyStillCommitsAndUnlocks() {
    service.autoCommitCreate = true;
    service.create(model("one"), "original create").get();
    assertEquals("original create", versions.getChangeLogs(path()).getFirst().getMessage());
    assertNull(locks.getLockOwner(path()));
    assertTrue(versions.isWorkspaceClean().get());
  }

  private String path() { return "rpc-model/Lookup.rpc.json"; }

  private RPCModelMetadata model(String title) {
    var model = new RPCModelMetadata();
    model.setName("Lookup"); model.setTitle(title); model.setType("HTTP"); model.setEnabled(true);
    model.setRequest(Map.of("url", "http://localhost/test"));
    return model;
  }

  private final class TestService extends RpcMetadataDevelopServiceImpl {
    private boolean autoCommitCreate;
    TestService() {
      vcsManagerCenter = mock(VCSManagerCenter.class);
      when(vcsManagerCenter.getVcsManager("rpc-save-test")).thenReturn(vcs);
      publisher = events::add;
    }
    @Override protected boolean isAutoCommitWhenCreate() { return autoCommitCreate; }
  }
}
