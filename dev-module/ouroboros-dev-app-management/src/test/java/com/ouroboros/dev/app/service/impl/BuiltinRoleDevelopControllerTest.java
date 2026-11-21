package com.ouroboros.dev.app.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.vavr.control.Try;

import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.modelmanage.DevelopService;
import com.ouroboros.modelmanage.web.ModelDevelopController;
import com.ouroboros.security.rbac.model.BuiltinRoleAuthorityModel;
import com.ouroboros.security.rbac.model.BuiltinRoleModel;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.VCSConfig;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.action.VCSAction;
import com.ouroboros.vcs.file.FileDiff;
import com.ouroboros.vcs.file.FileOperation;
import com.ouroboros.vcs.file.FileRevision;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.lock.LockState;
import com.ouroboros.vcs.versioning.ChangeLog;
import com.ouroboros.vcs.versioning.Tag;
import com.ouroboros.vcs.versioning.VersionState;

class BuiltinRoleDevelopControllerTest {

  @TempDir
  java.nio.file.Path tempDir;

  @AfterEach
  void tearDown() {
    DevAppHolder.removeAppName();
  }

  @Test
  void controllerShouldPersistBuiltinRoleFilesThroughDevelopService() throws Exception {
    DevAppHolder.setAppName("demo-app");
    FakeVCSManager vcsManager = new FakeVCSManager();
    TestBuiltinRoleDevelopService service = new TestBuiltinRoleDevelopService(vcsManager);
    ModelDevelopController<?> controller = new ModelDevelopController<>(Collections.<DevelopService<?>>singletonList(service));
    MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

    BuiltinRoleModel created = builtinRole("builtin-reviewer", "内置审阅者", "workflow.approve");
    mvc.perform(post("/api/dev/demo-app/builtin-role")
            .contentType("application/json")
            .content(Json.toPrettyJsonString(created)))
        .andExpect(status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(0));

    assertTrue(vcsManager.read("rbac/builtin-roles/builtin-reviewer.role.json").contains("内置审阅者"));

    mvc.perform(get("/api/dev/demo-app/builtin-role/builtin-reviewer"))
        .andExpect(status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(0));

    BuiltinRoleModel updated = builtinRole("builtin-reviewer", "内置审阅者 V2", "workflow.approve", "workflow.assign");
    mvc.perform(put("/api/dev/demo-app/builtin-role/builtin-reviewer")
            .contentType("application/json")
            .content(Json.toPrettyJsonString(updated)))
        .andExpect(status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(0));

    assertTrue(vcsManager.read("rbac/builtin-roles/builtin-reviewer.role.json").contains("内置审阅者 V2"));
    assertTrue(vcsManager.read("rbac/builtin-roles/builtin-reviewer.role.json").contains("workflow.assign"));

    mvc.perform(delete("/api/dev/demo-app/builtin-role/builtin-reviewer"))
        .andExpect(status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(0));

    assertFalse(vcsManager.exists("rbac/builtin-roles/builtin-reviewer.role.json"));
  }

  @Test
  void controllerShouldRejectUnsafeBuiltinRoleIdOnCreate() throws Exception {
    DevAppHolder.setAppName("demo-app");
    FakeVCSManager vcsManager = new FakeVCSManager();
    TestBuiltinRoleDevelopService service = new TestBuiltinRoleDevelopService(vcsManager);
    ModelDevelopController<?> controller = new ModelDevelopController<>(Collections.<DevelopService<?>>singletonList(service));
    MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

    BuiltinRoleModel unsafe = builtinRole("../escape", "非法内置角色", "workflow.approve");
    mvc.perform(post("/api/dev/demo-app/builtin-role")
            .contentType("application/json")
            .content(Json.toPrettyJsonString(unsafe)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(400))
        .andExpect(jsonPath("$.msg").value("内置角色标识非法"));

    assertFalse(vcsManager.exists("rbac/escape.role.json"));
  }

  @Test
  void controllerShouldRejectWhitespacePaddedBuiltinRoleIdOnCreate() throws Exception {
    DevAppHolder.setAppName("demo-app");
    FakeVCSManager vcsManager = new FakeVCSManager();
    TestBuiltinRoleDevelopService service = new TestBuiltinRoleDevelopService(vcsManager);
    ModelDevelopController<?> controller = new ModelDevelopController<>(Collections.<DevelopService<?>>singletonList(service));
    MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

    BuiltinRoleModel unsafe = builtinRole(" builtin-reviewer ", "非法内置角色", "workflow.approve");
    mvc.perform(post("/api/dev/demo-app/builtin-role")
            .contentType("application/json")
            .content(Json.toPrettyJsonString(unsafe)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(400))
        .andExpect(jsonPath("$.msg").value("内置角色标识非法"));

    assertFalse(vcsManager.exists("rbac/builtin-roles/ builtin-reviewer .role.json"));
  }

  @Test
  void controllerShouldRejectInvalidBuiltinRoleAuthoritiesOnCreate() throws Exception {
    DevAppHolder.setAppName("demo-app");
    FakeVCSManager vcsManager = new FakeVCSManager();
    TestBuiltinRoleDevelopService service = new TestBuiltinRoleDevelopService(vcsManager);
    ModelDevelopController<?> controller = new ModelDevelopController<>(Collections.<DevelopService<?>>singletonList(service));
    MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

    BuiltinRoleModel blankAuthority = builtinRole("builtin-reviewer", "内置审阅者", "");
    mvc.perform(post("/api/dev/demo-app/builtin-role")
            .contentType("application/json")
            .content(Json.toPrettyJsonString(blankAuthority)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(400))
        .andExpect(jsonPath("$.msg").value("权限标识不能为空: 第1项"));

    BuiltinRoleModel duplicateAuthority = builtinRole("builtin-reviewer", "内置审阅者", "workflow.approve", "workflow.approve");
    mvc.perform(post("/api/dev/demo-app/builtin-role")
            .contentType("application/json")
            .content(Json.toPrettyJsonString(duplicateAuthority)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(400))
        .andExpect(jsonPath("$.msg").value("权限标识重复: workflow.approve"));

    assertFalse(vcsManager.exists("rbac/builtin-roles/builtin-reviewer.role.json"));
  }

  @Test
  void serviceShouldRejectUnsafeBuiltinRoleFullNameBeforeVcsPathAccess() {
    FakeVCSManager vcsManager = new FakeVCSManager();
    TestBuiltinRoleDevelopService service = new TestBuiltinRoleDevelopService(vcsManager);

    BusinessException error = assertThrows(BusinessException.class, () -> service.delete("../escape", ""));

    assertEquals(400, error.getErrorCode());
    assertEquals("内置角色标识非法", error.getMessage());
    assertFalse(vcsManager.exists("rbac/escape.role.json"));
  }

  private BuiltinRoleModel builtinRole(String id, String name, String... authorities) {
    BuiltinRoleModel model = new BuiltinRoleModel();
    model.setId(id);
    model.setName(name);
    java.util.ArrayList<BuiltinRoleAuthorityModel> authorityModels = new java.util.ArrayList<>();
    for (String authority : authorities) {
      BuiltinRoleAuthorityModel authorityModel = new BuiltinRoleAuthorityModel();
      authorityModel.setAuthority(authority);
      authorityModel.setAccessControlParams(Collections.<String, Object>emptyMap());
      authorityModels.add(authorityModel);
    }
    model.setAuthorities(authorityModels);
    return model;
  }

  @com.ouroboros.modelmanage.annotation.DevelopModule("builtin-role")
  private static class TestBuiltinRoleDevelopService extends BuiltinRoleDevelopService {
    private TestBuiltinRoleDevelopService(FakeVCSManager vcsManager) {
      this.vcsManagerCenter = new VCSManagerCenter() {
        @Override
        public VCSManager getVcsManager(String appName) {
          return vcsManager;
        }
      };
      this.publisher = new ApplicationEventPublisher() {
        @Override
        public void publishEvent(Object event) {
        }
      };
    }
  }

  private static class FakeVCSManager implements VCSManager {
    private final Map<String, String> files = new HashMap<>();

    private String read(String path) {
      return files.get(path);
    }

    private boolean exists(String path) {
      return files.containsKey(path);
    }

    @Override
    public void destroy() {
    }

    @Override
    public VCSFile getFile(String relativePath) {
      return new FakeVCSFile(relativePath, files);
    }

    @Override
    public Try<Void> lock(String relativePath, boolean force) {
      return Try.success(null);
    }

    @Override
    public Try<Void> lock(Collection<String> relativePaths, boolean force) {
      return Try.success(null);
    }

    @Override
    public Try<Void> unlock(String relativePath, boolean force) {
      return Try.success(null);
    }

    @Override
    public Try<Void> unlock(Collection<String> relativePaths, boolean force) {
      return Try.success(null);
    }

    @Override
    public VCSAction.CreateFileAction createFile(String relativePath) {
      throw new UnsupportedOperationException();
    }

    @Override
    public VCSAction.RemoveFileAction removeFile(String relativePath) {
      throw new UnsupportedOperationException();
    }

    @Override
    public VCSAction.UpdateFileAction updateFile(String relativePath) {
      throw new UnsupportedOperationException();
    }

    @Override
    public VCSAction.MoveFileAction moveFile(String relativePath, String newRelativePath) {
      throw new UnsupportedOperationException();
    }

    @Override
    public VCSAction.BatchAction batchOperation() {
      return new FakeBatchAction(files);
    }

    @Override
    public Stream<VCSFile> walkDirectory(String relativeParentPath, String fileExtension) {
      return Stream.empty();
    }

    @Override
    public List<ChangeLog> getChangeLogs(String relativePath) {
      return Collections.emptyList();
    }

    @Override
    public List<ChangeLog> getChangeLogs(String relativePath, int skip, int limit) {
      return Collections.emptyList();
    }

    @Override
    public List<ChangeLog> getChangeLogs(Collection<String> relativePaths) {
      return Collections.emptyList();
    }

    @Override
    public List<ChangeLog> getChangeLogs(Collection<String> relativePaths, int skip, int limit) {
      return Collections.emptyList();
    }

    @Override
    public List<VCSFile> getUncommittedFiles(String relativePath) {
      return Collections.emptyList();
    }

    @Override
    public VCSFile getFile(String relativePath, String revision) {
      return getFile(relativePath);
    }

    @Override
    public VCSFile getFileFromBranch(String relativePath, String branchName) {
      return getFile(relativePath);
    }

    @Override
    public VCSFile getFileFromTag(String relativePath, String tagName) {
      return getFile(relativePath);
    }

    @Override
    public List<VCSFile> getFileList(String relativeParentPath, String revision) {
      return Collections.emptyList();
    }

    @Override
    public List<VCSFile> getFileListFromBranch(String relativeParentPath, String branchName) {
      return Collections.emptyList();
    }

    @Override
    public List<VCSFile> getFileListFromTag(String relativeParentPath, String tagName) {
      return Collections.emptyList();
    }

    @Override
    public Stream<VCSFile> walkDirectory(String relativeParentPath, String revision, String fileExtension) {
      return Stream.empty();
    }

    @Override
    public Try<String> createTag(String tagName, String message) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<String> createTag(String tagName, String revision, String message) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<Boolean> deleteTag(String tagName) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<List<Tag>> getTagList() {
      return Try.success(Collections.emptyList());
    }

    @Override
    public Try<String> getCurrentBranch() {
      return Try.success("main");
    }

    @Override
    public Try<List<String>> getBranchList() {
      return Try.success(Collections.singletonList("main"));
    }

    @Override
    public Try<String> createBranch(String branchName) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<String> createBranch(String branchName, String fromRevision) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<Boolean> switchBranch(String branchName) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<VCSConfig.RemoteConfig> getRemoteConfig() {
      return Try.success(null);
    }

    @Override
    public Try<Boolean> setRemoteConfig(VCSConfig.RemoteConfig remoteConfig) {
      return Try.success(true);
    }

    @Override
    public Try<Boolean> pull() {
      return Try.success(true);
    }

    @Override
    public Try<Boolean> push() {
      return Try.success(true);
    }

    @Override
    public Try<Boolean> isWorkspaceClean() {
      return Try.success(true);
    }
  }

  private static class FakeBatchAction implements VCSAction.BatchAction {
    private final Map<String, String> files;
    private final List<Runnable> validations = new java.util.ArrayList<>();
    private final Map<String, String> creates = new HashMap<>();
    private final Map<String, String> updates = new HashMap<>();
    private final java.util.List<String> removes = new java.util.ArrayList<>();

    private FakeBatchAction(Map<String, String> files) {
      this.files = files;
    }

    @Override
    public VCSAction.BatchAction committer(String user) {
      return this;
    }

    @Override
    public VCSAction.BatchAction committerEmail(String email) {
      return this;
    }

    @Override
    public VCSAction.BatchAction autoLock() {
      return this;
    }

    @Override
    public VCSAction.BatchAction unlockWhenCommit() {
      return this;
    }

    @Override
    public VCSAction.BatchAction beforeWrite(Runnable validation) {
      validations.add(validation);
      return this;
    }

    @Override
    public Try<Boolean> write(boolean forceWrite) {
      return Try.of(() -> {
        validations.forEach(Runnable::run);
        apply();
        return true;
      });
    }

    @Override
    public Try<Boolean> commit(String message) {
      return Try.of(() -> {
        validations.forEach(Runnable::run);
        apply();
        return true;
      });
    }

    @Override
    public VCSAction.BatchAction add(String relativePath) {
      return this;
    }

    @Override
    public VCSAction.BatchAction add(Collection<String> relativePaths) {
      return this;
    }

    @Override
    public VCSAction.BatchAction create(String relativePath, String content) {
      creates.put(relativePath, content);
      return this;
    }

    @Override
    public VCSAction.BatchAction create(String relativePath, byte[] content) {
      return create(relativePath, new String(content));
    }

    @Override
    public VCSAction.BatchAction create(String relativePath, InputStream content) {
      throw new UnsupportedOperationException();
    }

    @Override
    public VCSAction.BatchAction remove(String relativePath) {
      removes.add(relativePath);
      return this;
    }

    @Override
    public VCSAction.BatchAction remove(Collection<String> relativePaths) {
      removes.addAll(relativePaths);
      return this;
    }

    @Override
    public VCSAction.BatchAction update(String relativePath, String content) {
      updates.put(relativePath, content);
      return this;
    }

    @Override
    public VCSAction.BatchAction update(String relativePath, byte[] content) {
      return update(relativePath, new String(content));
    }

    @Override
    public VCSAction.BatchAction update(String relativePath, InputStream content) {
      throw new UnsupportedOperationException();
    }

    @Override
    public VCSAction.BatchAction move(String relativePath, String newRelativePath) {
      throw new UnsupportedOperationException();
    }

    @Override
    public VCSAction.BatchAction move(String relativePath, String newRelativePath, java.util.function.Function<String, String> updateContent) {
      throw new UnsupportedOperationException();
    }

    private void apply() {
      removes.forEach(files::remove);
      files.putAll(creates);
      files.putAll(updates);
      creates.clear();
      updates.clear();
      removes.clear();
    }
  }

  private static class FakeVCSFile implements VCSFile {
    private final String path;
    private final Map<String, String> files;

    private FakeVCSFile(String path, Map<String, String> files) {
      this.path = path;
      this.files = files;
    }

    @Override
    public boolean exists() {
      return files.containsKey(path);
    }

    @Override
    public String getPath() {
      return path;
    }

    @Override
    public String getFileName() {
      return path;
    }

    @Override
    public String getContent() {
      return files.get(path);
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(getBytesArray());
    }

    @Override
    public byte[] getBytesArray() {
      return getContent() == null ? new byte[0] : getContent().getBytes();
    }

    @Override
    public Long getSize() {
      return (long) getBytesArray().length;
    }

    @Override
    public LocalDateTime getCreationTime() {
      return null;
    }

    @Override
    public LocalDateTime getLastCommittedTime() {
      return null;
    }

    @Override
    public LocalDateTime getLastModifiedTime() {
      return null;
    }

    @Override
    public VersionState getVersionState() {
      return VersionState.UNKNOWN;
    }

    @Override
    public LockState getLockState() {
      return LockState.UNLOCKED;
    }

    @Override
    public String getLockOwner() {
      return null;
    }

    @Override
    public String getLockOwnerName() {
      return null;
    }

    @Override
    public Try<Void> lock(boolean forceLock) {
      return Try.success(null);
    }

    @Override
    public Try<Void> unlock(boolean forceUnlock) {
      return Try.success(null);
    }

    @Override
    public Try<Boolean> write(String content) {
      files.put(path, content);
      return Try.success(true);
    }

    @Override
    public Try<Boolean> write(byte[] content) {
      files.put(path, new String(content));
      return Try.success(true);
    }

    @Override
    public Try<Boolean> write(InputStream content) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<VCSFile> move(String newPath) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<VCSFile> remove() {
      files.remove(path);
      return Try.success(this);
    }

    @Override
    public Try<Boolean> commit(String message) {
      return Try.success(true);
    }

    @Override
    public Try<Boolean> rollback(boolean forceRollback) {
      return Try.success(true);
    }

    @Override
    public String getRevision() {
      return null;
    }

    @Override
    public boolean isHistorical() {
      return false;
    }

    @Override
    public VCSFile atRevision(String revision) {
      return this;
    }

    @Override
    public VCSFile atBranch(String branchName) {
      return this;
    }

    @Override
    public VCSFile atTag(String tagName) {
      return this;
    }

    @Override
    public Try<FileDiff> diffWith(String otherRevision) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<FileDiff> diffWithWorkingCopy() {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public List<FileRevision> getRevisions() {
      return Collections.emptyList();
    }

    @Override
    public List<FileRevision> getRevisions(int skip, int limit) {
      return Collections.emptyList();
    }

    @Override
    public boolean isOperationAllowed(FileOperation operation) {
      return true;
    }
  }
}
