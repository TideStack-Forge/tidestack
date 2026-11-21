package com.ouroboros.resource.dev.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.modelmanage.web.filter.DevelopAppNameFilter;
import com.ouroboros.resource.dev.model.ResourceModel;
import com.ouroboros.resource.dev.service.ResourceDevService;
import com.ouroboros.resource.model.ResourceScope;
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
import io.vavr.control.Try;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ResourceViewControllerTest {

  private static final String APP_NAME = "demo";

  private TestVCSManager vcsManager;
  private ResourceDevService service;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    vcsManager = new TestVCSManager(Collections.emptyMap());
    service = new ResourceDevService();
    ReflectionTestUtils.setField(service, "vcsManagerCenter", new TestVCSManagerCenter(vcsManager));
    ReflectionTestUtils.setField(service, "publisher", (ApplicationEventPublisher) event -> {
    });

    ResourceViewController controller = new ResourceViewController(service);
    mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .addFilters(new DevelopAppNameFilter())
        .build();
  }

  @AfterEach
  void cleanup() {
    DevAppHolder.removeAppName();
  }

  @Test
  void viewContentReturnsInlineBytesForFrontendResources() throws Exception {
    createMetadata("images/logo.svg");
    withinApp(() -> service.uploadResourceFile(
        "frontend/images/logo.svg",
        new ByteArrayInputStream("<svg/>".getBytes(StandardCharsets.UTF_8)),
        "image/svg+xml",
        "logo.svg"));

    mockMvc.perform(get("/api/dev/demo/resource/view")
            .param("key", "images/logo.svg"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/svg+xml"))
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")))
        .andExpect(content().bytes("<svg/>".getBytes(StandardCharsets.UTF_8)));
  }

  private void createMetadata(String key) {
    withinApp(() -> service.create(new ResourceModel()
        .setScope(ResourceScope.FRONTEND)
        .setKey(key), "init resource"));
  }

  private <T> T withinApp(java.util.function.Supplier<T> supplier) {
    DevAppHolder.setAppName(APP_NAME);
    try {
      return supplier.get();
    } finally {
      DevAppHolder.removeAppName();
    }
  }

  private static class TestVCSManagerCenter extends VCSManagerCenter {
    private final VCSManager vcsManager;

    private TestVCSManagerCenter(VCSManager vcsManager) {
      this.vcsManager = vcsManager;
    }

    @Override
    public VCSManager getVcsManager(String appName) {
      return vcsManager;
    }
  }

  private static class TestVCSManager implements VCSManager {
    private final Map<String, byte[]> files;

    private TestVCSManager(Map<String, String> initialFiles) {
      this.files = new LinkedHashMap<>();
      initialFiles.forEach((path, content) ->
          this.files.put(path, content.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public void destroy() {
    }

    @Override
    public VCSFile getFile(String relativePath) {
      return new TestFile(relativePath, files);
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
      return new TestBatchAction(files);
    }

    @Override
    public Stream<VCSFile> walkDirectory(String relativeParentPath, String fileExtension) {
      return files.keySet().stream()
          .filter(path -> path.startsWith(relativeParentPath))
          .filter(path -> fileExtension == null || path.endsWith(fileExtension))
          .sorted(Comparator.naturalOrder())
          .map(path -> new TestFile(path, files));
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
      return new ArrayList<>();
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
      return walkDirectory(relativeParentPath, fileExtension);
    }

    @Override
    public Try<String> createTag(String tagName, String message) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<String> createTag(String tagName, String revision, String message) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<Boolean> deleteTag(String tagName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<List<Tag>> getTagList() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<String> getCurrentBranch() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<List<String>> getBranchList() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<String> createBranch(String branchName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<String> createBranch(String branchName, String fromRevision) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<Boolean> switchBranch(String branchName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<com.ouroboros.vcs.VCSConfig.RemoteConfig> getRemoteConfig() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<Boolean> setRemoteConfig(com.ouroboros.vcs.VCSConfig.RemoteConfig remoteConfig) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<Boolean> pull() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<Boolean> push() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<Boolean> isWorkspaceClean() {
      throw new UnsupportedOperationException();
    }
  }

  private static class TestBatchAction implements VCSAction.BatchAction {
    private final Map<String, byte[]> files;
    private final List<Runnable> validations = new ArrayList<>();
    private final List<Runnable> operations = new ArrayList<>();

    private TestBatchAction(Map<String, byte[]> files) {
      this.files = files;
    }

    @Override
    public VCSAction.BatchAction beforeWrite(Runnable validation) {
      validations.add(validation);
      return this;
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
      operations.add(() -> files.put(relativePath, content.getBytes(StandardCharsets.UTF_8)));
      return this;
    }

    @Override
    public VCSAction.BatchAction create(String relativePath, byte[] content) {
      operations.add(() -> files.put(relativePath, content));
      return this;
    }

    @Override
    public VCSAction.BatchAction create(String relativePath, InputStream content) {
      operations.add(() -> files.put(relativePath, readBytes(content)));
      return this;
    }

    @Override
    public VCSAction.BatchAction remove(String relativePath) {
      operations.add(() -> files.remove(relativePath));
      return this;
    }

    @Override
    public VCSAction.BatchAction remove(Collection<String> relativePaths) {
      operations.add(() -> relativePaths.forEach(files::remove));
      return this;
    }

    @Override
    public VCSAction.BatchAction update(String relativePath, String content) {
      operations.add(() -> files.put(relativePath, content.getBytes(StandardCharsets.UTF_8)));
      return this;
    }

    @Override
    public VCSAction.BatchAction update(String relativePath, byte[] content) {
      operations.add(() -> files.put(relativePath, content));
      return this;
    }

    @Override
    public VCSAction.BatchAction update(String relativePath, InputStream content) {
      operations.add(() -> files.put(relativePath, readBytes(content)));
      return this;
    }

    @Override
    public VCSAction.BatchAction move(String relativePath, String newRelativePath) {
      operations.add(() -> {
        byte[] content = files.remove(relativePath);
        if (content != null) {
          files.put(newRelativePath, content);
        }
      });
      return this;
    }

    @Override
    public VCSAction.BatchAction move(String relativePath, String newRelativePath,
                                      java.util.function.Function<String, String> updateContent) {
      operations.add(() -> {
        byte[] content = files.remove(relativePath);
        if (content != null) {
          String updated = updateContent.apply(new String(content, StandardCharsets.UTF_8));
          files.put(newRelativePath, updated.getBytes(StandardCharsets.UTF_8));
        }
      });
      return this;
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
    public Try<Boolean> write(boolean forceWrite) {
      validations.forEach(Runnable::run);
      validations.clear();
      operations.forEach(Runnable::run);
      operations.clear();
      return Try.success(true);
    }

    @Override
    public Try<Boolean> commit(String message) {
      return write(false);
    }

    private static byte[] readBytes(InputStream inputStream) {
      try {
        byte[] buffer = new byte[1024];
        int length;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        while ((length = inputStream.read(buffer)) >= 0) {
          outputStream.write(buffer, 0, length);
        }
        return outputStream.toByteArray();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static class TestFile implements VCSFile {
    private final String path;
    private final Map<String, byte[]> files;

    private TestFile(String path, Map<String, byte[]> files) {
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
      int slashIndex = path.lastIndexOf('/');
      return slashIndex >= 0 ? path.substring(slashIndex + 1) : path;
    }

    @Override
    public String getContent() {
      return new String(files.getOrDefault(path, new byte[0]), StandardCharsets.UTF_8);
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(getBytesArray());
    }

    @Override
    public byte[] getBytesArray() {
      return files.getOrDefault(path, new byte[0]);
    }

    @Override
    public Long getSize() {
      return (long) getBytesArray().length;
    }

    @Override
    public LocalDateTime getCreationTime() {
      return LocalDateTime.of(2026, 3, 14, 0, 0);
    }

    @Override
    public LocalDateTime getLastCommittedTime() {
      return getCreationTime();
    }

    @Override
    public LocalDateTime getLastModifiedTime() {
      return getCreationTime();
    }

    @Override
    public VersionState getVersionState() {
      return VersionState.NOTCHANGED;
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
      files.put(path, content.getBytes(StandardCharsets.UTF_8));
      return Try.success(true);
    }

    @Override
    public Try<Boolean> write(byte[] content) {
      files.put(path, content);
      return Try.success(true);
    }

    @Override
    public Try<Boolean> write(InputStream content) {
      files.put(path, TestBatchAction.readBytes(content));
      return Try.success(true);
    }

    @Override
    public Try<VCSFile> move(String newPath) {
      byte[] content = files.remove(path);
      if (content != null) {
        files.put(newPath, content);
      }
      return Try.success(new TestFile(newPath, files));
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
      files.remove(path);
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
      return Try.success(null);
    }

    @Override
    public Try<FileDiff> diffWithWorkingCopy() {
      return Try.success(null);
    }

    @Override
    public List<FileRevision> getRevisions() {
      return Collections.emptyList();
    }

    @Override
    public List<FileRevision> getRevisions(int skip, int limit) {
      return Collections.emptyList();
    }
  }
}
